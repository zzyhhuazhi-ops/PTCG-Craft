import com.google.gson.Gson;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.view.*;
import java.util.*;

public final class MatchRegionTest {
    private static final UUID A = MatchRulesRegressionTest.A, B = MatchRulesRegressionTest.B, WATCHER = new UUID(0, 909);
    public static void main(String[] args) {
        var m = MatchRulesRegressionTest.game(100, List.of());
        var session = MatchSession.multiplayer(m, A, B); session.addSpectator(WATCHER);
        check(MatchRegionModel.from(session.view(A, A)).visibleCards().isEmpty(), "先后手决定前不能展示本人起手牌");
        OpeningTestSupport.decideOrder(m, A, B);
        m.chooseOpeningActive(A, MatchRulesRegressionTest.hand(m, A, "base").instanceId());
        check(MatchRegionModel.from(session.view(WATCHER, null)).visibleCards().isEmpty(), "盖放开局牌不能发给旁观者");
        check(MatchRegionModel.from(session.view(B, B)).zone(A + ":active").cards().isEmpty(), "不能看对方盖放的战斗位");
        m.placeOpeningBench(A, MatchRulesRegressionTest.hand(m, A, "base").instanceId());
        m.chooseOpeningActive(B, MatchRulesRegressionTest.hand(m, B, "base").instanceId());
        m.placeOpeningBench(B, MatchRulesRegressionTest.hand(m, B, "base").instanceId());
        m.startAfterOpening(A); m.startAfterOpening(B);
        m.endTurn(A);
        var energy = MatchRulesRegressionTest.hand(m, B, "energy");
        UUID target = m.activePokemonView(B).pokemonId(); m.attachEnergy(B, energy.instanceId(), target);
        m.endTurn(B); m.endTurn(A);
        m.evolve(B, target, MatchRulesRegressionTest.hand(m, B, "stage-one").instanceId());
        var snapshot = session.view(WATCHER, null);
        var field = snapshot.game().players().stream().filter(p -> p.id().equals(B)).findFirst().orElseThrow().active();
        check(field.evolutionCardIds().equals(List.of("base", "stage-one")), "进化栈从基础到顶牌公开");
        check(field.attachedEnergyCardIds().equals(List.of("energy")), "已附着能量应有公开卡面编号");
        check(snapshot.cardNames().keySet().containsAll(List.of("base", "stage-one", "energy")), "公开附属牌名称缺失");
        var regions = MatchRegionModel.from(snapshot);
        check(regions.zone(B + ":attachments").cards().size() == 2, "只展示底层进化牌和附着能量，不重复顶牌");
        check(regions.zone(A + ":hand").cards().isEmpty() && regions.zone(B + ":hand").cards().isEmpty(), "旁观者不能看任何手牌");
        for (UUID p : List.of(A, B)) {
            check(regions.zone(p + ":deck").cards().isEmpty(), "任何人的牌库都不能发卡面");
            check(regions.zone(p + ":prizes").cards().isEmpty(), "任何人的奖赏都不能发卡面");
            check(regions.zone(p + ":prizes").hiddenCount() == 6, "奖赏只显示公开张数");
        }
        String json = new Gson().toJson(snapshot);
        for (UUID p : List.of(A, B)) {
            var hidden = m.privateView(p);
            for (var cards : List.of(hidden.hand(), hidden.deck(), hidden.prizes())) for (var card : cards)
                check(!json.contains(card.instanceId().toString()), "隐藏卡实例泄露");
        }
        var practice = MatchSession.practice(m, WATCHER, A, B);
        var own = MatchRegionModel.from(practice.view(WATCHER, A));
        String handKey = own.zone(A + ":hand").cards().getFirst().key();
        check(own.allowsPreview(A, handKey), "本人手牌不可查看");
        check(!MatchRegionModel.from(practice.view(WATCHER, B)).allowsPreview(A, handKey), "切换练习视角后旧手牌预览仍有效");
        check(!regions.allowsPreview(null, "hand:forged"), "未知牌不能打开预览");
        var ownSnapshot = practice.view(WATCHER, A); var g = ownSnapshot.game();
        var withoutFirst = new MatchClientView(g.matchId(), g.viewingSeat(), g.phase(), g.turnNumber(), g.currentPlayer(), g.choicePlayer(),
            g.result(), g.players(), g.hand().subList(1, g.hand().size()), g.prizeSlots());
        var removed = new MatchSession.Snapshot(ownSnapshot.revision() + 1, true, ownSnapshot.controllableSeats(), withoutFirst, List.of(), ownSnapshot.cardNames());
        check(!MatchRegionModel.from(removed).allowsPreview(A, handKey), "旧手牌离开可见区域后不能继续当前预览");
        int audit = m.randomTranscript().size(); MatchRegionModel.from(session.view(B, B));
        check(m.randomTranscript().size() == audit, "查看区域不应消耗随机数或改变对局");
        var roundTrip = new Gson().fromJson(json, MatchSession.Snapshot.class);
        check(MatchRegionModel.from(roundTrip).zones().equals(regions.zones()), "公开视图往返不一致");
        var poison = List.of(new ConditionEffect(ConditionEffect.Target.SELF, SpecialCondition.POISONED),
            new ConditionEffect(ConditionEffect.Target.DEFENDER, SpecialCondition.POISONED));
        var ko = MatchRulesRegressionTest.game(10, poison); MatchRulesRegressionTest.setup(ko, true);
        ko.endTurn(A); ko.attack(B, "effect");
        var koSession = MatchSession.multiplayer(ko, A, B); koSession.addSpectator(WATCHER);
        var after = MatchRegionModel.from(koSession.view(WATCHER, null));
        check(after.zone(A + ":discard").cards().size() == 1 && after.zone(B + ":discard").cards().size() == 1, "双方弃牌区应公开");
        layouts();
        System.out.println("MatchRegionTest passed: opening privacy, public field/attachments/discard, hidden zones, practice preview invalidation, serialization and layout bounds");
    }
    private static void layouts() {
        for (int width : List.of(320, 427, 640, 960, 1280)) for (int height : List.of(240, 300, 480, 720)) {
            var layout = CardGridLayout.forScreen(width, height);
            check(layout.capacity() > 0 && layout.capacity() <= 32, "纹理页容量应有界");
            for (int i = 0; i < layout.capacity(); i++) {
                var cell = layout.cell(i);
                check(cell.x() >= 0 && cell.x() + cell.width() <= width && cell.y() >= 64 && cell.y() + cell.height() <= height - 44, "网格越界");
                check(layout.hit(cell.x() + 1, cell.y() + 1) == i, "网格命中映射错误");
            }
            check(layout.hit(0, 0) == -1, "标题不能当作卡牌点击");
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
