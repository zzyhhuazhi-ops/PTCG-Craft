package dev.hua.ptcglive.engine.field;

/** Printed rules of the current top card, independent of Abilities and their suppression. */
public enum PokemonPrintedRules {
    STANDARD,
    RESTORED,
    /** XY Mega Evolution / Primal Reversion rule, independent of Pokémon Abilities. */
    EVOLUTION_ENDS_TURN,
    /** Reviewed Item form: no Special Conditions or retreat; its owner may discard it during their turn. */
    FOSSIL_ITEM,
    /** Unidentified Fossil has no printed Special Condition immunity. */
    UNIDENTIFIED_FOSSIL_ITEM,
    ROBO_SUBSTITUTE_ITEM,
    POKE_DOLL_ITEM;

    public boolean preventsSpecialConditions() { return this == FOSSIL_ITEM; }
    public boolean isItemPokemon() { return this == FOSSIL_ITEM || this == UNIDENTIFIED_FOSSIL_ITEM || preventsPrizeTake(); }
    public boolean preventsRetreat() { return isItemPokemon(); }
    public boolean preventsPrizeTake() { return this == ROBO_SUBSTITUTE_ITEM || this == POKE_DOLL_ITEM; }
    public boolean allowsOwnerDiscard() { return this == FOSSIL_ITEM || this == UNIDENTIFIED_FOSSIL_ITEM || this == ROBO_SUBSTITUTE_ITEM; }
}
