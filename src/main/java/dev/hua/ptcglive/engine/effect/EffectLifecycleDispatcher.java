package dev.hua.ptcglive.engine.effect;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Match-scoped trigger dispatcher.  It keeps rate limits beside the rule
 * engine rather than in GUI code, so reconnects and a second client cannot
 * reset ONCE_PER_TURN or ONCE_PER_GAME Effects.
 */
public final class EffectLifecycleDispatcher {
    public record Source(UUID sourceId, String effectId, EffectProgram program) {
        /** Compatibility for callers with one entire program per source. Named abilities must use the explicit ID. */
        public Source(UUID sourceId, EffectProgram program) { this(sourceId, "anonymous", program); }
        public Source {
            Objects.requireNonNull(sourceId, "效果来源实例");
            if (effectId == null || effectId.isBlank()) throw new IllegalArgumentException("效果来源必须具有稳定标识");
            Objects.requireNonNull(program, "效果来源程序");
        }
    }
    public record DispatchResult(int matchingBindings, int executedBindings, int skippedByUsageLimit) { }

    /**
     * Reserves a validated activation before its first side effect. The cursor,
     * usage and source definition stay server-owned across UI refreshes and
     * choices. A failed later step cannot release usage and replay its prefix.
     */
    public synchronized EffectContinuation beginActivation(Source source, long turnSequence, EffectExecutionContext context) {
        Objects.requireNonNull(source); Objects.requireNonNull(context);
        if (turnSequence < 0) throw new IllegalArgumentException("回合序号不能为负数");
        var bindings = source.program().triggers();
        if (bindings.size() != 1 || !java.util.Set.of(EffectTrigger.ON_ACTIVATE, EffectTrigger.ONCE_PER_TURN, EffectTrigger.ONCE_PER_GAME)
            .contains(bindings.getFirst().trigger())) throw new IllegalArgumentException("主动续接须为单一明确发动入口");
        var trigger = bindings.getFirst().trigger();
        var sourceKey = new SourceKey(source.sourceId(), source.effectId());
        var previous = definitions.get(sourceKey);
        if (previous != null && !knownDefinition(sourceKey,previous,source.program()))
            throw new IllegalStateException("同一场上的效果标识不能在对局中替换其规则程序");
        if (usageLimited(source, trigger, turnSequence))
            throw new IllegalStateException("此特性使用资格已消耗或仍在结算");
        var binding = bindings.getFirst();
        var key = new UsageKey(sourceKey, trigger, 0);
        resolving.add(key);
        try {
            if (!binding.conditions().stream().allMatch(c -> ConditionEvaluator.matches(c, context)))
                throw new IllegalStateException("当前不满足特性使用条件");
            // Activation predicates are checked once, not again after a choice or zone change.
            var cursor = new EffectContinuation(new TriggeredEffects(binding.trigger(), List.of(), binding.effects()), new BoundContext(context));
            definitions.putIfAbsent(sourceKey, source.program());
            if (trigger == EffectTrigger.ONCE_PER_TURN) oncePerTurn.put(key, turnSequence);
            if (trigger == EffectTrigger.ONCE_PER_GAME) oncePerGame.put(key, Boolean.TRUE);
            activations.put(key, cursor);
            return cursor;
        } finally { resolving.remove(key); }
    }

    private record SourceKey(UUID sourceId, String effectId) { }

