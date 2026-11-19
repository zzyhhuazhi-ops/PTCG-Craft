package dev.hua.ptcglive.rules;

/** New automatic trigger timing is never silently applied to an unreviewed regional/historical snapshot. */
public enum TriggerTiming {
    UNVERIFIED,
    /** Finish the original card first; current player orders turn events, NEXT player orders Checkup events. */
    AFTER_CARD_ACTIVE_PLAYER_V1
}
