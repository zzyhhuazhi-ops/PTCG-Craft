import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.field.BattlefieldState;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Retreat locks are server-owned, expire by source-controller turn, and never block forced switching. */
public final class RetreatLockEffectTest {
    private static final UUID A = new UUID(0, 5501), B = new UUID(0, 5502);
    public static void main(String[] args) {
        battlefieldExpiryAndForcedSwitch();
        matchReplayAndPublicState();
        System.out.println("RetreatLockEffectTest passed: server retreat lock rejects only normal retreat and expires correctly");
    }
    private static void battlefieldExpiryAndForcedSwitch() {
        var profile = new PokemonProfile(100, 0, null, null, 0, null, 0, 1, List.of());
        var field = new BattlefieldState(A, B, Map.of("basic", profile), new ServerRandom(5503));
        field.placeInitialActive(A, card(A)); field.placeOnBench(A, card(A)); field.placeInitialActive(B, card(B)); field.beginTurn(A);
        UUID active = field.activeView(A).pokemonId(), bench = field.benchView(A).getFirst().pokemonId();
        field.preventRetreatUntilOwnerNextTurn(A, A, active);
        rejects(() -> field.retreat(A, bench, List.of()));
        field.switchActiveByEffect(A, bench);
        if (!field.activeView(A).pokemonId().equals(bench)) throw new AssertionError("撤退限制错误阻止了强制交换");
        field.beginTurn(B); field.beginTurn(A);
        field.retreat(A, active, List.of());
        if (!field.activeView(A).pokemonId().equals(active)) throw new AssertionError("撤退限制没有在效果拥有者下个回合开始时到期");
    }
    private static void matchReplayAndPublicState() {
        ServerMatch match = PersistentTrainerTest.game();
        UUID active = match.activePokemonView(PersistentTrainerTest.A).pokemonId();
        match.preventRetreatByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A, active);
        if (!match.activePokemonView(PersistentTrainerTest.A).continuousEffectZh().contains("不能撤退")
            || !match.replayTranscript().getLast().operation().equals("已审核效果禁止撤退"))
            throw new AssertionError("服务器撤退限制没有公开场上提示或回放事实");
        match.endTurn(PersistentTrainerTest.A); match.endTurn(PersistentTrainerTest.B);
        if (!match.activePokemonView(PersistentTrainerTest.A).continuousEffectZh().isBlank())
            throw new AssertionError("对局中的撤退限制没有在效果拥有者下个回合开始时清除");
    }
    private static CardInstance card(UUID owner) { return new CardInstance(UUID.randomUUID(), owner, "basic"); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("被锁定的普通撤退没有被服务器拒绝");
    }
}
