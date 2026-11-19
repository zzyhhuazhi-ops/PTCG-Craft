package dev.hua.ptcglive.engine.effect;

import java.util.Objects;
import java.util.UUID;

/** Attribution supplied by the authoritative card entry point, never by Effect JSON or client intent. */
public record EffectOrigin(Kind kind, UUID controller, UUID sourceId) {
    public enum Kind { ATTACK, ABILITY, TRAINER, ENERGY, RULE }
    public EffectOrigin {
        Objects.requireNonNull(kind); Objects.requireNonNull(controller);
        if (kind != Kind.RULE) Objects.requireNonNull(sourceId, "卡牌效果来源");
    }
    public static EffectOrigin rule(UUID player) { return new EffectOrigin(Kind.RULE, player, null); }
    public String labelZh() {
        return switch (kind) { case ATTACK -> "攻击"; case ABILITY -> "特性"; case TRAINER -> "训练家"; case ENERGY -> "能量"; case RULE -> "规则"; };
    }
}
