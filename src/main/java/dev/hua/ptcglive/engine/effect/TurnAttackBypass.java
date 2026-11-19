package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** This-turn damage calculation permission; never removes post-damage survival or other attack effects. */
public final class TurnAttackBypass {
    private TurnAttackBypass() { }
    public static PokemonFamilyFilter from(EffectDefinition effect) {
        if(!effect.type().equals("turn_ignore_defender_damage_effects")||effect.target()!=EffectTarget.SELF
            ||!effect.arguments().keySet().equals(Set.of("pokemon_families"))
            ||!Set.of("ULTRA_BEAST","VMAX").contains(effect.arguments().get("pokemon_families"))
            ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
            throw new IllegalArgumentException("本回合穿透须明确究极异兽或VMAX，仅用于对手战斗位伤害计算");
        return PokemonFamilyFilter.fromWire(effect.arguments().get("pokemon_families"),null);
    }
}
