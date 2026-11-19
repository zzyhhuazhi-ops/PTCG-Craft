import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.effect.EffectRegistry;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/**
 * “This attack does N more damage for each {C} in your opponent's Active
 * Pokémon's Retreat Cost.” reads the defending Active Pokémon's current Retreat
 * Cost (after temporary changes) before Weakness/Resistance.
 */
public final class RetreatCostDamageModifierTest {
    private static final UUID A = new UUID(0, 6201), B = new UUID(0, 6202);
    public static void main(String[] args) {
        check(new DamageModifier(DamageModifier.Basis.DEFENDER_RETREAT_COST, 30, 0)
            .resolve(new DamageContext(0, 0, 0, 0, 6, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3)) == 90, "撤退费依据未按单位结算");
        check(new DamageContext(0, 0, 0, 0, 6, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0).defenderRetreatCost() == 0, "兼容构造器未把撤退费置 0");
        rejects(() -> new DamageContext(0, 0, 0, 0, 6, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 21));
        check(damage(3, 0, null) == 80, "撤退费 3 时应为 20 + 3×20");
        check(damage(0, 0, null) == 20, "撤退费 0 时不应追加伤害");
        check(damage(3, 0, EnergyType.GRASS) == 160, "撤退费追加伤害应在弱点之前加入");
        check(damage(2, -1, null) == 40, "临时撤退费变化应计入当前撤退费");
        rejects(() -> attack("OPPONENT_BENCH"));
        System.out.println("RetreatCostDamageModifierTest passed: retreat-cost damage reads the defender's current Retreat Cost server-side");
    }
    static AttackDefinition attack(String target) {
        return BundledCombatData.attack(JsonParser.parseString("{\"id\":\"test\",\"nameZh\":\"按撤退费追加伤害\",\"energyCost\":{},"
            + "\"baseDamage\":20,\"damageType\":\"GRASS\",\"effectSupport\":\"SUPPORTED\",\"damageTarget\":\"" + target + "\""
            + ",\"damageModifiers\":[{\"basis\":\"DEFENDER_RETREAT_COST\",\"amountPerUnit\":20}]}").getAsJsonObject(), EffectRegistry.standard());
    }
    private static int damage(int retreatCost, int temporaryChange, EnergyType weakness) {
        var definitions = new CardDatabase(List.of(definition("attacker"), definition("defender")));
        var combat = new CombatCatalog(Map.of(
            "attacker", new PokemonProfile(300, 0, null, null, 0, null, 0, 1, List.of(attack("OPPONENT_ACTIVE"))),
            "defender", new PokemonProfile(300, retreatCost, null, weakness, weakness == null ? 0 : 2, null, 0, 1, List.of())), Map.of());
        var match = new ServerMatch(A, B, deck(A, "attacker"), deck(B, "defender"), MatchRulesRegressionTest.RULE,
            definitions, combat, new ServerRandom(6203));
        OpeningTestSupport.complete(match, A, B, 0);
        while (!match.currentPlayer().equals(A) || !match.mayAttack(A)) match.endTurn(match.currentPlayer());
        if (temporaryChange != 0) match.modifyRetreatCostByEffect(A, B, match.activePokemonView(B).pokemonId(), temporaryChange);
        match.attack(A, "test");
        return match.lastAttack().finalDamage();
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
        throw new AssertionError("非法的撤退费伤害数据被接受");
    }
}
