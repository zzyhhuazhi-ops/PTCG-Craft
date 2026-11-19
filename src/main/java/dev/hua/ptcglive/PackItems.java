package dev.hua.ptcglive;

import dev.hua.ptcglive.packs.SetPackDefinition;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers one booster pack item per READY 美版 expansion
 * ({@code ptcglive:<set>_booster_pack}) plus the single retired-pack item that
 * legacy IDs without a real booster resolve to.
 */
public final class PackItems {
    public static final String ITEM_SUFFIX = "_booster_pack";
    public static final RetiredBoosterPackItem RETIRED = new RetiredBoosterPackItem();
    private static Map<String, PackItem> items = Map.of();
    private PackItems() { }
    public static synchronized void initialize() {
        if (!items.isEmpty()) return;
        var registered = new LinkedHashMap<String, PackItem>();
        for (SetPackDefinition pack : PtcgServices.packs().values().stream().sorted(dev.hua.ptcglive.packs.PackBrowseOrder.NEWEST).toList()) {
            PackItem item = new PackItem(pack);
            Registry.register(Registries.ITEM, Identifier.of(CardItems.MOD_ID, pack.itemPath()), item);
            registered.put(pack.setId(), item);
        }
        items = java.util.Collections.unmodifiableMap(registered);
        Registry.register(Registries.ITEM, Identifier.of(CardItems.MOD_ID, "retired_booster_pack"), RETIRED);
        // Creative tab in US release order; every printed pack design is listed separately.
        Registry.register(Registries.ITEM_GROUP, Identifier.of(CardItems.MOD_ID, "packs"), FabricItemGroup.builder()
            .displayName(Text.translatable("itemGroup.ptcglive.booster_packs"))
            .icon(() -> items.values().iterator().next().getDefaultStack())
            .entries((context, entries) -> items.values().forEach(item -> {
                if (!item.pack().custom() || item.setId().startsWith("collection_")) for (var variant : item.pack().identity().artVariants()) entries.add(item.stack(variant.customModelData()));
            })).build());
        Registry.register(Registries.ITEM_GROUP, Identifier.of(CardItems.MOD_ID, "custom_packs"), FabricItemGroup.builder()
            .displayName(Text.literal("PTCG Craft · 收藏卡包"))
            .icon(() -> items.get("all_energy_pack").getDefaultStack())
            .entries((context, entries) -> items.values().stream().filter(item -> item.pack().custom() && !item.setId().startsWith("collection_")).forEach(item -> entries.add(item.getDefaultStack()))).build());
    }
    /** Booster packs in US release order. */
    public static List<PackItem> all() { return List.copyOf(items.values()); }
    public static PackItem get(String setId) { PackItem item = items.get(setId); if (item == null) throw new IllegalArgumentException("未注册卡包: " + setId); return item; }
}
