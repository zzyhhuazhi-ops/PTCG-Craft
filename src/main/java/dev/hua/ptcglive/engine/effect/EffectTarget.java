package dev.hua.ptcglive.engine.effect;

import java.util.Locale;

/** Stable, data-only targets. Server code resolves these against the current public match state. */
public enum EffectTarget {
    SELF,
    CHOSEN_POKEMON,
    TRIGGER_POKEMON,
    ATTACKING_POKEMON,
    OPPONENT_ACTIVE,
    OWN_ACTIVE,
    OWN_BENCH,
    OPPONENT_BENCH,
    ALL_OWN_POKEMON,
    ALL_OPPONENT_POKEMON,
    SELECT_OWN_POKEMON,
    SELECT_OPPONENT_POKEMON,
    /** A reviewed multi-target allocation may explicitly offer public Pokémon from both fields. */
    SELECT_ANY_POKEMON,
    HAND,
    DECK,
    DISCARD,
    LOST_ZONE,
    ENERGY,
    TRAINER,
    POKEMON;

    public boolean eventBound() { return this == TRIGGER_POKEMON || this == ATTACKING_POKEMON; }

    public static EffectTarget fromWire(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Effect 目标不能为空");
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("未知 Effect 目标：" + value, invalid); }
    }
}
