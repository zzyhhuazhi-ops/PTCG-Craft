package dev.hua.ptcglive.engine.field;
/** Printed restrictions remain data owned; field-only Ability suppression cannot waive a hidden deck card's rule. */
public enum PokemonPlacementRule {
    ANY_EFFECT, ZERO_TO_HERO_ONLY, EMERGENCY_ROTATION, SWELLING_FLASH, EXCITED_DIVE, EMERGENCY_ENTRY;
    public boolean permitsDeckReplacement(){return this==ANY_EFFECT||this==EMERGENCY_ENTRY;}
    public boolean permitsDiscardReplacement(){return this==ANY_EFFECT||this==EMERGENCY_ENTRY;}
    public boolean permitsBenchPlacement(){return this==ANY_EFFECT||this==EMERGENCY_ENTRY;}
}
