package dev.hua.ptcglive.engine.effect;

import java.util.Map;
import java.util.Set;

/** Exact public-field contract for Dragon Launcher; the server owns both selection stages. */
public final class DragonLauncherEffect {
    public static final String TYPE = "discard_named_bench_multi_target_damage";
    public static final Set<String> FIELDS = Set.of("pokemon_names", "amount", "limit_mode", "unique_targets");

    private DragonLauncherEffect() { }

    public static boolean handles(EffectDefinition effect) { return TYPE.equals(effect.type()); }

    public static void validate(EffectDefinition effect) {
        if (!handles(effect) || effect.target() != EffectTarget.OWN_BENCH
            || !effect.arguments().equals(Map.of("pokemon_names", "Dreepy", "amount", "100",
                "limit_mode", "OPPONENT_FIELD", "unique_targets", "true"))
            || !effect.conditions().isEmpty() || !effect.effects().isEmpty())
            throw new IllegalArgumentException("龙之发射器只允许弃置备战区多龙梅西亚，并按实际弃置数量各对不同对手宝可梦造成100点攻击伤害");
    }
}
