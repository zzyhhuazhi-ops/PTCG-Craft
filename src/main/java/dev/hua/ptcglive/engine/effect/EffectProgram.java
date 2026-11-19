package dev.hua.ptcglive.engine.effect;

import java.util.List;
import java.util.Objects;

/** Versioned composition attached to a card attack, Ability or Trainer card. */
public record EffectProgram(List<TriggeredEffects> triggers) {
    public static final EffectProgram EMPTY = new EffectProgram(List.of());
    public EffectProgram {
        triggers = List.copyOf(Objects.requireNonNull(triggers, "Effect 触发列表"));
    }
    public boolean isEmpty() { return triggers.isEmpty(); }
    public List<TriggeredEffects> forTrigger(EffectTrigger trigger) {
        return triggers.stream().filter(binding -> binding.trigger() == trigger).toList();
    }
}
