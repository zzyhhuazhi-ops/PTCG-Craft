import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Explicit attack data may bypass Weakness and Resistance; omitted data keeps the normal calculation. */
public final class AttackIgnoreWeaknessResistanceTest {
    private static final UUID A = new UUID(0, 5901), B = new UUID(0, 5902);
    public static void main(String[] args) {
        assertDamage("weak", EnergyType.GRASS, 2, null, 0, false, 80, true, false);
        assertDamage("weak", EnergyType.GRASS, 2, null, 0, true, 40, false, false);
        assertDamage("resist", null, 0, EnergyType.GRASS, 30, false, 10, false, true);
        assertDamage("resist", null, 0, EnergyType.GRASS, 30, true, 40, false, false);
        System.out.println("AttackIgnoreWeaknessResistanceTest passed: explicit override bypasses only Weakness/Resistance server-side");
    }

    private static void assertDamage(String defenderId, EnergyType weakness, int multiplier, EnergyType resistance,
                                     int resistanceAmount, boolean ignores, int expectedDamage,
                                     boolean expectedWeakness, boolean expectedResistance) {
        ServerMatch match = fixture(defenderId, weakness, multiplier, resistance, resistanceAmount, ignores);
        while (!match.currentPlayer().equals(A) || !match.mayAttack(A)) match.endTurn(match.currentPlayer());
        match.attack(A, "test");
        if (match.lastAttack().finalDamage() != expectedDamage
            || match.lastAttack().weaknessApplied() != expectedWeakness
            || match.lastAttack().resistanceApplied() != expectedResistance)
            throw new AssertionError("弱点／抵抗力忽略标记的服务器结算与攻击数据不一致");
    }

    private static ServerMatch fixture(String defenderId, EnergyType weakness, int multiplier, EnergyType resistance,
                                       int resistanceAmount, boolean ignores) {
        var definitions = new CardDatabase(List.of(definition("attacker"), definition(defenderId)));
        var attack = new AttackDefinition("test", "测试忽略弱点抵抗", Map.of(), 40, EnergyType.GRASS,
            EffectSupport.SUPPORTED, null, List.of(), null, null, List.of(), AttackDefinition.DamageTarget.OPPONENT_ACTIVE,
            0, 1, false, 0, 0, ignores);
        var combat = new CombatCatalog(Map.of(
            "attacker", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(attack)),
            defenderId, new PokemonProfile(120, 0, null, weakness, multiplier, resistance, resistanceAmount, 1, List.of())), Map.of());
        var match = new ServerMatch(A, B, deck(A, "attacker"), deck(B, defenderId), MatchRulesRegressionTest.RULE,
            definitions, combat, new ServerRandom(5903));
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
