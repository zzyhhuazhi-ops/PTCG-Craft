package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.field.SpecialCondition;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Data-driven cure for one or more Special Conditions on one Active Pokémon.
 * <p>
 * {@code statuses=ALL} means every condition currently on the target; a
 * comma-separated list (for example {@code POISONED,BURNED}) is deliberately
 * explicit so a card cannot silently clear a positional condition it did not
 * print.  The server still rejects an empty cure at execution time.
 */
public record ClearSpecialConditionsEffect(Set<SpecialCondition> conditions) {
    public static final Set<String> FIELDS = Set.of("statuses");
    public static final Set<String> ON_ATTACH_FIELDS = Set.of("statuses", "on_attach");

    public ClearSpecialConditionsEffect {
        conditions = Set.copyOf(conditions);
        if (conditions.isEmpty()) throw new IllegalArgumentException("解除异常状态必须至少声明一种状态");
    }

    public static boolean handles(EffectDefinition effect) {
        return effect.type().equals("clear_status");
    }

    public static ClearSpecialConditionsEffect from(EffectDefinition effect) {
        if (!handles(effect) || !effect.effects().isEmpty())
            throw new IllegalArgumentException("ClearStatusEffect 不接受嵌套文本");
        Map<String, String> arguments = effect.arguments();
        if (!arguments.keySet().equals(FIELDS) && !arguments.keySet().equals(ON_ATTACH_FIELDS))
            throw new IllegalArgumentException("ClearStatusEffect 必须声明 statuses，可选声明 on_attach=true");
        if (arguments.containsKey("on_attach") && !"true".equals(arguments.get("on_attach")))
            throw new IllegalArgumentException("ClearStatusEffect 的 on_attach 只能为 true");
        String raw = arguments.get("statuses");
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("ClearStatusEffect 的 statuses 不能为空");
        if (raw.trim().equalsIgnoreCase("ALL"))
            return new ClearSpecialConditionsEffect(EnumSet.allOf(SpecialCondition.class));

        var result = EnumSet.noneOf(SpecialCondition.class);
        for (String token : raw.split(",", -1)) {
            String normalized = token.trim().toUpperCase(Locale.ROOT);
            if (normalized.isEmpty()) throw new IllegalArgumentException("ClearStatusEffect 的 statuses 不能含空状态");
            try {
                if (!result.add(SpecialCondition.valueOf(normalized)))
                    throw new IllegalArgumentException("ClearStatusEffect 的 statuses 不能重复：" + normalized);
            } catch (IllegalArgumentException invalid) {
                if (invalid.getMessage() != null && invalid.getMessage().startsWith("ClearStatusEffect")) throw invalid;
                throw new IllegalArgumentException("ClearStatusEffect 包含未知异常状态：" + normalized, invalid);
            }
        }
        return new ClearSpecialConditionsEffect(result);
    }
    public static boolean onAttach(EffectDefinition effect) {
        return handles(effect)&&"true".equals(effect.arguments().get("on_attach"));
    }
}
