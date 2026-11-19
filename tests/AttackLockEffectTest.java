import dev.hua.ptcglive.engine.ServerMatch;
import java.util.UUID;

/** An attack lock is public, enforced before attack resolution, and expires by its source controller. */
public final class AttackLockEffectTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        UUID target = match.activePokemonView(PersistentTrainerTest.B).pokemonId();
        match.preventAttackByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, target);
        if (!match.activePokemonView(PersistentTrainerTest.B).continuousEffectZh().contains("不能攻击")
            || !match.replayTranscript().getLast().operation().equals("已审核效果禁止攻击"))
            throw new AssertionError("攻击限制没有以公开场上状态和服务器回放保存");
        match.endTurn(PersistentTrainerTest.A);
        if (match.mayAttack(PersistentTrainerTest.B)) throw new AssertionError("受攻击限制的当前战斗宝可梦仍可攻击");
        rejects(() -> match.attack(PersistentTrainerTest.B, "ko"));
        match.endTurn(PersistentTrainerTest.B);
        match.endTurn(PersistentTrainerTest.A);
        if (!match.mayAttack(PersistentTrainerTest.B) || !match.activePokemonView(PersistentTrainerTest.B).continuousEffectZh().isBlank())
            throw new AssertionError("攻击限制没有在效果拥有者下个回合开始时过期");
        System.out.println("AttackLockEffectTest passed: server attack lock blocks attacks and expires correctly");
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("受攻击限制时服务端没有拒绝攻击");
    }
}
