package dev.hua.ptcglive.engine.effect;

import java.util.*;
import java.util.function.Predicate;

/** Checks whether at least one branch can affect current state, without rolling a coin or changing a zone. */
public final class EffectPotential {
    /** Non-mutating hypothetical contexts supplied only by the authoritative field resolver. */
    public record Alternative(EffectConditionContext state,Predicate<EffectDefinition> atomic) { }
    public interface SelectionContext {
        List<Alternative> pokemonAlternatives(EffectDefinition selection);
        Alternative namedPokemonAlternative(String name);
    }
    private int budget = 8192;
    private final boolean abilityCostCounts;
    private EffectPotential(boolean abilityCostCounts) { this.abilityCostCounts=abilityCostCounts; }
    public static boolean canAffect(TriggeredEffects binding, EffectConditionContext state, Predicate<EffectDefinition> atomic) {
        return canAffect(binding,state,atomic,false);
    }
    /** Recheck one already chosen branch against the current authoritative resolution state. */
    public static boolean chosenBranchCanAffect(EffectDefinition effect, EffectExecutionContext.CoinResult coin,
                                    EffectConditionContext state, Predicate<EffectDefinition> atomic) {
        return new EffectPotential(false).canAffect(effect,coin,state,atomic);
    }
    /** Trade may discard with an empty deck. Trainer play still needs a non-cost effect. */
    public static boolean canActivateAbility(TriggeredEffects binding, EffectConditionContext state, Predicate<EffectDefinition> atomic) {
        return canAffect(binding,state,atomic,true);
    }
    private static boolean canAffect(TriggeredEffects binding, EffectConditionContext state, Predicate<EffectDefinition> atomic,boolean abilityCostCounts) {
        var potential = new EffectPotential(abilityCostCounts);
        return binding.conditions().stream().allMatch(c -> ConditionEvaluator.matches(c, state)) && potential.any(binding.effects(), null,state,atomic);
    }
    private boolean any(List<EffectDefinition> effects, EffectExecutionContext.CoinResult coin,EffectConditionContext state,Predicate<EffectDefinition> atomic) {
        for (var effect : effects) if (canAffect(effect, coin,state,atomic)) return true;
        return false;
    }
    private boolean canAffect(EffectDefinition effect, EffectExecutionContext.CoinResult coin,EffectConditionContext state,Predicate<EffectDefinition> atomic) {
        // Affordability is checked separately before activation. A Trainer's cost
        // normally cannot justify play alone. A reviewed standalone discard effect or Ability can.
        if(ActivationCostRules.handCost(effect)>0&&!abilityCostCounts&&!"false".equals(effect.arguments().get("requires_effect")))return false;
        if (--budget < 0) throw new IllegalArgumentException("效果可用性分析超过安全步数，尚未执行");
        var context = new CoinContext(state, coin);
        if (!effect.conditions().stream().allMatch(c -> ConditionEvaluator.matches(c, context,
            candidate -> !(state instanceof EffectExecutionContext execution) || execution.conditionAvailable(candidate)))) return false;
        if (PokemonEffectSelection.handles(effect) && !PokemonSelectionScope.handles(effect)) {
            // Legacy SELECT_* atomics own a server-issued choice just as
            // select_pokemon does.  Test each authoritative hypothetical
            // target with its own bound context; never ask the base context
            // to resolve an as-yet-unselected target.
            if (!(state instanceof SelectionContext selection))
                throw new IllegalStateException("此入口未提供公开宝可梦选择可用性分析");
            for (var candidate : selection.pokemonAlternatives(effect))
                if (candidate.atomic().test(effect)) return true;
            return false;
        }
        return switch (effect.type()) {
            case "conditional", "repeat", "choice" -> any(effect.effects(), coin,state,atomic);
            case "repeat_per_heads" -> coin != null && coin.heads() > 0 && any(effect.effects(), coin,state,atomic);
            case "select_pokemon" -> {
                if(!(state instanceof SelectionContext selection))throw new IllegalStateException("此入口未提供固定目标可用性分析");
                var alternatives = selection.pokemonAlternatives(effect);
                // A named selector may deliberately defer all of its card
                // text to a later with_pokemon step.  It is still usable when
                // the authoritative field can issue at least one target.
                if (effect.arguments().containsKey("bind") && effect.effects().isEmpty()) yield !alternatives.isEmpty();
                boolean found=false;
                // All nested alternatives share this budget. No recursive call may reset it.
                for(var candidate:alternatives)
                    if(any(effect.effects(),coin,candidate.state(),candidate.atomic())) {found=true;break;}
                yield found;
            }
            case "with_pokemon" -> {
                if (!(state instanceof EffectExecutionContext execution) || !execution.supportsPokemonBindings())
                    throw new IllegalStateException("此入口未提供命名宝可梦绑定上下文");
                if(!(state instanceof SelectionContext selection))throw new IllegalStateException("此入口未提供命名目标可用性分析");
                var bound = selection.namedPokemonAlternative(effect.arguments().get("ref"));
                yield bound != null && any(effect.effects(), coin, bound.state(), bound.atomic());
            }
            case "coin_flip" -> {
                var policy=CoinFlipPolicy.from(effect);
                if(policy.untilTails())yield any(effect.effects(),new EffectExecutionContext.CoinResult(1,0),state,atomic)
                    ||any(effect.effects(),new EffectExecutionContext.CoinResult(2,1),state,atomic);
                int flips = policy.resolvedFlips(state); boolean found = false;
                for (int heads = 0; heads <= flips && !found; heads++) found = any(effect.effects(), new EffectExecutionContext.CoinResult(flips, heads),state,atomic);
                yield found;
            }
            default -> atomic.test(effect);
        };
    }
    private record CoinContext(EffectConditionContext delegate, EffectExecutionContext.CoinResult coin) implements EffectConditionContext {
        public boolean hasExtraAttackEnergy(ExtraAttackEnergyRequirement requirement) { return delegate.hasExtraAttackEnergy(requirement); }
        public int number(ConditionType type, EffectTarget target, Map<String,String> args) { return delegate.number(type, target, args); }
        public int opponentNumber(ConditionType type, Map<String,String> args) { return delegate.opponentNumber(type, args); }
        public boolean hasStatus(EffectTarget target, String status) { return delegate.hasStatus(target, status); }
        public boolean flag(ConditionType type, EffectTarget target, Map<String,String> args) { return delegate.flag(type, target, args); }
        public boolean coinResult(String expected) { return coin == null ? delegate.coinResult(expected) : coin.matches(expected); }
        public EffectExecutionContext.CoinResult namedCoinResult(String name) { return delegate.namedCoinResult(name); }
        public int boundCount(String name) { return delegate.boundCount(name); }
        public boolean customCondition(ConditionDefinition condition) { return delegate.customCondition(condition); }
    }
}
