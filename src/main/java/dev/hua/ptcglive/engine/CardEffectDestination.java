package dev.hua.ptcglive.engine;

/** Server-resolved destinations for one selected, publicly visible discard card. */
public enum CardEffectDestination {
    HAND,
    /** The card enters its owner's deck and that deck is immediately shuffled by the server. */
    DECK_SHUFFLED,
    /** Chosen order is top-first; no shuffle is performed. */
    DECK_TOP,
    /** Chosen cards enter their owner's deck bottom without disturbing existing order. */
    DECK_BOTTOM
}
