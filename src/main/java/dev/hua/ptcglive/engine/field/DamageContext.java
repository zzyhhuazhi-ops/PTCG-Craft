package dev.hua.ptcglive.engine.field;

/**
 * Public battlefield facts available to a data-driven damage calculation.
 * Hidden cards, a player's hand and arbitrary card text are deliberately not
 * represented here: those need an explicit, audited resolver rather than a
 * guess in the generic damage pipeline.
 */
public record DamageContext(
    int attackerDamage,
    int defenderDamage,
    int attackerBenchSize,
    int defenderBenchSize,
    int attackerPrizesRemaining,
    int defenderPrizesRemaining,
    int attackerSpecialConditionCount,
    int defenderSpecialConditionCount,
    int attackerAttachedEnergyCount, // Current supplied units, not the number of physical cards.
    int defenderAttachedEnergyCount,
    int attackerAttachedToolCount,
    int defenderAttachedToolCount,
    int attackerHandSize,
    int defenderHandSize,
    int attackerDeckSize,
    int defenderDeckSize,
    int attackerDiscardSize,
    int defenderDiscardSize,
    int attackerLostZoneSize,
    int defenderLostZoneSize,
    /** The defending Active Pokémon's current Retreat Cost, after Tool/Stadium/temporary changes. */
    int defenderRetreatCost
) {
    /** Compatibility constructor for data before the Retreat Cost basis. */
    public DamageContext(int attackerDamage, int defenderDamage, int attackerBenchSize, int defenderBenchSize,
                         int attackerPrizesRemaining, int defenderPrizesRemaining, int attackerSpecialConditionCount,
                         int defenderSpecialConditionCount, int attackerAttachedEnergyCount, int defenderAttachedEnergyCount,
                         int attackerAttachedToolCount, int defenderAttachedToolCount, int attackerHandSize, int defenderHandSize,
                         int attackerDeckSize, int defenderDeckSize, int attackerDiscardSize, int defenderDiscardSize,
                         int attackerLostZoneSize, int defenderLostZoneSize) {
        this(attackerDamage, defenderDamage, attackerBenchSize, defenderBenchSize, attackerPrizesRemaining, defenderPrizesRemaining,
            attackerSpecialConditionCount, defenderSpecialConditionCount, attackerAttachedEnergyCount, defenderAttachedEnergyCount,
            attackerAttachedToolCount, defenderAttachedToolCount, attackerHandSize, defenderHandSize, attackerDeckSize, defenderDeckSize,
            attackerDiscardSize, defenderDiscardSize, attackerLostZoneSize, defenderLostZoneSize, 0);
    }
    /** Compatibility constructor for existing cards that do not read the expanded public field state. */
    public DamageContext(int attackerDamage, int defenderDamage, int attackerBenchSize, int defenderBenchSize,
                         int attackerPrizesRemaining, int defenderPrizesRemaining) {
        this(attackerDamage, defenderDamage, attackerBenchSize, defenderBenchSize, attackerPrizesRemaining,
            defenderPrizesRemaining, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
    public DamageContext {
        validateDamage(attackerDamage, "攻击方伤害指示物");
        validateDamage(defenderDamage, "防御方伤害指示物");
        validateRange(attackerBenchSize, 0, BattlefieldState.MAX_BENCH_SIZE, "攻击方备战区数量");
        validateRange(defenderBenchSize, 0, BattlefieldState.MAX_BENCH_SIZE, "防御方备战区数量");
        // Prize count is selected by the frozen match-setup rules.  The
        // international package currently uses six, but the generic damage
        // pipeline must not reject a valid historical/custom ruleset before
        // an effect can read its public remaining-prize value.
        validateRange(attackerPrizesRemaining, 0, 60, "攻击方奖赏卡数量");
        validateRange(defenderPrizesRemaining, 0, 60, "防御方奖赏卡数量");
        validateRange(attackerSpecialConditionCount, 0, 5, "攻击方异常状态数量");
        validateRange(defenderSpecialConditionCount, 0, 5, "防御方异常状态数量");
        // Up to 120 physical cards across both decks and up to four provision units per card.
        validateRange(attackerAttachedEnergyCount, 0, 480, "攻击方附着能量单位");
        validateRange(defenderAttachedEnergyCount, 0, 480, "防御方附着能量单位");
        validateRange(attackerAttachedToolCount, 0, 4, "攻击方附着工具数量");
        validateRange(defenderAttachedToolCount, 0, 4, "防御方附着工具数量");
        validateRange(attackerHandSize, 0, 60, "攻击方手牌数量");
        validateRange(defenderHandSize, 0, 60, "防御方手牌数量");
        validateRange(attackerDeckSize, 0, 60, "攻击方牌库数量");
        validateRange(defenderDeckSize, 0, 60, "防御方牌库数量");
        validateRange(attackerDiscardSize, 0, 60, "攻击方弃牌区数量");
        validateRange(defenderDiscardSize, 0, 60, "防御方弃牌区数量");
        validateRange(attackerLostZoneSize, 0, 60, "攻击方放逐区数量");
        validateRange(defenderLostZoneSize, 0, 60, "防御方放逐区数量");
        validateRange(defenderRetreatCost, 0, 20, "防御方撤退费");
    }

    private static void validateDamage(int value, String label) {
        if (value < 0 || value % 10 != 0) throw new IllegalArgumentException(label + "必须是非负的 10 的倍数");
    }
    private static void validateRange(int value, int minimum, int maximum, String label) {
        if (value < minimum || value > maximum) throw new IllegalArgumentException(label + "超出有效范围");
    }
}
