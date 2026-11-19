import dev.hua.ptcglive.engine.CardEffectDestination;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.field.LeavePlayDestination;
import java.util.UUID;

/** Public-discard recovery remains server-only and must not leak a hidden destination. */
public final class DiscardRecoveryEffectTest {
    public static void main(String[] args) {
        recoverToHandWithoutClientLeak();
        recoverToDeckWithAuditedShuffle();
        rejectMissingDiscardCardAtomically();
        System.out.println("DiscardRecoveryEffectTest passed: server-only discard recovery preserves privacy, identity and shuffle audit");
    }

    private static void recoverToHandWithoutClientLeak() {
        ServerMatch match = PersistentTrainerTest.game();
        match.endTurn(PersistentTrainerTest.A);
        var item = PersistentTrainerTest.hand(match, PersistentTrainerTest.B, "draw-all");
        match.playTrainer(PersistentTrainerTest.B, item.instanceId(), null);
        match.endTurn(PersistentTrainerTest.B);
        int handBefore = match.privateView(PersistentTrainerTest.B).hand().size();
        match.recoverDiscardCardByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, item.instanceId(), CardEffectDestination.HAND);
        check(match.privateView(PersistentTrainerTest.B).hand().size() == handBefore + 1
            && match.privateView(PersistentTrainerTest.B).hand().contains(item), "弃牌回手没有保留原始卡牌实例");
        check(!match.privateView(PersistentTrainerTest.B).discard().contains(item), "回手后卡牌仍在弃牌区");
        check(!match.clientView(PersistentTrainerTest.A).hand().contains(item), "对方客户端收到了回手的隐藏卡牌身份");
        check(match.replayTranscript().getLast().operation().equals("已审核效果从弃牌区取回卡牌"), "弃牌取回没有写入公开回放");
        PersistentTrainerTest.conserve(match);
    }

    private static void recoverToDeckWithAuditedShuffle() {
        ServerMatch match = PersistentTrainerTest.game();
        UUID bench = match.benchPokemonView(PersistentTrainerTest.B).getFirst().pokemonId();
        UUID physical=PokemonIdentityTestSupport.physical(match,PersistentTrainerTest.B,bench);
        int deckBefore = match.privateView(PersistentTrainerTest.B).deck().size();
        match.movePokemonByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, bench, LeavePlayDestination.DISCARD);
        int randomBefore = match.randomTranscript().size();
        match.recoverDiscardCardByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, physical, CardEffectDestination.DECK_SHUFFLED);
        check(match.privateView(PersistentTrainerTest.B).deck().size() == deckBefore + 1
            && match.privateView(PersistentTrainerTest.B).deck().stream().anyMatch(card -> card.instanceId().equals(physical)), "弃牌洗回牌库失败");
        check(match.randomTranscript().size() > randomBefore, "弃牌洗回牌库没有服务器随机审计");
        PersistentTrainerTest.conserve(match);
    }

    private static void rejectMissingDiscardCardAtomically() {
        ServerMatch match = PersistentTrainerTest.game();
        var before = match.privateView(PersistentTrainerTest.B);
        int randomBefore = match.randomTranscript().size();
        try { match.recoverDiscardCardByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, UUID.randomUUID(), CardEffectDestination.HAND); }
        catch (IllegalArgumentException expected) {
            check(before.equals(match.privateView(PersistentTrainerTest.B)) && randomBefore == match.randomTranscript().size(),
                "无效弃牌目标在拒绝前改变了服务器状态");
            return;
        }
        throw new AssertionError("不在弃牌区的卡牌被取回");
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
