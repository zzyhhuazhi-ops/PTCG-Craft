package dev.hua.ptcglive.engine.effect;

import java.util.*;

/** A self-applied attack effect: signed damage change after Weakness/Resistance on the next opposing turn. */
public record AttackDamageReceivedModifier(int amount, AttackSourceFilter attackers) {
    public static final Set<String> FIELDS=Set.of("amount","duration","attacker_filter");
    public AttackDamageReceivedModifier {
        Objects.requireNonNull(attackers);
        if(amount==0||Math.abs((long)amount)>1000||amount%10!=0)
            throw new IllegalArgumentException("临时受伤修正须为非零的10倍数，范围-1000至1000");
        if(!Set.of(AttackSourceFilter.Kind.ANY,AttackSourceFilter.Kind.EVOLUTION).contains(attackers.kind())
            ||!attackers.excludedNames().isEmpty()||attackers.typeRestricted()||attackers.dynamicRestricted())
            throw new IllegalArgumentException("临时受伤修正目前只核验任意或进化攻击者");
    }
    public static AttackDamageReceivedModifier from(EffectDefinition effect) {
        if(!effect.type().equals("modify_damage_taken")||effect.target()!=EffectTarget.SELF||!effect.effects().isEmpty()
            ||!FIELDS.containsAll(effect.arguments().keySet())||!"OPPONENT_NEXT_TURN".equals(effect.arguments().get("duration")))
            throw new IllegalArgumentException("临时受伤修正须指定SELF、amount和OPPONENT_NEXT_TURN，不接受额外参数或子效果");
        int amount;
        try {amount=Integer.parseInt(effect.arguments().get("amount"));}
        catch(RuntimeException bad){throw new IllegalArgumentException("临时受伤修正缺少整数amount",bad);}
        return new AttackDamageReceivedModifier(amount,AttackSourceFilter.from(effect.arguments()));
    }
    public String labelZh(){return "持续效果：下个对手回合，受到"+(attackers.kind()==AttackSourceFilter.Kind.EVOLUTION?"进化宝可梦的":"")
        +"招式伤害"+(amount>0?"增加 ":"减少 ")+Math.abs(amount)+"（计算弱点和抵抗力后）";}
}
