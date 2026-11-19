import dev.hua.ptcglive.engine.EffectSourceZone;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.List;
import java.util.Set;

/** Energy-effect source identities and public target scope remain server-authoritative. */
public final class EnergyAttachmentPolicyTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        CardInstance energy = PersistentTrainerTest.hand(match, PersistentTrainerTest.A, "energy");
        var ownBasic = new ServerMatch.EnergyAttachmentPolicy(1, 1, ServerMatch.InPlayTargetScope.OWN_FIELD, true, Set.of());
        var target = match.activePokemonView(PersistentTrainerTest.A).pokemonId();
        match.attachEnergyByEffect(PersistentTrainerTest.A, ownBasic, PersistentTrainerTest.A, EffectSourceZone.HAND,
            List.of(energy.instanceId()), PersistentTrainerTest.A, target);
        if (!match.activePokemonView(PersistentTrainerTest.A).attachedEnergyIds().contains(energy.instanceId()))
            throw new AssertionError("受限能量附着没有移动符合条件的基础能量");

        ServerMatch rejected = PersistentTrainerTest.game();
        CardInstance nextEnergy = PersistentTrainerTest.hand(rejected, PersistentTrainerTest.A, "energy");
        var before = rejected.privateView(PersistentTrainerTest.A);
        int replaySize = rejected.replayTranscript().size();
        rejects(() -> rejected.attachEnergyByEffect(PersistentTrainerTest.A, ownBasic, PersistentTrainerTest.A, EffectSourceZone.HAND,
            List.of(nextEnergy.instanceId()), PersistentTrainerTest.B, rejected.activePokemonView(PersistentTrainerTest.B).pokemonId()));
        var wrongName = new ServerMatch.EnergyAttachmentPolicy(1, 1, ServerMatch.InPlayTargetScope.OWN_FIELD, true, Set.of("other"));
        rejects(() -> rejected.attachEnergyByEffect(PersistentTrainerTest.A, wrongName, PersistentTrainerTest.A, EffectSourceZone.HAND,
            List.of(nextEnergy.instanceId()), PersistentTrainerTest.A, rejected.activePokemonView(PersistentTrainerTest.A).pokemonId()));
        if (!before.equals(rejected.privateView(PersistentTrainerTest.A)) || replaySize != rejected.replayTranscript().size())
            throw new AssertionError("无效能量附着在拒绝前改变了隐藏区域或回放");
        System.out.println("EnergyAttachmentPolicyTest passed: Energy count, Basic/name and public target scope are server-enforced");
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("服务器错误接受了无效能量附着策略或目标");
    }
}
