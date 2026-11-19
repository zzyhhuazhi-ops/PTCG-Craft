import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.effect.EffectProgram;
import dev.hua.ptcglive.engine.effect.EffectRegistry;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** “This attack's damage isn't affected by Weakness.”: Weakness is skipped, Resistance still applies. */
public final class AttackIgnoreWeaknessTest {
    private static final UUID A = new UUID(0, 6111), B = new UUID(0, 6112);
    public static void main(String[] args) {
        assertDamage(EnergyType.GRASS, 2, null, 0, true, 40, false, false);
        assertDamage(EnergyType.GRASS, 2, null, 0, false, 80, true, false);
        assertDamage(null, 0, EnergyType.GRASS, 30, true, 10, false, true);
        var attack = attack("OPPONENT_ACTIVE", ",\"ignoresWeakness\":true");
        check(attack.ignoresWeakness() && !attack.ignoresWeaknessAndResistance() && !attack.ignoresResistance(), "JSON 未读取 ignoresWeakness");
        check(attack.withEffectProgram(EffectProgram.EMPTY).ignoresWeakness(), "替换组件程序丢失 ignoresWeakness");
        check(!attack("OPPONENT_ACTIVE", "").ignoresWeakness(), "省略字段时不应无视弱点");
        rejects(() -> attack("OPPONENT_ACTIVE", ",\"ignoresWeakness\":true,\"ignoresWeaknessAndResistance\":true"));
        rejects(() -> attack("OPPONENT_ACTIVE", ",\"ignoresWeakness\":true,\"ignoresResistance\":true"));
        rejects(() -> attack("OPPONENT_BENCH", ",\"ignoresWeakness\":true"));
        System.out.println("AttackIgnoreWeaknessTest passed: Weakness-only override keeps Resistance server-side");
    }

    static AttackDefinition attack(String target, String extra) {
        return BundledCombatData.attack(JsonParser.parseString("{\"id\":\"test\",\"nameZh\":\"测试只无视弱点\",\"energyCost\":{},"
            + "\"baseDamage\":40,\"damageType\":\"GRASS\",\"effectSupport\":\"SUPPORTED\",\"damageTarget\":\"" + target + "\"" + extra
            + "}").getAsJsonObject(), EffectRegistry.standard());
    }

    private static void assertDamage(EnergyType weakness, int multiplier, EnergyType resistance, int resistanceAmount,
                                     boolean ignoresWeakness, int expectedDamage, boolean expectedWeakness, boolean expectedResistance) {
        var definitions = new CardDatabase(List.of(definition("attacker"), definition("defender")));
        var combat = new CombatCatalog(Map.of(
            "attacker", new PokemonProfile(120, 0, null, null, 0, null, 0, 1,
                List.of(attack("OPPONENT_ACTIVE", ignoresWeakness ? ",\"ignoresWeakness\":true" : ""))),
            "defender", new PokemonProfile(120, 0, null, weakness, multiplier, resistance, resistanceAmount, 1, List.of())), Map.of());
        var match = new ServerMatch(A, B, deck(A, "attacker"), deck(B, "defender"), MatchRulesRegressionTest.RULE,
            definitions, combat, new ServerRandom(6113));
        OpeningTestSupport.complete(match, A, B, 0);
        while (!match.currentPlayer().equals(A) || !match.mayAttack(A)) match.endTurn(match.currentPlayer());
        match.attack(A, "test");
        check(match.lastAttack().finalDamage() == expectedDamage && match.lastAttack().weaknessApplied() == expectedWeakness
            && match.lastAttack().resistanceApplied() == expectedResistance,
            "只无视弱点的服务器结算与攻击数据不一致：伤害 " + match.lastAttack().finalDamage());
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
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法的只无视弱点数据被接受");
    }
}
