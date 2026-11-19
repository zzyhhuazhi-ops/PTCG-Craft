package dev.hua.ptcglive.engine.effect;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Resolution result is either fixed public targets or an explicit server-side selection requirement. */
public record EffectTargetResolution(List<TargetRef> fixedTargets, EffectTarget selectionTarget) {
    public EffectTargetResolution {
        fixedTargets = List.copyOf(Objects.requireNonNull(fixedTargets, "固定效果目标"));
        if (selectionTarget != null && !fixedTargets.isEmpty())
            throw new IllegalArgumentException("选择目标不能同时伪装成固定目标");
    }
    public boolean requiresSelection() { return selectionTarget != null; }

    public sealed interface TargetRef permits PokemonTarget, ZoneTarget, CardClassTarget { }
    public record PokemonTarget(UUID owner, UUID pokemonId) implements TargetRef {
        public PokemonTarget { Objects.requireNonNull(owner, "宝可梦拥有者"); Objects.requireNonNull(pokemonId, "宝可梦实例"); }
    }
    public enum Zone { HAND, DECK, DISCARD, LOST_ZONE, PRIZES }
    public record ZoneTarget(UUID owner, Zone zone) implements TargetRef {
        public ZoneTarget { Objects.requireNonNull(owner, "区域拥有者"); Objects.requireNonNull(zone, "区域"); }
    }
    public enum CardClass { ENERGY, TRAINER, POKEMON }
    public record CardClassTarget(UUID owner, CardClass cardClass) implements TargetRef {
        public CardClassTarget { Objects.requireNonNull(owner, "卡牌类别拥有者"); Objects.requireNonNull(cardClass, "卡牌类别"); }
    }
}
