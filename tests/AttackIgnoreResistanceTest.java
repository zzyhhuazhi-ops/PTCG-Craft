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

/** “This attack's damage isn't affected by Resistance.”: Resistance is skipped, Weakness still applies. */
public final class AttackIgnoreResistanceTest {
    private static final UUID A = new UUID(0, 6101), B = new UUID(0, 6102);
    public static void main(String[] args) {
        assertDamage(null, 0, EnergyType.GRASS, 30, false, 10, false, true);
        assertDamage(null, 0, EnergyType.GRASS, 30, true, 40, false, false);
        assertDamage(EnergyType.GRASS, 2, null, 0, true, 80, true, false);
        var attack = attack(true, "OPPONENT_ACTIVE", false);
        check(attack.ignoresResistance() && !attack.ignoresWeaknessAndResistance(), "JSON 未读取 ignoresResistance");
        check(attack.withEffectProgram(EffectProgram.EMPTY).ignoresResistance(), "替换组件程序丢失 ignoresResistance");
        check(!attack(false, "OPPONENT_ACTIVE", false).ignoresResistance(), "省略字段时不应无视抵抗力");
        rejects(() -> attack(true, "OPPONENT_ACTIVE", true));
        rejects(() -> attack(true, "OPPONENT_BENCH", false));
        System.out.println("AttackIgnoreResistanceTest passed: Resistance-only override keeps Weakness server-side");
    }

    static AttackDefinition attack(boolean ignoresResistance, String target, boolean ignoresBoth) {
        return BundledCombatData.attack(JsonParser.parseString("{\"id\":\"test\",\"nameZh\":\"测试只无视抵抗力\",\"energyCost\":{},"
            + "\"baseDamage\":40,\"damageType\":\"GRASS\",\"effectSupport\":\"SUPPORTED\",\"damageTarget\":\"" + target + "\""
            + (ignoresResistance ? ",\"ignoresResistance\":true" : "") + (ignoresBoth ? ",\"ignoresWeaknessAndResistance\":true" : "")
            + "}").getAsJsonObject(), EffectRegistry.standard());
    }

    private static void assertDamage(EnergyType weakness, int multiplier, EnergyType resistance, int resistanceAmount,
                                     boolean ignoresResistance, int expectedDamage, boolean expectedWeakness, boolean expectedResistance) {
        var definitions = new CardDatabase(List.of(definition("attacker"), definition("defender")));
        var combat = new CombatCatalog(Map.of(
            "attacker", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(attack(ignoresResistance, "OPPONENT_ACTIVE", false))),
            "defender", new PokemonProfile(120, 0, null, weakness, multiplier, resistance, resistanceAmount, 1, List.of())), Map.of());
        var match = new ServerMatch(A, B, deck(A, "attacker"), deck(B, "defender"), MatchRulesRegressionTest.RULE,
            definitions, combat, new ServerRandom(6103));
        OpeningTestSupport.complete(match, A, B, 0);
        while (!match.currentPlayer().equals(A) || !match.mayAttack(A)) match.endTurn(match.currentPlayer());
        match.attack(A, "test");
        check(match.lastAttack().finalDamage() == expectedDamage && match.lastAttack().weaknessApplied() == expectedWeakness
            && match.lastAttack().resistanceApplied() == expectedResistance, "只无视抵抗力的服务器结算与攻击数据不一致");
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
        throw new AssertionError("非法的只无视抵抗力数据被接受");
    }
}
