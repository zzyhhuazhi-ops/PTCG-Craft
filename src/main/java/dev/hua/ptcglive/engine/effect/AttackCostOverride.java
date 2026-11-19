package dev.hua.ptcglive.engine.effect;
import dev.hua.ptcglive.engine.field.EnergyType;
import java.util.*;

/** Printed “can be used for …” replaces the requirement, ignoring all cost increases and reductions. */
public final class AttackCostOverride {
    private AttackCostOverride() { }
    public static Map<EnergyType,Integer> from(EffectDefinition effect) {
        if(!effect.type().equals("attack_cost_override")||effect.target()!=EffectTarget.SELF||!effect.effects().isEmpty()
            ||!Set.of("energy_type","amount","attack_id").containsAll(effect.arguments().keySet())
            ||!effect.arguments().keySet().containsAll(Set.of("energy_type","amount")))throw new IllegalArgumentException("招式替代费用须指定SELF、energy_type和amount，可选attack_id");
        if(effect.arguments().containsKey("attack_id")&&!effect.arguments().get("attack_id").matches("[a-z0-9]+(?:-[a-z0-9]+)*"))
            throw new IllegalArgumentException("招式替代费用的attack_id无效");
        int count=Integer.parseInt(effect.arguments().get("amount"));if(count<0||count>10)throw new IllegalArgumentException("替代费用数量须为0至10");
        effect.conditions().forEach(AttackCostOverride::condition);
        return count==0?Map.of():Map.of(EnergyType.valueOf(effect.arguments().get("energy_type")),count);
    }
    private static void condition(ConditionDefinition c) {
        boolean lostProvisions=c.type()==ConditionType.LOST_ZONE_COUNT&&c.arguments().equals(Map.of(
            "target","SELF","value","4","comparison",">="));
        boolean weatherReading=c.type()==ConditionType.DISCARD_COUNT&&c.arguments().equals(Map.of(
            "target","SELF","value","8","comparison",">=","card_category","TRAINER","trainer_category","STADIUM"));
        if(lostProvisions||weatherReading) {
            if(!c.children().isEmpty())throw new IllegalArgumentException("免能量费用的区域数量条件不能嵌套");
            ResumableProgramRules.condition(c,true);
            return;
        }
        if(!Set.of(ConditionType.BECAME_ACTIVE_THIS_TURN,ConditionType.DAMAGE_COUNTER_COUNT,ConditionType.POKEMON_COUNT,ConditionType.DISCARD_COUNT,ConditionType.HAND_SIZE,ConditionType.HAS_STATUS,ConditionType.HAS_TOOL_DEFINITION).contains(c.type())
            ||c.type()!=ConditionType.DISCARD_COUNT&&EffectTarget.fromWire(c.arguments().getOrDefault("target","SELF"))!=EffectTarget.SELF
            ||c.type()==ConditionType.DISCARD_COUNT&&EffectTarget.fromWire(c.arguments().getOrDefault("target","SELF"))!=EffectTarget.OPPONENT_ACTIVE
            ||!c.children().isEmpty())
            throw new IllegalArgumentException("替代费用目前只支持自身本回合换入战斗位或伤害指示物条件");
        ResumableProgramRules.condition(c,true);
    }
    public static void validate(EffectProgram program) {
        var entries=program.forTrigger(EffectTrigger.PASSIVE);
        if(entries.size()>1)throw new IllegalArgumentException("同一招式的替代费用只能声明一次");
        for(var b:entries) {
            if(b.effects().size()!=1)throw new IllegalArgumentException("替代费用须为单一纯查询");
            b.conditions().forEach(AttackCostOverride::condition);from(b.effects().getFirst());
            if(b.conditions().isEmpty()&&b.effects().getFirst().conditions().isEmpty())throw new IllegalArgumentException("替代费用必须声明生效条件");
        }
    }
    public static Optional<Map<EnergyType,Integer>> current(EffectProgram program,EffectConditionContext context) {
        return current(program,context,null);
    }
    public static Optional<Map<EnergyType,Integer>> current(EffectProgram program,EffectConditionContext context,String attackId) {
        validate(program);
        for(var b:program.forTrigger(EffectTrigger.PASSIVE)) {
            var e=b.effects().getFirst();
            if(e.arguments().containsKey("attack_id")&&!e.arguments().get("attack_id").equals(attackId))continue;
            if(b.conditions().stream().allMatch(c->ConditionEvaluator.matches(c,context))&&e.conditions().stream().allMatch(c->ConditionEvaluator.matches(c,context)))return Optional.of(from(e));
        }
        return Optional.empty();
    }
}
