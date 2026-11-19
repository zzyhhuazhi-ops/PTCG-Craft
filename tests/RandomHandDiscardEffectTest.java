import dev.hua.ptcglive.engine.ServerMatch;
import java.util.List;

/** Random hand disruption must be server-chosen, replay-safe and all-or-nothing. */
public final class RandomHandDiscardEffectTest {
    public static void main(String[] args) {
        exactRandomDiscardUsesOnlyServerRandomness();
        minimumCountRejectsWithoutPartialDiscardOrRandomness();
        System.out.println("RandomHandDiscardEffectTest passed: random hand discard is server-only, audited, public-after-discard and atomic");
    }

    private static void exactRandomDiscardUsesOnlyServerRandomness() {
        ServerMatch match = PersistentTrainerTest.game();
        List<dev.hua.ptcglive.rules.CardInstance> beforeHand = match.privateView(PersistentTrainerTest.B).hand();
        int discard = match.privateView(PersistentTrainerTest.B).discard().size();
        int random = match.randomTranscript().size();
        match.discardRandomHandCardsByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, 2);
        List<dev.hua.ptcglive.rules.CardInstance> afterHand = match.privateView(PersistentTrainerTest.B).hand();
        List<dev.hua.ptcglive.rules.CardInstance> newlyDiscarded = match.privateView(PersistentTrainerTest.B).discard().subList(discard,
            match.privateView(PersistentTrainerTest.B).discard().size());
        if (afterHand.size() != beforeHand.size() - 2 || newlyDiscarded.size() != 2 || newlyDiscarded.stream().anyMatch(card -> !beforeHand.contains(card))
            || match.randomTranscript().size() != random + 2
            || match.randomTranscript().subList(random, random + 2).stream().anyMatch(event -> !event.operation().startsWith("effect-random-hand-discard:")))
            throw new AssertionError("随机弃置没有仅通过服务器随机数从目标手牌选择两张");
        if (!match.replayTranscript().getLast().operation().equals("已审核效果随机弃置手牌"))
            throw new AssertionError("随机弃置没有写入公开事实回放");
    }

    private static void minimumCountRejectsWithoutPartialDiscardOrRandomness() {
        ServerMatch match = PersistentTrainerTest.game();
        int hand = match.privateView(PersistentTrainerTest.B).hand().size();
        var before = match.privateView(PersistentTrainerTest.B);
        int random = match.randomTranscript().size();
        try {
            match.discardRandomHandCardsByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B,
                new ServerMatch.RandomHandDiscardPolicy(hand + 1, hand + 1));
        } catch (IllegalStateException expected) {
            if (!before.equals(match.privateView(PersistentTrainerTest.B)) || random != match.randomTranscript().size())
                throw new AssertionError("随机弃置数量不足时没有原子拒绝");
            return;
        }
        throw new AssertionError("手牌不足仍允许随机弃置");
    }
}