    /** A captured event already passed its trigger predicates. Reserve once, then suspend without replaying them. */
    public synchronized EffectContinuation beginCaptured(Source source,EffectTrigger trigger,long turnSequence,
                                                         EffectExecutionContext context,EnergyTriggerPolicy.Usage usage) {
        Objects.requireNonNull(source);Objects.requireNonNull(context);Objects.requireNonNull(usage);
        if(turnSequence<0)throw new IllegalArgumentException("回合序号不能为负数");
        if(!java.util.Set.of(EffectTrigger.ON_ENERGY_ATTACHED,EffectTrigger.ON_DAMAGE,EffectTrigger.ON_KNOCKOUT,
            EffectTrigger.ON_POKEMON_CHECKUP,EffectTrigger.ON_TURN_START,EffectTrigger.ON_TURN_END,
            EffectTrigger.ON_ENTER_PLAY,EffectTrigger.ON_EVOLVE,EffectTrigger.ON_RETREAT,EffectTrigger.ON_SWITCH,EffectTrigger.ON_ACTIVE_TO_BENCH,
            EffectTrigger.ON_LEAVE_PLAY,EffectTrigger.ON_CARD_PLAYED,EffectTrigger.ON_DISCARDED_FROM_DECK,EffectTrigger.ON_DISCARDED_FROM_HAND,EffectTrigger.ON_BENCH_TO_ACTIVE).contains(trigger) || source.program().triggers().size()!=1
            || source.program().triggers().getFirst().trigger()!=trigger)
            throw new IllegalArgumentException("自动续接须为已支持事件的单一明确入口");
        var sourceKey=new SourceKey(source.sourceId(),source.effectId());
        var previous=definitions.get(sourceKey);
        if(previous!=null&&!knownDefinition(sourceKey,previous,source.program()))throw new IllegalStateException("同一场上的效果标识不能替换规则程序");
        if(usageLimited(source,trigger,turnSequence,usage))throw new IllegalStateException("触发资格已消耗或仍在结算");
        var key=new UsageKey(sourceKey,trigger,0);
        var binding=source.program().triggers().getFirst();
        var cursor=new EffectContinuation(new TriggeredEffects(trigger,List.of(),binding.effects()),new BoundContext(context));
        definitions.putIfAbsent(sourceKey,source.program());
        if(usage==EnergyTriggerPolicy.Usage.ONCE_PER_TURN)oncePerTurn.put(key,turnSequence);
        if(usage==EnergyTriggerPolicy.Usage.ONCE_PER_GAME)oncePerGame.put(key,Boolean.TRUE);
        activations.put(key,cursor);
        return cursor;
    }
    private record UsageKey(SourceKey source, EffectTrigger trigger, int bindingIndex) { }
    private final Map<UsageKey, Long> oncePerTurn = new HashMap<>();
    private final Map<UsageKey, Boolean> oncePerGame = new HashMap<>();
    private final Map<SourceKey, EffectProgram> definitions = new HashMap<>();
    private final java.util.Set<UsageKey> resolving = new java.util.HashSet<>();
    /** A paused or failed cursor owns its activation until completion/match shutdown, even when usage is unlimited. */
    private final Map<UsageKey, EffectContinuation> activations = new HashMap<>();
    private boolean activationPending(UsageKey key) {
        var cursor = activations.get(key);
        return cursor != null && !cursor.complete();
    }

    /**
     * Dispatches one lifecycle event in data order.  {@code turnSequence} is
     * supplied by ServerMatch and must monotonically change at each new turn.
     * Normal triggers have no hidden rate limit; the two explicit once triggers
     * are consumed only after their program reaches the authoritative
     * interpreter.
     */
    public synchronized DispatchResult dispatch(Source source, EffectTrigger trigger, long turnSequence,
                                                EffectExecutionContext context) {
        return dispatch(source, trigger, turnSequence, context, defaultUsage(trigger), false);
    }
    /** The server event queue already latched this binding's trigger condition at the event, not at GUI confirmation. */
    public synchronized DispatchResult dispatchCaptured(Source source, EffectTrigger trigger, long turnSequence,
                                                        EffectExecutionContext context, EnergyTriggerPolicy.Usage usage) {
        return dispatch(source, trigger, turnSequence, context, usage, true);
    }
    private DispatchResult dispatch(Source source, EffectTrigger trigger, long turnSequence, EffectExecutionContext context,
                                     EnergyTriggerPolicy.Usage usage, boolean captured) {
        Objects.requireNonNull(source, "效果来源");
        Objects.requireNonNull(trigger, "效果触发器");
        Objects.requireNonNull(context, "效果执行上下文");
        if (turnSequence < 0) throw new IllegalArgumentException("回合序号不能为负数");
        var sourceKey = new SourceKey(source.sourceId(), source.effectId());
        EffectProgram previous = definitions.putIfAbsent(sourceKey, source.program());
        if (previous != null && !knownDefinition(sourceKey,previous,source.program()))
            throw new IllegalStateException("同一场上的效果标识不能在对局中替换其规则程序");
        List<TriggeredEffects> bindings = source.program().triggers();
        int matching = 0, executed = 0, skipped = 0;
        for (int index = 0; index < bindings.size(); index++) {
            TriggeredEffects binding = bindings.get(index);
            if (binding.trigger() != trigger) continue;
            matching++;
            UsageKey key = new UsageKey(sourceKey, trigger, index);
            if (resolving.contains(key) || activationPending(key)) throw new IllegalStateException("同一 Effect 不得重入结算");
            if (usage == EnergyTriggerPolicy.Usage.ONCE_PER_TURN && Long.valueOf(turnSequence).equals(oncePerTurn.get(key))) {
                skipped++;
                continue;
            }
            if (usage == EnergyTriggerPolicy.Usage.ONCE_PER_GAME && oncePerGame.containsKey(key)) {
                skipped++;
                continue;
            }
            resolving.add(key);
            try {
                var executable = captured ? new TriggeredEffects(binding.trigger(), List.of(), binding.effects()) : binding;
                if (new EffectInterpreter(new BoundContext(context)).executeBinding(executable)) {
                    if (usage == EnergyTriggerPolicy.Usage.ONCE_PER_TURN) oncePerTurn.put(key, turnSequence);
                    if (usage == EnergyTriggerPolicy.Usage.ONCE_PER_GAME) oncePerGame.put(key, Boolean.TRUE);
                    executed++;
                }
            } finally { resolving.remove(key); }
        }
        return new DispatchResult(matching, executed, skipped);
    }

