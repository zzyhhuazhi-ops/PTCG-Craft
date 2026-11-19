package dev.hua.ptcglive.engine.effect;

import java.util.ArrayList;
import java.util.List;

/** Maps data targets to authoritative public-field references without allowing clients to invent UUIDs. */
public final class EffectTargetResolver {
    private EffectTargetResolver() { }

    public static EffectTargetResolution resolve(EffectTarget target, EffectTargetContext context) {
        List<EffectTargetResolution.TargetRef> resolved = new ArrayList<>();
        switch (target) {
            case CHOSEN_POKEMON -> throw new IllegalStateException("已选宝可梦只能由服务器续接作用域解析");
            case TRIGGER_POKEMON, ATTACKING_POKEMON -> addBound(resolved,target,context);
            case SELF -> {
                if(context.eventBindings().sourcePokemon()!=null) {
                    var bound=context.eventBindings().sourcePokemon();
                    if(!bound.owner().equals(context.actor())||!bound.pokemonId().equals(context.sourcePokemon()))
                        throw new IllegalArgumentException("已捕获的特性来源与结算操作者不一致");
                    addBound(resolved,target,context);
                } else resolved.add(new EffectTargetResolution.PokemonTarget(context.actor(), required(context.sourcePokemon(), "SELF 需要场上来源宝可梦")));
            }
            case OWN_ACTIVE -> resolved.add(new EffectTargetResolution.PokemonTarget(context.actor(), required(context.ownActive(), "己方没有战斗宝可梦")));
            case OPPONENT_ACTIVE -> resolved.add(new EffectTargetResolution.PokemonTarget(context.opponent(), required(context.opponentActive(), "对方没有战斗宝可梦")));
            case OWN_BENCH -> addPokemonTargets(resolved, context.actor(), context.ownBench());
            case OPPONENT_BENCH -> addPokemonTargets(resolved, context.opponent(), context.opponentBench());
            case ALL_OWN_POKEMON -> {
                if (context.ownActive() != null) resolved.add(new EffectTargetResolution.PokemonTarget(context.actor(), context.ownActive()));
                addPokemonTargets(resolved, context.actor(), context.ownBench());
            }
            case ALL_OPPONENT_POKEMON -> {
                if (context.opponentActive() != null) resolved.add(new EffectTargetResolution.PokemonTarget(context.opponent(), context.opponentActive()));
                addPokemonTargets(resolved, context.opponent(), context.opponentBench());
            }
            case HAND -> resolved.add(new EffectTargetResolution.ZoneTarget(context.actor(), EffectTargetResolution.Zone.HAND));
            case DECK -> resolved.add(new EffectTargetResolution.ZoneTarget(context.actor(), EffectTargetResolution.Zone.DECK));
            case DISCARD -> resolved.add(new EffectTargetResolution.ZoneTarget(context.actor(), EffectTargetResolution.Zone.DISCARD));
            case LOST_ZONE -> resolved.add(new EffectTargetResolution.ZoneTarget(context.actor(), EffectTargetResolution.Zone.LOST_ZONE));
            case ENERGY -> resolved.add(new EffectTargetResolution.CardClassTarget(context.actor(), EffectTargetResolution.CardClass.ENERGY));
            case TRAINER -> resolved.add(new EffectTargetResolution.CardClassTarget(context.actor(), EffectTargetResolution.CardClass.TRAINER));
            case POKEMON -> resolved.add(new EffectTargetResolution.CardClassTarget(context.actor(), EffectTargetResolution.CardClass.POKEMON));
            case SELECT_OWN_POKEMON, SELECT_OPPONENT_POKEMON, SELECT_ANY_POKEMON -> { return new EffectTargetResolution(List.of(), target); }
        }
        return new EffectTargetResolution(resolved, null);
    }

    private static void addBound(List<EffectTargetResolution.TargetRef> resolved,EffectTarget target,EffectTargetContext context) {
        var bound=context.eventBindings().required(target);
        boolean own=bound.owner().equals(context.actor());
        if(!own&&!bound.owner().equals(context.opponent()))throw new IllegalArgumentException("事件目标不属于本场对局");
        var active=own?context.ownActive():context.opponentActive();
        var bench=own?context.ownBench():context.opponentBench();
        // Physical-card identity alone is insufficient after returning to hand and re-entering play.
        if((bound.pokemonId().equals(active)||bench.contains(bound.pokemonId()))
            && context.entrySequences().getOrDefault(bound,0L)==context.eventBindings().entrySequence(target))resolved.add(bound);
    }

    private static void addPokemonTargets(List<EffectTargetResolution.TargetRef> target, java.util.UUID owner, List<java.util.UUID> pokemon) {
        for (java.util.UUID id : pokemon) target.add(new EffectTargetResolution.PokemonTarget(owner, id));
    }
    private static java.util.UUID required(java.util.UUID value, String message) {
        if (value == null) throw new IllegalStateException(message);
        return value;
    }
}
