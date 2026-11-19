package dev.hua.ptcglive.engine.effect;

import java.util.ArrayList;
import java.util.List;

/**
 * Conservative static gate for a program containing both a direct Knock Out
 * and a voluntary leave-play result.  They may share one final checkpoint
 * only when their targets are provably on opposite sides of the field.  Any
 * same-side or dynamically selected pair stays unsupported rather than
 * allowing a card to Knock Out and then return the same Pokémon.
 */
public final class DirectKoLeavePlaySafety {
    private DirectKoLeavePlaySafety() { }

    public static void validate(EffectProgram program) {
        var knockouts = new ArrayList<EffectDefinition>();
        var departures = new ArrayList<EffectDefinition>();
        for (TriggeredEffects binding : program.triggers()) {
            for (EffectDefinition effect : binding.effects()) collect(effect, knockouts, departures);
        }
        validate(knockouts, departures);
    }

    public static void validate(List<EffectDefinition> effects) {
        var knockouts = new ArrayList<EffectDefinition>();
        var departures = new ArrayList<EffectDefinition>();
        for (EffectDefinition effect : effects) collect(effect, knockouts, departures);
        validate(knockouts, departures);
    }

    private static void collect(EffectDefinition effect, List<EffectDefinition> knockouts, List<EffectDefinition> departures) {
        // A delayed effect owns a different future checkpoint and is assessed when it is scheduled.
        if (effect.type().equals("delayed_effect")) return;
        if (effect.type().equals("knock_out")) knockouts.add(effect);
        if (effect.type().equals("return_to_hand") || effect.type().equals("shuffle_into_deck") || effect.type().equals("discard_pokemon")) departures.add(effect);
        for (EffectDefinition child : effect.effects()) collect(child, knockouts, departures);
    }

    private static void validate(List<EffectDefinition> knockouts, List<EffectDefinition> departures) {
        for (EffectDefinition knockout : knockouts) for (EffectDefinition departure : departures) {
            if (sideOf(knockout.target()) == sideOf(departure.target()))
                throw new IllegalStateException("直接击倒与离场可能作用于同一宝可梦，尚未核验的组合不会执行");
        }
    }

    private enum Side { OWN, OPPONENT, UNKNOWN }
    private static Side sideOf(EffectTarget target) {
        return switch (target) {
            case SELF, OWN_ACTIVE, OWN_BENCH, ALL_OWN_POKEMON -> Side.OWN;
            case OPPONENT_ACTIVE, OPPONENT_BENCH, ALL_OPPONENT_POKEMON -> Side.OPPONENT;
            default -> Side.UNKNOWN;
        };
    }
}
