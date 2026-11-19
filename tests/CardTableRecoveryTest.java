import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.table.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Restart recovery never recreates hidden match state.  It restores only idle
 * lobbies, clears physical-card locks through CardBoxService.restore, and keeps
 * an owner-only interruption audit.  Removing a table follows the same release
 * invariant without trusting a client result packet.
 */
public final class CardTableRecoveryTest {
    private static final String RULESET = "standard-international-2026";
    public static void main(String[] args) {
        Clock clock = Clock.fixed(Instant.parse("2026-10-02T08:00:00Z"), ZoneOffset.UTC);
        CardDatabase cards = BundledRules.loadCardDefinitions();
        var rulesets = BundledRules.loadRulesets();
        CardBoxService boxes = new CardBoxService(clock);
        CardTableManager tables = new CardTableManager(rulesets, boxes, cards, BundledCombatData.load(), () -> new ServerRandom(104));
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
        DeckBox aBox = legalBox(boxes, a, "重启甲"), bBox = legalBox(boxes, b, "重启乙");
        DeckBox cBox = legalBox(boxes, c, "等待丙"), dBox = legalBox(boxes, d, "等待丁");

        CardTableKey liveKey = new CardTableKey("minecraft:overworld", 8101);
        tables.open(liveKey, a, RULESET); tables.join(liveKey, b);
        tables.selectBox(liveKey, a, aBox.id()); tables.selectBox(liveKey, b, bBox.id());
        tables.setReady(liveKey, a, true); tables.setReady(liveKey, b, true);
        UUID interruptedMatch = tables.start(liveKey, a).id();
        if (!boxes.isLocked(a, aBox.id())) throw new AssertionError("准备重启的对局没有锁定卡盒");

        CardTableKey waitingKey = new CardTableKey("minecraft:overworld", 8102);
        tables.open(waitingKey, c, RULESET); tables.join(waitingKey, d);
        tables.selectBox(waitingKey, c, cBox.id()); tables.selectBox(waitingKey, d, dBox.id());
        tables.setReady(waitingKey, c, true); tables.setReady(waitingKey, d, true);

        CardBoxService restartedBoxes = CardBoxService.restore(clock, boxes.snapshot());
        CardTableManager restarted = CardTableManager.restore(rulesets, restartedBoxes, cards, BundledCombatData.load(),
            () -> new ServerRandom(105), clock, tables.snapshot());
        if (restarted.table(liveKey).isPresent()) throw new AssertionError("重启后不得伪造恢复进行中的隐藏对局");
        if (restarted.recoveryHistoryFor(a).stream().noneMatch(record -> record.matchId().equals(interruptedMatch)))
            throw new AssertionError("玩家未收到自己的服务器中断审计记录");
        if (restarted.recoveryHistoryFor(c).size() != 0) throw new AssertionError("无关玩家看到了其他对局的中断记录");
        if (!restarted.recoveryNoticeFor(a).contains("安全解除锁定")) throw new AssertionError("恢复提示没有说明安全解锁");
        restartedBoxes.take(a, aBox.id(), aBox.contents().getFirst()); // proves restart released the transient lock

        var restoredWaiting = restarted.publicTableView(waitingKey);
        if (restoredWaiting.status() != TableLobbyStatus.WAITING || restoredWaiting.guest() == null
            || restoredWaiting.hostReady() || restoredWaiting.guestReady())
            throw new AssertionError("等待大厅没有恢复，或重启后错误保留旧准备确认");
        restarted.setReady(waitingKey, c, true); restarted.setReady(waitingKey, d, true);
        UUID restartedMatch = restarted.start(waitingKey, c).id();
        if (!restartedBoxes.isLocked(c, cBox.id()) || !restartedBoxes.isLocked(d, dBox.id()))
            throw new AssertionError("恢复后的大厅未重新锁定双方卡盒");
        restarted.removePhysicalTable(waitingKey);
        if (restarted.table(waitingKey).isPresent() || restartedBoxes.isLocked(c, cBox.id()) || restartedBoxes.isLocked(d, dBox.id()))
            throw new AssertionError("拆除卡桌后没有删除大厅并安全解除卡盒锁定");
        if (restarted.recoveryHistoryFor(c).stream().noneMatch(record -> record.matchId().equals(restartedMatch)))
            throw new AssertionError("拆桌中止没有写入仅参与者可见的审计记录");
        System.out.println("CardTableRecoveryTest passed: 重启安全中止、等待大厅重建、隐私审计、拆桌解锁均已验证");
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
