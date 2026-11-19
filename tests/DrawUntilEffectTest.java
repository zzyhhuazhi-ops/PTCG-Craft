import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import java.util.*;

/**
 * draw_until: “(You may) draw cards until you have N cards in your hand.” Draws the difference, nothing when the
 * hand already has N or more, and only what the deck holds; the optional wording is a choice with “不发动”.
 */
public final class DrawUntilEffectTest {
    static final UUID A = ResumableAbilityTest.A, B = ResumableAbilityTest.B;
    public static void main(String[] args) {
        drawsDifference(); fullHandAndShortDeck(); optionalChoice(); schema();
        System.out.println("DrawUntilEffectTest passed: draw-until-hand-size draws the difference, respects the deck and can be declined");
    }
    static void drawsDifference() {
        var a = ResumableAttackTest.attack("{\"type\":\"draw_until\",\"target\":\"SELF\",\"amount\":10}");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(), "攻击中抽到 N 张被拒绝：" + AttackProgramRules.unsupportedReason(a));
        var m = ResumableAttackTest.fixture(a);
        int opponentHand = m.privateView(B).hand().size();
        check(m.privateView(A).hand().size() < 10, "夹具手牌已经不少于 10 张");
        m.attack(A, a.id());
        check(m.privateView(A).hand().size() == 10, "没有抽到手牌正好 10 张，实际 " + m.privateView(A).hand().size());
        check(m.privateView(B).hand().size() == opponentHand + 1, "对手手牌被改变（对手回合开始只应抽 1 张）");
    }
    static void fullHandAndShortDeck() {
        var a = ResumableAttackTest.attack("{\"type\":\"draw_until\",\"target\":\"SELF\",\"amount\":2},{\"type\":\"draw\",\"amount\":1}");
        var m = ResumableAttackTest.fixture(a);
        int hand = m.privateView(A).hand().size();
        check(hand >= 2, "夹具手牌少于 2 张");
        m.attack(A, a.id());
        check(m.privateView(A).hand().size() == hand + 1, "手牌已够时不应再抽，后续文字应照常");
        var shortDeck = ResumableAttackTest.fixture(ResumableAttackTest.attack("{\"type\":\"draw_until\",\"target\":\"SELF\",\"amount\":60}"));
        shortDeck.discardTopDeckCardsByEffect(A, A, shortDeck.privateView(A).deck().size() - 3);
        int before = shortDeck.privateView(A).hand().size();
        check(shortDeck.drawUntilHandSizeByEffect(A, A, 60) == 3 && shortDeck.privateView(A).hand().size() == before + 3
            && shortDeck.privateView(A).deck().isEmpty() && shortDeck.phase() == MatchPhase.TURN, "牌库不足时没有抽完剩下的牌，或因此判负");
    }
    static void optionalChoice() {
        var a = ResumableAttackTest.attack("{\"type\":\"choice\",\"optional\":true,\"effects\":[{\"type\":\"draw_until\",\"target\":\"SELF\",\"amount\":10}]}");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(), "可选的抽到 N 张被拒绝：" + AttackProgramRules.unsupportedReason(a));
    }
    static void schema() {
        var registry = EffectRegistry.standard();
        for (String bad : List.of(
            "{\"type\":\"draw_until\",\"target\":\"OPPONENT_BENCH\",\"amount\":5}",
            "{\"type\":\"draw_until\",\"target\":\"SELF\",\"amount\":0}",
            "{\"type\":\"draw_until\",\"target\":\"SELF\",\"amount\":61}",
            "{\"type\":\"draw_until\",\"target\":\"SELF\"}"))
            rejects(() -> registry.parse(JsonParser.parseString(bad).getAsJsonObject()));
        var direct = registry.parse(JsonParser.parseString("{\"type\":\"draw_until\",\"target\":\"SELF\",\"amount\":6}").getAsJsonObject());
        check(EffectExecutionCapabilityCatalog.assess(direct).capability() == EffectExecutionCapability.DIRECT_SERVER, "抽到 N 张应由服务器直接结算");
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法的抽到 N 张数据被接受");
    }
}
