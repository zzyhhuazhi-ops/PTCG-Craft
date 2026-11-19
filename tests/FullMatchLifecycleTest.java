import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.table.*;
import java.time.Clock;
import java.util.*;

/** Complete games through the real table/session service; synthetic legal decks, no private-state writes. */
public final class FullMatchLifecycleTest {
    private static final String RULE = "standard-international-2026";
    private record Game(CardTableManager tables, CardBoxService boxes, UUID controller,
                        DeckBox first, DeckBox second, CardTableManager.PracticeMatch practice) {
        ServerMatch match() { return practice.match(); }
        UUID a() { return practice.firstSeat(); }
        UUID b() { return practice.secondSeat(); }
        MatchSession session() { return tables.session(practice.matchId()); }
        MatchSession.Snapshot view(UUID seat) { return session().view(controller, seat); }
        void submit(UUID seat, String label) {
            var view = view(seat);
            var option = view.options().stream().filter(o -> o.labelZh().startsWith(label)).findFirst()
                .orElseThrow(() -> new AssertionError("缺少操作 " + label + " / " + match().phase()));
            tables.submit(practice.matchId(), controller, seat, view.revision(), option.index());
        }
    }
    public static void main(String[] args) {
        sixPrizes(); deckOut(); concession(); loneActive();
        System.out.println("FullMatchLifecycleTest passed: legal 60-card practice decks, six consecutive KOs/prizes, replacement, deck-out, concession, no Pokemon, stale intents and safe unlock");
    }
    private static Game game(int benches) {
        var definitions = new ArrayList<CardDefinition>();
        var profiles = new HashMap<String, PokemonProfile>();
        var attack = new AttackDefinition("hit", "流程验证招式", Map.of(), 10, EnergyType.COLORLESS,
            EffectSupport.SUPPORTED, null);
        // Fifteen different names × four copies: valid deck construction, not a 60-copy bypass.
        for (int i = 0; i < 15; i++) {
            String id = "lifecycle-" + i;
            definitions.add(new CardDefinition(id, "流程验证宝可梦" + i, CardCategory.POKEMON,
                TrainerCategory.NONE, "H", Set.of("international"), true, false, 4, Map.of(), EffectSupport.SUPPORTED, null));
            profiles.put(id, new PokemonProfile(10, 0, null, null, 0, null, 0, 1, List.of(attack)));
        }
        var boxes = new CardBoxService(Clock.systemUTC());
        var tables = new CardTableManager(BundledRules.loadRulesets(), boxes, new CardDatabase(definitions),
            new CombatCatalog(profiles, Map.of()), () -> new ServerRandom(47));
        UUID controller = UUID.randomUUID();
        var first = box(boxes, controller);
        var second = box(boxes, controller);
        var practice = tables.startPractice(controller, first.id(), second.id(), RULE);
        check(practice.match().phase() == MatchPhase.COIN_CALL, "未从服务器猜硬币开始");
        OpeningTestSupport.complete(practice.match(), practice.firstSeat(), practice.secondSeat(), benches);
        check(practice.match().privateView(practice.firstSeat()).prizes().size() == 6, "开局奖赏设置错误");
        return new Game(tables, boxes, controller, first, second, practice);
    }
    private static DeckBox box(CardBoxService boxes, UUID owner) {
        var box = boxes.create(owner, "完整对局回归卡盒", RULE);
        for (int i = 0; i < 60; i++) {
            var card = boxes.mint(owner, "lifecycle-" + i / 4);
            boxes.put(owner, box.id(), card.instanceId());
        }
        return box;
    }
    private static void sixPrizes() {
        var g = game(5); var m = g.match();
        g.submit(g.a(), "结束回合"); g.submit(g.b(), "结束回合");
        for (int knockout = 1; knockout <= 6; knockout++) {
            long sequence = g.view(g.a()).events().stream().mapToLong(BattleEventJournal.Event::sequence).max().orElse(0);
            g.submit(g.a(), "攻击：");
            var resolved = g.view(g.a()).events().stream().filter(e -> e.sequence() > sequence).toList();
            check(resolved.stream().anyMatch(e -> e.cue().kind() == dev.hua.ptcglive.view.MatchPresentation.Kind.ATTACK), "没有服务端攻击记录");
            check(resolved.stream().anyMatch(e -> e.cue().kind() == dev.hua.ptcglive.view.MatchPresentation.Kind.KNOCKOUT), "没有服务端击倒记录");
            check(m.phase() == MatchPhase.PRIZE_SELECTION, "第 " + knockout + " 次击倒未进入奖赏选择");
            int hand = m.privateView(g.a()).hand().size();
            var before = g.view(g.a());
            var option = before.options().stream().filter(o -> o.labelZh().startsWith("拿取背面")).findFirst().orElseThrow();
            g.submit(g.a(), "拿取背面");
            check(dev.hua.ptcglive.view.MatchPresentation.changes(before,g.view(g.a())).stream().noneMatch(c ->
                c.kind() == dev.hua.ptcglive.view.MatchPresentation.Kind.ATTACK || c.kind() == dev.hua.ptcglive.view.MatchPresentation.Kind.DAMAGE), "旧攻击事件重播到领奖步骤");
            check(m.privateView(g.a()).prizes().size() == 6 - knockout && m.privateView(g.a()).hand().size() == hand + 1,
                "奖赏卡没有且仅有一次进入手牌");
            reject(() -> g.tables.submit(m.id(), g.controller, g.a(), before.revision(), option.index()));
            check(m.privateView(g.a()).prizes().size() == 6 - knockout, "重复意图领取额外奖赏");
            if (knockout < 6) {
                check(m.phase() == MatchPhase.CHOOSE_REPLACEMENT, "拿奖后没有要求替补");
                g.submit(g.b(), "选择替补：");
                check(m.phase() == MatchPhase.TURN && m.currentPlayer().equals(g.b()), "替补后未恢复下一回合");
                // Keep a reserve on every KO, including the last: this tests PRIZES, not NO_POKEMON.
                m.playBasicPokemonToBench(g.b(), m.privateView(g.b()).hand().getFirst().instanceId());
                g.submit(g.b(), "结束回合");
            }
        }
        check(m.privateView(g.b()).discard().size() == 6, "连续击倒没有正确弃置六张宝可梦");
        finished(g, g.a(), MatchResult.Reason.PRIZES);
    }
    private static void deckOut() {
        var g = game(0); var m = g.match(); int turns = 0; boolean sawEmpty = false;
        while (m.phase() != MatchPhase.FINISHED && turns++ < 110) {
            if (m.privateView(m.currentPlayer()).deck().isEmpty()) {
                sawEmpty = true;
                check(m.phase() == MatchPhase.TURN, "抽到最后一张牌就提前判负");
            }
            g.submit(m.currentPlayer(), "结束回合");
        }
        check(sawEmpty && turns < 110, "未覆盖空牌库回合或出现死循环");
        finished(g, g.b(), MatchResult.Reason.DECK_OUT);
    }
    private static void concession() {
        var g = game(1);
        g.submit(g.b(), "认输并结束本局"); // May concede even when not the current turn.
        finished(g, g.a(), MatchResult.Reason.CONCESSION);
    }
    private static void loneActive() {
        var g = game(0);
        g.submit(g.a(), "结束回合");
        g.submit(g.b(), "攻击：");
        finished(g, g.b(), MatchResult.Reason.NO_POKEMON);
    }
    private static void finished(Game g, UUID winner, MatchResult.Reason reason) {
        var m = g.match();
        check(m.phase() == MatchPhase.FINISHED && m.result().reason() == reason && winner.equals(m.result().winner()),
            "胜负错误：" + m.phase() + " / " + m.result());
        for (UUID seat : List.of(g.a(), g.b())) check(g.view(seat).options().isEmpty(), "结束后仍接受游戏操作");
        var latest = g.view(g.a());
        reject(() -> g.tables.submit(m.id(), g.controller, g.a(), latest.revision(), 0));
        for (var box : List.of(g.first, g.second)) {
            check(g.boxes.contentsForValidation(g.controller, box.id()).size() == 60, "对局永久消耗了卡盒的卡");
            check(g.boxes.contentsForValidation(g.controller, box.id()).stream().allMatch(c -> c.ownerId().equals(g.controller)), "真实卡牌所有权被练习席位替换");
            var card = box.contents().getFirst();
            g.boxes.take(g.controller, box.id(), card); // Also proves automatic release of match locks.
            g.boxes.put(g.controller, box.id(), card);
        }
    }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法或过期操作被接受");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
