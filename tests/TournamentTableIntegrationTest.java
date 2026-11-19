import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.table.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/** End-to-end table test: the player-facing table is strictly one server-resolved game with no referee path. */
public final class TournamentTableIntegrationTest {
    private static final String RULESET = "standard-international-2026";
    public static void main(String[] args) {
        CardDatabase cards = BundledRules.loadCardDefinitions();
        CardBoxService boxes = new CardBoxService(Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC));
        CardTableManager tables = new CardTableManager(BundledRules.loadRulesets(), boxes, cards, BundledCombatData.load(), () -> new ServerRandom(77));
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        DeckBox aBox = legalBox(boxes, a, "赛事甲"), bBox = legalBox(boxes, b, "赛事乙");
        CardTableKey key = new CardTableKey("minecraft:overworld", 777);
        tables.open(key, a, RULESET); tables.join(key, b);
        if (!"system-single".equals(tables.publicTableView(key).tournamentSettingId())) throw new AssertionError("卡桌未固定为系统单局定胜负");
        tables.selectBox(key, a, aBox.id()); tables.selectBox(key, b, bBox.id()); tables.setReady(key, a, true); tables.setReady(key, b, true);
        var first = tables.start(key, a);
        concedeThroughSession(tables, first.id(), a, a);
        if (boxes.isLocked(a, aBox.id()) || boxes.isLocked(b, bBox.id())) throw new AssertionError("单局系统结算后必须安全解锁卡盒");
        try { tables.startNextTournamentGame(key, a); throw new AssertionError("单局卡桌不应存在赛事下一局"); }
        catch (IllegalStateException expected) { }
        if (!tables.tournamentSummary(key).contains("单局定胜负")) throw new AssertionError("大厅未显示单局系统赛制");
        System.out.println("TournamentTableIntegrationTest passed: 系统单局定胜负、服务器结算、无裁判下一局和赛后解锁均已验证");
    }
    private static void concedeThroughSession(CardTableManager tables, UUID matchId, UUID connection, UUID seat) {
        var session = tables.session(matchId); var view = session.view(connection, seat);
        int option = view.options().stream().filter(o -> o.labelZh().equals("认输并结束本局")).findFirst().orElseThrow().index();
        tables.submit(matchId, connection, seat, view.revision(), option);
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
