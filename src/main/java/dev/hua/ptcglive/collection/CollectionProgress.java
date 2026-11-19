package dev.hua.ptcglive.collection;

import dev.hua.ptcglive.packs.SetPackDefinition;
import java.util.*;

/** Read-only projection of existing holdings. No discovery history or second ownership store. */
public final class CollectionProgress {
    private CollectionProgress() { }
    public enum Filter {
        ALL("全部"), OWNED("已收集"), MISSING("未收集"), DUPLICATES("有重复");
        private final String label;
        Filter(String label) { this.label = label; }
        public String labelZh() { return label; }
        public boolean accepts(int count) { return switch (this) {
            case ALL -> true; case OWNED -> count > 0; case MISSING -> count == 0; case DUPLICATES -> count > 1;
        }; }
    }
    public record Summary(String setId, String nameZh, int total, int collected, long copies, long duplicates) {
        public int missing() { return total - collected; }
        public double percent() { return total == 0 ? 0 : collected * 100.0 / total; }
    }
    public static Set<String> poolIds(SetPackDefinition pack) {
        var ids = new HashSet<String>();
        pack.slots().forEach(slot -> ids.addAll(pack.pools().get(slot.poolId()).keySet()));
        return Set.copyOf(ids);
    }
    @SafeVarargs public static Map<String, Integer> combine(Map<String, Integer>... sources) {
        var result = new HashMap<String, Integer>();
        for (var source : sources) source.forEach((id, count) -> {
            if (count < 0) throw new IllegalArgumentException("卡牌数量不能为负数");
            result.merge(id, count, Math::addExact);
        });
        return Map.copyOf(result);
    }
    public static Summary summarize(SetPackDefinition pack, Map<String, Integer> counts) {
        var ids = poolIds(pack); int collected = 0; long copies = 0, duplicates = 0;
        for (var id : ids) {
            int count = counts.getOrDefault(id, 0);
            if (count > 0) collected++;
            copies += count; duplicates += Math.max(0, count - 1);
        }
        return new Summary(pack.setId(), pack.displayNameZh(), ids.size(), collected, copies, duplicates);
    }
}
