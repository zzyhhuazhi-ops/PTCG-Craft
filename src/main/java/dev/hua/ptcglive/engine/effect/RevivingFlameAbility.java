package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.field.AbilityDefinition;
import java.util.Map;

/** Exact discard-zone activation contract for Ho-Oh V's Reviving Flame. */
public final class RevivingFlameAbility {
    private RevivingFlameAbility() { }

    public static boolean reviveStep(EffectDefinition effect) {
        return effect.type().equals("discard_pokemon_to_bench_self") && effect.target() == EffectTarget.SELF
            && effect.arguments().isEmpty() && effect.conditions().isEmpty() && effect.effects().isEmpty();
    }

    public static boolean containsReviveStep(EffectDefinition effect) {
        return effect.type().equals("discard_pokemon_to_bench_self")
            || effect.effects().stream().anyMatch(RevivingFlameAbility::containsReviveStep);
    }

    public static boolean matches(AbilityDefinition ability) {
        if (ability.kind() != AbilityDefinition.Kind.ACTIVATED_ONCE_PER_TURN || !ability.composed()
            || !ability.endsTurn() || ability.usageLimit() != null) return false;
        var bindings = ability.effectProgram().triggers();
        if (bindings.size() != 1) return false;
        var binding = bindings.getFirst();
        if (binding.trigger() != EffectTrigger.ONCE_PER_TURN || !binding.conditions().isEmpty()
            || binding.effects().size() != 2 || !reviveStep(binding.effects().getFirst())) return false;
        var energy = binding.effects().get(1);
        return energy.type().equals("attach_energy") && energy.target() == EffectTarget.SELF
            && energy.conditions().isEmpty() && energy.effects().isEmpty()
            && energy.arguments().equals(Map.of("source", "DISCARD", "amount", "4", "minimum", "0",
                "basic_energy_only", "true", "target_scope", "OWN_FIELD"));
    }

    public static boolean containsReviveStep(AbilityDefinition ability) {
        return ability.effectProgram().triggers().stream().flatMap(binding -> binding.effects().stream())
            .anyMatch(RevivingFlameAbility::containsReviveStep);
    }
}
