package dev.hua.ptcglive.engine.effect;
import java.util.Set;

/** The Defending Pokemon pays additional attack or retreat Energy during the next opposing turn. */
public final class AttackCostIncrease {
    private AttackCostIncrease() { }
    public static int amount(EffectDefinition effect) {
        if(!Set.of("increase_attack_cost","increase_retreat_cost").contains(effect.type())||effect.target()!=EffectTarget.OPPONENT_ACTIVE
            ||!effect.effects().isEmpty()||!effect.arguments().keySet().equals(Set.of("amount")))
            throw new IllegalArgumentException("攻击费用增加须指定对方战斗宝可梦和amount");
        int n=Integer.parseInt(effect.arguments().get("amount"));
        if(n<1||n>10)throw new IllegalArgumentException("攻击无色费用增加须为1至10");
        return n;
    }
}
