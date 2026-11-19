import dev.hua.ptcglive.engine.ServerMatch;
import java.util.List;

/** A future effect chooser may select entitled hidden cards, never forge them. */
public final class HandDiscardEffectTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        var one = match.privateView(PersistentTrainerTest.B).hand().getFirst();
        var two = match.privateView(PersistentTrainerTest.B).hand().get(1);
        int handBefore = match.privateView(PersistentTrainerTest.B).hand().size();
        int discardBefore = match.privateView(PersistentTrainerTest.B).discard().size();
        match.discardHandCardsByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, List.of(one.instanceId(), two.instanceId()));
        if (match.privateView(PersistentTrainerTest.B).hand().size() != handBefore - 2
            || match.privateView(PersistentTrainerTest.B).discard().size() != discardBefore + 2
            || !match.privateView(PersistentTrainerTest.B).discard().containsAll(List.of(one, two)))
            throw new AssertionError("服务器没有正确弃置指定手牌");
        if (match.clientView(PersistentTrainerTest.A).hand().contains(one) || match.clientView(PersistentTrainerTest.A).hand().contains(two))
            throw new AssertionError("对手客户端获得了被弃置前的隐藏手牌身份");
        var before = match.privateView(PersistentTrainerTest.B);
        try { match.discardHandCardsByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, List.of(one.instanceId(), one.instanceId())); }
        catch (IllegalArgumentException expected) {
            if (!before.equals(match.privateView(PersistentTrainerTest.B)) || !match.replayTranscript().getLast().operation().equals("已审核效果弃置手牌"))
                throw new AssertionError("无效手牌选择没有原子拒绝或有效操作未进入回放");
            System.out.println("HandDiscardEffectTest passed: server-only hand discard preserves hidden information and rejects duplicate choices");
            return;
        }
        throw new AssertionError("重复手牌选择被接受");
    }
}
