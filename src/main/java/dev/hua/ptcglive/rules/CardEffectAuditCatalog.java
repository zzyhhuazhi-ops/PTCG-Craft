package dev.hua.ptcglive.rules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable evidence index for effects the server is permitted to resolve.
 * This is deliberately separate from art/name imports: a card image never
 * counts as verification of its effect text or tournament identity.
 */
public final class CardEffectAuditCatalog {
    public record Entry(Set<String> cardIds, String sourceUrl, String sourceType, String region,
                        String documentVersion, String checkedFactsZh) {
        public Entry {
            cardIds = Set.copyOf(cardIds);
            if (cardIds.isEmpty() || sourceUrl == null || sourceUrl.isBlank() || sourceType == null || sourceType.isBlank()
                || region == null || region.isBlank() || documentVersion == null || documentVersion.isBlank()
                || checkedFactsZh == null || checkedFactsZh.isBlank()) throw new IllegalArgumentException("效果审核来源不完整");
        }
    }
    private final Map<String, Entry> byCardId;
    private CardEffectAuditCatalog(Map<String, Entry> byCardId) { this.byCardId = Map.copyOf(byCardId); }

    public static CardEffectAuditCatalog loadBundled() {
        var stream = CardEffectAuditCatalog.class.getResourceAsStream("/data/ptcglive/references/card-effect-audit-2026-10-02.json");
        if (stream == null) throw new IllegalStateException("缺少卡牌效果审核来源索引");
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("schemaVersion").getAsInt() != 1) throw new IllegalStateException("不支持的效果审核索引版本");
            Map<String, Entry> result = new LinkedHashMap<>();
            for (JsonElement value : root.getAsJsonArray("entries")) {
                JsonObject item = value.getAsJsonObject(); Set<String> ids = new LinkedHashSet<>();
                for (JsonElement id : item.getAsJsonArray("cardIds")) {
                    String cardId = id.getAsString();
                    if (!cardId.matches("[a-z0-9_-]+") || !ids.add(cardId)) throw new IllegalStateException("无效或重复的效果审核卡牌 ID：" + cardId);
                }
                Entry entry = new Entry(ids, item.get("sourceUrl").getAsString(), item.get("sourceType").getAsString(),
                    item.get("region").getAsString(), item.get("documentVersion").getAsString(), item.get("checkedFactsZh").getAsString());
                for (String id : entry.cardIds()) if (result.putIfAbsent(id, entry) != null)
                    throw new IllegalStateException("同一卡牌存在多份效果审核来源：" + id);
            }
            return new CardEffectAuditCatalog(result);
        } catch (Exception exception) { throw new IllegalStateException("无法读取卡牌效果审核来源索引", exception); }
    }

    public void requireEvidenceForSupported(List<CardDefinition> cards) {
        List<String> missing = new ArrayList<>();
        for (CardDefinition card : cards) if (card.effectSupport() == EffectSupport.SUPPORTED && !byCardId.containsKey(card.id())) missing.add(card.id());
        if (!missing.isEmpty()) throw new IllegalStateException("已支持卡牌缺少效果审核来源：" + String.join("、", missing));
    }
    public Entry evidence(String cardId) { return byCardId.get(cardId); }
    public Set<String> auditedCardIds() { return byCardId.keySet(); }
}
