package dev.hua.ptcglive.engine.field;

import java.util.Objects;
import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.CardDefinition;

/**
 * Closed, server-resolved Ability opcodes. A card that needs an opcode not
 * listed here must be marked unsupported in its audited card data instead of
 * being approximated by the client.
 */
public record AbilityEffect(Opcode opcode, int amount, int minimumDeckSelections) {
    public enum Opcode {
        /** Transitional discriminator: semantics come only from EffectProgram, never from a card-specific opcode. */
        COMPOSED,
        /** Draw up to {@code amount} cards; an effect draw never causes deck-out by itself. */
        DRAW,
        /** Draw until the owner's hand contains {@code amount} cards; an effect draw never causes deck-out by itself. */
        DRAW_UNTIL_HAND_SIZE,
        /** Search the owner's deck for Basic Pokémon and put the selected cards into hand, then shuffle. */
        SEARCH_BASIC_POKEMON_TO_HAND,
        /** Search the owner's deck for Pokémon and put the selected cards into hand, then shuffle. */
        SEARCH_POKEMON_TO_HAND,
        /** Search the owner's deck for cards and put the selected cards into hand, then shuffle. */
        SEARCH_CARD_TO_HAND,
        /** Discard this Pokémon and every attached card, then draw up to {@code amount} cards. */
        DISCARD_SELF_DRAW,
        /** Draw up to {@code amount}; if at least one card was drawn, shuffle this Pokémon and its attachments into its owner's deck. */
        SHUFFLE_SELF_DRAW_IF_DREW,
        /** Choose one of your in-play Pokémon and heal it by {@code amount}. */
        HEAL_OWN_POKEMON,
        /** Move exactly one selected attached Energy from this Pokémon to another of your in-play Pokémon. */
        MOVE_ONE_ATTACHED_ENERGY_TO_OWN_POKEMON,
        /** Passive: this Pokémon's total Pokémon Tool capacity is {@code amount}. */
        PASSIVE_OWN_TOOL_CAPACITY,
        /** Passive: no Pokémon Tool may be attached to this Pokémon. */
        PASSIVE_PREVENT_POKEMON_TOOLS,
        /** Passive: this Pokémon cannot receive any Special Condition. */
        PASSIVE_PREVENT_ALL_SPECIAL_CONDITIONS,
        /** Passive: prevent attack damage done to this Pokémon, but not other attack effects. */
        PASSIVE_PREVENT_OPPONENT_ATTACK_DAMAGE,
        /** Passive: reduce attack damage done to this Pokémon by {@code amount}. */
        PASSIVE_REDUCE_OPPONENT_ATTACK_DAMAGE,
        /** Passive: while this Pokémon is on the Bench, prevent all damage done to it by attacks (both players' attacks). */
        PASSIVE_PREVENT_ATTACK_DAMAGE_WHILE_BENCHED,
        /** Passive: whenever an attack would do damage to this Pokémon, the server flips a coin; heads prevents that damage. */
        PASSIVE_COIN_PREVENT_ATTACK_DAMAGE,
        /** Passive: change Prize cards taken when this Pokémon is Knocked Out by {@code amount}. */
        PASSIVE_PRIZE_CARD_MODIFIER,
        /** Passive: a Star Cipher-granted Ability makes opposing in-play Pokémon Psychic-Weak without changing the amount. */
        PASSIVE_STAR_CIPHER
    }

