package dev.hua.ptcglive.rules;

/** Chinese, stable machine-readable failure code plus a player-facing explanation. */
public record DeckIssue(Severity severity, String code, String messageZh) {
    public enum Severity { ERROR, WARNING }
}
