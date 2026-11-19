package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.field.AttackDefinition;
import java.util.*;

/** Distributes a fixed number of equal damage selections, allowing the same opposing Pokémon more than once. */
public final class RepeatTargetDamage {
    private RepeatTargetDamage() { }

    public static boolean handles(EffectDefinition effect) { return effect.type().equals("repeat_target_damage"); }
    public static boolean handles(AttackDefinition attack) {
        return attack.effectProgram().forTrigger(EffectTrigger.ON_ATTACK).stream()
            .flatMap(binding -> binding.effects().stream()).anyMatch(RepeatTargetDamage::handles);
    }
    public static void validate(AttackDefinition attack,EffectDefinition effect) {
        if(!handles(effect)||effect.target()!=EffectTarget.OPPONENT_BENCH
            ||!effect.arguments().keySet().equals(Set.of("count","amount"))||!effect.effects().isEmpty()
            ||attack.damageTarget()!=AttackDefinition.DamageTarget.OPPONENT_ANY||attack.baseDamage()!=0
            ||!attack.ignoresWeaknessAndResistance())
            throw new IllegalArgumentException("重复选目标计伤须为忽略弱点与抵抗力的对方任意宝可梦零基础伤害");
        int count=Integer.parseInt(effect.arguments().get("count"));
        int amount=Integer.parseInt(effect.arguments().get("amount"));
        if(count<1||count>60||attack.targetCount()!=count||amount<10||amount>1000||amount%10!=0)
            throw new IllegalArgumentException("重复选目标计伤的次数或伤害无效");
    }
    public static EffectDefinition effect(AttackDefinition attack) {
        var matches=attack.effectProgram().forTrigger(EffectTrigger.ON_ATTACK).stream()
            .flatMap(binding -> binding.effects().stream()).filter(RepeatTargetDamage::handles).toList();
        if(matches.size()!=1)throw new IllegalArgumentException("重复选目标计伤必须且只能声明一次");
        validate(attack,matches.getFirst());return matches.getFirst();
    }
}
