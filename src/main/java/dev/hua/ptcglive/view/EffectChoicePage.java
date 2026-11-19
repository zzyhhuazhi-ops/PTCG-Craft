package dev.hua.ptcglive.view;

import dev.hua.ptcglive.engine.MatchSession;
import java.util.List;

/** Presentation-only pagination. The original authoritative option indices are never changed. */
public record EffectChoicePage(List<MatchSession.Option> options, int page, int pages) {
    public EffectChoicePage { options = List.copyOf(options); }
    /** All effect / trigger-order choices in server order; the table pages them by its own height. */
    public static List<MatchSession.Option> all(MatchSession.Snapshot snapshot) {
        return snapshot.options().stream().filter(option -> switch(option.type()) {
            case CHOICE, TRIGGER_ORDER, TRIGGER_USE, TRIGGER_SKIP -> true;
            // Copied attacks retain field anchors but belong to the continuation prompt.
            case ATTACK, COPY_SKIP -> snapshot.game().resolvingCardEffect();
            default -> false;
        }).toList();
    }
    public static EffectChoicePage from(MatchSession.Snapshot snapshot, int requestedPage) {
        var choices = all(snapshot);
        int pages = Math.max(1, (choices.size() + 1) / 2), page = Math.max(0, Math.min(requestedPage, pages - 1));
        return new EffectChoicePage(choices.subList(Math.min(choices.size(), page * 2), Math.min(choices.size(), page * 2 + 2)), page, pages);
    }
}
