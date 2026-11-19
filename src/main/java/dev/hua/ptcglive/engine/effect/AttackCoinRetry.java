package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.EnergyType;
import java.util.*;

/** A live Tool or Stadium permission, never an automatic replacement of tails. */
public record AttackCoinRetry(EnergyType pokemonType) {
    public static boolean handles(EffectDefinition effect){return effect.type().equals("reroll_attack_coins");}
    public static AttackCoinRetry from(EffectDefinition effect) {
        if(!handles(effect)||effect.target()!=EffectTarget.SELF||!Set.of("pokemon_type").containsAll(effect.arguments().keySet())
            ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
            throw new IllegalArgumentException("攻击重投只接受可选的宝可梦属性，不接受其他参数或嵌套效果");
        return new AttackCoinRetry(effect.arguments().containsKey("pokemon_type")?EnergyType.valueOf(effect.arguments().get("pokemon_type")):null);
    }
    public static boolean contains(EffectProgram program){return program.triggers().stream().flatMap(b->b.effects().stream()).anyMatch(AttackCoinRetry::contains);}
    private static boolean contains(EffectDefinition effect){return handles(effect)||effect.effects().stream().anyMatch(AttackCoinRetry::contains);}
    public static AttackCoinRetry fromProgram(EffectProgram program) {
        if(!contains(program))return null;
        if(program.triggers().size()!=1)throw new IllegalArgumentException("攻击重投须为单独的持续程序");
        var binding=program.triggers().getFirst();
        if(binding.trigger()!=EffectTrigger.PASSIVE||!binding.conditions().isEmpty()||binding.effects().size()!=1)
            throw new IllegalArgumentException("攻击重投须为唯一无条件PASSIVE授权");
        return from(binding.effects().getFirst());
    }
    /** A single complete sequence at any attack phase. Never retry just the last group. */
    public static void requireSingleSequence(AttackDefinition attack) {
        int count=(attack.coinDamage()==null?0:1)+(attack.coinProtection()==null?0:1);
        for(var status:attack.conditions())if(status.coinCondition()!=null)count++;
        for(var binding:attack.effectProgram().triggers())for(var effect:binding.effects())
            count+=count(effect,!binding.conditions().isEmpty(),false);
        if(count>1)throw new IllegalArgumentException("此攻击的跨阶段多段硬币重投规则尚未支持");
    }
    private static int count(EffectDefinition effect,boolean conditional,boolean nested) {
        int n=0;
        if(effect.type().equals("coin_flip")) {
            if(nested||!effect.conditions().isEmpty()||CoinFlipPolicy.from(effect).reroll()!=CoinFlipPolicy.Reroll.NONE)
                throw new IllegalArgumentException("此攻击的条件、嵌套或卡文内重投规则尚未支持");
            n++;
        }
        for(var child:effect.effects())n+=count(child,conditional,nested||!effect.type().equals("conditional"));
        return n;
    }
    private static boolean hasCoin(EffectDefinition effect){return effect.type().equals("coin_flip")||effect.effects().stream().anyMatch(AttackCoinRetry::hasCoin);}
    public static boolean hasDamageCoin(AttackDefinition attack){return attack.effectProgram().forTrigger(EffectTrigger.ON_ATTACK).stream().flatMap(b->b.effects().stream()).anyMatch(AttackCoinRetry::hasCoin);}
    /** A complete set of this attack's coins can be planned in its pure damage phase. */
    public static boolean damagePhaseOnly(AttackDefinition attack) {
        if(attack.coinDamage()!=null||attack.coinProtection()!=null||attack.conditions().stream().anyMatch(c->c.coinCondition()!=null))return false;
        return hasDamageCoin(attack)&&attack.effectProgram().triggers().stream().filter(b->b.trigger()!=EffectTrigger.ON_ATTACK)
            .flatMap(b->b.effects().stream()).noneMatch(AttackCoinRetry::hasCoin);
    }
    public static void requireSupportedPlan(AttackDefinition attack) {
        if(PerTargetCoinDamage.contains(attack)) {PerTargetCoinDamage.validate(attack);return;}
        if(damagePhaseOnly(attack)) {
            AttackProgramRules.validate(attack);
            for(var b:attack.effectProgram().forTrigger(EffectTrigger.ON_ATTACK))b.effects().forEach(AttackCoinRetry::rejectPrintedRetry);
        } else requireSingleSequence(attack);
    }
    private static void rejectPrintedRetry(EffectDefinition effect) {
        if(effect.type().equals("coin_flip")&&CoinFlipPolicy.from(effect).reroll()!=CoinFlipPolicy.Reroll.NONE)
            throw new IllegalArgumentException("卡文内置重投尚需明确的整次攻击规则");
        effect.effects().forEach(AttackCoinRetry::rejectPrintedRetry);
    }
}
