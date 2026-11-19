package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** Public, server-checked eligibility for a printed attack damage target. */
public final class AttackTargetFilter {
    private AttackTargetFilter() { }

    public static boolean handles(EffectDefinition effect) {
        return effect.type().equals("target_filter");
    }

    public static int minimumDamageCounters(EffectDefinition effect) {
        validate(effect);
        return Integer.parseInt(effect.arguments().get("damage_counters_min"));
    }

    public static void validate(EffectDefinition effect) {
        boolean both=effect.target()==EffectTarget.POKEMON
            &&effect.arguments().keySet().equals(Set.of("damage_counters_min","exclude_attacker"))
            &&"true".equals(effect.arguments().get("exclude_attacker"));
        boolean opponent=effect.target()==EffectTarget.ALL_OPPONENT_POKEMON
            &&effect.arguments().keySet().equals(Set.of("damage_counters_min"));
        if (!handles(effect) || !(both||opponent)
            || !effect.conditions().isEmpty() || !effect.effects().isEmpty())
            throw new IllegalArgumentException("攻击目标筛选须为对方全场，或双方全场排除攻击者，并声明最少伤害指示物数量");
        int minimum = Integer.parseInt(effect.arguments().get("damage_counters_min"));
        if (minimum < 1 || minimum > 60)
            throw new IllegalArgumentException("攻击目标最少伤害指示物数量须为1至60");
    }
}
