import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.rules.CardInstance;

/** Shared Stadium removal returns the card to the original controller's discard, not the effect user. */
public final class StadiumRemovalEffectTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        CardInstance stadium = PersistentTrainerTest.hand(match, PersistentTrainerTest.A, "court");
        int baseCost = match.activeRetreatCost(PersistentTrainerTest.A);
        match.playTrainer(PersistentTrainerTest.A, stadium.instanceId(), null);
        if (match.activeRetreatCost(PersistentTrainerTest.A) >= baseCost) throw new AssertionError("测试场地没有实际生效");
        match.discardStadiumByEffect(PersistentTrainerTest.A);
        if (match.clientView(null).stadium() != null || !match.privateView(PersistentTrainerTest.A).discard().contains(stadium)
            || match.activeRetreatCost(PersistentTrainerTest.A) != baseCost)
            throw new AssertionError("弃置场地没有撤销效果或进入正确弃牌区");
        try { match.discardStadiumByEffect(PersistentTrainerTest.A); throw new AssertionError("空场地弃置被接受"); }
        catch (IllegalStateException expected) { }
        if (!match.replayTranscript().getLast().operation().equals("已审核效果弃置场地"))
            throw new AssertionError("弃置场地没有进入服务器回放");
        System.out.println("StadiumRemovalEffectTest passed: shared Stadium discard restores field state and owner discard");
    }
}
