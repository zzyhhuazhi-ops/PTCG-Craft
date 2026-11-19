package dev.hua.ptcglive.engine;

import java.util.UUID;

public record MatchResult(UUID winner, Reason reason) {
    public enum Reason { DECK_OUT, NO_POKEMON, PRIZES, CARD_EFFECT, CONCESSION, TIME, DRAW, SUDDEN_DEATH }
}
