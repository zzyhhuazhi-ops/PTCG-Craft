package dev.hua.ptcglive.engine.effect;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One composable card effect. It has no Minecraft GUI state and no client
 * authority; all target selection and execution remain in the server engine.
 */
public record EffectDefinition(String type, EffectTarget target, Map<String, String> arguments,
                               List<ConditionDefinition> conditions, List<EffectDefinition> effects) {
    public EffectDefinition {
        if (type == null || !type.matches("[a-z][a-z0-9_-]*")) throw new IllegalArgumentException("Effect 类型无效");
        Objects.requireNonNull(target, "Effect 目标");
        arguments = ConditionDefinition.copyArguments(arguments, "Effect 参数");
        conditions = List.copyOf(Objects.requireNonNull(conditions, "Effect 条件"));
        effects = List.copyOf(Objects.requireNonNull(effects, "嵌套 Effect"));
    }

    public boolean isCustom() { return type.equals("custom"); }
    public String customHandler() { return arguments.get("handler"); }
}
