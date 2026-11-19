import dev.hua.ptcglive.engine.effect.*;
import java.util.*;

/** A trigger gate is not an activation; usage belongs to a server source and named effect. */
public final class EffectLifecycleAuthorityTest {
    private static final UUID SOURCE = new UUID(0, 7541);
    public static void main(String[] args) {
        falseGateDoesNotSpendUsage();
        failedExecutionCanRetry();
        namedEffectsDoNotShareLimits();
        reentryCannotDoubleExecute();
        inactiveCoinBranchStillCountsAsActivation();
        resumableReservationAndGate();
        System.out.println("EffectLifecycleAuthorityTest passed: condition gates and successful activation own usage");
    }
    private static void falseGateDoesNotSpendUsage() {
        for (var trigger : List.of(EffectTrigger.ONCE_PER_TURN, EffectTrigger.ONCE_PER_GAME)) {
            var dispatcher = new EffectLifecycleDispatcher();
            var gate = new ConditionDefinition(ConditionType.HAND_SIZE, Map.of("value", "1", "comparison", ">="), List.of());
            var source = new EffectLifecycleDispatcher.Source(SOURCE, program(trigger, List.of(gate)));
            var context = new Context();
            var first = dispatcher.dispatch(source, trigger, 1, context);
            check(first.matchingBindings() == 1 && first.executedBindings() == 0 && context.executions == 0,
                "不满足触发条件却被记录为已执行");
            context.hand = 1;
            var second = dispatcher.dispatch(source, trigger, 1, context);
            check(second.executedBindings() == 1 && context.executions == 1, "未满足条件的尝试已错误消耗限次");
            check(dispatcher.dispatch(source, trigger, 1, context).skippedByUsageLimit() == 1,
                "成功使用没有限制重复触发");
        }
    }
    private static void failedExecutionCanRetry() {
        var dispatcher = new EffectLifecycleDispatcher();
        var source = new EffectLifecycleDispatcher.Source(SOURCE, program(EffectTrigger.ONCE_PER_TURN, List.of()));
        var context = new Context();
        context.fail = true;
        rejects(() -> dispatcher.dispatch(source, EffectTrigger.ONCE_PER_TURN, 1, context));
        context.fail = false;
        check(dispatcher.dispatch(source, EffectTrigger.ONCE_PER_TURN, 1, context).executedBindings() == 1,
            "执行失败后没有释放限次预留");
    }
    static EffectProgram program(EffectTrigger trigger, List<ConditionDefinition> gate) {
        return new EffectProgram(List.of(new TriggeredEffects(trigger, gate,
            List.of(new EffectDefinition("draw", EffectTarget.SELF, Map.of("amount", "1"), List.of(), List.of())))));
    }
    private static void namedEffectsDoNotShareLimits() {
        var dispatcher = new EffectLifecycleDispatcher();
        var program = program(EffectTrigger.ONCE_PER_TURN, List.of());
        var first = new EffectLifecycleDispatcher.Source(SOURCE, "first", program);
        var second = new EffectLifecycleDispatcher.Source(SOURCE, "second", program);
        var context = new Context();
        dispatcher.dispatch(first, EffectTrigger.ONCE_PER_TURN, 1, context);
        check(!dispatcher.usageLimited(second, EffectTrigger.ONCE_PER_TURN, 1), "不同标识的相同特性文本被错误共享次数");
        dispatcher.dispatch(second, EffectTrigger.ONCE_PER_TURN, 1, context);
        check(context.executions == 2, "同一宝可梦的第二个特性没有独立执行");
        var reconstructed = new EffectLifecycleDispatcher.Source(SOURCE, "first", program(EffectTrigger.ONCE_PER_TURN, List.of()));
        check(dispatcher.dispatch(reconstructed, EffectTrigger.ONCE_PER_TURN, 1, context).skippedByUsageLimit() == 1,
            "刷新后重建同值规则对象重置了次数");
        var altered = new EffectLifecycleDispatcher.Source(SOURCE, "first", program(EffectTrigger.ONCE_PER_GAME, List.of()));
        rejects(() -> dispatcher.dispatch(altered, EffectTrigger.ONCE_PER_GAME, 1, context));
    }
    private static void reentryCannotDoubleExecute() {
        var dispatcher = new EffectLifecycleDispatcher();
        var source = new EffectLifecycleDispatcher.Source(SOURCE, "recursive", program(EffectTrigger.ONCE_PER_TURN, List.of()));
        var context = new Context();
        context.callback = () -> rejects(() -> dispatcher.dispatch(source, EffectTrigger.ONCE_PER_TURN, 1, new Context()));
        check(dispatcher.dispatch(source, EffectTrigger.ONCE_PER_TURN, 1, context).executedBindings() == 1
            && context.executions == 1, "限次特性在完成前被重入");
    }
    private static void inactiveCoinBranchStillCountsAsActivation() {
        var heads = new ConditionDefinition(ConditionType.COIN_RESULT, Map.of("value", "HEADS"), List.of());
        var conditionalDraw = new EffectDefinition("draw", EffectTarget.SELF, Map.of("amount", "1"), List.of(heads), List.of());
        var coin = new EffectDefinition("coin_flip", EffectTarget.SELF, Map.of("flips", "1"), List.of(), List.of(conditionalDraw));
        var source = new EffectLifecycleDispatcher.Source(SOURCE, "coin", new EffectProgram(List.of(
            new TriggeredEffects(EffectTrigger.ONCE_PER_TURN, List.of(), List.of(coin)))));
        var dispatcher = new EffectLifecycleDispatcher();
        var context = new Context();
        check(dispatcher.dispatch(source, EffectTrigger.ONCE_PER_TURN, 1, context).executedBindings() == 1 && context.executions == 0,
            "反面未抽牌被错误当成未使用特性");
        check(dispatcher.usageLimited(source, EffectTrigger.ONCE_PER_TURN, 1), "反面允许无限重新掷币");
    }
    private static void resumableReservationAndGate() {
        var dispatcher = new EffectLifecycleDispatcher();
        var gate = new ConditionDefinition(ConditionType.HAND_SIZE, Map.of("value", "1", "comparison", ">="), List.of());
        var draw = new EffectDefinition("draw", EffectTarget.SELF, Map.of("amount", "1"), List.of(), List.of());
        var choice = new EffectDefinition("choice", EffectTarget.SELF, Map.of(), List.of(), List.of(draw, draw));
        var source = new EffectLifecycleDispatcher.Source(SOURCE, "resumable", new EffectProgram(List.of(
            new TriggeredEffects(EffectTrigger.ONCE_PER_TURN, List.of(gate), List.of(draw, choice)))));
        var context = new Context();
        rejects(() -> dispatcher.beginActivation(source, 1, context));
        check(!dispatcher.usageLimited(source, EffectTrigger.ONCE_PER_TURN, 1), "失败条件消耗续接次数");
        context.hand=1;
        var recursiveContext=new Context();recursiveContext.hand=1;
        context.conditionCallback=()->rejects(()->dispatcher.beginActivation(source,1,recursiveContext));
        var cursor=dispatcher.beginActivation(source,1,context);
        context.hand=0;context.conditionCallback=null;cursor.advance();
        check(cursor.pendingChoice()!=null&&context.executions==1,"发动条件被错误重查，或未执行前缀");
        rejects(()->dispatcher.beginActivation(source,1,context));
        rejects(()->dispatcher.beginActivation(new EffectLifecycleDispatcher.Source(SOURCE,"resumable",
            program(EffectTrigger.ONCE_PER_TURN,List.of())),2,new Context()));
        context.fail=true;var pending=cursor.pendingChoice();cursor.select(pending.step(),0);
        rejects(cursor::advance);
        check(dispatcher.usageLimited(source,EffectTrigger.ONCE_PER_TURN,1),"续接后半段失败释放次数，允许重放前缀");
        rejects(()->dispatcher.beginActivation(source,1,context));
        context.fail=false;context.hand=1;
        rejects(()->dispatcher.beginActivation(source,2,context)); // A failed pending cursor cannot be escaped by a fabricated next turn.
        dispatcher.clearCompletedMatch();
        check(dispatcher.beginActivation(source,2,context)!=null,"整局安全结束后未释放旧游标");
    }
    private static final class Context implements EffectExecutionContext {
        int hand, executions;
        boolean fail;
        Runnable callback;
        Runnable conditionCallback;
        @Override public CoinResult flipCoins(EffectDefinition effect, int flips) { return new CoinResult(flips, 0); }
        @Override public int choose(EffectDefinition effect, int options) { return 0; }
        @Override public void executeAtomic(EffectDefinition effect) {
            if (fail) throw new IllegalStateException("验证失败");
            if (callback != null) callback.run();
            executions++;
        }
        @Override public int number(ConditionType type, EffectTarget target, Map<String, String> args) {
            if(conditionCallback!=null)conditionCallback.run();return hand;
        }
        @Override public boolean hasStatus(EffectTarget target, String status) { return false; }
        @Override public boolean flag(ConditionType type, EffectTarget target, Map<String, String> args) { return false; }
        @Override public boolean coinResult(String expected) { return false; }
        @Override public boolean customCondition(ConditionDefinition condition) { return false; }
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("无效特性触发被接受");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
