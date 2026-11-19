package dev.hua.ptcglive.engine;

public enum MatchPhase {
    MULLIGAN_REVEAL, MULLIGAN_DRAW, MULLIGAN_BENCH, COIN_CALL, TURN_ORDER, OPENING_POKEMON, TURN,
    /** Neither player's turn; all conditions/effects complete before checking any KOs. */
    POKEMON_CHECKUP,
    /** The player taking Prizes must choose an actual face-down Prize card. */
    PRIZE_SELECTION,
    /** A Knocked Out Active Pokémon must be replaced before the attacker's turn can end. */
    CHOOSE_REPLACEMENT,
    FINISHED,
    /** The ending player's automatic effects resolve before Pokémon Checkup; ordinary actions are closed. */
    TURN_END
}
