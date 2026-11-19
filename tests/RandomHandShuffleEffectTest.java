import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/**
 * shuffle_random_hand: “Choose a random card from your opponent's hand. Your opponent reveals that card and
 * shuffles it into their deck.” Printed effect text (not a cost); the server picks randomly and replayably.
 */
public final class RandomHandShuffleEffectTest {
    static final UUID A = ResumableAbilityTest.A, B = ResumableAbilityTest.B;
    public static void main(String[] args) {
        opponentHand(); asMuchAsPossibleAndEmpty(); schema();
        System.out.println("RandomHandShuffleEffectTest passed: random hand cards are revealed and shuffled into the owner's deck by the server");
    }
    static void opponentHand() {
        var a = ResumableAttackTest.attack("{\"type\":\"shuffle_random_hand\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(), "攻击中随机洗回手牌被拒绝：" + AttackProgramRules.unsupportedReason(a));
        var m = ResumableAttackTest.fixture(a);
        var hand = m.privateView(B).hand(); int deck = m.privateView(B).deck().size(), ownHand = m.privateView(A).hand().size();
        m.attack(A, a.id());
        // 对手手牌 −1 进牌库，随后对手回合开始抽 1 张：手牌数不变，牌库数不变，弃牌区仍为空
        check(m.privateView(B).hand().size() == hand.size() && m.privateView(B).deck().size() == deck
            && m.privateView(B).discard().isEmpty(), "没有把对手 1 张手牌洗回其牌库");
        check(m.privateView(A).hand().size() == ownHand, "洗回了攻击方的手牌");
        check(m.replayTranscript().stream().anyMatch(e -> e.operation().startsWith("已审核效果随机选手牌公开后洗回牌库：")), "洗回的卡没有公开记录");
        var r1 = ResumableAttackTest.fixture(a); var r2 = ResumableAttackTest.fixture(a);
        r1.attack(A, a.id()); r2.attack(A, a.id());
        check(r1.randomTranscript().equals(r2.randomTranscript()) && r1.privateView(B).deck().stream().map(CardInstance::definitionId).toList().equals(r2.privateView(B).deck().stream().map(CardInstance::definitionId).toList()),
            "随机选牌或洗牌没有使用服务器可重放的随机源");
    }
    static void asMuchAsPossibleAndEmpty() {
        var a = ResumableAttackTest.attack("{\"type\":\"shuffle_random_hand\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":3},{\"type\":\"draw\",\"amount\":1}");
        var m = ResumableAttackTest.fixture(a);
        m.discardRandomHandCardsByEffect(A, B, m.privateView(B).hand().size() - 1);
        int deck = m.privateView(B).deck().size(), ownHand = m.privateView(A).hand().size();
        check(m.shuffleRandomHandCardsIntoDeckByEffect(A, B, 3) == 1 && m.privateView(B).hand().isEmpty()
            && m.privateView(B).deck().size() == deck + 1, "手牌不足时没有尽量洗回");
        m.attack(A, a.id());
        check(m.privateView(A).hand().size() == ownHand + 1 && m.currentPlayer().equals(B), "对手没有手牌时后续文字未继续或攻击未结束");
    }
    static void schema() {
        var registry = EffectRegistry.standard();
        for (String bad : List.of(
            "{\"type\":\"shuffle_random_hand\",\"target\":\"OPPONENT_BENCH\",\"amount\":1}",
            "{\"type\":\"shuffle_random_hand\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":0}",
            "{\"type\":\"shuffle_random_hand\",\"target\":\"OPPONENT_ACTIVE\"}",
            "{\"type\":\"shuffle_random_hand\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1,\"bottom\":true}"))
            rejects(() -> registry.parse(JsonParser.parseString(bad).getAsJsonObject()));
        var direct = registry.parse(JsonParser.parseString("{\"type\":\"shuffle_random_hand\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}").getAsJsonObject());
        check(EffectExecutionCapabilityCatalog.assess(direct).capability() == EffectExecutionCapability.DIRECT_SERVER, "随机洗回手牌应由服务器直接结算");
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法的随机洗回手牌数据被接受");
    }
}
