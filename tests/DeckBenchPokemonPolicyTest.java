import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.List;
import java.util.Set;

/** Search-and-bench effects check their Basic-only policy before touching a hidden deck. */
public final class DeckBenchPokemonPolicyTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        CardInstance basic = deck(match, "basic");
        int benchBefore = match.benchPokemonView(PersistentTrainerTest.A).size();
        int randomBefore = match.randomTranscript().size();
        var upToTwoBasics = new ServerMatch.CardSelectionPolicy(0, 2, CardCategory.POKEMON, null, true, Set.of());
        match.putBasicPokemonFromOwnDeckToBenchByEffect(PersistentTrainerTest.A, upToTwoBasics, List.of(basic.instanceId()));
        if (match.benchPokemonView(PersistentTrainerTest.A).size() != benchBefore + 1
            || match.privateView(PersistentTrainerTest.A).deck().contains(basic)
            || match.randomTranscript().size() <= randomBefore)
            throw new AssertionError("受限牌库上场没有移动基础宝可梦或洗牌审计");

        var before = match.privateView(PersistentTrainerTest.A);
        int replaySize = match.replayTranscript().size();
        var wrongName = new ServerMatch.CardSelectionPolicy(0, 2, CardCategory.POKEMON, null, true, Set.of("evolved"));
        rejects(() -> match.putBasicPokemonFromOwnDeckToBenchByEffect(PersistentTrainerTest.A, wrongName, List.of(basic.instanceId())));
        rejects(() -> match.putBasicPokemonFromOwnDeckToBenchByEffect(PersistentTrainerTest.A,
            new ServerMatch.CardSelectionPolicy(0, 1, CardCategory.POKEMON, null, false, Set.of()), List.of()));
        if (!before.equals(match.privateView(PersistentTrainerTest.A)) || replaySize != match.replayTranscript().size())
            throw new AssertionError("不符合牌库上场条件的选择改变了隐藏牌库或回放");
        System.out.println("DeckBenchPokemonPolicyTest passed: Basic-only deck-to-Bench count and definition policies are server-enforced");
    }
    private static CardInstance deck(ServerMatch match, String definitionId) {
        return match.privateView(PersistentTrainerTest.A).deck().stream().filter(card -> card.definitionId().equals(definitionId)).findFirst()
            .orElseThrow(() -> new AssertionError("测试夹具牌库缺少 " + definitionId));
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("服务器错误接受了无效牌库上场策略或选择");
    }
}