    public AbilityEffect {
        Objects.requireNonNull(opcode, "特性操作码");
        if (isDeckSearchOpcode(opcode)) {
            if (minimumDeckSelections < 0 || minimumDeckSelections > amount)
                throw new IllegalArgumentException("特性牌库检索的最少选择数必须在 0 至检索上限之间");
        } else if (minimumDeckSelections != 0) {
            throw new IllegalArgumentException("非牌库检索特性不能声明最少牌库选择数");
        }
        if (opcode == Opcode.COMPOSED) {
            if (amount != 0) throw new IllegalArgumentException("组合特性的数值必须放在 Effect 参数中");
        } else if (opcode == Opcode.HEAL_OWN_POKEMON) {
            if (amount < 10 || amount > 1000 || amount % 10 != 0)
                throw new IllegalArgumentException("特性回复量必须为 10 至 1000 的 10 倍数");
        } else if (opcode == Opcode.MOVE_ONE_ATTACHED_ENERGY_TO_OWN_POKEMON) {
            if (amount != 1) throw new IllegalArgumentException("移动附着能量特性每次必须移动恰好 1 张能量");
        } else if (opcode == Opcode.PASSIVE_OWN_TOOL_CAPACITY) {
            if (amount < 1 || amount > 4) throw new IllegalArgumentException("被动工具容量必须在 1 至 4 之间");
        } else if (opcode == Opcode.PASSIVE_PREVENT_POKEMON_TOOLS || opcode == Opcode.PASSIVE_PREVENT_ALL_SPECIAL_CONDITIONS
            || opcode == Opcode.PASSIVE_PREVENT_OPPONENT_ATTACK_DAMAGE || opcode == Opcode.PASSIVE_PREVENT_ATTACK_DAMAGE_WHILE_BENCHED
            || opcode == Opcode.PASSIVE_COIN_PREVENT_ATTACK_DAMAGE || opcode == Opcode.PASSIVE_STAR_CIPHER) {
            if (amount != 0) throw new IllegalArgumentException("免疫类被动特性不接受数值参数");
        } else if (opcode == Opcode.PASSIVE_REDUCE_OPPONENT_ATTACK_DAMAGE) {
            if (amount < 10 || amount > 1000 || amount % 10 != 0)
                throw new IllegalArgumentException("被动攻击减伤必须为 10 至 1000 的 10 倍数");
        } else if (opcode == Opcode.PASSIVE_PRIZE_CARD_MODIFIER) {
            if (amount < -2 || amount > 3)
                throw new IllegalArgumentException("被动奖赏修正必须在 -2 至 3 之间");
        } else if (amount < 1 || amount > 60) throw new IllegalArgumentException("特性抽牌数量必须在 1 至 60 之间");
    }
    /** Compatibility constructor: existing search effects are exact-count unless data opts into a lower minimum. */
    public AbilityEffect(Opcode opcode, int amount) {
        this(opcode, amount, isDeckSearchOpcode(opcode) ? amount : 0);
    }
    public boolean needsPokemonTarget() {
        return opcode == Opcode.HEAL_OWN_POKEMON || opcode == Opcode.MOVE_ONE_ATTACHED_ENERGY_TO_OWN_POKEMON;
    }
    /** Only this opcode requires an attached-Energy instance id in addition to its public Pokémon target. */
    public boolean needsAttachedEnergyTarget() { return opcode == Opcode.MOVE_ONE_ATTACHED_ENERGY_TO_OWN_POKEMON; }
    /** This activated Ability needs an owner-private deck selection. */
    public boolean needsDeckSelection() { return isDeckSearchOpcode(opcode); }
    /** The legacy one-click table can safely show only this subset of deck searches. */
    public boolean singleDeckSelection() { return needsDeckSelection() && amount == 1 && minimumDeckSelections == 1; }
    /** Closed selector set for Ability deck searches. */
    public boolean allowsDeckSearchCandidate(CardDefinition definition) {
        return switch (opcode) {
            case SEARCH_BASIC_POKEMON_TO_HAND -> definition.basicPokemon();
            case SEARCH_POKEMON_TO_HAND -> definition.category() == CardCategory.POKEMON;
            case SEARCH_CARD_TO_HAND -> true;
            default -> false;
        };
    }
    public boolean passiveOnly() {
        return opcode == Opcode.PASSIVE_OWN_TOOL_CAPACITY || opcode == Opcode.PASSIVE_PREVENT_POKEMON_TOOLS
            || opcode == Opcode.PASSIVE_PREVENT_ALL_SPECIAL_CONDITIONS
            || opcode == Opcode.PASSIVE_PREVENT_OPPONENT_ATTACK_DAMAGE || opcode == Opcode.PASSIVE_REDUCE_OPPONENT_ATTACK_DAMAGE
            || opcode == Opcode.PASSIVE_PREVENT_ATTACK_DAMAGE_WHILE_BENCHED || opcode == Opcode.PASSIVE_COIN_PREVENT_ATTACK_DAMAGE
            || opcode == Opcode.PASSIVE_PRIZE_CARD_MODIFIER || opcode == Opcode.PASSIVE_STAR_CIPHER;
    }
    private static boolean isDeckSearchOpcode(Opcode opcode) {
        return opcode == Opcode.SEARCH_BASIC_POKEMON_TO_HAND || opcode == Opcode.SEARCH_POKEMON_TO_HAND || opcode == Opcode.SEARCH_CARD_TO_HAND;
    }
}
