package dev.hua.ptcglive.engine.field;

import java.util.*;

/**
 * Server-side energy provision facts. Each unit can provide one of its listed
 * types for a single attack-cost unit. This represents Basic Energy, a
 * choose-one-type Special Energy, and multi-unit Special Energy without
 * letting a client choose or duplicate a payment unit.
 */
public record EnergyProfile(List<Unit> units,dev.hua.ptcglive.engine.effect.EffectProgram passiveProgram,
                            List<dev.hua.ptcglive.engine.effect.EnergyProvisionRule> provisionRules,
                            List<dev.hua.ptcglive.engine.effect.CardTriggerDefinition> automaticEffects,
                            dev.hua.ptcglive.engine.effect.EnergyAttachmentRule attachmentRule,int handDiscardCost) {
    public EnergyProfile(List<Unit> units,dev.hua.ptcglive.engine.effect.EffectProgram program,List<dev.hua.ptcglive.engine.effect.EnergyProvisionRule> rules,
            List<dev.hua.ptcglive.engine.effect.CardTriggerDefinition> automaticEffects,dev.hua.ptcglive.engine.effect.EnergyAttachmentRule attachmentRule) {
        this(units,program,rules,automaticEffects,attachmentRule,0);
    }
    public EnergyProfile(List<Unit> units,dev.hua.ptcglive.engine.effect.EffectProgram program,List<dev.hua.ptcglive.engine.effect.EnergyProvisionRule> rules,
                         List<dev.hua.ptcglive.engine.effect.CardTriggerDefinition> automaticEffects) {this(units,program,rules,automaticEffects,dev.hua.ptcglive.engine.effect.EnergyAttachmentRule.NONE);}
    public EnergyProfile(List<Unit> units,dev.hua.ptcglive.engine.effect.EffectProgram program,List<dev.hua.ptcglive.engine.effect.EnergyProvisionRule> provisionRules) {this(units,program,provisionRules,List.of());}
    public EnergyProfile(List<Unit> units) {this(units,dev.hua.ptcglive.engine.effect.EffectProgram.EMPTY);}
    public EnergyProfile(List<Unit> units,dev.hua.ptcglive.engine.effect.EffectProgram passiveProgram) {this(units,passiveProgram,List.of());}
    public record Unit(Set<EnergyType> selectableTypes) {
        public Unit {
            selectableTypes = Set.copyOf(Objects.requireNonNull(selectableTypes, "能量可提供类型"));
            if (selectableTypes.isEmpty()) throw new IllegalArgumentException("能量单位至少需要一种可提供类型");
        }
    }
    public EnergyProfile {
        units = List.copyOf(Objects.requireNonNull(units, "能量单位"));
        if (units.size() > 4) throw new IllegalArgumentException("一张能量卡可提供的能量单位必须在 0 至 4 之间");
        if(handDiscardCost<0||handDiscardCost>1)throw new IllegalArgumentException("已审核手牌附能费用只允许0或1张");
        attachmentRule=Objects.requireNonNull(attachmentRule,"能量附着限制");
        provisionRules=List.copyOf(Objects.requireNonNull(provisionRules));
        automaticEffects=dev.hua.ptcglive.engine.effect.CardTriggerDefinition.validated(automaticEffects,dev.hua.ptcglive.engine.effect.PassiveModifierProgram.Source.ENERGY);
        if(provisionRules.size()>64)throw new IllegalArgumentException("一张能量的条件供能分支超过安全上限");
        dev.hua.ptcglive.engine.effect.PassiveModifierProgram.validate(passiveProgram,dev.hua.ptcglive.engine.effect.PassiveModifierProgram.Source.ENERGY);
    }
    public static EnergyProfile basic(EnergyType type) { return new EnergyProfile(List.of(new Unit(Set.of(Objects.requireNonNull(type, "能量类型"))))); }
    public static EnergyProfile fixed(EnergyType type, int units) {
        if (units < 1 || units > 4) throw new IllegalArgumentException("固定能量单位必须在 1 至 4 之间");
        List<Unit> result = new ArrayList<>(); for (int i = 0; i < units; i++) result.add(new Unit(Set.of(type)));
        return new EnergyProfile(result);
    }
    /** Number of generic energy units this physical Energy card supplies. */
    public int unitCount() { requireResolved();return units.size(); }
    /** Exact provided type, unlike generic Colorless cost. A multitype unit contributes at most one. */
    public int unitsOfType(EnergyType type) {
        requireResolved();Objects.requireNonNull(type,"能量计数类型");
        return (int)units.stream().filter(unit->unit.selectableTypes().contains(type)).count();
    }
    /** A match resolves live conditions before every query. No cached answer survives a state change. */
    public EnergyProfile resolve(dev.hua.ptcglive.engine.effect.EffectConditionContext state) {
        for(var rule:provisionRules)if(rule.matches(state))return new EnergyProfile(rule.units(),passiveProgram,List.of(),automaticEffects,attachmentRule,handDiscardCost);
        return provisionRules.isEmpty()?this:new EnergyProfile(units,passiveProgram,List.of(),automaticEffects,attachmentRule,handDiscardCost);
    }
    private void requireResolved() {
        if(!provisionRules.isEmpty())throw new IllegalStateException("动态能量必须先通过服务器当前场上状态解析，不能使用默认供能代替");
    }
    /**
     * Retreat costs are generic: they consume energy units irrespective of
     * colour. A multi-unit Special Energy is still one physical card and is
     * discarded as one card when selected for a retreat payment.
     */
    public static int totalUnits(Collection<EnergyProfile> attached) {
        Objects.requireNonNull(attached, "附着能量");
        int total = 0;
        for (EnergyProfile profile : attached) {
            if (profile == null) throw new IllegalArgumentException("附着能量缺少供给档案");
            total += profile.unitCount();
        }
        return total;
    }
    /**
     * Requires sufficient generic units and rejects a selected energy card
     * which is unnecessary for the payment. This permits one double-energy
     * card to pay a one-unit Retreat Cost, but not two Basic Energy cards.
     */
    public static boolean isMinimalGenericPayment(Collection<EnergyProfile> selected, int requiredUnits) {
        if (requiredUnits < 0) throw new IllegalArgumentException("撤退费用不能为负数");
        List<EnergyProfile> profiles = List.copyOf(Objects.requireNonNull(selected, "支付能量"));
        int total = totalUnits(profiles);
        if (total < requiredUnits) return false;
        for (EnergyProfile profile : profiles)
            if (total - profile.unitCount() >= requiredUnits) return false;
        return true;
    }
    /** Every selected card contributes at least one chosen unit; its remaining units leave with the indivisible card. */
    public static boolean isValidGenericPayment(Collection<EnergyProfile> selected, int requiredUnits) {
        if(requiredUnits<0)throw new IllegalArgumentException("撤退费用不能为负数");
        var profiles=List.copyOf(Objects.requireNonNull(selected,"支付能量"));
        return profiles.size()<=requiredUnits && profiles.stream().allMatch(p->p.unitCount()>0) && totalUnits(profiles)>=requiredUnits;
    }
    /**
     * Determines payment by bipartite matching: each physical provided unit is
     * spent at most once, colored requirements reserve compatible units first,
     * and generic Colorless requirements consume any remaining unit.
     */
    public static boolean canPay(Collection<EnergyProfile> attached, Map<EnergyType, Integer> cost) {
        Objects.requireNonNull(attached, "附着能量"); Objects.requireNonNull(cost, "攻击费用");
        List<Set<EnergyType>> supplied = new ArrayList<>();
        for (EnergyProfile profile : attached) {
            if (profile == null) throw new IllegalArgumentException("附着能量缺少供给档案");
            profile.requireResolved();
            for (Unit unit : profile.units()) supplied.add(unit.selectableTypes());
        }
        List<EnergyType> colored = new ArrayList<>(); int colorless = 0;
        for (Map.Entry<EnergyType, Integer> entry : cost.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() < 1) throw new IllegalArgumentException("攻击费用无效");
            if (entry.getKey() == EnergyType.COLORLESS) colorless += entry.getValue();
            else for (int i = 0; i < entry.getValue(); i++) colored.add(entry.getKey());
        }
        if (supplied.size() < colored.size() + colorless) return false;
        int[] assignedRequirement = new int[supplied.size()]; Arrays.fill(assignedRequirement, -1);
        for (int requirement = 0; requirement < colored.size(); requirement++) {
            if (!assign(requirement, colored, supplied, assignedRequirement, new boolean[supplied.size()])) return false;
        }
        int unused = 0; for (int assignment : assignedRequirement) if (assignment < 0) unused++;
        return unused >= colorless;
    }
    private static boolean assign(int requirement, List<EnergyType> colored, List<Set<EnergyType>> supplied,
                                  int[] assignedRequirement, boolean[] visited) {
        for (int unit = 0; unit < supplied.size(); unit++) {
            if (visited[unit] || !supplied.get(unit).contains(colored.get(requirement))) continue;
            visited[unit] = true;
            if (assignedRequirement[unit] < 0 || assign(assignedRequirement[unit], colored, supplied, assignedRequirement, visited)) {
                assignedRequirement[unit] = requirement; return true;
            }
        }
        return false;
    }
}
