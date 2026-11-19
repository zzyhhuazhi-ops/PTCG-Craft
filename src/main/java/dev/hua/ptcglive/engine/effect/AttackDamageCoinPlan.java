package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.field.AttackDefinition;
import java.util.*;

/**
 * Compiles the pure ON_ATTACK calculation into a coin planner. It reads current
 * server facts but never applies damage, publishes coin bindings or runs atomic
 * effects. BEFORE/AFTER phases that change the field require a separate state
 * projection; this class makes no claim to plan them.
 */
public final class AttackDamageCoinPlan implements AttackCoinAttempt.Planner {
    private final List<TriggeredEffects> bindings;
    private final EffectExecutionContext context;

    public AttackDamageCoinPlan(AttackDefinition attack,EffectExecutionContext context) {
        AttackProgramRules.validate(attack);
        this.bindings=List.copyOf(attack.effectProgram().forTrigger(EffectTrigger.ON_ATTACK));
        this.context=Objects.requireNonNull(context);
    }
    /** Server-expanded, fixed target sequence; never supplied by card data or a client. */
    public AttackDamageCoinPlan(List<EffectDefinition> targetCoins,EffectExecutionContext context) {
        if(targetCoins.isEmpty()||targetCoins.size()>60)throw new IllegalArgumentException("目标硬币数量无效");
        for(var coin:targetCoins) {
            if(!coin.type().equals("coin_flip")||coin.target()!=EffectTarget.SELF
                ||!coin.arguments().keySet().equals(Set.of("flips","bind"))||!"1".equals(coin.arguments().get("flips"))
                ||!coin.conditions().isEmpty()||!coin.effects().isEmpty())throw new IllegalArgumentException("目标硬币序列仅接受独立单次掷币");
            AttackCoinBindings.name(coin.arguments().get("bind"));
        }
        this.bindings=List.of(new TriggeredEffects(EffectTrigger.ON_ATTACK,List.of(),targetCoins));
        this.context=Objects.requireNonNull(context);
    }
    @Override public AttackCoinAttempt.Instruction next(Map<String,EffectExecutionContext.CoinResult> provisional) {
        var scan=new Scan(provisional);
        for(var binding:bindings) {
            if(scan.matches(binding.conditions(),context,null))scan.walk(binding.effects(),context,null);
            if(scan.pending!=null)return scan.pending;
        }
        if(scan.coinIndex!=provisional.size())throw new IllegalStateException("Coin plan no longer matches the pending attempt");
        return null;
    }
    private static final class Scan {
        final Map<String,EffectExecutionContext.CoinResult> provisional;
        final Map<String,EffectExecutionContext.CoinResult> named=new HashMap<>();
        int coinIndex,budget=4096;
        AttackCoinAttempt.Instruction pending;
        Scan(Map<String,EffectExecutionContext.CoinResult> provisional){this.provisional=provisional;}
        boolean matches(List<ConditionDefinition> conditions,EffectExecutionContext scope,EffectExecutionContext.CoinResult coin) {
            var projected=new Conditions(scope,coin,named);
            return conditions.stream().allMatch(c->ConditionEvaluator.matches(c,projected,scope::conditionAvailable));
        }
        void walk(List<EffectDefinition> effects,EffectExecutionContext scope,EffectExecutionContext.CoinResult enclosing) {
            for(var effect:effects) {
                if(--budget<0)throw new IllegalStateException("Attack coin plan exceeded the effect execution budget");
                if(!matches(effect.conditions(),scope,enclosing))continue;
                switch(effect.type()) {
                    case "coin_flip" -> {
                        String id="damage-coin-"+coinIndex++;
                        var result=provisional.get(id);
                        if(result==null){pending=new AttackCoinAttempt.Instruction(id,effect,scope);return;}
                        String bind=effect.arguments().get("bind");
                        if(bind!=null) {
                            if(named.putIfAbsent(bind,result)!=null||scope.namedCoinResult(bind)!=null)
                                throw new IllegalStateException("Duplicate planned coin binding");
                        }
                        walk(effect.effects(),scope,result);
                    }
                    case "conditional" -> walk(effect.effects(),scope,enclosing);
                    case "repeat", "repeat_per_heads" -> {
                        int n=effect.type().equals("repeat")?Integer.parseInt(effect.arguments().get("count")):
                            Objects.requireNonNull(enclosing,"repeat_per_heads requires its enclosing coin result").heads();
                        for(int i=0;i<n&&pending==null;i++)walk(effect.effects(),scope,enclosing);
                    }
                    case "with_pokemon" -> {
                        var chosen=scope.withNamedPokemon(effect.arguments().get("ref"));
                        if(chosen!=null)walk(effect.effects(),chosen,enclosing);
                    }
                    default -> {
                        // AttackProgramRules has already proved these leaves are pure damage calculations.
                        // Crucially, do not call executeAtomic or rememberCoinResult during planning.
                        if(!effect.effects().isEmpty())throw new IllegalArgumentException("Unplanned damage calculation container: "+effect.type());
                    }
                }
                if(pending!=null)return;
            }
        }
    }
    private record Conditions(EffectExecutionContext delegate,EffectExecutionContext.CoinResult coin,
                              Map<String,EffectExecutionContext.CoinResult> named) implements EffectConditionContext {
        public boolean hasExtraAttackEnergy(ExtraAttackEnergyRequirement requirement){return delegate.hasExtraAttackEnergy(requirement);}
        public boolean opponentActiveMatches(AttackDefenderFilter filter){return delegate.opponentActiveMatches(filter);}
        public int number(ConditionType type,EffectTarget target,Map<String,String> args){return delegate.number(type,target,args);}
        public int opponentNumber(ConditionType type,Map<String,String> args){return delegate.opponentNumber(type,args);}
        public int opponentPrizeCardsTaken(){return delegate.opponentPrizeCardsTaken();}
        public int ownPrizeCardsTaken(){return delegate.ownPrizeCardsTaken();}
        public int opponentPokemonCount(PokemonFamilyFilter family){return delegate.opponentPokemonCount(family);}
        public boolean hasStatus(EffectTarget target,String status){return delegate.hasStatus(target,status);}
        public boolean flag(ConditionType type,EffectTarget target,Map<String,String> args){return delegate.flag(type,target,args);}
        public boolean coinResult(String expected){return coin==null?delegate.coinResult(expected):coin.matches(expected);}
        public int printedAttachedEnergyCards(EnergyCountQuery query){return delegate.printedAttachedEnergyCards(query);}
        public int energyUnitsForHitPoints(EnergyCountQuery query){return delegate.energyUnitsForHitPoints(query);}
        public EffectExecutionContext.CoinResult namedCoinResult(String name){return named.containsKey(name)?named.get(name):delegate.namedCoinResult(name);}
        public int boundCount(String name){var result=named.get(name);return result==null?delegate.boundCount(name):result.heads();}
        public boolean customCondition(ConditionDefinition condition){return delegate.customCondition(condition);}
    }
}
