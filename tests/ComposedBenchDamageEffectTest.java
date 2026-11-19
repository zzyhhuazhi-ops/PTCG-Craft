import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.MatchPhase;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.effect.AttackProgramRules;
import dev.hua.ptcglive.engine.effect.EffectProgram;
import dev.hua.ptcglive.engine.effect.EffectRegistry;
import dev.hua.ptcglive.engine.effect.EffectTrigger;
import dev.hua.ptcglive.engine.effect.TriggeredEffects;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.CardDatabase;
import dev.hua.ptcglive.rules.CardDefinition;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.rules.CombatCatalog;
import dev.hua.ptcglive.rules.EffectSupport;
import dev.hua.ptcglive.rules.TrainerCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** BenchDamageEffect is an AFTER_ATTACK, server-issued target selection: no weakness/resistance, no client damage. */
public final class ComposedBenchDamageEffectTest {
    private static final UUID A = new UUID(0, 8941), B = new UUID(0, 8942);

    public static void main(String[] args) {
        selectedBenchDamageUsesAttackPipeline();
        knockoutWaitsForTheSelectedEffect();
        malformedOrWrongTimingFailsBeforeAttack();
        System.out.println("ComposedBenchDamageEffectTest passed: issued Bench target, no weakness/resistance, shared damage triggers and KO/prize timing");
    }

    private static void selectedBenchDamageUsesAttackPipeline() {
        ServerMatch match = fixture(20);
        UUID active = match.activePokemonView(B).pokemonId();
        UUID bench = match.benchPokemonView(B).getFirst().pokemonId();
        match.attack(A, "bench-damage");
        var prompt = match.effectPokemonPrompt(A);
        check(prompt.targets().size() == 1 && prompt.targets().getFirst().pokemonId().equals(bench),
            "BenchDamageEffect 没有只签发对方备战位目标");
        rejects(() -> match.chooseEffectPokemon(A, prompt.resolutionId(), prompt.step(), active));
        rejects(() -> match.effectPokemonPrompt(B));
        match.chooseEffectPokemon(A, prompt.resolutionId(), prompt.step(), bench);
        // The printed 10 Grass damage hits the Active for Weakness; the extra 20 Bench damage never does.
        check(match.activePokemonView(B).damage() == 20 && match.benchPokemonView(B).getFirst().damage() == 20,
            "备战伤害错误使用弱点／抵抗，或没有进入服务器攻击结算");
        check(match.currentPlayer().equals(B) && !match.hasPendingEffectChoice(), "备战伤害结算后没有唯一结束攻击回合");
    }

    private static void knockoutWaitsForTheSelectedEffect() {
        ServerMatch match = fixture(300);
        UUID bench = match.benchPokemonView(B).getFirst().pokemonId();
        match.attack(A, "bench-damage");
        var prompt = match.effectPokemonPrompt(A);
        check(match.phase() == MatchPhase.TURN, "选择前不应提前处理备战位击倒");
        match.chooseEffectPokemon(A, prompt.resolutionId(), prompt.step(), bench);
        check(match.phase() == MatchPhase.PRIZE_SELECTION && match.privateView(A).prizes().size() == 6,
            "备战攻击伤害击倒未进入统一奖赏卡流程");
        match.takePrizeAt(A, 0);
        check(match.currentPlayer().equals(B), "备战位击倒拿奖后攻击未正常结束");
    }

    private static void malformedOrWrongTimingFailsBeforeAttack() {
        for (String json : List.of(
            // OPPONENT_BENCH / OWN_BENCH are the whole-Bench group form since 2026-10-07 (GroupBenchDamageTest).
            "{\"type\":\"bench_damage\",\"target\":\"OWN_ACTIVE\",\"amount\":20}",
            "{\"type\":\"bench_damage\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"amount\":21}",
            "{\"type\":\"bench_damage\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"amount\":20,\"effects\":[{\"type\":\"draw\",\"amount\":1}]}")) {
            var attack = attack(EffectTrigger.AFTER_ATTACK, json);
            check(AttackProgramRules.unsupportedReason(attack).isPresent(), "错误 BenchDamageEffect 被静默接受：" + json);
        }
        var wrongTiming = attack(EffectTrigger.ON_ATTACK,
            "{\"type\":\"bench_damage\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"amount\":20}");
        ServerMatch match = fixture(wrongTiming);
        var before = match.clientView(A);
        rejects(() -> match.attack(A, wrongTiming.id()));
        check(before.equals(match.clientView(A)), "错误时序的备战伤害在拒绝前改变了对局");
    }

    private static ServerMatch fixture(int benchDamage) { return fixture(attack(EffectTrigger.AFTER_ATTACK,
        "{\"type\":\"bench_damage\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"amount\":" + benchDamage + "}")); }

    private static ServerMatch fixture(AttackDefinition attack) {
        var attacker = definition("attacker");
        var defender = definition("defender");
        var combat = new CombatCatalog(Map.of(
            "attacker", new PokemonProfile(300, 0, null, null, 0, null, 0, 1, List.of(attack)),
            "defender", new PokemonProfile(300, 0, null, EnergyType.GRASS, 2, null, 0, 1, List.of())), Map.of());
        ServerMatch match = new ServerMatch(A, B, deck(A, "attacker"), deck(B, "defender"),
            MatchRulesRegressionTest.RULE, new CardDatabase(List.of(attacker, defender)), combat, new ServerRandom(8943));
        OpeningTestSupport.complete(match, A, B, 1);
        while (!match.currentPlayer().equals(A) || !match.mayAttack(A)) match.endTurn(match.currentPlayer());
        return match;
    }

    private static AttackDefinition attack(EffectTrigger trigger, String json) {
        var effect = EffectRegistry.standard().parse(JsonParser.parseString(json).getAsJsonObject());
        return new AttackDefinition("bench-damage", "备战伤害夹具", Map.of(), 10, EnergyType.GRASS,
            EffectSupport.SUPPORTED, null).withEffectProgram(new EffectProgram(List.of(new TriggeredEffects(trigger, List.of(), List.of(effect)))));
    }

    private static CardDefinition definition(String id) {
        return new CardDefinition(id, id, CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"),
            true, false, 4, Map.of(), EffectSupport.SUPPORTED, null);
    }
    private static List<CardInstance> deck(UUID owner, String id) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, id));
        return cards;
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("服务器接受了伪造或错误时序的备战伤害目标");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
