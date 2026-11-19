package dev.hua.ptcglive.packs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the generated 美版 booster pack snapshot (tools/packs/generate_booster_packs.py).
 * Opening and probability display use this same immutable object.  Iteration
 * order is the US release order written by the generator.
 */
public final class PackCatalog {
    public static final String RESOURCE = "/data/ptcglive/packs/booster-packs.json";
    private PackCatalog() { }
    public static Map<String, SetPackDefinition> loadBundled() {
        var stream = PackCatalog.class.getResourceAsStream(RESOURCE);
        if (stream == null) throw new IllegalStateException("缺少补充包数据快照");
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("schemaVersion").getAsInt() != 2) throw new IllegalStateException("未知补充包数据版本");
            String dataVersion = root.get("dataVersion").getAsString();
            Map<String, List<PackSlot>> profiles = new LinkedHashMap<>();
            for (var profile : root.getAsJsonObject("collationProfiles").entrySet()) {
                List<PackSlot> slots = new ArrayList<>();
                for (JsonElement slot : profile.getValue().getAsJsonObject().getAsJsonArray("slots")) {
                    JsonObject s = slot.getAsJsonObject();
                    slots.add(new PackSlot(s.get("poolId").getAsString(), s.get("count").getAsInt(), s.get("displayNameZh").getAsString()));
                }
                profiles.put(profile.getKey(), List.copyOf(slots));
            }
            Map<String, SetPackDefinition> result = new LinkedHashMap<>();
            for (JsonElement e : root.getAsJsonArray("packs")) {
                JsonObject raw = e.getAsJsonObject();
                String setId = raw.get("setId").getAsString();
                Map<String, Map<String, Integer>> pools = new LinkedHashMap<>();
                raw.getAsJsonObject("pools").entrySet().forEach(pool -> {
                    Map<String, Integer> weights = new LinkedHashMap<>();
                    pool.getValue().getAsJsonObject().entrySet().forEach(card -> weights.put(card.getKey(), card.getValue().getAsInt()));
                    pools.put(pool.getKey(), weights);
                });
                String profileId = raw.get("collationProfile").getAsString();
                List<PackSlot> slots = profiles.get(profileId);
                if (slots == null) throw new IllegalStateException("补充包引用了不存在的开包配置: " + setId + " → " + profileId);
                List<BoosterSetIdentity.ArtVariant> variants = new ArrayList<>();
                for (JsonElement v : raw.getAsJsonArray("artVariants")) {
                    JsonObject variant = v.getAsJsonObject();
                    variants.add(new BoosterSetIdentity.ArtVariant(variant.get("key").getAsString(), variant.get("label").getAsString(),
                        variant.get("customModelData").getAsInt()));
                }
                if (variants.isEmpty()) throw new IllegalStateException("补充包没有美版封面，不能注册: " + setId);
                List<String> sources = new ArrayList<>();
                raw.getAsJsonArray("sourceSetIds").forEach(id -> sources.add(id.getAsString()));
                var identity = new BoosterSetIdentity(text(raw, "setCode"), text(raw, "officialEnglishName"), text(raw, "officialEnglishFullName"),
                    text(raw, "chineseName"), text(raw, "displayNameEn"), text(raw, "releaseDate"), text(raw, "era"),
                    text(raw, "eraEnglishName"), text(raw, "eraChineseName"), sources, raw.get("cardCount").getAsInt(),
                    raw.has("officialCardsPerPack") && !raw.get("officialCardsPerPack").isJsonNull() ? raw.get("officialCardsPerPack").getAsInt() : null,
                    profileId, variants);
                SetPackDefinition pack = new SetPackDefinition(setId, raw.get("displayNameZh").getAsString(), dataVersion, pools, slots,
                    PackPublicationCatalog.of(identity), identity);
                for (var pool : pack.pools().values())
                    for (String cardId : pool.keySet())
                        if (sources.stream().noneMatch(source -> cardId.startsWith(source + "_")))
                            throw new IllegalStateException("补充包卡池混入其他系列的卡: " + setId + " / " + cardId);
                if (result.putIfAbsent(setId, pack) != null) throw new IllegalStateException("重复 setId 卡包: " + setId);
            }
            if (result.isEmpty()) throw new IllegalStateException("没有任何可用的补充包");
            return java.util.Collections.unmodifiableMap(result);
        } catch (IllegalStateException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("无法读取补充包数据快照", e); }
    }
    private static String text(JsonObject object, String member) {
        return object.has(member) && !object.get(member).isJsonNull() ? object.get(member).getAsString() : "";
    }
}
