import dev.hua.ptcglive.engine.ServerMatch;
import java.util.List;

/** Top-deck discard reveals only cards that have already entered the public discard pile. */
public final class TopDeckDiscardEffectTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        List<dev.hua.ptcglive.rules.CardInstance> expected = List.of(
            match.privateView(PersistentTrainerTest.B).deck().getLast(),
            match.privateView(PersistentTrainerTest.B).deck().get(match.privateView(PersistentTrainerTest.B).deck().size() - 2));
        int deckBefore = match.privateView(PersistentTrainerTest.B).deck().size();
        int discardBefore = match.privateView(PersistentTrainerTest.B).discard().size();
        match.discardTopDeckCardsByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, 2);
        if (match.privateView(PersistentTrainerTest.B).deck().size() != deckBefore - 2
            || match.privateView(PersistentTrainerTest.B).discard().size() != discardBefore + 2
            || !match.privateView(PersistentTrainerTest.B).discard().containsAll(expected))
            throw new AssertionError("服务器没有从目标牌库顶依次弃置准确卡牌");
        var publicDiscard = match.clientView(PersistentTrainerTest.A).players().stream().filter(player -> player.id().equals(PersistentTrainerTest.B)).findFirst().orElseThrow().discard();
        if (!publicDiscard.containsAll(expected)) throw new AssertionError("已公开弃牌没有同步给对手");
        var before = match.privateView(PersistentTrainerTest.B);
        try { match.discardTopDeckCardsByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, deckBefore); }
        catch (IllegalArgumentException expectedFailure) {
            if (!before.equals(match.privateView(PersistentTrainerTest.B)) || !match.replayTranscript().getLast().operation().equals("已审核效果弃置牌库顶卡牌"))
                throw new AssertionError("超出牌库的弃置没有原子拒绝或未进入回放");
            System.out.println("TopDeckDiscardEffectTest passed: server top-deck discard reveals only public discard cards and rejects partial requests");
            return;
        }
        throw new AssertionError("超出剩余牌库的顶牌弃置被接受");
    }
}
