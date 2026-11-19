import dev.hua.ptcglive.engine.EffectSourceZone;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.UUID;

/** An attached card always changes zone under its printed-card owner, not the field owner. */
public final class AttachedCardOwnershipTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        UUID target = match.activePokemonView(PersistentTrainerTest.A).pokemonId();
        CardInstance foreignEnergy = PersistentTrainerTest.hand(match, PersistentTrainerTest.B, "energy");
        match.attachEnergyByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, EffectSourceZone.HAND,
            foreignEnergy.instanceId(), PersistentTrainerTest.A, target);
        match.placeDamageCountersByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A, target, 60);

        if (!match.privateView(PersistentTrainerTest.B).discard().contains(foreignEnergy)
            || match.privateView(PersistentTrainerTest.A).discard().contains(foreignEnergy))
            throw new AssertionError("击倒时跨方附着能量没有进入实际拥有者弃牌区");
        PersistentTrainerTest.conserve(match);
        System.out.println("AttachedCardOwnershipTest passed: Knock Out preserves attached-card zone ownership");
    }
}
