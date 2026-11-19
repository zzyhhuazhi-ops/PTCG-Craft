import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.field.LeavePlayDestination;
import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.List;
import java.util.Set;

/** Basic revival from a public discard pile still obeys count/type constraints before any card moves. */
public final class DiscardBenchPokemonPolicyTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        var benched = match.benchPokemonView(PersistentTrainerTest.A).getFirst();
        java.util.UUID physical=PokemonIdentityTestSupport.physical(match,PersistentTrainerTest.A,benched.pokemonId());
        match.movePokemonByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A, benched.pokemonId(), LeavePlayDestination.DISCARD);
        CardInstance basic = discard(match, physical);
        int benchBefore = match.benchPokemonView(PersistentTrainerTest.A).size();
        var upToTwoBasics = new ServerMatch.CardSelectionPolicy(0, 2, CardCategory.POKEMON, null, true, Set.of());
        match.putBasicPokemonFromDiscardToBenchByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A, upToTwoBasics, List.of(basic.instanceId()));
        if (match.benchPokemonView(PersistentTrainerTest.A).size() != benchBefore + 1
            || match.privateView(PersistentTrainerTest.A).discard().contains(basic))
            throw new AssertionError("受限弃牌复活没有移动基础宝可梦");

        CardInstance energy = hand(match, "energy");
        match.discardHandCardsByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A, List.of(energy.instanceId()));
        var before = match.privateView(PersistentTrainerTest.A);
        int replaySize = match.replayTranscript().size();
        rejects(() -> match.putBasicPokemonFromDiscardToBenchByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A,
            upToTwoBasics, List.of(energy.instanceId())));
        rejects(() -> match.putBasicPokemonFromDiscardToBenchByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A,
            new ServerMatch.CardSelectionPolicy(0, 1, CardCategory.POKEMON, null, false, Set.of()), List.of()));
        if (!before.equals(match.privateView(PersistentTrainerTest.A)) || replaySize != match.replayTranscript().size())
            throw new AssertionError("不符合弃牌复活条件的选择改变了状态或回放");
        System.out.println("DiscardBenchPokemonPolicyTest passed: Basic revival count and type policies are server-enforced");
    }
    private static CardInstance discard(ServerMatch match, java.util.UUID instanceId) {
        return match.privateView(PersistentTrainerTest.A).discard().stream().filter(card -> card.instanceId().equals(instanceId)).findFirst()
            .orElseThrow(() -> new AssertionError("测试夹具弃牌区缺少基础宝可梦"));
    }
    private static CardInstance hand(ServerMatch match, String definitionId) {
        return match.privateView(PersistentTrainerTest.A).hand().stream().filter(card -> card.definitionId().equals(definitionId)).findFirst()
            .orElseThrow(() -> new AssertionError("测试夹具手牌缺少 " + definitionId));
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("服务器错误接受了无效弃牌复活策略或选择");
    }
}
