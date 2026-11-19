package dev.hua.ptcglive.packs;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One 美版 booster pack, keyed by a stable slug ({@code surging_sparks}).  Its
 * opening slots are mod gameplay data (collation profile) and deliberately do
 * not claim official pull rates.
 */
public record SetPackDefinition(String setId, String displayNameZh, String dataVersion,
                                Map<String, Map<String, Integer>> pools, List<PackSlot> slots,
                                PackPublication publication, BoosterSetIdentity identity) {
    /** Gameplay default kept for hand-built packs; each pack reports its own data-driven {@link #cardsPerPack()}. */
    public static final int CARDS_PER_PACK = 5;
    public SetPackDefinition(String setId, String displayNameZh, String dataVersion,
                             Map<String, Map<String, Integer>> pools, List<PackSlot> slots) {
        this(setId, displayNameZh, dataVersion, pools, slots, PackPublication.unreviewed(), BoosterSetIdentity.unreviewed(setId));
    }
    public SetPackDefinition {
        if (setId == null || !setId.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("setId 无效");
        if (displayNameZh == null || displayNameZh.isBlank() || dataVersion == null || dataVersion.isBlank()) throw new IllegalArgumentException("卡包名称或数据版本为空");
        var safePools = new java.util.LinkedHashMap<String, Map<String, Integer>>();
        pools.forEach((id, weights) -> {
            if (id == null || id.isBlank() || weights == null || weights.isEmpty() || weights.values().stream().anyMatch(w -> w == null || w < 1))
                throw new IllegalArgumentException("卡池权重无效: " + id);
            safePools.put(id, Map.copyOf(weights));
        });
        pools = Map.copyOf(safePools); slots = List.copyOf(slots);
        publication = Objects.requireNonNull(publication, "卡包发行资料"); identity = Objects.requireNonNull(identity, "系列身份");
        if (slots.stream().mapToInt(PackSlot::count).sum() < 1) throw new IllegalArgumentException("卡包至少要开出 1 张卡");
        if (slots.stream().anyMatch(slot -> !safePools.containsKey(slot.poolId()))) throw new IllegalArgumentException("栏位引用了不存在的卡池");
    }
    /** Cards produced by one opening, derived from the configured slots rather than a global constant. */
    public int cardsPerPack() { return slots.stream().mapToInt(PackSlot::count).sum(); }
    public boolean custom() { return publication.catalogRoute() == PackPublication.CatalogRoute.MOD_CUSTOM; }
    public String itemPath() { return custom() ? setId : setId + "_booster_pack"; }
}
