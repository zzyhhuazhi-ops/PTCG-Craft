package dev.hua.ptcglive;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

public final class CardItems {
    /** Fabric registry namespace. Asset namespace remains stable for the imported card art. */
    public static final String MOD_ID = "ptcglive";
    public static final String ASSET_NAMESPACE = "ptcglive_mc";
    private static List<CardItem> cards = List.of();
    private static java.util.Map<String, CardItem> byDefinitionId = java.util.Map.of();

    private CardItems() { }
    public static List<CardItem> cards() { return cards; }

    public static void initialize() {
        if (!cards.isEmpty()) throw new IllegalStateException("Card items initialized twice");
        var registered = new ArrayList<CardItem>();
        for (var entry : CardCatalog.load()) {
            var item = new CardItem(entry);
            Registry.register(Registries.ITEM, Identifier.of(MOD_ID, entry.id()), item);
            registered.add(item);
        }
        cards = List.copyOf(registered);
        var indexed = new java.util.LinkedHashMap<String, CardItem>();
        for (CardItem card : cards) indexed.put(card.card().id(), card);
        byDefinitionId = java.util.Map.copyOf(indexed);
        for (String era : List.of("bw", "xy", "sm", "swsh", "sv", "me", "other")) {
            List<CardItem> groupCards = cards.stream().filter(c -> c.card().era().equals(era)).toList();
            if (groupCards.isEmpty()) continue;
            Registry.register(Registries.ITEM_GROUP, Identifier.of(MOD_ID, "cards_" + era),
                FabricItemGroup.builder()
                    .displayName(Text.translatable("itemGroup.ptcglive_mc." + era))
                    .icon(() -> groupCards.getFirst().getDefaultStack())
                    .entries((context, entries) -> groupCards.forEach(entries::add))
                    .build());
        }
        LoggerFactory.getLogger(MOD_ID).info("Registered {} independent PTCG card items", cards.size());
    }
    /** Server inventory grants resolve IDs through the same registry as client card art. */
    public static CardItem byDefinitionId(String id) {
        CardItem item = byDefinitionId.get(id);
        if (item == null) throw new IllegalArgumentException("未注册卡牌物品：" + id);
        return item;
    }
}
