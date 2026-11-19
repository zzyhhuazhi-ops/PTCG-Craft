package dev.hua.ptcglive.engine.effect;
import java.util.Set;

/** Distinguishes all evolution from the printed restriction on playing Evolution cards from hand. */
public enum EvolutionLockEffect {
    ANY, HAND;
    public static final Set<String> FIELDS=Set.of("source");
    public static EvolutionLockEffect from(EffectDefinition effect) {
        if(!effect.type().equals("prevent_evolution")||!effect.effects().isEmpty()||!FIELDS.containsAll(effect.arguments().keySet()))
            throw new IllegalArgumentException("禁止进化仅接受source: ANY或HAND");
        return valueOf(effect.arguments().getOrDefault("source","ANY"));
    }
}
