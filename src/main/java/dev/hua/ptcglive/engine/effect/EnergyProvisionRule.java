package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.field.EnergyProfile;
import java.util.*;

/** Ordered, first-matching printed provision branches; conditions read the current host, never supplied energy. */
public record EnergyProvisionRule(List<ConditionDefinition> conditions,List<EnergyProfile.Unit> units) {
    public EnergyProvisionRule {
        conditions=List.copyOf(Objects.requireNonNull(conditions));
        units=new EnergyProfile(units).units();
        if(conditions.isEmpty())throw new IllegalArgumentException("供能分支必须有条件；默认供能请写在energyUnits");
        int[] budget={4096};for(var condition:conditions)validate(condition,0,budget);
    }
    private static void validate(ConditionDefinition condition,int depth,int[] budget) {
        if(depth>32||--budget[0]<0)throw new IllegalArgumentException("供能条件超过安全上限");
        if(!condition.children().isEmpty()) {
            if(!condition.arguments().isEmpty())throw new IllegalArgumentException("复合供能条件不能带额外参数");
            for(var child:condition.children())validate(child,depth+1,budget);
            return;
        }
        if(condition.type()==ConditionType.ENERGY_COUNT||condition.type()==ConditionType.HAS_ENERGY)
            throw new IllegalArgumentException("供能条件不能依赖计算后的能量，避免循环依赖；此卡需补充专用规则数据");
        PassiveModifierProgram.condition(condition);
    }
    public boolean matches(EffectConditionContext state) {
        return conditions.stream().allMatch(c->ConditionEvaluator.matches(c,state));
    }
}
