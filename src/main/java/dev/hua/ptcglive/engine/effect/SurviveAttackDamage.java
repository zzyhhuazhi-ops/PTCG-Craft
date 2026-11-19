package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** Attack-damage Knock Out replacement; reducing counters here is not healing. */
public final class SurviveAttackDamage {
    private SurviveAttackDamage() { }
    public static void validate(EffectDefinition effect) {
        if (!effect.type().equals("survive_attack_damage") || effect.target()!=EffectTarget.SELF
            || !effect.arguments().keySet().equals(Set.of("remaining_hp"))
            || !"10".equals(effect.arguments().get("remaining_hp")) || !effect.effects().isEmpty())
            throw new IllegalArgumentException("招式伤害昏厥替代必须明确自身剩余10HP");
    }
}
