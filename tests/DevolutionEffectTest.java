import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.field.SpecialCondition;
import java.util.UUID;

/** Devolution keeps damage/attachments but clears Special Conditions and continuous effects. */
public final class DevolutionEffectTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        UUID active = match.activePokemonView(PersistentTrainerTest.B).pokemonId();
        match.endTurn(PersistentTrainerTest.A);
        var evolution = PersistentTrainerTest.hand(match, PersistentTrainerTest.B, "evolved");
        match.evolve(PersistentTrainerTest.B, active, evolution.instanceId());
        match.placeDamageCountersByEffect(PersistentTrainerTest.B, PersistentTrainerTest.B, active, 2);
        match.applySpecialConditionByEffect(PersistentTrainerTest.B, PersistentTrainerTest.B, SpecialCondition.POISONED);
        match.endTurn(PersistentTrainerTest.B);
        match.devolvePokemonToHandByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, active);
        var view = match.activePokemonView(PersistentTrainerTest.B);
        // The prior turn's Poisoned Checkup adds 10 after the two counters;
        // devolution must retain that resulting 30 damage while curing status.
        if (!view.topCardId().equals("basic") || view.damage() != 30 || !view.conditions().isEmpty()
            || !match.privateView(PersistentTrainerTest.B).hand().contains(evolution))
            throw new AssertionError("退化没有保留伤害、清除异常状态并将最高进化牌回手");
        try { match.devolvePokemonToHandByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, active); }
        catch (IllegalStateException expected) {
            if (!match.replayTranscript().getLast().operation().equals("已审核效果使宝可梦退化"))
                throw new AssertionError("退化没有写入服务器回放");
            PersistentTrainerTest.conserve(match);
            System.out.println("DevolutionEffectTest passed: server devolution preserves damage/attachments, clears conditions and returns top Evolution");
            return;
        }
        throw new AssertionError("基础宝可梦被错误允许退化");
    }
}
