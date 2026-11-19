import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.CoinSide;
import dev.hua.ptcglive.engine.field.AbilityDefinition;
import dev.hua.ptcglive.engine.field.AbilityEffect;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.engine.effect.ConditionDefinition;
import dev.hua.ptcglive.engine.effect.ConditionType;
import dev.hua.ptcglive.engine.effect.EffectDefinition;
import dev.hua.ptcglive.engine.effect.EffectProgram;
import dev.hua.ptcglive.engine.effect.EffectTarget;
import dev.hua.ptcglive.engine.effect.EffectTrigger;
import dev.hua.ptcglive.engine.effect.TriggeredEffects;
import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.CardDatabase;
import dev.hua.ptcglive.rules.CardDefinition;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.rules.CombatCatalog;
import dev.hua.ptcglive.rules.EffectSupport;
import dev.hua.ptcglive.rules.Ruleset;
import dev.hua.ptcglive.rules.TrainerCategory;
import dev.hua.ptcglive.rules.TrainerEffect;
import dev.hua.ptcglive.rules.TrainerProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent ability suppression is a live server query, not a per-card Java exception. */
public final class StaticAbilitySuppressionTest {
    private static final UUID A = new UUID(0, 80_101), B = new UUID(0, 80_102);

    public static void main(String[] args) {
        ServerMatch match = game();
        UUID aActive = match.activePokemonView(A).pokemonId();
        UUID bActive = match.activePokemonView(B).pokemonId();

        check(match.canUseAbility(A, aActive, "draw"), "基础宝可梦的已支持特性在场地前不可用");
        check(match.abilityOptions(B).size() == 1, "对手基础宝可梦的已支持特性没有出现在服务器选项中");

        match.playTrainer(A, hand(match, A, "watchtower").instanceId(), null);
        check(!match.canUseAbility(A, aActive, "draw"), "场地没有压制己方基础宝可梦特性");
        check(match.abilityOptions(B).isEmpty(), "场地没有压制对方基础宝可梦特性");
        check(match.abilityOptions(A).isEmpty(), "被压制特性仍被服务器列为可点击选项");

        // A Stadium survives normal turn changes.  B cannot evolve during its
        // first turn, so advance to B's next turn before checking the stage
        // predicate against a real evolution.
        match.endTurn(A);
        match.endTurn(B);
        match.endTurn(A);
        match.evolve(B, bActive, hand(match, B, "evolved").instanceId());
        check(match.canUseAbility(B, bActive, "draw"), "基础限定的压制错误影响进化宝可梦");

        match.playTrainer(B, hand(match, B, "clear-stadium").instanceId(), null);
        match.endTurn(B);
        check(match.canUseAbility(A, aActive, "draw"), "场地被替换后基础宝可梦特性没有立即恢复");
        System.out.println("StaticAbilitySuppressionTest passed: live Stadium suppression covers both fields, respects stage conditions, and restores on replacement");
    }

    private static ServerMatch game() {
        Ruleset rule = dev.hua.ptcglive.rules.BundledRules.loadRulesets().get("standard-international-2026");
        var definitions = List.of(
            card("basic", CardCategory.POKEMON, TrainerCategory.NONE, true),
            card("evolved", CardCategory.POKEMON, TrainerCategory.NONE, false),
            card("watchtower", CardCategory.TRAINER, TrainerCategory.STADIUM, false),
            card("clear-stadium", CardCategory.TRAINER, TrainerCategory.STADIUM, false));
        var draw = new AbilityDefinition("draw", "测试抽牌", new AbilityEffect(AbilityEffect.Opcode.DRAW, 1), EffectSupport.SUPPORTED, null);
        var attack = new AttackDefinition("tap", "测试招式", Map.of(), 10, EnergyType.COLORLESS, EffectSupport.SUPPORTED, null);
        var suppression = new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.PASSIVE, List.of(), List.of(
            new EffectDefinition("suppress_abilities", EffectTarget.SELF, Map.of("scope", "ALL_IN_PLAY"),
                List.of(new ConditionDefinition(ConditionType.IS_BASIC, Map.of(), List.of())), List.of())))));
        var trainers = Map.of(
            "watchtower", new TrainerProfile(TrainerCategory.STADIUM,
                new TrainerEffect(TrainerEffect.Opcode.PASSIVE_STADIUM, 0, 0), suppression),
            "clear-stadium", new TrainerProfile(TrainerCategory.STADIUM,
                new TrainerEffect(TrainerEffect.Opcode.STADIUM_BASIC_RETREAT_REDUCTION, 1, 0)));
        var combat = new CombatCatalog(Map.of(
            "basic", new PokemonProfile(80, 0, null, null, 0, null, 0, 1, List.of(attack), List.of(draw), EnergyType.COLORLESS),
            "evolved", new PokemonProfile(120, 0, "basic", null, 0, null, 0, 1, List.of(attack), List.of(draw), EnergyType.COLORLESS)),
            Map.of(), trainers);
        for (int seed = 0; seed < 10_000; seed++) {
            var match = new ServerMatch(A, B, deck(A), deck(B), rule, new CardDatabase(definitions), combat, new ServerRandom(seed));
            match.callCoin(A, CoinSide.HEADS);
            UUID winner = match.publicCoinResult() == CoinSide.HEADS ? A : B;
            // The test's scripted card plays begin with A regardless of the
            // deterministic coin result; this is the winner's legal choice.
            match.chooseWhoGoesFirst(winner, winner.equals(A));
            OpeningTestSupport.complete(match, A, B, 0);
            if (match.privateView(A).hand().stream().anyMatch(card -> card.definitionId().equals("watchtower"))
                && match.privateView(B).hand().stream().anyMatch(card -> card.definitionId().equals("clear-stadium"))
                && match.privateView(B).hand().stream().anyMatch(card -> card.definitionId().equals("evolved"))) return match;
        }
        throw new AssertionError("未找到包含场地替换与进化卡的确定性测试开局");
    }
    private static List<CardInstance> deck(UUID owner) {
        var result = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) {
            String id = i < 24 ? "basic" : i < 40 ? "evolved" : i < 50 ? "watchtower" : "clear-stadium";
            result.add(new CardInstance(new UUID(owner.getLeastSignificantBits(), i + 1), owner, id));
        }
        return result;
    }
    private static CardDefinition card(String id, CardCategory category, TrainerCategory trainer, boolean basic) {
        return new CardDefinition(id, id, category, trainer, "I", Set.of("international"), basic, false, 4, Map.of(), EffectSupport.SUPPORTED, null);
    }
    private static CardInstance hand(ServerMatch match, UUID player, String id) {
        return match.privateView(player).hand().stream().filter(card -> card.definitionId().equals(id)).findFirst().orElseThrow();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
