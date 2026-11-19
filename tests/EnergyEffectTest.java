import dev.hua.ptcglive.engine.EffectSourceZone;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.List;
import java.util.UUID;

/** Effect energy attachment/removal is separate from the normal once-per-turn attachment. */
public final class EnergyEffectTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        UUID active = match.activePokemonView(PersistentTrainerTest.A).pokemonId();
        CardInstance energy = PersistentTrainerTest.hand(match, PersistentTrainerTest.A, "energy");
        match.attachEnergy(PersistentTrainerTest.A, energy.instanceId(), active);
        match.discardAttachedEnergyByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A, active, List.of(energy.instanceId()));
        if (!match.privateView(PersistentTrainerTest.A).discard().contains(energy)
            || match.activePokemonView(PersistentTrainerTest.A).attachedEnergyIds().contains(energy.instanceId()))
            throw new AssertionError("效果弃置附着能量没有原子地进入正确弃牌区");
        match.attachEnergyByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A, EffectSourceZone.DISCARD, energy.instanceId(),
            PersistentTrainerTest.A, active);
        if (!match.activePokemonView(PersistentTrainerTest.A).attachedEnergyIds().contains(energy.instanceId())
            || match.privateView(PersistentTrainerTest.A).discard().contains(energy))
            throw new AssertionError("效果附能没有从弃牌区正确回到场上");
        if (!match.replayTranscript().getLast().operation().equals("已审核效果附着能量"))
            throw new AssertionError("效果附能没有进入服务器回放");
        System.out.println("EnergyEffectTest passed: effect attachment/removal bypasses manual cap and preserves ownership");
    }
}
