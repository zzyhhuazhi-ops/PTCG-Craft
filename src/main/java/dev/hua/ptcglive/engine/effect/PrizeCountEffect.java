package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** Shared wire contract for attacks whose damage is counted from Prize cards. */
public final class PrizeCountEffect {
    public enum Mode { REMAINING, TAKEN, PREVIOUS_TURN_TAKEN }

    public static final Set<String> FIELDS = Set.of("amount", "prize_count");

    private PrizeCountEffect() { }

    /** Old data omitted the field and therefore keeps its original REMAINING behavior. */
    public static Mode mode(EffectDefinition effect) {
        if (!effect.type().equals("damage_per_prize"))
            throw new IllegalArgumentException("奖赏计数格式只能用于 damage_per_prize");
        String raw = effect.arguments().getOrDefault("prize_count", "REMAINING");
        try {
            return Mode.valueOf(raw);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("damage_per_prize 的 prize_count 无效", invalid);
        }
    }
}
