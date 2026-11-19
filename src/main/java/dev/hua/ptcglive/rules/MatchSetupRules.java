package dev.hua.ptcglive.rules;

/** Versioned opening constants locked into a Ruleset snapshot when a match starts. */
public record MatchSetupRules(int deckSize, int openingHandSize, int prizeCards, int benchLimit) {
    public static final MatchSetupRules INTERNATIONAL_STANDARD = new MatchSetupRules(60, 7, 6, 5);
    public MatchSetupRules {
        if (deckSize < 1 || deckSize > 60) throw new IllegalArgumentException("卡组张数无效；当前卡盒容量最多 60 张");
        if (openingHandSize < 1 || openingHandSize > 20) throw new IllegalArgumentException("起手张数无效");
        if (prizeCards < 1 || prizeCards > 12) throw new IllegalArgumentException("奖赏卡数量无效");
        if (benchLimit < 0 || benchLimit > 5) throw new IllegalArgumentException("备战区上限无效");
    }
}
