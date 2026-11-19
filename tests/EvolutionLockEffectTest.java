import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.UUID;

/** Evolution prevention is a server-owned expiring effect, not a client button rule. */
public final class EvolutionLockEffectTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        UUID active = match.activePokemonView(PersistentTrainerTest.A).pokemonId();
        CardInstance evolution = PersistentTrainerTest.hand(match, PersistentTrainerTest.A, "evolved");
        if (!match.canEvolve(PersistentTrainerTest.A, active, "evolved"))
            throw new AssertionError("测试夹具中的正常进化时点不可用");

        match.preventEvolutionByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A, active);
        if (match.canEvolve(PersistentTrainerTest.A, active, "evolved")
            || !match.activePokemonView(PersistentTrainerTest.A).continuousEffectZh().contains("不能进化"))
            throw new AssertionError("进化限制没有成为公开的服务器持续效果");
        var before = match.clientView(PersistentTrainerTest.A);
        rejects(() -> match.evolve(PersistentTrainerTest.A, active, evolution.instanceId()));
        if (!before.equals(match.clientView(PersistentTrainerTest.A)) || !match.privateView(PersistentTrainerTest.A).hand().contains(evolution))
            throw new AssertionError("被拒绝的进化没有保持卡牌和场面原子性");

        match.endTurn(PersistentTrainerTest.A); match.endTurn(PersistentTrainerTest.B);
        if (!match.canEvolve(PersistentTrainerTest.A, active, "evolved")
            || match.activePokemonView(PersistentTrainerTest.A).continuousEffectZh().contains("不能进化"))
            throw new AssertionError("进化限制没有在效果拥有者下回合开始时到期");
        match.evolve(PersistentTrainerTest.A, active, evolution.instanceId());
        if (!match.activePokemonView(PersistentTrainerTest.A).topCardId().equals("evolved"))
            throw new AssertionError("进化限制到期后服务器仍拒绝正常进化");
        System.out.println("EvolutionLockEffectTest passed: server evolution lock blocks, preserves atomicity and expires correctly");
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("服务器错误接受了受进化限制的进化操作");
    }
}
