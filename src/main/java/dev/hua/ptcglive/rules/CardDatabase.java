package dev.hua.ptcglive.rules;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Server-side card definition registry. Network clients receive only public definitions they need. */
public final class CardDatabase {
    private final Map<String, CardDefinition> cards;
    private final Map<String, PrintedHeight> printedHeights;

    public CardDatabase(Collection<CardDefinition> definitions) {
        this(definitions, Map.of());
    }

    public CardDatabase(Collection<CardDefinition> definitions, Map<String, PrintedHeight> heights) {
        var indexed = new LinkedHashMap<String, CardDefinition>();
        for (CardDefinition card : definitions) {
            if (indexed.putIfAbsent(card.id(), card) != null) throw new IllegalArgumentException("重复卡牌定义: " + card.id());
        }
        this.cards = Map.copyOf(indexed);
        heights.forEach((id, height) -> {
            if (!cards.containsKey(id) || cards.get(id).category() != CardCategory.POKEMON)
                throw new IllegalArgumentException("印刷身高只能属于已定义的宝可梦卡：" + id);
        });
        this.printedHeights = Map.copyOf(heights);
    }

    public Optional<CardDefinition> find(String id) { return Optional.ofNullable(cards.get(id)); }
    public Collection<CardDefinition> all() { return cards.values(); }
    public Optional<PrintedHeight> printedHeight(String id) { return Optional.ofNullable(printedHeights.get(id)); }
    public Collection<PrintedHeight> printedHeights() { return printedHeights.values(); }
}
