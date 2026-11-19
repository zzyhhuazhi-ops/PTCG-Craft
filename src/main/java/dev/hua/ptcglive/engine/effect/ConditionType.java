package dev.hua.ptcglive.engine.effect;

import java.util.Locale;

/** Reusable predicate vocabulary. Values and card-specific filters remain data, never Java card subclasses. */
public enum ConditionType {
    HAS_ENERGY,
    HAS_EXTRA_ATTACK_ENERGY,
    ENERGY_COUNT,
    DAMAGE_COUNTER_COUNT,
    /** Current Active Retreat Cost, after live modifiers; independent of permission to retreat. */
    RETREAT_COST,
    HP_BELOW,
    HP_ABOVE,
    HAS_STATUS,
    TARGET_HAS_STATUS,
    BENCH_COUNT,
    HAND_SIZE,
    PRIZE_COUNT,
    DISCARD_COUNT,
    LOST_ZONE_COUNT,
    /** Number of cards currently remaining in the selected player's deck. */
    DECK_SIZE,
    /** Current top-card printed names in an explicit public field range. SELF means the owner's whole field. */
    POKEMON_COUNT,
    COIN_RESULT,
    /** “If you do”: the attack's named count (AttackCountBindings, argument bind) is at least value. */
    BOUND_COUNT,
    IS_EVOLVED,
    EVOLVED_THIS_TURN,
    IS_STAGE,
    IS_BASIC,
    IS_EX,
    IS_V,
    IS_GX,
    IS_VSTAR,
    /** Printed “Pokémon-EX” (Black & White / XY era); distinct from “Pokémon ex” (IS_EX) by the rulebook. */
    IS_LEGACY_EX,
    /** Printed “Pokémon VMAX” (data also carries V). */
    IS_VMAX,
    /** Tera Pokémon (printed Tera rule; PTCGCardCN reads it from the PTCG Live card format code). */
    IS_TERA,
    /** Printed Pokémon type (argument pokemon_type); a dual-type Pokémon is each of its types. */
    IS_TYPE,
    /** The Pokémon moved from its owner's Bench to the Active Spot during the current turn. */
    BECAME_ACTIVE_THIS_TURN,
    BECAME_ACTIVE_LAST_OPPONENT_TURN,
    /** This exact in-play Pokémon successfully retreated from Active during its owner's previous turn. */
    RETREATED_LAST_OPPONENT_TURN,
    /** The Pokémon has at least one Pokémon Tool attached (attached, even while Tool effects are disabled). */
    HAS_TOOL,
    /** The target's currently printed Resistance is the requested Energy type. */
    HAS_RESISTANCE,
    /** The host has a current weakness matching any type of its opponent's Active Pokemon. */
    WEAK_TO_OPPONENT_ACTIVE,
    /** Current possession of an Ability, distinct from being allowed to activate it. */
    HAS_ABILITIES,
    /** The target currently has the reviewed printed Ability id. */
    HAS_ABILITY,
    /** An attached Special Energy has at least one other Special Energy beside it. */
    HAS_OTHER_SPECIAL_ENERGY,
    /** At least one Pokémon type appears on both players' current fields. */
    FIELDS_SHARE_POKEMON_TYPE,
    ALL_BENCH_DAMAGED,
    HAS_TOOL_DEFINITION,
    TOOL_ATTACHED_THIS_TURN,
    /** A printed attack of the current top card, independent of attack permission. */
    HAS_ATTACK,
    /** Canonical printed card name contains the reviewed value; never uses the translated UI label. */
    NAME_CONTAINS,
    /** Exact canonical name: e.g. Zapdos does not match Zapdos ex. */
    NAME_EQUALS,
    /** Every Pokémon on the acting player's field has a canonical printed name containing value. */
    ALL_POKEMON_NAME_CONTAINS,
    /** A heal removed damage from the Pokémon during the current turn (moving damage counters is not healing). */
    HEALED_THIS_TURN,
    /** One of the target's owner's Pokémon was Knocked Out by damage from an attack during the previous turn (the opponent's last turn). */
    KNOCKED_OUT_BY_ATTACK_LAST_TURN,
    /** Any KO during the opponent's preceding turn, excluding between-turns Pokémon Checkup. */
    KNOCKED_OUT_LAST_TURN,
    POKEMON_ATTACKED_LAST_OWN_TURN,
    CARD_PLAYED_THIS_TURN,
    /** Opponent's server-owned, game-wide GX or VSTAR usage marker. */
    OPPONENT_USED_LIMIT_GROUP,
    /** Acting player's server-owned, game-wide GX or VSTAR usage marker. */
    OWN_USED_LIMIT_GROUP,
    ATTACK_DID_DAMAGE,
    /** An explicit printed family/Battle Style marker (pokemon_family). */
    IS_FAMILY,
    /** Every Pokémon currently on the acting player's field has the requested printed family. */
    ALL_POKEMON_FAMILY,
    HAS_RULE_BOX,
    IS_ACTIVE,
    /** A Stadium card currently occupies the shared Stadium zone, regardless of its controller. */
    STADIUM_IN_PLAY,
    TURN_STATE,
    CUSTOM,
    AND,
    OR,
    NOT;

    public static ConditionType fromWire(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("条件类型不能为空");
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("未知条件类型：" + value, invalid); }
    }
}
