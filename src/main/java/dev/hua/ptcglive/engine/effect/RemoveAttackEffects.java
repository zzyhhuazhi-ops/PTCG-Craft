package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** A Supporter removes ongoing attack effects from the printed players and their in-play Pokémon. */
public final class RemoveAttackEffects {
    private RemoveAttackEffects() { }
    public static boolean both(EffectDefinition effect) {
        if(!effect.type().equals("remove_attack_effects") || effect.target()!=EffectTarget.SELF
            || !effect.arguments().keySet().equals(Set.of("scope"))
            || !Set.of("OWN","BOTH").contains(effect.arguments().get("scope"))
            || !effect.conditions().isEmpty() || !effect.effects().isEmpty())
            throw new IllegalArgumentException("清除招式效果必须明确指定己方或双方玩家与场上宝可梦");
        return effect.arguments().get("scope").equals("BOTH");
    }
}
