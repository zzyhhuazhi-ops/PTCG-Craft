import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.table.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/** Finished table records preserve public replay facts across a service restore without exposing them to observers. */
public final class CardTableReplayPersistenceTest {
    private static final String RULESET = "standard-international-2026";
    public static void main(String[] args) {
        Clock clock = Clock.fixed(Instant.parse("2026-10-02T09:00:00Z"), ZoneOffset.UTC);
        CardDatabase cards = BundledRules.loadCardDefinitions();
        var rulesets = BundledRules.loadRulesets();
        CardBoxService boxes = new CardBoxService(clock);
        CardTableManager tables = new CardTableManager(rulesets, boxes, cards, BundledCombatData.load(), () -> new ServerRandom(111));
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), stranger = UUID.randomUUID();
        DeckBox firstBox = legalBox(boxes, first, "回放甲"), secondBox = legalBox(boxes, second, "回放乙");
        CardTableKey table = new CardTableKey("minecraft:overworld", 8201);
        tables.open(table, first, RULESET); tables.join(table, second);
        tables.selectBox(table, first, firstBox.id()); tables.selectBox(table, second, secondBox.id());
        tables.setReady(table, first, true); tables.setReady(table, second, true);
        UUID matchId = tables.start(table, first).id();
        var session = tables.session(matchId); var view = session.view(first, first);
        int concede = view.options().stream().filter(option -> option.labelZh().equals("认输并结束本局")).findFirst().orElseThrow().index();
        tables.submit(matchId, first, first, view.revision(), concede);

        var record = tables.completedHistoryFor(first).stream().filter(entry -> entry.matchId().equals(matchId)).findFirst().orElseThrow();
        if (!second.equals(record.winner()) || record.reason() != dev.hua.ptcglive.engine.MatchResult.Reason.CONCESSION)
            throw new AssertionError("服务器结算结果未写入公开赛后记录");
        if (record.replay().stream().noneMatch(event -> event.operation().equals("认输")))
            throw new AssertionError("公开动作回放遗漏认输事件");
        if (!record.randomAudit().isEmpty()) throw new AssertionError("未进行随机流程的对局不应伪造随机审计");
        if (!tables.completedHistoryFor(stranger).isEmpty()) throw new AssertionError("旁观／无关身份读取了参与者赛后记录");
        if (!tables.completedHistorySummaryFor(first).contains(matchId.toString().substring(0, 8))
            || !tables.completedHistorySummaryFor(stranger).contains("暂无"))
            throw new AssertionError("卡桌大厅的参与者回放摘要权限不正确");
        if (tables.publicTableView(table).status() != TableLobbyStatus.ENDED || boxes.isLocked(first, firstBox.id()) || boxes.isLocked(second, secondBox.id()))
            throw new AssertionError("单局结束没有关闭卡桌并解锁双方卡盒");

        CardBoxService restartedBoxes = CardBoxService.restore(clock, boxes.snapshot());
        CardTableManager restarted = CardTableManager.restore(rulesets, restartedBoxes, cards, BundledCombatData.load(),
            () -> new ServerRandom(112), clock, tables.snapshot());
        var restored = restarted.completedHistoryFor(second).stream().filter(entry -> entry.matchId().equals(matchId)).findFirst().orElseThrow();
        if (!restored.replay().equals(record.replay()) || !restored.randomAudit().equals(record.randomAudit())
            || restored.reason() != record.reason() || !restored.finishedAt().equals(record.finishedAt()))
            throw new AssertionError("公开赛后回放在服务恢复后发生改变");
        System.out.println("CardTableReplayPersistenceTest passed: 公开回放、随机审计边界、参与者权限和重启恢复均已验证");
    }
    private static DeckBox legalBox(CardBoxService boxes, UUID owner, String name) {
        DeckBox box = boxes.create(owner, name, RULESET);
        for (int i = 0; i < 4; i++) put(boxes, owner, box, "fixture-basic-h");
        for (int i = 0; i < 56; i++) put(boxes, owner, box, "fixture-energy-h");
        return box;
    }
    private static void put(CardBoxService boxes, UUID owner, DeckBox box, String definition) {
        CardInstance card = boxes.mint(owner, definition); boxes.put(owner, box.id(), card.instanceId());
    }
}
