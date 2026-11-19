import dev.hua.ptcglive.packs.*;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.rules.CardBoxService;
import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.rules.BundledRules;
import java.time.Clock;
import java.util.*;

public final class PackBatchTest {
    public static void main(String[] args) {
        var pack = new SetPackDefinition("demo", "测试卡包", "v1", Map.of("pool", Map.of("a", 1)), List.of(new PackSlot("pool", 5, "五张")));
        var ledger = new CardBoxService(Clock.systemUTC());
        var opening = new PackOpeningService(ledger);
        var owner = UUID.randomUUID();
        int selected = 1;
        for (int expected : List.of(4, 5, 8, 10, 16, 32, 64, 1)) {
            selected = PackBatchPolicy.nextCount(selected);
            check(selected == expected, "客户端数量切换与服务端不一致");
        }
        for (int count : List.of(1, 4, 5, 8, 10, 16, 32, 64)) {
            PackBatchPolicy.validateCount(count);
            var draws = new ArrayList<PackOpeningService.Result>();
            var random = new ServerRandom(123);
            for (int i = 0; i < count; i++) draws.add(opening.drawPhysical(owner, pack, random));
            check(draws.stream().mapToInt(r -> r.cards().size()).sum() == count * 5, "整批结果数量");
            check(draws.stream().mapToInt(r -> r.audit().size()).sum() == count * 5, "每包随机记录");
            check(ledger.unboxedForOwner(owner).isEmpty(), "实体发卡不得重复记入收藏");
            var audit = new PackOpeningAuditService(Map.of("demo", pack));
            audit.onChanged(() -> { throw new IllegalStateException("测试写入失败"); });
            try { audit.record(owner, draws, 1); throw new AssertionError("没有拒绝写入失败"); }
            catch (IllegalStateException expected) { }
            check(audit.recordsForAdministration().isEmpty(), "整批日志必须回滚");
            audit.onChanged(() -> { }); audit.record(owner, draws, 2);
            check(audit.recordsForAdministration().size() == count, "逐包日志完整");
        }
        for (int bad : new int[]{-1, 0, 2, 6, 11, Integer.MAX_VALUE}) {
            try { PackBatchPolicy.validateCount(bad); throw new AssertionError("数量未拒绝"); } catch (IllegalArgumentException expected) { }
        }
        check(PackBatchPolicy.countForAction("CONFIRM") == 1 && PackBatchPolicy.countForAction("CONFIRM_10") == 10
            && PackBatchPolicy.countForAction("CONFIRM_99") == 0, "网络意图白名单");
        var real = PackCatalog.loadBundled().values().iterator().next();
        var catalog = CardCatalog.load(); var db = BundledRules.loadCardDefinitions();
        var preview = PackUiSnapshot.preview(UUID.randomUUID(), real, catalog, db);
        var ids = Collections.nCopies(real.cardsPerPack() * 10, preview.cards().getFirst().definitionId());
        check(preview.withBatchResults(ids, 10, catalog, db).results().size() == ids.size(), "50张结果不能丢失或去重");
        try { preview.withBatchResults(ids, 5, catalog, db); throw new AssertionError("批次数量不匹配未拒绝"); } catch (IllegalArgumentException expected) { }
        System.out.println("PackBatchTest passed: 1/4/5/8/10/16/32/64, exact results, physical ownership and atomic audit rollback");
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