    /** Non-mutating query used by the authoritative action list and final activation check. */
    public synchronized boolean usageLimited(Source source, EffectTrigger trigger, long turnSequence) {
        return usageLimited(source, trigger, turnSequence, defaultUsage(trigger));
    }
    public synchronized boolean usageLimited(Source source, EffectTrigger trigger, long turnSequence, EnergyTriggerPolicy.Usage usage) {
        if (turnSequence < 0) throw new IllegalArgumentException("回合序号不能为负数");
        SourceKey sourceKey = new SourceKey(source.sourceId(), source.effectId());
        for (int index = 0; index < source.program().triggers().size(); index++) {
            if (source.program().triggers().get(index).trigger() != trigger) continue;
            var key = new UsageKey(sourceKey, trigger, index);
            if (resolving.contains(key) || activationPending(key)
                || (usage == EnergyTriggerPolicy.Usage.ONCE_PER_TURN && Long.valueOf(turnSequence).equals(oncePerTurn.get(key)))
                || (usage == EnergyTriggerPolicy.Usage.ONCE_PER_GAME && oncePerGame.containsKey(key))) return true;
        }
        return false;
    }
    private static EnergyTriggerPolicy.Usage defaultUsage(EffectTrigger trigger) {
        return trigger == EffectTrigger.ONCE_PER_TURN ? EnergyTriggerPolicy.Usage.ONCE_PER_TURN
            : trigger == EffectTrigger.ONCE_PER_GAME ? EnergyTriggerPolicy.Usage.ONCE_PER_GAME : EnergyTriggerPolicy.Usage.NONE;
    }

    private final Map<SourceKey,java.util.Set<EffectProgram>> replacementDefinitions=new HashMap<>();
    private boolean knownDefinition(SourceKey key,EffectProgram previous,EffectProgram requested) {
        return previous==null||previous.equals(requested)||replacementDefinitions.getOrDefault(key,java.util.Set.of()).contains(requested);
    }
    /** Read-only lookup of a previously captured exact logical/entry/Ability key, even during an intervening form without it. */
    public synchronized java.util.Optional<Source> registeredSource(UUID sourceId,String effectId) {
        Objects.requireNonNull(sourceId);Objects.requireNonNull(effectId);
        var program=definitions.get(new SourceKey(sourceId,effectId));
        return program==null?java.util.Optional.empty():java.util.Optional.of(new Source(sourceId,effectId,program));
    }
    /** Pure validation, before a server-owned physical card swap commits. No generic program mutation is authorized. */
    public synchronized void validateDefinitionReplacement(Source before,Source after) {
        if(!before.sourceId().equals(after.sourceId())||!before.effectId().equals(after.effectId()))throw new IllegalArgumentException("继承特性必须属于同一在场身份和特性标识");
        var key=new SourceKey(before.sourceId(),before.effectId());
        if(!knownDefinition(key,definitions.get(key),before.program()))throw new IllegalStateException("替换前特性程序不属于已捕获的服务器来源");
    }
    /** Keep old captured cursors valid and same-Ability usage unchanged; accept only these two audited definitions. */
    public synchronized void authorizeDefinitionReplacement(Source before,Source after) {
        validateDefinitionReplacement(before,after);var key=new SourceKey(before.sourceId(),before.effectId());
        definitions.putIfAbsent(key,before.program());var known=replacementDefinitions.computeIfAbsent(key,k->new java.util.HashSet<>());
        known.add(before.program());known.add(after.program());
    }
    /** Clears a completed match only; it must not be invoked during reconnect or UI refresh. */
    public synchronized void clearCompletedMatch() {
        oncePerTurn.clear();
        oncePerGame.clear();
        definitions.clear();replacementDefinitions.clear();
        resolving.clear();
        activations.clear();
    }

