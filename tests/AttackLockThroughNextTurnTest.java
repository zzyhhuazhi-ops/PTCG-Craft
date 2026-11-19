import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import java.util.*;

/**
 * “During your next turn, this Pokémon can't attack.”: prevent_attack with
 * duration SOURCE_NEXT_TURN_END lasts through the source player's own next
 * turn and expires at its end. The default still ends when that turn starts.
 */
public final class AttackLockThroughNextTurnTest {
    static final UUID A = ResumableAbilityTest.A, B = ResumableAbilityTest.B;
    public static void main(String[] args) {
        lockedThroughOwnNextTurn(); defaultEndsAtTurnStart(); benchClearsLock(); schema();
        System.out.println("AttackLockThroughNextTurnTest passed: self attack lock covers the source player's next turn only");
    }
    static void lockedThroughOwnNextTurn() {
        var a = ResumableAttackTest.attack("{\"type\":\"prevent_attack\",\"target\":\"SELF\",\"duration\":\"SOURCE_NEXT_TURN_END\"}");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(), "自身下回合不能攻击被拒绝：" + AttackProgramRules.unsupportedReason(a));
        var m = ResumableAttackTest.fixture(a);
        m.attack(A, a.id());
        check(m.currentPlayer().equals(B) && m.mayAttack(B), "对手回合受到了影响");
        m.endTurn(B);
        check(m.currentPlayer().equals(A) && !m.mayAttack(A), "自己的下个回合仍能攻击");
        var before = m.clientView(A);
        reject(() -> m.attack(A, a.id()));
        check(before.equals(m.clientView(A)), "被拒绝的攻击改变了状态");
        m.endTurn(A); m.endTurn(B);
        check(m.mayAttack(A), "自己的下个回合结束后仍不能攻击");
        m.attack(A, a.id());
    }
    static void defaultEndsAtTurnStart() {
        var a = ResumableAttackTest.attack("{\"type\":\"prevent_attack\",\"target\":\"SELF\"}");
        var m = ResumableAttackTest.fixture(a);
        m.attack(A, a.id()); m.endTurn(B);
        check(m.mayAttack(A), "默认时限应在来源玩家下个回合开始时结束");
    }
    static void benchClearsLock() {
        var a = ResumableAttackTest.attack("{\"type\":\"prevent_attack\",\"target\":\"SELF\",\"duration\":\"SOURCE_NEXT_TURN_END\"}");
        var m = ResumableAttackTest.fixture(a);
        UUID locked = m.activePokemonView(A).pokemonId();
        m.attack(A, a.id()); m.endTurn(B);
        UUID bench = m.benchPokemonView(A).getFirst().pokemonId();
        m.switchActiveByEffect(A, A, bench);
        check(m.mayAttack(A), "换上来的宝可梦不应受限制");
        m.switchActiveByEffect(A, A, locked);
        check(m.mayAttack(A), "回到备战区后效果应已清除");
    }
    static void schema() {
        var registry = EffectRegistry.standard();
        for (String bad : List.of(
            "{\"type\":\"prevent_attack\",\"target\":\"SELF\",\"duration\":\"FOREVER\"}",
            "{\"type\":\"prevent_attack\",\"target\":\"SELF\",\"amount\":1}"))
            reject(() -> registry.parse(JsonParser.parseString(bad).getAsJsonObject()));
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法的禁止攻击数据或动作被接受");
    }
}
