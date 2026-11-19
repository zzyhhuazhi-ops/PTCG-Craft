package dev.hua.ptcglive.engine;

/**
 * Server-only source zones that an audited card-effect resolver may send to
 * the Lost Zone. Lost Zone cards have no generic return route: a future card
 * text needs its own audited resolver before it can create such an exception.
 */
public enum EffectSourceZone {
    HAND, DECK, DISCARD
}
