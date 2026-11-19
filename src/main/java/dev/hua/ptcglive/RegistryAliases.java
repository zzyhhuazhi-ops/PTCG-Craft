package dev.hua.ptcglive;

import dev.hua.ptcglive.packs.PackIdMigration;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fabric 1.21.1 has no missing-mapping / remap hook, so retired IDs of this mod
 * are resolved by {@code SimpleRegistryAliasMixin}: only when the stored ID is
 * <em>not</em> registered and is listed here (namespace {@code ptcglive} only).
 * The table comes from data/ptcglive/packs/old-pack-id-map.json.
 */
public final class RegistryAliases {
    private static final Map<RegistryKey<?>, Map<Identifier, Identifier>> ALIASES = new ConcurrentHashMap<>();
    private RegistryAliases() { }

    public static Identifier resolve(RegistryKey<?> registry, Identifier id) {
        if (id == null || !CardItems.MOD_ID.equals(id.getNamespace())) return null;
        Map<Identifier, Identifier> table = ALIASES.get(registry);
        return table == null ? null : table.get(id);
    }

    static void register(RegistryKey<? extends Registry<?>> registry, Identifier from, Identifier to) {
        if (!CardItems.MOD_ID.equals(from.getNamespace())) throw new IllegalArgumentException("只允许为本模组 ID 建立别名: " + from);
        if (from.equals(to)) return;
        ALIASES.computeIfAbsent(registry, key -> new ConcurrentHashMap<>()).put(from, to);
    }

    /** Called once during mod initialisation, before any world or player data is decoded. */
    static void registerPackMigrations(PackIdMigration migration) {
        for (var entry : migration.entries()) {
            register(RegistryKeys.ITEM, Identifier.of(entry.oldItemId()), Identifier.of(entry.newItemId()));
            if (entry.oldProfessionId() != null)
                register(RegistryKeys.VILLAGER_PROFESSION, Identifier.of(entry.oldProfessionId()), Identifier.of("minecraft", "none"));
            if (entry.newProfessionId() != null && !entry.newProfessionId().equals("minecraft:none"))
                register(RegistryKeys.VILLAGER_PROFESSION, Identifier.of(entry.newProfessionId()), Identifier.of("minecraft", "none"));
        }
        LoggerFactory.getLogger(CardItems.MOD_ID).info("Registered {} legacy booster pack ID aliases", migration.entries().size());
    }

    /** Old merchant tokens become the useful replacement item without retaining token behavior or registration. */
    static void registerRetiredTradingMigrations() {
        register(RegistryKeys.ITEM, Identifier.of(CardItems.MOD_ID, "merchant_token"), Identifier.of(CardItems.MOD_ID, "phone"));
    }
}
