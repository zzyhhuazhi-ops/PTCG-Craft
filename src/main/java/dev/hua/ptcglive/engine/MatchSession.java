package dev.hua.ptcglive.engine;

import dev.hua.ptcglive.engine.field.BattlefieldState.PokemonView;
import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.rules.TrainerCategory;
import dev.hua.ptcglive.rules.PairedTrainerPlay;
import java.util.*;

/** Authenticates the actual server connection, filters information and rejects replayed/stale intents. */
public final class MatchSession {
    /** Presentation anchor only; authority remains the issued option and revision. */
    public static final String POKEMON_SELECTION_KEY = "pokemon:choose";
    /**
     * cardKeys are presentation-only anchors for cards which are already visible
     * to this recipient. They never carry a command or grant authority: submit()
     * still resolves the server-owned option index against the current revision.
     */
    public record Option(int index, String labelZh, List<String> cardKeys, boolean enabled,ActionType type,String commandId) {
        public Option {
            cardKeys = List.copyOf(cardKeys == null ? List.of() : cardKeys);
            type=type==null?ActionType.legacy(labelZh):type;commandId=commandId==null?"":commandId;
        }
        public Option(int index,String labelZh,List<String> cardKeys,boolean enabled){this(index,labelZh,cardKeys,enabled,ActionType.legacy(labelZh),"");}
        public Option(int index, String labelZh, List<String> cardKeys) { this(index,labelZh,cardKeys,true); }
        public Option(int index, String labelZh) { this(index, labelZh, List.of(),true); }
    }
    public record Snapshot(long revision, boolean practice, List<UUID> controllableSeats,
                           MatchClientView game, List<Option> options, Map<String, String> cardNames,
                           List<String> publicCues, String attackBlockZh, BattleGuidance guidance, List<BattleEventJournal.Event> events,
                           ComputerOpponent computer) {
        public Snapshot {controllableSeats=List.copyOf(controllableSeats);options=List.copyOf(options);cardNames=Map.copyOf(cardNames);publicCues=List.copyOf(publicCues);events=events==null?null:List.copyOf(events);}
        public Snapshot(long revision,boolean practice,List<UUID> seats,MatchClientView game,List<Option> options,
                        Map<String,String> names,List<String> cues,String block,BattleGuidance guidance,List<BattleEventJournal.Event> events) {
            this(revision,practice,seats,game,options,names,cues,block,guidance,events,null);
        }
        public Snapshot(long revision,boolean practice,List<UUID> seats,MatchClientView game,List<Option> options,
                        Map<String,String> names,List<String> cues,String block) {
            this(revision,practice,seats,game,options,names,cues,block,BattleGuidance.EMPTY,null);
        }
        public Snapshot(long revision, boolean practice, List<UUID> controllableSeats, MatchClientView game,
                        List<Option> options, Map<String,String> cardNames) {
            this(revision,practice,controllableSeats,game,options,cardNames,List.of(),"");
        }
    }
    public record Audit(long revision, UUID connectionPlayer, UUID seat, String operation) { }
    public record ComputerOpponent(UUID seat,String sourceBoxId,String name,String status,String dimension,long tablePosition){}
    private ComputerOpponent computer;
    public synchronized void computerOpponent(ComputerOpponent value){computer=value;}
    private record Action(String label, Runnable apply, List<String> cardKeys,ActionType type,String commandId,boolean enabled) {
        private Action {
            cardKeys = List.copyOf(cardKeys == null ? List.of() : cardKeys);
        }
        private Action(String label, Runnable apply) { this(label, apply, List.of()); }
        private Action(String label,Runnable apply,List<String> keys,ActionType type,String commandId){this(label,apply,keys,type,commandId,apply!=null);}
        private Action(String label,Runnable apply,List<String> keys){this(label,apply,keys,ActionType.OTHER,"");}
        private Action(ActionType type,String label,Runnable apply){this(label,apply,List.of(),type,"");}
        private Action(ActionType type,String label,Runnable apply,List<String> keys){this(label,apply,keys,type,"");}
    }
    @FunctionalInterface
    private interface AttackSubmit {
        void submit(UUID selectedEffectTarget, List<UUID> damageTargets, List<UUID> discardedEnergy);
    }
    private final ServerMatch match;
    private final boolean practice;
    private final Map<UUID, List<UUID>> controllers;
    private final Set<UUID> spectators = new LinkedHashSet<>();
    private final List<Audit> audit = new ArrayList<>();
    private long revision;
    private record RetreatPayment(UUID id, UUID player, UUID active, UUID replacement, List<UUID> selected) {
        RetreatPayment { selected=List.copyOf(selected); }
    }
    private RetreatPayment retreatPayment;
    private String readWaitKey="";
    private long readWaitSince;
    /** Server ticks: only read acknowledgements expire; no costs, targets or mandatory choices are selected. */
    public synchronized boolean tickReadAcknowledgements(long tick) {
        var reveal=match.effectCardReveal();
        String key=reveal!=null?"reveal:"+reveal.id():match.phase()==MatchPhase.MULLIGAN_REVEAL
            ? "mulligan:"+match.clientView(null).mulliganReveals():"";
        if(!key.equals(readWaitKey)){readWaitKey=key;readWaitSince=tick;}
        if(key.isEmpty()||tick-readWaitSince<600)return false;
        long before=revision;
        for(var entry:controllers.entrySet())for(UUID seat:entry.getValue()) {
            var state=view(entry.getKey(),seat);
            String label=reveal!=null?"确认已查看展示卡牌":"确认无基础宝可梦的重抽展示";
            var option=state.options().stream().filter(o->o.enabled()&&o.labelZh().equals(label)).findFirst();
            if(option.isPresent()){submit(entry.getKey(),seat,state.revision(),option.get().index());if(reveal!=null){readWaitKey="";return true;}}
        }
        readWaitKey="";
        return revision!=before;
    }
    /** Last successful revision only. Fixed public labels, never action text or hidden card IDs. */
    private List<String> publicCues = List.of();
    /** Public field source survives a server choice that pauses attack resolution. */
    private UUID announcedAttackActor, announcedAttackSource;
    private String announcedAttackName;

