import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.table.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/** Multiple independent tables, spectator admission, box locks and practice matches share the same server core. */
public final class CardTableTest {
    private static final String RULESET = "standard-international-2026";
    public static void main(String[] args) {
        CardDatabase cards = BundledRules.loadCardDefinitions();
        var rulesets = BundledRules.loadRulesets();
        CardBoxService boxes = new CardBoxService(Clock.fixed(Instant.parse("2026-09-29T00:00:00Z"), ZoneOffset.UTC));
        CardTableManager tables = new CardTableManager(rulesets, boxes, cards, BundledCombatData.load(), () -> new ServerRandom(9));
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID(), solo = UUID.randomUUID();
        DeckBox aBox = legalBox(boxes, a, "A 的卡盒"), bBox = legalBox(boxes, b, "B 的卡盒"), cBox = legalBox(boxes, c, "C 的卡盒"), dBox = legalBox(boxes, d, "D 的卡盒");
        CardTableKey one = new CardTableKey("minecraft:overworld", 101), two = new CardTableKey("minecraft:overworld", 202);
        CardTableLobby first = tables.open(one, a, RULESET); tables.join(one, b);
        DeckBox invalid = boxes.create(a, "不合法卡盒", RULESET);
        try { tables.selectBox(one, a, invalid.id()); throw new AssertionError("图形大厅接口接受了不合法卡盒"); } catch (IllegalStateException expected) { }
        try { tables.chooseRuleset(one, b, RULESET); throw new AssertionError("对手能够修改房主规则包"); } catch (IllegalStateException expected) { }
        tables.selectBox(one, a, aBox.id()); tables.selectBox(one, b, bBox.id()); tables.setReady(one, a, true); tables.setReady(one, b, true); tables.spectate(one, c);
        CardTableLobby.PublicView beforeStart = tables.publicTableView(one);
        if (beforeStart.spectatorCount() != 1 || beforeStart.guest() == null || beforeStart.hostReady() != true || beforeStart.guestReady() != true)
            throw new AssertionError("卡桌公开大厅视图遗漏席位或准备状态");
        var firstMatch = tables.start(one, a);
        if (first.status() != TableLobbyStatus.IN_MATCH || first.spectators().size() != 1) throw new AssertionError("第一桌未正确开始或旁观者未登记");
        try { boxes.take(a, aBox.id(), aBox.contents().getFirst()); throw new AssertionError("已锁定卡盒仍可编辑"); } catch (IllegalStateException expected) { }
        CardTableLobby second = tables.open(two, c, RULESET); tables.join(two, d); tables.selectBox(two, c, cBox.id()); tables.selectBox(two, d, dBox.id()); tables.setReady(two, c, true); tables.setReady(two, d, true);
        var secondMatch = tables.start(two, c);
        if (tables.tableCount() != 2 || firstMatch.id().equals(secondMatch.id())) throw new AssertionError("多桌并发错误");
        tables.endMatch(firstMatch.id());
        boxes.take(a, aBox.id(), aBox.contents().getFirst());
        tables.endTable(one, a);
        if (first.status() != TableLobbyStatus.ENDED) throw new AssertionError("房主结束卡桌后状态不正确");

        DeckBox soloOne = legalBox(boxes, solo, "练习卡盒一"), soloTwo = legalBox(boxes, solo, "练习卡盒二");
        try { tables.startPractice(solo, soloOne.id(), soloOne.id(), RULESET); throw new AssertionError("单人练习接受了同一卡盒两次"); } catch (IllegalArgumentException expected) { }
        CardTableManager.PracticeMatch practice = tables.startPractice(solo, soloOne.id(), soloTwo.id(), RULESET);
        if (tables.authorizedPracticeSeats(practice.matchId(), solo).size() != 2) throw new AssertionError("练习双方视角授权错误");
        try { tables.authorizedPracticeSeats(practice.matchId(), a); throw new AssertionError("其他玩家获得练习私有视图"); } catch (IllegalArgumentException expected) { }
        OpeningTestSupport.complete(practice.match(), practice.firstSeat(), practice.secondSeat(), 0);
        for (UUID seat : java.util.List.of(practice.firstSeat(), practice.secondSeat())) {
            var hand = practice.match().privateView(seat).hand();
            if (hand.stream().anyMatch(card -> !card.ownerId().equals(seat)))
                throw new AssertionError("练习快照仍使用实体玩家所有者，击倒弃牌会找不到席位");
        }
        var match = practice.match();
        UUID attacker = practice.secondSeat(), defender = practice.firstSeat();
        // Wait for a reserve through real draw/turn actions, then attack through the same engine.
        for (int step = 0; step < 90 && match.phase() == dev.hua.ptcglive.engine.MatchPhase.TURN; step++) {
            UUID actor = match.currentPlayer();
            if (actor.equals(defender) && match.benchPokemonView(defender).isEmpty()) {
                match.privateView(defender).hand().stream().filter(card -> card.definitionId().equals("fixture-basic-h"))
                    .findFirst().ifPresent(card -> match.playBasicPokemonToBench(defender, card.instanceId()));
            }
            if (actor.equals(attacker) && !match.benchPokemonView(defender).isEmpty()) {
                var active = match.activePokemonView(attacker);
                if (active.attachedEnergyIds().isEmpty()) {
                    var energy = match.privateView(attacker).hand().stream().filter(card -> card.definitionId().equals("fixture-energy-h")).findFirst().orElseThrow();
                    match.attachEnergy(attacker, energy.instanceId(), active.pokemonId());
                }
                match.attack(attacker, "fixture-strike");
            } else match.endTurn(actor);
        }
        if (match.phase() != dev.hua.ptcglive.engine.MatchPhase.PRIZE_SELECTION)
            throw new AssertionError("练习击倒没有进入拿奖阶段");
        var session = tables.session(practice.matchId());
        var view = session.view(solo, attacker);
        var opponentBefore = session.view(solo, defender);
        int handBefore = view.game().hand().size();
        var prizeOption = view.options().stream().filter(option -> option.labelZh().startsWith("拿取背面朝上的奖赏卡：位置 ")).findFirst().orElseThrow();
        session.submit(solo, attacker, view.revision(), prizeOption.index());
        if (match.privateView(attacker).prizes().size() != 5 || match.privateView(attacker).hand().size() != handBefore + 1)
            throw new AssertionError("练习奖赏卡没有实际进入手牌");
        if (match.phase() != dev.hua.ptcglive.engine.MatchPhase.CHOOSE_REPLACEMENT)
            throw new AssertionError("练习拿奖后没有等待替补");
        var confirmedPrize = session.view(solo, attacker);
        var revealed = dev.hua.ptcglive.view.MatchPresentation.changes(view,confirmedPrize).stream()
            .filter(cue->cue.kind()==dev.hua.ptcglive.view.MatchPresentation.Kind.PRIZE).findFirst().orElseThrow();
        var known = confirmedPrize.game().hand().stream().filter(card->view.game().hand().stream()
            .noneMatch(old->old.instanceId().equals(card.instanceId()))).findFirst().orElseThrow();
        if(!known.definitionId().equals(revealed.cardId()))throw new AssertionError("真实拿奖事件没有翻开本人已确认卡面");
        if(confirmedPrize.events().stream().anyMatch(e->e.cue().kind()==dev.hua.ptcglive.view.MatchPresentation.Kind.PRIZE&&e.cue().cardId()!=null))
            throw new AssertionError("奖赏正面被写入公开日志");
        if(dev.hua.ptcglive.view.MatchPresentation.changes(opponentBefore,session.view(solo,defender)).stream()
            .anyMatch(cue->cue.kind()==dev.hua.ptcglive.view.MatchPresentation.Kind.PRIZE&&cue.cardId()!=null))
            throw new AssertionError("对手拿奖正面泄漏到另一方视图");
        match.chooseReplacement(defender, match.benchPokemonView(defender).getFirst().pokemonId());
        if (match.phase() != dev.hua.ptcglive.engine.MatchPhase.TURN || !match.currentPlayer().equals(defender))
            throw new AssertionError("练习替补后未恢复回合");
        tables.endMatch(practice.matchId());
        if (boxes.contentsForValidation(solo, soloOne.id()).stream().anyMatch(card -> !card.ownerId().equals(solo)))
            throw new AssertionError("练习更改了真实卡牌所有权");
        droppedBoxesCannotStart(cards, rulesets);
        System.out.println("CardTableTest passed: 多桌、旁观、锁定、单人练习及丢弃卡盒校验均已验证");
    }
    private static void droppedBoxesCannotStart(CardDatabase cards, Map<String, Ruleset> rulesets) {
        var boxes = new CardBoxService(Clock.systemUTC());
        var tables = new CardTableManager(rulesets, boxes, cards, BundledCombatData.load(), () -> new ServerRandom(9));
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var first = legalBox(boxes, a, "甲" ); var second = legalBox(boxes, b, "乙");
        var spare = legalBox(boxes, a, "练习乙");
        var held = new java.util.HashMap<UUID, java.util.Set<UUID>>();
        held.put(a, new java.util.HashSet<>(java.util.Set.of(first.id(), spare.id())));
        held.put(b, new java.util.HashSet<>(java.util.Set.of(second.id())));
        tables.requireBoxPossession((owner, id) -> held.getOrDefault(owner, java.util.Set.of()).contains(id));
        var key = new CardTableKey("minecraft:overworld", 303);
        tables.open(key, a, RULESET); tables.join(key, b);
        held.get(a).remove(first.id());
        rejected(() -> tables.selectBox(key, a, first.id()));
        held.get(a).add(first.id()); tables.selectBox(key, a, first.id());
        held.get(a).remove(first.id());
        rejected(() -> tables.setReady(key, a, true));
        if (tables.table(key).orElseThrow().boxOf(a).isPresent()) throw new AssertionError("丢弃卡盒仍被选用");
        held.get(a).add(first.id()); tables.selectBox(key, a, first.id()); tables.setReady(key, a, true);
        tables.selectBox(key, b, second.id()); tables.setReady(key, b, true);
        held.get(b).remove(second.id());
        rejected(() -> tables.start(key, a));
        if (boxes.isLocked(a, first.id()) || boxes.isLocked(b, second.id())) throw new AssertionError("失败开局残留锁");
        if (tables.publicTableView(key).guestReady()) throw new AssertionError("丢弃后仍准备");
        held.get(b).add(second.id());
        rejected(() -> tables.start(key, a)); // Picking up must not restore stale readiness.
        tables.selectBox(key, b, second.id()); tables.setReady(key, b, true);
        var match = tables.start(key, a);
        held.get(a).remove(first.id());
        if (tables.table(key).orElseThrow().match().isEmpty() || !boxes.isLocked(a, first.id()))
            throw new AssertionError("丢弃不应删除已开局快照或解除锁");
        tables.endMatch(match.id());
        rejected(() -> tables.startPractice(a, first.id(), spare.id(), RULESET));
        if (boxes.isLocked(a, spare.id())) throw new AssertionError("练习失败残留锁");
        held.get(a).add(first.id()); held.get(a).remove(spare.id());
        rejected(() -> tables.startPractice(a, first.id(), spare.id(), RULESET));
        held.get(a).add(spare.id());
        var practice = tables.startPractice(a, first.id(), spare.id(), RULESET);
        tables.endMatch(practice.matchId());
        if (boxes.contentsForValidation(a, first.id()).size() != 60) throw new AssertionError("丢弃导致卡盒内容丢失");
    }
    private static void rejected(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("不在背包的卡盒被接受");
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
