package dev.hua.ptcglive.engine.field;

/** Server rule data uses these stable type IDs rather than client-rendered symbols. */
public enum EnergyType {
    GRASS, FIRE, WATER, LIGHTNING, PSYCHIC, FIGHTING, DARKNESS, METAL, DRAGON, COLORLESS,
    /** Historical type support is independent of any ruleset's current card legality. */
    FAIRY
}
