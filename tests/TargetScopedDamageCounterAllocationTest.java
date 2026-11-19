import dev.hua.ptcglive.engine.ServerMatch;
import java.util.List;
import java.util.UUID;

/** Target scope and number of targets are server data, never client-side UI rules. */
public final class TargetScopedDamageCounterAllocationTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        UUID ownActive = match.activePokemonView(PersistentTrainerTest.A).pokemonId();
        UUID opponentActive = match.activePokemonView(PersistentTrainerTest.B).pokemonId();
        UUID opponentBench = match.benchPokemonView(PersistentTrainerTest.B).getFirst().pokemonId();

        var opponentBenchOnly = new ServerMatch.DamageCounterAllocation(
            3, ServerMatch.InPlayTargetScope.OPPONENT_BENCH, 1, 1);
        match.placeExactDamageCountersAcrossTargetsByEffect(PersistentTrainerTest.A, opponentBenchOnly, List.of(
            new ServerMatch.DamageCounterPlacement(PersistentTrainerTest.B, opponentBench, 3)));
        check(match.benchPokemonView(PersistentTrainerTest.B).getFirst().damage() == 30, "对方备战区目标没有由服务器接受");

        rejectAtomic(match, opponentBenchOnly, List.of(
            new ServerMatch.DamageCounterPlacement(PersistentTrainerTest.B, opponentActive, 3)));
        rejectAtomic(match, opponentBenchOnly, List.of(
            new ServerMatch.DamageCounterPlacement(PersistentTrainerTest.A, ownActive, 3)));

        var exactlyTwoOpponentField = new ServerMatch.DamageCounterAllocation(
            2, ServerMatch.InPlayTargetScope.OPPONENT_FIELD, 2, 2);
        rejectAtomic(match, exactlyTwoOpponentField, List.of(
            new ServerMatch.DamageCounterPlacement(PersistentTrainerTest.B, opponentBench, 2)));
        match.placeExactDamageCountersAcrossTargetsByEffect(PersistentTrainerTest.A, exactlyTwoOpponentField, List.of(
            new ServerMatch.DamageCounterPlacement(PersistentTrainerTest.B, opponentActive, 1),
            new ServerMatch.DamageCounterPlacement(PersistentTrainerTest.B, opponentBench, 1)));
        check(match.activePokemonView(PersistentTrainerTest.B).damage() == 10, "对方场上范围没有包含战斗宝可梦");
        check(match.benchPokemonView(PersistentTrainerTest.B).getFirst().damage() == 40, "目标数合规的分配没有全部结算");
        System.out.println("TargetScopedDamageCounterAllocationTest passed: scope and target-count policies are server-validated");
    }

    private static void rejectAtomic(ServerMatch match, ServerMatch.DamageCounterAllocation allocation,
                                     List<ServerMatch.DamageCounterPlacement> placements) {
        var before = match.clientView(PersistentTrainerTest.A);
        int replaySize = match.replayTranscript().size();
        try {
            match.placeExactDamageCountersAcrossTargetsByEffect(PersistentTrainerTest.A, allocation, placements);
        } catch (IllegalArgumentException expected) {
            check(before.equals(match.clientView(PersistentTrainerTest.A)), "非法目标范围改变了对局状态");
            check(replaySize == match.replayTranscript().size(), "非法目标范围写入了回放");
            return;
        }
        throw new AssertionError("服务器接受了不属于效果目标范围的宝可梦");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
