import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.MatchSession;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.effect.AttackProgramRules;
import dev.hua.ptcglive.engine.effect.EffectRegistry;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/**
 * “This attack does N damage to 1 of your opponent's Pokémon.” (OPPONENT_ANY)
 * and “… to each of your opponent's Pokémon.” (ALL_OPPONENT_POKEMON): the
 * Active Pokémon takes Weakness/Resistance, Benched Pokémon never do.
 */
public final class OpponentAnyTargetDamageTest {
    private static final UUID A = new UUID(0, 6301), B = new UUID(0, 6302);
    public static void main(String[] args) {
        anyTargetActiveAppliesWeakness(); anyTargetBenchIgnoresWeakness(); anyTargetRejectsForgery(); anyTargetTwoOfFewer();
        allOpponentPokemon(); tableOptions(); validation();
        System.out.println("OpponentAnyTargetDamageTest passed: any-Pokémon and all-Pokémon damage apply Weakness only to the Active");
    }
    static AttackDefinition attack(String target, int count, String extra) {
        return BundledCombatData.attack(JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"任选目标\",\"energyCost\":{},\"baseDamage\":30,"
            + "\"damageType\":\"GRASS\",\"effectSupport\":\"SUPPORTED\",\"damageTarget\":\"" + target + "\""
            + (count >= 0 ? ",\"targetCount\":" + count : "") + extra + "}").getAsJsonObject(), EffectRegistry.standard());
    }
    static ServerMatch fixture(AttackDefinition attack, int benches) {
        var definitions = new CardDatabase(List.of(definition("attacker"), definition("defender")));
        var combat = new CombatCatalog(Map.of(
            "attacker", new PokemonProfile(300, 0, null, null, 0, null, 0, 1, List.of(attack)),
            "defender", new PokemonProfile(300, 0, null, EnergyType.GRASS, 2, null, 0, 1, List.of())), Map.of());
        var match = new ServerMatch(A, B, deck(A, "attacker"), deck(B, "defender"), MatchRulesRegressionTest.RULE,
            definitions, combat, new ServerRandom(6303));
        OpeningTestSupport.complete(match, A, B, benches);
        while (!match.currentPlayer().equals(A) || !match.mayAttack(A)) match.endTurn(match.currentPlayer());
        return match;
    }
    static void anyTargetActiveAppliesWeakness() {
        var m = fixture(attack("OPPONENT_ANY", 1, ""), 2);
        m.attack(A, "hit", m.activePokemonView(B).pokemonId());
        check(m.activePokemonView(B).damage() == 60 && m.lastAttack().weaknessApplied(), "选战斗宝可梦时应计算弱点");
    }
    static void anyTargetBenchIgnoresWeakness() {
        var m = fixture(attack("OPPONENT_ANY", 1, ""), 2);
        UUID bench = m.benchPokemonView(B).getFirst().pokemonId();
        m.attack(A, "hit", bench);
        check(m.benchPokemonView(B).getFirst().damage() == 30 && m.activePokemonView(B).damage() == 0
            && !m.lastAttack().weaknessApplied(), "选备战宝可梦时不应计算弱点");
    }
    static void anyTargetRejectsForgery() {
        var m = fixture(attack("OPPONENT_ANY", 1, ""), 2);
        var before = m.clientView(A);
        reject(() -> m.attack(A, "hit"));
        reject(() -> m.attack(A, "hit", m.activePokemonView(A).pokemonId()));
        reject(() -> m.attackTargets(A, "hit", List.of(m.activePokemonView(B).pokemonId(), m.benchPokemonView(B).getFirst().pokemonId())));
        check(before.equals(m.clientView(A)), "被拒绝的目标选择改变了状态");
    }
    static void anyTargetTwoOfFewer() {
        var m = fixture(attack("OPPONENT_ANY", 2, ""), 0);
        // Only the Active is in play: do as much as possible and hit that one Pokémon.
        m.attackTargets(A, "hit", List.of(m.activePokemonView(B).pokemonId()));
        check(m.activePokemonView(B).damage() == 60, "对手只有 1 只时应只打这 1 只");
        var two = fixture(attack("OPPONENT_ANY", 2, ""), 2);
        UUID active = two.activePokemonView(B).pokemonId(), bench = two.benchPokemonView(B).getFirst().pokemonId();
        reject(() -> two.attackTargets(A, "hit", List.of(active)));
        reject(() -> two.attackTargets(A, "hit", List.of(active, active)));
        two.attackTargets(A, "hit", List.of(active, bench));
        check(two.activePokemonView(B).damage() == 60 && two.benchPokemonView(B).getFirst().damage() == 30, "两个目标的弱点处理不对");
    }
    static void allOpponentPokemon() {
        var m = fixture(attack("ALL_OPPONENT_POKEMON", -1, ""), 2);
        reject(() -> m.attack(A, "hit", m.activePokemonView(B).pokemonId()));
        m.attack(A, "hit");
        check(m.activePokemonView(B).damage() == 60 && m.benchPokemonView(B).stream().allMatch(p -> p.damage() == 30),
            "全体伤害：战斗位应计算弱点、备战位不计");
        var ignore = fixture(attack("ALL_OPPONENT_POKEMON", -1, ",\"ignoresWeaknessAndResistance\":true"), 1);
        ignore.attack(A, "hit");
        check(ignore.activePokemonView(B).damage() == 30, "不计弱点抵抗的全体伤害仍计算了弱点");
    }
    static void tableOptions() {
        var m = fixture(attack("OPPONENT_ANY", 1, ""), 2);
        var labels = MatchSession.practice(m, A, A, B).view(A, A).options().stream().map(o -> o.labelZh()).filter(l -> l.contains("任选目标")).toList();
        check(labels.size() == 3 && labels.stream().anyMatch(l -> l.contains("战斗位")) && labels.stream().filter(l -> l.contains("备战区")).count() == 2,
            "卡桌没有给出对方战斗位和每只备战宝可梦作为目标：" + labels);
    }
    static void validation() {
        reject(() -> attack("OPPONENT_ANY", 0, ""));
        reject(() -> attack("ALL_OPPONENT_POKEMON", 1, ""));
        reject(() -> attack("OPPONENT_ANY", 1, ",\"conditions\":[{\"target\":\"DEFENDER\",\"condition\":\"POISONED\"}]"));
        var program = attack("OPPONENT_ANY", 1, ",\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}]");
        check(AttackProgramRules.unsupportedReason(program).isPresent(), "任选目标招式的伤害组件时序尚未接入，应被拒绝");
        check(attack("OPPONENT_ANY", 1, ",\"ignoresResistance\":true").ignoresResistance(), "任选目标招式应可只无视抵抗力");
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
    static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法的目标或数据被接受");
    }
}
