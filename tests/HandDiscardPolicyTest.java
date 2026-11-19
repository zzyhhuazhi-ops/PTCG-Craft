import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.List;
import java.util.Set;

/** Printed hand-discard choices stay constrained and atomic on the server. */
public final class HandDiscardPolicyTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        CardInstance energy = hand(match, "energy");
        CardInstance tool = hand(match, "balloon");
        var energyOnly = new ServerMatch.CardSelectionPolicy(1, 1, CardCategory.ENERGY, null, false, Set.of());
        match.discardHandCardsByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A, energyOnly, List.of(energy.instanceId()));
        if (!match.privateView(PersistentTrainerTest.A).discard().contains(energy))
            throw new AssertionError("受限弃置没有移动匹配的能量卡");

        var before = match.privateView(PersistentTrainerTest.A);
        int replaySize = match.replayTranscript().size();
        rejects(() -> match.discardHandCardsByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A, energyOnly, List.of(tool.instanceId())));
        if (!before.equals(match.privateView(PersistentTrainerTest.A)) || replaySize != match.replayTranscript().size())
            throw new AssertionError("不符合手牌弃置条件的选择改变了状态或回放");

        var namedTool = new ServerMatch.CardSelectionPolicy(1, 1, CardCategory.TRAINER, null, false, Set.of("balloon"));
        match.discardHandCardsByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A, namedTool, List.of(tool.instanceId()));
        if (!match.privateView(PersistentTrainerTest.A).discard().contains(tool))
            throw new AssertionError("指定编号弃置没有接受匹配的工具卡");
        rejects(() -> new ServerMatch.CardSelectionPolicy(2, 1, null, null, false, Set.of()));
        rejects(() -> new ServerMatch.CardSelectionPolicy(0, 1, CardCategory.ENERGY, null, true, Set.of()));
        System.out.println("HandDiscardPolicyTest passed: hand discard count, card type and exact-definition policies are server-enforced");
    }
    private static CardInstance hand(ServerMatch match, String definitionId) {
        return match.privateView(PersistentTrainerTest.A).hand().stream().filter(card -> card.definitionId().equals(definitionId)).findFirst()
            .orElseThrow(() -> new AssertionError("测试夹具手牌缺少 " + definitionId));
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("服务器错误接受了无效手牌选择策略或选择");
    }
}
