import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import java.util.*;

/** shuffle_hand: “Shuffle your hand into your deck. Then, draw N cards.” in attack text (not a cost). */
public final class ShuffleHandEffectTest {
    static final UUID A = ResumableAbilityTest.A, B = ResumableAbilityTest.B;
    public static void main(String[] args) {
        shuffleThenDraw(); emptyHand(); schema();
        System.out.println("ShuffleHandEffectTest passed: whole hand shuffled into the owner's deck before the following text");
    }
    static void shuffleThenDraw() {
        var a = ResumableAttackTest.attack("{\"type\":\"shuffle_hand\",\"target\":\"SELF\"},{\"type\":\"draw\",\"amount\":3}");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(), "招式中手牌洗回牌库被拒绝：" + AttackProgramRules.unsupportedReason(a));
        var m = ResumableAttackTest.fixture(a);
        int hand = m.privateView(A).hand().size(), deck = m.privateView(A).deck().size(), opponentHand = m.privateView(B).hand().size();
        check(hand > 3, "夹具手牌太少");
        m.attack(A, a.id());
        check(m.privateView(A).hand().size() == 3 && m.privateView(A).deck().size() == deck + hand - 3, "没有把整副手牌洗回牌库后再抽 3 张");
        check(m.privateView(B).hand().size() == opponentHand + 1, "对手手牌被改变（对手回合开始只应抽 1 张）");
    }
    static void emptyHand() {
        var a = ResumableAttackTest.attack("{\"type\":\"shuffle_hand\",\"target\":\"SELF\"},{\"type\":\"draw\",\"amount\":2}");
        var m = ResumableAttackTest.fixture(a);
        m.discardRandomHandCardsByEffect(A, A, m.privateView(A).hand().size());
        check(m.shuffleHandIntoDeckUpToByEffect(A, A) == 0, "空手牌时不应洗回任何牌");
        m.attack(A, a.id());
        check(m.privateView(A).hand().size() == 2 && m.currentPlayer().equals(B), "空手牌时后续抽牌没有照常结算");
    }
    static void schema() {
        var registry = EffectRegistry.standard();
        for (String bad : List.of("{\"type\":\"shuffle_hand\",\"target\":\"OPPONENT_BENCH\"}", "{\"type\":\"shuffle_hand\",\"target\":\"SELF\",\"amount\":1}"))
            rejects(() -> registry.parse(JsonParser.parseString(bad).getAsJsonObject()));
        var direct = registry.parse(JsonParser.parseString("{\"type\":\"shuffle_hand\",\"target\":\"SELF\"}").getAsJsonObject());
        check(EffectExecutionCapabilityCatalog.assess(direct).capability() == EffectExecutionCapability.DIRECT_SERVER, "手牌洗回牌库应由服务器直接结算");
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法的手牌洗回数据被接受");
    }
}
