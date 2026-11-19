package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** Blind Prize positions first, then an equally sized ordered private hand selection. Not taking Prizes for a KO. */
public final class PrizeHandSwap {
    private PrizeHandSwap() { }
    public static int maximum(EffectDefinition effect) {
        if(!effect.type().equals("swap_prizes_hand")||effect.target()!=EffectTarget.SELF||!effect.effects().isEmpty()
            ||!effect.arguments().keySet().equals(Set.of("amount","minimum"))||!"1".equals(effect.arguments().get("minimum")))
            throw new IllegalArgumentException("奖赏交换须为SELF、至少1张且明确张数上限");
        int amount=Integer.parseInt(effect.arguments().get("amount"));
        if(amount<1||amount>6)throw new IllegalArgumentException("奖赏交换上限须为1至6");
        return amount;
    }
}
