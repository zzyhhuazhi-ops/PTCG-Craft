import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Attack-declared temporary damage reduction must be public, server-resolved and expire by its owner turn. */
public final class AttackTemporaryDamageReductionTest {
    private static final UUID A = new UUID(0, 6001), B = new UUID(0, 6002);
    public static void main(String[] args) {
        ServerMatch reduced = fixture(30);
        reachTurn(reduced, A);
        reduced.attack(A, "guard");
        if (!reduced.activePokemonView(A).continuousEffectZh().contains("减少 30"))
            throw new AssertionError("攻击声明的临时减伤没有成为公开场上状态");
        reduced.attack(B, "strike");
        if (reduced.lastAttack().finalDamage() != 50 || !reduced.lastAttack().weaknessApplied())
            throw new AssertionError("攻击临时减伤没有在弱点之后由服务器结算");
        reduced.endTurn(A);
        if (!reduced.activePokemonView(A).continuousEffectZh().isBlank())
            throw new AssertionError("攻击临时减伤没有在效果拥有者下个回合开始时过期");
        reduced.attack(B, "strike");
        if (reduced.lastAttack().finalDamage() != 80)
            throw new AssertionError("过期的攻击临时减伤仍错误影响后续攻击");

        ServerMatch plain = fixture(0);
        reachTurn(plain, A);
        plain.attack(A, "guard");
        if (!plain.activePokemonView(A).continuousEffectZh().isBlank())
            throw new AssertionError("未声明临时减伤的攻击不应生成持续效果");
        plain.attack(B, "strike");
        if (plain.lastAttack().finalDamage() != 80)
            throw new AssertionError("未声明临时减伤的攻击改变了既有伤害结算");
        System.out.println("AttackTemporaryDamageReductionTest passed: attack-declared reduction is public, ordered and expiring");
    }

    private static void reachTurn(ServerMatch match, UUID player) {
        while (!match.currentPlayer().equals(player) || !match.mayAttack(player)) match.endTurn(match.currentPlayer());
    }
    private static ServerMatch fixture(int reduction) {
        var definitions = new CardDatabase(List.of(definition("guarder"), definition("striker")));
        var guard = new AttackDefinition("guard", "测试减伤", Map.of(), 0, EnergyType.COLORLESS,
            EffectSupport.SUPPORTED, null, List.of(), null, null, List.of(), AttackDefinition.DamageTarget.OPPONENT_ACTIVE,
            0, 1, false, 0, 0, false, reduction);
        var strike = new AttackDefinition("strike", "测试弱点攻击", Map.of(), 40, EnergyType.GRASS,
            EffectSupport.SUPPORTED, null);
        var combat = new CombatCatalog(Map.of(
            "guarder", new PokemonProfile(180, 0, null, EnergyType.GRASS, 2, null, 0, 1, List.of(guard)),
            "striker", new PokemonProfile(180, 0, null, null, 0, null, 0, 1, List.of(strike))), Map.of());
        var match = new ServerMatch(A, B, deck(A, "guarder"), deck(B, "striker"), MatchRulesRegressionTest.RULE,
            definitions, combat, new ServerRandom(6003));
        OpeningTestSupport.complete(match, A, B, 0);
        return match;
    }
    private static CardDefinition definition(String id) {
        return new CardDefinition(id, id, CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"), true,
            false, 60, Map.of(), EffectSupport.SUPPORTED, null);
    }
    private static List<CardInstance> deck(UUID owner, String definitionId) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, definitionId));
        return cards;
    }
}
