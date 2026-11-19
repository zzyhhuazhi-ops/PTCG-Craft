package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** Opponent-turn rule that prevents Pokémon with at most the printed number of attached Energy units from attacking. */
public record TurnAttackLock(int energyMaximum, boolean ownerNextTurn,boolean ownerCurrentTurn) {
    public static final Set<String> FIELDS=Set.of("energy_max","duration");
    public static TurnAttackLock from(EffectDefinition effect) {
        if(!effect.type().equals("turn_attack_lock")||effect.target()!=EffectTarget.SELF
            ||!effect.conditions().isEmpty()||!effect.effects().isEmpty()||!effect.arguments().keySet().equals(FIELDS)
            ||!Set.of("OPPONENT_NEXT_TURN","OWNER_NEXT_TURN","OWNER_CURRENT_TURN").contains(effect.arguments().get("duration")))
            throw new IllegalArgumentException("回合攻击限制须作用于对手或自己的下个回合且不含子效果");
        int maximum;
        try {maximum=Integer.parseInt(effect.arguments().get("energy_max"));}
        catch(NumberFormatException invalid){throw new IllegalArgumentException("攻击限制能量上限必须为整数",invalid);}
        if(maximum<0||maximum>60)throw new IllegalArgumentException("攻击限制能量上限须为0至60");
        return new TurnAttackLock(maximum,"OWNER_NEXT_TURN".equals(effect.arguments().get("duration")),
            "OWNER_CURRENT_TURN".equals(effect.arguments().get("duration")));
    }
}
