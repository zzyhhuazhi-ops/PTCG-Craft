package dev.hua.ptcglive.engine.effect;

import java.util.*;

/** Server-owned execution cursor. A suspension retains both its remaining steps and enclosing coin outcomes. */
public final class EffectContinuation {
    public record Choice(long step, EffectDefinition definition) {
        /** An optional choice (“you may …”) has one extra, last option that resolves nothing. */
        public boolean optional() { return EffectSchemaValidator.optionalChoice(definition); }
        public int optionCount() { return definition.effects().size() + (optional() ? 1 : 0); }
    }
    public record CoinChoice(long step, AttackCoinSequence.Prompt result) { }
    public record CoinOutcomeChoice(long step) { }
    private CoinOutcomeChoice coinOutcomeChoice;
    private Step coinOutcomeStep;
    public CoinOutcomeChoice pendingCoinOutcomeChoice(){return coinOutcomeChoice;}
    private AttackCoinSequence coinSequence;
    private Step coinStep;
    private CoinChoice coinChoice;
    public CoinChoice pendingCoinChoice(){return coinChoice;}
    public record CardSelection(long step, EffectDefinition definition) { }
    public record Interaction(long step, EffectExecutionContext.InteractionKind kind, EffectDefinition definition) { }
    private record Step(EffectDefinition effect, EffectExecutionContext.CoinResult coin, EffectExecutionContext context) { }
    private final EffectExecutionContext context;
    private final TriggeredEffects binding;
    private final Deque<Step> remaining = new ArrayDeque<>();
    private Choice choice;
    private Interaction interaction;
    private EffectExecutionContext.CoinResult choiceCoin;
    private EffectExecutionContext choiceContext, interactionContext;
    private EffectExecutionContext.CoinResult interactionCoin;
    private long choiceSequence;
    private int budget = 4096;
    private boolean started, activated, complete, running, failed;

