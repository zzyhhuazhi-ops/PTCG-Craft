import com.google.gson.Gson;
import dev.hua.ptcglive.engine.MatchReplayEvent;
import dev.hua.ptcglive.engine.MatchResult;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.table.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The recipient-specific lobby view: roles, seats, spectators, own boxes only, rules with own-deck checks, and history wording. */
public final class TableLobbyViewTest {
    private static final String RULESET = "standard-international-2026";
    public static void main(String[] args) {
        CardDatabase cards = BundledRules.loadCardDefinitions();
        Map<String, Ruleset> rulesets = BundledRules.loadRulesets();
        CardBoxService boxes = new CardBoxService(Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneOffset.UTC));
        CardTableManager tables = new CardTableManager(rulesets, boxes, cards, BundledCombatData.load(), () -> new ServerRandom(7));
        DeckValidator validator = new DeckValidator(cards, BundledCombatData.load());
        UUID host = UUID.randomUUID(), guest = UUID.randomUUID(), watcher = UUID.randomUUID(), stranger = UUID.randomUUID();
        Map<UUID, String> names = Map.of(host, "Steve", guest, "Alex", watcher, "Zuri", stranger, "Kai");
        DeckBox hostBox = legalBox(boxes, host, "雷电速攻"), guestBox = legalBox(boxes, guest, "超能控场");
        DeckBox hostSpare = boxes.create(host, "火系测试", RULESET);
        CardTableKey key = new CardTableKey("minecraft:overworld", 7);

        // No table yet: an empty, non-existent view.
        var none = view(null, stranger, rulesets, names, boxes, validator);
        check(!none.exists() && "VISITOR".equals(none.role()) && none.host() == null && none.myBoxes().isEmpty(), "空桌视图");

        CardTableLobby lobby = tables.open(key, host, RULESET);
        var hostView = view(lobby, host, rulesets, names, boxes, validator);
        check(hostView.exists() && "HOST".equals(hostView.role()) && "WAITING".equals(hostView.status()), "房主身份与状态");
        check("Steve".equals(hostView.host().name()) && hostView.guest() == null && !hostView.host().deckChosen(), "房主座位与空位");
        check(hostView.myBoxes().size() == 2 && hostView.myBoxId().isEmpty(), "房主看到自己的两个卡盒且尚未选择");
        check(hostView.myBoxes().stream().filter(b -> b.nameZh().equals("雷电速攻")).allMatch(TableLobbyView.Box::canStart)
            && hostView.myBoxes().stream().filter(b -> b.nameZh().equals("火系测试")).noneMatch(TableLobbyView.Box::canStart), "卡盒按当前规则检查");
        check(hostView.rulesets().size() == rulesets.size() && hostView.rule().id().equals(RULESET), "规则包列表与当前规则");
        check(hostView.rulesets().stream().allMatch(rule -> rule.myDeckZh().isEmpty()), "未选卡组时规则列表不检查卡组");
        check("系统单局定胜负".equals(hostView.formatZh()) && hostView.timeLimitMinutes() == 60, "赛制与限时");

        tables.join(key, guest);
        tables.selectBox(key, host, hostBox.id());
        tables.selectBox(key, guest, guestBox.id());
        tables.setReady(key, guest, true);
        tables.spectate(key, watcher);
        hostView = view(lobby, host, rulesets, names, boxes, validator);
        var guestView = view(lobby, guest, rulesets, names, boxes, validator);
        var watcherView = view(lobby, watcher, rulesets, names, boxes, validator);
        var strangerView = view(lobby, stranger, rulesets, names, boxes, validator);
        check("GUEST".equals(guestView.role()) && "SPECTATOR".equals(watcherView.role()) && "VISITOR".equals(strangerView.role()), "四种身份");
        check(hostView.guest() != null && "Alex".equals(hostView.guest().name()) && hostView.guest().ready() && hostView.guest().deckChosen(), "对手座位");
        check(!hostView.host().ready() && hostView.host().deckChosen(), "房主选了卡组但未准备");
        check(hostView.spectators().size() == 1 && "Zuri".equals(hostView.spectators().getFirst().name()), "观众名单");
        check(hostView.myBoxId().equals(hostBox.id().toString()) && guestView.myBoxId().equals(guestBox.id().toString()), "各自看到自己选的卡组");
        check(watcherView.myBoxId().isEmpty() && strangerView.myBoxId().isEmpty(), "观众和路人没有已选卡组");
        check(hostView.rulesets().stream().filter(rule -> rule.id().equals(RULESET)).allMatch(TableLobbyView.Rule::myDeckOk), "已选卡组按每个规则包检查");

