import dev.hua.ptcglive.engine.CardEffectDestination;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.List;
import java.util.Set;

/** Public discard recovery still uses a server-side count/type policy before a card re-enters a hidden zone. */
public final class DiscardRecoveryPolicyTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        CardInstance energy = hand(match, "energy");
        CardInstance tool = hand(match, "balloon");
        match.discardHandCardsByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A, List.of(energy.instanceId(), tool.instanceId()));
        var energyOnly = new ServerMatch.CardSelectionPolicy(1, 1, CardCategory.ENERGY, null, false, Set.of());
        match.recoverDiscardCardsByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A, energyOnly,
            List.of(energy.instanceId()), CardEffectDestination.HAND);
        if (!match.privateView(PersistentTrainerTest.A).hand().contains(energy)
            || match.privateView(PersistentTrainerTest.A).discard().contains(energy))
            throw new AssertionError("受限弃牌回收没有移动匹配的能量卡");

        var before = match.privateView(PersistentTrainerTest.A);
        int randomBefore = match.randomTranscript().size();
        int replaySize = match.replayTranscript().size();
        rejects(() -> match.recoverDiscardCardsByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A, energyOnly,
            List.of(tool.instanceId()), CardEffectDestination.HAND));
        if (!before.equals(match.privateView(PersistentTrainerTest.A)) || randomBefore != match.randomTranscript().size()
            || replaySize != match.replayTranscript().size())
            throw new AssertionError("不符合弃牌回收条件的选择改变了状态、随机审计或回放");

        var namedTool = new ServerMatch.CardSelectionPolicy(1, 1, CardCategory.TRAINER, null, false, Set.of("balloon"));
        match.recoverDiscardCardsByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A, namedTool,
            List.of(tool.instanceId()), CardEffectDestination.DECK_SHUFFLED);
        if (!match.privateView(PersistentTrainerTest.A).deck().contains(tool) || match.privateView(PersistentTrainerTest.A).discard().contains(tool)
            || match.randomTranscript().size() <= randomBefore)
            throw new AssertionError("指定弃牌洗回牌库没有保留卡牌实例或随机审计");
        rejects(() -> new ServerMatch.CardSelectionPolicy(1, 0, null, null, false, Set.of()));
        System.out.println("DiscardRecoveryPolicyTest passed: discard recovery count, type and exact-definition policies are server-enforced");
    }
    private static CardInstance hand(ServerMatch match, String definitionId) {
        return match.privateView(PersistentTrainerTest.A).hand().stream().filter(card -> card.definitionId().equals(definitionId)).findFirst()
            .orElseThrow(() -> new AssertionError("测试夹具手牌缺少 " + definitionId));
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("服务器错误接受了无效弃牌回收策略或选择");
    }
}
