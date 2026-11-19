package dev.hua.ptcglive.engine.field;

/**
 * The destination selected by a fully resolved server card effect when a
 * Pokémon leaves play without being Knocked Out. The Pokémon stack and every
 * attached card always travel together to the affected player's zone.
 */
public enum LeavePlayDestination {
    DISCARD,
    HAND,
    /** Cards are returned together, then the affected player's deck is shuffled by the server. */
    DECK_SHUFFLED
}
