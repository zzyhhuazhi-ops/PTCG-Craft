package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.field.AttackDefinition;
import java.util.Map;

/** One independent coin for every opposing Pokemon; all results precede damage. */
public final class PerTargetCoinDamage {
    public static final String TYPE="per_target_coin_damage";
    private PerTargetCoinDamage() { }
    public static boolean handles(EffectDefinition effect) {return TYPE.equals(effect.type());}
    public static void validate(EffectDefinition effect) {
        if(!handles(effect)||effect.target()!=EffectTarget.ALL_OPPONENT_POKEMON
            ||!effect.arguments().equals(Map.of("amount","150"))
            ||!effect.effects().isEmpty()||!effect.conditions().isEmpty())
            throw new IllegalArgumentException("逐只掷币伤害须对对手全场每只独立掷币，正面150伤害");
    }
    public static boolean contains(AttackDefinition attack) {
        return attack.effectProgram().triggers().stream().flatMap(b->b.effects().stream()).anyMatch(PerTargetCoinDamage::handles);
    }
    public static void validate(AttackDefinition attack) {
        if(!contains(attack))return;
        var bindings=attack.effectProgram().triggers();
        if(attack.damageTarget()!=AttackDefinition.DamageTarget.ALL_OPPONENT_POKEMON
            ||attack.baseDamage()!=0||attack.coinDamage()!=null||attack.coinProtection()!=null
            ||!attack.conditions().isEmpty()||bindings.size()!=1
            ||bindings.getFirst().trigger()!=EffectTrigger.ON_ATTACK
            ||!bindings.getFirst().conditions().isEmpty()||bindings.getFirst().effects().size()!=1)
            throw new IllegalArgumentException("逐目标掷币须为独立全场攻击计算，不能混入其他阶段或硬币");
        validate(bindings.getFirst().effects().getFirst());
    }
}