    /** Small wrapper prevents an interpreter from retaining a mutable UI object as its context. */
    private record BoundContext(EffectExecutionContext delegate) implements EffectExecutionContext {
        @Override public boolean canAffect(EffectDefinition effect) { return delegate.canAffect(effect); }
        @Override public boolean hasExtraAttackEnergy(ExtraAttackEnergyRequirement requirement) { return delegate.hasExtraAttackEnergy(requirement); }
        @Override public CoinResult flipCoins(EffectDefinition source, int flips) { return delegate.flipCoins(source, flips); }
        @Override public CoinResult flipCoins(EffectDefinition source, int flips, String operation) { return delegate.flipCoins(source, flips, operation); }
        @Override public boolean canChooseNextCoinOutcome(EffectDefinition source) {return delegate.canChooseNextCoinOutcome(source);}
        @Override public boolean canChooseAtomicCoinOutcome(EffectDefinition source) {return delegate.canChooseAtomicCoinOutcome(source);}
        @Override public void chooseNextCoinOutcome(EffectDefinition source,boolean heads) {delegate.chooseNextCoinOutcome(source,heads);}
        @Override public boolean supportsCoinBindings() {return delegate.supportsCoinBindings();}
        @Override public void rememberCoinResult(String name,CoinResult result) {delegate.rememberCoinResult(name,result);}
        @Override public void coinResultCommitted(EffectDefinition source,CoinResult result) {delegate.coinResultCommitted(source,result);}
        @Override public CoinResult namedCoinResult(String name) {return delegate.namedCoinResult(name);}
        @Override public int boundCount(String name) {return delegate.boundCount(name);}
        @Override public void rememberCount(String name,int count) {delegate.rememberCount(name,count);}
        @Override public boolean supportsPokemonBindings() {return delegate.supportsPokemonBindings();}
        @Override public void rememberPokemon(String name,EffectTargetResolution.PokemonTarget target) {delegate.rememberPokemon(name,target);}
        @Override public EffectExecutionContext withNamedPokemon(String name) {
            var nested=delegate.withNamedPokemon(name);return nested==null?null:new BoundContext(nested);
        }
        @Override public EffectTargetResolution.PokemonTarget namedPokemonTarget(String name) { return delegate.namedPokemonTarget(name); }
        @Override public int choose(EffectDefinition source, int optionCount) { return delegate.choose(source, optionCount); }
        @Override public void executeAtomic(EffectDefinition effect) { delegate.executeAtomic(effect); }
        @Override public boolean hasPendingRuleChoice() {return delegate.hasPendingRuleChoice();}
        @Override public boolean requiresCardSelection(EffectDefinition effect) { return delegate.requiresCardSelection(effect); }
        @Override public InteractionKind interactionKind(EffectDefinition effect) { return delegate.interactionKind(effect); }
        @Override public void executeSelectedPokemon(EffectDefinition effect,EffectTargetResolution.PokemonTarget target) { delegate.executeSelectedPokemon(effect,target); }
        @Override public void executeSelectedPokemons(EffectDefinition effect,java.util.List<EffectTargetResolution.PokemonTarget> targets) { delegate.executeSelectedPokemons(effect,targets); }
        @Override public EffectExecutionContext withChosenPokemon(EffectTargetResolution.PokemonTarget target) { return new BoundContext(delegate.withChosenPokemon(target)); }
        @Override public EffectTargetResolution.PokemonTarget liveSourcePokemon() { return delegate.liveSourcePokemon(); }
        @Override public EffectTargetResolution.PokemonTarget liveEventPokemon(EffectTarget target) { return delegate.liveEventPokemon(target); }
        @Override public EffectTargetResolution.PokemonTarget chosenPokemonTarget() { return delegate.chosenPokemonTarget(); }
        @Override public boolean conditionAvailable(ConditionDefinition condition) { return delegate.conditionAvailable(condition); }
        @Override public int number(ConditionType type, EffectTarget target, Map<String, String> arguments) { return delegate.number(type, target, arguments); }
        @Override public int opponentNumber(ConditionType type, Map<String, String> arguments) { return delegate.opponentNumber(type, arguments); }
        @Override public boolean hasStatus(EffectTarget target, String status) { return delegate.hasStatus(target, status); }
        @Override public boolean flag(ConditionType type, EffectTarget target, Map<String, String> arguments) { return delegate.flag(type, target, arguments); }
        @Override public boolean coinResult(String expected) { return delegate.coinResult(expected); }
        @Override public boolean customCondition(ConditionDefinition condition) { return delegate.customCondition(condition); }
    }
}
