package dev.hua.ptcglive.engine.effect;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Map;

/** Public-field target state supplied by ServerMatch; it never exposes a hidden-zone card identity. */
public record EffectTargetContext(UUID actor, UUID opponent, UUID sourcePokemon,
                                  UUID ownActive, UUID opponentActive,
                                  List<UUID> ownBench, List<UUID> opponentBench, EffectEventBindings eventBindings,
                                  Map<EffectTargetResolution.PokemonTarget,Long> entrySequences) {
    public EffectTargetContext(UUID actor,UUID opponent,UUID sourcePokemon,UUID ownActive,UUID opponentActive,List<UUID> ownBench,List<UUID> opponentBench) {
        this(actor,opponent,sourcePokemon,ownActive,opponentActive,ownBench,opponentBench,EffectEventBindings.NONE,Map.of());
    }
    public EffectTargetContext {
        Objects.requireNonNull(actor, "效果操作者");
        Objects.requireNonNull(opponent, "效果对手");
        ownBench = List.copyOf(Objects.requireNonNull(ownBench, "己方备战区"));
        opponentBench = List.copyOf(Objects.requireNonNull(opponentBench, "对方备战区"));
        Objects.requireNonNull(eventBindings,"服务器事件绑定");
        entrySequences=Map.copyOf(Objects.requireNonNull(entrySequences,"当前在场序号"));
    }
}
