package dev.hua.ptcglive.view;

import dev.hua.ptcglive.engine.MatchSession;
import java.util.List;

/** Five full faces per row, preserving every server candidate across GUI sizes. */
public record EffectCardPage(List<MatchRegionModel.Card> cards, int page, int pages) {
    public EffectCardPage { cards = List.copyOf(cards); }
    public static String identity(MatchSession.Snapshot snapshot) {
        var game = snapshot.game();
        if(game.toolOverflow()!=null)return "tool-overflow:"+game.toolOverflow().id();
        if (game.effectReveal() != null) return "reveal:" + game.effectReveal().id();
        if (game.effectAttachment() != null) return "attachment:" + game.effectAttachment().resolutionId() + ":" + game.effectAttachment().step();
        if (game.effectSelection() != null) return "selection:" + game.effectSelection().resolutionId() + ":" + game.effectSelection().step();
        return null;
    }
    public static EffectCardPage from(MatchSession.Snapshot snapshot, int requestedPage) {
        if (identity(snapshot) == null) return new EffectCardPage(List.of(), 0, 1);
        var cards = MatchRegionModel.from(snapshot).zone(snapshot.game().toolOverflow()!=null ? "tool-overflow" : snapshot.game().effectReveal() != null ? "effect-reveal"
            : snapshot.game().effectAttachment() != null ? "effect-attachment" : "effect-selection").cards();
        int pages = Math.max(1, (cards.size() + 4) / 5), page = Math.max(0, Math.min(requestedPage, pages - 1));
        return new EffectCardPage(cards.subList(Math.min(cards.size(), page * 5), Math.min(cards.size(), page * 5 + 5)), page, pages);
    }
}
