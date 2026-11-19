package dev.hua.ptcglive.engine.effect;

/** A selected physical Trainer stays in discard until the opponent decides its destination. */
public final class TrainerRecoveryBargain {
    public static final String TYPE = "trainer_recovery_bargain";
    public static final String RESOLVE = "resolve_trainer_recovery_bargain";

    private TrainerRecoveryBargain() { }

    public static void validate(EffectDefinition effect) {
        if (!TYPE.equals(effect.type()) || effect.target() != EffectTarget.DISCARD
            || !effect.arguments().isEmpty() || !effect.conditions().isEmpty() || !effect.effects().isEmpty())
            throw new IllegalArgumentException("索德与西尔迪必须从弃牌区选一张训练家，等待对手许可后回手或抽3张");
    }

    public static void validateResolution(EffectDefinition effect) {
        if (!RESOLVE.equals(effect.type()) || effect.target() != EffectTarget.SELF
            || !("true".equals(effect.arguments().get("allow")) || "false".equals(effect.arguments().get("allow")))
            || effect.arguments().size() != 1 || !effect.conditions().isEmpty() || !effect.effects().isEmpty())
            throw new IllegalArgumentException("索德与西尔迪的许可结算参数无效");
    }
}
