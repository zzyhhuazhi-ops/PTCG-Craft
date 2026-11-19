package dev.hua.ptcglive.view;

import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.BattlefieldState.PokemonView;
import dev.hua.ptcglive.engine.field.SpecialCondition;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.*;

/** Presentation only: never rolls, mutates, authorizes or inspects a hidden zone. */
public final class MatchPresentation {
    private MatchPresentation() {}
    public enum Kind { DAMAGE, HEAL, ENERGY, STATUS, PRIZE, ATTACK, KNOCKOUT, COIN, DRAW, PLAY, EVOLVE, STADIUM, ABILITY,
        MOVE, LEAVE, TOOL, DISCARD, LOST, SHUFFLE, DEVOLVE, TURN, RESULT, CHECKUP }
    public record Cue(Kind kind, UUID seat, UUID pokemon, int amount, String text, String cardId) {}
    public static boolean journalGap(MatchSession.Snapshot before,MatchSession.Snapshot after){
        if(before.events()==null||after.events()==null)return false;
        long seen=before.events().isEmpty()?0:before.events().getLast().sequence();
        if(after.events().isEmpty())return seen!=0;
        return after.events().getFirst().sequence()>seen+1||after.events().getLast().sequence()<seen;
    }
    public static List<Cue> batchDraws(List<Cue> cues) {
        var result=new ArrayList<Cue>();
        for(var cue:cues) {
            if(cue.kind()==Kind.DRAW&&!result.isEmpty()&&result.getLast().kind()==Kind.DRAW&&Objects.equals(result.getLast().seat(),cue.seat())) {
                var previous=result.removeLast();int count=previous.amount()+cue.amount();
                result.add(new Cue(Kind.DRAW,cue.seat(),null,count,"抽牌 "+count+" 张",null));
            } else result.add(cue);
        }
        return List.copyOf(result);
    }
    public static List<Cue> changes(MatchSession.Snapshot before,MatchSession.Snapshot after) {
        if(before==null||after==null||after.revision()<=before.revision()
            ||!before.game().matchId().equals(after.game().matchId())
            ||!Objects.equals(before.game().viewingSeat(),after.game().viewingSeat()))return List.of();
        var result=new ArrayList<Cue>();
        if(before.events()!=null&&after.events()!=null) {
            if(journalGap(before,after))return List.of();
            long seen=before.events().isEmpty()?0:before.events().getLast().sequence();
            var fresh=after.events().stream().filter(e->e.sequence()>seen).map(dev.hua.ptcglive.engine.BattleEventJournal.Event::cue).toList();
            return batchDraws(prizeFace(before,after,fresh));
        }
        boolean adjacent=after.revision()==before.revision()+1;
        List<String> publicCues=after.publicCues()==null?List.of():after.publicCues();
        // Public cues describe the latest confirmed action even when a network update was skipped.
        // Field deltas need adjacent revisions, or several actions could be mistaken for one play.
        if(adjacent) {
        var publicToolIds=new HashSet<UUID>();
        for(var player:before.game().players())for(var mon:field(player).values())
            for(var tool:mon.attachedTools())publicToolIds.add(tool.instanceId());
        for(var next:after.game().players()) {
            var old=before.game().players().stream().filter(p->p.id().equals(next.id())).findFirst().orElse(null);
            if(old==null)continue;
            var previous=field(old);
            var current=field(next);
            var newDiscard=addedCards(old.discard(),next.discard());
            var newLost=addedCards(old.lostZone(),next.lostZone());
            var priorEnergy=energyAttachments(previous);
            var nextEnergy=energyAttachments(current);
            var priorTools=toolAttachments(previous);
            var nextTools=toolAttachments(current);
            var handEnergy=handEnergyCounts(publicCues,next.id());
            var switchMarker=switchMarker(publicCues,next.id(),
                old.active()==null?null:old.active().pokemonId(),
                next.active()==null?null:next.active().pokemonId());
            // Setup can reveal an already placed opponent field. Only live, public field changes are new plays.
            if(before.game().turnNumber()>0&&after.game().turnNumber()>0)for(var mon:current.values()) {
                var prior=previous.get(mon.pokemonId());
                if(prior==null) {
                    String handCue="手牌宝可梦·"+next.id()+"·"+mon.pokemonId();
                    boolean fromHand=publicCues.contains(handCue);
                    result.add(new Cue(Kind.PLAY,next.id(),mon.pokemonId(),0,
                        fromHand?"从手牌上场":"宝可梦登场",mon.topCardId()));
                }
                else if(!Objects.equals(prior.topCardId(),mon.topCardId())&&evolved(prior,mon))
                    result.add(new Cue(Kind.EVOLVE,next.id(),mon.pokemonId(),0,"宝可梦进化",mon.topCardId()));
                else if(!Objects.equals(prior.topCardId(),mon.topCardId())
                        &&mon.evolutionCardIds().size()<prior.evolutionCardIds().size())
                    result.add(new Cue(Kind.DEVOLVE,next.id(),mon.pokemonId(),0,"宝可梦退化",mon.topCardId()));
            }
            if(before.game().turnNumber()==0&&after.game().turnNumber()==1)
                for(var mon:current.values()) result.add(new Cue(Kind.PLAY,next.id(),mon.pokemonId(),0,"开局亮牌",mon.topCardId()));
            // A promotion after a Knock Out is one-way. A confirmed retreat/switch moves both cards.
            for(var entry:current.entrySet()) {
                var mon=entry.getValue();var prior=previous.get(entry.getKey());if(prior==null)continue;
                boolean wasActive=old.active()!=null&&old.active().pokemonId().equals(mon.pokemonId());
                boolean isActive=next.active()!=null&&next.active().pokemonId().equals(mon.pokemonId());
                if(!wasActive&&isActive&&old.active()==null&&before.game().phase()==MatchPhase.CHOOSE_REPLACEMENT)
                    result.add(new Cue(Kind.MOVE,next.id(),mon.pokemonId(),0,"备战区→战斗位·替补",mon.topCardId()));
                if(wasActive!=isActive&&switchMarker!=null
                    &&(wasActive&&mon.pokemonId().equals(switchMarker.outgoing())
                        ||isActive&&mon.pokemonId().equals(switchMarker.incoming()))) {
                    String direction=wasActive?"战斗位→备战区":"备战区→战斗位";
                    result.add(new Cue(Kind.MOVE,next.id(),mon.pokemonId(),0,
                        direction+"·"+switchMarker.reason(),mon.topCardId()));
                }
                int change=mon.damage()-prior.damage();
                if(change!=0)result.add(new Cue(change>0?Kind.DAMAGE:Kind.HEAL,next.id(),mon.pokemonId(),
                    Math.abs(change),(change>0?"伤害 +":"伤害移除 ")+Math.abs(change),null));
                for(var condition:SpecialCondition.values()) {
                    boolean had=prior.conditions().contains(condition),has=mon.conditions().contains(condition);
                    if(had!=has)result.add(new Cue(Kind.STATUS,next.id(),mon.pokemonId(),0,
                        conditionName(condition)+(has?"·附加":"·解除"),null));
                }
            }
            for(var prior:previous.values())if(!current.containsKey(prior.pokemonId())) {
                String destination=leaveDestination(publicCues,next.id(),prior.pokemonId());
                if(destination==null) {
                    boolean discard=hasDefinition(newDiscard,prior.topCardId());
                    boolean lost=hasDefinition(newLost,prior.topCardId());
                    if(discard!=lost)destination=discard?"弃牌区":"放逐区";
                }
                String action=publicCues.contains("击倒·"+next.id()+"·"+prior.pokemonId())?"击倒":"离场";
                result.add(new Cue(Kind.LEAVE,next.id(),prior.pokemonId(),0,
                    destination==null?action:action+"→"+destination,prior.topCardId()));
                if("弃牌区".equals(destination)||"放逐区".equals(destination)) {
                    var zone="弃牌区".equals(destination)?newDiscard:newLost;
                    // One animation represents the leaving stack; its attached cards must not fly twice.
                    var stack=prior.evolutionCardIds();
                    for(int i=stack.size()-1;i>=0;i--)claimDefinition(zone,stack.get(i));
                    for(var energy:prior.attachedEnergyIds())zone.remove(energy);
                    for(var tool:prior.attachedTools())zone.remove(tool.instanceId());
                }
                // Other attached cards may have been redirected to a different public zone.
                for(var energy:prior.attachedEnergyIds()) {newDiscard.remove(energy);newLost.remove(energy);}
                for(var tool:prior.attachedTools()) {newDiscard.remove(tool.instanceId());newLost.remove(tool.instanceId());}
            }
            for(var entry:priorEnergy.entrySet()) {
                UUID instance=entry.getKey();var source=entry.getValue();
                if(current.containsKey(source.pokemon())&&!nextEnergy.containsKey(instance)) {
                    String destination=claimAttachment(newDiscard,newLost,instance);
                    result.add(new Cue(Kind.ENERGY,next.id(),source.pokemon(),-1,
                        destination==null?"能量移除":"能量"+(destination.equals("弃牌区")?"弃置":"放逐"),source.cardId()));
                }
            }
            for(var entry:nextEnergy.entrySet()) {
                UUID instance=entry.getKey();var target=entry.getValue();var source=priorEnergy.get(instance);
                if(source!=null&&source.pokemon().equals(target.pokemon()))continue;
                if(source!=null)result.add(new Cue(Kind.ENERGY,next.id(),target.pokemon(),1,
                    "能量转移·"+source.pokemon(),target.cardId()));
                else if(!consumeHandEnergy(handEnergy,target.pokemon(),target.cardId()))
                    result.add(new Cue(Kind.ENERGY,next.id(),target.pokemon(),1,"能量附着",target.cardId()));
            }
            for(var entry:priorTools.entrySet()) {
                UUID instance=entry.getKey();var source=entry.getValue();
                String returned=publicCues.stream().filter(c->c.startsWith("工具回手·"+next.id()+"·"+source.pokemon()+"·"+instance+"·"))
                    .findFirst().orElse(null);
                // A mandatory return can accompany the host's departure; preserve that separate public destination.
                if((current.containsKey(source.pokemon())||returned!=null)&&!nextTools.containsKey(instance)) {
                    var pile=toolPile(after.game(),instance);
                    result.add(new Cue(Kind.TOOL,next.id(),source.pokemon(),-1,
                        pile!=null?(pile.lost()?"工具放逐":"工具弃置")+(pile.seat().equals(next.id())?"":"·"+pile.seat()):
                            returned==null?"工具移除":"工具回手·"+returned.substring(returned.lastIndexOf('·')+1)+"·"+instance,source.cardId()));
                }
            }
            for(var entry:nextTools.entrySet()) {
                UUID instance=entry.getKey();var target=entry.getValue();var source=priorTools.get(instance);
                if(source!=null&&source.pokemon().equals(target.pokemon()))continue;
                result.add(new Cue(Kind.TOOL,next.id(),target.pokemon(),1,
                    source==null?"工具附着":"工具转移·"+source.pokemon(),target.cardId()));
            }
            // A played Trainer that immediately reaches discard has already been shown flying
            // out of the hand. Its destination is part of that play, not a second card event.
            for(String cue:publicCues) {
                String prefix="出牌·"+next.id()+"·";
                if(!cue.startsWith(prefix))continue;
                String cardId=cue.substring(prefix.length());
                if(!cardId.isBlank()&&result.stream().noneMatch(shown->shown.kind()==Kind.TOOL
                    &&shown.seat().equals(next.id())&&shown.amount()>0&&cardId.equals(shown.cardId())))
                    claimDefinition(newDiscard,cardId);
            }
            for(var card:newDiscard.values())if(!publicToolIds.contains(card.instanceId()))result.add(new Cue(Kind.DISCARD,next.id(),null,1,"弃牌区",card.definitionId()));
            for(var card:newLost.values())if(!publicToolIds.contains(card.instanceId()))result.add(new Cue(Kind.LOST,next.id(),null,1,"放逐区",card.definitionId()));
            int prizes=old.prizeCount()-next.prizeCount();
            if(prizes>0) {
                // A face is allowed only when this exact new hand identity is visible to the receiver.
                var oldIds=before.game().hand().stream().map(c->c.instanceId()).collect(java.util.stream.Collectors.toSet());
                var added=after.game().hand().stream().filter(c->!oldIds.contains(c.instanceId())).toList();
                String face=next.id().equals(after.game().viewingSeat())&&prizes==1&&added.size()==1
                    ?added.getFirst().definitionId():null;
                result.add(new Cue(Kind.PRIZE,next.id(),null,prizes,"拿取奖赏 "+prizes+" 张",face));
            }
        }
        var oldStadium=before.game().stadium();
        var stadium=after.game().stadium();
        boolean playedFromHand=publicCues.stream()
            .anyMatch(cue->cue.startsWith("场地出牌·"));
        if(!playedFromHand&&!Objects.equals(oldStadium==null?null:oldStadium.instanceId(),stadium==null?null:stadium.instanceId()))
            result.add(new Cue(Kind.STADIUM,null,null,0,stadium==null?"场地离场":"场地更新",
                stadium==null?null:stadium.definitionId()));
        }
        // Actual attack hits survive an immediate KO and must not be inferred from a net HP delta.
        // Counter placement, poison, and recoil keep neutral feedback unless the server marks a hit.
        var markedTargets=new HashSet<UUID>();
        for(String cue:publicCues)if(cue.startsWith("招式伤害·")) {
            String[] parts=cue.split("·",-1);
            if(parts.length==5)try {
                UUID owner=UUID.fromString(parts[1]),target=UUID.fromString(parts[2]);
                int amount=Integer.parseInt(parts[3]);
                String type=parts[4].isEmpty()?"":dev.hua.ptcglive.engine.field.EnergyType.valueOf(parts[4]).name();
                boolean visible=java.util.stream.Stream.of(before,after).flatMap(s->s.game().players().stream())
                    .anyMatch(player->player.id().equals(owner)&&field(player).containsKey(target));
                if(amount>0&&visible) {
                    if(markedTargets.add(target))result.removeIf(c->c.kind()==Kind.DAMAGE&&target.equals(c.pokemon()));
                    result.add(new Cue(Kind.DAMAGE,owner,target,amount,"招式伤害·"+type,null));
                }
            } catch(IllegalArgumentException ignored) { }
        }
        for(String cue:publicCues) {
            if(cue.equals("攻击结算完成")||cue.equals("混乱判定反面：攻击失败，自身放置3个伤害指示物（30点）")
                ||cue.equals("攻击前判定反面：攻击失败"))result.add(new Cue(Kind.ATTACK,null,null,0,cue,null));
            if(cue.equals("击倒结算完成"))result.add(new Cue(Kind.KNOCKOUT,null,null,0,cue,null));
            if(cue.startsWith("硬币·"))result.add(new Cue(Kind.COIN,null,null,0,cue,null));
            if(cue.equals("特性发动"))result.add(new Cue(Kind.ABILITY,null,null,0,cue,null));
            if(cue.startsWith("招式·")) {
                String[] parts=cue.split("·",4);
                if(parts.length==4)try {
                    UUID actor=UUID.fromString(parts[1]);
                    UUID source=parts[2].isEmpty()?null:UUID.fromString(parts[2]);
                    if(after.game().players().stream().anyMatch(player->player.id().equals(actor))) {
                        String label=parts[3];int amount=0;
                        for(String publicCue:publicCues)if(publicCue.startsWith("招式演出·"+actor+"·")) {
                            String[] presentation=publicCue.split("·",4);
                            if(presentation.length==4) {
                                amount=Math.max(0,Integer.parseInt(presentation[2]));
                                if(parts[3].equals("攻击结算完成"))label=presentation[3];
                            }
                        }
                        result.add(new Cue(Kind.ATTACK,actor,source,amount,label,null));
                    }
                } catch(IllegalArgumentException ignored) { }
            }
            if(cue.startsWith("特性·")) {
                String[] parts=cue.split("·",4);
                if(parts.length>=3)try {
                    UUID actor=UUID.fromString(parts[1]);
                    UUID source=parts[2].isEmpty()?null:UUID.fromString(parts[2]);
                    var nextPlayer=after.game().players().stream().filter(player->player.id().equals(actor)).findFirst().orElse(null);
                    var oldPlayer=before.game().players().stream().filter(player->player.id().equals(actor)).findFirst().orElse(null);
                    if(nextPlayer!=null){
                        if(source!=null&&!field(nextPlayer).containsKey(source)&&(oldPlayer==null||!field(oldPlayer).containsKey(source)))source=null;
                        result.add(new Cue(Kind.ABILITY,actor,source,0,parts.length==4&&!parts[3].isBlank()?parts[3]:"特性发动",null));
                    }
                } catch(IllegalArgumentException ignored) { }
            }
            if(cue.startsWith("出牌·")) {
                String[] parts=cue.split("·",3);
                if(parts.length==3&&!parts[2].isBlank())try {
                    UUID actor=UUID.fromString(parts[1]);
                    if(after.game().players().stream().anyMatch(player->player.id().equals(actor)))
                        if(result.stream().noneMatch(presentation->presentation.kind()==Kind.TOOL
                            &&presentation.seat().equals(actor)&&presentation.amount()>0&&parts[2].equals(presentation.cardId()))
                            ||result.stream().noneMatch(presentation->presentation.kind()==Kind.PLAY
                            &&presentation.seat().equals(actor)&&presentation.pokemon()==null
                            &&parts[2].equals(presentation.cardId())))
                            result.add(new Cue(Kind.PLAY,actor,null,0,"训练家出牌",parts[2]));
                } catch(IllegalArgumentException ignored) { }
            }
            if(cue.startsWith("场地出牌·")) {
                String[] parts=cue.split("·",3);
                if(parts.length==3&&!parts[2].isBlank())try {
                    UUID actor=UUID.fromString(parts[1]);
                    if(after.game().players().stream().anyMatch(player->player.id().equals(actor)))
                        result.add(new Cue(Kind.STADIUM,actor,null,0,"从手牌放置场地",parts[2]));
                } catch(IllegalArgumentException ignored) { }
            }
            if(!adjacent&&cue.startsWith("手牌宝可梦·")) {
                String[] parts=cue.split("·",3);
                if(parts.length==3)try {
                    UUID actor=UUID.fromString(parts[1]);
                    UUID pokemon=UUID.fromString(parts[2]);
                    var owner=after.game().players().stream().filter(player->player.id().equals(actor)).findFirst().orElse(null);
                    var publicCard=owner==null?null:field(owner).get(pokemon);
                    if(publicCard!=null)result.add(new Cue(Kind.PLAY,actor,pokemon,0,"从手牌上场",publicCard.topCardId()));
                } catch(IllegalArgumentException ignored) { }
            }
            if(cue.startsWith("手牌能量·")) {
                String[] parts=cue.split("·",4);
                if(parts.length==4&&!parts[3].isBlank())try {
                    UUID actor=UUID.fromString(parts[1]);
                    UUID target=UUID.fromString(parts[2]);
                    if(after.game().players().stream().anyMatch(player->player.id().equals(actor)))
                        result.add(new Cue(Kind.PLAY,actor,target,0,"能量附着",parts[3]));
                } catch(IllegalArgumentException ignored) { }
            }
            if(cue.startsWith("抽牌·")) {
                String[] parts=cue.split("·",3);
                if(parts.length==3)try {
                    UUID actor=UUID.fromString(parts[1]);
                    int count=Integer.parseInt(parts[2]);
                    if(count>0&&count<=60&&after.game().players().stream().anyMatch(player->player.id().equals(actor)))
                        result.add(new Cue(Kind.DRAW,actor,null,count,"抽牌 "+count+" 张",null));
                } catch(IllegalArgumentException ignored) { }
            }
            if(cue.startsWith("洗牌·")) {
                String[] parts=cue.split("·",2);
                if(parts.length==2)try {
                    UUID owner=UUID.fromString(parts[1]);
                    if(after.game().players().stream().anyMatch(player->player.id().equals(owner)))
                        result.add(new Cue(Kind.SHUFFLE,owner,null,0,"洗牌",null));
                } catch(IllegalArgumentException ignored) { }
            }
        }
        if(adjacent&&after.game().phase()==MatchPhase.TURN
            &&(after.game().turnNumber()!=before.game().turnNumber()
                ||!Objects.equals(after.game().currentPlayer(),before.game().currentPlayer())))
            result.add(new Cue(Kind.TURN,after.game().currentPlayer(),null,after.game().turnNumber(),"回合开始",null));
        if(adjacent&&after.game().phase()==MatchPhase.POKEMON_CHECKUP&&before.game().phase()!=MatchPhase.POKEMON_CHECKUP)
            result.add(new Cue(Kind.CHECKUP,null,null,0,"宝可梦检查",null));
        if(before.game().result()==null&&after.game().result()!=null)
            result.add(new Cue(Kind.RESULT,after.game().result().winner(),null,0,
                after.game().result().winner()==null?"平局":"对局结束",null));
        return List.copyOf(result);
    }
    /** The public journal never carries private prizes. Enrich only this receiver's unambiguous confirmed hand delta. */
    private static List<Cue> prizeFace(MatchSession.Snapshot before,MatchSession.Snapshot after,List<Cue> cues){
        UUID seat=after.game().viewingSeat();
        if(seat==null||after.revision()!=before.revision()+1)return cues;
        var prizes=cues.stream().filter(c->c.kind()==Kind.PRIZE&&seat.equals(c.seat())).toList();
        if(prizes.size()!=1||prizes.getFirst().amount()!=1||prizes.getFirst().cardId()!=null)return cues;
        if(cues.stream().anyMatch(c->seat.equals(c.seat())&&switch(c.kind()){
            case DRAW,PLAY,SHUFFLE,DISCARD,LOST,LEAVE,EVOLVE,DEVOLVE->true;default->false;
        }))return cues;
        var old=before.game().players().stream().filter(p->seat.equals(p.id())).findFirst().orElse(null);
        var next=after.game().players().stream().filter(p->seat.equals(p.id())).findFirst().orElse(null);
        if(old==null||next==null||old.prizeCount()-next.prizeCount()!=1||after.game().hand().size()!=before.game().hand().size()+1)return cues;
        var ids=before.game().hand().stream().map(CardInstance::instanceId).collect(java.util.stream.Collectors.toSet());
        if(!after.game().hand().stream().map(CardInstance::instanceId).collect(java.util.stream.Collectors.toSet()).containsAll(ids))return cues;
        var added=after.game().hand().stream().filter(c->!ids.contains(c.instanceId())).toList();
        if(added.size()!=1)return cues;
        var prize=prizes.getFirst();String face=added.getFirst().definitionId();
        return cues.stream().map(c->c==prize?new Cue(c.kind(),c.seat(),c.pokemon(),c.amount(),c.text(),face):c).toList();
    }
    private record Attachment(UUID pokemon,String cardId) { }
    private record ToolPile(UUID seat,boolean lost) { }
    private static ToolPile toolPile(MatchClientView view,UUID instance){
        for(var player:view.players()){
            if(player.discard().stream().anyMatch(c->c.instanceId().equals(instance)))return new ToolPile(player.id(),false);
            if(player.lostZone().stream().anyMatch(c->c.instanceId().equals(instance)))return new ToolPile(player.id(),true);
        }
        return null;
    }
    private record SwitchMarker(UUID outgoing,UUID incoming,String reason) { }
    private static SwitchMarker switchMarker(List<String> cues,UUID seat,UUID outgoing,UUID incoming) {
        if(outgoing==null||incoming==null)return null;
        String prefix="换位·"+seat+"·";
        for(String cue:cues)if(cue.startsWith(prefix)) {
            String[] parts=cue.split("·",5);
            if(parts.length!=5||!(parts[4].equals("撤退")||parts[4].equals("效果")))continue;
            try {
                UUID from=UUID.fromString(parts[2]),to=UUID.fromString(parts[3]);
                if(from.equals(outgoing)&&to.equals(incoming))return new SwitchMarker(from,to,parts[4]);
            }
            catch(IllegalArgumentException ignored) { }
        }
        return null;
    }
    private static String leaveDestination(List<String> cues,UUID seat,UUID pokemon) {
        String prefix="退场·"+seat+"·"+pokemon+"·";
        for(String cue:cues)if(cue.startsWith(prefix)) {
            String zone=cue.substring(prefix.length());
            if(Set.of("弃牌区","放逐区","手牌","牌库").contains(zone))return zone;
        }
        return null;
    }
    public static String conditionName(SpecialCondition condition) {
        return switch(condition) {
            case POISONED -> "中毒"; case BURNED -> "灼伤"; case ASLEEP -> "睡眠";
            case PARALYZED -> "麻痹"; case CONFUSED -> "混乱";
        };
    }
    private static LinkedHashMap<UUID,CardInstance> addedCards(List<CardInstance> before,List<CardInstance> after) {
        var old=new HashSet<UUID>();
        for(var card:before)old.add(card.instanceId());
        var added=new LinkedHashMap<UUID,CardInstance>();
        for(var card:after)if(!old.contains(card.instanceId()))added.put(card.instanceId(),card);
        return added;
    }
    private static boolean hasDefinition(Map<UUID,CardInstance> cards,String definitionId) {
        return cards.values().stream().anyMatch(card->card.definitionId().equals(definitionId));
    }
    private static CardInstance claimDefinition(Map<UUID,CardInstance> cards,String definitionId) {
        var iterator=cards.values().iterator();
        while(iterator.hasNext()) {
            var card=iterator.next();
            if(card.definitionId().equals(definitionId)) {iterator.remove();return card;}
        }
        return null;
    }
    private static String claimAttachment(Map<UUID,CardInstance> discard,Map<UUID,CardInstance> lost,UUID instance) {
        if(discard.remove(instance)!=null)return "弃牌区";
        if(lost.remove(instance)!=null)return "放逐区";
        return null;
    }
    private static Map<UUID,Attachment> energyAttachments(Map<UUID,PokemonView> field) {
        var result=new LinkedHashMap<UUID,Attachment>();
        for(var mon:field.values())for(int i=0;i<mon.attachedEnergyIds().size();i++) {
            String cardId=i<mon.attachedEnergyCardIds().size()?mon.attachedEnergyCardIds().get(i):null;
            result.put(mon.attachedEnergyIds().get(i),new Attachment(mon.pokemonId(),cardId));
        }
        return result;
    }
    private static Map<UUID,Attachment> toolAttachments(Map<UUID,PokemonView> field) {
        var result=new LinkedHashMap<UUID,Attachment>();
        for(var mon:field.values())for(var tool:mon.attachedTools())
            result.put(tool.instanceId(),new Attachment(mon.pokemonId(),tool.definitionId()));
        return result;
    }
    private static Map<String,Integer> handEnergyCounts(List<String> cues,UUID seat) {
        var result=new HashMap<String,Integer>();
        String prefix="手牌能量·"+seat+"·";
        for(String cue:cues)if(cue.startsWith(prefix)) {
            String[] parts=cue.split("·",4);
            if(parts.length==4&&!parts[3].isBlank())try {
                UUID target=UUID.fromString(parts[2]);
                result.merge(target+"·"+parts[3],1,Integer::sum);
            } catch(IllegalArgumentException ignored) { }
        }
        return result;
    }
    private static boolean consumeHandEnergy(Map<String,Integer> counts,UUID pokemon,String cardId) {
        String key=pokemon+"·"+cardId;
        int count=counts.getOrDefault(key,0);
        if(count<=0)return false;
        if(count==1)counts.remove(key);else counts.put(key,count-1);
        return true;
    }
    private static boolean evolved(PokemonView before,PokemonView after) {
        var oldStack=before.evolutionCardIds();var newStack=after.evolutionCardIds();
        return newStack.size()>oldStack.size()&&newStack.subList(0,oldStack.size()).equals(oldStack);
    }
    private static Map<UUID,PokemonView> field(MatchClientView.Player player) {
        var map=new LinkedHashMap<UUID,PokemonView>();
        if(player.active()!=null)map.put(player.active().pokemonId(),player.active());
        for(var mon:player.bench())map.put(mon.pokemonId(),mon);
        return map;
    }
    public static String blockedCard(MatchSession.Snapshot snapshot,String key) {
        var game=snapshot.game();
        if(game.result()!=null)return "本局已结束，只能查看卡牌。";
        if(game.viewingSeat()==null)return "观战模式只能查看公开卡牌。";
        if(game.phase()==MatchPhase.PRIZE_SELECTION)return "请先完成奖赏卡选择；高亮奖赏区由有选择权的一方点击。";
        if(game.phase()==MatchPhase.CHOOSE_REPLACEMENT)return "请先由被击倒的一方选择备战宝可梦替补。";
        if(game.phase()==MatchPhase.TURN&&!Objects.equals(game.currentPlayer(),game.viewingSeat()))return "现在是对手的回合，请等待对手行动。";
        if(game.resolvingCardEffect())return "请先完成当前效果的目标选择或确认。";
        if(key.startsWith("field:")&&snapshot.attackBlockZh()!=null&&!snapshot.attackBlockZh().isBlank())
            return snapshot.attackBlockZh();
        return "服务器未提供这张卡的可用操作；请查看当前阶段、目标与卡牌限制。";
    }
}
