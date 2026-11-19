package dev.hua.ptcglive.engine.effect;

/** One simultaneous attachment operation, not two sequential discard effects. */
public final class DiscardToolsAndSpecialEnergy {
    public static final String TYPE = "discard_tools_and_special_energy";
    private DiscardToolsAndSpecialEnergy() { }

    public static void validate(EffectDefinition effect) {
        if (!TYPE.equals(effect.type()) || !java.util.Set.of(EffectTarget.OPPONENT_ACTIVE,EffectTarget.ALL_OPPONENT_POKEMON).contains(effect.target())
            || !effect.arguments().isEmpty() || !effect.conditions().isEmpty() || !effect.effects().isEmpty())
            throw new IllegalArgumentException("同时弃置工具和特殊能量只接受对方战斗位或全场、无额外参数的原子效果");
    }
}
