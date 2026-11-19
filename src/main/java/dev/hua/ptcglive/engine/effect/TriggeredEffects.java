package dev.hua.ptcglive.engine.effect;

import java.util.List;
import java.util.Objects;

/** Effects attached to one lifecycle trigger, with optional reusable predicates. */
public record TriggeredEffects(EffectTrigger trigger, List<ConditionDefinition> conditions, List<EffectDefinition> effects) {
    public TriggeredEffects {
        Objects.requireNonNull(trigger, "Effect 触发器");
        conditions = List.copyOf(Objects.requireNonNull(conditions, "触发条件"));
        effects = List.copyOf(Objects.requireNonNull(effects, "触发 Effect"));
        if (effects.isEmpty()) throw new IllegalArgumentException("触发器至少需要一个 Effect");
    }
}
