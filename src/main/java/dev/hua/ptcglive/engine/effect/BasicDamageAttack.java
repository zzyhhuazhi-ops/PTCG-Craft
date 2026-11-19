package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.field.EnergyType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared ordinary-attack template. Every no-text attack is one data instance,
 * not a per-card Java class; optional Effects compose extra printed text.
 */
public record BasicDamageAttack(String attackName, Map<EnergyType, Integer> energyCost, int damage,
                                List<EffectDefinition> effects) {
    public BasicDamageAttack {
        if (attackName == null || attackName.isBlank()) throw new IllegalArgumentException("攻击名称不能为空");
        if (damage < 0 || damage % 10 != 0) throw new IllegalArgumentException("攻击伤害必须是非负的 10 倍数");
        var safeCost = new LinkedHashMap<EnergyType, Integer>();
        Objects.requireNonNull(energyCost, "攻击费用").forEach((type, count) -> {
            if (type == null || count == null || count < 1) throw new IllegalArgumentException("攻击费用无效");
            safeCost.put(type, count);
        });
        energyCost = Map.copyOf(safeCost);
        effects = List.copyOf(Objects.requireNonNull(effects, "攻击 Effect"));
    }
    public EffectProgram asProgram() {
        return effects.isEmpty() ? EffectProgram.EMPTY : new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_ATTACK, List.of(), effects)));
    }
}