    public EffectContinuation(TriggeredEffects binding, EffectExecutionContext context) {
        this.binding = Objects.requireNonNull(binding); this.context = Objects.requireNonNull(context);
        CoinFlipPolicy.validatePerHeadsScope(binding.effects());
    }
    public boolean complete() { return complete; }
    public boolean activated() { return activated; }
    public Choice pendingChoice() { return choice; }
    public Interaction pendingInteraction() { return interaction; }
    public CardSelection pendingCardSelection() { return interaction != null && interaction.kind() == EffectExecutionContext.InteractionKind.CARD_SELECTION
        ? new CardSelection(interaction.step(), interaction.definition()) : null; }
    /**
     * Internal server bridge for a pending card choice. The context retains a
     * previously selected Pokémon incarnation, if any; clients only receive
     * the separately projected prompt.
     */
    public EffectExecutionContext pendingCardSelectionContext() {
        if (pendingCardSelection() == null || interactionContext == null)
            throw new IllegalStateException("当前没有服务器选牌上下文");
        return interactionContext;
    }
    /** The server rechecks the selected option before consuming its choice prompt. */
    public EffectExecutionContext pendingChoiceContext() {
        if (choice == null || choiceContext == null)
            throw new IllegalStateException("当前没有服务器效果选择上下文");
        return choiceContext;
    }
    public EffectExecutionContext.CoinResult pendingChoiceCoinResult() {
        if (choice == null) throw new IllegalStateException("当前没有服务器效果选择");
        return choiceCoin;
    }
    /** Server-only scope of a typed interaction, preserving the attack's actual actor and count memory. */
    public EffectExecutionContext pendingInteractionContext(EffectExecutionContext.InteractionKind kind) {
        if(interaction==null||interaction.kind()!=kind||interactionContext==null)
            throw new IllegalStateException("当前没有指定类型的服务器交互上下文");
        return interactionContext;
    }
    public void advance() {
        if (failed || running) throw new IllegalStateException("效果续接已失败或正在执行");
        if (complete || choice != null || interaction != null || coinChoice != null || coinOutcomeChoice != null || context.hasPendingRuleChoice()) return;
        running = true;
        try {
            if (!started) {
                started = true;
                activated = binding.conditions().stream().allMatch(c -> ConditionEvaluator.matches(c, context));
                if (activated) push(binding.effects(), null, context);
            }
            if(coinSequence!=null&&!advanceCoinSequence())return;
            while (!remaining.isEmpty()) {
                if (--budget < 0) throw new IllegalStateException("效果续接超出安全步数");
                var step = remaining.pop(); var effect = step.effect();
                var scope=step.context();
                var conditions = new CoinContext(scope, step.coin());
                if (!effect.conditions().stream().allMatch(c -> ConditionEvaluator.matches(c, conditions, scope::conditionAvailable))) continue;
                switch (effect.type()) {
                    case "draw" -> {
                        if(DrawCount.upTo(effect)) {
                            int maximum=Math.min(DrawCount.from(effect).resolve(scope),scope.number(ConditionType.DECK_SIZE,effect.target(),Map.of()));
                            if(maximum<=0)continue;
                            var options=new ArrayList<EffectDefinition>();
                            for(int n=1;n<=maximum;n++)options.add(new EffectDefinition("draw",effect.target(),Map.of("amount",Integer.toString(n)),List.of(),List.of()));
                            var args=effect.target()==EffectTarget.OPPONENT_ACTIVE?Map.of("optional","true","chooser","OPPONENT"):Map.of("optional","true");
                            choice=new Choice(++choiceSequence,new EffectDefinition("choice",EffectTarget.SELF,args,List.of(),options));
                            choiceCoin=step.coin();choiceContext=scope;return;
                        }
                        scope.executeAtomic(effect);
                        if(scope.hasPendingRuleChoice())return;
                    }
                    case "turn_prize_face_up" -> {
                        if("true".equals(effect.arguments().get("all"))){scope.executeAtomic(effect);continue;}
                        if(effect.arguments().containsKey("position")){scope.executeAtomic(effect);continue;}
                        var options=new ArrayList<EffectDefinition>();
                        for(int slot:scope.faceDownPrizePositions()) {
                            var args=new LinkedHashMap<>(effect.arguments());args.put("position",Integer.toString(slot));
                            options.add(new EffectDefinition(effect.type(),effect.target(),args,List.of(),List.of()));
                        }
                        if(options.isEmpty()){scope.rememberCount(effect.arguments().get("bind"),0);continue;}
                        var selection=new EffectDefinition("choice",EffectTarget.SELF,Map.of("optional","true"),List.of(),options);
                        choice=new Choice(++choiceSequence,selection);choiceCoin=step.coin();choiceContext=scope;return;
                    }
                    case "move_damage_counter" -> {
                        var move=MoveDamageCounterEffect.from(effect);
                        if(move.upTo()) {
                            var counterSource=move.sourceRef()==null?scope:scope.withNamedPokemon(move.sourceRef());
                            if(counterSource==null||move.sourceRef()!=null&&scope.namedPokemonTarget(move.sourceRef())==null)continue;
                            int limit=Math.min(move.counters(),counterSource.number(ConditionType.DAMAGE_COUNTER_COUNT,
                                move.sourceRef()==null?effect.target():EffectTarget.CHOSEN_POKEMON,Map.of()));
                            if(limit<=0)continue;
                            var options=new ArrayList<EffectDefinition>();
                            for(int n=1;n<=limit;n++) {
                                var args=new LinkedHashMap<>(effect.arguments());args.remove("up_to");args.put("amount",Integer.toString(n));
                                options.add(new EffectDefinition(effect.type(),effect.target(),args,List.of(),List.of()));
                            }
                            boolean optional=move.sourceRef()!=null&&!MoveDamageCounterEffect.opponentTransfer(effect);
                            if(limit==1&&!optional){push(options,step.coin(),scope);continue;}
                            var selection=new EffectDefinition("choice",EffectTarget.SELF,optional?Map.of("optional","true","require_usable_option","true"):Map.of(),List.of(),options);
                            choice=new Choice(++choiceSequence,selection);choiceCoin=step.coin();choiceContext=scope;return;
                        }
                        scope.executeAtomic(effect);
                        if(scope.hasPendingRuleChoice())return;
                    }
                    case "with_pokemon" -> {
                        if(!scope.supportsPokemonBindings())throw new IllegalStateException("此入口没有命名宝可梦上下文");
                        var named=scope.withNamedPokemon(effect.arguments().get("ref"));
                        if(named!=null)push(effect.effects(),step.coin(),named);
                    }
                    case "conditional" -> push(effect.effects(), step.coin(), scope);
                    case "repeat" -> {
                        int count = amount(effect, "count", 60);
                        for (int i = 0; i < count; i++) push(effect.effects(), step.coin(), scope);
                    }
                    case "repeat_per_heads" -> {
                        if (step.coin() == null) throw new IllegalStateException("repeat_per_heads 缺少同一硬币步骤的结果");
                        for (int i = 0; i < step.coin().heads(); i++) push(effect.effects(), step.coin(), scope);
                    }
                    case "coin_flip" -> {
                        String name=effect.arguments().get("bind");
                        if(name!=null&&(!scope.supportsCoinBindings()||scope.namedCoinResult(AttackCoinBindings.name(name))!=null))
                            throw new IllegalStateException("硬币绑定入口无效或结果已生成，未重新掷币");
                        var prepared=scope.preparedAttackCoin(effect);
                        if(prepared!=null) {
                            if(name!=null)scope.rememberCoinResult(name,prepared);
                            scope.coinResultCommitted(effect,prepared);
                            push(effect.effects(),prepared,scope);continue;
                        }
                        var coinPolicy=CoinFlipPolicy.from(effect);
                        if((coinPolicy.untilTails()||coinPolicy.resolvedFlips(scope)>0)&&scope.canChooseNextCoinOutcome(effect)) {
                            coinOutcomeStep=step;coinOutcomeChoice=new CoinOutcomeChoice(++choiceSequence);return;
                        }
                        if(scope.attackCoinRetryAvailable(effect)) {
                            coinSequence=new AttackCoinSequence(effect,scope);coinStep=step;
                            if(!advanceCoinSequence())return;
                            continue;
                        }
                        var result=CoinFlipPolicy.from(effect).flip(scope,effect);
                        if(name!=null)scope.rememberCoinResult(name,result);
                        scope.coinResultCommitted(effect,result);
                        push(effect.effects(),result,scope);
                    }
                    case "choice" -> {
                        int least = EffectSchemaValidator.optionalChoice(effect) ? 1 : 2;
                        if (effect.effects().size() < least || effect.effects().size() > 60)
                            throw new IllegalArgumentException("效果选项数量必须在 " + least + " 至 60 之间");
                        choice = new Choice(++choiceSequence, effect); choiceCoin = step.coin();
                        choiceContext=scope;
                        return;
                    }
                    default -> {
                        if(scope.canChooseAtomicCoinOutcome(effect)) {
                            coinOutcomeStep=step;coinOutcomeChoice=new CoinOutcomeChoice(++choiceSequence);return;
                        }
                        var kind = scope.interactionKind(effect);
                        if(PokemonSelectionScope.handles(effect) && kind!=EffectExecutionContext.InteractionKind.POKEMON_SELECTION)
                            throw new IllegalStateException("select_pokemon必须由服务器宝可梦选择续接控制器执行");
                        if (kind != null) {
                            interaction = new Interaction(++choiceSequence, kind, effect);
                            interactionContext=scope;interactionCoin=step.coin();
                            return;
                        }
                        scope.executeAtomic(effect);
                        if(scope.hasPendingRuleChoice())return;
                    }
                }
            }
            complete = true;
        } catch (RuntimeException failure) {
            failed = true; throw failure; // A committed prefix must never be silently replayed after an internal error.
        } finally { running = false; }
    }
    private boolean advanceCoinSequence() {
        coinSequence.advance(coinStep.context().attackCoinRetryAvailable(coinStep.effect()));
        if(coinSequence.prompt()!=null) {
            coinChoice=new CoinChoice(++choiceSequence,coinSequence.prompt());return false;
        }
        var result=coinSequence.result();var effect=coinStep.effect();var scope=coinStep.context();
        String name=effect.arguments().get("bind");
        if(name!=null)scope.rememberCoinResult(name,result);
        scope.coinResultCommitted(effect,result);
        push(effect.effects(),result,scope);
        coinSequence=null;coinStep=null;return true;
    }
    /** No branch or named coin binding is committed before this issued choice is confirmed. */
    public void decideCoinOutcome(long step,boolean heads) {
        if(failed||running||complete||coinOutcomeChoice==null||coinOutcomeStep==null||coinOutcomeChoice.step()!=step)
            throw new IllegalStateException("硬币结果选择已完成或已更新");
        var pending=coinOutcomeStep;
        if(!pending.context().canChooseNextCoinOutcome(pending.effect()))
            throw new IllegalStateException("选择硬币结果的来源已不可用");
        pending.context().chooseNextCoinOutcome(pending.effect(),heads);
        remaining.push(pending);coinOutcomeStep=null;coinOutcomeChoice=null;
    }
    /** No branch or named coin binding is committed before this issued choice is confirmed. */
    public void decideCoin(long step,AttackCoinSequence.Decision decision) {
        if(failed||running||complete||coinChoice==null||coinSequence==null||coinChoice.step()!=step)
            throw new IllegalStateException("硬币选择已完成或已更新");
        Objects.requireNonNull(decision);
        if(decision==AttackCoinSequence.Decision.FINISH_SEQUENCE&&coinChoice.result().sequenceFinished())
            throw new IllegalArgumentException("本段硬币已投完");
        if(decision==AttackCoinSequence.Decision.RETRY) {
            if(!coinStep.context().attackCoinRetryAvailable(coinStep.effect()))
                throw new IllegalStateException("攻击重投来源已不可用");
            coinStep.context().consumeAttackCoinRetry(coinStep.effect());
        }
        coinSequence.decide(coinChoice.result().revision(),decision);coinChoice=null;
    }
    /** Called only after the Battle Engine has committed the validated zone transfer. */
    public void completeCardSelection(long step) {
        completeInteraction(step, EffectExecutionContext.InteractionKind.CARD_SELECTION);
    }
    /** Called only after the Battle Engine has committed the exact issued interaction. */
    public void completeInteraction(long step, EffectExecutionContext.InteractionKind kind) {
        if (failed || running || complete || interaction == null || interaction.step() != step || interaction.kind() != kind)
            throw new IllegalStateException("交互步骤已完成、类型不符或已更新");
        interaction = null;
        interactionContext=null;interactionCoin=null;
    }
    /** Adds an audited server-resolved card program after its typed interaction has committed. */
    public void inject(TriggeredEffects injected,EffectExecutionContext scope) {
        if(failed||running||complete||interaction!=null||coinOutcomeChoice!=null)throw new IllegalStateException("不能在当前续接状态注入卡牌程序");
        Objects.requireNonNull(injected);Objects.requireNonNull(scope);
        if(injected.conditions().isEmpty())push(injected.effects(),null,scope);
        else remaining.push(new Step(new EffectDefinition("conditional",EffectTarget.SELF,Map.of(),injected.conditions(),injected.effects()),null,scope));
    }
    /** Validates before consuming a prompt. Resuming execution is a separate server-controlled operation. */
    public void resolvePokemonSelection(long step,EffectTargetResolution.PokemonTarget target) {
        resolvePokemonSelections(step,java.util.List.of(Objects.requireNonNull(target,"已核验目标")));
    }
    /** One step that chooses several different Pokémon (bench_damage target_count) resolves them together. */
    public void resolvePokemonSelections(long step,java.util.List<EffectTargetResolution.PokemonTarget> targets) {
        if(failed||running||complete||interaction==null||interaction.step()!=step
            || interaction.kind()!=EffectExecutionContext.InteractionKind.POKEMON_SELECTION)
            throw new IllegalStateException("宝可梦选择步骤已完成、类型不符或已更新");
        targets=java.util.List.copyOf(targets);
        if(targets.isEmpty())throw new IllegalArgumentException("已核验目标不能为空");
        running=true;
        try {
            if(PokemonSelectionScope.handles(interaction.definition())) {
                int count=PokemonEffectSelection.targetCount(interaction.definition());
                // The server selection contract requires all available targets when fewer than printed exist.
                if(targets.size()>count)throw new IllegalStateException("select_pokemon 选择数量与卡文不符");
                var target=targets.getFirst();
                if(interaction.definition().arguments().containsKey("bind")) {
                    if(!interactionContext.supportsPokemonBindings())throw new IllegalStateException("此入口没有命名宝可梦上下文");
                    interactionContext.rememberPokemon(interaction.definition().arguments().get("bind"),target);
                }
                for(int i=targets.size()-1;i>=0;i--)
                    push(interaction.definition().effects(),interactionCoin,interactionContext.withChosenPokemon(targets.get(i)));
            }
            else interactionContext.executeSelectedPokemons(interaction.definition(),targets);
            interaction=null;interactionContext=null;interactionCoin=null;
        }
        catch(RuntimeException failure) {failed=true;throw failure;}
        finally {running=false;}
    }
    /** Validates before consuming a prompt. Resuming execution is a separate server-controlled operation. */
    public void select(long step, int option) {
        if (failed || running || complete || choice == null || choice.step() != step)
            throw new IllegalStateException("效果选择步骤已完成或已更新");
        if (option < 0 || option >= choice.optionCount()) throw new IllegalArgumentException("无效的效果选项");
        // The extra last option of an optional choice declines: nothing is pushed.
        if (option < choice.definition().effects().size())
            remaining.push(new Step(choice.definition().effects().get(option), choiceCoin, choiceContext));
        choice = null; choiceCoin = null;choiceContext=null;
    }
    private void push(List<EffectDefinition> effects, EffectExecutionContext.CoinResult coin, EffectExecutionContext scope) {
        for (int i = effects.size() - 1; i >= 0; i--) remaining.push(new Step(effects.get(i), coin, scope));
    }
    private static int amount(EffectDefinition effect, String key, int max) {
        try {
            int amount = Integer.parseInt(effect.arguments().get(key));
            if (amount < 1 || amount > max) throw new IllegalArgumentException("效果参数超出范围：" + key);
            return amount;
        } catch (NumberFormatException invalid) { throw new IllegalArgumentException("效果参数必须为整数：" + key, invalid); }
    }
    private record CoinContext(EffectConditionContext delegate, EffectExecutionContext.CoinResult coin) implements EffectConditionContext {
        public boolean hasExtraAttackEnergy(ExtraAttackEnergyRequirement requirement) { return delegate.hasExtraAttackEnergy(requirement); }
        public int number(ConditionType type, EffectTarget target, Map<String,String> args) { return delegate.number(type, target, args); }
        public int opponentNumber(ConditionType type, Map<String,String> args) { return delegate.opponentNumber(type, args); }
        public boolean hasStatus(EffectTarget target, String status) { return delegate.hasStatus(target, status); }
        public boolean flag(ConditionType type, EffectTarget target, Map<String,String> args) { return delegate.flag(type, target, args); }
        public boolean coinResult(String expected) { return coin == null ? delegate.coinResult(expected) : coin.matches(expected); }
        public EffectExecutionContext.CoinResult namedCoinResult(String name) { return delegate.namedCoinResult(name); }
        public int boundCount(String name) { return delegate.boundCount(name); }
        public boolean customCondition(ConditionDefinition condition) { return delegate.customCondition(condition); }
    }
}
