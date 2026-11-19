import dev.hua.ptcglive.packs.PackCatalog;
import dev.hua.ptcglive.packs.PackIdMigration;

/** Every pre-refactor pack item ID has a safe destination after merchant retirement. */
public final class PackIdMigrationTest {
    public static void main(String[] args) {
        var packs = PackCatalog.loadBundled();
        var migration = PackIdMigration.loadBundled();
        if (migration.entries().size() != 98) throw new AssertionError("旧卡包 ID 应为 98 个");
        for (var entry : migration.entries()) {
            if (!entry.oldItemId().equals("ptcglive:" + entry.oldSetId() + "_pack")) throw new AssertionError("旧物品 ID 格式错误: " + entry.oldItemId());
            if (entry.status() == PackIdMigration.Status.RETIRED) {
                if (entry.newSetId() != null || !entry.newItemId().equals(migration.retiredItemId())) throw new AssertionError("停用卡包没有指向停用物品: " + entry.oldSetId());
            } else if (!packs.containsKey(entry.newSetId()) || !entry.newItemId().equals("ptcglive:" + entry.newSetId() + "_booster_pack")
                || !packs.get(entry.newSetId()).identity().sourceSetIds().contains(entry.oldSetId()))
                throw new AssertionError("旧 ID 迁移目标与补充包来源系列不一致: " + entry.oldSetId());
        }
        var current = packs.keySet();
        expect(migration.currentSetId("sv8", current), "surging_sparks");
        expect(migration.currentSetId("swsh12a", current), "silver_tempest");
        expect(migration.currentSetId("gum", current), "detective_pikachu");
        expect(migration.currentSetId("surging_sparks", current), "surging_sparks");
        expect(migration.currentSetId("svbsp", current), null);
        expect(migration.currentSetId("not-a-set", current), null);

        System.out.println("PackIdMigrationTest passed: 98 个旧卡包物品 ID 均有去向（改名／并入主包／安全停用）");
    }
    private static void expect(String actual, String expected) {
        if (!java.util.Objects.equals(actual, expected)) throw new AssertionError("迁移结果 " + actual + "，应为 " + expected);
    }
}