        // Privacy: nobody's view contains another player's box id or box name.
        var json = new Gson();
        String hostJson = json.toJson(hostView), guestJson = json.toJson(guestView), watcherJson = json.toJson(watcherView);
        check(!hostJson.contains(guestBox.id().toString()) && !hostJson.contains("超能控场"), "房主视图泄露对手卡组");
        check(!guestJson.contains(hostBox.id().toString()) && !guestJson.contains("雷电速攻") && !guestJson.contains("火系测试"), "对手视图泄露房主卡组");
        check(!watcherJson.contains(hostBox.id().toString()) && !watcherJson.contains(guestBox.id().toString()), "观众视图泄露卡组");
        check(json.fromJson(hostJson, TableLobbyView.Lobby.class).equals(hostView), "视图 JSON 往返");

        // A ruleset change resets ready, as the settings page tells the host.
        tables.chooseRuleset(key, host, "custom-open-all");
        guestView = view(lobby, guest, rulesets, names, boxes, validator);
        check(!guestView.guest().ready() && guestView.rule().id().equals("custom-open-all") && guestView.rule().casualOnly(), "换规则包后取消准备");

        tables.endTable(key, host);
        check(!view(lobby, host, rulesets, names, boxes, validator).exists(), "结束后视图为本桌已结束");

        // History: newest first, at most 8, wording from the viewer's side, public replay lines with names.
        UUID matchA = UUID.randomUUID(), matchB = UUID.randomUUID();
        var replay = List.of(new MatchReplayEvent(1, host, "猜硬币：正面", 0), new MatchReplayEvent(2, guest, "备战：皮卡丘", 0));
        var older = new CardTableManager.CompletedMatch(matchA, key, host, guest, guest, MatchResult.Reason.PRIZES,
            Instant.parse("2026-10-05T10:00:00Z"), replay, List.of());
        var newer = new CardTableManager.CompletedMatch(matchB, key, host, guest, host, MatchResult.Reason.CONCESSION,
            Instant.parse("2026-10-06T10:00:00Z"), replay, List.of());
        var history = TableLobbyView.history(List.of(older, newer), host, names::get);
        check(history.records().size() == 2 && history.records().getFirst().matchId().equals(matchB.toString()), "记录按时间倒序");
        var won = history.records().getFirst();
        check("WIN".equals(won.outcome()) && "对手认输".equals(won.reasonZh()) && "Alex".equals(won.opponentName()) && won.viewerWasHost(), "胜局措辞");
        var lost = history.records().get(1);
        check("LOSS".equals(lost.outcome()) && "对手拿完奖赏卡".equals(lost.reasonZh()), "负局措辞");
        check(won.replay().size() == 2 && "Steve".equals(won.replay().getFirst().actor()) && "备战：皮卡丘".equals(won.replay().get(1).operation()), "公开动作带玩家名");
        var guestHistory = TableLobbyView.history(List.of(newer), guest, names::get);
        check("LOSS".equals(guestHistory.records().getFirst().outcome()) && "你认输".equals(guestHistory.records().getFirst().reasonZh())
            && !guestHistory.records().getFirst().viewerWasHost(), "对手视角措辞");
        var many = new java.util.ArrayList<CardTableManager.CompletedMatch>();
        for (int i = 0; i < 12; i++) many.add(new CardTableManager.CompletedMatch(UUID.randomUUID(), key, host, guest, null, MatchResult.Reason.DRAW,
            Instant.parse("2026-10-01T00:00:00Z").plusSeconds(i), List.of(), List.of()));
        var limited = TableLobbyView.history(many, host, names::get);
        check(limited.records().size() == TableLobbyView.HISTORY_LIMIT && "DRAW".equals(limited.records().getFirst().outcome()), "最多 8 场、平局");
        check(TableLobbyView.regionZh("international").equals("国际赛区") && TableLobbyView.regionZh("cn").equals("简中赛区"), "赛区名称");
        System.out.println("TableLobbyViewTest passed: roles, seats, spectators, own boxes only, per-ruleset own-deck checks, ready reset and history wording");
    }
    private static TableLobbyView.Lobby view(CardTableLobby lobby, UUID viewer, Map<String, Ruleset> rulesets, Map<UUID, String> names,
                                             CardBoxService boxes, DeckValidator validator) {
        return TableLobbyView.of(lobby, viewer, rulesets, names::get, boxes.boxesForOwner(viewer),
            (box, ruleset) -> validator.validate(rulesets.get(ruleset), boxes.contentsForValidation(viewer, box)), "");
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
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
