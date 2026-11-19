import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.effect.AttackProgramRules;
import dev.hua.ptcglive.engine.effect.EffectRegistry;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/**
 * “Pokémon ex” and “Pokémon-EX” are different by the rulebook: IS_EX reads the
 * EX marker only and IS_LEGACY_EX reads EX_LEGACY only. Pokémon VMAX data that
 * also carries V counts as a Pokémon V.
 */
public final class LegacyExFamilyTest {
    private static final UUID A = new UUID(0, 6401), B = new UUID(0, 6402);
    public static void main(String[] args) {
        check(damage("IS_LEGACY_EX", Set.of(PokemonFamily.RULE_BOX, PokemonFamily.EX_LEGACY)) == 60, "Pokémon-EX 应满足 IS_LEGACY_EX");
        check(damage("IS_LEGACY_EX", Set.of(PokemonFamily.RULE_BOX, PokemonFamily.EX)) == 30, "Pokémon ex 不应满足 IS_LEGACY_EX");
        check(damage("IS_EX", Set.of(PokemonFamily.RULE_BOX, PokemonFamily.EX_LEGACY)) == 30, "Pokémon-EX 不应满足 IS_EX");
        check(damage("IS_EX", Set.of(PokemonFamily.RULE_BOX, PokemonFamily.EX)) == 60, "Pokémon ex 应满足 IS_EX");
        check(damage("IS_V", Set.of(PokemonFamily.RULE_BOX, PokemonFamily.VMAX, PokemonFamily.V)) == 60, "带 V 标记的 VMAX 应满足 IS_V");
        check(PokemonFamily.valueOf("EX_LEGACY") == PokemonFamily.EX_LEGACY, "缺少 EX_LEGACY 标记");
        System.out.println("LegacyExFamilyTest passed: Pokémon ex, Pokémon-EX and Pokémon V conditions read distinct printed markers");
    }
    static int damage(String condition, Set<PokemonFamily> defenderFamilies) {
        AttackDefinition attack = BundledCombatData.attack(JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"条件加伤\",\"energyCost\":{},"
            + "\"baseDamage\":30,\"damageType\":\"COLORLESS\",\"effectSupport\":\"SUPPORTED\",\"effects\":[{\"type\":\"conditional\",\"conditions\":"
            + "[{\"type\":\"" + condition + "\",\"target\":\"OPPONENT_ACTIVE\"}],\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}]}]}")
            .getAsJsonObject(), EffectRegistry.standard());
        check(AttackProgramRules.unsupportedReason(attack).isEmpty(), "条件加伤被拒绝：" + AttackProgramRules.unsupportedReason(attack));
        var definitions = new CardDatabase(List.of(definition("attacker", Set.of()), definition("defender", defenderFamilies)));
        var combat = new CombatCatalog(Map.of(
            "attacker", new PokemonProfile(300, 0, null, null, 0, null, 0, 1, List.of(attack)),
            "defender", new PokemonProfile(300, 0, null, null, 0, null, 0, 2, List.of())), Map.of());
        var match = new ServerMatch(A, B, deck(A, "attacker"), deck(B, "defender"), MatchRulesRegressionTest.RULE,
            definitions, combat, new ServerRandom(6403));
        OpeningTestSupport.complete(match, A, B, 0);
        while (!match.currentPlayer().equals(A) || !match.mayAttack(A)) match.endTurn(match.currentPlayer());
        match.attack(A, "hit");
        return match.activePokemonView(B).damage();
    }
    private static CardDefinition definition(String id, Set<PokemonFamily> families) {
        return new CardDefinition(id, id, CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"), true,
            false, 60, Map.of(), EffectSupport.SUPPORTED, null, families);
    }
    private static List<CardInstance> deck(UUID owner, String definitionId) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, definitionId));
        return cards;
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
