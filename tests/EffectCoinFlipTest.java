import dev.hua.ptcglive.engine.CoinSide;
import dev.hua.ptcglive.engine.ServerMatch;
import java.util.List;

/** Non-attack coin effects use the same audited server randomness as attack coins. */
public final class EffectCoinFlipTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        int randomBefore = match.randomTranscript().size();
        List<CoinSide> results = match.flipCoinsByEffect(PersistentTrainerTest.A, 3);
        if (results.size() != 3 || results.stream().anyMatch(side -> side != CoinSide.HEADS && side != CoinSide.TAILS)
            || match.randomTranscript().size() != randomBefore + 3)
            throw new AssertionError("效果掷硬币没有由服务器产生三项审计结果");
        try { match.flipCoinsByEffect(PersistentTrainerTest.A, 0); }
        catch (IllegalArgumentException expected) {
            if (!match.replayTranscript().getLast().operation().equals("已审核效果掷硬币"))
                throw new AssertionError("效果掷硬币没有进入服务器回放");
            System.out.println("EffectCoinFlipTest passed: server-only effect coins are bounded, audited and replayed");
            return;
        }
        throw new AssertionError("零次效果掷硬币被接受");
    }
}
