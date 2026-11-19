package dev.hua.ptcglive.rules;

/**
 * Audited printed Pokémon-family markers.  These are data, never guessed from
 * a localized name, artwork filename or card number.  More family markers can
 * be appended without changing Condition evaluation.
 */
public enum PokemonFamily {
    /** Explicit printed Rule Box metadata; never inferred from an era, name or other family marker. */
    RULE_BOX,
    /** Printed “Pokémon ex” (lowercase; EX era and Scarlet & Violet). */
    EX,
    /**
     * “Pokémon V”. Pokémon VMAX, VSTAR and V-UNION are also Pokémon V, so their
     * data carries V in addition to their own marker.
     */
    V,
    GX, VSTAR,
    VMAX, TAG_TEAM, BREAK, PRISM_STAR, RADIANT, TERA,
    /** Printed “Pokémon-EX” (Black & White / XY era). The rulebook treats it as different from “Pokémon ex”. */
    EX_LEGACY,
    /** Printed Battle Style labels; these do not themselves imply a Rule Box. */
    SINGLE_STRIKE, RAPID_STRIKE, FUSION_STRIKE,
    /** Printed labels, independent of card name and Rule Box. */
    FUTURE, ANCIENT, ULTRA_BEAST, TEAM_PLASMA,
    /** Printed Mega Evolution ex rule; distinct from XY Mega Pokemon-EX. */
    MEGA_EVOLUTION_EX,
    /** XY M Evolution Pokemon-EX, including Primal Kyogre/Groudon. */
    MEGA_EVOLUTION_LEGACY
}
