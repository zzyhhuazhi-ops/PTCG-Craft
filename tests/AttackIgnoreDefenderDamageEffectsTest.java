import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.ConditionEffect;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.engine.field.SpecialCondition;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Damage-only bypass must ignore an Active defender's prevention/reduction but not its protection from attack effects. */
public final class AttackIgnoreDefenderDamageEffectsTest {
    private static final UUID A = new UUID(0, 6101), B = new UUID(0, 6102);
    public static void main(String[] args) {
        assertResolution(false, 0);
        assertResolution(true, 40);
        System.out.println("AttackIgnoreDefenderDamageEffectsTest passed: bypass is damage-only and server-authoritative");
    }
    private static void assertResolution(boolean bypassesDamageEffects, int expectedDamage) {
        for (int seed = 0; seed < 100; seed++) {
            ServerMatch match = fixture(seed, bypassesDamageEffects);
            reachTurn(match, B);
            match.attack(B, "guard");
            if (!match.activePokemonView(B).continuousEffectZh().contains("与效果")) continue;
            match.attack(A, bypassesDamageEffects ? "pierce" : "normal");
            if (match.lastAttack().finalDamage() != expectedDamage)
                throw new AssertionError("攻击对防守方防伤效果的穿透结算不正确");
            if (match.activePokemonView(B).conditions().contains(SpecialCondition.POISONED))
                throw new AssertionError("仅穿透伤害的攻击错误穿透了防守方的攻击效果保护");
            return;
        }
        throw new AssertionError("没有找到触发完整攻击保护的可复现种子");
    }
    private static void reachTurn(ServerMatch match, UUID player) {
        while (!match.currentPlayer().equals(player) || !match.mayAttack(player)) match.endTurn(match.currentPlayer());
    }
    private static ServerMatch fixture(int seed, boolean bypassesDamageEffects) {
        var definitions = new CardDatabase(List.of(definition("striker"), definition("guarder")));
        var normal = new AttackDefinition("normal", "普通攻击", Map.of(), 40, EnergyType.COLORLESS,
            EffectSupport.SUPPORTED, null, List.of(new ConditionEffect(ConditionEffect.Target.DEFENDER, SpecialCondition.POISONED)));
        var pierce = new AttackDefinition("pierce", "穿透伤害攻击", Map.of(), 40, EnergyType.COLORLESS,
            EffectSupport.SUPPORTED, null, List.of(new ConditionEffect(ConditionEffect.Target.DEFENDER, SpecialCondition.POISONED)),
            null, null, List.of(), AttackDefinition.DamageTarget.OPPONENT_ACTIVE, 0, 1, false, 0, 0, false, 0,
            bypassesDamageEffects);
        var guard = new AttackDefinition("guard", "完整保护", Map.of(), 0, EnergyType.COLORLESS,
            EffectSupport.SUPPORTED, null, List.of(), null,
            new AttackDefinition.CoinProtection(1, 1, AttackDefinition.ProtectionScope.DAMAGE_AND_EFFECTS), List.of(),
            AttackDefinition.DamageTarget.OPPONENT_ACTIVE, 0, 1, false, 0, 0, false, 30, false);
        var combat = new CombatCatalog(Map.of(
            "striker", new PokemonProfile(160, 0, null, null, 0, null, 0, 1, List.of(normal, pierce)),
            "guarder", new PokemonProfile(160, 0, null, null, 0, null, 0, 1, List.of(guard))), Map.of());
        var match = new ServerMatch(A, B, deck(A, "striker"), deck(B, "guarder"), MatchRulesRegressionTest.RULE,
            definitions, combat, new ServerRandom(seed));
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
