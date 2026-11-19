package dev.hua.ptcglive.engine.effect;

import java.util.Map;

/** Reviewed live scopes include later Pokémon and preserve their distinct turn-end boundaries. */
public final class TurnAbilitySuppression {
    private TurnAbilitySuppression() { }
    public static void validate(EffectDefinition effect) {
        if(!effect.type().equals("turn_ability_suppression")||effect.target()!=EffectTarget.SELF
            ||!effect.arguments().equals(Map.of("scope","OPPONENT_ACTIVE","duration","OWN_TURN_END"))
                &&!effect.arguments().equals(Map.of("scope","FIELD_HAND_DISCARD","duration","OPPONENT_TURN_END"))
            ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
            throw new IllegalArgumentException("特性压制须为本回合对手战斗位，或至对手回合末的双方场上、手牌、弃牌区");
    }
    public static boolean acrossZones(EffectDefinition effect) {validate(effect);return effect.arguments().get("scope").equals("FIELD_HAND_DISCARD");}
}
