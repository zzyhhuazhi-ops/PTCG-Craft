import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.packs.*;
import dev.hua.ptcglive.rules.CardBoxService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A displayed weighted pool is the exact pool consumed by the server opening operation. */
public final class PackOpeningTest {
    public static void main(String[] args) {
        var ownership = new CardBoxService(Clock.fixed(Instant.parse("2026-09-29T00:00:00Z"), ZoneOffset.UTC));
        var pack = new SetPackDefinition("demo-h", "演示五张卡包", "test-1", Map.of(
            "普通", Map.of("fixture-basic-h", 2, "fixture-energy-h", 2),
            "稀有", Map.of("fixture-radiant-h", 1)),
            List.of(new PackSlot("普通", 3, "普通位"), new PackSlot("稀有", 2, "稀有位")));
        var result = new PackOpeningService(ownership).open(UUID.randomUUID(), pack, new ServerRandom(7));
        if (result.cards().size() != 5 || result.audit().size() != 5) throw new AssertionError("卡包没有固定开出五张或未记录随机数");
        long rare = result.cards().stream().filter(c -> c.definitionId().equals("fixture-radiant-h")).count();
        if (rare != 2 || result.cards().stream().anyMatch(c -> !pack.pools().get(c.definitionId().equals("fixture-radiant-h") ? "稀有" : "普通").containsKey(c.definitionId()))) throw new AssertionError("开包结果不属于配置卡池");
        if (PackOpeningService.probability(pack.pools().get("普通"), "fixture-basic-h") != 0.5) throw new AssertionError("显示概率与开包权重不一致");
        System.out.println("PackOpeningTest passed: 固定五张、卡池归属、服务器随机与显示权重均已验证");
    }
}
