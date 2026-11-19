package dev.hua.ptcglive.client;

import dev.hua.ptcglive.CardCatalog;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Client-only navigation history. It never grants ownership and is intentionally not part of save data. */
final class PhoneRecentCards {
    private static final Deque<CardCatalog.Entry> CARDS = new ArrayDeque<>();
    static synchronized void remember(CardCatalog.Entry card) {
        CARDS.removeIf(existing -> existing.id().equals(card.id()));
        CARDS.addFirst(card);
        while (CARDS.size() > 12) CARDS.removeLast();
    }
    static synchronized CardCatalog.Entry latest() { return CARDS.peekFirst(); }
    static synchronized List<CardCatalog.Entry> all() { return List.copyOf(CARDS); }
    private PhoneRecentCards() { }
}
