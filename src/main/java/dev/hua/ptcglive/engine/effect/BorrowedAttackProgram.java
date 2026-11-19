package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.EnergyType;
import java.util.Map;

/** Printed, payable attack usability from a live source; does not change ownership of printed attacks. */
public final class BorrowedAttackProgram {
    private BorrowedAttackProgram() { }
    public static boolean handles(String type) {return type.equals("borrow_attacks");}
    public static boolean contains(EffectDefinition effect) {return handles(effect.type())||effect.effects().stream().anyMatch(BorrowedAttackProgram::contains);}
    public static boolean contains(EffectProgram program) {return program.triggers().stream().flatMap(b->b.effects().stream()).anyMatch(BorrowedAttackProgram::contains);}
    public static void validate(EffectDefinition effect) {
        boolean bench=effect.target()==EffectTarget.SELF
            &&effect.arguments().equals(Map.of("source_scope","OWN_BENCH","cost_policy","SELECTED_ATTACK"));
        boolean discard=effect.target()==EffectTarget.SELF
            &&effect.arguments().equals(Map.of("source_scope","OWN_DISCARD","basic_pokemon_only","true",
                "rule_box","NONE","cost_policy","SELECTED_ATTACK"));
        boolean previous=effect.target()==EffectTarget.ALL_OWN_POKEMON
            &&effect.arguments().equals(Map.of("source","PREVIOUS_EVOLUTIONS"));
        boolean attached=effect.target()==EffectTarget.SELF&&effect.arguments().equals(Map.of("source","PREVIOUS_EVOLUTIONS"));
        boolean stadium=effect.target()==EffectTarget.SELF&&effect.arguments().equals(Map.of("source","PREVIOUS_EVOLUTIONS","both_players","true"));
        if(!handles(effect.type())||!effect.conditions().isEmpty()||!effect.effects().isEmpty()||!bench&&!discard&&!previous&&!attached&&!stadium)
            throw new IllegalArgumentException("借用招式须读取己方备战位、弃牌区基础非规则宝可梦，或己方宝可梦进化前的印刷招式");
    }
    public static void validate(EffectProgram program,PassiveModifierProgram.Source source) {
        validate(program);var effect=program.triggers().getFirst().effects().getFirst();
        boolean previous="PREVIOUS_EVOLUTIONS".equals(effect.arguments().get("source"));
        boolean valid=switch(source) {
            case ABILITY -> !previous||effect.target()==EffectTarget.ALL_OWN_POKEMON;
            case TOOL,ENERGY -> previous&&effect.target()==EffectTarget.SELF&&!effect.arguments().containsKey("both_players");
            case STADIUM -> previous&&"true".equals(effect.arguments().get("both_players"));
        };
        if(!valid)throw new IllegalArgumentException("借招范围与特性、附着卡或双方场地来源不符");
    }
    public static void validate(EffectProgram program) {
        var bindings=program.triggers();
        if(bindings.size()!=1||bindings.getFirst().trigger()!=EffectTrigger.PASSIVE||!bindings.getFirst().conditions().isEmpty()||bindings.getFirst().effects().size()!=1)
            throw new IllegalArgumentException("借招特性须为独立、无条件的PASSIVE根层授权");
        validate(bindings.getFirst().effects().getFirst());
    }
    public static boolean previousEvolutions(EffectProgram program) {
        validate(program);
        EffectDefinition effect=program.triggers().getFirst().effects().getFirst();
        return "PREVIOUS_EVOLUTIONS".equals(effect.arguments().get("source"));
    }
    public static boolean discardBasics(EffectProgram program) {
        validate(program);
        return "OWN_DISCARD".equals(program.triggers().getFirst().effects().getFirst().arguments().get("source_scope"));
    }
    public static AttackDefinition materialize(AttackDefinition selected,EnergyType holderType) {
        if(holderType==null)throw new IllegalArgumentException("借招缺少实际使用者的印刷属性");
        if(selected.effectSupport()!=dev.hua.ptcglive.rules.EffectSupport.SUPPORTED)throw new IllegalArgumentException("借用的印刷招式尚未支持："+selected.unsupportedReasonZh());
        AttackProgramRules.validate(selected);
        return new AttackDefinition(selected.id(),selected.nameZh(),selected.energyCost(),selected.baseDamage(),holderType,
            selected.effectSupport(),selected.unsupportedReasonZh(),selected.conditions(),selected.coinDamage(),selected.coinProtection(),
            selected.damageModifiers(),selected.damageTarget(),selected.selfDamage(),selected.targetCount(),selected.discardAllAttachedEnergyAfterAttack(),
            selected.selfHealAfterAttack(),selected.discardSelectedAttachedEnergyAfterAttack(),selected.ignoresWeaknessAndResistance(),
            selected.damageReductionFromOpponentNextTurn(),selected.ignoresActiveDefenderDamageEffects(),selected.cannotUseSameAttackDuringNextTurn(),
            selected.effectProgram(),selected.usageLimit(),selected.ignoresResistance(),selected.ignoresWeakness(),selected.targetFamilies(),selected.mayUseFromBench(),selected.targetHasSpecialEnergy(),selected.mayAttackFirstTurn(),selected.cannotAttackSecondPlayerFirstTurn(),selected.onlySecondPlayerFirstTurn(),selected.useRequirement(),selected.applyWeaknessResistanceToBench());
    }
}
