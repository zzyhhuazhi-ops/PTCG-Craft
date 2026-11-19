package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.field.EvolutionPermission;
import java.util.Set;

/** Reviewed hand evolution of the actual activated Ability source, never a deck search. */
public final class HandEvolutionEffect {
    private HandEvolutionEffect() { }
    public static final String TYPE = "evolve_from_hand";
    public static final Set<String> FIELDS = Set.of("amount", "minimum", "card_category", "timing", "bind");
    public static boolean handles(EffectDefinition effect) { return effect.type().equals(TYPE); }
    public static void validate(EffectDefinition effect) {
        if (!handles(effect) || effect.target() != EffectTarget.SELF || !effect.effects().isEmpty()
            || !effect.arguments().keySet().equals(FIELDS)
            || !"1".equals(effect.arguments().get("amount")) || !"1".equals(effect.arguments().get("minimum"))
            || !"POKEMON".equals(effect.arguments().get("card_category"))
            || !"AFTER_OWNER_FIRST_TURN".equals(effect.arguments().get("timing")))
            throw new IllegalArgumentException("手牌自我进化须明确自己首回合之后，从手牌选择恰好1张直接进化宝可梦并记录成功次数");
        AttackCountBindings.name(effect.arguments().get("bind"));
    }
    public static EvolutionPermission permission() { return new EvolutionPermission(false, true, true, false); }
}
