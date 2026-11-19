package dev.hua.ptcglive.engine.effect;

import java.util.*;

/** Select cards for damage calculation, then discard those physical cards during attack effects. */
public final class PlannedEnergyDiscard {
    private PlannedEnergyDiscard() { }
    public static final String PLAN = "plan_energy_discard", COMMIT = "commit_energy_discard";
    public static final Set<String> PLAN_FIELDS = Set.of("amount", "minimum", "all", "energy_type", "card_kind", "bind", "count_mode");
    public static boolean handles(EffectDefinition effect) { return plan(effect) || commit(effect); }
    public static boolean plan(EffectDefinition effect) { return effect.type().equals(PLAN); }
    public static boolean commit(EffectDefinition effect) { return effect.type().equals(COMMIT); }

    public static AttachedEnergyEffect selection(EffectDefinition effect) {
        if (!plan(effect) || !PLAN_FIELDS.containsAll(effect.arguments().keySet())
            || !Set.of(EffectTarget.SELF, EffectTarget.OWN_ACTIVE, EffectTarget.OWN_BENCH, EffectTarget.ALL_OWN_POKEMON).contains(effect.target()))
            throw new IllegalArgumentException("伤害计数选能只接受己方附着能量卡与明确的计数名称");
        AttackCountBindings.name(effect.arguments().get("bind"));
        return AttachedEnergyEffect.from(new EffectDefinition("discard_energy", effect.target(), effect.arguments(), effect.conditions(), effect.effects()));
    }
    public static String reference(EffectDefinition effect) {
        if (!commit(effect) || effect.target() != EffectTarget.SELF || !effect.arguments().keySet().equals(Set.of("ref"))
            || !effect.conditions().isEmpty() || !effect.effects().isEmpty())
            throw new IllegalArgumentException("伤害后弃能只能引用服务器已选的能量，不接受目标、条件或嵌套效果");
        return AttackCountBindings.name(effect.arguments().get("ref"));
    }
    public static void validate(EffectDefinition effect) {
        if (plan(effect)) selection(effect); else reference(effect);
    }
    private static boolean contains(EffectDefinition effect) {
        return handles(effect) || effect.effects().stream().anyMatch(PlannedEnergyDiscard::contains);
    }
    /**
     * A plan is the final pre-damage step; its unconditional commit starts the after-damage text.
     * This leaves genuine pre-damage actions live, but prevents an intervening mutation from changing
     * a selected card before calculation. A choice may select one alternative or decline it;
     * each alternative has its own named commit, and an unchosen alternative commits nothing.
     */
    public static void validate(EffectProgram program) {
        var relevant = program.triggers().stream().flatMap(b -> b.effects().stream()).filter(PlannedEnergyDiscard::contains).toList();
        if (relevant.isEmpty()) return;
        var before = program.forTrigger(EffectTrigger.BEFORE_ATTACK);
        var after = program.forTrigger(EffectTrigger.AFTER_ATTACK);
        if (before.size() != 1 || after.size() != 1 || !after.getFirst().conditions().isEmpty()
            || before.getFirst().effects().isEmpty() || after.getFirst().effects().isEmpty())
            throw new IllegalArgumentException("选能计伤须配对伤害前选择和无条件伤害后弃置");
        var last = before.getFirst().effects().getLast();
        var alternatives = last.type().equals("choice") ? last.effects() : List.of(last);
        if (alternatives.isEmpty() || alternatives.stream().anyMatch(e -> !plan(e)))
            throw new IllegalArgumentException("计伤选能须为伤害前最后一步，不能在选能后改变场上状态");
        var names = new LinkedHashSet<String>();
        for (var selected : alternatives) {
            selection(selected);
            if (!names.add(selected.arguments().get("bind"))) throw new IllegalArgumentException("选能分支必须使用不同的计数名称");
        }
        var effects = after.getFirst().effects();
        if (effects.size() < names.size()) throw new IllegalArgumentException("每个选能分支都必须有伤害后弃置步骤");
        var commits = effects.subList(0,names.size());
        if (!commits.stream().map(PlannedEnergyDiscard::reference).toList().equals(new ArrayList<>(names)))
            throw new IllegalArgumentException("伤害后开头必须按序提交同一组服务器选能记录");
        // Reject duplicate, nested, delayed and wrong-phase plans/commits even on an unexecuted branch.
        if (relevant.size() != 1 + commits.size() || !relevant.contains(last) || !relevant.containsAll(commits))
            throw new IllegalArgumentException("一次攻击仅核验一组互斥的完整选能与弃能事务");
    }
}
