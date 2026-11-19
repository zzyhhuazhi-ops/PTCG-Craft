package dev.hua.ptcglive;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Display titles only: stable printing IDs and combat/evolution identities stay unchanged. */
public final class CardNameLocalization {
    private static final Map<String, String> NAMES = load();
    private CardNameLocalization() { }
    private static Map<String, String> load() {
        try (var stream = CardNameLocalization.class.getResourceAsStream("/data/ptcglive/card-name-localization.json")) {
            if (stream == null) throw new IllegalStateException("缺少中文卡名对应表");
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schemaVersion").getAsInt() != 1) throw new IllegalStateException("不支持的中文卡名版本");
            var names = new LinkedHashMap<String, String>();
            for (var entry : root.getAsJsonObject("namesByCardId").entrySet()) {
                String name = entry.getValue().getAsString();
                if (!entry.getKey().matches("[a-z0-9_-]+") || name.isBlank()) throw new IllegalStateException("中文卡名记录无效");
                names.put(entry.getKey(), name);
            }
            return Map.copyOf(names);
        } catch (java.io.IOException error) { throw new IllegalStateException("无法读取中文卡名对应表", error); }
    }
    public static String name(String id, String fallback) { return NAMES.getOrDefault(id, fallback); }
    public static String label(String id, String imported) {
        String replacement = NAMES.get(id);
        if (replacement == null) return imported;
        int split = imported.indexOf(" · ");
        if (split >= 0) return replacement + imported.substring(split);
        String[] fields = imported.trim().split("\\s+");
        if (fields.length < 4) return imported;
        return replacement + " " + String.join(" ", java.util.Arrays.copyOfRange(fields, fields.length - 3, fields.length));
    }
}
