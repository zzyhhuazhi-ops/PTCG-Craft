package dev.hua.ptcglive.engine.field;

import dev.hua.ptcglive.rules.EffectSupport;
import dev.hua.ptcglive.engine.effect.BasicDamageAttack;
import dev.hua.ptcglive.engine.effect.EffectDefinition;
import dev.hua.ptcglive.engine.effect.EffectProgram;
import dev.hua.ptcglive.engine.effect.EffectTrigger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A fixed-damage attack whose remaining effect support is explicitly declared in data. */
public record AttackDefinition(
    String id,
    String nameZh,
    Map<EnergyType, Integer> energyCost,
    int baseDamage,
    EnergyType damageType,
    EffectSupport effectSupport,
    String unsupportedReasonZh,
    java.util.List<ConditionEffect> conditions,
    CoinDamage coinDamage,
    CoinProtection coinProtection,
    java.util.List<DamageModifier> damageModifiers,
    DamageTarget damageTarget,
    int selfDamage,
    int targetCount,
    boolean discardAllAttachedEnergyAfterAttack,
    int selfHealAfterAttack,
    int discardSelectedAttachedEnergyAfterAttack,
    boolean ignoresWeaknessAndResistance,
    int damageReductionFromOpponentNextTurn,
    boolean ignoresActiveDefenderDamageEffects,
    boolean cannotUseSameAttackDuringNextTurn,
    EffectProgram effectProgram,
    dev.hua.ptcglive.engine.effect.SharedUsageLimit usageLimit,
    /** “This attack's damage isn't affected by Resistance.” Weakness still applies. */
    boolean ignoresResistance,
    /** “This attack's damage isn't affected by Weakness.” Resistance still applies. */
    boolean ignoresWeakness,
    dev.hua.ptcglive.engine.effect.PokemonFamilyFilter targetFamilies,
    /** Printed permission to declare this attack from the Bench; distinct from copying its final body. */
    boolean mayUseFromBench,
    /** Printed recipient restriction on physical attached Special Energy cards, independent of their effects. */
    boolean targetHasSpecialEnergy,
    /** Printed exception allowing this attack when its player went first on turn 1. */
    boolean mayAttackFirstTurn,
    /** Printed restriction forbidding this attack when its player went second on their first turn. */
    boolean cannotAttackSecondPlayerFirstTurn,
    /** Printed restriction: this attack is usable only by the second player on that player's first turn. */
    boolean onlySecondPlayerFirstTurn,
    /** Printed prerequisite for declaring the attack; null means no additional prerequisite. */
    AttackUseRequirement useRequirement,
    /** Printed exception: a chosen Benched target also takes Weakness and Resistance. */
    boolean applyWeaknessResistanceToBench
) {
    /** Compatibility for attacks without the printed Bench Weakness/Resistance exception. */
    public AttackDefinition(String id,String nameZh,Map<EnergyType,Integer> energyCost,int baseDamage,EnergyType damageType,
        EffectSupport effectSupport,String unsupportedReasonZh,java.util.List<ConditionEffect> conditions,CoinDamage coinDamage,
        CoinProtection coinProtection,java.util.List<DamageModifier> damageModifiers,DamageTarget damageTarget,int selfDamage,
        int targetCount,boolean discardAllAttachedEnergyAfterAttack,int selfHealAfterAttack,int discardSelectedAttachedEnergyAfterAttack,
        boolean ignoresWeaknessAndResistance,int damageReductionFromOpponentNextTurn,boolean ignoresActiveDefenderDamageEffects,
        boolean cannotUseSameAttackDuringNextTurn,EffectProgram effectProgram,dev.hua.ptcglive.engine.effect.SharedUsageLimit usageLimit,
        boolean ignoresResistance,boolean ignoresWeakness,dev.hua.ptcglive.engine.effect.PokemonFamilyFilter targetFamilies,
        boolean mayUseFromBench,boolean targetHasSpecialEnergy,boolean mayAttackFirstTurn,
        boolean cannotAttackSecondPlayerFirstTurn,boolean onlySecondPlayerFirstTurn,AttackUseRequirement useRequirement) {
        this(id,nameZh,energyCost,baseDamage,damageType,effectSupport,unsupportedReasonZh,conditions,coinDamage,coinProtection,
            damageModifiers,damageTarget,selfDamage,targetCount,discardAllAttachedEnergyAfterAttack,selfHealAfterAttack,
            discardSelectedAttachedEnergyAfterAttack,ignoresWeaknessAndResistance,damageReductionFromOpponentNextTurn,
            ignoresActiveDefenderDamageEffects,cannotUseSameAttackDuringNextTurn,effectProgram,usageLimit,ignoresResistance,
            ignoresWeakness,targetFamilies,mayUseFromBench,targetHasSpecialEnergy,mayAttackFirstTurn,
            cannotAttackSecondPlayerFirstTurn,onlySecondPlayerFirstTurn,useRequirement,false);
    }
    /** Compatibility for previously reviewed attacks without a declaration prerequisite. */
    public AttackDefinition(String id,String nameZh,Map<EnergyType,Integer> energyCost,int baseDamage,EnergyType damageType,
        EffectSupport effectSupport,String unsupportedReasonZh,java.util.List<ConditionEffect> conditions,CoinDamage coinDamage,
        CoinProtection coinProtection,java.util.List<DamageModifier> damageModifiers,DamageTarget damageTarget,int selfDamage,
        int targetCount,boolean discardAllAttachedEnergyAfterAttack,int selfHealAfterAttack,int discardSelectedAttachedEnergyAfterAttack,
        boolean ignoresWeaknessAndResistance,int damageReductionFromOpponentNextTurn,boolean ignoresActiveDefenderDamageEffects,
        boolean cannotUseSameAttackDuringNextTurn,EffectProgram effectProgram,dev.hua.ptcglive.engine.effect.SharedUsageLimit usageLimit,
        boolean ignoresResistance,boolean ignoresWeakness,dev.hua.ptcglive.engine.effect.PokemonFamilyFilter targetFamilies,
        boolean mayUseFromBench,boolean targetHasSpecialEnergy,boolean mayAttackFirstTurn,
        boolean cannotAttackSecondPlayerFirstTurn,boolean onlySecondPlayerFirstTurn) {
        this(id,nameZh,energyCost,baseDamage,damageType,effectSupport,unsupportedReasonZh,conditions,coinDamage,coinProtection,
            damageModifiers,damageTarget,selfDamage,targetCount,discardAllAttachedEnergyAfterAttack,selfHealAfterAttack,
            discardSelectedAttachedEnergyAfterAttack,ignoresWeaknessAndResistance,damageReductionFromOpponentNextTurn,
            ignoresActiveDefenderDamageEffects,cannotUseSameAttackDuringNextTurn,effectProgram,usageLimit,ignoresResistance,
            ignoresWeakness,targetFamilies,mayUseFromBench,targetHasSpecialEnergy,mayAttackFirstTurn,
            cannotAttackSecondPlayerFirstTurn,onlySecondPlayerFirstTurn,null);
    }
    /** Compatibility for data before per-attack opening-turn timing. */
    public AttackDefinition(String id,String nameZh,Map<EnergyType,Integer> energyCost,int baseDamage,EnergyType damageType,
        EffectSupport effectSupport,String unsupportedReasonZh,java.util.List<ConditionEffect> conditions,CoinDamage coinDamage,
        CoinProtection coinProtection,java.util.List<DamageModifier> damageModifiers,DamageTarget damageTarget,int selfDamage,
        int targetCount,boolean discardAllAttachedEnergyAfterAttack,int selfHealAfterAttack,int discardSelectedAttachedEnergyAfterAttack,
        boolean ignoresWeaknessAndResistance,int damageReductionFromOpponentNextTurn,boolean ignoresActiveDefenderDamageEffects,
        boolean cannotUseSameAttackDuringNextTurn,EffectProgram effectProgram,dev.hua.ptcglive.engine.effect.SharedUsageLimit usageLimit,
        boolean ignoresResistance,boolean ignoresWeakness,dev.hua.ptcglive.engine.effect.PokemonFamilyFilter targetFamilies,
        boolean mayUseFromBench,boolean targetHasSpecialEnergy) {
        this(id,nameZh,energyCost,baseDamage,damageType,effectSupport,unsupportedReasonZh,conditions,coinDamage,coinProtection,
            damageModifiers,damageTarget,selfDamage,targetCount,discardAllAttachedEnergyAfterAttack,selfHealAfterAttack,
            discardSelectedAttachedEnergyAfterAttack,ignoresWeaknessAndResistance,damageReductionFromOpponentNextTurn,
            ignoresActiveDefenderDamageEffects,cannotUseSameAttackDuringNextTurn,effectProgram,usageLimit,ignoresResistance,
            ignoresWeakness,targetFamilies,mayUseFromBench,targetHasSpecialEnergy,false,false,false);
    }
    /** Compatibility for snapshots before physical Special Energy recipient restrictions. */
    public AttackDefinition(String id,String nameZh,Map<EnergyType,Integer> energyCost,int baseDamage,EnergyType damageType,
        EffectSupport effectSupport,String unsupportedReasonZh,java.util.List<ConditionEffect> conditions,CoinDamage coinDamage,
        CoinProtection coinProtection,java.util.List<DamageModifier> damageModifiers,DamageTarget damageTarget,int selfDamage,
        int targetCount,boolean discardAllAttachedEnergyAfterAttack,int selfHealAfterAttack,int discardSelectedAttachedEnergyAfterAttack,
        boolean ignoresWeaknessAndResistance,int damageReductionFromOpponentNextTurn,boolean ignoresActiveDefenderDamageEffects,
        boolean cannotUseSameAttackDuringNextTurn,EffectProgram effectProgram,dev.hua.ptcglive.engine.effect.SharedUsageLimit usageLimit,
        boolean ignoresResistance,boolean ignoresWeakness,dev.hua.ptcglive.engine.effect.PokemonFamilyFilter targetFamilies,boolean mayUseFromBench) {
        this(id,nameZh,energyCost,baseDamage,damageType,effectSupport,unsupportedReasonZh,conditions,coinDamage,coinProtection,
            damageModifiers,damageTarget,selfDamage,targetCount,discardAllAttachedEnergyAfterAttack,selfHealAfterAttack,
            discardSelectedAttachedEnergyAfterAttack,ignoresWeaknessAndResistance,damageReductionFromOpponentNextTurn,
            ignoresActiveDefenderDamageEffects,cannotUseSameAttackDuringNextTurn,effectProgram,usageLimit,ignoresResistance,ignoresWeakness,targetFamilies,mayUseFromBench,false);
    }
    /** Compatibility for all previously reviewed attack snapshots. */
    public AttackDefinition(String id,String nameZh,Map<EnergyType,Integer> energyCost,int baseDamage,EnergyType damageType,
        EffectSupport effectSupport,String unsupportedReasonZh,java.util.List<ConditionEffect> conditions,CoinDamage coinDamage,
        CoinProtection coinProtection,java.util.List<DamageModifier> damageModifiers,DamageTarget damageTarget,int selfDamage,
        int targetCount,boolean discardAllAttachedEnergyAfterAttack,int selfHealAfterAttack,int discardSelectedAttachedEnergyAfterAttack,
        boolean ignoresWeaknessAndResistance,int damageReductionFromOpponentNextTurn,boolean ignoresActiveDefenderDamageEffects,
        boolean cannotUseSameAttackDuringNextTurn,EffectProgram effectProgram,dev.hua.ptcglive.engine.effect.SharedUsageLimit usageLimit,
        boolean ignoresResistance,boolean ignoresWeakness,dev.hua.ptcglive.engine.effect.PokemonFamilyFilter targetFamilies) {
        this(id,nameZh,energyCost,baseDamage,damageType,effectSupport,unsupportedReasonZh,conditions,coinDamage,coinProtection,
            damageModifiers,damageTarget,selfDamage,targetCount,discardAllAttachedEnergyAfterAttack,selfHealAfterAttack,
            discardSelectedAttachedEnergyAfterAttack,ignoresWeaknessAndResistance,damageReductionFromOpponentNextTurn,
            ignoresActiveDefenderDamageEffects,cannotUseSameAttackDuringNextTurn,effectProgram,usageLimit,ignoresResistance,ignoresWeakness,targetFamilies,false);
    }
    /** Compatibility constructor for attacks without a printed damage-target family restriction. */
    public AttackDefinition(String id,String nameZh,Map<EnergyType,Integer> energyCost,int baseDamage,EnergyType damageType,
        EffectSupport effectSupport,String unsupportedReasonZh,java.util.List<ConditionEffect> conditions,CoinDamage coinDamage,
        CoinProtection coinProtection,java.util.List<DamageModifier> damageModifiers,DamageTarget damageTarget,int selfDamage,
        int targetCount,boolean discardAllAttachedEnergyAfterAttack,int selfHealAfterAttack,int discardSelectedAttachedEnergyAfterAttack,
        boolean ignoresWeaknessAndResistance,int damageReductionFromOpponentNextTurn,boolean ignoresActiveDefenderDamageEffects,
        boolean cannotUseSameAttackDuringNextTurn,EffectProgram effectProgram,dev.hua.ptcglive.engine.effect.SharedUsageLimit usageLimit,
        boolean ignoresResistance,boolean ignoresWeakness) {
        this(id,nameZh,energyCost,baseDamage,damageType,effectSupport,unsupportedReasonZh,conditions,coinDamage,coinProtection,
            damageModifiers,damageTarget,selfDamage,targetCount,discardAllAttachedEnergyAfterAttack,selfHealAfterAttack,
            discardSelectedAttachedEnergyAfterAttack,ignoresWeaknessAndResistance,damageReductionFromOpponentNextTurn,
            ignoresActiveDefenderDamageEffects,cannotUseSameAttackDuringNextTurn,effectProgram,usageLimit,ignoresResistance,ignoresWeakness,
            dev.hua.ptcglive.engine.effect.PokemonFamilyFilter.ANY);
    }
    /** Compatibility constructor for data before the Weakness-only override. */
    public AttackDefinition(String id,String nameZh,Map<EnergyType,Integer> energyCost,int baseDamage,EnergyType damageType,
        EffectSupport effectSupport,String unsupportedReasonZh,java.util.List<ConditionEffect> conditions,CoinDamage coinDamage,
        CoinProtection coinProtection,java.util.List<DamageModifier> damageModifiers,DamageTarget damageTarget,int selfDamage,
        int targetCount,boolean discardAllAttachedEnergyAfterAttack,int selfHealAfterAttack,int discardSelectedAttachedEnergyAfterAttack,
        boolean ignoresWeaknessAndResistance,int damageReductionFromOpponentNextTurn,boolean ignoresActiveDefenderDamageEffects,
        boolean cannotUseSameAttackDuringNextTurn,EffectProgram effectProgram,dev.hua.ptcglive.engine.effect.SharedUsageLimit usageLimit,
        boolean ignoresResistance) {
        this(id,nameZh,energyCost,baseDamage,damageType,effectSupport,unsupportedReasonZh,conditions,coinDamage,coinProtection,
            damageModifiers,damageTarget,selfDamage,targetCount,discardAllAttachedEnergyAfterAttack,selfHealAfterAttack,
            discardSelectedAttachedEnergyAfterAttack,ignoresWeaknessAndResistance,damageReductionFromOpponentNextTurn,
            ignoresActiveDefenderDamageEffects,cannotUseSameAttackDuringNextTurn,effectProgram,usageLimit,ignoresResistance,false);
    }
    /** Compatibility constructor for data before the Resistance-only override. */
    public AttackDefinition(String id,String nameZh,Map<EnergyType,Integer> energyCost,int baseDamage,EnergyType damageType,
        EffectSupport effectSupport,String unsupportedReasonZh,java.util.List<ConditionEffect> conditions,CoinDamage coinDamage,
        CoinProtection coinProtection,java.util.List<DamageModifier> damageModifiers,DamageTarget damageTarget,int selfDamage,
        int targetCount,boolean discardAllAttachedEnergyAfterAttack,int selfHealAfterAttack,int discardSelectedAttachedEnergyAfterAttack,
        boolean ignoresWeaknessAndResistance,int damageReductionFromOpponentNextTurn,boolean ignoresActiveDefenderDamageEffects,
        boolean cannotUseSameAttackDuringNextTurn,EffectProgram effectProgram,dev.hua.ptcglive.engine.effect.SharedUsageLimit usageLimit) {
        this(id,nameZh,energyCost,baseDamage,damageType,effectSupport,unsupportedReasonZh,conditions,coinDamage,coinProtection,
            damageModifiers,damageTarget,selfDamage,targetCount,discardAllAttachedEnergyAfterAttack,selfHealAfterAttack,
            discardSelectedAttachedEnergyAfterAttack,ignoresWeaknessAndResistance,damageReductionFromOpponentNextTurn,
            ignoresActiveDefenderDamageEffects,cannotUseSameAttackDuringNextTurn,effectProgram,usageLimit,false);
    }
    public AttackDefinition(String id,String nameZh,Map<EnergyType,Integer> energyCost,int baseDamage,EnergyType damageType,
        EffectSupport effectSupport,String unsupportedReasonZh,java.util.List<ConditionEffect> conditions,CoinDamage coinDamage,
        CoinProtection coinProtection,java.util.List<DamageModifier> damageModifiers,DamageTarget damageTarget,int selfDamage,
        int targetCount,boolean discardAllAttachedEnergyAfterAttack,int selfHealAfterAttack,int discardSelectedAttachedEnergyAfterAttack,
        boolean ignoresWeaknessAndResistance,int damageReductionFromOpponentNextTurn,boolean ignoresActiveDefenderDamageEffects,
        boolean cannotUseSameAttackDuringNextTurn,EffectProgram effectProgram) {
        this(id,nameZh,energyCost,baseDamage,damageType,effectSupport,unsupportedReasonZh,conditions,coinDamage,coinProtection,
            damageModifiers,damageTarget,selfDamage,targetCount,discardAllAttachedEnergyAfterAttack,selfHealAfterAttack,
            discardSelectedAttachedEnergyAfterAttack,ignoresWeaknessAndResistance,damageReductionFromOpponentNextTurn,
            ignoresActiveDefenderDamageEffects,cannotUseSameAttackDuringNextTurn,effectProgram,null);
    }
    /** The target of the attack's printed damage. Bench damage ordinarily ignores Weakness and Resistance. */
    public enum DamageTarget {
        OPPONENT_ACTIVE(false),
        OPPONENT_BENCH(true),
        ALL_OPPONENT_BENCH(false),
        /** “N damage to N of your opponent's Pokémon”: the attacker chooses Active and/or Benched Pokémon. */
        OPPONENT_ANY(true),
        /** “N damage to each of your opponent's Pokémon”: the Active and every Benched Pokémon. */
        ALL_OPPONENT_POKEMON(false),
        /** All eligible Pokemon on BOTH fields, including the actual attacker. */
        ALL_POKEMON(false);
        private final boolean requiresTargetChoice;
        DamageTarget(boolean requiresTargetChoice) { this.requiresTargetChoice = requiresTargetChoice; }
        public boolean requiresTargetChoice() { return requiresTargetChoice; }
        public boolean appliesWeaknessAndResistance() { return this == OPPONENT_ACTIVE; }
        /** Targets may include the opponent's Active Pokémon, which ordinarily alone takes Weakness and Resistance. */
        public boolean mixedTargets() { return this == OPPONENT_ANY || this == ALL_OPPONENT_POKEMON || this == ALL_POKEMON; }
    }
    public record CoinDamage(int flips, int perHeads) {
        public CoinDamage {
            if (flips < 1 || flips > 20 || perHeads < 10 || perHeads > 1000 || perHeads % 10 != 0)
                throw new IllegalArgumentException("硬币伤害参数无效");
        }
    }
    /** What a successful temporary attack protection cancels. */
    public enum ProtectionScope { DAMAGE_ONLY, DAMAGE_AND_EFFECTS }
    /** A supported “flip; on heads protect this Pokémon during the opponent's next turn” continuous effect. */
    public record CoinProtection(int flips, int requiredHeads, ProtectionScope scope) {
        public CoinProtection {
            if (flips < 1 || flips > 20 || requiredHeads < 1 || requiredHeads > flips)
                throw new IllegalArgumentException("硬币免疫参数无效");
            Objects.requireNonNull(scope, "攻击保护范围");
        }
        public CoinProtection(int flips, int requiredHeads) { this(flips, requiredHeads, ProtectionScope.DAMAGE_AND_EFFECTS); }
    }
    /**
     * Compatibility constructor for existing combat snapshots. New snapshots
     * may provide an EffectProgram, while all older attacks remain a shared
     * BasicDamageAttack with an empty composed-effect list.
     */
    public AttackDefinition(String id, String nameZh, Map<EnergyType, Integer> energyCost, int baseDamage,
                            EnergyType damageType, EffectSupport effectSupport, String unsupportedReasonZh,
                            java.util.List<ConditionEffect> conditions, CoinDamage coinDamage, CoinProtection coinProtection,
                            java.util.List<DamageModifier> damageModifiers, DamageTarget damageTarget, int selfDamage, int targetCount,
                            boolean discardAllAttachedEnergyAfterAttack, int selfHealAfterAttack,
                            int discardSelectedAttachedEnergyAfterAttack, boolean ignoresWeaknessAndResistance,
                            int damageReductionFromOpponentNextTurn, boolean ignoresActiveDefenderDamageEffects,
                            boolean cannotUseSameAttackDuringNextTurn) {
        this(id, nameZh, energyCost, baseDamage, damageType, effectSupport, unsupportedReasonZh, conditions, coinDamage, coinProtection,
            damageModifiers, damageTarget, selfDamage, targetCount, discardAllAttachedEnergyAfterAttack, selfHealAfterAttack,
            discardSelectedAttachedEnergyAfterAttack, ignoresWeaknessAndResistance, damageReductionFromOpponentNextTurn,
            ignoresActiveDefenderDamageEffects, cannotUseSameAttackDuringNextTurn, EffectProgram.EMPTY);
    }
    public AttackDefinition(String id, String nameZh, Map<EnergyType, Integer> energyCost, int baseDamage,
                            EnergyType damageType, EffectSupport effectSupport, String unsupportedReasonZh,
                            java.util.List<ConditionEffect> conditions) {
        this(id, nameZh, energyCost, baseDamage, damageType, effectSupport, unsupportedReasonZh, conditions, null, null, java.util.List.of(), DamageTarget.OPPONENT_ACTIVE, 0, 1, false, 0, 0, false);
    }
    public AttackDefinition(String id, String nameZh, Map<EnergyType, Integer> energyCost, int baseDamage,
                            EnergyType damageType, EffectSupport effectSupport, String unsupportedReasonZh) {
        this(id, nameZh, energyCost, baseDamage, damageType, effectSupport, unsupportedReasonZh, java.util.List.of(), null, null, java.util.List.of(), DamageTarget.OPPONENT_ACTIVE, 0, 1, false, 0, 0, false);
    }
    /** Compatibility constructor for combat snapshots without a target field. */
    public AttackDefinition(String id, String nameZh, Map<EnergyType, Integer> energyCost, int baseDamage,
                            EnergyType damageType, EffectSupport effectSupport, String unsupportedReasonZh,
                            java.util.List<ConditionEffect> conditions, CoinDamage coinDamage, CoinProtection coinProtection,
                            java.util.List<DamageModifier> damageModifiers) {
        this(id, nameZh, energyCost, baseDamage, damageType, effectSupport, unsupportedReasonZh, conditions, coinDamage, coinProtection,
            damageModifiers, DamageTarget.OPPONENT_ACTIVE, 0, 1, false, 0, 0, false);
    }
    /** Compatibility constructor for data with an explicit target but no recoil field. */
    public AttackDefinition(String id, String nameZh, Map<EnergyType, Integer> energyCost, int baseDamage,
                            EnergyType damageType, EffectSupport effectSupport, String unsupportedReasonZh,
                            java.util.List<ConditionEffect> conditions, CoinDamage coinDamage, CoinProtection coinProtection,
                            java.util.List<DamageModifier> damageModifiers, DamageTarget damageTarget) {
        this(id, nameZh, energyCost, baseDamage, damageType, effectSupport, unsupportedReasonZh, conditions, coinDamage, coinProtection,
            damageModifiers, damageTarget, 0, damageTarget == DamageTarget.ALL_OPPONENT_BENCH || damageTarget == DamageTarget.ALL_OPPONENT_POKEMON || damageTarget == DamageTarget.ALL_POKEMON ? 0 : 1, false, 0, 0, false);
    }
    /** Compatibility constructor for data with recoil but no explicit selection count. */
    public AttackDefinition(String id, String nameZh, Map<EnergyType, Integer> energyCost, int baseDamage,
                            EnergyType damageType, EffectSupport effectSupport, String unsupportedReasonZh,
                            java.util.List<ConditionEffect> conditions, CoinDamage coinDamage, CoinProtection coinProtection,
                            java.util.List<DamageModifier> damageModifiers, DamageTarget damageTarget, int selfDamage) {
        this(id, nameZh, energyCost, baseDamage, damageType, effectSupport, unsupportedReasonZh, conditions, coinDamage, coinProtection,
            damageModifiers, damageTarget, selfDamage, damageTarget == DamageTarget.ALL_OPPONENT_BENCH || damageTarget == DamageTarget.ALL_OPPONENT_POKEMON || damageTarget == DamageTarget.ALL_POKEMON ? 0 : 1, false, 0, 0, false);
    }
    /** Compatibility constructor for existing multi-target attack data without a post-attack Energy effect. */
    public AttackDefinition(String id, String nameZh, Map<EnergyType, Integer> energyCost, int baseDamage,
                            EnergyType damageType, EffectSupport effectSupport, String unsupportedReasonZh,
                            java.util.List<ConditionEffect> conditions, CoinDamage coinDamage, CoinProtection coinProtection,
                            java.util.List<DamageModifier> damageModifiers, DamageTarget damageTarget, int selfDamage, int targetCount) {
        this(id, nameZh, energyCost, baseDamage, damageType, effectSupport, unsupportedReasonZh, conditions, coinDamage, coinProtection,
            damageModifiers, damageTarget, selfDamage, targetCount, false, 0, 0, false);
    }
    /** Compatibility constructor for data that already uses post-attack Energy discard but not self-healing. */
    public AttackDefinition(String id, String nameZh, Map<EnergyType, Integer> energyCost, int baseDamage,
                            EnergyType damageType, EffectSupport effectSupport, String unsupportedReasonZh,
                            java.util.List<ConditionEffect> conditions, CoinDamage coinDamage, CoinProtection coinProtection,
                            java.util.List<DamageModifier> damageModifiers, DamageTarget damageTarget, int selfDamage, int targetCount,
                            boolean discardAllAttachedEnergyAfterAttack) {
        this(id, nameZh, energyCost, baseDamage, damageType, effectSupport, unsupportedReasonZh, conditions, coinDamage, coinProtection,
            damageModifiers, damageTarget, selfDamage, targetCount, discardAllAttachedEnergyAfterAttack, 0, 0, false);
    }
    /** Compatibility constructor for data that also uses self-healing but not selected Energy discard. */
    public AttackDefinition(String id, String nameZh, Map<EnergyType, Integer> energyCost, int baseDamage,
                            EnergyType damageType, EffectSupport effectSupport, String unsupportedReasonZh,
                            java.util.List<ConditionEffect> conditions, CoinDamage coinDamage, CoinProtection coinProtection,
                            java.util.List<DamageModifier> damageModifiers, DamageTarget damageTarget, int selfDamage, int targetCount,
                            boolean discardAllAttachedEnergyAfterAttack, int selfHealAfterAttack) {
        this(id, nameZh, energyCost, baseDamage, damageType, effectSupport, unsupportedReasonZh, conditions, coinDamage, coinProtection,
            damageModifiers, damageTarget, selfDamage, targetCount, discardAllAttachedEnergyAfterAttack, selfHealAfterAttack, 0, false);
    }
    /** Compatibility constructor for existing data before the explicit Weakness/Resistance override. */
    public AttackDefinition(String id, String nameZh, Map<EnergyType, Integer> energyCost, int baseDamage,
                            EnergyType damageType, EffectSupport effectSupport, String unsupportedReasonZh,
                            java.util.List<ConditionEffect> conditions, CoinDamage coinDamage, CoinProtection coinProtection,
                            java.util.List<DamageModifier> damageModifiers, DamageTarget damageTarget, int selfDamage, int targetCount,
                            boolean discardAllAttachedEnergyAfterAttack, int selfHealAfterAttack,
                            int discardSelectedAttachedEnergyAfterAttack) {
        this(id, nameZh, energyCost, baseDamage, damageType, effectSupport, unsupportedReasonZh, conditions, coinDamage, coinProtection,
            damageModifiers, damageTarget, selfDamage, targetCount, discardAllAttachedEnergyAfterAttack, selfHealAfterAttack,
            discardSelectedAttachedEnergyAfterAttack, false);
    }
    /** Compatibility constructor for data before attack-declared temporary damage reduction. */
    public AttackDefinition(String id, String nameZh, Map<EnergyType, Integer> energyCost, int baseDamage,
                            EnergyType damageType, EffectSupport effectSupport, String unsupportedReasonZh,
                            java.util.List<ConditionEffect> conditions, CoinDamage coinDamage, CoinProtection coinProtection,
                            java.util.List<DamageModifier> damageModifiers, DamageTarget damageTarget, int selfDamage, int targetCount,
                            boolean discardAllAttachedEnergyAfterAttack, int selfHealAfterAttack,
                            int discardSelectedAttachedEnergyAfterAttack, boolean ignoresWeaknessAndResistance) {
        this(id, nameZh, energyCost, baseDamage, damageType, effectSupport, unsupportedReasonZh, conditions, coinDamage, coinProtection,
            damageModifiers, damageTarget, selfDamage, targetCount, discardAllAttachedEnergyAfterAttack, selfHealAfterAttack,
            discardSelectedAttachedEnergyAfterAttack, ignoresWeaknessAndResistance, 0);
    }
    /** Compatibility constructor for data before active-defender damage-effect bypass. */
    public AttackDefinition(String id, String nameZh, Map<EnergyType, Integer> energyCost, int baseDamage,
                            EnergyType damageType, EffectSupport effectSupport, String unsupportedReasonZh,
                            java.util.List<ConditionEffect> conditions, CoinDamage coinDamage, CoinProtection coinProtection,
                            java.util.List<DamageModifier> damageModifiers, DamageTarget damageTarget, int selfDamage, int targetCount,
                            boolean discardAllAttachedEnergyAfterAttack, int selfHealAfterAttack,
                            int discardSelectedAttachedEnergyAfterAttack, boolean ignoresWeaknessAndResistance,
                            int damageReductionFromOpponentNextTurn) {
        this(id, nameZh, energyCost, baseDamage, damageType, effectSupport, unsupportedReasonZh, conditions, coinDamage, coinProtection,
            damageModifiers, damageTarget, selfDamage, targetCount, discardAllAttachedEnergyAfterAttack, selfHealAfterAttack,
            discardSelectedAttachedEnergyAfterAttack, ignoresWeaknessAndResistance, damageReductionFromOpponentNextTurn, false);
    }
    /** Compatibility constructor for data before a same-attack cooldown declaration. */
    public AttackDefinition(String id, String nameZh, Map<EnergyType, Integer> energyCost, int baseDamage,
                            EnergyType damageType, EffectSupport effectSupport, String unsupportedReasonZh,
                            java.util.List<ConditionEffect> conditions, CoinDamage coinDamage, CoinProtection coinProtection,
                            java.util.List<DamageModifier> damageModifiers, DamageTarget damageTarget, int selfDamage, int targetCount,
                            boolean discardAllAttachedEnergyAfterAttack, int selfHealAfterAttack,
                            int discardSelectedAttachedEnergyAfterAttack, boolean ignoresWeaknessAndResistance,
                            int damageReductionFromOpponentNextTurn, boolean ignoresActiveDefenderDamageEffects) {
        this(id, nameZh, energyCost, baseDamage, damageType, effectSupport, unsupportedReasonZh, conditions, coinDamage, coinProtection,
            damageModifiers, damageTarget, selfDamage, targetCount, discardAllAttachedEnergyAfterAttack, selfHealAfterAttack,
            discardSelectedAttachedEnergyAfterAttack, ignoresWeaknessAndResistance, damageReductionFromOpponentNextTurn,
            ignoresActiveDefenderDamageEffects, false);
    }
    public AttackDefinition {
        conditions = java.util.List.copyOf(conditions);
        damageModifiers = java.util.List.copyOf(damageModifiers);
        effectProgram = Objects.requireNonNull(effectProgram, "攻击 Effect 程序");
        Objects.requireNonNull(damageTarget, "攻击伤害目标");
        Objects.requireNonNull(targetFamilies,"攻击伤害目标家族");
        if(applyWeaknessResistanceToBench && (damageTarget!=DamageTarget.OPPONENT_ANY||targetCount!=1
            ||ignoresWeaknessAndResistance))
            throw new IllegalArgumentException("备战区弱点与抵抗力例外只支持任选对方一只宝可梦且实际计算弱点与抵抗力");
        if(targetHasSpecialEnergy && (damageTarget != DamageTarget.OPPONENT_ANY || targetCount != 1))
            throw new IllegalArgumentException("附着特殊能量限定目前只核验对方场上一只宝可梦");
        // Until targets can be reselected after a suspended pre-damage mutation, reject that unreviewed combination.
        if(targetHasSpecialEnergy && !effectProgram.forTrigger(EffectTrigger.BEFORE_ATTACK).isEmpty())
            throw new IllegalArgumentException("附着特殊能量目标限定与伤害前程序的组合尚未核验");
        if(!targetFamilies.equals(dev.hua.ptcglive.engine.effect.PokemonFamilyFilter.ANY)&&!damageTarget.mixedTargets()
            &&!(damageTarget==DamageTarget.OPPONENT_BENCH&&targetCount==1))
            throw new IllegalArgumentException("家族限定攻击目标须使用任选或全体宝可梦区域");
        if (damageTarget == DamageTarget.OPPONENT_ACTIVE && targetCount != 1)
            throw new IllegalArgumentException("战斗位攻击目标数量必须为 1");
        if (damageTarget == DamageTarget.OPPONENT_BENCH && (targetCount < 1 || targetCount > 5))
            throw new IllegalArgumentException("备战区攻击目标数量必须在 1 至 5 之间");
        if (damageTarget == DamageTarget.ALL_OPPONENT_BENCH && targetCount != 0)
            throw new IllegalArgumentException("全体备战攻击不能指定目标数量");
        if (damageTarget == DamageTarget.OPPONENT_ANY && (targetCount < 1 || targetCount > 6))
            throw new IllegalArgumentException("任选对方宝可梦的目标数量必须在 1 至 6 之间");
        if ((damageTarget == DamageTarget.ALL_OPPONENT_POKEMON || damageTarget == DamageTarget.ALL_POKEMON) && targetCount != 0)
            throw new IllegalArgumentException("对方全体宝可梦攻击不能指定目标数量");
        // The server already snapshots each actual damage recipient before applying damage.
        // Initially expose only the reviewed single-recipient damage-counter query.
        if (damageTarget.mixedTargets() && !damageModifiers.isEmpty()
            && !(damageTarget == DamageTarget.OPPONENT_ANY && targetCount == 1
                && damageModifiers.stream().allMatch(m -> m.basis() == DamageModifier.Basis.DEFENDER_DAMAGE_COUNTERS)))
            throw new IllegalArgumentException("任选／全体对方宝可梦伤害的逐目标伤害修正尚未接入");
        if (id == null || !id.matches("[a-z0-9-]+")) throw new IllegalArgumentException("攻击 ID 无效");
        if (nameZh == null || nameZh.isBlank()) throw new IllegalArgumentException("攻击名称为空");
        var safeCost = new LinkedHashMap<EnergyType, Integer>();
        Objects.requireNonNull(energyCost, "攻击费用").forEach((type, count) -> {
            if (type == null || count == null || count < 1) throw new IllegalArgumentException("攻击费用无效");
            safeCost.put(type, count);
        });
        energyCost = Map.copyOf(safeCost);
        if (baseDamage < 0 || baseDamage % 10 != 0) throw new IllegalArgumentException("基础伤害必须为非负的 10 的倍数");
        if (selfDamage < 0 || selfDamage % 10 != 0) throw new IllegalArgumentException("反伤必须为非负的 10 的倍数");
        if (selfHealAfterAttack < 0 || selfHealAfterAttack % 10 != 0) throw new IllegalArgumentException("攻击后回复量必须为非负的 10 的倍数");
        if (damageReductionFromOpponentNextTurn < 0 || damageReductionFromOpponentNextTurn > 1000
            || damageReductionFromOpponentNextTurn % 10 != 0)
            throw new IllegalArgumentException("攻击后临时减伤必须为 0 至 1000 的 10 的倍数");
        if (discardSelectedAttachedEnergyAfterAttack < 0 || discardSelectedAttachedEnergyAfterAttack > 10)
            throw new IllegalArgumentException("攻击后选择弃能数量必须在 0 至 10 之间");
        if (discardAllAttachedEnergyAfterAttack && discardSelectedAttachedEnergyAfterAttack > 0)
            throw new IllegalArgumentException("攻击不能同时声明全部弃能和选择弃能");
        Objects.requireNonNull(effectSupport, "攻击效果支持状态");
        if (effectSupport == EffectSupport.UNSUPPORTED && (unsupportedReasonZh == null || unsupportedReasonZh.isBlank()))
            throw new IllegalArgumentException("未支持攻击必须说明原因");
        if (damageTarget != DamageTarget.OPPONENT_ACTIVE && conditions.stream().anyMatch(effect -> effect.target() == ConditionEffect.Target.DEFENDER))
            throw new IllegalArgumentException("备战区伤害不能静默附加异常状态；请使用经审核的专用效果解释器");
        if ((damageTarget == DamageTarget.ALL_OPPONENT_BENCH || targetCount > 1) && !damageModifiers.isEmpty())
            throw new IllegalArgumentException("全体备战伤害的逐目标伤害修正必须使用经审核的专用效果解释器");
        if (ignoresActiveDefenderDamageEffects && damageTarget == DamageTarget.ALL_OPPONENT_BENCH)
            throw new IllegalArgumentException("无视目标防伤效果须逐只选择目标，不能用于整组备战区");
        if (damageTarget != DamageTarget.OPPONENT_ACTIVE
            && damageModifiers.stream().anyMatch(modifier -> modifier.basis() == DamageModifier.Basis.DEFENDER_RETREAT_COST))
            throw new IllegalArgumentException("按对方战斗宝可梦撤退费追加伤害只用于攻击对方战斗宝可梦的招式");
        if (ignoresResistance && ignoresWeaknessAndResistance)
            throw new IllegalArgumentException("“只无视抵抗力”与“无视弱点和抵抗力”不能同时声明");
        if (ignoresResistance && damageTarget != DamageTarget.OPPONENT_ACTIVE && !damageTarget.mixedTargets())
            throw new IllegalArgumentException("只有攻击对方战斗宝可梦时才计算抵抗力，备战伤害不能声明“只无视抵抗力”");
        if (ignoresWeakness && (ignoresWeaknessAndResistance || ignoresResistance))
            throw new IllegalArgumentException("“只无视弱点”不能与“无视抵抗力”同时声明；两者都无视时写“无视弱点和抵抗力”");
        if (ignoresWeakness && damageTarget != DamageTarget.OPPONENT_ACTIVE && !damageTarget.mixedTargets())
            throw new IllegalArgumentException("只有攻击对方战斗宝可梦时才计算弱点，备战伤害不能声明“只无视弱点”");
        if(mayAttackFirstTurn&&cannotAttackSecondPlayerFirstTurn||onlySecondPlayerFirstTurn&&(mayAttackFirstTurn||cannotAttackSecondPlayerFirstTurn))
            throw new IllegalArgumentException("同一招式不能同时声明先手首回合许可与后手首回合禁止");
    }
    /** The common basic template is available for every attack, independent of the printed card ID. */
    public BasicDamageAttack basicDamageTemplate() {
        java.util.List<EffectDefinition> onAttackEffects = effectProgram.forTrigger(EffectTrigger.ON_ATTACK).stream()
            .filter(binding -> binding.conditions().isEmpty())
            .flatMap(binding -> binding.effects().stream()).toList();
        return new BasicDamageAttack(nameZh, energyCost, baseDamage, onAttackEffects);
    }
    /** Returns the same data attack with a parsed component program, without introducing a per-card class. */
    public AttackDefinition withEffectProgram(EffectProgram program) {
        return new AttackDefinition(id, nameZh, energyCost, baseDamage, damageType, effectSupport, unsupportedReasonZh,
            conditions, coinDamage, coinProtection, damageModifiers, damageTarget, selfDamage, targetCount,
            discardAllAttachedEnergyAfterAttack, selfHealAfterAttack, discardSelectedAttachedEnergyAfterAttack,
            ignoresWeaknessAndResistance, damageReductionFromOpponentNextTurn, ignoresActiveDefenderDamageEffects,
            cannotUseSameAttackDuringNextTurn, program, usageLimit, ignoresResistance, ignoresWeakness,targetFamilies,
            mayUseFromBench,targetHasSpecialEnergy,mayAttackFirstTurn,cannotAttackSecondPlayerFirstTurn,onlySecondPlayerFirstTurn,useRequirement,
            applyWeaknessResistanceToBench);
    }
    public AttackDefinition withUsageLimit(dev.hua.ptcglive.engine.effect.SharedUsageLimit limit) {
        return new AttackDefinition(id, nameZh, energyCost, baseDamage, damageType, effectSupport, unsupportedReasonZh,
            conditions, coinDamage, coinProtection, damageModifiers, damageTarget, selfDamage, targetCount,
            discardAllAttachedEnergyAfterAttack, selfHealAfterAttack, discardSelectedAttachedEnergyAfterAttack,
            ignoresWeaknessAndResistance, damageReductionFromOpponentNextTurn, ignoresActiveDefenderDamageEffects,
            cannotUseSameAttackDuringNextTurn, effectProgram, limit, ignoresResistance, ignoresWeakness,targetFamilies,
            mayUseFromBench,targetHasSpecialEnergy,mayAttackFirstTurn,cannotAttackSecondPlayerFirstTurn,onlySecondPlayerFirstTurn,useRequirement,
            applyWeaknessResistanceToBench);
    }
    public int resolveBaseDamage(dev.hua.ptcglive.engine.ServerRandom random) {
        return resolveBaseDamage(random,false);
    }
    public int resolveBaseDamage(dev.hua.ptcglive.engine.ServerRandom random,boolean forceTails) {
        int damage = baseDamage;
        if (coinDamage != null) for (int i = 0; i < coinDamage.flips(); i++)
            if (random.coinHeads("attack-coin:" + id + ":" + i, forceTails)) damage += coinDamage.perHeads();
        return damage;
    }
    public int resolveDamageModifiers(DamageContext context) {
        int adjustment = 0;
        for (DamageModifier modifier : damageModifiers) adjustment += modifier.resolve(context);
        return adjustment;
    }
    public int resolveDamageModifiers(int defenderDamage) { return resolveDamageModifiers(new DamageContext(0, defenderDamage, 0, 0, 0, 0)); }
    public boolean resolvesProtection(dev.hua.ptcglive.engine.ServerRandom random) {
        return resolvesProtection(random,false);
    }
    public boolean resolvesProtection(dev.hua.ptcglive.engine.ServerRandom random,boolean forceTails) {
        if (coinProtection == null) return false;
        int heads = 0;
        for (int i = 0; i < coinProtection.flips(); i++) if (random.coinHeads("attack-protection-coin:" + id + ":" + i, forceTails)) heads++;
        return heads >= coinProtection.requiredHeads();
    }
}
