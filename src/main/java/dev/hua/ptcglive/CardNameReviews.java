package dev.hua.ptcglive;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Audited titles are independent of rules, stable card IDs, and rarity classification. */
public final class CardNameReviews {
    public record Review(String cardId, String printedNameZh, String displayNameZh, String printedNumber,
                         String jpegSha256, String reviewDate, String note) { }
    private static final Map<String, Review> REVIEWS = load();
    private CardNameReviews() { }
    public static Map<String, Review> all() { return REVIEWS; }
    private static Map<String, Review> load() {
        try (var stream = CardNameReviews.class.getResourceAsStream("/data/ptcglive/card-name-reviews.json")) {
            if (stream == null) throw new IllegalStateException("缺少卡名核对清单");
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schemaVersion").getAsInt() != 1) throw new IllegalStateException("未知卡名清单版本");
            var reviews = new LinkedHashMap<String, Review>();
            for (var element : root.getAsJsonArray("reviews")) {
                var o = element.getAsJsonObject();
                var r = new Review(o.get("cardId").getAsString(), o.get("printedNameZh").getAsString(),
                    o.get("displayNameZh").getAsString(), o.get("printedNumber").getAsString(),
                    o.get("jpegSha256").getAsString(), o.get("reviewDate").getAsString(), o.get("note").getAsString());
                if (r.displayNameZh().isBlank() || r.printedNameZh().isBlank() || !r.jpegSha256().matches("[a-f0-9]{64}")
                    || reviews.putIfAbsent(r.cardId(), r) != null) throw new IllegalStateException("卡名核对记录无效: " + r.cardId());
            }
            return Collections.unmodifiableMap(reviews);
        } catch (java.io.IOException e) { throw new IllegalStateException("无法读取卡名核对清单", e); }
    }
    public static String apply(String cardId, String imported) {
        var review = REVIEWS.get(cardId);
        int end = imported.indexOf(" · ");
        return review == null || end < 0 ? imported : review.displayNameZh() + imported.substring(end);
    }
}
