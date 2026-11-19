package dev.hua.ptcglive.packs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Old pack ID → 美版 booster pack ID, generated as data/ptcglive/packs/old-pack-id-map.json.
 * Retired entries (sets with no real booster) map to {@code null} set IDs and to
 * the retired-pack item.  Pure Java so the mapping is testable without Minecraft.
 */
public final class PackIdMigration {
    public static final String RESOURCE = "/data/ptcglive/packs/old-pack-id-map.json";
    public enum Status { RENAMED, MERGED_INTO_PARENT, RETIRED }
    public record Entry(String oldSetId, String oldItemId, String newSetId, String newItemId,
                        String oldProfessionId, String newProfessionId, Status status, String reasonZh) { }

    private final Map<String, Entry> bySetId;
    private final String retiredItemId;

    public PackIdMigration(List<Entry> entries, String retiredItemId) {
        var map = new LinkedHashMap<String, Entry>();
        for (Entry entry : entries) if (map.putIfAbsent(entry.oldSetId(), entry) != null)
            throw new IllegalStateException("旧卡包 ID 重复: " + entry.oldSetId());
        this.bySetId = java.util.Collections.unmodifiableMap(map);
        this.retiredItemId = retiredItemId;
    }

    public static PackIdMigration loadBundled() {
        var stream = PackIdMigration.class.getResourceAsStream(RESOURCE);
        if (stream == null) throw new IllegalStateException("缺少旧卡包 ID 迁移表");
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("schemaVersion").getAsInt() != 1) throw new IllegalStateException("未知旧卡包迁移表版本");
            List<Entry> entries = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("entries")) {
                JsonObject raw = element.getAsJsonObject();
                entries.add(new Entry(text(raw, "oldSetId"), text(raw, "oldItemId"), text(raw, "newSetId"), text(raw, "newItemId"),
                    text(raw, "oldProfessionId"), text(raw, "newProfessionId"), Status.valueOf(text(raw, "status")), text(raw, "reasonZh")));
            }
            return new PackIdMigration(entries, text(root, "retiredItemId"));
        } catch (IllegalStateException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("无法读取旧卡包 ID 迁移表", e); }
    }

    public Collection<Entry> entries() { return bySetId.values(); }
    public String retiredItemId() { return retiredItemId; }

    /**
     * Current pack key for a stored set ID: unchanged when it is already current,
     * the new slug for a renamed/merged legacy ID, or {@code null} when retired or unknown.
     */
    public String currentSetId(String storedSetId, java.util.Set<String> currentSetIds) {
        if (storedSetId == null) return null;
        if (currentSetIds.contains(storedSetId)) return storedSetId;
        Entry entry = bySetId.get(storedSetId);
        return entry == null || entry.newSetId() == null || !currentSetIds.contains(entry.newSetId()) ? null : entry.newSetId();
    }

    private static String text(JsonObject object, String member) {
        return object.has(member) && !object.get(member).isJsonNull() ? object.get(member).getAsString() : null;
    }
}
