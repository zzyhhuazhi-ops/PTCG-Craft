package dev.hua.ptcglive.engine.field;

/**
 * Explicit, server-issued exceptions to normal evolution timing. Normal player
 * actions always use STRICT; only an audited card-effect resolver may pass an
 * alternative permission.
 */
public record EvolutionPermission(boolean allowOwnerFirstTurn, boolean allowJustEnteredThisTurn,
                                  boolean allowAnotherEvolutionThisTurn, boolean allowSkipOneEvolutionStage) {
    /** Compatibility constructor: previous permissions never skipped an evolution stage. */
    public EvolutionPermission(boolean allowOwnerFirstTurn, boolean allowJustEnteredThisTurn,
                              boolean allowAnotherEvolutionThisTurn) {
        this(allowOwnerFirstTurn, allowJustEnteredThisTurn, allowAnotherEvolutionThisTurn, false);
    }
    public static final EvolutionPermission STRICT = new EvolutionPermission(false, false, false, false);
    public static final EvolutionPermission CARD_EFFECT_ALL_TIMING = new EvolutionPermission(true, true, true, false);
    /** One audited effect may skip exactly one intermediate evolution while retaining normal timing limits. */
    public static final EvolutionPermission CARD_EFFECT_SKIP_ONE_STAGE_STRICT_TIMING = new EvolutionPermission(false, false, false, true);
}
