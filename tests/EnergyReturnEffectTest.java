import dev.hua.ptcglive.engine.EffectSourceZone;
import dev.hua.ptcglive.engine.ServerMatch;
import java.util.List;
import java.util.UUID;

/** Returning attached Energy preserves its owner and never consumes manual attachment allowance. */
public final class EnergyReturnEffectTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        UUID target = match.activePokemonView(PersistentTrainerTest.B).pokemonId();
        var energy = PersistentTrainerTest.hand(match, PersistentTrainerTest.B, "energy");
        match.attachEnergyByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, EffectSourceZone.HAND, energy.instanceId(), PersistentTrainerTest.B, target);
        int handBefore = match.privateView(PersistentTrainerTest.B).hand().size();
        match.returnAttachedEnergyToHandByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, target, List.of(energy.instanceId()));
        if (match.activePokemonView(PersistentTrainerTest.B).attachedEnergyIds().contains(energy.instanceId())
            || match.privateView(PersistentTrainerTest.B).hand().size() != handBefore + 1
            || !match.privateView(PersistentTrainerTest.B).hand().contains(energy))
            throw new AssertionError("附着能量没有回到其拥有者手牌");
        try { match.returnAttachedEnergyToHandByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, target, List.of(energy.instanceId())); }
        catch (IllegalArgumentException expected) {
            if (!match.replayTranscript().getLast().operation().equals("已审核效果将附着能量回到手牌"))
                throw new AssertionError("附着能量回手没有进入服务器回放");
            PersistentTrainerTest.conserve(match);
            System.out.println("EnergyReturnEffectTest passed: server returns selected attached Energy to correct owner hand");
            return;
        }
        throw new AssertionError("已回手能量被重复从场上回收");
    }
}
