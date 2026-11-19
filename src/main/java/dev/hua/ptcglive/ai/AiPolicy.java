package dev.hua.ptcglive.ai;

import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Bounded heuristic decision maker. It cannot access a live match, hidden zones or game RNG. */
public final class AiPolicy {
    public record Decision(long revision,int option,String category){}
    public static final int MAX_OPTIONS=2048;
    private AiPolicy(){}
    public static Optional<Decision> choose(AiView input,long tieSeed,Set<String> usedOptionalActions) {
        return choose(input,tieSeed,usedOptionalActions,AiDifficulty.HARD);
    }
    public static Optional<Decision> choose(AiView input,long tieSeed,Set<String> usedOptionalActions,AiDifficulty difficulty) {
        Objects.requireNonNull(difficulty);
        var context=new Context(input,usedOptionalActions);var random=new SplittableRandom(tieSeed);
        var plans=difficulty.states==0?Map.<Integer,AiPlanner.Path>of():AiPlanner.plan(context,difficulty);
        double currentAttack=input.snapshot().options().stream().filter(o->o.enabled()&&isAttack(o))
            .mapToDouble(context::attackValue).max().orElse(0);
        double unavoidableRisk=difficulty==AiDifficulty.MASTER?input.snapshot().options().stream().filter(o->o.enabled()&&isAttack(o))
            .mapToDouble(context::counterattackRisk).min().orElse(0):0;
        MatchSession.Option best=null;double score=-Double.MAX_VALUE;int count=0;
        for(var option:input.snapshot().options()) {
            if(!option.enabled()||option.type()==ActionType.CONCEDE)continue;
            if(++count>MAX_OPTIONS&&option.type()!=ActionType.END_TURN)continue;
            double base=context.score(option);
            double value=base<0?base:Math.max(base,plans.getOrDefault(option.index(),AiPlanner.Path.NONE).value());
            // A useful, non-destructive setup move preserves the attack for later this turn.
            // A verified winning attack takes precedence over all preparation.
            if(difficulty.states>0&&currentAttack>0&&currentAttack<10000&&base>0&&context.safePreparation(option))
                value=Math.max(value,currentAttack+Math.min(80,base*.2));
            if(difficulty==AiDifficulty.EASY&&base>=0&&base<10000&&input.snapshot().game().phase()==MatchPhase.TURN&&!input.snapshot().game().resolvingCardEffect()) {
                // Simple, repeatable priorities; compulsory selections retain the legal-choice policy.
                if(isAttack(option))value=300;
                else if(option.type()==ActionType.ATTACH)value=200;
                else if(option.type()==ActionType.EVOLVE)value=180;
                else if(option.type()==ActionType.TRAINER)value=Math.min(120,base);
                else if(option.type()==ActionType.RETREAT)value=20;
            }
            if(difficulty==AiDifficulty.MASTER&&isAttack(option)&&value<10000)value=Math.max(10,value-Math.max(0,context.counterattackRisk(option)-unavoidableRisk));
            value+=random.nextDouble()*.001;
            if(value>score){score=value;best=option;}
        }
        if(best==null)return Optional.empty();
        if(score<0)throw new IllegalStateException("AI 无法完成当前必需选择："+context.game.phase());
        return Optional.of(new Decision(input.snapshot().revision(),best.index(),category(best)));
    }
    private static boolean isAttack(MatchSession.Option o){return o.type()==ActionType.ATTACK;}
    public static String optionalKey(MatchSession.Option option) {
        return switch(option.type()){
            case ABILITY,DISCARD_ABILITY,STADIUM,RETREAT -> option.type()+":"+option.commandId()+option.cardKeys();
            default -> "";
        };
    }
    private static String category(MatchSession.Option o) {
        return switch(o.type()){
            case ATTACK -> "攻击";case ATTACH -> "附能";case EVOLVE -> "进化";
            case ABILITY,DISCARD_ABILITY -> "特性";case TRAINER,STADIUM,TOOL_ABILITY -> "训练家";
            case REPLACEMENT -> "替补";case END_TURN -> "结束回合";
            case PRIZE_PICK -> "领奖";case ENERGY_ASSIGN,ENERGY_TARGET -> "附能";default -> "开局或选择";
        };
    }
    static final class Context {
        final AiView input;final MatchClientView game;final UUID seat;
        final MatchClientView.Player own,opponent;final Map<String,CardInstance> instances=new HashMap<>();
        final Map<String,BattlefieldState.PokemonView> field=new HashMap<>();final Set<String> used;
        final Map<UUID,Double> readyCache=new HashMap<>();final Map<String,Double> energyCache=new HashMap<>();
        Context(AiView input,Set<String> used){
            this.input=input;this.used=used;game=input.snapshot().game();seat=game.viewingSeat();
            own=game.players().stream().filter(p->p.id().equals(seat)).findFirst().orElseThrow();
            opponent=game.players().stream().filter(p->!p.id().equals(seat)).findFirst().orElseThrow();
            game.hand().forEach(c->instances.put("hand:"+c.instanceId(),c));
            if(game.effectSelection()!=null)game.effectSelection().visibleCards().forEach(c->instances.put("effect:"+c.instanceId(),c));
            if(game.effectAttachment()!=null)game.effectAttachment().cards().forEach(c->instances.put("effect-source:"+c.instanceId(),c));
            if(game.toolOverflow()!=null)game.toolOverflow().tools().forEach(c->instances.put("tool-overflow:"+c.instanceId(),c));
            for(var p:game.players()) {p.discard().forEach(c->instances.put("discard:"+c.instanceId(),c));
                if(p.active()!=null)field.put("field:"+p.active().pokemonId(),p.active());
                p.bench().forEach(m->field.put("field:"+m.pokemonId(),m));}
        }
        AiView.Card facts(String id){return input.cards().get(id);}
        List<BattlefieldState.PokemonView> ours(){var list=new ArrayList<>(own.bench());if(own.active()!=null)list.addFirst(own.active());return list;}
        CardInstance source(MatchSession.Option o){return o.cardKeys().stream().map(instances::get).filter(Objects::nonNull).findFirst().orElse(null);}
        BattlefieldState.PokemonView target(MatchSession.Option o){return o.cardKeys().stream().map(field::get).filter(Objects::nonNull).reduce((a,b)->b).orElse(null);}
        boolean ours(BattlefieldState.PokemonView mon){return mon!=null&&ours().stream().anyMatch(p->p.pokemonId().equals(mon.pokemonId()));}
        boolean opposingResource(CardInstance card) {
            if(game.hand().contains(card)||own.discard().contains(card))return false;
            for(var mon:field.values())if(mon.attachedEnergyIds().contains(card.instanceId())||mon.attachedTools().contains(card))return !ours(mon);
            return !card.ownerId().equals(seat);
        }
        boolean safePreparation(MatchSession.Option o){
            if(game.phase()!=MatchPhase.TURN||game.resolvingCardEffect()||!seat.equals(game.currentPlayer()))return false;
            var card=source(o);var mon=target(o);
            // Start conservatively: basic energy and bench evolution have no printed triggers.
            if(card==null)return false;var f=facts(card.definitionId());if(f==null)return false;
            if(o.type()==ActionType.ATTACH)return f.definition().basicEnergy()&&mon!=null&&ours(mon)
                &&plainPokemon(mon)&&f.energy()!=null&&f.energy().automaticEffects().isEmpty();
            if(o.type()==ActionType.EVOLVE)return mon!=null&&own.active()!=null&&!mon.pokemonId().equals(own.active().pokemonId())
                &&plainPokemon(mon)&&f.pokemon()!=null&&f.pokemon().abilities().isEmpty();
            return false;
        }
        boolean plainPokemon(BattlefieldState.PokemonView mon){var f=facts(mon.topCardId());return f!=null&&f.pokemon()!=null&&f.pokemon().abilities().isEmpty();}
        private static boolean copiesOpponentHandSupporter(EffectDefinition effect) {
            return effect.type().equals("use_opponent_hand_supporter")||effect.effects().stream().anyMatch(Context::copiesOpponentHandSupporter);
        }
        double score(MatchSession.Option o) {
            String s=o.labelZh();var card=source(o);var mon=target(o);
            if(game.effectSelection()!=null) {
                var p=game.effectSelection();String label=p.labelZh();boolean discard=label.contains("弃置")||label.contains("放逐")||label.contains("支付");
                if(o.type()==ActionType.CARD_DESELECT)return -1000;
                // Retreat shares the card selector but has its own confirmation label.
                // Only enabled server options reach scoring, so unit-based payment is already valid.
                if(o.type()==ActionType.RETREAT_CONFIRM)return 10000;
                if(o.type()==ActionType.CARD_CONFIRM)return discard||p.selected().size()>=p.maximum()?10000:25;
                if(o.type()==ActionType.CARD_SELECT&&card!=null) {
                    // Another copy of this revealed-hand effect only reopens the same optional choice.
                    if(label.contains("已公开手牌中的支援者效果")) {
                        var f=facts(card.definitionId());
                        if(f!=null&&f.trainer()!=null&&f.trainer().effectProgram().triggers().stream()
                            .flatMap(t->t.effects().stream()).anyMatch(Context::copiesOpponentHandSupporter))return 0;
                    }
                    double value=cardValue(card.definitionId());
                    if(discard)return 1000+(opposingResource(card)?value:-value); // Only the engine can decide whether units/typed payment is complete.
                    if(label.contains("选择顺序")&&!card.ownerId().equals(seat))value=150-value;
                    return p.selected().size()<p.minimum()?500+value:value;
                }
            }
            if(game.damageAllocation()!=null) {
                var p=game.damageAllocation();
                if(o.type()==ActionType.DAMAGE_CONFIRM)return p.optional()&&p.allocated()<p.budget()?0:10000;
                if(o.cardKeys().contains("counter:remove"))return -1000;
                if(o.cardKeys().contains("counter:add")&&mon!=null) {
                    int assigned=p.counters().getOrDefault(mon.pokemonId(),0);
                    int left=mon.hitPoints()-mon.damage()-assigned*10;
                    if(ours(mon)) {
                        // Taking counters away helps an endangered source; placing them should preserve our Pokémon.
                        double value=p.optional()?100+10000.0/Math.max(10,mon.hitPoints()-mon.damage()):left>10?100+Math.min(1000,left)*.1:1;
                        return p.counters().size()<p.minimumTargets()&&assigned>0?0:value;
                    }
                    if(p.counters().size()<p.minimumTargets())return assigned==0?2000:10;
                    return (ours(mon)?50:500)+(left>0?1000.0/left:-200);
                }
            }
            if(game.effectAttachment()!=null) {
                var p=game.effectAttachment();
                if(o.type()==ActionType.ENERGY_CONFIRM)return 10000;
                if(o.type()==ActionType.ENERGY_ASSIGN) {
                    if(card==null||p.assignedTargets().containsKey(card.instanceId()))return -1000;
                    long alternatives=input.snapshot().options().stream().filter(a->a.cardKeys().contains("effect-source:"+card.instanceId())).count();
                    return 600+100.0/Math.max(1,alternatives)+energyGain(card,mon);
                }
                if(o.type()==ActionType.ENERGY_TARGET&&mon!=null)return 600+energyGain(card,mon);
            }
            if(o.type()==ActionType.ACK_REVEAL||o.type()==ActionType.ACK_MULLIGAN)return 10000;
            if(o.type()==ActionType.TARGET_FINISH)return 20;
            if(o.type()==ActionType.TARGET_SELECT&&mon!=null) {
                String detail=s;boolean healing=detail.contains("回复")||detail.contains("治疗");
                if(ours(mon))return 200+(healing?mon.damage():readiness(mon))+Math.max(0,mon.hitPoints()-mon.damage())*.05;
                return 200+1000.0/Math.max(10,mon.hitPoints()-mon.damage());
            }
            if(o.type()==ActionType.TRIGGER_USE) {
                var ability=pendingTriggeredAbility();
                if(ability!=null&&ability.endsTurn()&&preserveAttackAfterTrigger())return -10;
                return own.deckSize()<=4&&s.contains("抽")?-10:200;
            }
            if(o.type()==ActionType.TRIGGER_SKIP) {
                var ability=pendingTriggeredAbility();
                return ability!=null&&ability.endsTurn()?10:own.deckSize()<=4?300:10;
            }
            if(o.type()==ActionType.TRIGGER_ORDER)return 100+(s.contains("抽")&&own.deckSize()>4?20:0)+(s.contains("伤害")?30:0);
            if(o.type()==ActionType.PRIZE_PICK||o.type()==ActionType.PRIZE_VIEW||o.type()==ActionType.PRIZE_REVEAL)return 500; // Position only; tie RNG is independent.
            if(o.type()==ActionType.PRIZE_CONFIRM)return 500;
            if(o.type()==ActionType.PRIZE_SELECT)return 100;
            if(o.type()==ActionType.PRIZE_DESELECT)return -1000;
            if(o.type()==ActionType.SWAP_SKIP||o.type()==ActionType.COPY_SKIP)return 40;
            if(o.type()==ActionType.PRIZE_SWAP)return 60;
            if(o.type()==ActionType.COIN_HEADS)return coinOutcomeValue(true);
            if(o.type()==ActionType.COIN_TAILS)return coinOutcomeValue(false);
            if(o.type()==ActionType.CHOICE) {
                if(s.contains("不")||s.contains("跳过"))return 25;
                if(s.contains("抽")&&own.deckSize()<=4)return 10;
                return 60+(s.contains("附")?30:0)+(s.contains("检索")?20:0)+(s.contains("伤害")?40:0);
            }
            if(o.type()==ActionType.OVERFLOW)return 200-(card==null?0:cardValue(card.definitionId()));
            if(o.type()==ActionType.COIN)return 100;
            if(o.type()==ActionType.GO_FIRST)return hasEvolutionInHand()?120:90;
            if(o.type()==ActionType.GO_SECOND)return hasEvolutionInHand()?90:120;
            if(o.type()==ActionType.MULLIGAN_DRAW){int amount=parseFirstInt(o.commandId().isEmpty()?s:o.commandId());return amount>Math.max(0,own.deckSize()-8)?-10:100+amount;}
            if(o.type()==ActionType.OPEN_ACTIVE)return 500+(card==null?0:openingValue(card.definitionId()));
            if(o.type()==ActionType.BENCH)return benchValue(card);
            if(o.type()==ActionType.OPEN_CONFIRM||o.type()==ActionType.BENCH_CONFIRM)return 50;
            if(o.type()==ActionType.REPLACEMENT&&mon!=null)return 500+readiness(mon)+Math.max(0,mon.hitPoints()-mon.damage())*.1;
            if(o.type()==ActionType.LUCKY_SKIP)return 60;
            if(o.type()==ActionType.LUCKY_USE)return own.bench().size()<3&&own.deckSize()>6?100:40;
            if(o.type()==ActionType.END_TURN)return 0;
            if(isAttack(o))return attackValue(o);
            if(o.type()==ActionType.ATTACH)return energyGain(card,mon);
            if(o.type()==ActionType.EVOLVE&&card!=null&&mon!=null) {
                var p=facts(card.definitionId()).pokemon();double gain=openingValue(card.definitionId())-openingValue(mon.topCardId());
                var e=input.energy().get(mon.pokemonId());var units=e==null?List.<EnergyProfile.Unit>of():e.units();
                boolean readyNow=e!=null&&e.payable().values().stream().anyMatch(Boolean::booleanValue);
                boolean readyAfter=p!=null&&p.attacks().stream().anyMatch(a->missing(units,a.energyCost())==0);
                double support=p==null?0:p.abilities().stream().filter(a->a.kind()!=AbilityDefinition.Kind.PASSIVE).map(AiEffects::ability)
                    .mapToDouble(a->a.has(AiEffects.Role.ATTACH)&&hasEnergyNeed()?55:a.has(AiEffects.Role.DRAW)&&game.hand().size()<5&&own.deckSize()>5?35:0).max().orElse(0);
                return 100+gain+support+(p==null?0:p.hitPoints()-mon.hitPoints())*.25-(readyNow&&!readyAfter?180:0);
            }
            if(o.type()==ActionType.RETREAT) {
                if(used.contains(optionalKey(o))||own.active()==null||mon==null)return -10;
                double gain=readiness(mon)-readiness(own.active());
                double cost=input.turn().retreatCost()*22;
                return gain>35+cost?110+gain-cost:threat(own.active())>0&&readiness(mon)>20?120-cost:-20;
            }
            if(o.type()==ActionType.ABILITY||o.type()==ActionType.DISCARD_ABILITY) {
                if(used.contains(optionalKey(o)))return -10;
                var host=o.cardKeys().stream().map(field::get).filter(Objects::nonNull).findFirst().orElse(null);
                var profile=host==null?null:facts(host.topCardId()).pokemon();
                if(profile!=null)for(var a:profile.abilities())if((o.commandId().isEmpty()?s.contains(a.nameZh()):o.commandId().equals(a.id()))) {
                    var effects=AiEffects.ability(a);
                    if(a.endsTurn()&&hasAttack())return -10;
                    if(effects.has(AiEffects.Role.DRAW))return own.deckSize()>Math.max(5,effects.draw())&&game.hand().size()<(effects.drawUntil()>0?effects.drawUntil():5)?180:-10;
                    if(effects.has(AiEffects.Role.ATTACH)||effects.has(AiEffects.Role.MOVE_ENERGY))return hasEnergyNeed()?185:-10;
                    if(effects.has(AiEffects.Role.HEAL))return ours().stream().mapToInt(BattlefieldState.PokemonView::damage).max().orElse(0)>0?100:-10;
                    if(effects.has(AiEffects.Role.SEARCH))return searchNeed()>70?150:-10;
                }
                return 70;
            }
            if(o.type()==ActionType.TRAINER&&card!=null) {
                var f=facts(card.definitionId());
                if(f!=null&&f.trainer()!=null&&f.trainer().directionalStadium())return directionalStadiumValue(f.trainer(),o.cardKeys().contains("stadium-first:SELF"));
                return trainerValue(card,mon,(int)o.cardKeys().stream().filter(k->k.startsWith("hand:")).count());
            }
            if(o.type()==ActionType.STADIUM||o.type()==ActionType.TOOL_ABILITY)return used.contains(optionalKey(o))?-10:40;
            if(o.type()==ActionType.FOSSIL_DISCARD||o.type()==ActionType.DOLL_RETURN) {
                if(mon==null||own.active()==null||!mon.pokemonId().equals(own.active().pokemonId())||own.bench().isEmpty())return -10;
                double gain=own.bench().stream().mapToDouble(this::readiness).max().orElse(0)-readiness(mon);
                return gain>35?110+gain:-10;
            }
            if(o.type()==ActionType.RECOVER_SUPPORTER&&card!=null) {
                double cost=o.cardKeys().stream().filter(k->k.startsWith("hand:")).map(instances::get).filter(Objects::nonNull).mapToDouble(c->cardValue(c.definitionId())).sum();
                return cardValue(card.definitionId())-cost;
            }
            return -100;
        }
        boolean hasAttack(){return input.snapshot().options().stream().anyMatch(o->o.enabled()&&isAttack(o));}
        AbilityDefinition pendingTriggeredAbility() {
            var prompt=game.triggerOrder();if(prompt==null||!prompt.optionalDecision()||prompt.entries().size()!=1)return null;
            var entry=prompt.entries().getFirst();if(entry.sourceKind()!=EffectOrigin.Kind.ABILITY)return null;
            var mon=field.get("field:"+entry.pokemonId());var f=mon==null?null:facts(mon.topCardId());
            return f==null||f.pokemon()==null?null:f.pokemon().abilities().stream()
                .filter(a->a.triggered()&&a.nameZh().equals(entry.nameZh())).findFirst().orElse(null);
        }
        /** Pending trigger menus hide attack actions; estimate their opportunity cost from public field/payment facts. */
        boolean preserveAttackAfterTrigger() {
            if(!seat.equals(game.currentPlayer())||game.turnNumber()<=1||own.active()==null||opponent.active()==null
                ||own.active().conditions().contains(SpecialCondition.ASLEEP)||own.active().conditions().contains(SpecialCondition.PARALYZED))return false;
            var energy=input.energy().get(own.active().pokemonId());if(energy==null)return false;
            return attackPlans(own.active()).stream().anyMatch(p->Boolean.TRUE.equals(energy.payable().get(p.attack().id()))
                &&estimatedDamage(p.attack(),opponent.active())>=opponent.active().hitPoints()-opponent.active().damage()
                &&p.attack().selfDamage()<own.active().hitPoints()-own.active().damage());
        }
        boolean hasEvolutionInHand(){return game.hand().stream().map(c->facts(c.definitionId())).filter(Objects::nonNull).anyMatch(c->c.pokemon()!=null&&c.pokemon().evolvesFromDefinitionId()!=null);}
        double benchValue(CardInstance card){if(card==null)return -10;int count=own.bench().size();if(count>=3)return -10;
            long duplicates=ours().stream().filter(p->p.topCardId().equals(card.definitionId())).count();
            boolean evolution=game.hand().stream().map(c->facts(c.definitionId())).filter(Objects::nonNull).anyMatch(c->c.pokemon()!=null&&facts(card.definitionId())!=null&&facts(card.definitionId()).pokemon()!=null&&AiDeckAnalysis.evolves(facts(card.definitionId()).pokemon(),card.definitionId(),c.pokemon()));
            return (count==0?180:75)+openingValue(card.definitionId())*.1+(evolution?75:0)-duplicates*35;
        }
        double openingValue(String id){var f=facts(id);if(f==null||f.pokemon()==null)return 0;var p=f.pokemon();
            double efficient=p.attacks().stream().mapToDouble(a->expected(a)/(1+costSize(a.energyCost()))).max().orElse(0);
            return p.hitPoints()*.2+efficient*2;
        }
        double readiness(BattlefieldState.PokemonView mon){return readyCache.computeIfAbsent(mon.pokemonId(),id->calculateReadiness(mon));}
        record AttackPlan(AttackDefinition attack,Map<EnergyType,Integer> cost){}
        List<AttackPlan> attackPlans(BattlefieldState.PokemonView mon) {
            var host=facts(mon.topCardId());if(host==null||host.pokemon()==null)return List.of();
            var state=input.energy().get(mon.pokemonId());var result=new ArrayList<AttackPlan>();
            for(var a:host.pokemon().attacks())result.add(new AttackPlan(a,state==null?a.energyCost():state.costs().getOrDefault(a.id(),a.energyCost())));
            if(state!=null)state.costs().forEach((key,cost)->{
                if(!key.startsWith("borrow|"))return;var parts=key.split("\\|",3);if(parts.length!=3)return;
                var provider=facts(parts[1]);if(provider==null||provider.pokemon()==null)return;
                provider.pokemon().attack(parts[2]).ifPresent(a->result.add(new AttackPlan(a,cost)));
            });
            return result;
        }
        double calculateReadiness(BattlefieldState.PokemonView mon){var f=facts(mon.topCardId());if(f==null||f.pokemon()==null)return 0;
            var state=input.energy().get(mon.pokemonId());return attackPlans(mon).stream().mapToDouble(a->{
                int missing=missing(state==null?List.of():state.units(),a.cost());
                return AiDamageEstimate.estimate(this,a.attack(),mon,opponent.active(),Map.of())/(1+missing*2)+(missing==0?65:0);
            }).max().orElse(0);
        }
        double energyGain(CardInstance card,BattlefieldState.PokemonView mon){
            if(card==null||mon==null||!ours(mon))return -10;
            return energyGain(card.definitionId(),mon);
        }
        double energyGain(String id,BattlefieldState.PokemonView mon){return energyCache.computeIfAbsent(id+mon.pokemonId(),key->calculateEnergyGain(id,mon));}
        double calculateEnergyGain(String id,BattlefieldState.PokemonView mon){var f=facts(id);var host=facts(mon.topCardId());
            if(f==null||f.energy()==null||host==null||host.pokemon()==null)return 10;
            var before=input.energy().get(mon.pokemonId());var units=new ArrayList<EnergyProfile.Unit>(before==null?List.of():before.units());
            // Prospective dynamic Special Energy is only a strategy estimate, never a legality/payment decision.
            var additions=f.energy().units();var plus=new ArrayList<>(units);plus.addAll(additions);double best=-20;
            for(var plan:attackPlans(mon)) {
                var a=plan.attack();var cost=plan.cost();int old=missing(units,cost);
                int now=missing(plus,cost);
                if(now<old)best=Math.max(best,60+(now==0?100:0)+expected(a)*.12+(own.active()!=null&&own.active().pokemonId().equals(mon.pokemonId())?25:0));
                if(now==0&&opponent.active()!=null){
                    double remaining=Math.max(0,opponent.active().hitPoints()-opponent.active().damage());
                    double current=AiDamageEstimate.estimate(this,a,mon,opponent.active(),Map.of(mon.pokemonId(),units));
                    double prepared=AiDamageEstimate.estimate(this,a,mon,opponent.active(),Map.of(mon.pokemonId(),plus));
                    double gain=Math.min(remaining,prepared)-Math.min(remaining,current);
                    if(gain>0)best=Math.max(best,60+gain*.5);
                }
            }
            // A ready pre-evolution must still prepare energy for an evolution actually in our hand.
            // Only visible cards are considered; this does not inspect the deck or assert future legality.
            for(var card:game.hand()) {
                var next=facts(card.definitionId());
                if(next==null||next.pokemon()==null||!AiDeckAnalysis.evolves(host.pokemon(),mon.topCardId(),next.pokemon()))continue;
                for(var attack:next.pokemon().attacks()) {
                    int old=missing(units,attack.energyCost()),now=missing(plus,attack.energyCost());
                    if(now<old)best=Math.max(best,55+(now==0?100:0)+expected(attack)*.12);
                }
            }
            return best;
        }
        boolean hasEnergyNeed(){return ours().stream().anyMatch(m->{var e=input.energy().get(m.pokemonId());return attackPlans(m).stream().anyMatch(a->missing(e==null?List.of():e.units(),a.cost())>0);});}
        double cardValue(String id){var f=facts(id);if(f==null)return 25;
            long copies=game.hand().stream().filter(c->c.definitionId().equals(id)).count();
            if(f.energy()!=null){double gain=ours().stream().mapToDouble(m->energyGain(id,m)).max().orElse(0);return gain>0?Math.max(30,80+gain*.3-copies*20):15;}
            if(f.pokemon()!=null){if(!f.definition().basicPokemon())return ours().stream().anyMatch(m->facts(m.topCardId())!=null&&facts(m.topCardId()).pokemon()!=null&&AiDeckAnalysis.evolves(facts(m.topCardId()).pokemon(),m.topCardId(),f.pokemon()))?190-copies*20:45-copies*10;
                return (ours().size()<2?180:65)-copies*20+Math.min(35,AiDeckAnalysis.attackValue(f.pokemon())*.2);}
            if(f.trainer()!=null){var effects=AiEffects.trainer(f.trainer());if(effects.has(AiEffects.Role.DRAW))return own.deckSize()>5&&game.hand().size()<5?90:20;return 45-copies*10;}
            return 25;
        }
        double searchNeed(){return input.deck().initialCards().keySet().stream().mapToDouble(this::cardValue).max().orElse(hasEnergyNeed()?130:70);}
        // Only printed public threats are used. The opponent's held energy/attacks are unknown.
        double threat(BattlefieldState.PokemonView mon){if(opponent.active()==null)return 0;var f=facts(opponent.active().topCardId());if(f==null||f.pokemon()==null)return 0;
            int visible=opponent.active().attachedEnergyIds().size();double damage=f.pokemon().attacks().stream().filter(a->costSize(a.energyCost())<=visible+1).mapToDouble(Context::expected).max().orElse(0);
            return Math.max(0,damage-(mon.hitPoints()-mon.damage()));}
        double trainerValue(CardInstance card,BattlefieldState.PokemonView target,int cards){var f=facts(card.definitionId());if(f==null||f.trainer()==null)return -10;var p=f.trainer();var effects=AiEffects.trainer(p,cards);
            if(effects.has(AiEffects.Role.COIN_CONTROL))return coinControlValue();
            if(effects.has(AiEffects.Role.PRIZE))return own.prizeCount()<=1?100000:500;
            if(cards==1&&p.pairedPlay()!=null&&p.pairedPlay().count()==4&&AiEffects.trainer(p,4).has(AiEffects.Role.PRIZE))return -10;
            if(effects.handCost()>0){
                var payments=game.hand().stream().filter(c->!c.instanceId().equals(card.instanceId())).mapToDouble(c->cardValue(c.definitionId())).sorted().toArray();
                if(payments.length<effects.handCost()||payments[effects.handCost()-1]>140)return -10;
            }
            if(effects.has(AiEffects.Role.GUST))return target==null?opponent.bench().stream().mapToDouble(this::gustValue).max().orElse(-10):gustValue(target);
            if(p.category()==TrainerCategory.TOOL) {
                if(target==null)return -10;
                if(p.opponentLegacyExTool()) {
                    var host=facts(target.topCardId());
                    double attack=host==null||host.pokemon()==null?0:host.pokemon().attacks().stream().mapToDouble(Context::expected).max().orElse(0);
                    return 90+(opponent.active()!=null&&target.pokemonId().equals(opponent.active().pokemonId())?65:0)
                        +Math.min(45,attack*.15)+Math.min(30,target.attachedEnergyIds().size()*10);
                }
                return 70+readiness(target)*.1;
            }
            if(effects.has(AiEffects.Role.SWITCH)){if(target==null||own.active()==null)return -10;return readiness(target)>readiness(own.active())+30||threat(own.active())>0&&readiness(target)>20?160:-10;}
            if(effects.has(AiEffects.Role.HEAL)){var patient=target!=null?target:own.active();return patient!=null&&(patient.damage()>=20||!patient.conditions().isEmpty())?100:-10;}
            if(effects.has(AiEffects.Role.DRAW)) {
                int amount=Math.max(1,effects.draw());if(own.deckSize()<=Math.max(5,amount))return -10;
                if(effects.resetHand()&&game.hand().stream().filter(c->!c.instanceId().equals(card.instanceId())).anyMatch(c->cardValue(c.definitionId())>150))return -10;
                if(effects.drawUntil()>0&&game.hand().size()-1>=effects.drawUntil())return -10;
                return game.hand().size()<5?145:game.hand().size()<7?30:-10;
            }
            if(effects.has(AiEffects.Role.MOVE_ENERGY))return hasEnergyNeed()&&ours().stream().anyMatch(m->m.attachedEnergyIds().size()>0)?110:-10;
            if(effects.has(AiEffects.Role.ATTACH))return hasEnergyNeed()?170:-10;
            if(effects.has(AiEffects.Role.RECOVER))return own.discard().stream().mapToDouble(c->cardValue(c.definitionId())).max().orElse(0)>70?125:-10;
            if(effects.has(AiEffects.Role.SEARCH))return own.deckSize()>2&&searchNeed()>70?135:-10;
            if(effects.has(AiEffects.Role.BOOST))return hasAttack()?65:-10;
            if(p.category()==TrainerCategory.STADIUM)return 25;
            return 40;
        }
        /** Compare an unambiguous printed coin branch using only this recipient's public view. */
        double coinOutcomeValue(boolean heads) {
            var effects=new ArrayList<EffectDefinition>();
            if(game.resolvingAttack()!=null) {
                var source=field.get("field:"+game.resolvingAttack().pokemonId());
                var card=source==null?null:facts(source.topCardId());
                var attack=card==null||card.pokemon()==null?null:card.pokemon().attacks().stream()
                    .filter(a->a.id().equals(game.resolvingAttack().attackId())).findFirst().orElse(null);
                // Native damage/protection coins may precede the printed continuation's branch.
                if(attack!=null&&attack.coinDamage()==null&&attack.coinProtection()==null
                    &&attack.conditions().stream().noneMatch(c->c.coinCondition()!=null))
                    attack.effectProgram().forTrigger(game.resolvingAttack().phase()).forEach(t->effects.addAll(t.effects()));
            } else if(game.resolvingTrainer()!=null) {
                var card=facts(game.resolvingTrainer().definitionId());
                if(card!=null&&card.trainer()!=null)card.trainer().effectProgram().triggers().forEach(t->effects.addAll(t.effects()));
            } else if(game.resolvingAbility()!=null) {
                var source=field.get("field:"+game.resolvingAbility().pokemonId());
                var card=source==null?null:facts(source.topCardId());
                if(card!=null&&card.pokemon()!=null)card.pokemon().abilities().stream()
                    .filter(a->a.id().equals(game.resolvingAbility().abilityId()))
                    .forEach(a->a.effectProgram().triggers().forEach(t->effects.addAll(t.effects())));
            }
            var coins=new ArrayList<EffectDefinition>();effects.forEach(e->collectCoinBranches(e,coins));
            if(coins.size()!=1||!"1".equals(coins.getFirst().arguments().get("flips")))return heads?200:100;
            double value=coins.getFirst().effects().stream().mapToDouble(e->coinBranchValue(e,heads)).sum();
            return 1000+Math.max(-900,Math.min(900,value))+(heads?.01:0);
        }
        private static void collectCoinBranches(EffectDefinition effect,List<EffectDefinition> coins) {
            if(effect.type().equals("coin_flip"))coins.add(effect);
            effect.effects().forEach(e->collectCoinBranches(e,coins));
        }
        double coinBranchValue(EffectDefinition effect,boolean heads) {
            for(var condition:effect.conditions()) {
                if(condition.type()!=ConditionType.COIN_RESULT)return 0; // Do not guess hidden conditions.
                String side=condition.arguments().get("value");
                if(!Set.of("HEADS","TAILS").contains(side)||heads!=side.equals("HEADS"))return 0;
            }
            if(effect.type().equals("repeat_per_heads")&&!heads)return 0;
            boolean other=effect.target().name().contains("OPPONENT");
            double sign=other?-1:1;int amount;
            try{amount=Integer.parseInt(effect.arguments().getOrDefault("amount","0"));}catch(NumberFormatException ignored){amount=0;}
            var player=other?opponent:own;var active=player.active();
            double value=switch(effect.type()) {
                case "draw" -> sign*(Math.min(amount,player.deckSize())*15-(amount>0&&player.deckSize()<=amount?500:0));
                case "search_deck","search_discard_pile" -> sign*Math.max(1,amount)*50;
                case "heal" -> sign*(active==null?0:Math.min(amount,active.damage()));
                case "paralyze" -> -sign*(active==null||active.conditions().contains(SpecialCondition.PARALYZED)?0:120);
                case "confuse" -> -sign*(active==null||active.conditions().contains(SpecialCondition.CONFUSED)?0:45);
                case "sleep" -> -sign*(active==null||active.conditions().contains(SpecialCondition.ASLEEP)?0:60);
                case "poison","burn" -> -sign*35;
                case "damage","self_damage","damage_counter" -> {
                    int damage=amount*(effect.type().equals("damage_counter")?10:1);
                    yield -sign*(damage+(active!=null&&damage>=active.hitPoints()-active.damage()?400:0));
                }
                case "discard_energy","discard","discard_hand","prevent_attack" -> -sign*80;
                default -> 0;
            };
            return value+effect.effects().stream().mapToDouble(e->coinBranchValue(e,heads)).sum();
        }
        double coinControlValue() {
            double value=-10;
            for(var option:input.snapshot().options())if(option.enabled()) {
                if(isAttack(option)) {
                    var host=option.cardKeys().stream().map(field::get).filter(Objects::nonNull).findFirst().orElse(own.active());
                    var f=host==null?null:facts(host.topCardId());if(f==null||f.pokemon()==null)continue;
                    var attack=f.pokemon().attacks().stream().filter(a->a.id().equals(option.commandId())).findFirst().orElse(null);
                    if(attack!=null&&(attack.coinDamage()!=null||attack.coinProtection()!=null||attack.conditions().stream().anyMatch(c->c.coinCondition()!=null)||hasCoinInstruction(attack.effectProgram()))) {
                        double score=attackValue(option);if(score<10000)value=Math.max(value,score+30);
                    }
                } else if(option.type()==ActionType.ABILITY) {
                    var host=option.cardKeys().stream().map(field::get).filter(Objects::nonNull).findFirst().orElse(null);
                    var f=host==null?null:facts(host.topCardId());if(f!=null&&f.pokemon()!=null&&f.pokemon().abilities().stream()
                        .anyMatch(a->a.id().equals(option.commandId())&&hasCoinInstruction(a.effectProgram())))value=Math.max(value,score(option)+25);
                } else if(option.type()==ActionType.TRAINER) {
                    var card=source(option);var f=card==null?null:facts(card.definitionId());
                    if(f!=null&&f.trainer()!=null&&f.trainer().category()==TrainerCategory.ITEM&&hasCoinInstruction(f.trainer().effectProgram()))
                        value=Math.max(value,score(option)+25);
                }
            }
            return value;
        }
        private static boolean hasCoinInstruction(dev.hua.ptcglive.engine.effect.EffectProgram program) {
            return program.triggers().stream().flatMap(b->b.effects().stream()).anyMatch(Context::hasCoinInstruction);
        }
        private static boolean hasCoinInstruction(dev.hua.ptcglive.engine.effect.EffectDefinition effect) {
            return Set.of("coin_flip","coin_copy_attack","per_target_coin_damage").contains(effect.type())
                ||effect.effects().stream().anyMatch(Context::hasCoinInstruction);
        }
        /** Only public board types, conditions and bench size influence direction. */
        double directionalStadiumValue(TrainerProfile profile,boolean ownFirst) {
            double value=25;
            for(var binding:profile.effectProgram().forTrigger(dev.hua.ptcglive.engine.effect.EffectTrigger.PASSIVE))for(var effect:binding.effects()) {
                boolean first=effect.target()==dev.hua.ptcglive.engine.effect.EffectTarget.ALL_OWN_POKEMON||effect.target()==dev.hua.ptcglive.engine.effect.EffectTarget.OWN_BENCH;
                boolean ours=first==ownFirst;var side=ours?own:opponent;double sign=ours?1:-1;
                if(effect.type().equals("bench_limit")) {value-=sign*(Math.max(0,side.bench().size()-3)*100+Math.min(3,side.bench().size())*5);continue;}
                var mons=new ArrayList<>(side.bench());if(side.active()!=null)mons.addFirst(side.active());
                if(effect.type().equals("special_condition_immunity")) {
                    var immune=dev.hua.ptcglive.engine.effect.PassiveRuleProgram.immuneConditions(effect);
                    for(var mon:mons)value+=sign*mon.conditions().stream().filter(immune::contains).count()*100;
                } else if(effect.type().equals("attack_damage_modifier"))for(var mon:mons) {
                    var facts=facts(mon.topCardId());if(facts==null)continue;
                    var filter=effect.conditions().getFirst();
                    var predicates=filter.children().isEmpty()?List.of(filter):filter.children();
                    var types=predicates.stream().map(c->EnergyType.valueOf(c.arguments().get("pokemon_type"))).toList();
                    if(facts.definition().pokemonTypes().stream().noneMatch(types::contains))continue;
                    int amount=Integer.parseInt(effect.arguments().get("amount"));
                    value+=sign*(effect.arguments().get("direction").equals("RECEIVED")?-amount:amount)*(mon==side.active()?3:1);
                }
            }
            return value;
        }
        double attackValue(MatchSession.Option option){
            var source=option.cardKeys().stream().map(field::get).filter(Objects::nonNull).findFirst().orElse(own.active());
            if(source==null)return 5;var f=facts(source.topCardId());if(f==null||f.pokemon()==null)return 5;
            var attack=f.pokemon().attacks().stream().filter(a->(option.commandId().isEmpty()?option.labelZh().contains(a.nameZh()):option.commandId().equals(a.id()))).findFirst().orElse(null);
            String copiedId=option.cardKeys().stream().filter(k->k.startsWith("copy-source:")).map(k->k.substring(12)).findFirst().orElse(null);
            String copiedAttack=option.cardKeys().stream().filter(k->k.startsWith("copy-attack:")).map(k->k.substring(12)).findFirst().orElse(null);
            if(copiedId!=null) {
                var copied=facts(copiedId);
                if(copied!=null&&copied.pokemon()!=null)attack=copied.pokemon().attacks().stream().filter(a->a.id().equals(copiedAttack)).findFirst().orElse(null);
            }
            var defender=target(option);if(defender==null||ours(defender))defender=opponent.active();
            if(attack==null)return 30;double value=estimatedDamage(attack,source,defender);boolean exact=false;
            for(var p:input.snapshot().guidance().attacks())if(copiedId==null&&defender==opponent.active()&&p.sourceKey().equals("field:"+source.pokemonId())&&p.name().equals(attack.nameZh())&&p.damage()!=null&&p.certainty().equals("确定伤害")){value=p.damage();exact=true;}
            double usefulDamage=defender==null?value:Math.min(value,Math.max(0,defender.hitPoints()-defender.damage()));
            double result=100+usefulDamage*.5-attack.selfDamage()*.25-attack.discardSelectedAttachedEnergyAfterAttack()*18;
            if(attack.discardAllAttachedEnergyAfterAttack())result-=source.attachedEnergyIds().size()*12;
            if(opponent.active()!=null&&exact&&value>=opponent.active().hitPoints()-opponent.active().damage()) {
                result+=500;var defenderFacts=facts(opponent.active().topCardId());
                if(opponent.bench().isEmpty()||defenderFacts!=null&&defenderFacts.pokemon()!=null&&own.prizeCount()<=defenderFacts.pokemon().prizeCards())result+=10000;
            }
            return result;
        }
        double gustValue(BattlefieldState.PokemonView target){
            if(target==null||ours(target)||opponent.active()==null||own.active()==null||!input.turn().mayAttack())return -10;
            if(target.pokemonId().equals(opponent.active().pokemonId()))return -10;
            var payable=attackPlans(own.active()).stream().filter(p->{var e=input.energy().get(own.active().pokemonId());return missing(e==null?List.of():e.units(),p.cost())==0;})
                .filter(p->p.attack().effectProgram().isEmpty()&&p.attack().coinDamage()==null&&p.attack().damageModifiers().isEmpty()
                    &&p.attack().useRequirement()==null&&p.attack().damageTarget()==AttackDefinition.DamageTarget.OPPONENT_ACTIVE).toList();
            double damage=payable.stream()
                .mapToDouble(p->estimatedDamage(p.attack(),target)).max().orElse(0);
            if(damage<=0||damage<target.hitPoints()-target.damage())return -10;
            var f=facts(target.topCardId());int prizes=f==null||f.pokemon()==null?1:f.pokemon().prizeCards();
            double current=payable.stream().mapToDouble(p->estimatedDamage(p.attack(),opponent.active())).max().orElse(0);
            var activeFacts=facts(opponent.active().topCardId());int currentPrizes=activeFacts==null||activeFacts.pokemon()==null?1:activeFacts.pokemon().prizeCards();
            if(current>=opponent.active().hitPoints()-opponent.active().damage()&&currentPrizes>=prizes)return -10;
            // Estimate only: after switching, re-read the engine's actual attack guidance.
            return 650+prizes*80+(own.prizeCount()<=prizes?1000:0);
        }
        double estimatedDamage(AttackDefinition attack,BattlefieldState.PokemonView defender){
            return estimatedDamage(attack,own.active(),defender);
        }
        double estimatedDamage(AttackDefinition attack,BattlefieldState.PokemonView source,BattlefieldState.PokemonView defender){
            double damage=AiDamageEstimate.estimate(this,attack,source,defender,Map.of());if(defender==null)return damage;var f=facts(defender.topCardId());if(f==null||f.pokemon()==null)return damage;
            var p=f.pokemon();boolean active=game.players().stream().anyMatch(player->player.active()!=null&&player.active().pokemonId().equals(defender.pokemonId()));if(active&&!attack.ignoresWeaknessAndResistance()){
                if(!attack.ignoresWeakness()&&p.weaknessType()==attack.damageType())damage*=p.weaknessMultiplier();
                if(!attack.ignoresResistance()&&p.resistanceType()==attack.damageType())damage-=p.resistanceAmount();
            }return Math.max(0,damage);
        }
        double counterattackRisk(MatchSession.Option option){
            if(own.active()==null||opponent.active()==null)return 0;
            var source=facts(own.active().topCardId());if(source==null||source.pokemon()==null)return 0;
            var attack=source.pokemon().attacks().stream().filter(a->(option.commandId().isEmpty()?option.labelZh().contains(a.nameZh()):option.commandId().equals(a.id()))).findFirst().orElse(null);
            if(attack==null)return 0;
            int remaining=own.active().hitPoints()-own.active().damage()-attack.selfDamage()+Math.min(own.active().damage(),attack.selfHealAfterAttack());
            double retaliation=0;
            var foes=new ArrayList<>(opponent.bench());foes.add(opponent.active());
            for(var mon:foes){var f=facts(mon.topCardId());if(f==null||f.pokemon()==null)continue;
                for(var a:f.pokemon().attacks())if(costSize(a.energyCost())<=mon.attachedEnergyIds().size()+1)
                    retaliation=Math.max(retaliation,estimatedDamage(a,mon,own.active())-attack.damageReductionFromOpponentNextTurn());
            }
            if(retaliation<remaining)return 0;
            int prizes=source.pokemon().prizeCards();
            return opponent.prizeCount()<=prizes?120:prizes*25;
        }
        static double expected(AttackDefinition a){return a.baseDamage()+(a.coinDamage()==null?0:a.coinDamage().flips()*a.coinDamage().perHeads()*.5);}
        static int costSize(Map<EnergyType,Integer> cost){return cost.values().stream().mapToInt(Integer::intValue).sum();}
        static int missing(List<EnergyProfile.Unit> units,Map<EnergyType,Integer> cost){
            var supply=new ArrayList<EnergyProfile>();for(var u:units)supply.add(new EnergyProfile(List.of(u)));
            for(int n=0;n<=Math.min(40,costSize(cost));n++){if(EnergyProfile.canPay(supply,cost))return n;supply.add(new EnergyProfile(List.of(new EnergyProfile.Unit(EnumSet.allOf(EnergyType.class)))));}
            return 40;
        }
        static int parseFirstInt(String s){var m=java.util.regex.Pattern.compile("\\d+").matcher(s);return m.find()?Integer.parseInt(m.group()):0;}
    }
}
