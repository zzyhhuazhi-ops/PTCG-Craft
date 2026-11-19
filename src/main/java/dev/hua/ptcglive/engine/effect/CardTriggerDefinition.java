package dev.hua.ptcglive.engine.effect;

import java.util.*;

/** Data-only automatic program for a Tool, Stadium or Energy card. Not a Pokémon Ability. */
public record CardTriggerDefinition(String id,String nameZh,EffectProgram effectProgram,AbilityEventPolicy eventPolicy,
                                    SharedUsageLimit usageLimit,boolean sourceCardOnly) implements AutomaticEffectDefinition {
    public CardTriggerDefinition(String id,String nameZh,EffectProgram program,AbilityEventPolicy policy,SharedUsageLimit usage) {this(id,nameZh,program,policy,usage,false);}
    public CardTriggerDefinition {
        if(id==null||!id.matches("[a-z0-9-]+")||nameZh==null||nameZh.isBlank())throw new IllegalArgumentException("自动效果须有稳定ID和中文名称");
        Objects.requireNonNull(effectProgram);Objects.requireNonNull(eventPolicy);
        if(eventPolicy instanceof BenchToActiveTriggerPolicy)throw new IllegalArgumentException("登上战斗位入口只允许宝可梦自身特性");
        if(eventPolicy instanceof DeckDiscardTriggerPolicy)throw new IllegalArgumentException("牌库弃置入口只允许宝可梦自身特性");
        if(sourceCardOnly&&!(eventPolicy instanceof EnergyTriggerPolicy))throw new IllegalArgumentException("来源卡限定只适用于附能事件");
        if(usageLimit!=null&&(usageLimit.group().equals(SharedUsageLimit.GX)||usageLimit.group().equals(SharedUsageLimit.VSTAR)))
            throw new IllegalArgumentException("工具／场地／能量自动效果不能占用宝可梦GX/VSTAR资格");
        if(eventPolicy instanceof CheckupTriggerPolicy checkup&&checkup.afterFailedSleepRecovery())
            throw new IllegalArgumentException("睡眠恢复失败目前只核验宝可梦自身特性");
        if(eventPolicy instanceof DamageTriggerPolicy damage&&damage.watch()!=EventPokemonScope.SELF)
            throw new IllegalArgumentException("跨宝可梦受伤监听目前只核验在场宝可梦的特性");
        TriggeredAbilityRules.validateProgram(effectProgram,eventPolicy,usageLimit);
    }
    public static List<CardTriggerDefinition> validated(List<CardTriggerDefinition> input,PassiveModifierProgram.Source source) {
        var result=List.copyOf(Objects.requireNonNull(input));var ids=new HashSet<String>();
        if(result.size()>32)throw new IllegalArgumentException("同一卡的自动效果数超过上限");
        for(var effect:result) {
            if(!ids.add(effect.id()))throw new IllegalArgumentException("同一卡的自动效果ID重复");
            if(effect.eventPolicy() instanceof TurnEndTriggerPolicy end&&end.sourceAttachedThisTurn()&&source!=PassiveModifierProgram.Source.ENERGY)
                throw new IllegalArgumentException("附着当回合结束目前仅支持能量卡");
            if(effect.sourceCardOnly()&&source!=PassiveModifierProgram.Source.ENERGY)throw new IllegalArgumentException("只有能量来源能监听自身附着");
            if(source!=PassiveModifierProgram.Source.TOOL&&effect.effectProgram().triggers().stream()
                .flatMap(binding->binding.effects().stream()).anyMatch(CardTriggerDefinition::discardsSourceAfterHealing))
                throw new IllegalArgumentException("回复后自弃仅支持附着在宝可梦上的工具卡");
            if(source==PassiveModifierProgram.Source.STADIUM) {
                if(effect.eventPolicy() instanceof DamageTriggerPolicy
                    ||effect.eventPolicy() instanceof EnergyTriggerPolicy e&&e.target()==EnergyTriggerPolicy.Target.SELF
                    ||effect.eventPolicy() instanceof KnockoutTriggerPolicy k&&k.watch()==EventPokemonScope.SELF
                    ||effect.eventPolicy() instanceof PokemonChangeTriggerPolicy p&&p.watch()==EventPokemonScope.SELF)
                    throw new IllegalArgumentException("场地没有附着宿主；事件监听须明确场上范围");
                for(var binding:effect.effectProgram().triggers()) {
                    binding.conditions().forEach(CardTriggerDefinition::stadiumCondition);
                    binding.effects().forEach(CardTriggerDefinition::stadiumEffect);
                }
            }
        }
        return result;
    }
    private static boolean discardsSourceAfterHealing(EffectDefinition effect) {
        return HealingEffect.discardSourceAfterHealing(effect)
            ||effect.effects().stream().anyMatch(CardTriggerDefinition::discardsSourceAfterHealing);
    }
    private static void stadiumEffect(EffectDefinition effect) {
        if(effect.target()==EffectTarget.SELF&&!Set.of("draw","conditional","choice","repeat","coin_flip","discard_source").contains(effect.type()))
            throw new IllegalArgumentException("场地效果没有SELF宝可梦；请明确公开场上目标");
        effect.conditions().forEach(CardTriggerDefinition::stadiumCondition);effect.effects().forEach(CardTriggerDefinition::stadiumEffect);
    }
    private static void stadiumCondition(ConditionDefinition condition) {
        condition.children().forEach(CardTriggerDefinition::stadiumCondition);
        if(EffectTarget.fromWire(condition.arguments().getOrDefault("target","SELF"))==EffectTarget.SELF
            &&Set.of(ConditionType.HAS_ENERGY,ConditionType.ENERGY_COUNT,ConditionType.DAMAGE_COUNTER_COUNT,ConditionType.HP_BELOW,ConditionType.HP_ABOVE,
                ConditionType.HAS_STATUS,ConditionType.TARGET_HAS_STATUS,ConditionType.IS_BASIC,ConditionType.IS_EVOLVED,ConditionType.IS_EX,
                ConditionType.IS_V,ConditionType.IS_GX,ConditionType.IS_VSTAR,ConditionType.IS_LEGACY_EX,ConditionType.IS_VMAX,ConditionType.IS_TERA,ConditionType.IS_TYPE,ConditionType.IS_FAMILY,ConditionType.BECAME_ACTIVE_THIS_TURN,ConditionType.KNOCKED_OUT_BY_ATTACK_LAST_TURN,ConditionType.KNOCKED_OUT_LAST_TURN,ConditionType.HEALED_THIS_TURN,ConditionType.HAS_TOOL,ConditionType.HAS_RULE_BOX,ConditionType.IS_ACTIVE).contains(condition.type()))
            throw new IllegalArgumentException("场地条件没有SELF宝可梦");
    }
}
