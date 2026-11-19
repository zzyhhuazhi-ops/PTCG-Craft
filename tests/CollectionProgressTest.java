import dev.hua.ptcglive.collection.CollectionProgress;
import dev.hua.ptcglive.packs.*;
import java.util.*;

public final class CollectionProgressTest {
    public static void main(String[] args) {
        var pack = new SetPackDefinition("demo", "测试卡包", "v1",
            Map.of("a", Map.of("a", 1, "b", 1), "b", Map.of("a", 2, "c", 1)),
            List.of(new PackSlot("a", 4, "普通"), new PackSlot("b", 1, "稀有")));
        var counts = CollectionProgress.combine(Map.of("a", 2), Map.of("a", 1), Map.of("b", 1, "outside", 9));
        var summary = CollectionProgress.summarize(pack, counts);
        check(summary.total() == 3 && summary.collected() == 2 && summary.missing() == 1, "跨概率档位去重");
        check(summary.copies() == 4 && summary.duplicates() == 2, "真实数量合计，卡池外不计入");
        check(Math.abs(summary.percent() - 200.0 / 3) < .0001, "按不同卡牌计完成率");
        check(CollectionProgress.Filter.MISSING.accepts(0) && !CollectionProgress.Filter.MISSING.accepts(1), "缺卡筛选");
        check(CollectionProgress.Filter.DUPLICATES.accepts(2) && !CollectionProgress.Filter.DUPLICATES.accepts(1), "重复筛选");
        check(CollectionProgress.summarize(pack, Map.of()).collected() == 0, "历史发现不等于当前持有");
        var afterMove = CollectionProgress.combine(Map.of("a", 1), Map.of("a", 1), Map.of("a", 1, "b", 1));
        check(CollectionProgress.summarize(pack, afterMove).equals(summary), "背包图鉴卡盒转移不改变进度");
        System.out.println("CollectionProgressTest passed: pool identity, current ownership, duplicates and transfer conservation");
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
