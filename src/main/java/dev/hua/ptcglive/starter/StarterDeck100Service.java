package dev.hua.ptcglive.starter;

import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.RandomAuditEvent;
import dev.hua.ptcglive.rules.CardBoxService;
import dev.hua.ptcglive.rules.DeckBox;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Server-only random deck grant; clients only receive the resulting card-box credential. */
public final class StarterDeck100Service {
    /** A server ledger entry: enough to replay the selection, but never a client decision. */
    public record GrantRecord(UUID ownerId, String deckNumber, String deckNameZh, UUID boxId, long serverTick,
                              List<RandomAuditEvent> randomAudit) {
        public GrantRecord { randomAudit = List.copyOf(randomAudit); }
    }
    public record Snapshot(List<GrantRecord> grants) { public Snapshot { grants = List.copyOf(grants); } }
    public record Grant(String deckNumber, String deckNameZh, UUID boxId) { }
    private final StarterDeck100Catalog catalog;
    private final CardBoxService boxes;
    private final List<GrantRecord> grants = new ArrayList<>();
    private Runnable changeListener = () -> { };
    public StarterDeck100Service(StarterDeck100Catalog catalog, CardBoxService boxes) {
        this(catalog, boxes, new Snapshot(List.of()));
    }
    public StarterDeck100Service(StarterDeck100Catalog catalog, CardBoxService boxes, Snapshot snapshot) {
        this.catalog = Objects.requireNonNull(catalog); this.boxes = Objects.requireNonNull(boxes);
        grants.addAll(Objects.requireNonNull(snapshot).grants());
    }
    public synchronized Grant grant(UUID owner, long serverTick) {
        return grant(owner, serverTick, new ServerRandom());
    }
    /** Seeded random is accepted only for deterministic server tests. */
    public synchronized Grant grant(UUID owner, long serverTick, ServerRandom random) {
        Objects.requireNonNull(owner); Objects.requireNonNull(random);
        int index = random.nextInt("starter-deck-100-grant", catalog.decks().size());
        var deck = catalog.decks().get(index);
        DeckBox box = boxes.createPopulated(owner, deck.nameZh(), catalog.rulesetId(), deck.expandedCardIds());
        grants.add(new GrantRecord(owner, deck.number(), deck.nameZh(), box.id(), serverTick, random.transcript()));
        changeListener.run();
        return new Grant(deck.number(), deck.nameZh(), box.id());
    }
    /** Explicit administrator grant. Empty randomAudit distinguishes it from the random first-join grant. */
    public synchronized Grant grantNumbered(UUID owner, String deckNumber, long serverTick) {
        Objects.requireNonNull(owner);
        var deck = catalog.deck(deckNumber); // Validate before minting any card or box.
        DeckBox box = boxes.createPopulated(owner, deck.nameZh(), catalog.rulesetId(), deck.expandedCardIds());
        grants.add(new GrantRecord(owner, deck.number(), deck.nameZh(), box.id(), serverTick, List.of()));
        changeListener.run();
        return new Grant(deck.number(), deck.nameZh(), box.id());
    }
    public synchronized Snapshot snapshot() { return new Snapshot(grants); }
    public synchronized List<GrantRecord> auditForAdministration() { return List.copyOf(grants); }
    public synchronized void onChanged(Runnable listener) { changeListener = Objects.requireNonNull(listener); }
}
