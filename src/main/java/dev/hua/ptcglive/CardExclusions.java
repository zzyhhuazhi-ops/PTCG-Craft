package dev.hua.ptcglive;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/** Explicitly removed card IDs, shared by the item, pack, and rules catalogs. */
public final class CardExclusions {
    private static final Set<String> IDS = load();

    private CardExclusions() { }

    public static boolean contains(String cardId) { return IDS.contains(cardId); }
    public static Set<String> ids() { return IDS; }

    private static Set<String> load() {
        var stream = CardExclusions.class.getResourceAsStream("/data/ptcglive/card-exclusions.txt");
        if (stream == null) throw new IllegalStateException("缺少卡牌排除清单");
        var ids = new LinkedHashSet<String>();
        try (stream; var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String id = line.trim();
                if (id.isEmpty() || id.startsWith("#")) continue;
                if (!id.matches("[a-z0-9_-]+") || !ids.add(id))
                    throw new IllegalStateException("卡牌排除清单包含无效或重复 ID：" + id);
            }
        } catch (IOException error) {
            throw new IllegalStateException("无法读取卡牌排除清单", error);
        }
        if (ids.isEmpty()) throw new IllegalStateException("卡牌排除清单不能为空");
        return Set.copyOf(ids);
    }
}
