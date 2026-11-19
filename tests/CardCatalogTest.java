import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.CardExclusions;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;

public final class CardCatalogTest {
    public static void main(String[] args) throws Exception {
        var cards = CardCatalog.loadBase();
        long coreCards = cards.stream().filter(card -> !card.set().equals("sd100")).count();
        long starterDeckCards = cards.stream().filter(card -> card.set().equals("sd100")).count();
        // The tracked base has 13,239 IDs, plus the 811 Mega printings added in 0.7.5.
        // BREAK entries are already excluded from these indexes.
        if (coreCards != 13239 + 811 + 3) throw new AssertionError("Expected all 14053 core cards including Mega completion, got " + coreCards);
        if (starterDeckCards != 438) throw new AssertionError("Expected 438 起始卡组100 cards, got " + starterDeckCards);
        var ids = new HashSet<String>();
        for (String ordinary : java.util.List.of("xy11_en_083", "xybsp_en_184"))
            if (CardExclusions.contains(ordinary) || cards.stream().noneMatch(card -> card.id().equals(ordinary)))
                throw new AssertionError("Ordinary Pokemon merely mentioning BREAK must remain registered: " + ordinary);
        for (String excluded : CardExclusions.ids()) if (cards.stream().anyMatch(card -> card.id().equals(excluded)))
            throw new AssertionError("BREAK card remains registered in the card catalog: " + excluded);
        for (var card : cards) {
            if (!ids.add(card.id())) throw new AssertionError("Duplicate card " + card.id());
            if (card.set().equals("sd100")) continue;
            for (String path : new String[]{
                "assets/ptcglive_mc/models/item/" + card.id() + ".json",
                "assets/ptcglive/models/item/" + card.id() + ".json",
                "assets/ptcglive_mc/textures/item/cards/" + card.id() + ".png",
                "assets/ptcglive_mc/card_thumb/" + card.id() + ".jpg"
            }) {
                if (CardCatalog.class.getClassLoader().getResource(path) == null)
                    throw new AssertionError("Missing resource: " + path);
            }
        }
        Map<String, String> aliases;
        try (var stream = CardCatalog.class.getClassLoader().getResourceAsStream("assets/ptcglive_mc/card_art_aliases.json")) {
            if (stream == null) throw new AssertionError("Missing Start Deck 100 HD alias index");
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            aliases = new java.util.LinkedHashMap<>();
            root.getAsJsonObject("aliases").entrySet().forEach(entry -> aliases.put(entry.getKey(), entry.getValue().getAsString()));
        }
        if (aliases.size() != 438) throw new AssertionError("Expected 438 SD100 HD aliases, got " + aliases.size());
        for (var card : cards) if (card.set().equals("sd100")) {
            String source = aliases.get(card.id());
            if (source == null) throw new AssertionError("Missing Start Deck 100 HD alias: " + card.id());
            if (!ids.contains(source)) throw new AssertionError("HD alias source does not exist: " + card.id() + " -> " + source);
            if (source.startsWith("sd100_")) throw new AssertionError("HD alias cannot loop to Start Deck 100: " + source);
            for (String path : new String[]{
                "assets/ptcglive_mc/models/item/" + card.id() + ".json",
                "assets/ptcglive/models/item/" + card.id() + ".json"
            }) if (CardCatalog.class.getClassLoader().getResource(path) == null)
                throw new AssertionError("Missing Start Deck 100 item model: " + path);
        }
        for (String invalid : new String[]{"", "../bad\tbw\tbw\tBad", "card\tbw\tunknown\tBad",
            "card\tbw\tbw\tOne\ncard\tbw\tbw\tTwo", "card\tbw\tbw\t"}) {
            try {
                CardCatalog.read(new StringReader(invalid));
                throw new AssertionError("Invalid catalog accepted: " + invalid);
            } catch (IOException expected) { }
        }
        try {
            cards.clear();
            throw new AssertionError("Catalog should be immutable");
        } catch (UnsupportedOperationException expected) { }
        System.out.println("CardCatalogTest passed: " + cards.size() + " unique items, all 438 SD100 models reuse verified thumbnails and HD aliases");
    }
}
