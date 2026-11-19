package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** Choose from the hand before seeing the top card; exchange exactly one card without shuffling. */
public final class HandTopDeckSwap {
    private HandTopDeckSwap() { }
    public static final Set<String> FIELDS=Set.of("amount","minimum");
    public static void validate(EffectDefinition effect) {
        if(!effect.type().equals("swap_hand_top_deck")||effect.target()!=EffectTarget.HAND||!effect.effects().isEmpty()
            ||!effect.arguments().keySet().equals(FIELDS)||!"1".equals(effect.arguments().get("amount"))||!"1".equals(effect.arguments().get("minimum")))
            throw new IllegalArgumentException("手牌与牌库顶交换须明确HAND、amount=1、minimum=1；不能先查看牌库顶");
    }
}
