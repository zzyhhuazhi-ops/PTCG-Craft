package dev.hua.ptcglive.engine.field;

import java.util.Objects;

/**
 * A deliberately narrow, data-driven damage modifier. It reads only public
 * battlefield facts; card text needing a choice or hidden information remains unsupported.
 */
public record DamageModifier(Basis basis, int amountPerUnit, int maximumUnits) {
    /**
     * Every basis is a public fact. Add a new enum value only with an engine
     * test that fixes its timing and its source/player orientation.
     */
    public enum Basis {
        ATTACKER_DAMAGE_COUNTERS,
        DEFENDER_DAMAGE_COUNTERS,
        ATTACKER_BENCH_SIZE,
        DEFENDER_BENCH_SIZE,
        ATTACKER_PRIZES_REMAINING,
        DEFENDER_PRIZES_REMAINING,
        ATTACKER_SPECIAL_CONDITION_COUNT,
        DEFENDER_SPECIAL_CONDITION_COUNT,
        ATTACKER_ATTACHED_ENERGY_COUNT,
        DEFENDER_ATTACHED_ENERGY_COUNT,
        ATTACKER_ATTACHED_TOOL_COUNT,
        DEFENDER_ATTACHED_TOOL_COUNT,
        ATTACKER_HAND_SIZE,
        DEFENDER_HAND_SIZE,
        ATTACKER_DECK_SIZE,
        DEFENDER_DECK_SIZE,
        ATTACKER_DISCARD_SIZE,
        DEFENDER_DISCARD_SIZE,
        ATTACKER_LOST_ZONE_SIZE,
        DEFENDER_LOST_ZONE_SIZE,
        /** One unit per {C} in the defending Active Pokémon's current Retreat Cost (Active-only attacks). */
        DEFENDER_RETREAT_COST
    }
    public DamageModifier {
        Objects.requireNonNull(basis, "伤害修正依据");
        if (amountPerUnit == 0 || amountPerUnit % 10 != 0) throw new IllegalArgumentException("每单位伤害修正必须是非零的 10 的倍数");
        if (maximumUnits < 0 || maximumUnits > 100) throw new IllegalArgumentException("伤害修正上限无效");
    }
    /** Resolves against the server's public, immutable combat snapshot. */
    public int resolve(DamageContext context) {
        Objects.requireNonNull(context, "伤害计算上下文");
        int units = switch (basis) {
            case ATTACKER_DAMAGE_COUNTERS -> context.attackerDamage() / 10;
            case DEFENDER_DAMAGE_COUNTERS -> context.defenderDamage() / 10;
            case ATTACKER_BENCH_SIZE -> context.attackerBenchSize();
            case DEFENDER_BENCH_SIZE -> context.defenderBenchSize();
            case ATTACKER_PRIZES_REMAINING -> context.attackerPrizesRemaining();
            case DEFENDER_PRIZES_REMAINING -> context.defenderPrizesRemaining();
            case ATTACKER_SPECIAL_CONDITION_COUNT -> context.attackerSpecialConditionCount();
            case DEFENDER_SPECIAL_CONDITION_COUNT -> context.defenderSpecialConditionCount();
            case ATTACKER_ATTACHED_ENERGY_COUNT -> context.attackerAttachedEnergyCount();
            case DEFENDER_ATTACHED_ENERGY_COUNT -> context.defenderAttachedEnergyCount();
            case ATTACKER_ATTACHED_TOOL_COUNT -> context.attackerAttachedToolCount();
            case DEFENDER_ATTACHED_TOOL_COUNT -> context.defenderAttachedToolCount();
            case ATTACKER_HAND_SIZE -> context.attackerHandSize();
            case DEFENDER_HAND_SIZE -> context.defenderHandSize();
            case ATTACKER_DECK_SIZE -> context.attackerDeckSize();
            case DEFENDER_DECK_SIZE -> context.defenderDeckSize();
            case ATTACKER_DISCARD_SIZE -> context.attackerDiscardSize();
            case DEFENDER_DISCARD_SIZE -> context.defenderDiscardSize();
            case ATTACKER_LOST_ZONE_SIZE -> context.attackerLostZoneSize();
            case DEFENDER_LOST_ZONE_SIZE -> context.defenderLostZoneSize();
            case DEFENDER_RETREAT_COST -> context.defenderRetreatCost();
        };
        if (maximumUnits > 0) units = Math.min(units, maximumUnits);
        return units * amountPerUnit;
    }

    /** Compatibility helper for pre-context card data. */
    public int resolve(int defenderDamage) {
        return resolve(new DamageContext(0, defenderDamage, 0, 0, 0, 0));
    }
}
