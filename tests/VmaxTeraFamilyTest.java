import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/**
 * IS_VMAX / IS_TERA read the printed VMAX and TERA markers. The Tera rule printed on every Tera Pokémon — “As long as
 * this Pokémon is on your Bench, prevent all damage done to this Pokémon by attacks (both yours and your opponent's).” —
 * protects it from selected and whole-Bench attack damage while benched, but not while it is the Active Pokémon.
 */
public final class VmaxTeraFamilyTest {
    private static final UUID A = new UUID(0, 6501), B = new UUID(0, 6502);
    static final Set<PokemonFamily> TERA_EX = Set.of(PokemonFamily.RULE_BOX, PokemonFamily.EX, PokemonFamily.TERA);
    public static void main(String[] args) {
        check(LegacyExFamilyTest.damage("IS_VMAX", Set.of(PokemonFamily.RULE_BOX, PokemonFamily.VMAX, PokemonFamily.V)) == 60, "Pokémon VMAX 应满足 IS_VMAX");
        check(LegacyExFamilyTest.damage("IS_VMAX", Set.of(PokemonFamily.RULE_BOX, PokemonFamily.V)) == 30, "Pokémon V 不应满足 IS_VMAX");
        check(LegacyExFamilyTest.damage("IS_TERA", TERA_EX) == 60, "太晶宝可梦应满足 IS_TERA");
        check(LegacyExFamilyTest.damage("IS_TERA", Set.of(PokemonFamily.RULE_BOX, PokemonFamily.EX)) == 30, "普通宝可梦ex 不应满足 IS_TERA");
        // Doctor: “Draw 2 cards. If your opponent's Active Pokémon is a Pokémon VMAX, draw 2 more cards.”
        TrainerProgramRules.validate(new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.ON_CARD_PLAYED,
            "{\"type\":\"draw\",\"amount\":2},{\"type\":\"conditional\",\"conditions\":[{\"type\":\"IS_VMAX\",\"target\":\"OPPONENT_ACTIVE\"}],"
                + "\"effects\":[{\"type\":\"draw\",\"amount\":2}]}"))));
        teraBenchProtection();
        System.out.println("VmaxTeraFamilyTest passed: IS_VMAX／IS_TERA 读取印刷标记；备战区的太晶宝可梦不受招式伤害");
    }
    static void teraBenchProtection() {
        // Selected Bench damage (damageTarget OPPONENT_BENCH).
        for (boolean tera : List.of(true, false)) {
            var bench = attack("\"damageTarget\":\"OPPONENT_BENCH\",\"targetCount\":1");
            var m = match(bench, tera ? TERA_EX : Set.of());
            UUID target = m.benchPokemonView(B).getFirst().pokemonId();
            m.attack(A, "hit", target);
            check(m.benchPokemonView(B).getFirst().damage() == (tera ? 0 : 30), (tera ? "备战区的太晶宝可梦不应受招式伤害" : "普通备战宝可梦应受伤害"));
        }
        // “This attack also does 30 damage to each of your opponent's Benched Pokémon.”: the Active still takes damage.
        var also = attack("\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"bench_damage\",\"target\":\"OPPONENT_BENCH\",\"amount\":30}]}]");
        var m = match(also, TERA_EX);
        m.attack(A, "hit");
        check(m.activePokemonView(B).damage() == 30, "战斗位的太晶宝可梦照常受伤");
        check(m.benchPokemonView(B).getFirst().damage() == 0, "全体备战伤害不应打到太晶宝可梦");
        // Own Bench (“both yours and your opponent's”).
        var own = attack("\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"bench_damage\",\"target\":\"OWN_BENCH\",\"amount\":30}]}]");
        var self = match(own, TERA_EX, TERA_EX);
        self.attack(A, "hit");
        check(self.benchPokemonView(A).getFirst().damage() == 0, "自己备战区的太晶宝可梦也不受自己的招式伤害");
    }
    static AttackDefinition attack(String extra) {
        return BundledCombatData.attack(JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"测试\",\"energyCost\":{},\"baseDamage\":30,"
            + "\"damageType\":\"COLORLESS\",\"effectSupport\":\"SUPPORTED\"," + extra + "}").getAsJsonObject(), EffectRegistry.standard());
    }
    static ServerMatch match(AttackDefinition attack, Set<PokemonFamily> defender) { return match(attack, defender, Set.of()); }
    static ServerMatch match(AttackDefinition attack, Set<PokemonFamily> defender, Set<PokemonFamily> attacker) {
        check(AttackProgramRules.unsupportedReason(attack).isEmpty(), "测试招式被拒绝：" + AttackProgramRules.unsupportedReason(attack));
        var definitions = new CardDatabase(List.of(definition("attacker", attacker), definition("defender", defender)));
        var combat = new CombatCatalog(Map.of(
            "attacker", new PokemonProfile(300, 0, null, null, 0, null, 0, 1, List.of(attack)),
            "defender", new PokemonProfile(300, 0, null, null, 0, null, 0, 2, List.of())), Map.of());
        var match = new ServerMatch(A, B, deck(A, "attacker"), deck(B, "defender"), MatchRulesRegressionTest.RULE,
            definitions, combat, new ServerRandom(6503));
        OpeningTestSupport.complete(match, A, B, 1);
        while (!match.currentPlayer().equals(A) || !match.mayAttack(A)) match.endTurn(match.currentPlayer());
        return match;
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
