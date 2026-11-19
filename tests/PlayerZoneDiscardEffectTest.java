import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/**
 * discard_top_deck / discard_random_hand: printed effect text (not a cost) on a
 * player's hidden zone. With fewer cards the effect does as much as it can and
 * an empty zone does nothing; the cards only become public in the discard pile.
 */
public final class PlayerZoneDiscardEffectTest {
    static final UUID A = ResumableAbilityTest.A, B = ResumableAbilityTest.B;
    public static void main(String[] args) {
        opponentTopDeck(); ownTopDeckAsMuchAsPossible(); emptyDeckStillCompletesAttack(); randomHand(); emptyHand(); trainerProgram(); schema();
        System.out.println("PlayerZoneDiscardEffectTest passed: top-deck and random-hand discards are server-side, owner-correct and do as much as possible");
    }
    static void opponentTopDeck() {
        var a = ResumableAttackTest.attack("{\"type\":\"discard_top_deck\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":2}");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(), "攻击后弃对手牌库顶被拒绝：" + AttackProgramRules.unsupportedReason(a));
        var m = ResumableAttackTest.fixture(a); int turn = m.turnNumber();
        var deck = m.privateView(B).deck(); var top = List.of(deck.get(deck.size() - 1), deck.get(deck.size() - 2));
        int ownDeck = m.privateView(A).deck().size();
        m.attack(A, a.id());
        // The opponent then starts a turn and draws one more card.
        check(m.privateView(B).deck().size() == deck.size() - 3 && m.privateView(B).discard().containsAll(top)
            && m.privateView(B).discard().size() == 2, "没有把对手牌库最上方 2 张放入对手弃牌区");
        check(m.privateView(A).deck().size() == ownDeck && m.privateView(A).discard().isEmpty(), "弃错了玩家的牌库");
        check(m.currentPlayer().equals(B) && m.turnNumber() == turn + 1, "攻击后效果未结束回合");
    }
    static void ownTopDeckAsMuchAsPossible() {
        var a = ResumableAttackTest.attack("{\"type\":\"discard_top_deck\",\"target\":\"SELF\",\"amount\":3}");
        var m = ResumableAttackTest.fixture(a);
        m.discardTopDeckCardsByEffect(A, A, m.privateView(A).deck().size() - 2);
        int discarded = m.privateView(A).discard().size();
        m.attack(A, a.id());
        check(m.privateView(A).deck().isEmpty() && m.privateView(A).discard().size() == discarded + 2, "牌库不足时没有尽量弃置");
        check(m.phase() == MatchPhase.TURN && m.currentPlayer().equals(B), "弃置牌库顶导致了牌库耗尽判负或卡住");
    }
    static void emptyDeckStillCompletesAttack() {
        var a = ResumableAttackTest.attack("{\"type\":\"discard_top_deck\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1},{\"type\":\"draw\",\"amount\":1}");
        var m = ResumableAttackTest.fixture(a);
        m.discardTopDeckCardsByEffect(A, B, m.privateView(B).deck().size());
        int hand = m.privateView(A).hand().size(), discard = m.privateView(B).discard().size();
        m.attack(A, a.id());
        check(m.privateView(B).discard().size() == discard && m.privateView(A).hand().size() == hand + 1, "空牌库时后续文字未继续结算");
    }
    static void randomHand() {
        var a = ResumableAttackTest.attack("{\"type\":\"discard_random_hand\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(), "攻击中随机弃手牌被拒绝");
        var m = ResumableAttackTest.fixture(a);
        var hand = m.privateView(B).hand(); int ownHand = m.privateView(A).hand().size();
        m.attack(A, a.id());
        var discard = m.privateView(B).discard();
        check(m.privateView(B).hand().size() == hand.size() - 1 + 1 && discard.size() == 1 && hand.contains(discard.getFirst()),
            "没有从对手手牌中随机弃置 1 张（对手随后回合开始抽 1 张）");
        check(m.privateView(A).hand().size() == ownHand, "随机弃了攻击方的手牌");
        var r1 = ResumableAttackTest.fixture(a); var r2 = ResumableAttackTest.fixture(a);
        r1.attack(A, a.id()); r2.attack(A, a.id());
        check(r1.privateView(B).discard().getFirst().definitionId().equals(r2.privateView(B).discard().getFirst().definitionId())
            && r1.randomTranscript().equals(r2.randomTranscript()), "随机弃牌没有使用服务器可重放的随机源");
    }
    static void emptyHand() {
        var a = ResumableAttackTest.attack("{\"type\":\"discard_random_hand\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":2}");
        var m = ResumableAttackTest.fixture(a);
        m.discardRandomHandCardsByEffect(A, B, m.privateView(B).hand().size());
        int discard = m.privateView(B).discard().size();
        m.attack(A, a.id());
        check(m.privateView(B).discard().size() == discard && m.currentPlayer().equals(B), "对手没有手牌时攻击未正常结束");
    }
    static void trainerProgram() {
        var profile = BundledCombatData.trainer(JsonParser.parseString("""
            {"trainerCategory":"ITEM","effects":[{"type":"discard_top_deck","target":"DECK","amount":1}]}
            """).getAsJsonObject(), EffectRegistry.standard());
        TrainerProgramRules.validate(profile.effectProgram());
        var m = ResumableAttackTest.fixture(SharedUsageLimitTest.attack("plain", null));
        var deck = m.privateView(A).deck();
        m.executeEffectProgramByRule(A, null, profile.effectProgram(), EffectTrigger.ON_CARD_PLAYED);
        check(m.privateView(A).discard().equals(List.of(deck.getLast())), "训练家程序没有弃置自己牌库最上方 1 张");
    }
    static void schema() {
        var registry = EffectRegistry.standard();
        for (String bad : List.of(
            "{\"type\":\"discard_top_deck\",\"target\":\"OPPONENT_BENCH\",\"amount\":1}",
            "{\"type\":\"discard_top_deck\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":0}",
            "{\"type\":\"discard_top_deck\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":61}",
            "{\"type\":\"discard_top_deck\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1,\"reveal\":true}",
            "{\"type\":\"discard_random_hand\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"amount\":1}",
            "{\"type\":\"discard_random_hand\",\"target\":\"OPPONENT_ACTIVE\"}"))
            rejects(() -> registry.parse(JsonParser.parseString(bad).getAsJsonObject()));
        var direct = registry.parse(JsonParser.parseString("{\"type\":\"discard_random_hand\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}").getAsJsonObject());
        check(EffectExecutionCapabilityCatalog.assess(direct).capability() == EffectExecutionCapability.DIRECT_SERVER, "随机弃手牌应由服务器直接结算");
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法的弃牌库顶／随机弃手牌数据被接受");
    }
}
