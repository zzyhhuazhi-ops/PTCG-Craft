package dev.hua.ptcglive.engine.field;

/** The five official Special Conditions. Only one positional condition may exist at once. */
public enum SpecialCondition {
    POISONED(false),
    BURNED(false),
    ASLEEP(true),
    PARALYZED(true),
    CONFUSED(true);

    private final boolean positional;
    SpecialCondition(boolean positional) { this.positional = positional; }
    public boolean positional() { return positional; }
}
