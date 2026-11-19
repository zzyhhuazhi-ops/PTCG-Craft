import dev.hua.ptcglive.engine.ServerMatch;
import java.util.UUID;

/** Effect healing is public-targeted but exclusively server resolved. */
public final class HealingEffectTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        UUID target = match.benchPokemonView(PersistentTrainerTest.B).getFirst().pokemonId();
        match.placeDamageCountersByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, target, 3);
        match.healDamageByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, target, 20);
        if (match.benchPokemonView(PersistentTrainerTest.B).getFirst().damage() != 10)
            throw new AssertionError("服务器效果回复量没有正确结算到对方公开目标");
        match.healDamageByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, target, 10);
        try { match.healDamageByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, target, 10); }
        catch (IllegalStateException expected) {
            if (!match.replayTranscript().getLast().operation().equals("已审核效果回复伤害"))
                throw new AssertionError("回复伤害没有进入服务器回放");
            System.out.println("HealingEffectTest passed: server-only healing targets public field state and rejects no-op");
            return;
        }
        throw new AssertionError("没有伤害的目标被静默回复");
    }
}
