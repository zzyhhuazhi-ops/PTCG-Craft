import dev.hua.ptcglive.engine.ServerMatch;
import java.util.List;
import java.util.UUID;

/** Exact-budget damage-counter effects must validate the complete allocation before resolving it. */
public final class ExactDamageCounterAllocationTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        UUID active = match.activePokemonView(PersistentTrainerTest.B).pokemonId();
        UUID bench = match.benchPokemonView(PersistentTrainerTest.B).getFirst().pokemonId();

        match.placeExactDamageCountersAcrossTargetsByEffect(PersistentTrainerTest.A, 5, List.of(
            new ServerMatch.DamageCounterPlacement(PersistentTrainerTest.B, active, 2),
            new ServerMatch.DamageCounterPlacement(PersistentTrainerTest.B, bench, 3)));
        check(match.activePokemonView(PersistentTrainerTest.B).damage() == 20, "精确额度分配没有结算到战斗宝可梦");
        check(match.benchPokemonView(PersistentTrainerTest.B).getFirst().damage() == 30, "精确额度分配没有结算到备战宝可梦");
        check(match.replayTranscript().getLast().operation().contains("5个"), "精确额度没有写入可复盘记录");

        rejectAtomic(match, 5, List.of(new ServerMatch.DamageCounterPlacement(PersistentTrainerTest.B, active, 4)));
        rejectAtomic(match, 5, List.of(
            new ServerMatch.DamageCounterPlacement(PersistentTrainerTest.B, active, 3),
            new ServerMatch.DamageCounterPlacement(PersistentTrainerTest.B, bench, 3)));
        rejectAtomic(match, 2, List.of(
            new ServerMatch.DamageCounterPlacement(PersistentTrainerTest.B, active, 1),
            new ServerMatch.DamageCounterPlacement(PersistentTrainerTest.B, active, 1)));
        rejectAtomic(match, 0, List.of(new ServerMatch.DamageCounterPlacement(PersistentTrainerTest.B, active, 1)));
        System.out.println("ExactDamageCounterAllocationTest passed: exact budgets, targets, atomic rejection and replay are server-enforced");
    }

    private static void rejectAtomic(ServerMatch match, int budget, List<ServerMatch.DamageCounterPlacement> placements) {
        var before = match.clientView(PersistentTrainerTest.A);
        int replaySize = match.replayTranscript().size();
        try {
            match.placeExactDamageCountersAcrossTargetsByEffect(PersistentTrainerTest.A, budget, placements);
        } catch (IllegalArgumentException expected) {
            check(before.equals(match.clientView(PersistentTrainerTest.A)), "被拒绝的额度分配改变了对局状态");
            check(replaySize == match.replayTranscript().size(), "被拒绝的额度分配被写入回放");
            return;
        }
        throw new AssertionError("非法精确额度分配被服务器接受");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
