package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.field.AttackDefinition;
import java.util.Map;
import java.util.Set;

/** One attack shuffles the exact physical hand cards it previously revealed. */
public final class ShuffleRevealedHandCards {
    private ShuffleRevealedHandCards() { }
    public static final String TYPE="shuffle_revealed_hand_cards";
    public static final Set<String> FIELDS=Set.of("bind");

    public static String binding(EffectDefinition effect) {
        if(!effect.type().equals(TYPE)||effect.target()!=EffectTarget.HAND
            ||!effect.arguments().keySet().equals(FIELDS)
            ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
            throw new IllegalArgumentException("展示后洗回只接受先前展示的实体卡绑定");
        return AttackCountBindings.name(effect.arguments().get("bind"));
    }

    /** Keep the reveal, counted damage and same-batch shuffle in one declared attack. */
    public static void validateProgram(AttackDefinition attack) {
        var program=attack.effectProgram();
        boolean hasShuffle=program.triggers().stream().flatMap(t->t.effects().stream())
            .anyMatch(effect->contains(effect,TYPE));
        boolean hasRapidReveal=program.triggers().stream().flatMap(t->t.effects().stream())
            .anyMatch(effect->containsRapidReveal(effect));
        if(!hasShuffle&&!hasRapidReveal)return;
        if(program.triggers().size()!=3||attack.baseDamage()!=0
            ||attack.damageTarget()!=AttackDefinition.DamageTarget.OPPONENT_ACTIVE||attack.targetCount()!=1)
            throw new IllegalArgumentException("连击触手须从零基础伤害按展示张数计算单目标伤害");
        var before=program.forTrigger(EffectTrigger.BEFORE_ATTACK);
        var damage=program.forTrigger(EffectTrigger.ON_ATTACK);
        var after=program.forTrigger(EffectTrigger.AFTER_ATTACK);
        if(before.size()!=1||damage.size()!=1||after.size()!=1
            ||!singleUnconditionalEffect(before.getFirst())||!singleUnconditionalEffect(damage.getFirst())
            ||!singleUnconditionalEffect(after.getFirst()))
            throw new IllegalArgumentException("连击触手必须依次且仅执行展示、计伤、洗回三个阶段");
        var reveal=before.getFirst().effects().getFirst();
        SelectedHandReveal.validate(reveal);
        if(!SelectedHandReveal.rapidStrike(reveal))
            throw new IllegalArgumentException("连击触手必须展示连击卡牌");
        String bind=AttackCountBindings.name(reveal.arguments().get("bind"));
        var countedDamage=damage.getFirst().effects().getFirst();
        if(!countedDamage.type().equals("damage_per_bound")
            ||countedDamage.target()!=EffectTarget.OPPONENT_ACTIVE
            ||!countedDamage.arguments().equals(Map.of("amount","40","bind",bind))
            ||!countedDamage.conditions().isEmpty()||!countedDamage.effects().isEmpty())
            throw new IllegalArgumentException("连击触手伤害须为展示的同一批卡每张40点");
        if(!binding(after.getFirst().effects().getFirst()).equals(bind))
            throw new IllegalArgumentException("连击触手洗回必须引用刚才展示的同一批卡");
    }

    private static boolean singleUnconditionalEffect(TriggeredEffects trigger) {
        return trigger.conditions().isEmpty()&&trigger.effects().size()==1;
    }
    private static boolean contains(EffectDefinition effect,String type) {
        return effect.type().equals(type)||effect.effects().stream().anyMatch(child->contains(child,type));
    }
    private static boolean containsRapidReveal(EffectDefinition effect) {
        return SelectedHandReveal.rapidStrike(effect)
            ||effect.effects().stream().anyMatch(ShuffleRevealedHandCards::containsRapidReveal);
    }
}
