package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/**
 * A card effect that makes its controller's Active Pokémon retreat without
 * paying Energy or consuming the ordinary once-per-turn retreat action.
 * Effects that require a paid retreat remain unsupported until their card
 * text can declare an audited payment contract.
 */
public final class RetreatEffect {
    public static final Set<String> FIELDS = Set.of("mode");
    private RetreatEffect() { }

    public static boolean handles(EffectDefinition effect) { return effect.type().equals("retreat"); }

    public static void validate(EffectDefinition effect) {
        if (!handles(effect) || effect.target() != EffectTarget.SELECT_OWN_POKEMON || !effect.effects().isEmpty())
            throw new IllegalArgumentException("RetreatEffect 必须由效果操控方选择己方备战宝可梦");
        if (!FIELDS.containsAll(effect.arguments().keySet()) || !"FREE".equals(effect.arguments().get("mode")))
            throw new IllegalArgumentException("当前只支持明确 mode=FREE 的卡牌效果撤退；支付费用的撤退不得按免费换位结算");
    }
}
