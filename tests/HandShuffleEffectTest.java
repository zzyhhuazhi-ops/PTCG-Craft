import dev.hua.ptcglive.engine.ServerMatch;
import java.util.List;

/** A whole-hand reset is server-randomized and never reveals former hand identities. */
public final class HandShuffleEffectTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        List<dev.hua.ptcglive.rules.CardInstance> formerHand = List.copyOf(match.privateView(PersistentTrainerTest.B).hand());
        int deckBefore = match.privateView(PersistentTrainerTest.B).deck().size();
        int randomBefore = match.randomTranscript().size();
        match.shuffleHandIntoDeckByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B);
        if (!match.privateView(PersistentTrainerTest.B).hand().isEmpty()
            || match.privateView(PersistentTrainerTest.B).deck().size() != deckBefore + formerHand.size()
            || !match.privateView(PersistentTrainerTest.B).deck().containsAll(formerHand))
            throw new AssertionError("整手洗回牌库没有保持卡牌守恒");
        if (match.randomTranscript().size() <= randomBefore || match.clientView(PersistentTrainerTest.A).hand().stream().anyMatch(formerHand::contains))
            throw new AssertionError("整手洗回没有服务器随机审计或泄露对方手牌身份");
        try { match.shuffleHandIntoDeckByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B); }
        catch (IllegalStateException expected) {
            if (!match.replayTranscript().getLast().operation().equals("已审核效果将手牌洗回牌库"))
                throw new AssertionError("整手洗回没有写入公开回放");
            System.out.println("HandShuffleEffectTest passed: server whole-hand shuffle conserves cards, audits randomness and preserves privacy");
            return;
        }
        throw new AssertionError("空手牌洗回被静默接受");
    }
}
