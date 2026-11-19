import dev.hua.ptcglive.engine.EffectSourceZone;
import dev.hua.ptcglive.engine.ServerMatch;
import java.util.List;
import java.util.UUID;

/** Existing attached Energy can move only through the server's public-field validation. */
public final class EnergyMoveEffectTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        UUID source = match.activePokemonView(PersistentTrainerTest.B).pokemonId();
        UUID target = match.benchPokemonView(PersistentTrainerTest.B).getFirst().pokemonId();
        var first = PersistentTrainerTest.hand(match, PersistentTrainerTest.B, "energy");
        match.attachEnergyByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, EffectSourceZone.HAND, first.instanceId(), PersistentTrainerTest.B, source);
        match.moveAttachedEnergyByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, source, PersistentTrainerTest.B, target, List.of(first.instanceId()));
        if (!match.activePokemonView(PersistentTrainerTest.B).attachedEnergyIds().isEmpty()
            || !match.benchPokemonView(PersistentTrainerTest.B).getFirst().attachedEnergyIds().contains(first.instanceId()))
            throw new AssertionError("服务器没有将指定附着能量移动到目标宝可梦");
        try { match.moveAttachedEnergyByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, source, PersistentTrainerTest.B, target, List.of(first.instanceId())); }
        catch (IllegalArgumentException expected) {
            if (!match.replayTranscript().getLast().operation().equals("已审核效果移动附着能量"))
                throw new AssertionError("能量移动没有进入服务器回放");
            System.out.println("EnergyMoveEffectTest passed: server-only attached Energy movement preserves public-field identity and rejects invalid source");
            return;
        }
        throw new AssertionError("非来源宝可梦上的能量被重复移动");
    }
}
