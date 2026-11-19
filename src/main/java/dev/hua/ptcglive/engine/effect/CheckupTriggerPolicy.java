package dev.hua.ptcglive.engine.effect;

import java.util.Objects;

public record CheckupTriggerPolicy(boolean optional, EnergyTriggerPolicy.Usage usage, boolean afterFailedSleepRecovery) implements AbilityEventPolicy {
    public CheckupTriggerPolicy(boolean optional,EnergyTriggerPolicy.Usage usage) {this(optional,usage,false);}
    public CheckupTriggerPolicy {
        Objects.requireNonNull(usage);
        if (usage == EnergyTriggerPolicy.Usage.ONCE_PER_TURN)
            throw new IllegalArgumentException("回合间不属于任何玩家回合，不能使用每回合事件限次");
    }
    @Override public EffectTrigger trigger() { return EffectTrigger.ON_POKEMON_CHECKUP; }
}
