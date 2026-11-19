package dev.hua.ptcglive.engine.effect;

import java.util.Map;
import java.util.Set;

/** Attack-attempt coins precede Confusion and are not part of the declared attack's coin sequence. */
public record AttackCoinGate(int flips) {
    public static final Set<String> FIELDS=Set.of("flips");
    public AttackCoinGate {if(flips<1||flips>2)throw new IllegalArgumentException("攻击前掷币仅支持1或2次且须全部正面");}
    public static AttackCoinGate from(EffectDefinition effect) {
        if(!effect.type().equals("attack_coin_gate")||effect.target()!=EffectTarget.OPPONENT_ACTIVE||!effect.effects().isEmpty())
            throw new IllegalArgumentException("攻击前掷币须为对方战斗宝可梦的原子效果");
        if(effect.arguments().isEmpty())return new AttackCoinGate(1);
        if(effect.arguments().equals(Map.of("flips","2")))return new AttackCoinGate(2);
        throw new IllegalArgumentException("攻击前掷币只接受既有单次模式或flips:2");
    }
}
