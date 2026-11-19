package dev.hua.ptcglive.engine.effect;

import java.util.Locale;

/** Lifecycle triggers shared by attacks, Abilities and persistent card effects. */
public enum EffectTrigger {
    ON_ATTACK,
    BEFORE_ATTACK,
    AFTER_ATTACK,
    ON_DAMAGE,
    ON_KNOCKOUT,
    ON_ENERGY_ATTACHED,
    ON_CARD_PLAYED,
    /** A face-down Prize has been selected and revealed to its owner, before it enters that player's hand. */
    ON_PRIZE_TAKEN,
    /** A physical Pokemon card committed from its deck to discard by an attributed effect. */
    ON_DISCARDED_FROM_DECK,
    /** A physical Pokemon card committed from its hand to discard by an opponent's attributed effect. */
    ON_DISCARDED_FROM_HAND,
    ON_EVOLVE,
    ON_ENTER_PLAY,
    ON_RETREAT,
    ON_SWITCH,
    /** An Active Pokemon actually moved to its Bench by retreat or a switching effect. */
    ON_ACTIVE_TO_BENCH,
    /** The incoming Pokemon actually moved from Bench to Active during its owner turn. */
    ON_BENCH_TO_ACTIVE,
    /** A public Pokémon left play through a non-Knock-Out reviewed effect. */
    ON_LEAVE_PLAY,
    ON_TURN_START,
    ON_TURN_END,
    ON_POKEMON_CHECKUP,
    /** Explicit player activation without a per-source frequency cap. */
    ON_ACTIVATE,
    PASSIVE,
    ONCE_PER_TURN,
    ONCE_PER_GAME;

    public static EffectTrigger fromWire(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Effect 触发器不能为空");
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("未知 Effect 触发器：" + value, invalid); }
    }
}
