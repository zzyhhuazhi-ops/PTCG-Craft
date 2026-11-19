package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.field.EnergyProfile;
import dev.hua.ptcglive.engine.field.EnergyType;
import java.util.*;

/** Units left after paying this attack, including live cost changes and flexible Special Energy. */
public record ExtraAttackEnergyRequirement(EnergyType type,int amount) {
    public ExtraAttackEnergyRequirement {
        Objects.requireNonNull(type,"额外能量类型");
        if(amount<1||amount>60)throw new IllegalArgumentException("额外能量数量须为1至60");
    }
    public static ExtraAttackEnergyRequirement from(ConditionDefinition condition) {
        var args=condition.arguments();
        if(condition.type()!=ConditionType.HAS_EXTRA_ATTACK_ENERGY||!condition.children().isEmpty()
            ||!Set.of("target","energy_type","value").containsAll(args.keySet())
            ||!"SELF".equals(args.getOrDefault("target","SELF")))
            throw new IllegalArgumentException("额外能量条件只接受当前攻击者SELF、energy_type和value");
        return new ExtraAttackEnergyRequirement(EnergyType.valueOf(args.getOrDefault("energy_type","COLORLESS")),
            Integer.parseInt(args.get("value")));
    }
    public boolean matches(Collection<EnergyProfile> supplied,Map<EnergyType,Integer> currentCost) {
        var combined=new EnumMap<EnergyType,Integer>(EnergyType.class);
        combined.putAll(currentCost);
        combined.merge(type,amount,Math::addExact);
        // One unit may satisfy only one symbol across the original cost and the extra requirement.
        return EnergyProfile.canPay(supplied,combined);
    }
}
