package dev.hua.ptcglive.ai;

import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Small public-facts projection, NOT a second rules engine. Only the first issued action is submitted.
 * Every real change discards the projection. Draw/search/coin/recovery are information barriers.
 * Future energy payments are pure estimates; engine options still authorize every subsequent action. */
public final class AiPlanner {
    public static final int MAX_CANDIDATES=24,MAX_STATES=384,MAX_DEPTH=4;
    public record Path(double value,List<String> steps) {
        public static final Path NONE=new Path(-Double.MAX_VALUE,List.of());
        public Path {steps=List.copyOf(steps);}
    }
    private enum Kind { ENERGY, SWITCH, BOOST, EVOLVE }
    private record Step(MatchSession.Option option,Kind kind,UUID card,UUID target,List<EnergyProfile.Unit> units,int boost,String evolution){
        Step(MatchSession.Option option,Kind kind,UUID card,UUID target,List<EnergyProfile.Unit> units,int boost){this(option,kind,card,target,units,boost,null);}
    }
    private record State(UUID active,Map<UUID,List<EnergyProfile.Unit>> units,Map<UUID,Integer> boosts,
                         Set<UUID> spent,boolean energy,boolean switched,List<String> steps,Map<UUID,String> evolved){}
    private AiPlanner(){}
    static Map<Integer,Path> plan(AiPolicy.Context c,AiDifficulty difficulty){
        if(c.game.phase()!=MatchPhase.TURN||!c.seat.equals(c.game.currentPlayer())||c.game.resolvingCardEffect()
            ||c.own.active()==null||!c.input.turn().mayAttack())return Map.of();
        var candidates=new ArrayList<Step>();
        for(var o:c.input.snapshot().options()){
            if(!o.enabled()||c.used.contains(AiPolicy.optionalKey(o)))continue;
            var source=c.source(o);var target=c.target(o);var facts=source==null?null:c.facts(source.definitionId());
            if(o.type()==ActionType.ATTACH&&facts!=null&&facts.definition().basicEnergy()&&facts.energy()!=null&&c.ours(target))
                candidates.add(new Step(o,Kind.ENERGY,source.instanceId(),target.pokemonId(),facts.energy().units(),0));
            else if(o.type()==ActionType.RETREAT&&c.input.turn().retreatCost()==0&&c.ours(target))
                candidates.add(new Step(o,Kind.SWITCH,null,target.pokemonId(),List.of(),0));
            else if(difficulty==AiDifficulty.MASTER&&o.type()==ActionType.EVOLVE&&facts!=null&&facts.pokemon()!=null
                &&facts.pokemon().abilities().isEmpty()&&c.ours(target)&&c.plainPokemon(target))
                candidates.add(new Step(o,Kind.EVOLVE,source.instanceId(),target.pokemonId(),List.of(),0,source.definitionId()));
            else if(o.type()==ActionType.TRAINER&&facts!=null&&facts.trainer()!=null&&c.ours(target)){
                var t=facts.trainer();if(t.endsTurn()||t.effect().discardHandCost()!=0)continue;
                if(t.effect().opcode()==TrainerEffect.Opcode.SWITCH_ACTIVE)
                    candidates.add(new Step(o,Kind.SWITCH,source.instanceId(),target.pokemonId(),List.of(),0));
                int boost=flatToolBoost(t);
                if(boost>0)candidates.add(new Step(o,Kind.BOOST,source.instanceId(),target.pokemonId(),List.of(),boost));
            }
        }
        // Deterministic work bounds; wall-clock cutoff would make fixed-seed decisions irreproducible.
        candidates.sort(Comparator.comparingDouble((Step s)->c.score(s.option())).reversed().thenComparingInt(s->s.option().index()));
        if(candidates.size()>difficulty.candidates)candidates.subList(difficulty.candidates,candidates.size()).clear();
        var units=new HashMap<UUID,List<EnergyProfile.Unit>>();c.input.energy().forEach((id,e)->units.put(id,e.units()));
        var initial=new State(c.own.active().pokemonId(),Map.copyOf(units),Map.of(),Set.of(),c.input.turn().energyAttached(),false,List.of(),Map.of());
        var result=new HashMap<Integer,Path>();
        // Each root gets a share: an early depth-first branch must not starve all later moves.
        for(var candidate:candidates){var next=apply(initial,candidate);if(next!=null)search(c,candidates,next,candidate.option().index(),result,new int[]{Math.max(1,difficulty.states/Math.max(1,candidates.size()))},difficulty.depth);}
        return Map.copyOf(result);
    }
    private static void search(AiPolicy.Context c,List<Step> candidates,State state,int root,Map<Integer,Path> results,int[] budget,int depth){
        if(--budget[0]<0)return;var terminal=attack(c,state);
        if(terminal.value()>results.getOrDefault(root,Path.NONE).value())results.put(root,terminal);
        if(state.steps().size()>=depth-1)return;
        for(var candidate:candidates){if(budget[0]<=0)break;var next=apply(state,candidate);if(next!=null)search(c,candidates,next,root,results,budget,depth);}
    }
    private static State apply(State s,Step step){
        if(step.card()!=null&&s.spent().contains(step.card()))return null;
        var units=new HashMap<>(s.units());var boosts=new HashMap<>(s.boosts());var evolved=new HashMap<>(s.evolved());UUID active=s.active();
        boolean energy=s.energy(),switched=s.switched();
        switch(step.kind()){
            case ENERGY -> {if(energy)return null;energy=true;var supplied=new ArrayList<>(units.getOrDefault(step.target(),List.of()));supplied.addAll(step.units());units.put(step.target(),List.copyOf(supplied));}
            case SWITCH -> {if(switched||active.equals(step.target()))return null;switched=true;active=step.target();}
            case BOOST -> {if(boosts.containsKey(step.target()))return null;boosts.put(step.target(),step.boost());}
            case EVOLVE -> {if(evolved.containsKey(step.target()))return null;evolved.put(step.target(),step.evolution());}
        }
        var spent=new HashSet<>(s.spent());if(step.card()!=null)spent.add(step.card());var steps=new ArrayList<>(s.steps());steps.add(step.option().labelZh());
        return new State(active,Map.copyOf(units),Map.copyOf(boosts),Set.copyOf(spent),energy,switched,List.copyOf(steps),Map.copyOf(evolved));
    }
    private static Path attack(AiPolicy.Context c,State s){
        var mon=c.ours().stream().filter(p->p.pokemonId().equals(s.active())).findFirst().orElse(null);
        if(mon==null)return Path.NONE;var f=c.facts(s.evolved().getOrDefault(mon.pokemonId(),mon.topCardId()));if(f==null||f.pokemon()==null)return Path.NONE;
        Path best=Path.NONE;var current=s.evolved().containsKey(mon.pokemonId())?null:c.input.energy().get(mon.pokemonId());
        for(var attack:f.pokemon().attacks()){
            // Unknown/conditional attacks do not acquire invented legality or a guaranteed knockout.
            if(attack.useRequirement()!=null||attack.usageLimit()!=null||attack.onlySecondPlayerFirstTurn()||attack.cannotAttackSecondPlayerFirstTurn()
                ||attack.damageTarget()!=AttackDefinition.DamageTarget.OPPONENT_ACTIVE
                ||!AiDamageEstimate.projectable(c,attack,mon,c.opponent.active(),s.units()))continue;
            var cost=current==null?attack.energyCost():current.costs().getOrDefault(attack.id(),attack.energyCost());
            if(AiPolicy.Context.missing(s.units().getOrDefault(s.active(),List.of()),cost)!=0)continue;
            double damage=AiDamageEstimate.estimate(c,attack,mon,c.opponent.active(),s.units())+s.boosts().getOrDefault(s.active(),0);
            double value=100+damage*.5-attack.selfDamage()*.25-attack.discardSelectedAttachedEnergyAfterAttack()*18-s.steps().size()*3;
            if(attack.discardAllAttachedEnergyAfterAttack())value-=mon.attachedEnergyIds().size()*12;
            // Only a strategy estimate. Shields, weaknesses and triggers are still resolved by the engine.
            if(c.opponent.active()!=null&&damage>=c.opponent.active().hitPoints()-c.opponent.active().damage())value+=180;
            if(value>best.value()){var steps=new ArrayList<>(s.steps());steps.add("攻击："+attack.nameZh());best=new Path(value,steps);}
        }
        return best;
    }
    private static int flatToolBoost(TrainerProfile t){
        if(t.category()!=TrainerCategory.TOOL||!t.automaticEffects().isEmpty()||t.toolActivation()!=null)return 0;
        int total=0;
        for(var b:t.ongoingProgram().triggers()){
            if(!b.conditions().isEmpty())return 0;
            for(var e:b.effects()){
                if(!e.type().equals("attack_damage_modifier")||e.target()!=EffectTarget.SELF||!e.conditions().isEmpty()||!e.effects().isEmpty()
                    ||!e.arguments().keySet().equals(Set.of("amount","direction"))||!"DEALT".equals(e.arguments().get("direction")))return 0;
                try{total+=Integer.parseInt(e.arguments().get("amount"));}catch(NumberFormatException ex){return 0;}
            }
        }return Math.max(0,total);
    }
}
