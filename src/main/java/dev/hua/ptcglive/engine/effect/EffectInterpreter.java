package dev.hua.ptcglive.engine.effect;

import java.util.List;
import java.util.Objects;

/**
 * Resolves nested conditional, coin, choice and repeat composition. Atomic
 * operations are delegated back to the authoritative Battle Engine, so this
 * class cannot bypass costs, damage ordering, KO or hidden-zone rules.
 */
public final class EffectInterpreter {
    private final EffectExecutionContext context;

    public EffectInterpreter(EffectExecutionContext context) { this.context = Objects.requireNonNull(context, "Effect 执行上下文"); }

    public void execute(EffectProgram program, EffectTrigger trigger) {
        for (TriggeredEffects binding : program.forTrigger(trigger)) executeBinding(binding);
    }

    /** False means the activation gate did not match, not that a coin branch did nothing. */
    public boolean executeBinding(TriggeredEffects binding) {
        var execution = new EffectContinuation(binding, context);
        execution.advance();
        while (!execution.complete()) {
            if (execution.pendingCoinChoice() != null)
                throw new IllegalStateException("可选攻击重投必须由服务器续接控制器执行");
            if (execution.pendingInteraction() != null)
                throw new IllegalStateException("选牌或分配效果必须由服务器续接控制器执行");
            var choice = execution.pendingChoice();
            execution.select(choice.step(), context.choose(choice.definition(), choice.optionCount()));
            execution.advance();
        }
        return execution.activated();
    }

    public void executeAll(List<EffectDefinition> effects) {
        if (!effects.isEmpty()) executeBinding(new TriggeredEffects(EffectTrigger.ON_CARD_PLAYED, List.of(), effects));
    }

    public void execute(EffectDefinition effect) {
        executeAll(List.of(effect));
    }
}
