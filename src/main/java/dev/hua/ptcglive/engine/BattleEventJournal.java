package dev.hua.ptcglive.engine;

import dev.hua.ptcglive.view.MatchPresentation;
import dev.hua.ptcglive.engine.field.BattlefieldState.CheckupEvent;
import java.util.*;
import java.util.function.Function;

/** In-memory, bounded PUBLIC checkpoints. Never stores private actions or random audit labels. */
public final class BattleEventJournal {
    public static final int LIMIT=160;
    public record Event(long sequence,int turn,MatchPresentation.Cue cue,String text) {}
    private final Deque<Event> events=new ArrayDeque<>();
    private MatchClientView previous;
    private long checkpoint,sequence;
    public List<Event> events(){return List.copyOf(events);}
    public void observe(MatchClientView current, UUID actor,String operation,List<String> coins,Function<String,String> names) {
        observe(current,actor,operation,coins,names,card->false);
    }
    /** The server attests the destination of previously PUBLIC attachments; no hidden hand is stored or enumerated. */
    public void observe(MatchClientView current, UUID actor,String operation,List<String> coins,Function<String,String> names,
                        java.util.function.Predicate<dev.hua.ptcglive.rules.CardInstance> nowInOwnerHand) {
        observe(current,actor,operation,coins,names,nowInOwnerHand,List.of());
    }
    void observe(MatchClientView current,UUID actor,String operation,List<String> coins,Function<String,String> names,
                 java.util.function.Predicate<dev.hua.ptcglive.rules.CardInstance> nowInOwnerHand,List<CheckupEvent> checkup) {
        var labels=new ArrayList<String>();
        if(previous!=null)for(var player:previous.players()) {
            var field=new ArrayList<>(player.bench());if(player.active()!=null)field.add(player.active());
            for(var mon:field)for(var tool:mon.attachedTools())if(nowInOwnerHand.test(tool))
                labels.add("工具回手·"+player.id()+"·"+mon.pokemonId()+"·"+tool.instanceId()+"·"+tool.ownerId());
        }
        if(operation.equals("公开洗牌"))labels.add("洗牌·"+actor);
        if(operation.startsWith("公开换位·"))labels.add(operation.substring(2));
        if(operation.startsWith("公开工具回手·"))labels.add(operation.substring(2));
        if(operation.startsWith("公开击倒·"))labels.add(operation.substring(2));
        if(operation.equals("击倒触发全部完成；同时移场并结算奖赏"))labels.add("击倒结算完成");
        if(operation.startsWith("服务器抽牌：")&&!operation.equals("服务器抽牌：prize")&&!operation.startsWith("服务器抽牌：reset-prizes:"))
            labels.add("抽牌·"+actor+"·1");
        if(operation.startsWith("结算触发特性：")&&current.resolvingAbility()==null)labels.add("特性·"+actor+"·");
        if(operation.startsWith("公开特性·"+actor+"·"))labels.add(operation.substring(2));
        var before=previous==null?null:new MatchSession.Snapshot(checkpoint,false,List.of(),previous,List.of(),Map.of(),List.of(),"");
        var after=new MatchSession.Snapshot(++checkpoint,false,List.of(),current,List.of(),Map.of(),labels,"");
        var cues=new ArrayList<MatchPresentation.Cue>();
        var changes=MatchPresentation.changes(before,after);
        if(!checkup.isEmpty())changes=new ArrayList<>(changes);
        for(var step:checkup){
            String label=switch(step.condition()){
                case POISONED -> "中毒";case BURNED -> "灼伤";default -> null;
            };
            if(label==null||step.damageAdded()<=0)continue;
            var active=current.players().stream().filter(p->p.id().equals(step.player())).findFirst().orElseThrow().active();
            if(active==null)continue;
            changes.removeIf(c->c.kind()==MatchPresentation.Kind.DAMAGE&&active.pokemonId().equals(c.pokemon()));
            cues.add(new MatchPresentation.Cue(MatchPresentation.Kind.DAMAGE,step.player(),active.pokemonId(),
                step.damageAdded(),label+"伤害",null));
        }
        for(String coin:coins)cues.add(new MatchPresentation.Cue(MatchPresentation.Kind.COIN,null,null,0,coin,null));
        cues.addAll(changes);
        if(operation.startsWith("训练家已公开打出·")) {
            String cardId=operation.substring("训练家已公开打出·".length());
            if(!cardId.isBlank())cues.add(new MatchPresentation.Cue(MatchPresentation.Kind.PLAY,actor,null,0,"公开使用训练家",cardId));
        }
        if(operation.startsWith("公开招式伤害·")) {
            String[] parts=operation.split("·",-1);
            if(parts.length==5)for(int i=0;i<cues.size();i++) {
                var cue=cues.get(i);
                if(cue.kind()==MatchPresentation.Kind.DAMAGE&&Objects.equals(cue.pokemon(),UUID.fromString(parts[2])))
                    cues.set(i,new MatchPresentation.Cue(cue.kind(),cue.seat(),cue.pokemon(),cue.amount(),"招式伤害·"+parts[4],cue.cardId()));
            }
        }
        if(operation.equals("混乱判定失败，攻击终止")||operation.equals("复制招式掷硬币：反面"))
            cues.add(new MatchPresentation.Cue(MatchPresentation.Kind.ATTACK,actor,null,0,operation+"；攻击失败",null));
        if(operation.startsWith("公开零伤害："))
            cues.add(new MatchPresentation.Cue(MatchPresentation.Kind.DAMAGE,actor,null,0,operation.substring(6),null));
        if(previous!=null)for(var card:current.resolvingTrainerCards())if(previous.resolvingTrainerCards().stream().noneMatch(c->c.instanceId().equals(card.instanceId())))
            cues.add(new MatchPresentation.Cue(MatchPresentation.Kind.PLAY,actor,null,0,"公开使用训练家",card.definitionId()));
        var ability=current.resolvingAbility();var priorAbility=previous==null?null:previous.resolvingAbility();
        boolean newAbility=ability!=null&&(priorAbility==null||!ability.abilityId().equals(priorAbility.abilityId())
            ||!ability.nameZh().equals(priorAbility.nameZh())||ability.sourceKind()!=priorAbility.sourceKind()
            ||ability.pokemonId()!=null&&!Objects.equals(ability.pokemonId(),priorAbility.pokemonId()));
        // The same trigger can fire twice without an intervening public null checkpoint.
        // Conversely, hiding a departing source UUID is not a second activation.
        if(ability!=null&&ability.sourceKind()==dev.hua.ptcglive.engine.effect.EffectOrigin.Kind.ABILITY
            &&(newAbility||operation.startsWith("结算触发特性：")))
            cues.add(new MatchPresentation.Cue(MatchPresentation.Kind.ABILITY,actor,current.resolvingAbility().pokemonId(),0,current.resolvingAbility().nameZh(),null));
        // A declaration is public even when its resolution suspends for a later choice.
        if(operation.startsWith("公开招式名称："))cues.add(new MatchPresentation.Cue(MatchPresentation.Kind.ATTACK,actor,
            current.players().stream().filter(p->p.id().equals(actor)).map(MatchClientView.Player::active).filter(Objects::nonNull)
                .map(p->p.pokemonId()).findFirst().orElse(null),0,operation.substring(7),null));
        for(var cue:cues) {
            String text=cue.text();
            if(text.startsWith("能量转移·"))text="转移能量"; // Internal public field anchors are not human log text.
            if(text.startsWith("工具回手·"))text="工具回到手牌";
            if(text.startsWith("工具弃置·"))text="工具弃置";
            if(text.startsWith("工具放逐·"))text="工具放逐";
            if(text.startsWith("招式伤害·"))text="招式伤害 "+cue.amount();
            if(text.equals("中毒伤害")||text.equals("灼伤伤害"))text+=" "+cue.amount();
            if(cue.cardId()!=null)text+=" · "+names.apply(cue.cardId());
            if(cue.kind()==MatchPresentation.Kind.PRIZE)text="领取奖赏 "+cue.amount()+" 张";
            if(cue.kind()==MatchPresentation.Kind.RESULT&&current.result()!=null)text+=" · "+switch(current.result().reason()) {
                case PRIZES -> "奖赏卡拿完";case NO_POKEMON -> "场上没有宝可梦";case DECK_OUT -> "牌库无牌可抽";
                case CARD_EFFECT -> "卡牌效果直接获胜";case CONCESSION -> "认输";case TIME -> "超时";
                case DRAW -> "双方同时满足胜利条件";case SUDDEN_DEATH -> "突然死亡";
            };
            events.addLast(new Event(++sequence,current.turnNumber(),cue,text));
            while(events.size()>LIMIT)events.removeFirst();
        }
        previous=current;
    }
}
