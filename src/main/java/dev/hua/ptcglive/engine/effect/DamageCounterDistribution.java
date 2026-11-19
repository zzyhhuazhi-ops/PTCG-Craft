package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.ServerMatch;
import java.util.Set;

/** Parameters of a reusable split allocation. This is damage counters, not attack damage. */
public final class DamageCounterDistribution {
    private DamageCounterDistribution() { }
    public static final Set<String> FIELDS = Set.of("amount", "distribution", "target_scope", "min_targets", "max_targets");
    public static boolean handles(EffectDefinition effect) {
        return effect.type().equals("damage_counter") && effect.arguments().containsKey("distribution");
    }
    public static ServerMatch.DamageCounterAllocation from(EffectDefinition effect) {
        if (!handles(effect) || !"EXACT".equals(effect.arguments().get("distribution")))
            throw new IllegalArgumentException("伤害指示物分配须明确为 EXACT 总额，不能把攻击伤害或移动指示物当作放置");
        if (!FIELDS.containsAll(effect.arguments().keySet()) || !effect.arguments().keySet().containsAll(FIELDS))
            throw new IllegalArgumentException("分配须明确 amount、target_scope、min_targets、max_targets，不接受未知参数");
        var scope = ServerMatch.InPlayTargetScope.valueOf(effect.arguments().get("target_scope"));
        EffectTarget requiredTarget = switch (scope) {
            case OWN_ACTIVE, OWN_BENCH, OWN_FIELD -> EffectTarget.SELECT_OWN_POKEMON;
            case OPPONENT_ACTIVE, OPPONENT_BENCH, OPPONENT_FIELD -> EffectTarget.SELECT_OPPONENT_POKEMON;
            case ANY_FIELD -> EffectTarget.SELECT_ANY_POKEMON;
        };
        if (effect.target() != requiredTarget)
            throw new IllegalArgumentException("指示物分配目标与公开场上范围不一致");
        var result = new ServerMatch.DamageCounterAllocation(Integer.parseInt(effect.arguments().get("amount")), scope,
            Integer.parseInt(effect.arguments().get("min_targets")), Integer.parseInt(effect.arguments().get("max_targets")));
        if (result.minTargets() > result.counterBudget()) throw new IllegalArgumentException("每个目标至少分配一个指示物，最少目标数不能超过总额");
        return result;
    }
}
