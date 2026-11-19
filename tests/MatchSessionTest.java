import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.view.MatchActionMatcher;
import dev.hua.ptcglive.view.MatchRegionModel;
import java.util.*;

public final class MatchSessionTest {
    public static void main(String[] args) {
        var game = MatchRulesRegressionTest.game(100, List.of());
        UUID a = MatchRulesRegressionTest.A, b = MatchRulesRegressionTest.B, observer = new UUID(0, 303);
        var session = MatchSession.multiplayer(game, a, b);
        session.addSpectator(observer);
        var view = session.view(a, a);
        MatchRulesRegressionTest.rejects(() -> session.view(a, b));
        MatchRulesRegressionTest.rejects(() -> session.view(observer, a));
        MatchRulesRegressionTest.rejects(() -> session.submit(observer, null, view.revision(), 0));
        MatchRulesRegressionTest.check(session.view(observer, null).game().hand().isEmpty(), "observer hand leak");
        UUID chooser = game.phase() == MatchPhase.MULLIGAN_DRAW ? a : game.pendingChoicePlayer();
        var chooseView = session.view(chooser, chooser);
        session.submit(chooser, chooser, chooseView.revision(), 0);
        MatchRulesRegressionTest.rejects(() -> session.submit(chooser, chooser, chooseView.revision(), 0));
        MatchRulesRegressionTest.check(session.audit().size() == 1, "duplicate packet mutated the game");
        var practiceGame = MatchRulesRegressionTest.game(100, List.of());
        var practice = MatchSession.practice(practiceGame, observer, a, b);
        MatchRulesRegressionTest.check(practice.view(observer, a).game().hand().isEmpty(), "practice hand leaked before turn order");
        MatchRulesRegressionTest.setup(practiceGame, true);
        MatchRulesRegressionTest.check(!practice.view(observer, a).game().hand().isEmpty(), "practice A unavailable");
        MatchRulesRegressionTest.check(!practice.view(observer, b).game().hand().isEmpty(), "practice B unavailable");
        MatchRulesRegressionTest.rejects(() -> practice.view(a, a));
        cardAnchors();
        System.out.println("MatchSessionTest passed: seat authority, spectators, stale packets, practice switching, card action anchors and action audit");
    }
    private static void cardAnchors() {
        var game = MatchRulesRegressionTest.game(100, List.of()); MatchRulesRegressionTest.setup(game, true);
        // The fixture guarantees B holds an Energy card; make B the acting seat
        // so this test always exercises a hand-card → field-card drag target.
        if (!game.currentPlayer().equals(MatchRulesRegressionTest.B)) game.endTurn(game.currentPlayer());
        var session = MatchSession.multiplayer(game, MatchRulesRegressionTest.A, MatchRulesRegressionTest.B);
        UUID actor = game.currentPlayer(); var snapshot = session.view(actor, actor);
        MatchRulesRegressionTest.check(snapshot.options().stream().anyMatch(option -> !option.cardKeys().isEmpty()),
            "可点击的卡牌操作没有服务器锚点");
        var visible = new HashSet<String>();
        for (var card : MatchRegionModel.from(snapshot).visibleCards()) visible.add(card.key());
        for (var option : snapshot.options()) for (String key : option.cardKeys())
            MatchRulesRegressionTest.check(visible.contains(key), "操作锚点不是当前接收者可见卡：" + key);
        var targeted = snapshot.options().stream().filter(option -> option.cardKeys().size() >= 2).findFirst().orElseThrow();
        var matched = MatchActionMatcher.forCards(snapshot, targeted.cardKeys());
        MatchRulesRegressionTest.check(matched.stream().anyMatch(option -> option.index() == targeted.index()),
            "拖放匹配器漏掉了服务器给出的目标操作");
        MatchRulesRegressionTest.check(MatchActionMatcher.forCards(snapshot, List.of("field:forged")).isEmpty(),
            "拖放匹配器不能为伪造卡面创建操作");
        UUID spectator = new UUID(0, 304); session.addSpectator(spectator);
        var watcher = session.view(spectator, null);
        MatchRulesRegressionTest.check(watcher.options().isEmpty(), "旁观者不应获得操作或卡面操作锚点");
        String watcherJson = new com.google.gson.Gson().toJson(watcher);
        for (var card : game.privateView(actor).hand())
            MatchRulesRegressionTest.check(!watcherJson.contains("hand:" + card.instanceId()), "旁观者收到了手牌操作锚点");
    }
}
