package dev.hua.ptcglive.view;

import java.util.List;

/** Minimum practice cadence; the owner's presentation acknowledgement also gates the server AI. */
public final class AiPresentationTiming {
    private AiPresentationTiming() { }
    public static long millis(MatchPresentation.Cue cue) {
        return switch(cue.kind()) {
            case COIN, RESULT -> 1300;
            case ATTACK, KNOCKOUT -> 1200;
            case EVOLVE, DEVOLVE, ABILITY, STADIUM -> 1100;
            case PLAY, PRIZE, MOVE -> 1000;
            case DRAW, ENERGY, TOOL, SHUFFLE -> 900;
            case DAMAGE, HEAL, STATUS, DISCARD, LOST, LEAVE -> 800;
            case TURN, CHECKUP -> 700;
        };
    }
    public static long nanos(MatchPresentation.Cue cue) {return millis(cue)*1_000_000L;}
    public static long ticks(List<MatchPresentation.Cue> cues) {
        return Math.max(14,cues.stream().mapToLong(AiPresentationTiming::millis).sum()/50);
    }
}
