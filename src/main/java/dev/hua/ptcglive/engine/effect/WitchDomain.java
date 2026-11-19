package dev.hua.ptcglive.engine.effect;

import java.util.Map;

/** One or two counters from any own Pokemon, moved together to the opposing Active. */
public final class WitchDomain {
    private WitchDomain() { }
    public static final String TYPE="redistribute_damage_counters";
    public static void validate(EffectDefinition effect) {
        if(!effect.type().equals(TYPE)||effect.target()!=EffectTarget.ALL_OWN_POKEMON
            ||!effect.arguments().equals(Map.of("maximum","2","destination","OPPONENT_ACTIVE"))
            ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
            throw new IllegalArgumentException("魔女领域须从己方任意宝可梦合计移动1至2个指示物至对手战斗宝可梦");
    }
}
