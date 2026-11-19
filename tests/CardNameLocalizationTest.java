import dev.hua.ptcglive.*;
import dev.hua.ptcglive.rules.BundledRules;
import java.util.*;

public final class CardNameLocalizationTest {
    public static void main(String[] args) {
        var cards = CardCatalog.load();
        var definitions = BundledRules.loadCardDefinitions();
        var exceptions = Set.of("N", "AZ", "Imakuni?", "IMAKUNI");
        for (var card : cards) {
            String name = card.nameZh();
            if (!name.matches(".*[\\p{IsHan}].*") && !exceptions.contains(name)) throw new AssertionError("Untranslated: " + card.id() + " " + name);
            if (name.matches(".*\\{[A-Za-z]+}.*")) throw new AssertionError("Raw symbol: " + card.id());
            definitions.find(card.id()).ifPresent(def -> {
                if (!name.equals(def.nameZh())) throw new AssertionError("Item/rule mismatch: " + card.id() + " " + name + " / " + def.nameZh());
            });
        }
        if (!CardNameLocalization.name("bw10_en_085", "Rare Candy").equals("神奇糖果")) throw new AssertionError("Trainer localization");
        if (!CardNameLocalization.label("bw10_en_085", "Rare Candy · BW10 085 · 非凡").equals("神奇糖果 · BW10 085 · 非凡")) throw new AssertionError("Label suffix changed");
        if (!CardNameLocalization.name("unknown-id", "原名").equals("原名")) throw new AssertionError("Fallback lost");
        for (var def : definitions.all()) {
            if (def.id().startsWith("fixture-")) continue;
            if (!def.nameZh().matches(".*[\\p{IsHan}].*") && !exceptions.contains(def.nameZh())) throw new AssertionError("Untranslated rule name: " + def.id());
            if (def.nameZh().matches(".*\\{[A-Za-z]+}.*")) throw new AssertionError("Unrendered rule symbol: " + def.id());
        }
        var byId = new HashMap<String, String>();
        for (var card : cards) byId.put(card.id(), card.nameZh());
        int verified = 0;
        try (var stream = CardNameLocalizationTest.class.getResourceAsStream("/data/ptcglive/card-name-face-corrections.json")) {
            if (stream == null) throw new AssertionError("Missing face evidence");
            var root = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var seen = new HashSet<String>();
            for (var element : root.getAsJsonArray("corrections")) {
                var row = element.getAsJsonObject();
                String id = row.get("cardId").getAsString();
                String title = row.get("displayNameZh").getAsString();
                if (!seen.add(id) || !title.equals(byId.get(id))) throw new AssertionError("Verified title lost: " + id);
                if (!row.get("sha256").getAsString().matches("[a-f0-9]{64}")) throw new AssertionError("Missing image hash: " + id);
                if (!CardNameLocalization.label(id, "旧名 · SET 001 · 稀有").equals(title + " · SET 001 · 稀有")) throw new AssertionError("Printing suffix changed: " + id);
                verified++;
            }
        } catch (java.io.IOException e) { throw new AssertionError(e); }
        for (var entry : Map.of("bw11r_en_001", "藤藤蛇", "bw11r_en_020", "小菊儿",
                "xy9-5r_en_001", "菊草叶", "swsh7-5r_en_002", "喷火龙").entrySet()) {
            if (!entry.getValue().equals(byId.get(entry.getKey()))) throw new AssertionError("Subset name regression: " + entry.getKey());
        }
        System.out.println("CardNameLocalizationTest passed: " + cards.size() + " item names; " + verified + " verified face corrections; printed Latin names explicitly retained.");
    }
}
