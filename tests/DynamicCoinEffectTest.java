import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.effect.ConditionDefinition;
import dev.hua.ptcglive.engine.effect.ConditionType;
import dev.hua.ptcglive.engine.effect.EffectDefinition;
import dev.hua.ptcglive.engine.effect.EffectExecutionContext;
import dev.hua.ptcglive.engine.effect.EffectExecutionContext.CoinResult;
import dev.hua.ptcglive.engine.effect.EffectInterpreter;
import dev.hua.ptcglive.engine.effect.EffectProgram;
import dev.hua.ptcglive.engine.effect.EffectRegistry;
import dev.hua.ptcglive.engine.effect.EffectTarget;
import dev.hua.ptcglive.engine.effect.EffectTrigger;
import dev.hua.ptcglive.engine.effect.TriggeredEffects;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Regression coverage for reusable variable flips, retries and per-head branches. */
public final class DynamicCoinEffectTest {
    public static void main(String[] args) {
        dynamicPerHeadsWithTailRetry();
        allTailsRetry();
        zeroDynamicFlipsHaveNoFalseCoinSide();
        finiteCountsAboveTwenty();
        serverAbilityUsesAuthoritativeCount();
        invalidPerHeadsIsRejectedBeforeMutation();
        System.out.println("DynamicCoinEffectTest passed: variable flips, retry and per-head composition stay server-owned");
    }

    private static void dynamicPerHeadsWithTailRetry() {
        var effect = parse("""
            {"type":"coin_flip","flips":0,"flips_from":"ENERGY_COUNT","flips_target":"SELF","flips_per":1,
             "reroll":"TAILS_ONCE","effects":[{"type":"repeat_per_heads","effects":[{"type":"draw","amount":1}]}]}
            """);
        var context = new Context(3, new CoinResult(3, 1), new CoinResult(2, 1));
        execute(effect, context);
        check(context.requested.equals(List.of(3, 2)), "动态次数或尾面重投没有按服务器事实执行");
        check(context.atomic == 2, "repeat_per_heads 没有按最终正面数重复子效果");
    }

    private static void finiteCountsAboveTwenty() {
        var effect=parse("{\"type\":\"coin_flip\",\"flips\":0,\"flips_from\":\"ENERGY_COUNT\",\"effects\":[{\"type\":\"repeat_per_heads\",\"effects\":[{\"type\":\"draw\",\"amount\":1}]}]}");
        var context=new Context(480,new CoinResult(480,480));
        execute(effect,context);
        check(context.requested.equals(List.of(480))&&context.atomic==480,"有限计数被20次保护上限截断");
        var over=new Context(481);
        reject(()->execute(effect,over));
        check(over.requested.isEmpty()&&over.atomic==0,"超范围有限次数未在随机或效果前拒绝");
    }
    private static void allTailsRetry() {
        var effect = parse("""
            {"type":"coin_flip","flips":2,"reroll":"ALL_TAILS_ONCE",
             "effects":[{"type":"repeat_per_heads","effects":[{"type":"draw","amount":1}]}]}
            """);
        var context = new Context(0, new CoinResult(2, 0), new CoinResult(2, 1));
        execute(effect, context);
        check(context.requested.equals(List.of(2, 2)) && context.atomic == 1,
            "全反面重投没有替换最终硬币结果");
    }

    private static void zeroDynamicFlipsHaveNoFalseCoinSide() {
        var effect = parse("""
            {"type":"coin_flip","flips":0,"flips_from":"BENCH_COUNT","flips_target":"SELF",
             "effects":[{"type":"repeat_per_heads","effects":[{"type":"draw","amount":1}]}]}
            """);
        var context = new Context(0);
        execute(effect, context);
        check(context.requested.isEmpty() && context.atomic == 0, "零次动态投币仍消耗随机数或执行子效果");
        var none = new CoinResult(0, 0);
        check(!none.matches("HEADS") && !none.matches("TAILS") && !none.matches("ALL_HEADS") && !none.matches("ALL_TAILS"),
            "零次投币被误判为正面或反面");
    }

    /** This goes through ServerMatch rather than the lightweight interpreter fixture. */
    private static void serverAbilityUsesAuthoritativeCount() {
        var ability = ResumableAbilityTest.ability("dynamic-coin", """
            {"type":"coin_flip","flips":0,"flips_from":"BENCH_COUNT","flips_target":"SELF",
             "effects":[{"type":"repeat_per_heads","effects":[{"type":"draw","amount":1}]}]}
            """);
        var match = ResumableAbilityTest.fixture(ability);
        int random = match.randomTranscript().size();
        int hand = match.privateView(ResumableAbilityTest.A).hand().size();
        match.useAbility(ResumableAbilityTest.A, match.activePokemonView(ResumableAbilityTest.A).pokemonId(), ability.id());
        check(match.randomTranscript().size() == random + 1, "服务器没有按当前备战区数量执行动态投币");
        int gained = match.privateView(ResumableAbilityTest.A).hand().size() - hand;
        check(gained == 0 || gained == 1, "服务器的每正面抽牌次数不在合法范围");
    }

    private static void invalidPerHeadsIsRejectedBeforeMutation() {
        var effect = parse("""
            {"type":"repeat_per_heads","effects":[{"type":"draw","amount":1}]}
            """);
        var context = new Context(1);
        reject(() -> execute(effect, context));
        check(context.atomic == 0 && context.requested.isEmpty(), "脱离硬币的 repeat_per_heads 在拒绝前改变了状态");
        reject(() -> parse("""
            {"type":"coin_flip","flips":1,"flips_from":"HP_ABOVE","effects":[{"type":"draw","amount":1}]}
            """));
    }

    private static EffectDefinition parse(String json) {
        return EffectRegistry.standard().parse(JsonParser.parseString(json).getAsJsonObject());
    }
    private static void execute(EffectDefinition effect, Context context) {
        new EffectInterpreter(context).execute(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_ATTACK, List.of(), List.of(effect)))), EffectTrigger.ON_ATTACK);
    }
    private static final class Context implements EffectExecutionContext {
        private final int count;
        private final ArrayDeque<CoinResult> results = new ArrayDeque<>();
        private final List<Integer> requested = new ArrayList<>();
        private int atomic;
        private Context(int count, CoinResult... results) { this.count = count; java.util.Collections.addAll(this.results, results); }
        @Override public CoinResult flipCoins(EffectDefinition effect, int flips) {
            requested.add(flips);
            var result = results.removeFirst();
            if (result.flips() != flips) throw new AssertionError("测试硬币次数不匹配");
            return result;
        }
        @Override public int choose(EffectDefinition effect, int choices) { return 0; }
        @Override public void executeAtomic(EffectDefinition effect) { atomic++; }
        @Override public int number(ConditionType type, EffectTarget target, Map<String, String> arguments) { return count; }
        @Override public boolean hasStatus(EffectTarget target, String status) { return false; }
        @Override public boolean flag(ConditionType type, EffectTarget target, Map<String, String> arguments) { return false; }
        @Override public boolean coinResult(String expected) { return false; }
        @Override public boolean customCondition(ConditionDefinition condition) { return false; }
    }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法动态硬币规则未被拒绝");
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
