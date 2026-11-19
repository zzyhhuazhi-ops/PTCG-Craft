package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.field.AttackDefinition;
import java.util.*;

/** BEFORE_ATTACK is printed pre-damage text after declaration/confusion; ON_ATTACK computes damage; AFTER_ATTACK completes text. */
public final class AttackProgramRules {
    private AttackProgramRules() { }
    public static Optional<String> unsupportedReason(AttackDefinition attack) {
        try { validate(attack); return Optional.empty(); }
        catch (IllegalArgumentException | IllegalStateException e) { return Optional.of(e.getMessage()); }
    }
    public static EffectProgram afterDamage(AttackDefinition attack) {
        return new EffectProgram(attack.effectProgram().forTrigger(EffectTrigger.AFTER_ATTACK));
    }
    public static EffectProgram beforeDamage(AttackDefinition attack) {
        return new EffectProgram(attack.effectProgram().forTrigger(EffectTrigger.BEFORE_ATTACK));
    }
    /**
     * A legacy SELECT_* target in the pre-damage program is chosen when the
     * attack is declared.  It is intentionally separate from a Bench damage
     * target and from lexical {@code select_pokemon} continuations.
     */
    public static EffectTarget beforeDamageSelectionTarget(AttackDefinition attack) {
        return ResumableProgramRules.selectionTarget(beforeDamage(attack));
    }
    public static void validate(AttackDefinition attack) {
        PerTargetCoinDamage.validate(attack);
        ShuffleRevealedHandCards.validateProgram(attack);
        if(CopyAttackProgram.containsCopy(attack)) { CopyAttackProgram.validate(attack); return; }
        var program=attack.effectProgram();
        for(var binding:program.forTrigger(EffectTrigger.ON_ATTACK))for(var effect:binding.effects())
            if(AttackTargetFilter.handles(effect)&&effect.target()==EffectTarget.POKEMON
                &&attack.damageTarget()!=AttackDefinition.DamageTarget.ALL_POKEMON)
                throw new IllegalArgumentException("双方全场目标筛选须与全场攻击范围一致");
        AttackDamageKoProtection.validateProgram(program);
        for(var b:program.triggers())if(b.trigger()!=EffectTrigger.AFTER_ATTACK&&(b.conditions().stream().anyMatch(AttackProgramRules::readsActualDamage)||b.effects().stream().anyMatch(AttackProgramRules::readsActualDamage)))throw new IllegalArgumentException("实际攻击伤害只能在伤害后读取");
        AttackCostOverride.validate(program);
        for(var binding:program.triggers()) {
            if(binding.trigger()!=EffectTrigger.PASSIVE&&binding.trigger()!=EffectTrigger.ON_ATTACK&&binding.trigger()!=EffectTrigger.AFTER_ATTACK&&binding.trigger()!=EffectTrigger.BEFORE_ATTACK)
                throw new IllegalArgumentException("此攻击触发时序尚未接入："+binding.trigger());
        }
        AttackCoinBindings.validate(program);
        AttackCountBindings.validate(program);
        PlannedEnergyDiscard.validate(program);
        AttackDeckWindow.validate(program);
        AttackPokemonBindings.validate(program);
        DirectKoLeavePlaySafety.validate(program);
        var before=beforeDamage(attack);
        if(!before.triggers().isEmpty()) {
            ResumableProgramRules.validate(before,true,EffectTrigger.BEFORE_ATTACK);
            if(contains(before,"return_to_hand")||contains(before,"shuffle_into_deck")||contains(before,"discard_pokemon"))
                throw new IllegalArgumentException("伤害前离场及重新绑定攻击目标的规则尚未核验");
        }
        if(!program.forTrigger(EffectTrigger.ON_ATTACK).isEmpty()) {
            if(attack.damageTarget().mixedTargets())for(var b:program.forTrigger(EffectTrigger.ON_ATTACK)) {
                if(!b.conditions().isEmpty())throw new IllegalArgumentException("任选／全体伤害的条件依赖尚未核验");
                b.effects().forEach(e->validateMixedTargetDamage(attack,e));
            }
            EffectResolutionPreflight.validate(program,EffectTrigger.ON_ATTACK,condition -> attackCondition(attack,condition),
                effect -> validateDamage(attack,effect),false,true);
            if (multipleBenchTargets(attack)) for (var binding : program.forTrigger(EffectTrigger.ON_ATTACK))
                for (var effect : binding.effects()) validateMultipleBenchTargets(effect);
        }
        var after=afterDamage(attack);
        if(after.triggers().isEmpty()) return;
        ResumableProgramRules.validate(after,true,EffectTrigger.AFTER_ATTACK);
    }
    static void attackCondition(AttackDefinition attack,ConditionDefinition condition) {
        if(condition.type()==ConditionType.RETREATED_LAST_OPPONENT_TURN) {
            EffectSchemaValidator.validate(condition);
            if(attack.damageTarget()!=AttackDefinition.DamageTarget.OPPONENT_BENCH||attack.targetCount()!=1)
                throw new IllegalArgumentException("上回合撤退条件只适用于单一对方备战伤害目标");
            return;
        }
        attackCondition(condition);
    }
    /** BEFORE/AFTER attack programs have no per-damage target and must not read its retreat history. */
    static void attackCondition(ConditionDefinition condition) {
        if(condition.type()==ConditionType.RETREATED_LAST_OPPONENT_TURN)
            throw new IllegalArgumentException("上回合撤退条件只能在ON_ATTACK读取本次所选备战伤害目标");
        if(condition.type()==ConditionType.HAS_EXTRA_ATTACK_ENERGY) ExtraAttackEnergyRequirement.from(condition);
        // Restrict live Retreat Cost queries to damage calculation. In a passive cost modifier
        // this query could recursively depend on the very modifier being evaluated.
        else if(condition.type()==ConditionType.RETREAT_COST) NumericConditionPolicy.validate(condition);
        else ResumableProgramRules.condition(condition,true);
    }
    private static boolean readsActualDamage(ConditionDefinition c) {return c.type()==ConditionType.ATTACK_DID_DAMAGE||c.children().stream().anyMatch(AttackProgramRules::readsActualDamage);}
    private static boolean readsActualDamage(EffectDefinition e) {return e.conditions().stream().anyMatch(AttackProgramRules::readsActualDamage)||e.effects().stream().anyMatch(AttackProgramRules::readsActualDamage);}
    private static boolean contains(EffectDefinition effect,String type) {
        if(effect.type().equals("delayed_effect"))return false;
        return effect.type().equals(type)||effect.effects().stream().anyMatch(e->contains(e,type));
    }
    private static boolean contains(EffectProgram program,String type) {
        return program.triggers().stream()
            .flatMap(binding -> binding.effects().stream())
            .anyMatch(effect -> contains(effect,type));
    }
    public static void validateDamage(AttackDefinition attack,EffectDefinition effect) {
        switch(effect.type()) {
            case "target_filter" -> AttackTargetFilter.validate(effect);
            case "per_target_coin_damage" -> {PerTargetCoinDamage.validate(effect);PerTargetCoinDamage.validate(attack);}
            case "damage_from_last_opponent_attack" -> PreviousAttackDamage.validate(effect);
            case "ignore_resistance" -> EffectSchemaValidator.validate(effect);
            case "conditional","coin_flip","repeat","repeat_per_heads","with_pokemon" -> effect.effects().forEach(child -> validateDamage(attack,child));
            case "damage" -> {
                if(effect.target()!=damageTarget(attack)) throw new IllegalArgumentException("攻击 DamageEffect 必须指定本次攻击的伤害目标");
                if(effect.arguments().containsKey("mode")
                    &&(attack.damageTarget()!=AttackDefinition.DamageTarget.OPPONENT_BENCH||attack.targetCount()!=1))
                    throw new IllegalArgumentException("攻击伤害替换只适用于单一对方备战目标");
                amount(effect,0,1000,true);
            }
            case "damage_multiplier" -> {
                if(effect.target()!=EffectTarget.SELF
                    && (attack.damageTarget()!=AttackDefinition.DamageTarget.OPPONENT_ACTIVE||effect.target()!=EffectTarget.OPPONENT_ACTIVE))
                    throw new IllegalArgumentException("攻击倍率必须作用于本次攻击伤害");
                amount(effect,0,10,false);
            }
            case "damage_per_discard_card" -> DiscardPileEffects.from(effect);
            case "damage_per_pokemon" -> PokemonCountEffect.from(effect);
            case "damage_per_energy" -> {
                EnergyCountQuery.from(effect.arguments());validateCounterTarget(attack,effect.target());amount(effect,0,1000,true);
            }
            case "damage_per_damage_counter","damage_per_bench" -> {
                validateCounterTarget(attack,effect.target());
                amount(effect,0,1000,true);
                if(effect.type().equals("damage_per_damage_counter")&&effect.arguments().containsKey("pokemon_types"))
                    PokemonTypeFilter.fromWire(effect.arguments().get("pokemon_types"));
            }
            case "damage_per_prize" -> {
                // Prize counts belong to players, independently of which opposing Pokemon is damaged.
                if(effect.target()!=EffectTarget.SELF&&effect.target()!=EffectTarget.OPPONENT_ACTIVE)
                    validateCounterTarget(attack,effect.target());
                amount(effect,0,1000,true);
                PrizeCountEffect.mode(effect);
            }
            // “Your opponent reveals their hand. This attack does N damage for each Trainer card you find there.”
            case "damage_per_hand_card" -> {
                if(attack.damageTarget()!=AttackDefinition.DamageTarget.OPPONENT_ACTIVE)
                    throw new IllegalArgumentException("按手牌张数计算的伤害只用于对方战斗宝可梦");
                amount(effect,10,1000,true);
                EffectSchemaValidator.validate(effect);
            }
            // “… this attack does N more damage for each card you discarded in this way.”
            case "damage_per_bound" -> {
                if(effect.target()!=damageTarget(attack)) throw new IllegalArgumentException("按攻击计数追加的伤害必须指定本次攻击的伤害目标");
                amount(effect,10,1000,true);
                EffectSchemaValidator.validate(effect);
            }
            case "damage_per_tails", "damage_per_special_condition" -> EffectSchemaValidator.validate(effect);
            case "repeat_target_damage" -> RepeatTargetDamage.validate(attack,effect);
            default -> throw new IllegalArgumentException("此攻击效果尚未声明正确伤害时序："+effect.type());
        }
    }
    /** Compatibility helper for active-only call sites outside a declared attack. */
    public static void validateDamage(EffectDefinition effect) {
        validateDamage(new AttackDefinition("damage-context","伤害上下文",Map.of(),0,null,
            dev.hua.ptcglive.rules.EffectSupport.SUPPORTED,null),effect);
    }
    private static EffectTarget damageTarget(AttackDefinition attack) {
        return switch (attack.damageTarget()) {
            case OPPONENT_ACTIVE -> EffectTarget.OPPONENT_ACTIVE;
            // Existing non-Active calculation token; W/R uses the actual declared recipient, not this token.
            case OPPONENT_BENCH, ALL_OPPONENT_BENCH, OPPONENT_ANY -> EffectTarget.OPPONENT_BENCH;
            case ALL_OPPONENT_POKEMON, ALL_POKEMON -> throw new IllegalArgumentException("全体宝可梦伤害的 ON_ATTACK 伤害组件尚未接入");
        };
    }
    private static void validateCounterTarget(AttackDefinition attack,EffectTarget target) {
        if(attack.damageTarget().mixedTargets()) {
            if(target!=EffectTarget.SELF&&target!=EffectTarget.OPPONENT_ACTIVE)
                throw new IllegalArgumentException("任选／全体伤害计数目前只读取自身或对方玩家");
            return;
        }
        if(attack.damageTarget()!=AttackDefinition.DamageTarget.OPPONENT_ACTIVE) {
            if(target!=EffectTarget.SELF&&target!=EffectTarget.OPPONENT_BENCH)
                throw new IllegalArgumentException("备战伤害的攻击计数只能读取攻击者或当前受击目标");
            return;
        }
        EnergyCountQuery.validateDamageTarget(target);
        if(target==EffectTarget.SELECT_OWN_POKEMON||target==EffectTarget.SELECT_OPPONENT_POKEMON)
            throw new IllegalArgumentException("攻击计数伤害尚未接入目标选择");
    }
    /** Pure quantities are evaluated against the same pre-damage field for each target, with no repeated coins or choices. */
    private static void validateMixedTargetDamage(AttackDefinition attack,EffectDefinition effect) {
        if(AttackTargetFilter.handles(effect)) {AttackTargetFilter.validate(effect);return;}
        if(PerTargetCoinDamage.handles(effect)) {PerTargetCoinDamage.validate(attack);return;}
        if(RepeatTargetDamage.handles(effect)){RepeatTargetDamage.validate(attack,effect);return;}
        if(attack.damageTarget()==AttackDefinition.DamageTarget.OPPONENT_ANY&&attack.targetCount()==1
            &&effect.type().equals("damage_per_bound")&&effect.conditions().isEmpty()&&effect.effects().isEmpty())return;
        if(attack.damageTarget()==AttackDefinition.DamageTarget.OPPONENT_ANY&&attack.targetCount()==1
            &&effect.type().equals("conditional")&&!effect.conditions().isEmpty()) {
            EffectSchemaValidator.validate(effect);
            effect.effects().forEach(e->validateMixedTargetDamage(attack,e));
            return;
        }
        if(attack.damageTarget()==AttackDefinition.DamageTarget.OPPONENT_ANY&&attack.targetCount()==1&&effect.conditions().isEmpty()) {
            if(effect.type().equals("coin_flip")||effect.type().equals("repeat_per_heads")) {
                effect.effects().forEach(e->validateMixedTargetDamage(attack,e));return;
            }
            if(effect.type().equals("damage")&&effect.effects().isEmpty())return;
        }
        if(!effect.conditions().isEmpty()||!effect.effects().isEmpty()
            ||!(effect.type().equals("damage_per_energy")&&effect.target()==EffectTarget.SELF
                ||effect.type().equals("damage_per_damage_counter")&&effect.target()==EffectTarget.SELF
                ||effect.type().equals("damage_per_prize")&&(effect.target()==EffectTarget.SELF||effect.target()==EffectTarget.OPPONENT_ACTIVE)))
            throw new IllegalArgumentException("任选／全体伤害目前只支持自身能量、伤害指示物及双方玩家奖赏数量的纯查询");
    }
    private static boolean multipleBenchTargets(AttackDefinition attack) {
        return attack.damageTarget()==AttackDefinition.DamageTarget.ALL_OPPONENT_BENCH
            || attack.damageTarget()==AttackDefinition.DamageTarget.OPPONENT_BENCH&&attack.targetCount()>1;
    }
    /**
     * A multi-Bench attack runs this effect program once per actual target.
     * Components that roll inside ON_ATTACK would otherwise spend a separate
     * random result for every target, and a named/selected target would no
     * longer mean the same thing as the current damaged Pokémon.  Keep those
     * combinations closed until their printed timing has a dedicated model.
     */
    private static void validateMultipleBenchTargets(EffectDefinition effect) {
        switch(effect.type()) {
            case "conditional", "repeat" -> effect.effects().forEach(AttackProgramRules::validateMultipleBenchTargets);
            case "damage", "damage_multiplier", "damage_per_energy", "damage_per_damage_counter", "damage_per_bench", "damage_per_prize", "damage_per_bound", "damage_per_pokemon" -> { }
            case "coin_flip", "repeat_per_heads" -> throw new IllegalArgumentException("多目标备战伤害组件不能在每个目标重复掷硬币");
            case "with_pokemon", "select_pokemon" -> throw new IllegalArgumentException("多目标备战伤害组件不能混用独立已选宝可梦目标");
            default -> throw new IllegalArgumentException("多目标备战伤害尚未支持此攻击组件："+effect.type());
        }
    }
    private static void amount(EffectDefinition effect,int min,int max,boolean units) {
        int value=Integer.parseInt(effect.arguments().get("amount"));
        if(value<min||value>max||units&&value%10!=0) throw new IllegalArgumentException("攻击 Effect 数值无效");
    }
}
