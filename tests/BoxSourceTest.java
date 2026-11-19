import dev.hua.ptcglive.collection.BoxEditorSession;
import dev.hua.ptcglive.rules.*;
import java.time.Clock;
import java.util.*;

/** Source isolation without loading gameplay rules or starting Minecraft. */
public final class BoxSourceTest {
    public static void main(String[] args) {
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        var boxes = new CardBoxService(Clock.systemUTC());
        var box = boxes.create(owner, "来源测试", "fixture");
        boxes.mint(owner, "same"); boxes.mint(owner, "same");
        boxes.mint(owner, "collection_only"); boxes.mint(other, "foreign");
        var second = boxes.create(owner, "其他盒", "fixture");
        boxes.put(owner, second.id(), boxes.mint(owner, "boxed_only").instanceId());
        var physical = new HashMap<>(Map.of("same", 1, "inventory_only", 2));
        var editor = new BoxEditorSession(boxes, new CardDatabase(List.of()), new CombatCatalog(Map.of(), Map.of()),
            Map.of(), Map.of(), owner, box.id());
        // Stand-in for the existing atomic physical-inventory transfer, not an in-game inventory test.
        editor.connectInventory(() -> Map.copyOf(physical), id -> {
            boxes.transaction(() -> {
                boxes.put(owner, box.id(), boxes.mint(owner, id).instanceId()); return null;
            });
            physical.compute(id, (key, count) -> count - 1);
        });
        var view = editor.view(owner);
        check(view.source().equals("INVENTORY") && view.collectionCards() == 3, "default inventory total");
        check(view.collection().stream().noneMatch(r -> r.collectionCount() != 0), "inventory excludes collection");
        view = editor.submit(owner, view.revision(), "ADD", "same", 0, "");
        check(physical.get("same") == 0 && boxes.unboxedForOwner(owner).size() == 3, "inventory move leaves collection alone");
        final int exhausted = view.revision();
        rejects(() -> editor.submit(owner, exhausted, "ADD", "same", 0, ""));
        check(boxes.unboxedForOwner(owner).size() == 3, "no fallback to collection");
        rejects(() -> editor.submit(owner, exhausted, "SOURCE", "ALL", 0, ""));
        rejects(() -> editor.submit(other, exhausted, "SOURCE", "COLLECTION", 0, ""));
        view = editor.submit(owner, view.revision(), "SOURCE", "COLLECTION", 0, "");
        check(view.collectionCards() == 3 && view.collection().size() == 2, "collection excludes inventory, foreign and boxed cards");
        check(view.collection().stream().allMatch(r -> r.inventoryCount() == 0), "collection counts are separate");
        rejects(() -> editor.submit(owner, exhausted, "ADD", "same", 0, ""));
        view = editor.submit(owner, view.revision(), "PAGE", "", 0, "same");
        check(view.collection().size() == 1 && view.collectionCards() == 2, "search counts only the selected source");
        view = editor.submit(owner, view.revision(), "PAGE", "", 0, "");
        view = editor.submit(owner, view.revision(), "ADD", "same", 0, "");
        check(boxes.unboxedForOwner(owner).size() == 2 && physical.get("inventory_only") == 2, "collection move leaves inventory alone");
        final int collectionRevision = view.revision();
        rejects(() -> editor.submit(owner, collectionRevision, "ADD", "inventory_only", 0, ""));
        view = editor.submit(owner, view.revision(), "PAGE", "1", 1, "");
        check(view.collectionPages() == 2 && view.collectionPage() == 1, "pagination uses selected source");
        view = editor.submit(owner, view.revision(), "SOURCE", "INVENTORY", 0, "");
        check(view.collectionPages() == 1 && view.collectionPage() == 0 && view.collectionCards() == 2, "source switch resets pagination");
        view = editor.submit(owner, view.revision(), "TAKE", "same", 0, "");
        check(boxes.unboxedForOwner(owner).size() == 3 && physical.get("same") == 0, "take still returns to encyclopedia");
        UUID match = UUID.randomUUID(); boxes.snapshotForMatch(owner, box.id(), match);
        final int locked = view.revision();
        rejects(() -> editor.submit(owner, locked, "SOURCE", "COLLECTION", 0, ""));
        rejects(() -> editor.submit(owner, locked, "ADD", "inventory_only", 0, ""));
        boxes.unlock(match);
        var restored = CardBoxService.restore(Clock.systemUTC(), boxes.snapshot());
        check(restored.contentsForEditing(owner, box.id()).size() == 1 && restored.unboxedForOwner(owner).size() == 3,
            "existing persistence format preserves ownership");
        System.out.println("BoxSourceTest passed: separate totals, source-only moves, no fallback, pagination, replay/owner/lock guards, persistence");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Expected rejection");
    }
}
