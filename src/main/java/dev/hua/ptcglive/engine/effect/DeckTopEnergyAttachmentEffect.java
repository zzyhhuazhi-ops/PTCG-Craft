package dev.hua.ptcglive.engine.effect;

import java.util.Map;

/** Celestial Roar: discard the actual top cards, then attach their Energy cards to its attacker. */
public final class DeckTopEnergyAttachmentEffect {
    private DeckTopEnergyAttachmentEffect() { }

    public static void validate(EffectDefinition effect) {
        if (!effect.type().equals("discard_top_attach_energy") || effect.target() != EffectTarget.SELF
            || !effect.arguments().equals(Map.of("top_count", "3", "card_category", "ENERGY"))
            || !effect.conditions().isEmpty() || !effect.effects().isEmpty())
            throw new IllegalArgumentException("弃牌库顶附能只接受无条件SELF、top_count=3、card_category=ENERGY");
    }
}
