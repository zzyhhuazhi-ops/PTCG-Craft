package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** Moves the specified player's whole hand, in server-randomized order, to that deck's bottom. */
public final class HandToDeckBottom {
    public static final String TYPE="hand_to_deck_bottom";
    public static final Set<String> FIELDS=Set.of("bind");
    private HandToDeckBottom() { }
    public static void validate(EffectDefinition effect) {
        if(!TYPE.equals(effect.type())||!Set.of(EffectTarget.SELF,EffectTarget.OPPONENT_ACTIVE).contains(effect.target())
            ||!effect.arguments().keySet().equals(FIELDS)||!effect.effects().isEmpty())
            throw new IllegalArgumentException("整手牌放牌库底须指定对手、实际移动数量绑定，且不接受子效果");
        AttackCountBindings.name(effect.arguments().get("bind"));
    }
}