    private MatchSession(ServerMatch match, boolean practice, Map<UUID, List<UUID>> controllers) {
        this.match = Objects.requireNonNull(match); this.practice = practice; this.controllers = Map.copyOf(controllers);
        match.enablePublicEvents();
    }
    public static MatchSession multiplayer(ServerMatch match, UUID first, UUID second) {
        return new MatchSession(match, false, Map.of(first, List.of(first), second, List.of(second)));
    }
    public static MatchSession practice(ServerMatch match, UUID controller, UUID first, UUID second) {
        return new MatchSession(match, true, Map.of(controller, List.of(first, second)));
    }
    public ServerMatch match() { return match; }
    public synchronized void addSpectator(UUID player) {
        if (practice && !controllers.containsKey(player)) throw new IllegalStateException("练习对局不开放他人私有视角");
        if (!controllers.containsKey(player)) spectators.add(player);
    }
    public synchronized void removeSpectator(UUID player) { spectators.remove(player); }
    public synchronized boolean controls(UUID player) { return controllers.containsKey(player); }
    public synchronized List<UUID> seats(UUID player) { return controllers.getOrDefault(player, List.of()); }
    public synchronized List<Audit> audit() { return List.copyOf(audit); }
    public synchronized long publicRevision(){return revision;}
    /** Read-only screen boundary; deliberately does not register a controller or generate actions. */
    public synchronized dev.hua.ptcglive.spectator.ScreenMatch screenView(){
        return dev.hua.ptcglive.spectator.ScreenMatch.capture(revision,match.clientView(null),match.publicEvents());
    }
    public synchronized Snapshot view(UUID connectionPlayer, UUID seat) {
        authorize(connectionPlayer, seat, false);
        List<Action> actions = seat == null ? List.of() : actions(seat);
        List<Option> options = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            var action=actions.get(i);
            options.add(new Option(i,action.label,action.cardKeys,action.apply!=null&&action.enabled(),action.type(),action.commandId()));
        }
        MatchClientView game = retreatPaymentView(match.clientView(seat),seat);
        Map<String, String> names = new LinkedHashMap<>();
        for(var fieldStadium:game.stadiumCards()) names.put(fieldStadium.definitionId(), name(fieldStadium.definitionId()));
        for(var played:game.resolvingTrainerCards())names.put(played.definitionId(),name(played.definitionId()));
        if (game.effectSelection() != null) for (var card : game.effectSelection().visibleCards()) names.put(card.definitionId(), name(card.definitionId()));
        if (game.effectAttachment() != null) for (var card : game.effectAttachment().cards()) names.put(card.definitionId(), name(card.definitionId()));
        if (game.effectReveal() != null) for (String cardId : game.effectReveal().cardIds()) names.put(cardId, name(cardId));
        for (var card : game.hand()) names.put(card.definitionId(), name(card.definitionId()));
        for (var reveal : game.mulliganReveals()) for (String card : reveal.cardIds()) names.put(card, name(card));
        for (var player : game.players()) {
            if (player.active() != null) addFieldNames(names, player.active());
            for (var card : player.bench()) addFieldNames(names, card);
            for (var card : player.discard()) names.put(card.definitionId(), name(card.definitionId()));
            for (var card : player.lostZone()) names.put(card.definitionId(), name(card.definitionId()));
        }
        return new Snapshot(revision, practice, seats(connectionPlayer), game, List.copyOf(options), Map.copyOf(names),
            publicCues, attackBlockReason(game,seat),match.battleGuidance(seat,options),match.publicEvents(),computer);
    }
    private String attackBlockReason(MatchClientView game, UUID seat) {
        if(seat==null)return "观战模式不能使用招式。";
        if(game.result()!=null)return "本局已结束。";
        if(game.phase()!=MatchPhase.TURN)return "请先完成当前阶段，再使用招式。";
        if(!seat.equals(game.currentPlayer()))return "现在是对手的回合。";
        if(game.resolvingCardEffect()||game.triggerOrder()!=null||game.effectSelection()!=null)
            return "请先完成当前效果的选择与结算。";
        if(match.turnNumber()==1&&!match.ruleset().firstTurnRules().firstPlayerMayAttack()&&!match.mayAttack(seat))
            return "当前规则包不允许先手在首回合攻击。";
        var own=game.players().stream().filter(p->p.id().equals(seat)).findFirst().orElseThrow();
        if(own.active()==null)return "请先放置战斗宝可梦。";
        if(own.active().conditions().contains(dev.hua.ptcglive.engine.field.SpecialCondition.ASLEEP)&&!match.canAttackWhileAsleepOrParalyzed(seat,own.active().pokemonId()))
            return "战斗宝可梦处于睡眠状态，不能攻击。";
        if(own.active().conditions().contains(dev.hua.ptcglive.engine.field.SpecialCondition.PARALYZED)&&!match.canAttackWhileAsleepOrParalyzed(seat,own.active().pokemonId()))
            return "战斗宝可梦处于麻痹状态，不能攻击。";
        if(!match.mayAttack(seat))return "当前有攻击限制或尚未完成的结算，请查看宝可梦状态与当前提示。";
        return "";
    }
    /** Reuse the existing card-choice gallery without starting or mutating a card effect. */
    private MatchClientView retreatPaymentView(MatchClientView game, UUID seat) {
        var payment=retreatPayment;
        if(payment==null || !payment.player().equals(seat) || game.phase()!=MatchPhase.TURN) return game;
        var cards=match.retreatEnergyCards(seat);
        int units=payment.selected().stream().mapToInt(id->match.retreatEnergyUnits(seat,id)).sum();
        int cost=match.activeRetreatCost(seat);
        var prompt=new EffectCardPrompt(payment.id(),1,"撤退：选择要弃置的能量；费用 "+cost+"，已选 "+payment.selected().size()
            +" 张／供能 "+units+"。确认前不弃牌。",1,Math.min(cost,cards.size()),cards,payment.selected());
        return new MatchClientView(game.matchId(),game.viewingSeat(),game.phase(),game.turnNumber(),game.currentPlayer(),seat,
            game.result(),game.players(),game.hand(),game.prizeSlots(),game.setupPlayers(),game.mulliganReveals(),game.stadium(),
            game.resolvingTrainer(),prompt,game.effectReveal(),game.effectAttachment(),game.triggerOrder(),game.damageAllocation(),
            game.resolvingAbility(),game.resolvingAttack(),game.toolOverflow(),game.resolvingTrainerCards(),game.stadiumCompanion());
    }
    private void addFieldNames(Map<String, String> names, PokemonView field) {
        // Only reached through the already-filtered recipient view: never enumerate the server deck or prizes.
        names.put(field.topCardId(), name(field.topCardId()));
        for (String id : field.evolutionCardIds()) names.put(id, name(id));
        for (String id : field.attachedEnergyCardIds()) names.put(id, name(id));
        for (var card : field.attachedTools()) names.put(card.definitionId(), name(card.definitionId()));
    }
    public synchronized void submit(UUID connectionPlayer, UUID seat, long expectedRevision, int option) {
        authorize(connectionPlayer, seat, true);
        if (expectedRevision != revision) throw new IllegalStateException("对局状态已更新，请按最新界面重新操作");
        List<Action> actions = actions(seat);
        if (option < 0 || option >= actions.size()) throw new IllegalArgumentException("无效的对局操作");
        Action action = actions.get(option);
        if (action.apply == null) throw new IllegalStateException(action.label);
        MatchClientView beforeGame=action.cardKeys().stream().anyMatch(key->key.startsWith("hand:"))
            ?match.clientView(seat):null;
        List<CardInstance> handCandidates=beforeGame==null?List.of():beforeGame.hand().stream()
            .filter(card->action.cardKeys().contains(handKey(card.instanceId()))).toList();
        int previousEvents=match.replayTranscript().size();
        int previousRandom=match.randomTranscript().size();
        action.apply.run();
        match.checkpointPublicEvents();
        var cues=new ArrayList<String>();
        // Translate one established public checkpoint; never forward the transcript itself.
        var events=match.replayTranscript();
        var newEvents=events.subList(Math.min(previousEvents,events.size()),events.size());
        UUID sourcePokemon=action.cardKeys().stream().filter(key->key.startsWith("field:"))
            .map(key->UUID.fromString(key.substring(6))).findFirst().orElse(null);
        if(beforeGame!=null&&newEvents.stream().anyMatch(event->event.actor().equals(seat)
            &&event.operation().equals("从手牌放置基础宝可梦到备战区"))) {
            var oldOwn=beforeGame.players().stream().filter(player->player.id().equals(seat)).findFirst().orElseThrow();
            var oldIds=new HashSet<UUID>();
            if(oldOwn.active()!=null)oldIds.add(oldOwn.active().pokemonId());
            oldOwn.bench().forEach(mon->oldIds.add(mon.pokemonId()));
            var nowOwn=match.clientView(seat).players().stream().filter(player->player.id().equals(seat)).findFirst().orElseThrow();
            var playedIds=handCandidates.stream().filter(card->match.cardDefinition(card.definitionId()).basicPokemon())
                .map(CardInstance::definitionId).collect(java.util.stream.Collectors.toSet());
            var newcomers=new ArrayList<PokemonView>();
            if(nowOwn.active()!=null)newcomers.add(nowOwn.active());
            newcomers.addAll(nowOwn.bench());
            newcomers.removeIf(mon->oldIds.contains(mon.pokemonId())||!playedIds.contains(mon.topCardId()));
            // If triggers placed another identical card, the origin is ambiguous; use a neutral reveal.
            if(newcomers.size()==1)cues.add("手牌宝可梦·"+seat+"·"+newcomers.getFirst().pokemonId());
        }
        if(action.label().startsWith("附着 ")&&sourcePokemon!=null)
            handCandidates.stream().filter(card->match.cardDefinition(card.definitionId()).category()==CardCategory.ENERGY)
                .forEach(card->cues.add("手牌能量·"+seat+"·"+sourcePokemon+"·"+card.definitionId()));
        var declaration=newEvents.stream().filter(event->event.operation().startsWith("宣告攻击："))
            .findFirst().orElse(null);
        if(declaration!=null) {
            announcedAttackActor=declaration.actor();
            announcedAttackSource=sourcePokemon;
            announcedAttackName=newEvents.stream().map(MatchReplayEvent::operation)
                .filter(operation->operation.startsWith("公开招式名称："))
                .map(operation->operation.substring("公开招式名称：".length())).findFirst().orElse(null);
        }
        MatchReplayEvent attackResult=null;
        String outcome=null;
        for(var event:newEvents) {
            String operation=event.operation();
            if(operation.equals("混乱判定失败，攻击终止")) {
                attackResult=event;outcome="混乱判定反面：攻击失败，自身放置3个伤害指示物（30点）";break;
            }
            if(operation.startsWith("攻击前第")&&operation.endsWith("：反面")) {
                attackResult=event;outcome="攻击前判定反面：攻击失败";
            }
            if(operation.equals("复制招式掷硬币：反面")) {
                attackResult=event;outcome="复制招式判定反面：攻击失败";break;
            }
            if(operation.equals("使用攻击")) {
                attackResult=event;outcome="攻击结算完成";break;
            }
        }
        if(attackResult!=null) {
            UUID actor=attackResult.actor();
            UUID attackSource=actor.equals(announcedAttackActor)?announcedAttackSource
                :actor.equals(seat)?sourcePokemon:null;
            cues.add("招式·"+actor+"·"+(attackSource==null?"":attackSource)+"·"+outcome);
            if(announcedAttackName!=null&&actor.equals(announcedAttackActor)) {
                var resolution=match.lastAttack();
                int damage=outcome.equals("攻击结算完成")&&resolution!=null&&actor.equals(resolution.attacker())
                    ?Math.max(0,resolution.finalDamage()):0;
                cues.add("招式演出·"+actor+"·"+damage+"·"+announcedAttackName);
            }
            announcedAttackActor=null;announcedAttackSource=null;announcedAttackName=null;
        }
        if(newEvents.stream()
            .anyMatch(event->event.operation().equals("击倒触发全部完成；同时移场并结算奖赏")))
            cues.add("击倒结算完成");
        for(var event:newEvents) {
            String operation=event.operation();
            if(operation.equals("公开洗牌")) {
                cues.add("洗牌·"+event.actor());
            } else if(operation.startsWith("公开工具回手·")) {
                cues.add(operation.substring(2));
            } else if(operation.startsWith("公开击倒·")) {
                String[] parts=operation.split("·",3);
                if(parts.length==3&&parts[1].equals(event.actor().toString()))
                    cues.add("击倒·"+parts[1]+"·"+parts[2]);
            } else if(operation.startsWith("公开招式伤害·")) {
                String[] parts=operation.split("·",-1);
                if(parts.length==5&&parts[1].equals(event.actor().toString()))
                    cues.add(operation.substring("公开".length()));
            } else if(operation.startsWith("公开换位·")) {
                String[] parts=operation.split("·",5);
                if(parts.length==5&&parts[1].equals(event.actor().toString()))
                    cues.add("换位·"+parts[1]+"·"+parts[2]+"·"+parts[3]+"·"+parts[4]);
            }
        }
        boolean directAbility=action.label().startsWith("使用特性") || action.label().startsWith("使用弃牌区特性");
        if(directAbility)cues.add("特性·"+seat+"·"+(sourcePokemon==null?"":sourcePokemon));
        for(var event:newEvents)if(event.operation().startsWith("结算触发特性："))
            cues.add("特性·"+event.actor()+"·");
        boolean trainerPlayed=newEvents.stream().anyMatch(event->event.actor().equals(seat)
            &&(event.operation().equals("训练家已公开打出")
                ||event.operation().startsWith("使用已审核训练家卡")
                ||event.operation().startsWith("同时使用两张训练家")
                ||event.operation().startsWith("同时使用四张训练家")
                ||event.operation().startsWith("同时使用四张四尖石")));
        if(trainerPlayed)handCandidates.stream()
            .filter(card->match.cardDefinition(card.definitionId()).category()==CardCategory.TRAINER)
            .map(CardInstance::definitionId).distinct().forEach(cardId->cues.add(
                (match.cardDefinition(cardId).trainerCategory()==TrainerCategory.STADIUM?"场地出牌·":"出牌·")
                    +seat+"·"+cardId));
        // Only a server-recorded deck draw can drive a draw animation. Searches,
        // prizes, and cards moved from discard may also increase hand size.
        var draws=new LinkedHashMap<UUID,Integer>();
        for(var event:newEvents) {
            String operation=event.operation();
            if(!operation.startsWith("服务器抽牌："))continue;
            String reason=operation.substring("服务器抽牌：".length());
            if(reason.equals("prize")||reason.startsWith("reset-prizes:"))continue;
            if(reason.equals("turn-draw")&&match.hasPendingTurnDraw())continue;
            draws.merge(event.actor(),1,Integer::sum);
        }
        if(newEvents.stream().anyMatch(event->(event.operation().equals("放弃紧急入场，正常将回合开始抽到的卡加入手牌")||event.operation().equals("将回合开始抽到的卡加入手牌"))))
            draws.merge(seat,1,Integer::sum);
        draws.forEach((actor,count)->cues.add("抽牌·"+actor+"·"+count));
        var randomEvents=match.randomTranscript();
        cues.addAll(PublicCoinResults.describe(randomEvents.subList(Math.min(previousRandom,randomEvents.size()),randomEvents.size())));
        publicCues=List.copyOf(cues);
        audit.add(new Audit(++revision, connectionPlayer, seat, action.label));
    }
    private void addBorrowedAttackChoices(List<Action> result,UUID player,MatchClientView view,PokemonView source,
        ServerMatch.BorrowedAttackOption offered,dev.hua.ptcglive.engine.field.AttackDefinition body,String label,ServerMatch.CopyAttackOption copied) {
        var locks=ServerMatch.locksOpponentAttack(body)?match.opponentAttackLockOptions(player):List.<dev.hua.ptcglive.engine.field.AttackDefinition>of();
        List<String> lockIds=locks.isEmpty()?java.util.Collections.singletonList(null):locks.stream().map(dev.hua.ptcglive.engine.field.AttackDefinition::id).toList();
        for(var lockId:lockIds) {
            String shown=lockId==null?label:label+" → 锁定 "+locks.stream().filter(a->a.id().equals(lockId)).findFirst().orElseThrow().nameZh();
            int from=result.size();
            addAttackChoices(result,player,view,source,offered.attack(),body,shown,copied==null?null:copied.pokemonId(),
                (effectTarget,targets,discarded)->match.attackBorrowed(player,offered.holderId(),offered.holderEntry(),offered.holderTop(),offered.abilityId(),
                    offered.providerId(),offered.providerEntry(),offered.providerTop(),offered.attackId(),effectTarget,targets,discarded,
                    copied==null?null:copied.pokemonId(),copied==null?-1:copied.entrySequence(),copied==null?null:copied.attackId(),lockId));
            // A provider is context, never the card-face action's actual source or a cost-paying host.
            for(int i=from;i<result.size();i++) {var action=result.get(i);var keys=new ArrayList<>(action.cardKeys());
                String provider=fieldKey(offered.providerId());if(!keys.contains(provider))keys.add(provider);
                result.set(i,new Action(action.label(),action.apply(),keys,action.type(),action.commandId(),action.enabled()));
            }
            markPublicAttack(result,from,copied==null?offered.providerCardId():copied.topCardId(),copied==null?offered.attackId():copied.attackId());
        }
    }
    /** Definition IDs describe already public attacks; they never grant submission authority. */
    private static void markPublicAttack(List<Action> actions,int from,String card,String attack) {
        for(int i=from;i<actions.size();i++) {
            var a=actions.get(i);var keys=new ArrayList<>(a.cardKeys());
            keys.add("copy-source:"+card);keys.add("copy-attack:"+attack);
            actions.set(i,new Action(a.label(),a.apply(),keys,a.type(),a.commandId(),a.enabled()));
        }
    }
    private void addAttackChoices(List<Action> result,UUID player,MatchClientView view,PokemonView active,
                                  dev.hua.ptcglive.engine.field.AttackDefinition costAttack,
                                  dev.hua.ptcglive.engine.field.AttackDefinition attack,String label,UUID copiedSource,
                                  AttackSubmit submit) {
        // Keep an unavailable declaration in the server-issued intent list so
        // every client request still reaches the authoritative cost check.  The
        // client matcher deliberately hides this labelled entry, so it is never
        // presented as an attack button to the player.
        boolean payable = match.canPayAttackCost(player,active.pokemonId(),costAttack);
        String offeredLabel = payable ? label : label + "（能量不足）";
        int discardCount=attack.discardSelectedAttachedEnergyAfterAttack();
        var payments=discardCount==0?List.of(List.<UUID>of()):energyDiscardCombinations(match.attackDiscardEnergyUnits(player,active.pokemonId()),discardCount);
        var opponentField=view.players().stream().filter(p->!p.id().equals(player)).findFirst().orElseThrow();
        List<List<PokemonView>> targets;
        if(attack.damageTarget()==dev.hua.ptcglive.engine.field.AttackDefinition.DamageTarget.OPPONENT_ANY) {
            // The opponent's Active and Benched Pokémon are all choosable; with fewer in play, choose them all.
            var pool=new ArrayList<PokemonView>();
            if(opponentField.active()!=null)pool.add(opponentField.active());
            pool.addAll(opponentField.bench());
            pool.removeIf(p->!match.matchesAttackDamageTargetFilter(opponentField.id(),p.pokemonId(),attack));
            targets=dev.hua.ptcglive.engine.effect.RepeatTargetDamage.handles(attack)
                ?targetCombinationsWithRepetition(pool,attack.targetCount())
                :targetCombinations(pool,Math.min(attack.targetCount(),pool.size()));
        } else if(attack.damageTarget().requiresTargetChoice()) {
            var eligible=opponentField.bench().stream().filter(p->match.matchesAttackDamageTargetFilter(opponentField.id(),p.pokemonId(),attack)).toList();
            int available=Math.min(attack.targetCount(),eligible.size());
            targets=available==0?List.of(List.of()):targetCombinations(eligible,available);
        } else targets=List.of(List.of());
        var selection=dev.hua.ptcglive.engine.effect.AttackProgramRules.beforeDamageSelectionTarget(attack);
        List<PokemonView> effectTargets;
        if(selection==null) effectTargets=java.util.Collections.singletonList(null);
        else {
            UUID owner=selection==dev.hua.ptcglive.engine.effect.EffectTarget.SELECT_OWN_POKEMON?player:
                view.players().stream().filter(p->!p.id().equals(player)).findFirst().orElseThrow().id();
            var selectedPlayer=view.players().stream().filter(p->p.id().equals(owner)).findFirst().orElseThrow();
            effectTargets=new ArrayList<>(selectedPlayer.bench());
            if(selectedPlayer.active()!=null)effectTargets.addFirst(selectedPlayer.active());
        }
        for(var effectTarget:effectTargets)for(var selected:targets)for(var discarded:payments) {
            var ids=selected.stream().map(PokemonView::pokemonId).toList();
            var keys=new ArrayList<String>();keys.add(fieldKey(active.pokemonId()));
            if(copiedSource!=null)keys.add(fieldKey(copiedSource));
            if(effectTarget!=null)keys.add(fieldKey(effectTarget.pokemonId()));
            ids.forEach(id->keys.add(fieldKey(id)));
            String targetLabel=selected.isEmpty()?offeredLabel
                :attack.damageTarget()==dev.hua.ptcglive.engine.field.AttackDefinition.DamageTarget.OPPONENT_ANY
                ?offeredLabel+" → 对方 "+selected.stream().map(p->(opponentField.active()!=null&&p.pokemonId().equals(opponentField.active().pokemonId())
                    ?"战斗位 ":"备战区 ")+name(p.topCardId())).collect(java.util.stream.Collectors.joining("、"))
                :offeredLabel+" → 对方备战区 "+selected.stream()
                .map(p->name(p.topCardId())).collect(java.util.stream.Collectors.joining("、"));
            if(effectTarget!=null)targetLabel+=" → 伤害前选择"+(effectTarget.pokemonId().equals(active.pokemonId())?"己方战斗位":"目标")+" "+name(effectTarget.topCardId());
            result.add(new Action(targetLabel+(discarded.isEmpty()?"":"（弃置 "+discarded.size()+" 张附着能量）"),
                ()->submit.submit(effectTarget==null?null:effectTarget.pokemonId(),ids,discarded),keys,ActionType.ATTACK,attack.id(),payable));
        }
    }
    private void authorize(UUID connectionPlayer, UUID seat, boolean acting) {
        if (seat != null) {
            if (!seats(connectionPlayer).contains(seat)) throw new IllegalArgumentException("你无权操作或查看这个对局席位");
        } else if (acting || (!controllers.containsKey(connectionPlayer) && !spectators.contains(connectionPlayer))) {
            throw new IllegalArgumentException("你没有加入这场对局或观战");
        }
    }
    private List<Action> actions(UUID player) {
        var result = new ArrayList<Action>();
        if(retreatPayment!=null && (match.phase()!=MatchPhase.TURN || !retreatPayment.player().equals(match.currentPlayer())
            || match.activePokemonView(retreatPayment.player())==null
            || !retreatPayment.active().equals(match.activePokemonView(retreatPayment.player()).pokemonId()))) retreatPayment=null;
        if(retreatPayment!=null && !match.retreatEnergyCards(retreatPayment.player()).stream().map(CardInstance::instanceId)
            .toList().containsAll(retreatPayment.selected())) retreatPayment=null;
        if(retreatPayment!=null) {
            var payment=retreatPayment;
            if(player.equals(payment.player())) {
                String problem=match.retreatProblem(player,payment.replacement(),payment.selected());
                result.add(new Action(ActionType.RETREAT_CONFIRM,"确认撤退（弃置所选 "+payment.selected().size()+" 张能量）",
                    problem.isEmpty()?()->{match.retreat(player,payment.replacement(),payment.selected());retreatPayment=null;}:null));
                result.add(new Action(ActionType.RETREAT_CANCEL,"取消撤退",()->retreatPayment=null));
                int index=0;
                for(var card:match.retreatEnergyCards(player)) {
                    boolean selected=payment.selected().contains(card.instanceId());
                    int units=match.retreatEnergyUnits(player,card.instanceId());
                    String label=(selected?"取消选牌：":"选择卡牌：")+name(card.definitionId())+" #"+(++index)+"（供能 "+units+"）";
                    result.add(action(selected?ActionType.CARD_DESELECT:ActionType.CARD_SELECT,label,units>0&&(selected||payment.selected().size()<match.activeRetreatCost(player))?()->{
                        var ids=new ArrayList<>(payment.selected());
                        if(!ids.remove(card.instanceId())) ids.add(card.instanceId());
                        retreatPayment=new RetreatPayment(payment.id(),player,payment.active(),payment.replacement(),ids);
                    }:null,"effect:"+card.instanceId()));
                }
            }
            result.add(new Action(ActionType.CONCEDE,"认输并结束本局",()->{match.concede(player);retreatPayment=null;}));
            return result;
        }
        var overflow=match.toolOverflowPrompt();
        if(overflow!=null) {
            if(overflow.owner().equals(player))for(var tool:overflow.tools())
                result.add(new Action(ActionType.OVERFLOW,(overflow.bench()?"弃置超额备战宝可梦：":"弃置超量工具：")+name(tool.definitionId()),
                    ()->match.discardOverflowTool(player,overflow.id(),tool.instanceId()),List.of("tool-overflow:"+tool.instanceId())));
            result.add(new Action(ActionType.CONCEDE,"认输并结束本局",()->match.concede(player)));
            return result;
        }
        var trigger = match.triggerOrderPrompt();
        if (trigger != null) {
            if (trigger.choosingPlayer().equals(player)) {
                if (trigger.optionalDecision()) {
                    result.add(new Action(ActionType.TRIGGER_USE,(trigger.entries().getFirst().sourceKind()==dev.hua.ptcglive.engine.effect.EffectOrigin.Kind.ABILITY?"使用触发特性：":"使用触发效果：") + triggerLabel(trigger.entries().getFirst(),player),
                        () -> match.decideTriggeredEffect(player, trigger.id(), true)));
                    result.add(new Action(ActionType.TRIGGER_SKIP,trigger.entries().getFirst().sourceKind()==dev.hua.ptcglive.engine.effect.EffectOrigin.Kind.ABILITY?"放弃触发特性":"放弃触发效果", () -> match.decideTriggeredEffect(player, trigger.id(), false)));
                } else for (var entry : trigger.entries()) result.add(new Action(ActionType.TRIGGER_ORDER,"优先结算触发：" + (entry.pokemonId()==null ? "" : entry.owner().equals(player) ? "己方·" : "对方·") + triggerLabel(entry,player),
                    () -> match.chooseTriggeredEffect(player, trigger.id(), entry.id()), entry.pokemonId()==null || !match.triggerSourceStillInPlay(entry.id()) ? List.of() : List.of(fieldKey(entry.pokemonId()))));
            }
            result.add(new Action(ActionType.CONCEDE,"认输并结束本局", () -> match.concede(player)));
            return result;
        }
        if (match.hasPendingEffectChoice()) {
            var reveal = match.effectCardReveal();
            if (reveal != null) {
                if (reveal.confirmingPlayer().equals(player)) result.add(new Action(ActionType.ACK_REVEAL,"确认已查看展示卡牌",
                    () -> match.acknowledgeEffectReveal(player, reveal.id())));
                result.add(new Action(ActionType.CONCEDE,"认输并结束本局", () -> match.concede(player)));
                return result;
            }
            if (player.equals(match.pendingChoicePlayer())) {
                try {
                    if(match.hasPendingTopDeckAttackCopy()) {
                        var prompt=match.topDeckAttackCopyPrompt(player);var copyView=match.clientView(player);
                        var own=copyView.players().stream().filter(p->p.id().equals(player)).findFirst().orElseThrow();
                        if(match.opponentChoosingCopiedAttack()) {
                            for(var option:prompt.options())result.add(new Action(ActionType.CHOICE,"指定复制招式："+copySourceLabel(copyView,option)+"／"+option.nameZh(),
                                ()->match.chooseOpponentCopiedAttack(player,prompt.resolutionId(),prompt.step(),option.pokemonId(),option.entrySequence(),option.attackId()),
                                List.of(fieldKey(option.pokemonId()),"copy-source:"+option.topCardId(),"copy-attack:"+option.attackId())));
                            result.add(new Action(ActionType.CONCEDE,"认输并结束本局",()->match.concede(player)));
                            return result;
                        }
                        boolean opponentSelected=dev.hua.ptcglive.engine.effect.CopyAttackProgram.opponentChooses(prompt.declared());
                        boolean coinCopy=dev.hua.ptcglive.engine.effect.CopyAttackProgram.coinGated(prompt.declared());
                        if(!opponentSelected&&!coinCopy)result.add(new Action(ActionType.COPY_SKIP,"不复制公开牌库顶中的招式",()->match.declineTopDeckCopiedAttack(player,prompt.resolutionId(),prompt.step())));
                        for(var option:prompt.options()) {
                            String label=(opponentSelected?"结算对手指定招式：":coinCopy?"复制攻击：":"复制牌库顶招式：")+copySourceLabel(copyView,option)+"／"+option.nameZh();
                            if(option.effectiveAttack()==null) {result.add(new Action(label+"（不可用："+option.unsupportedReasonZh()+"）",null));continue;}
                            int firstCopyAction=result.size();
                            var body=option.effectiveAttack();var locks=ServerMatch.locksOpponentAttack(body)
                                ?match.opponentAttackLockOptions(player):List.<dev.hua.ptcglive.engine.field.AttackDefinition>of();
                            if(locks.isEmpty()) addAttackChoices(result,player,copyView,own.active(),prompt.declared(),body,label,null,
                                (effectTarget,targets,discarded)->match.chooseTopDeckCopiedAttack(player,prompt.resolutionId(),prompt.step(),option.pokemonId(),option.attackId(),effectTarget,targets,discarded,null));
                            else for(var locked:locks) addAttackChoices(result,player,copyView,own.active(),prompt.declared(),body,
                                label+" → 锁定对方招式："+locked.nameZh(),null,
                                (effectTarget,targets,discarded)->match.chooseTopDeckCopiedAttack(player,prompt.resolutionId(),prompt.step(),option.pokemonId(),option.attackId(),effectTarget,targets,discarded,locked.id()));
                            markPublicAttack(result,firstCopyAction,option.topCardId(),option.attackId());
                        }
                    } else if(match.hasPendingPokemonSelection()) {
                        var prompt=match.effectPokemonPrompt(player);
                        if(prompt.canFinish())result.add(new Action(ActionType.TARGET_FINISH,"完成目标选择（可少于上限）",
                            ()->match.finishEffectPokemonSelection(player,prompt.resolutionId(),prompt.step())));
                        var publicField=match.clientView(player);
                        for(var target:prompt.targets()) {
                            var board=publicField.players().stream().filter(p->p.id().equals(target.owner())).findFirst().orElseThrow();
                            var cards=new ArrayList<PokemonView>(board.bench());if(board.active()!=null)cards.addFirst(board.active());
                            var card=cards.stream().filter(p->p.pokemonId().equals(target.pokemonId())).findFirst().orElse(null);
                            if(card==null)continue;
                            String side=target.owner().equals(player)?"己方":"对方";
                            result.add(new Action(ActionType.TARGET_SELECT,"选择效果目标："+side+"·"+name(card.topCardId())+"（"+prompt.labelZh()+"）",
                                ()->match.chooseEffectPokemon(player,prompt.resolutionId(),prompt.step(),target.pokemonId()),List.of(fieldKey(target.pokemonId()),POKEMON_SELECTION_KEY)));
                        }
                    } else if (match.hasPendingDamageAllocation()) {
                        var prompt = match.damageAllocationPrompt(player);
                        if (prompt.canConfirm()) result.add(new Action(ActionType.DAMAGE_CONFIRM,"确认分配（" + prompt.budget() + " 个伤害指示物）",
                            () -> match.confirmDamageAllocation(player,prompt.resolutionId(),prompt.step())));
                        for (var target : prompt.candidates()) {
                            int assigned = prompt.counters().getOrDefault(target.pokemonId(),0);
                            if (assigned < prompt.limit(target.pokemonId()) && prompt.allocated() < prompt.budget() && (assigned > 0 || prompt.counters().size() < prompt.maximumTargets()))
                                result.add(new Action("增加伤害指示物（已分配 " + assigned + " 个）",
                                    () -> match.adjustDamageAllocation(player,prompt.resolutionId(),prompt.step(),target.pokemonId(),1),
                                    List.of(fieldKey(target.pokemonId()),"counter:add")));
                            if (assigned > 0) result.add(new Action("撤回伤害指示物（已分配 " + assigned + " 个）",
                                () -> match.adjustDamageAllocation(player,prompt.resolutionId(),prompt.step(),target.pokemonId(),-1),
                                List.of(fieldKey(target.pokemonId()),"counter:remove")));
                        }
                    } else if (match.hasPendingEnergyDistribution()) {
                        var prompt = match.effectAttachmentPrompt(player);
                        var view = match.clientView(player);
                        if (match.canConfirmEnergyDistribution(player, prompt.resolutionId(), prompt.step()))
                            result.add(new Action(ActionType.ENERGY_CONFIRM,"确认能量分配（" + prompt.cards().size() + " 张）",
                                () -> match.confirmEffectEnergyDistribution(player, prompt.resolutionId(), prompt.step())));
                        for (var card : prompt.cards()) for (UUID targetId : match.energyDistributionTargets(player, prompt.resolutionId(), prompt.step(), card.instanceId())) {
                            var field = view.players().stream().flatMap(seat -> {
                                var cards = new ArrayList<PokemonView>(seat.bench());
                                if (seat.active() != null) cards.addFirst(seat.active());
                                return cards.stream();
                            }).filter(value -> value.pokemonId().equals(targetId)).findFirst().orElseThrow();
                            result.add(new Action(ActionType.ENERGY_ASSIGN,"分配 " + name(card.definitionId()) + " → " + name(field.topCardId()),
                                () -> match.assignEffectEnergyDistribution(player, prompt.resolutionId(), prompt.step(), card.instanceId(), targetId),
                                List.of("effect-source:" + card.instanceId(), fieldKey(targetId))));
                        }
                    } else if (match.hasPendingAttachmentTarget()) {
                        var prompt = match.effectAttachmentPrompt(player);
                        var view = match.clientView(player);
                        for (UUID id : prompt.targets()) {
                            var field = view.players().stream().flatMap(p -> {
                                var list = new ArrayList<PokemonView>(p.bench());
                                if (p.active() != null) list.add(p.active()); return list.stream();
                            }).filter(p -> p.pokemonId().equals(id)).findFirst().orElseThrow();
                            var keys = new ArrayList<String>(); keys.add(fieldKey(id));
                            prompt.cards().forEach(card -> keys.add("effect-source:" + card.instanceId()));
                            result.add(new Action(ActionType.ENERGY_TARGET,"选择" + prompt.actionZh() + "目标：" + name(field.topCardId()),
                                () -> match.chooseEffectAttachmentTarget(player, prompt.resolutionId(), prompt.step(), id), keys));
                        }
                    } else if (match.hasPendingPrizeInspectSelection()) {
                        var prompt=match.prizeInspectPrompt(player);
                        for(int slot=0;slot<prompt.slotCount();slot++) {
                            int position=slot;
                            result.add(new Action(ActionType.PRIZE_VIEW,"查看对手暗面奖赏卡：位置 "+(slot+1),
                                ()->match.inspectEffectPrizeAt(player,prompt.resolutionId(),prompt.step(),position)));
                        }
                    } else if(match.hasPendingPrizeRandomSwapSelection()) {
                        var prompt=match.prizeRandomSwapPrompt(player);
                        if(prompt.decisionReady()) {
                            result.add(new Action(ActionType.PRIZE_SWAP,"交换公开奖赏与随机展示的手牌",()->match.decidePrizeRandomHandSwap(player,prompt.resolutionId(),prompt.step(),true)));
                            result.add(new Action(ActionType.SWAP_SKIP,"不交换",()->match.decidePrizeRandomHandSwap(player,prompt.resolutionId(),prompt.step(),false)));
                        } else for(int slot:prompt.faceDownSlots()) {
                            int position=slot;
                            result.add(new Action(ActionType.PRIZE_REVEAL,"翻开对手暗面奖赏卡：位置 "+(slot+1),
                                ()->match.choosePrizeRandomSwapSlot(player,prompt.resolutionId(),prompt.step(),position)));
                        }
                    } else if (match.hasPendingPrizeTakeSelection()) {
                        var prompt=match.prizeTakePrompt(player);
                        for(int slot=0;slot<prompt.slotCount();slot++) {
                            int position=slot;
                            result.add(new Action(ActionType.PRIZE_PICK,"拿取背面朝上的奖赏卡：位置 "+(slot+1),
                                ()->match.takeEffectPrizeAt(player,prompt.resolutionId(),prompt.step(),prompt.selection(),position)));
                        }
                    } else if (match.hasPendingPrizeSwapSelection()) {
                        var prompt=match.prizeSwapPrompt(player);
                        if(!prompt.selectedSlots().isEmpty())result.add(new Action(ActionType.PRIZE_CONFIRM,"确认选择"+prompt.selectedSlots().size()+"张暗面奖赏",()->match.confirmPrizeSwapSlots(player,prompt.resolutionId(),prompt.step())));
                        for(int slot=0;slot<prompt.slotCount();slot++) {
                            int selectedSlot=slot;boolean selected=prompt.selectedSlots().contains(slot);
                            if(selected||prompt.selectedSlots().size()<prompt.maximum())result.add(new Action(selected?ActionType.PRIZE_DESELECT:ActionType.PRIZE_SELECT,(selected?"取消":"选择")+"暗面奖赏位置 "+(slot+1),()->match.togglePrizeSwapSlot(player,prompt.resolutionId(),prompt.step(),selectedSlot)));
                        }
                    } else if (match.hasPendingCardSelection()) {
                        var prompt = match.effectCardPrompt(player);
                        if (match.canConfirmEffectCards(player)) result.add(new Action(ActionType.CARD_CONFIRM,"确认选牌（" + prompt.selected().size() + " 张）",
                            () -> match.submitEffectCards(player, prompt.resolutionId(), prompt.step(), prompt.selected())));
                        for (var card : prompt.candidates()) {
                            boolean selected = prompt.selected().contains(card.instanceId());
                            if (selected || match.canSelectEffectCard(player,card.instanceId())) result.add(action(selected?ActionType.CARD_DESELECT:ActionType.CARD_SELECT,
                                (selected ? "取消选牌：" : "选择卡牌：") + name(card.definitionId()),
                                () -> match.toggleEffectCard(player, prompt.resolutionId(), prompt.step(), card.instanceId()), "effect:" + card.instanceId()));
                        }
                    } else {
                    var prompt = match.effectChoicePrompt(player);
                    for (int i = 0; i < prompt.optionsZh().size(); i++) {
                        if(!match.canChooseEffectOption(player,i))continue;
                        int selected = i;
                        UUID firstTarget=match.firstCoinTargetOption(player,i);
                        var kind=firstTarget!=null?ActionType.TARGET_SELECT:match.choosingCoinOutcome(player)?(i==0?ActionType.COIN_HEADS:ActionType.COIN_TAILS):ActionType.CHOICE;
                        result.add(new Action(kind,"选择效果 " + (i + 1) + "：" + prompt.optionsZh().get(i),
                            () -> match.chooseEffectOption(player, prompt.resolutionId(), prompt.step(), selected),firstTarget==null?List.of():List.of(fieldKey(firstTarget))));
                    }
                    }
                } catch (IllegalStateException failure) { result.add(new Action(failure.getMessage(), null)); }
            }
            result.add(new Action(ActionType.CONCEDE,"认输并结束本局", () -> match.concede(player)));
            return result;
        }
        if(match.hasPendingDrawnTrainer()) {
            if(match.hasPendingDrawnTrainer(player)) {
                var prompt=match.drawnTrainerPrompt(player);
                result.add(new Action(ActionType.LUCKY_USE,"入手前使用："+name(prompt.cardId())+"，抽3张牌",
                    ()->match.decideDrawnTrainer(player,prompt.id(),true)));
                result.add(new Action(ActionType.LUCKY_SKIP,"将抽到的卡加入手牌",
                    ()->match.decideDrawnTrainer(player,prompt.id(),false)));
            }
            result.add(new Action(ActionType.CONCEDE,"认输并结束本局",()->match.concede(player)));
            return result;
        }
        if(match.hasPendingEmergencyEntry()) {
            if(match.hasPendingEmergencyEntry(player)) {
                String cardName=match.emergencyEntryCardName(player);
                result.add(new Action(ActionType.LUCKY_USE,"紧急入场："+cardName+"放入备战区，再抽3张",
                    ()->match.decideEmergencyEntry(player,true)));
                result.add(new Action(ActionType.LUCKY_SKIP,"将"+cardName+"正常加入手牌",
                    ()->match.decideEmergencyEntry(player,false)));
            }
            result.add(new Action(ActionType.CONCEDE,"认输并结束本局",()->match.concede(player)));
            return result;
        }
        var view = match.clientView(player);
        var own = view.players().stream().filter(p -> p.id().equals(player)).findFirst().orElseThrow();
        switch (match.phase()) {
            case MULLIGAN_REVEAL -> {
                if (!match.mulliganAcknowledged(player)) result.add(new Action(ActionType.ACK_MULLIGAN,"确认无基础宝可梦的重抽展示", () -> match.acknowledgeMulligan(player)));
            }
            case MULLIGAN_BENCH -> {
                if (!match.mulliganBenchReady(player)) {
                    if (own.bench().size() < match.benchLimit(player)) for (var card : view.hand()) if (match.isMulliganBonusBasic(player, card.instanceId()))
                        result.add(action(ActionType.BENCH,"补抽基础宝可梦放入备战：" + name(card.definitionId()), () -> match.placeMulliganBench(player, card.instanceId()), handKey(card.instanceId())));
                    result.add(new Action(ActionType.BENCH_CONFIRM,"完成补抽后的备战设置", () -> match.finishMulliganBench(player)));
                }
            }
            case MULLIGAN_DRAW -> {
                if (!match.mulliganChosen(player)) for (int i = 0; i <= match.mulliganDrawLimit(player); i++) {
                    int count = i; result.add(new Action("重抽补偿：抽 " + count + " 张", () -> match.chooseMulliganDraw(player, count),List.of(),ActionType.MULLIGAN_DRAW,Integer.toString(count)));
                }
            }
            case COIN_CALL -> {
                if (player.equals(match.pendingChoicePlayer())) {
                    result.add(new Action(ActionType.COIN,"猜硬币：正面", () -> match.callCoin(player, CoinSide.HEADS)));
                    result.add(new Action(ActionType.COIN,"猜硬币：反面", () -> match.callCoin(player, CoinSide.TAILS)));
                }
            }
            case TURN_ORDER -> {
                if (player.equals(match.pendingChoicePlayer())) {
                    result.add(new Action(ActionType.GO_FIRST,"选择自己先手", () -> match.chooseWhoGoesFirst(player, true)));
                    result.add(new Action(ActionType.GO_SECOND,"选择对方先手", () -> match.chooseWhoGoesFirst(player, false)));
                }
            }
            case OPENING_POKEMON -> {
                if (!match.openingReady(player)) {
                    for (var card : view.hand()) if (match.canChooseOpeningActive(card.definitionId())) {
                        if (own.active() == null) result.add(action(ActionType.OPEN_ACTIVE,"设为战斗宝可梦：" + name(card.definitionId()), () -> match.chooseOpeningActive(player, card.instanceId()), handKey(card.instanceId())));
                        else if (own.bench().size() < match.benchLimit(player)) result.add(action(ActionType.BENCH,"放入备战区：" + name(card.definitionId()), () -> match.placeOpeningBench(player, card.instanceId()), handKey(card.instanceId())));
                    }
                    if (own.active() != null) result.add(new Action(ActionType.OPEN_CONFIRM,"确认开局设置", () -> match.startAfterOpening(player)));
                }
            }
            case TURN -> {
                if (player.equals(match.currentPlayer())) {
                    var pokemon = new ArrayList<PokemonView>();
                    if (own.active() != null) pokemon.add(own.active()); pokemon.addAll(own.bench());
                    var opponent = view.players().stream().filter(candidate -> !candidate.id().equals(player)).findFirst().orElseThrow();
                    // Each explicit unordered pair is one server-issued play; either hand card can anchor it in the UI.
                    for(int i=0;i<view.hand().size();i++) {
                        var first=view.hand().get(i);
                        if(match.trainerProfile(first.definitionId()).map(p->p.pairedPlay()==null||p.pairedPlay().count()!=2).orElse(true))continue;
                        for(int j=i+1;j<view.hand().size();j++) {
                            var second=view.hand().get(j);var ids=List.of(first.instanceId(),second.instanceId());
                            if(match.canPlayTrainerPair(player,ids))result.add(action(ActionType.TRAINER,"使用："+name(first.definitionId())
                                +" ×2（手牌第"+(i+1)+"、"+(j+1)+"张）",()->match.playTrainerPair(player,ids),
                                handKey(first.instanceId()),handKey(second.instanceId())));
                        }
                    }
                    var combinedGroups=new HashSet<PairedTrainerPlay>();
                    for(var first:view.hand()) {
                        var group=match.trainerProfile(first.definitionId()).map(p->p.pairedPlay()).orElse(null);
                        if(group==null||group.count()!=4||!combinedGroups.add(group))continue;
                        var ids=view.hand().stream().filter(c->group.definitionIds().contains(c.definitionId())).limit(group.count()).map(CardInstance::instanceId).toList();
                        if(match.canPlayTrainerPair(player,ids))result.add(action(ActionType.TRAINER,"使用："+name(first.definitionId())+" ×4",
                            ()->match.playTrainerPair(player,ids),ids.stream().map(MatchSession::handKey).toArray(String[]::new)));
                    }
                    var quadStones=view.hand().stream().filter(card->"swsh12_en_163".equals(card.definitionId()))
                        .limit(4).toList();
                    if(quadStones.size()==4) {
                        var ids=quadStones.stream().map(card->card.instanceId()).toList();
                        if(match.canPlayQuadStoneFour(player,ids))
                            result.add(action(ActionType.TRAINER,"使用：四尖石 ×4",()->match.playQuadStoneFour(player,ids),
                                ids.stream().map(MatchSession::handKey).toArray(String[]::new)));
                    }
                    for (var card : view.hand()) {
                        var def = match.cardDefinition(card.definitionId());
                        if (def.category() == CardCategory.TRAINER && match.trainerPlayLocked(player, card.definitionId())) {
                            result.add(new Action("本回合不能使用：" + name(card.definitionId()) + "（对手招式的效果）", null));
                            continue;
                        }
                        if (def.category() == CardCategory.TRAINER) match.trainerProfile(card.definitionId()).ifPresent(profile -> {
                            if(profile.pairedPlay()!=null&&!profile.pairedPlay().allowsSingle())return;
                            if (profile.composed()) {
                                var problem = dev.hua.ptcglive.engine.effect.TrainerProgramRules.unsupportedReason(profile.effectProgram());
                                if (problem.isPresent()) {
                                    result.add(new Action("训练家组合尚未支持：" + name(card.definitionId()) + "（" + problem.get() + "）", null));
                                } else {
                                    var selection = dev.hua.ptcglive.engine.effect.TrainerProgramRules.selectionTarget(profile.effectProgram());
                                    if (selection == null) {
                                        if (match.canPlayTrainer(player, card.instanceId(), null))
                                            result.add(action(ActionType.TRAINER,(profile.category()==TrainerCategory.STADIUM?"放置场地：":"使用：") + name(card.definitionId()), () -> match.playTrainer(player, card.instanceId(), null), handKey(card.instanceId())));
                                    } else {
                                        var candidates = new ArrayList<PokemonView>();
                                        if (selection == dev.hua.ptcglive.engine.effect.EffectTarget.SELECT_OWN_POKEMON) candidates.addAll(pokemon);
                                        else { if (opponent.active() != null) candidates.add(opponent.active()); candidates.addAll(opponent.bench()); }
                                        for (var target : candidates) if (match.canPlayTrainer(player, card.instanceId(), target.pokemonId()))
                                            result.add(action(ActionType.TRAINER,"使用 " + name(card.definitionId()) + " → " + name(target.topCardId()),
                                                () -> match.playTrainer(player, card.instanceId(), target.pokemonId()), handKey(card.instanceId()), fieldKey(target.pokemonId())));
                                    }
                                }
                            } else if (profile.effect().needsDeckSelection()) {
                                if(match.canBeginTrainerSearch(player,card.instanceId()))result.add(action(ActionType.TRAINER,"使用："+name(card.definitionId())+"（开始检索）",
                                    ()->match.beginTrainerSearch(player,card.instanceId()),handKey(card.instanceId())));
                            } else if (profile.effect().needsEvolutionCardSelection()) {
                                for (var target : pokemon) for (var evolution : view.hand()) {
                                    var effect = profile.effect();
                                    var permission = effect.evolutionPermission();
                                    boolean canEvolve = effect.requiresSkippedEvolutionStage()
                                        ? match.canEvolveByEffectSkippingOneStage(player, target.pokemonId(), evolution.definitionId(), permission)
                                        : match.canEvolveByEffect(player, target.pokemonId(), evolution.definitionId(), permission);
                                    if (evolution.instanceId().equals(card.instanceId())
                                        || match.cardDefinition(evolution.definitionId()).category() != CardCategory.POKEMON || !canEvolve) continue;
                                    result.add(action(ActionType.TRAINER,"使用 " + name(card.definitionId()) + "：" + targetLabel(own.active(), target)
                                            + " 进化为 " + name(evolution.definitionId()),
                                        () -> match.playTrainerWithEvolution(player, card.instanceId(), target.pokemonId(), evolution.instanceId()),
                                        handKey(card.instanceId()), handKey(evolution.instanceId()), fieldKey(target.pokemonId())));
                                }
                            } else if (profile.effect().needsPokemonTarget()) {
                                List<PokemonView> trainerTargets = profile.opponentLegacyExTool()?java.util.stream.Stream.concat(java.util.stream.Stream.ofNullable(opponent.active()),opponent.bench().stream()).toList()
                                    :profile.effect().targetsOpponentPokemon() ? opponent.bench() : pokemon;
                                for (var target : trainerTargets) if (match.canPlayTrainer(player, card.instanceId(), target.pokemonId())) {
                                    String targetName = profile.opponentLegacyExTool()?"对方"+targetLabel(opponent.active(),target):profile.effect().targetsOpponentPokemon()
                                        ? "对方备战：" + name(target.topCardId()) : targetLabel(own.active(), target);
                                    result.add(action(ActionType.TRAINER,"使用 " + name(card.definitionId()) + " → " + targetName,
                                        () -> match.playTrainer(player, card.instanceId(), target.pokemonId()), handKey(card.instanceId()), fieldKey(target.pokemonId())));
                                }
                            } else if (match.canPlayTrainer(player, card.instanceId(), null)) {
                                if(profile.directionalStadium())for(boolean ownFirst:List.of(true,false)) {
                                    UUID firstSide=ownFirst?player:opponent.id();
                                    result.add(action(ActionType.TRAINER,"放置场地："+name(card.definitionId())+"（我方："+profile.stadiumSideLabel(ownFirst)+"；对方："+profile.stadiumSideLabel(!ownFirst)+"）",
                                        ()->match.playTrainer(player,card.instanceId(),null,firstSide),handKey(card.instanceId()),"stadium-first:"+(ownFirst?"SELF":"OPPONENT")));
                                } else result.add(action(ActionType.TRAINER,(profile.category()==TrainerCategory.STADIUM?"放置场地：":"使用：") + name(card.definitionId()), () -> match.playTrainer(player, card.instanceId(), null), handKey(card.instanceId())));
                            }
                        });
                        if (def.basicPokemon() && match.canPlayBasicPokemonToBench(player,card.definitionId()))
                            result.add(action(ActionType.BENCH,"备战：" + name(card.definitionId()), () -> match.playBasicPokemonToBench(player, card.instanceId()), handKey(card.instanceId())));
                        if(match.canUseEmergencyRotation(player,card.instanceId()))
                            result.add(action(ActionType.ABILITY,"使用特性·紧急转动："+name(card.definitionId()),()->match.useEmergencyRotation(player,card.instanceId()),handKey(card.instanceId())));
                        if(match.canUseSwellingFlash(player,card.instanceId()))
                            result.add(action(ActionType.ABILITY,"使用特性·"+match.handPlacementAbilityName(player,card.instanceId())+"："+name(card.definitionId()),()->match.useSwellingFlash(player,card.instanceId()),handKey(card.instanceId())));
                        if (def.category() == CardCategory.ENERGY && !match.energyAttached(player)) for (var target : pokemon) if(match.canAttachEnergy(player,card.instanceId(),target.pokemonId()))
                            result.add(action(ActionType.ATTACH,"附着 " + name(card.definitionId()) + " → " + targetLabel(own.active(), target),
                                () -> match.attachEnergy(player, card.instanceId(), target.pokemonId()), handKey(card.instanceId()), fieldKey(target.pokemonId())));
                        if (def.category() == CardCategory.POKEMON && !def.basicPokemon()) for (var target : pokemon)
                            if (match.canEvolve(player, target.pokemonId(), card.definitionId())) result.add(action(ActionType.EVOLVE,"进化：" + targetLabel(own.active(), target) + " → " + name(card.definitionId()),
                                () -> match.evolve(player, target.pokemonId(), card.instanceId()), handKey(card.instanceId()), fieldKey(target.pokemonId())));
                    }
                    for(var recoverable:own.discard())if(match.canRecoverDiscardSupporter(player,recoverable.instanceId()))
                        for(int i=0;i<view.hand().size();i++)for(int j=i+1;j<view.hand().size();j++) {
                            var first=view.hand().get(i);var second=view.hand().get(j);var costs=List.of(first.instanceId(),second.instanceId());
                            if(!match.canRecoverDiscardSupporter(player,recoverable.instanceId(),costs))continue;
                            result.add(action(ActionType.RECOVER_SUPPORTER,"弃置手牌第"+(i+1)+"、"+(j+1)+"张，回收："+name(recoverable.definitionId()),
                                ()->match.recoverDiscardSupporter(player,recoverable.instanceId(),costs),discardKey(recoverable.instanceId()),
                                handKey(first.instanceId()),handKey(second.instanceId())));
                        }
                    for(var option:match.discardAbilityOptions(player))
                        if(match.canUseDiscardAbility(player,option.cardInstanceId(),option.ability().id()))
                            result.add(action(ActionType.DISCARD_ABILITY,"使用弃牌区特性："+name(option.cardDefinitionId())+" · "+option.ability().nameZh(),
                                ()->match.useDiscardAbility(player,option.cardInstanceId(),option.ability().id()),
                                discardKey(option.cardInstanceId())));
                    if(match.canActivateStadium(player)) {
                        var stadiumCard=view.stadium();long entry=match.stadiumEntrySequence();
                        result.add(action(ActionType.STADIUM,"使用竞技场："+name(stadiumCard.definitionId()),
                            ()->match.activateStadium(player,stadiumCard.instanceId(),entry)));
                    }
                    for(var option:match.toolActivationOptions(player))
                        result.add(action(ActionType.TOOL_ABILITY,"使用VSTAR力量："+name(option.toolDefinitionId()),
                            ()->match.activateTool(player,option.pokemonId(),option.pokemonEntry(),option.toolId(),option.toolEntry()),
                            fieldKey(option.pokemonId())));
                    for(var target:pokemon) if(match.canDiscardFossil(player,target.pokemonId()))
                        result.add(action(ActionType.FOSSIL_DISCARD,(match.trainerProfile(target.topCardId()).orElseThrow().inPlayPokemon().printedRules()==dev.hua.ptcglive.engine.field.PokemonPrintedRules.ROBO_SUBSTITUTE_ITEM?"自行弃置替身：":"自行弃置化石：")+targetLabel(own.active(),target),
                            ()->match.discardFossil(player,target.pokemonId()),fieldKey(target.pokemonId())));
                    for(var target:pokemon) if(match.canReturnPokeDoll(player,target.pokemonId()))
                        result.add(action(ActionType.DOLL_RETURN,"收回玩偶："+targetLabel(own.active(),target),
                            ()->match.returnPokeDoll(player,target.pokemonId()),fieldKey(target.pokemonId())));
                    for (var option : match.abilityOptions(player)) {
                        String owner = targetLabel(own.active(), new PokemonView(option.pokemonId(), option.pokemonDefinitionId(), 0, 0,
                            List.of(), List.of(), List.of(), List.of()));
                        String label = "使用特性：" + owner + " · " + option.ability().nameZh();
                        if (option.ability().kind() != dev.hua.ptcglive.engine.field.AbilityDefinition.Kind.ACTIVATED_ONCE_PER_TURN)
                            label += "（" + option.ability().activationLabelZh() + "）";
                        if (option.ability().effectSupport() != dev.hua.ptcglive.rules.EffectSupport.SUPPORTED)
                            label = "特性尚未支持：" + owner + " · " + option.ability().nameZh() + "（" + option.ability().unsupportedReasonZh() + "）";
                        else if (option.usedThisTurn()) label = (option.ability().usageLimit()!=null
                            && match.sharedUsageExhausted(player,option.ability().usageLimit()) ? option.ability().usageLimit().exhaustedZh()
                            : "本回合已使用特性") + "：" + owner + " · " + option.ability().nameZh();
                        if (option.ability().composed()) {
                            var program = option.ability().effectProgram();
                            var problem = dev.hua.ptcglive.engine.effect.ActivatedAbilityRules.unsupportedReason(option.ability());
                            if (problem.isPresent()) result.add(new Action("特性组合尚未支持：" + option.ability().nameZh() + "（" + problem.get() + "）", null));
                            else {
                                var selection = dev.hua.ptcglive.engine.effect.ResumableProgramRules.selectionTarget(program);
                                if (selection == null) {
                                    if (match.canUseAbility(player, option.pokemonId(), option.ability().id()))
                                        result.add(ability(option.ability().id(),label, () -> match.useAbility(player, option.pokemonId(), option.ability().id()), fieldKey(option.pokemonId())));
                                } else {
                                    var candidates = new ArrayList<PokemonView>();
                                    if (selection == dev.hua.ptcglive.engine.effect.EffectTarget.SELECT_OWN_POKEMON) candidates.addAll(pokemon);
                                    else { if (opponent.active() != null) candidates.add(opponent.active()); candidates.addAll(opponent.bench()); }
                                    for (var target : candidates) if (match.canUseAbility(player, option.pokemonId(), option.ability().id(), target.pokemonId()))
                                        result.add(ability(option.ability().id(),label + " → " + name(target.topCardId()),
                                            () -> match.useAbility(player, option.pokemonId(), option.ability().id(), target.pokemonId()),
                                            fieldKey(option.pokemonId()), fieldKey(target.pokemonId())));
                                }
                            }
                        } else if (option.ability().effect().needsDeckSelection()) {
                            if(match.canBeginAbilitySearch(player,option.pokemonId(),option.ability().id()))result.add(ability(option.ability().id(),label+"（开始检索）",
                                ()->match.beginAbilitySearch(player,option.pokemonId(),option.ability().id()),fieldKey(option.pokemonId())));
                        } else if (option.ability().effect().needsAttachedEnergyTarget()) {
                            PokemonView source = pokemon.stream().filter(candidate -> candidate.pokemonId().equals(option.pokemonId()))
                                .findFirst().orElse(null);
                            if (source != null) for (var target : pokemon) {
                                if (target.pokemonId().equals(source.pokemonId())) continue;
                                for (UUID energyId : source.attachedEnergyIds()) {
                                    if (!match.canUseAbility(player, option.pokemonId(), option.ability().id(), target.pokemonId(), energyId)) continue;
                                    String actionLabel = label + " → " + targetLabel(own.active(), target) + "（移动 1 张附着能量）";
                                    result.add(ability(option.ability().id(),actionLabel,
                                        () -> match.useAbility(player, option.pokemonId(), option.ability().id(), target.pokemonId(), energyId),
                                        fieldKey(option.pokemonId()), fieldKey(target.pokemonId())));
                                }
                            }
                        } else if (option.ability().effect().needsPokemonTarget()) {
                            for (var target : pokemon) {
                                if(!match.canUseAbility(player,option.pokemonId(),option.ability().id(),target.pokemonId()))continue;
                                String actionLabel = label + " → " + targetLabel(own.active(), target);
                                result.add(ability(option.ability().id(),actionLabel, () -> match.useAbility(player, option.pokemonId(), option.ability().id(), target.pokemonId()),
                                    fieldKey(option.pokemonId()), fieldKey(target.pokemonId())));
                            }
                        } else {
                            String actionLabel = label;
                            if(!match.canUseAbility(player,option.pokemonId(),option.ability().id(),null))continue;
                            result.add(ability(option.ability().id(),actionLabel, () -> match.useAbility(player, option.pokemonId(), option.ability().id()), fieldKey(option.pokemonId())));
                        }
                    }
                    if (match.mayAttack(player)) for (var attack : match.activeAttacks(player)) {
                        var unsupported = dev.hua.ptcglive.engine.effect.AttackProgramRules.unsupportedReason(attack);
                        if (unsupported.isPresent()) {
                            result.add(new Action("攻击组合尚未支持：" + attack.nameZh() + "（" + unsupported.get() + "）",null));
                            continue;
                        }
                        String label = attack.effectSupport() == dev.hua.ptcglive.rules.EffectSupport.SUPPORTED
                            ? "攻击：" + attack.nameZh() + (attack.coinDamage() == null ? "（基础伤害 " + attack.baseDamage() + "）"
                                : "（服务器掷 " + attack.coinDamage().flips() + " 次硬币）")
                            : "攻击尚未支持：" + attack.nameZh() + "（" + attack.unsupportedReasonZh() + "）";
                        if(match.needsCopyAttackSelection(player,attack)) {
                            var choices=match.copyAttackOptions(player,attack.id());
                            if(choices.isEmpty()) {
                                if(match.previousEvolutionCopyHasNoChoice(player,own.active().pokemonId(),attack))
                                    addAttackChoices(result,player,view,own.active(),attack,attack.withEffectProgram(dev.hua.ptcglive.engine.effect.EffectProgram.EMPTY),
                                        label+"（没有进化前招式）",null,
                                        (effectTarget,targets,discarded)->match.attackWithSelectedEffectTargetAndEnergyDiscards(player,attack.id(),effectTarget,targets,discarded));
                                else result.add(new Action("没有可复制的公开招式："+attack.nameZh(),null));
                            }
                            for(var choice:choices) {
                                String copyLabel="复制攻击："+attack.nameZh()+" → "+name(choice.topCardId())+"／"+choice.nameZh();
                                if(choice.unsupportedReasonZh()!=null) {
                                    result.add(new Action(copyLabel+"（不可用："+choice.unsupportedReasonZh()+"）",null));continue;
                                }
                                int firstCopyAction=result.size();
                                if(ServerMatch.locksOpponentAttack(choice.effectiveAttack())&&!match.opponentAttackLockOptions(player).isEmpty()) {
                                    for(var locked:match.opponentAttackLockOptions(player))
                                        addAttackChoices(result,player,view,own.active(),attack,choice.effectiveAttack(),
                                            copyLabel+" → 锁定对方招式："+locked.nameZh(),choice.pokemonId(),
                                            (effectTarget,targets,discarded)->match.copyAttackWithSelectedEffectTargetAndEnergyDiscards(player,attack.id(),
                                                choice.pokemonId(),choice.entrySequence(),choice.attackId(),effectTarget,targets,discarded,locked.id()));
                                } else addAttackChoices(result,player,view,own.active(),attack,choice.effectiveAttack(),copyLabel,choice.pokemonId(),
                                    (effectTarget,targets,discarded)->match.copyAttackWithSelectedEffectTargetAndEnergyDiscards(player,attack.id(),choice.pokemonId(),
                                        choice.entrySequence(),choice.attackId(),effectTarget,targets,discarded));
                                markPublicAttack(result,firstCopyAction,choice.topCardId(),choice.attackId());
                            }
                        } else if(ServerMatch.locksOpponentAttack(attack)&&!match.opponentAttackLockOptions(player).isEmpty()) {
                            // “Choose 1 of your opponent's Active Pokémon's attacks …”: one server-issued action per printed attack.
                            for(var locked:match.opponentAttackLockOptions(player))
                                addAttackChoices(result,player,view,own.active(),attack,attack,label+" → 锁定对方招式："+locked.nameZh(),null,
                                    (effectTarget,targets,discarded)->match.attackLockingOpponentAttack(player,attack.id(),locked.id(),effectTarget,targets,discarded));
                        } else addAttackChoices(result,player,view,own.active(),attack,attack,label,null,
                            (effectTarget,targets,discarded)->match.attackWithSelectedEffectTargetAndEnergyDiscards(player,attack.id(),effectTarget,targets,discarded));
                    }
                    for(var offered:match.borrowedAttackOptions(player)) {
                        var source=own.active()!=null&&own.active().pokemonId().equals(offered.holderId())?own.active():own.bench().stream().filter(p->p.pokemonId().equals(offered.holderId())).findFirst().orElseThrow();
                        String label="攻击："+(source==own.active()?"":"备战 ")+name(source.topCardId())+" 借用 "+name(offered.providerCardId())+"／"+offered.nameZh();
                        if(offered.attack()==null){result.add(new Action(label+"（不可用："+offered.unsupportedReasonZh()+"）",null));continue;}
                        var attack=offered.attack();
                        if(match.needsCopyAttackSelection(player,attack)) {
                            var copies=match.borrowedCopyAttackOptions(player,offered.holderId(),offered.holderEntry(),offered.holderTop(),offered.abilityId(),offered.providerId(),offered.providerEntry(),offered.providerTop(),offered.attackId());
                            if(copies.isEmpty()) {
                                if(match.previousEvolutionCopyHasNoChoice(player,offered.holderId(),attack))
                                    addBorrowedAttackChoices(result,player,view,source,offered,
                                        attack.withEffectProgram(dev.hua.ptcglive.engine.effect.EffectProgram.EMPTY),label+"（没有进化前招式）",null);
                                else result.add(new Action(label+"（没有可复制的公开招式）",null));
                            }
                            for(var copied:copies) {
                                if(copied.effectiveAttack()==null){result.add(new Action(label+" → "+copied.nameZh()+"（不可用："+copied.unsupportedReasonZh()+"）",null));continue;}
                                addBorrowedAttackChoices(result,player,view,source,offered,copied.effectiveAttack(),label+" → "+name(copied.topCardId())+"／"+copied.nameZh(),copied);
                            }
                        } else addBorrowedAttackChoices(result,player,view,source,offered,attack,label,null);
                    }
                    for(var offered:match.benchAttackOptions(player)) {
                        var source=own.bench().stream().filter(p->p.pokemonId().equals(offered.pokemonId())).findFirst().orElseThrow();
                        var attack=offered.attack();String label="攻击：备战 "+name(source.topCardId())+"／"+attack.nameZh();
                        if(match.needsCopyAttackSelection(player,attack)) {
                            var copies=match.benchCopyAttackOptions(player,source.pokemonId(),attack.id());
                            if(copies.isEmpty()&&match.previousEvolutionCopyHasNoChoice(player,source.pokemonId(),attack))
                                addAttackChoices(result,player,view,source,attack,attack.withEffectProgram(dev.hua.ptcglive.engine.effect.EffectProgram.EMPTY),
                                    label+"（没有进化前招式）",null,
                                    (effectTarget,targets,discarded)->match.attackFromBenchWithChoices(player,source.pokemonId(),offered.entry(),offered.topCardId(),attack.id(),effectTarget,targets,discarded,null,-1,null,null));
                            for(var copied:copies) {
                                if(copied.effectiveAttack()==null)continue;
                                var body=copied.effectiveAttack();var locks=ServerMatch.locksOpponentAttack(body)?match.opponentAttackLockOptions(player):List.<dev.hua.ptcglive.engine.field.AttackDefinition>of();
                                if(locks.isEmpty())addAttackChoices(result,player,view,source,attack,body,label+" → "+name(copied.topCardId())+"／"+copied.nameZh(),copied.pokemonId(),
                                    (effectTarget,targets,discarded)->match.attackFromBenchWithChoices(player,source.pokemonId(),offered.entry(),offered.topCardId(),attack.id(),effectTarget,targets,discarded,copied.pokemonId(),copied.entrySequence(),copied.attackId(),null));
                                else for(var locked:locks)addAttackChoices(result,player,view,source,attack,body,label+" → "+copied.nameZh()+" → 锁定 "+locked.nameZh(),copied.pokemonId(),
                                    (effectTarget,targets,discarded)->match.attackFromBenchWithChoices(player,source.pokemonId(),offered.entry(),offered.topCardId(),attack.id(),effectTarget,targets,discarded,copied.pokemonId(),copied.entrySequence(),copied.attackId(),locked.id()));
                            }
                        } else {
                            var locks=ServerMatch.locksOpponentAttack(attack)?match.opponentAttackLockOptions(player):List.<dev.hua.ptcglive.engine.field.AttackDefinition>of();
                            if(locks.isEmpty())addAttackChoices(result,player,view,source,attack,attack,label,null,
                                (effectTarget,targets,discarded)->match.attackFromBenchWithChoices(player,source.pokemonId(),offered.entry(),offered.topCardId(),attack.id(),effectTarget,targets,discarded,null,-1,null,null));
                            else for(var locked:locks)addAttackChoices(result,player,view,source,attack,attack,label+" → 锁定 "+locked.nameZh(),null,
                                (effectTarget,targets,discarded)->match.attackFromBenchWithChoices(player,source.pokemonId(),offered.entry(),offered.topCardId(),attack.id(),effectTarget,targets,discarded,null,-1,null,locked.id()));
                        }
                    }
                    if (own.active() != null && !match.retreated()) {
                        int cost = match.activeRetreatCost(player);
                        var suggested = match.suggestedRetreatPayment(player);
                        if (suggested.isPresent()) for (var bench : own.bench()) {
                            var payment = suggested.get();
                            if(!match.retreatProblem(player,bench.pokemonId(),payment).isEmpty())continue;
                            boolean automatic=computer!=null&&computer.seat().equals(player);
                            result.add(action(ActionType.RETREAT,"撤退 → " + name(bench.topCardId()) + (cost==0?"（无需弃能）":"（选择能量支付 "+cost+" 费用）"),
                                () -> {
                                    if(cost==0||automatic) match.retreat(player,bench.pokemonId(),payment);
                                    else retreatPayment=new RetreatPayment(UUID.randomUUID(),player,own.active().pokemonId(),bench.pokemonId(),List.of());
                                }, fieldKey(own.active().pokemonId()), fieldKey(bench.pokemonId())));
                        }
                    }
                    result.add(new Action(ActionType.END_TURN,"结束回合", () -> match.endTurn(player)));
                }
            }
            case PRIZE_SELECTION -> {
                if(player.equals(match.pendingChoicePlayer())&&match.hasPendingLuckyBonus(player)) {
                    result.add(new Action(ActionType.LUCKY_USE,"发动幸运奖励：将吉利蛋放到备战区并掷硬币",()->match.decideLuckyBonus(player,true)));
                    result.add(new Action(ActionType.LUCKY_SKIP,"不发动幸运奖励：将吉利蛋加入手牌",()->match.decideLuckyBonus(player,false)));
                } else if (player.equals(match.pendingChoicePlayer())) for (int slot : view.prizeSlots())
                    result.add(new Action(ActionType.PRIZE_PICK,"拿取背面朝上的奖赏卡：位置 " + (slot + 1), () -> match.takePrizeAt(player, slot)));
            }
            case CHOOSE_REPLACEMENT -> {
                if (player.equals(match.pendingChoicePlayer())) for (var bench : own.bench())
                    result.add(action(ActionType.REPLACEMENT,"选择替补：" + name(bench.topCardId()), () -> match.chooseReplacement(player, bench.pokemonId()), fieldKey(bench.pokemonId())));
            }
            case POKEMON_CHECKUP, TURN_END, FINISHED -> { }
        }
        if (match.phase() != MatchPhase.FINISHED) result.add(new Action(ActionType.CONCEDE,"认输并结束本局", () -> match.concede(player)));
        return result;
    }
    private String name(String card) { return match.cardDefinition(card).nameZh(); }
    private String triggerLabel(TriggerOrderPrompt.Entry entry,UUID viewer) {
        String label=entry.nameZh()+(entry.sourceKind()==dev.hua.ptcglive.engine.effect.EffectOrigin.Kind.TRAINER?"（训练家）":entry.sourceKind()==dev.hua.ptcglive.engine.effect.EffectOrigin.Kind.ENERGY?"（能量）":"")
            +(entry.sourceKind()==dev.hua.ptcglive.engine.effect.EffectOrigin.Kind.ATTACK?"（攻击延迟效果）":entry.sourceCardId()!=null&&!match.triggerSourceStillInPlay(entry.id())?
                (entry.sourceKind()==dev.hua.ptcglive.engine.effect.EffectOrigin.Kind.ABILITY?"（原特性来源已离场）":"（原效果来源已离场）"):"");
        var target=entry.triggerPokemon();
        if(target==null)return label;
        if(!match.triggerSubjectStillInPlay(entry.id()))return label+"（原事件对象已离场）";
        String side=target.owner().equals(viewer)?"己方":"对方";
        var publicField=match.clientView(null).players().stream().filter(p->p.id().equals(target.owner())).findFirst().orElseThrow();
        if(publicField.active()!=null && publicField.active().pokemonId().equals(target.pokemonId()))
            return label+"（事件对象："+side+"战斗位·"+name(publicField.active().topCardId())+"）";
        for(int i=0;i<publicField.bench().size();i++) {
            var pokemon=publicField.bench().get(i);
            if(pokemon.pokemonId().equals(target.pokemonId()))return label+"（事件对象："+side+"备战"+(i+1)+"·"+name(pokemon.topCardId())+"）";
        }
        return label+"（事件对象已离场·"+target.pokemonId().toString().substring(0,8)+"）";
    }
    private static Action action(String label, Runnable apply, String... cardKeys) { return new Action(label, apply, List.of(cardKeys)); }
    private static Action action(ActionType type,String label,Runnable apply,String... cardKeys){return new Action(type,label,apply,List.of(cardKeys));}
    private static Action ability(String id,String label,Runnable apply,String... cardKeys){return new Action(label,apply,List.of(cardKeys),ActionType.ABILITY,id);}
    private static List<List<PokemonView>> targetCombinations(List<PokemonView> source, int count) {
        if (count < 1 || count > source.size()) return List.of();
        var result = new ArrayList<List<PokemonView>>();
        collectTargetCombinations(source, count, 0, new ArrayList<>(), result);
        return List.copyOf(result);
    }
    private static List<List<PokemonView>> targetCombinationsWithRepetition(List<PokemonView> source,int count) {
        if(source.isEmpty()||count<1||count>60)return List.of();
        var result=new ArrayList<List<PokemonView>>();
        collectTargetCombinationsWithRepetition(source,count,0,new ArrayList<>(),result);return List.copyOf(result);
    }
    private static void collectTargetCombinationsWithRepetition(List<PokemonView> source,int count,int start,
                                                                  List<PokemonView> selected,List<List<PokemonView>> output) {
        if(selected.size()==count){output.add(List.copyOf(selected));return;}
        for(int index=start;index<source.size();index++) {
            selected.add(source.get(index));collectTargetCombinationsWithRepetition(source,count,index,selected,output);selected.removeLast();
        }
    }
    /** Each selected card supplies at least one unit; a multi-unit card may satisfy several. */
    private static List<List<UUID>> energyDiscardCombinations(Map<UUID,Integer> units, int count) {
        count=Math.min(count,units.values().stream().mapToInt(Integer::intValue).sum());
        if (count == 0) return List.of(List.of());
        var source=List.copyOf(units.keySet());
        var result = new ArrayList<List<UUID>>();
        for(int cards=1;cards<=Math.min(count,source.size());cards++)
            collectEnergyCombinations(source,cards,0,new ArrayList<>(),result);
        int required=count;
        result.removeIf(selected->selected.stream().mapToInt(units::get).sum()<required);
        return List.copyOf(result);
    }
    private static void collectEnergyCombinations(List<UUID> source, int count, int start, List<UUID> selected,
                                                  List<List<UUID>> output) {
        if (selected.size() == count) { output.add(List.copyOf(selected)); return; }
        for (int index = start; index <= source.size() - (count - selected.size()); index++) {
            selected.add(source.get(index)); collectEnergyCombinations(source, count, index + 1, selected, output); selected.removeLast();
        }
    }
    private static void collectTargetCombinations(List<PokemonView> source, int count, int start, List<PokemonView> selected,
                                                  List<List<PokemonView>> output) {
        if (selected.size() == count) { output.add(List.copyOf(selected)); return; }
        for (int index = start; index <= source.size() - (count - selected.size()); index++) {
            selected.add(source.get(index)); collectTargetCombinations(source, count, index + 1, selected, output); selected.removeLast();
        }
    }
    private static String handKey(UUID cardId) { return "hand:" + cardId; }
    private static String discardKey(UUID cardId) { return "discard:" + cardId; }
    private String copySourceLabel(MatchClientView view,ServerMatch.CopyAttackOption option) {
        String card=name(option.topCardId());
        for(var board:view.players()) {
            if(!board.id().equals(option.owner()))continue;
            if(board.active()!=null&&board.active().pokemonId().equals(option.pokemonId()))return "战斗位 "+card;
            for(int i=0;i<board.bench().size();i++)
                if(board.bench().get(i).pokemonId().equals(option.pokemonId()))return "备战位 "+(i+1)+" "+card;
        }
        return card;
    }
    private static String fieldKey(UUID pokemonId) { return "field:" + pokemonId; }
    private String targetLabel(PokemonView active, PokemonView target) {
        return (active != null && active.pokemonId().equals(target.pokemonId()) ? "战斗位 " : "备战区 ") + name(target.topCardId());
    }
}
