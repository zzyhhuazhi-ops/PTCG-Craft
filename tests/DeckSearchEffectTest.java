import dev.hua.ptcglive.engine.ServerMatch;
import java.util.List;

/** Deck identities stay owner-private while a server effect moves audited selected instances. */
public final class DeckSearchEffectTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        var chosen = match.privateView(PersistentTrainerTest.A).deck().getFirst();
        int handBefore = match.privateView(PersistentTrainerTest.A).hand().size();
        int deckBefore = match.privateView(PersistentTrainerTest.A).deck().size();
        int randomBefore = match.randomTranscript().size();
        match.searchOwnDeckToHandByEffect(PersistentTrainerTest.A, List.of(chosen.instanceId()));
        if (match.privateView(PersistentTrainerTest.A).hand().size() != handBefore + 1
            || match.privateView(PersistentTrainerTest.A).deck().size() != deckBefore - 1
            || !match.privateView(PersistentTrainerTest.A).hand().contains(chosen))
            throw new AssertionError("服务器牌库检索没有移动所选实际卡牌");
        if (match.randomTranscript().size() <= randomBefore || match.clientView(PersistentTrainerTest.B).hand().contains(chosen))
            throw new AssertionError("牌库检索没有服务器洗牌审计或泄露检索到的手牌身份");
        var before = match.privateView(PersistentTrainerTest.A);
        try { match.searchOwnDeckToHandByEffect(PersistentTrainerTest.A, List.of(chosen.instanceId())); }
        catch (IllegalArgumentException expected) {
            if (!before.equals(match.privateView(PersistentTrainerTest.A)) || !match.replayTranscript().getLast().operation().equals("已审核效果从牌库检索卡牌"))
                throw new AssertionError("无效牌库选择没有原子拒绝或检索未进入回放");
            System.out.println("DeckSearchEffectTest passed: owner-only server deck search shuffles and preserves hidden identities");
            return;
        }
        throw new AssertionError("已离开牌库的卡牌被再次检索");
    }
}
