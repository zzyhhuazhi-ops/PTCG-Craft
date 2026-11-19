package dev.hua.ptcglive.engine.effect;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable recursive condition tree used by conditional and triggered effects. */
public record ConditionDefinition(ConditionType type, Map<String, String> arguments, List<ConditionDefinition> children) {
    public ConditionDefinition {
        Objects.requireNonNull(type, "条件类型");
        arguments = copyArguments(arguments, "条件参数");
        children = List.copyOf(Objects.requireNonNull(children, "条件子项"));
        if ((type == ConditionType.AND || type == ConditionType.OR) && children.size() < 2)
            throw new IllegalArgumentException(type + " 至少需要两个子条件");
        if (type == ConditionType.NOT && children.size() != 1)
            throw new IllegalArgumentException("NOT 必须恰好包含一个子条件");
        if (type != ConditionType.AND && type != ConditionType.OR && type != ConditionType.NOT && !children.isEmpty())
            throw new IllegalArgumentException("原子条件不能嵌套子条件：" + type);
    }

    public static Map<String, String> copyArguments(Map<String, String> source, String label) {
        var result = new LinkedHashMap<String, String>();
        Objects.requireNonNull(source, label).forEach((key, value) -> {
            if (key == null || !key.matches("[a-z][a-z0-9_]*") || value == null || value.isBlank())
                throw new IllegalArgumentException(label + "包含无效键值");
            if (result.putIfAbsent(key, value) != null) throw new IllegalArgumentException(label + "包含重复参数：" + key);
        });
        return Map.copyOf(result);
    }
}
