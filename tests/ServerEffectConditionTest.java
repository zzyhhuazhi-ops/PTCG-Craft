import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.effect.ConditionDefinition;
import dev.hua.ptcglive.engine.effect.ConditionType;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.engine.field.SpecialCondition;
import dev.hua.ptcglive.rules.BundledRules;
import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.CardDatabase;
import dev.hua.ptcglive.rules.CardDefinition;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.rules.CombatCatalog;
import dev.hua.ptcglive.rules.EffectSupport;
import dev.hua.ptcglive.rules.PokemonFamily;
import dev.hua.ptcglive.rules.TrainerCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Common Condition predicates are read from server state and fail closed for unaudited family metadata. */
public final class ServerEffectConditionTest {
    private static final UUID A = new UUID(0, 7461), B = new UUID(0, 7462);
    public static void main(String[] args) {
        ServerMatch match = fixture();
        UUID source = match.activePokemonView(A).pokemonId();
        check(match.matchesEffectCondition(A, source, numeric(ConditionType.BENCH_COUNT, "SELF", "1", ">=")), "备战区数量条件未读取服务器场地");
        check(match.matchesEffectCondition(A, source, new ConditionDefinition(ConditionType.IS_ACTIVE, Map.of("target", "SELF"), List.of())), "战斗位条件未读取服务器场地");
        check(match.matchesEffectCondition(A, source, new ConditionDefinition(ConditionType.IS_BASIC, Map.of("target", "SELF"), List.of())), "基础宝可梦条件未读取审核卡定义");
        check(!match.matchesEffectCondition(A, source, new ConditionDefinition(ConditionType.IS_EVOLVED, Map.of("target", "SELF"), List.of())), "仅有基础卡的进化栈被误判为已进化");
        check(match.matchesEffectCondition(A, source, new ConditionDefinition(ConditionType.IS_V, Map.of("target", "SELF"), List.of())), "经审核 V 标记没有从卡牌数据读取");
        check(!match.matchesEffectCondition(A, source, new ConditionDefinition(ConditionType.IS_EX, Map.of("target", "SELF"), List.of())), "未标注 EX 的卡牌被名称或猜测错误放行");
        check(match.matchesEffectCondition(A, source, new ConditionDefinition(ConditionType.HAS_RULE_BOX, Map.of("target", "SELF"), List.of())),
            "明确审核的 Rule Box 标记没有从卡牌数据读取");
        ServerMatch noRuleBox = fixture(Set.of(PokemonFamily.V));
        check(!noRuleBox.matchesEffectCondition(A, noRuleBox.activePokemonView(A).pokemonId(),
            new ConditionDefinition(ConditionType.HAS_RULE_BOX, Map.of("target", "SELF"), List.of())),
            "Rule Box 条件从 V 名称或族标记被擅自推断");
        match.applySpecialConditionByEffect(A, B, SpecialCondition.POISONED);
        check(match.matchesEffectCondition(A, source, new ConditionDefinition(ConditionType.TARGET_HAS_STATUS,
            Map.of("target", "OPPONENT_ACTIVE", "status", "POISONED"), List.of())), "目标异常状态条件未读取服务器场地");
        check(match.matchesEffectCondition(A, source, new ConditionDefinition(ConditionType.TURN_STATE, Map.of("value", "ACTOR_TURN"), List.of())),
            "回合条件未读取服务器当前行动方");
        relativeCounts(match, source);
        evolutionTransitions();
        System.out.println("ServerEffectConditionTest passed: reusable and relative conditions read only authoritative match state");
    }
    private static void relativeCounts(ServerMatch match, UUID source) {
        var moreDamage = new ConditionDefinition(ConditionType.DAMAGE_COUNTER_COUNT,
            Map.of("target", "SELF", "compare_target", "OPPONENT_ACTIVE", "comparison", ">"), List.of());
        match.placeDamageCountersByEffect(A, A, source, 1);
        check(match.matchesEffectCondition(A, source, moreDamage), "公开场上数值比较没有读取第二个服务器目标");

        var morePrizes = new ConditionDefinition(ConditionType.PRIZE_COUNT,
            Map.of("comparison", ">", "compare_to", "OPPONENT"), List.of());
        check(!match.matchesEffectCondition(A, source, morePrizes), "双方奖赏数相同却错误满足相对奖赏条件");
        match.endTurn(A);
        match.knockOutPokemonByEffect(B, A, source);
        match.takePrizeAt(B, 0);
        match.chooseReplacement(A, match.benchPokemonView(A).getFirst().pokemonId());
        source = match.activePokemonView(A).pokemonId();
        check(match.matchesEffectCondition(A, source, morePrizes), "拿取奖赏后没有按服务器剩余奖赏数满足相对条件");
        final UUID relativeSource = source;

        reject(() -> match.matchesEffectCondition(A, relativeSource, new ConditionDefinition(ConditionType.PRIZE_COUNT,
            Map.of("value", "1", "compare_to", "OPPONENT"), List.of())));
        reject(() -> match.matchesEffectCondition(A, relativeSource, new ConditionDefinition(ConditionType.PRIZE_COUNT,
            Map.of("compare_target", "DECK", "comparison", ">"), List.of())));
    }
    private static void evolutionTransitions() {
        UUID a=MatchRulesRegressionTest.A,b=MatchRulesRegressionTest.B;
        var m=MatchRulesRegressionTest.game(100,List.of());OpeningTestSupport.decideOrder(m,a,b);
        for(UUID seat:List.of(a,b))m.chooseOpeningActive(seat,MatchRulesRegressionTest.hand(m,seat,"base").instanceId());
        m.startAfterOpening(a);m.startAfterOpening(b);m.endTurn(a);m.endTurn(b);m.endTurn(a);
        UUID id=m.activePokemonView(b).pokemonId();var evolved=new ConditionDefinition(ConditionType.IS_EVOLVED,Map.of("target","SELF"),List.of());
        check(!m.matchesEffectCondition(b,id,evolved),"未进化的基础卡应为false");
        m.evolve(b,id,MatchRulesRegressionTest.hand(m,b,"stage-one").instanceId());
        check(m.matchesEffectCondition(b,id,evolved),"进化成功后IS_EVOLVED应为true");
        m.devolvePokemonToHandByEffect(b,b,id);
        check(!m.matchesEffectCondition(b,id,evolved),"退化至基础卡后IS_EVOLVED未清除");
    }
    private static ConditionDefinition numeric(ConditionType type, String target, String value, String comparison) {
        return new ConditionDefinition(type, Map.of("target", target, "value", value, "comparison", comparison), List.of());
    }
    private static ServerMatch fixture() { return fixture(Set.of(PokemonFamily.V, PokemonFamily.RULE_BOX)); }
    private static ServerMatch fixture(Set<PokemonFamily> families) {
        List<CardDefinition> definitions = List.of(new CardDefinition("basic", "basic", CardCategory.POKEMON, TrainerCategory.NONE,
            "H", Set.of("international"), true, false, 60, Map.of(), EffectSupport.SUPPORTED, null, families));
        CombatCatalog combat = new CombatCatalog(Map.of("basic", new PokemonProfile(70, 0, null, null, 0, null, 0, 1, List.of())), Map.of());
        for (int seed = 1; seed < 20_000; seed++) {
            ServerMatch candidate = new ServerMatch(A, B, deck(A), deck(B), BundledRules.loadRulesets().get("standard-international-2026"),
                new CardDatabase(definitions), combat, new ServerRandom(seed));
            candidate.callCoin(A, dev.hua.ptcglive.engine.CoinSide.HEADS);
            UUID winner = candidate.publicCoinResult() == dev.hua.ptcglive.engine.CoinSide.HEADS ? A : B;
            candidate.chooseWhoGoesFirst(winner, winner.equals(A));
            if (!candidate.needsMulligan(A) && !candidate.needsMulligan(B)) {
                OpeningTestSupport.complete(candidate, A, B, 1);
                if (!candidate.benchPokemonView(A).isEmpty()) return candidate;
            }
        }
        throw new AssertionError("未找到可验证 Effect 条件的测试开局");
    }
    private static List<CardInstance> deck(UUID owner) {
        List<CardInstance> cards = new ArrayList<>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "basic"));
        return cards;
    }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法相对数量条件未被服务器拒绝");
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
