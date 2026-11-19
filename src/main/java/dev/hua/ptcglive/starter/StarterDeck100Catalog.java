package dev.hua.ptcglive.starter;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Immutable, bundled 001–102 snapshot. It never fetches a card list at runtime. */
public final class StarterDeck100Catalog {
    public record Card(String id, String nameZh) { }
    public record CardCount(String id, int count) { }
    public record Deck(String number, String nameZh, List<CardCount> cards, int total) {
        public Deck {
            cards = List.copyOf(cards);
            if (!number.matches("\\d{3}") || total != 60 || cards.stream().mapToInt(CardCount::count).sum() != 60)
                throw new IllegalArgumentException("起始卡组数据不是 60 张：" + number);
        }
        public List<String> expandedCardIds() {
            List<String> result = new ArrayList<>(60);
            for (CardCount card : cards) {
                if (card.count() < 1) throw new IllegalArgumentException("卡组存在无效数量：" + number);
                for (int i = 0; i < card.count(); i++) result.add(card.id());
            }
            return List.copyOf(result);
        }
    }
    private record SourceSnapshot(int schemaVersion, String dataVersion, String rulesetId, List<Card> cards, List<Deck> decks) { }

    private final String dataVersion;
    private final String rulesetId;
    private final List<Deck> decks;
    private final Map<String, Deck> byNumber;

    private StarterDeck100Catalog(SourceSnapshot snapshot) {
        if (snapshot.schemaVersion() != 1 || snapshot.decks() == null || snapshot.decks().size() != 102)
            throw new IllegalArgumentException("起始卡组100资源不完整");
        this.dataVersion = Objects.requireNonNull(snapshot.dataVersion());
        this.rulesetId = Objects.requireNonNull(snapshot.rulesetId());
        this.decks = snapshot.decks().stream().sorted(Comparator.comparing(Deck::number)).toList();
        this.byNumber = decks.stream().collect(Collectors.toUnmodifiableMap(Deck::number, deck -> deck));
        for (int index = 1; index <= 102; index++) {
            String number = "%03d".formatted(index);
            if (!byNumber.containsKey(number)) throw new IllegalArgumentException("缺少起始卡组：" + number);
        }
    }
    public static StarterDeck100Catalog loadBundled() {
        var stream = StarterDeck100Catalog.class.getResourceAsStream("/data/ptcglive/preconstructed/starter-deck-100-cn-community.json");
        if (stream == null) throw new IllegalStateException("缺少起始卡组100资源");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new StarterDeck100Catalog(new Gson().fromJson(reader, SourceSnapshot.class));
        } catch (Exception failure) {
            throw new IllegalStateException("无法读取起始卡组100资源", failure);
        }
    }
    public String dataVersion() { return dataVersion; }
    public String rulesetId() { return rulesetId; }
    public List<Deck> decks() { return decks; }
    public Deck deck(String number) {
        var deck = byNumber.get(number);
        if (deck == null) throw new IllegalArgumentException("未知起始卡组：" + number);
        return deck;
    }
}
