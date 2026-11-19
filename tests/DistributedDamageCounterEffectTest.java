import dev.hua.ptcglive.engine.ServerMatch;
import java.util.List;
import java.util.UUID;

/** Distributed counters prevalidate all public targets and preserve atomic target selection. */
public final class DistributedDamageCounterEffectTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        UUID active = match.activePokemonView(PersistentTrainerTest.B).pokemonId();
        UUID bench = match.benchPokemonView(PersistentTrainerTest.B).getFirst().pokemonId();
        match.placeDamageCountersAcrossTargetsByEffect(PersistentTrainerTest.A, List.of(
            new ServerMatch.DamageCounterPlacement(PersistentTrainerTest.B, active, 2),
            new ServerMatch.DamageCounterPlacement(PersistentTrainerTest.B, bench, 3)));
        if (match.activePokemonView(PersistentTrainerTest.B).damage() != 20 || match.benchPokemonView(PersistentTrainerTest.B).getFirst().damage() != 30)
            throw new AssertionError("多目标伤害指示物没有按服务器分配结算");
        var before = match.clientView(PersistentTrainerTest.A);
        try { match.placeDamageCountersAcrossTargetsByEffect(PersistentTrainerTest.A, List.of(
            new ServerMatch.DamageCounterPlacement(PersistentTrainerTest.B, active, 1),
            new ServerMatch.DamageCounterPlacement(PersistentTrainerTest.B, active, 1))); }
        catch (IllegalArgumentException expected) {
            if (!before.equals(match.clientView(PersistentTrainerTest.A)) || !match.replayTranscript().getLast().operation().equals("已审核效果分配伤害指示物"))
                throw new AssertionError("重复目标没有原子拒绝或多目标效果未进入回放");
            System.out.println("DistributedDamageCounterEffectTest passed: server allocation validates all public targets and preserves atomic rejection");
            return;
        }
        throw new AssertionError("同一目标在一次伤害分配中被重复接受");
    }
}
