package dev.hua.ptcglive.engine;

import java.util.*;

/** Public source identities only. No program, private cards, deck order or RNG state crosses this boundary. */
public record TriggerOrderPrompt(UUID id, UUID choosingPlayer, boolean optionalDecision, List<Entry> entries,
                                 dev.hua.ptcglive.engine.effect.EffectTrigger trigger) {
    public TriggerOrderPrompt(UUID id, UUID choosingPlayer, boolean optionalDecision, List<Entry> entries) {
        this(id,choosingPlayer,optionalDecision,entries,dev.hua.ptcglive.engine.effect.EffectTrigger.ON_ENERGY_ATTACHED);
    }
    public TriggerOrderPrompt { entries = List.copyOf(entries); Objects.requireNonNull(trigger); }
    public record Entry(UUID id, UUID owner, UUID pokemonId, String nameZh,
                        dev.hua.ptcglive.engine.effect.EffectTargetResolution.PokemonTarget triggerPokemon,
                        dev.hua.ptcglive.engine.effect.EffectTargetResolution.PokemonTarget attackingPokemon,
                        dev.hua.ptcglive.engine.effect.EffectOrigin.Kind sourceKind,UUID sourceCardId) {
        public Entry(UUID id,UUID owner,UUID pokemonId,String nameZh,dev.hua.ptcglive.engine.effect.EffectTargetResolution.PokemonTarget subject,
                     dev.hua.ptcglive.engine.effect.EffectTargetResolution.PokemonTarget attacker) {
            this(id,owner,pokemonId,nameZh,subject,attacker,dev.hua.ptcglive.engine.effect.EffectOrigin.Kind.ABILITY,pokemonId);
        }
        public Entry(UUID id,UUID owner,UUID pokemonId,String nameZh) { this(id,owner,pokemonId,nameZh,null,null); }
    }
}
