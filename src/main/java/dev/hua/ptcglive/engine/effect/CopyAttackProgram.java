package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.EnergyType;
import java.util.Set;
import java.util.Map;

/** Data-defined “use as this attack”. This is not an Ability granting additional payable attacks. */
public final class CopyAttackProgram {
    private CopyAttackProgram() { }
    public static boolean apexDragon(AttackDefinition attack) {
        if(!"apex-dragon".equals(attack.id())||!containsCopy(attack))return false;
        validate(attack);
        return sourceScope(attack)==EffectTarget.DISCARD
            &&sourceFilters(attack).equals(Map.of("pokemon_types","DRAGON"))
            &&attack.energyCost().equals(Map.of(EnergyType.GRASS,2,EnergyType.FIRE,1));
    }
    public static boolean containsCopy(AttackDefinition attack) {
        return attack.effectProgram().triggers().stream().flatMap(t->t.effects().stream()).anyMatch(CopyAttackProgram::containsCopy);
    }
    private static boolean containsCopy(EffectDefinition effect) {
        return Set.of("copy_attack","coin_copy_attack","copy_attack_from_opponent_deck_top","copy_attack_from_own_deck_top").contains(effect.type())
            ||effect.effects().stream().anyMatch(CopyAttackProgram::containsCopy);
    }
    public static boolean coinGated(AttackDefinition attack) { return copyEffect(attack).type().equals("coin_copy_attack"); }
    public static boolean opponentChooses(AttackDefinition attack) {
        return containsCopy(attack) && "OPPONENT".equals(copyEffect(attack).arguments().get("chooser"));
    }
    /** Read the actual cards beneath this Pokémon, not a fixed evolutionary line in the catalog. */
    public static boolean previousEvolutions(AttackDefinition attack) {
        if(!containsCopy(attack))return false;
        var effect=copyEffect(attack);
        return effect.type().equals("copy_attack")&&effect.target()==EffectTarget.SELF
            &&"EVOLUTION_STACK".equals(effect.arguments().get("source_scope"));
    }
    public static boolean opponentDeckTop(AttackDefinition attack) { return copyEffect(attack).type().equals("copy_attack_from_opponent_deck_top"); }
    public static boolean ownDiscardedDeckTop(AttackDefinition attack) {return copyEffect(attack).type().equals("copy_attack_from_own_deck_top");}
    public static boolean deckTop(AttackDefinition attack) {return opponentDeckTop(attack)||ownDiscardedDeckTop(attack);}
    public static int topCount(AttackDefinition attack) {
        var effect=copyEffect(attack);validateCopyEffect(effect);return Integer.parseInt(effect.arguments().get("top_count"));
    }
    private static EffectDefinition copyEffect(AttackDefinition attack) {
        validateShape(attack);
        return attack.effectProgram().triggers().getFirst().effects().getFirst();
    }
    public static EffectDefinition definition(AttackDefinition attack) { validate(attack);return copyEffect(attack); }
    public static EffectTarget sourceScope(AttackDefinition attack) {
        validate(attack);
        var effect=copyEffect(attack);
        if(Set.of("copy_attack_from_opponent_deck_top","copy_attack_from_own_deck_top").contains(effect.type()))throw new IllegalArgumentException("牌库顶复制没有公开场上来源范围");
        return effect.target();
    }
    public static Map<String,String> sourceFilters(AttackDefinition attack) {
        validate(attack);
        var filters=new java.util.LinkedHashMap<>(copyEffect(attack).arguments());
        filters.remove("cost_policy");
        return Map.copyOf(filters);
    }
    /** The only audited conditional copy currently requires an empty Hand of the actual user. */
    public static boolean conditionSatisfied(AttackDefinition attack,int actorHandSize) {
        validate(attack);
        if(deckTop(attack))return true;
        if(actorHandSize<0)throw new IllegalArgumentException("手牌张数不能为负");
        return copyEffect(attack).conditions().isEmpty()||actorHandSize==0;
    }
    private static void validateCondition(EffectDefinition effect) {
        if(effect.conditions().isEmpty())return;
        if(effect.conditions().size()!=1)throw new IllegalArgumentException("复制攻击只核验自身无手牌条件");
        var condition=effect.conditions().getFirst();
        if(condition.type()!=ConditionType.HAND_SIZE||!condition.children().isEmpty()
            ||!condition.arguments().equals(java.util.Map.of("target","SELF","value","0","comparison","==")))
            throw new IllegalArgumentException("复制攻击只核验自身无手牌条件");
    }
    private static void validateShape(AttackDefinition attack) {
        var bindings=attack.effectProgram().triggers();
        if(bindings.size()!=1||bindings.getFirst().trigger()!=EffectTrigger.ON_ATTACK
            ||!bindings.getFirst().conditions().isEmpty()||bindings.getFirst().effects().size()!=1)
            throw new IllegalArgumentException("复制攻击必须是独立ON_ATTACK步骤；嵌套复制和复制前后额外卡文尚未核验");
    }
    public static void validateCopyEffect(EffectDefinition effect) {
        if(effect.type().equals("copy_attack")&&effect.arguments().containsKey("chooser")) {
            if(effect.target()!=EffectTarget.ALL_OPPONENT_POKEMON
                ||!effect.arguments().equals(Map.of("chooser","OPPONENT","cost_policy","DECLARED_ATTACK"))
                ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
                throw new IllegalArgumentException("对手选招式须来自对手全场，使用已宣告招式的费用");
            return;
        }
        if(effect.type().equals("copy_attack")&&effect.target()==EffectTarget.SELF) {
            if(!effect.arguments().equals(Map.of("source_scope","EVOLUTION_STACK","cost_policy","DECLARED_ATTACK"))
                ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
                throw new IllegalArgumentException("进化前招式复制仅允许当前宝可梦叠牌下层及原招式费用");
            return;
        }
        if(effect.type().equals("copy_attack")&&effect.target()==EffectTarget.DISCARD) {
            if(!effect.arguments().equals(Map.of("cost_policy","DECLARED_ATTACK","pokemon_types","DRAGON"))
                ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
                throw new IllegalArgumentException("弃牌区借招仅支持己方龙属性宝可梦，并由原招式支付能量");
            return;
        }
        if(effect.type().equals("copy_attack_from_own_deck_top")) {
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().equals(Map.of("top_count","1","cost_policy","DECLARED_ATTACK","discard","true","rule_box","NONE"))
                ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())throw new IllegalArgumentException("己方牌库顶弃置复制招式参数无效");
            return;
        }
        if(effect.type().equals("copy_attack_from_opponent_deck_top")) {
            if(effect.target()!=EffectTarget.OPPONENT_ACTIVE||!effect.arguments().keySet().equals(Set.of("top_count","cost_policy"))
                ||!"DECLARED_ATTACK".equals(effect.arguments().get("cost_policy"))||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
                throw new IllegalArgumentException("对手牌库顶复制招式参数无效");
            int count=Integer.parseInt(effect.arguments().get("top_count"));
            if(count<1||count>10)throw new IllegalArgumentException("对手牌库顶复制查看数量无效");
            return;
        }
        if(effect.type().equals("coin_copy_attack")) {
            if(!Set.of(EffectTarget.OPPONENT_ACTIVE,EffectTarget.ALL_OPPONENT_POKEMON).contains(effect.target())||!effect.arguments().equals(Map.of("cost_policy","DECLARED_ATTACK"))
                ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
                throw new IllegalArgumentException("掷币复制招式须指定对方战斗位或全场与已宣告费用策略");
            return;
        }
        if(!effect.type().equals("copy_attack")||!effect.effects().isEmpty()
            ||!Set.of("cost_policy","source_name_contains","source_family").containsAll(effect.arguments().keySet())
            ||!"DECLARED_ATTACK".equals(effect.arguments().get("cost_policy"))
            ||effect.arguments().containsKey("source_name_contains")&&effect.arguments().get("source_name_contains").isBlank()
            ||effect.arguments().containsKey("source_family")&&!"TERA".equals(effect.arguments().get("source_family")))
            throw new IllegalArgumentException("复制攻击需要明确费用策略；来源筛选只接受名称片段或TERA家族");
        validateCondition(effect);
    }
    public static void validate(AttackDefinition attack) {
        validateShape(attack);
        var effect=copyEffect(attack);
        validateCopyEffect(effect);
        if(deckTop(attack)) {
            validateAttackShell(attack);return;
        }
        if(previousEvolutions(attack)) {
            validateAttackShell(attack);return;
        }
        if(!Set.of(EffectTarget.DISCARD,EffectTarget.OPPONENT_ACTIVE,EffectTarget.OWN_BENCH,EffectTarget.OPPONENT_BENCH,
            EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON).contains(effect.target()))
            throw new IllegalArgumentException("复制来源必须是已支持的公开场上范围");
        validateAttackShell(attack);
    }
    private static void validateAttackShell(AttackDefinition attack) {
        if(attack.baseDamage()!=0||!attack.conditions().isEmpty()||attack.coinDamage()!=null||attack.coinProtection()!=null
            ||!attack.damageModifiers().isEmpty()||attack.damageTarget()!=AttackDefinition.DamageTarget.OPPONENT_ACTIVE
            ||attack.targetCount()!=1||attack.selfDamage()!=0||attack.discardAllAttachedEnergyAfterAttack()
            ||attack.selfHealAfterAttack()!=0||attack.discardSelectedAttachedEnergyAfterAttack()!=0
            ||attack.ignoresWeaknessAndResistance()||attack.damageReductionFromOpponentNextTurn()!=0
            ||attack.ignoresActiveDefenderDamageEffects()||attack.cannotUseSameAttackDuringNextTurn()||attack.ignoresResistance()
            ||attack.ignoresWeakness())
            throw new IllegalArgumentException("复制招式混有尚未定义先后顺序的伤害或额外效果，不能忽略这些字段");
    }
    public static AttackDefinition materialize(AttackDefinition declared,AttackDefinition selected,EnergyType attackerType) {
        validate(declared);
        if(attackerType==null)throw new IllegalArgumentException("复制攻击缺少实际攻击者的pokemonType资料，不能借用被复制卡的属性");
        if(containsCopy(selected))throw new IllegalArgumentException("连续复制或循环复制尚未核验，暂不能选择此招式");
        if(selected.effectSupport()!=dev.hua.ptcglive.rules.EffectSupport.SUPPORTED)
            throw new IllegalArgumentException("被复制攻击尚未支持："+selected.unsupportedReasonZh());
        AttackProgramRules.validate(selected);
        return new AttackDefinition(selected.id(),selected.nameZh(),declared.energyCost(),selected.baseDamage(),attackerType,
            selected.effectSupport(),selected.unsupportedReasonZh(),selected.conditions(),selected.coinDamage(),selected.coinProtection(),
            selected.damageModifiers(),selected.damageTarget(),selected.selfDamage(),selected.targetCount(),
            selected.discardAllAttachedEnergyAfterAttack(),selected.selfHealAfterAttack(),selected.discardSelectedAttachedEnergyAfterAttack(),
            selected.ignoresWeaknessAndResistance(),selected.damageReductionFromOpponentNextTurn(),selected.ignoresActiveDefenderDamageEffects(),
            selected.cannotUseSameAttackDuringNextTurn(),new EffectProgram(selected.effectProgram().triggers().stream().filter(b->b.trigger()!=EffectTrigger.PASSIVE).toList()),selected.usageLimit(),selected.ignoresResistance(),
            selected.ignoresWeakness(),selected.targetFamilies(),false,selected.targetHasSpecialEnergy(),selected.mayAttackFirstTurn(),selected.cannotAttackSecondPlayerFirstTurn(),selected.onlySecondPlayerFirstTurn(),selected.useRequirement(),selected.applyWeaknessResistanceToBench());
    }
}
