package dev.hua.ptcglive.engine.effect;

import java.util.Map;

/** A turn-long exception for the printed recipients; the shared GX marker stays used. */
public record TurnGxPermission(boolean water) {
    public static final String TYPE="turn_gx_permission";
    public static TurnGxPermission from(EffectDefinition effect) {
        boolean water=effect.arguments().equals(Map.of("pokemon_type","WATER"));
        if(!effect.type().equals(TYPE)||effect.target()!=EffectTarget.SELF
            ||!water&&!effect.arguments().equals(Map.of("pokemon_name","Zygarde-GX"))
            ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
            throw new IllegalArgumentException("本回合GX许可只核验水属性或基格尔德GX，不能重置整局资格");
        return new TurnGxPermission(water);
    }
}
