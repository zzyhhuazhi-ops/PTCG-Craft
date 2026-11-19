import dev.hua.ptcglive.engine.MatchPhase;
import dev.hua.ptcglive.engine.ServerMatch;
import java.util.List;

/** Effect draws use server deck state and do not convert an empty deck into an immediate loss. */
public final class EffectDrawTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        var expected = List.of(match.privateView(PersistentTrainerTest.B).deck().getLast(),
            match.privateView(PersistentTrainerTest.B).deck().get(match.privateView(PersistentTrainerTest.B).deck().size() - 2));
        int handBefore = match.privateView(PersistentTrainerTest.B).hand().size();
        int deckBefore = match.privateView(PersistentTrainerTest.B).deck().size();
        if (match.drawCardsByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, 2) != 2
            || match.privateView(PersistentTrainerTest.B).hand().size() != handBefore + 2
            || match.privateView(PersistentTrainerTest.B).deck().size() != deckBefore - 2
            || !match.privateView(PersistentTrainerTest.B).hand().containsAll(expected))
            throw new AssertionError("服务器效果抽牌没有从目标牌库顶移动实际卡牌");
        if (match.clientView(PersistentTrainerTest.A).hand().stream().anyMatch(expected::contains))
            throw new AssertionError("对手客户端收到效果抽到的隐藏手牌身份");
        match.discardTopDeckCardsByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, match.privateView(PersistentTrainerTest.B).deck().size());
        if (match.drawCardsByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, 3) != 0 || match.phase() != MatchPhase.TURN)
            throw new AssertionError("空牌库的效果抽牌错误导致败北或返回错误张数");
        try { match.drawCardsByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, 0); }
        catch (IllegalArgumentException expectedFailure) {
            if (!match.replayTranscript().getLast().operation().equals("已审核效果抽牌"))
                throw new AssertionError("效果抽牌没有进入服务器回放");
            System.out.println("EffectDrawTest passed: server effect draw preserves hidden cards and does not deck-out mid-effect");
            return;
        }
        throw new AssertionError("零张效果抽牌被接受");
    }
}
