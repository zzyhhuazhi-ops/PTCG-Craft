package dev.hua.ptcglive.engine.tournament;

/**
 * International Play! Pokémon uses the full-prize Tiebreaker Game. The legacy
 * single-prize Sudden Death mode is never selected by the international ruleset.
 */
public enum TiebreakerPolicy {
    OFFICIAL_FULL_PRIZE,
    LEGACY_SINGLE_PRIZE_SUDDEN_DEATH
}
