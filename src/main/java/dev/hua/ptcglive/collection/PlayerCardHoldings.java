package dev.hua.ptcglive.collection;

import dev.hua.ptcglive.CardBoxItem;
import dev.hua.ptcglive.rules.CardBoxService;
import dev.hua.ptcglive.rules.DeckBox;
import net.minecraft.entity.player.PlayerEntity;
import java.util.*;

/** Server-side physical credentials, not historical ownership. No ground/container/cursor scans. */
public final class PlayerCardHoldings {
    private PlayerCardHoldings() { }
    public static boolean hasBox(PlayerEntity player, UUID box) {
        return player != null && box != null && boxIds(player).contains(box);
    }
    private static Set<UUID> boxIds(PlayerEntity player) {
        Set<UUID> ids = new HashSet<>();
        for (int slot = 0; slot <= 40; slot++) {
            if (slot >= 36 && slot < 40) continue;
            var stack = player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof CardBoxItem) {
                UUID id = CardBoxItem.boxId(stack);
                if (id != null) ids.add(id);
            }
        }
        return ids;
    }
    public static List<DeckBox> heldBoxes(PlayerEntity player, CardBoxService service) {
        Set<UUID> ids = boxIds(player);
        return service.boxesForOwner(player.getUuid()).stream().filter(box -> ids.contains(box.id())).toList();
    }
    public static void requireBox(PlayerEntity player, CardBoxService service, UUID box) {
        service.getOwned(player.getUuid(), box);
        if (!hasBox(player, box)) throw new IllegalStateException("卡盒已不在背包或副手中，请拾回后重新选择");
    }
    public record Snapshot(Map<String, Integer> inventory, Map<String, Integer> collection,
                           Map<String, Integer> boxed, List<DeckBox> boxes) {
        public Snapshot {
            inventory = Map.copyOf(inventory); collection = Map.copyOf(collection);
            boxed = Map.copyOf(boxed); boxes = List.copyOf(boxes);
        }
    }
    public static Snapshot snapshot(PlayerEntity player, CardBoxService service) {
        var boxes = heldBoxes(player, service);
        Map<String, Integer> collection = new HashMap<>(), boxed = new HashMap<>();
        // Explicit deposits remain stored in the encyclopedia; discoveries are not copies.
        service.unboxedForOwner(player.getUuid()).forEach(card -> collection.merge(card.definitionId(), 1, Integer::sum));
        for (var box : boxes) service.contentsForValidation(player.getUuid(), box.id())
            .forEach(card -> boxed.merge(card.definitionId(), 1, Integer::sum));
        return new Snapshot(InventoryCardTransfers.counts(player), collection, boxed, boxes);
    }
}
