package dev.hua.ptcglive.rules;

/** First-player-only restrictions, frozen with the selected ruleset. */
public record FirstTurnRules(boolean firstPlayerMayAttack, boolean firstPlayerMayPlaySupporter) {
    public static final FirstTurnRules INTERNATIONAL_STANDARD = new FirstTurnRules(false, false);
}
