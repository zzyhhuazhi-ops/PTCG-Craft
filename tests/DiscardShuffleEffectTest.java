import dev.hua.ptcglive.engine.ServerMatch;
import java.util.List;

/** Whole-discard recycle is server-randomized and leaves no duplicate public cards. */
public final class DiscardShuffleEffectTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        var cards = List.of(PersistentTrainerTest.hand(match, PersistentTrainerTest.A, "energy"), PersistentTrainerTest.hand(match, PersistentTrainerTest.A, "balloon"));
        match.discardHandCardsByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A, cards.stream().map(card -> card.instanceId()).toList());
        List<dev.hua.ptcglive.rules.CardInstance> wholeDiscard = List.copyOf(match.privateView(PersistentTrainerTest.A).discard());
        int deckBefore = match.privateView(PersistentTrainerTest.A).deck().size();
        int randomBefore = match.randomTranscript().size();
        match.shuffleDiscardIntoDeckByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A);
        if (!match.privateView(PersistentTrainerTest.A).discard().isEmpty()
            || match.privateView(PersistentTrainerTest.A).deck().size() != deckBefore + wholeDiscard.size()
            || !match.privateView(PersistentTrainerTest.A).deck().containsAll(wholeDiscard)
            || match.randomTranscript().size() <= randomBefore)
            throw new AssertionError("弃牌区洗回牌库没有保持卡牌守恒或服务器随机审计");
        try { match.shuffleDiscardIntoDeckByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A); }
        catch (IllegalStateException expected) {
            if (!match.replayTranscript().getLast().operation().equals("已审核效果将弃牌区洗回牌库"))
                throw new AssertionError("弃牌回收没有进入服务器回放");
            PersistentTrainerTest.conserve(match);
            System.out.println("DiscardShuffleEffectTest passed: server discard recycle conserves instances and audits shuffle");
            return;
        }
        throw new AssertionError("空弃牌区洗回被静默接受");
    }
}
