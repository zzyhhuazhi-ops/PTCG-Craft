package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.field.AbilityDefinition;
import java.util.*;

/** Shared deck/runtime support gate for automatic event bindings. No unsupported component may half-resolve. */
public final class TriggeredAbilityRules {
    private TriggeredAbilityRules() { }
    public static Optional<String> unsupportedReason(AbilityDefinition ability) {
        try { validate(ability); return Optional.empty(); }
        catch (IllegalArgumentException | IllegalStateException invalid) { return Optional.of(invalid.getMessage()); }
    }
    public static void validate(AbilityDefinition ability) {
        if (ability.effectSupport() != dev.hua.ptcglive.rules.EffectSupport.SUPPORTED) throw new IllegalArgumentException("此自动特性尚未审核支持");
        if (!ability.triggered() || !ability.composed() || ability.eventPolicy() == null)
            throw new IllegalArgumentException("自动特性须使用明确的事件策略和组合程序");
        if(ability.eventPolicy() instanceof TurnEndTriggerPolicy end&&end.sourceAttachedThisTurn())
            throw new IllegalArgumentException("宝可梦特性不能使用来源能量的附着回合");
        validateProgram(ability.effectProgram(),ability.eventPolicy(),ability.usageLimit());
        if(ability.endsTurn())validateTurnEndingEntry(ability);
        if(ability.effectProgram().triggers().stream().flatMap(t->t.effects().stream()).anyMatch(TriggeredAbilityRules::containsSourceDiscard))
            throw new IllegalArgumentException("来源卡自弃仅限持续卡自动效果，不能把特性宿主宝可梦当作附着卡弃置");
    }
    private static void validateTurnEndingEntry(AbilityDefinition ability) {
        var entry=ability.effectProgram().triggers().getFirst();
        if(!ability.eventPolicy().equals(new PokemonChangeTriggerPolicy(EffectTrigger.ON_ENTER_PLAY,EventPokemonScope.SELF,
                true,EnergyTriggerPolicy.Usage.NONE,PokemonChangeTriggerPolicy.Source.HAND))
            ||ability.usageLimit()!=null||!entry.conditions().equals(List.of(new ConditionDefinition(ConditionType.TURN_STATE,
                Map.of("target","SELF","value","ACTOR_TURN"),List.of())))
            ||entry.effects().size()!=1)
            throw new IllegalArgumentException("结束回合的入场特性须为自身回合从手牌登场、可选的巅峰之门");
        var effect=entry.effects().getFirst();
        if(!ZoneSelectionEffect.climacticGatePlacement(effect)||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
            throw new IllegalArgumentException("巅峰之门须从牌库将最多2只VMAX直接放置备战区");
    }
    public static void validateProgram(EffectProgram program,AbilityEventPolicy eventPolicy,SharedUsageLimit usageLimit) {
        var trigger = eventPolicy.trigger();
        if(eventPolicy instanceof BenchToActiveTriggerPolicy) { BenchToActiveTriggerPolicy.validateProgram(program,usageLimit); return; }
        if(eventPolicy instanceof DeckDiscardTriggerPolicy) { DeckDiscardTriggerPolicy.validateProgram(program,usageLimit); return; }
        if(eventPolicy instanceof HandDiscardTriggerPolicy) { HandDiscardTriggerPolicy.validateProgram(program,usageLimit); return; }
        if(eventPolicy instanceof PrizeTakenTriggerPolicy) {
            if(usageLimit!=null||program.triggers().size()!=1||program.triggers().getFirst().trigger()!=EffectTrigger.ON_PRIZE_TAKEN
                ||program.triggers().getFirst().effects().size()!=1)throw new IllegalArgumentException("奖赏取出特性须为单一不限次事件程序");
            var effect=program.triggers().getFirst().effects().getFirst();
            if(!effect.type().equals("prize_pokemon_to_bench_coin_extra_prize"))throw new IllegalArgumentException("奖赏取出事件只支持幸运奖励");
            EffectSchemaValidator.validate(effect);return;
        }
        if(program.triggers().stream().flatMap(t->t.effects().stream()).anyMatch(ActivationCostRules::contains))
            throw new IllegalArgumentException("自动触发尚未接入主动发动成本");
        if (eventPolicy instanceof KnockoutTriggerPolicy policy && policy.cause()==KnockoutTriggerPolicy.Cause.ANY
            && (policy.usage()==EnergyTriggerPolicy.Usage.ONCE_PER_TURN || usageLimit!=null && usageLimit.period()==SharedUsageLimit.Period.TURN))
            throw new IllegalArgumentException("不限原因的击倒包含回合间；不能使用未经定义的玩家回合次数");
        if (program.triggers().size() != 1 || program.triggers().getFirst().trigger() != trigger)
            throw new IllegalArgumentException("自动特性的程序入口必须与事件策略一致且只有一个");
        if(eventPolicy instanceof DamageTriggerPolicy damage && damage.watch()==EventPokemonScope.OWN_FIELD && damage.pokemonNames().isEmpty()) {
            var entry=program.triggers().getFirst();
            if(entry.conditions().size()!=1 || entry.conditions().getFirst().type()!=ConditionType.IS_TYPE
                || !entry.conditions().getFirst().arguments().equals(Map.of("target","TRIGGER_POKEMON","pokemon_type","DARKNESS"))
                || entry.effects().size()!=1 || !entry.effects().getFirst().type().equals("damage_counter")
                || entry.effects().getFirst().target()!=EffectTarget.ATTACKING_POKEMON
                || !entry.effects().getFirst().arguments().equals(Map.of("amount","1")))
                throw new IllegalArgumentException("无名称受伤监听仅支持恶属性战斗宝可梦受到招式伤害后反击1个指示物");
        }
        if (program.triggers().getFirst().effects().stream().anyMatch(e -> e.type().equals("prevent_prize_take"))) {
            if (!(eventPolicy instanceof KnockoutTriggerPolicy ko)
                || ko.cause()!=KnockoutTriggerPolicy.Cause.OPPONENT_ATTACK_DAMAGE || ko.watch()!=EventPokemonScope.SELF
                || ko.activeOnly() || ko.optional() || ko.usage()!=EnergyTriggerPolicy.Usage.NONE || usageLimit!=null
                || !program.triggers().getFirst().conditions().isEmpty() || program.triggers().getFirst().effects().size()!=1)
                throw new IllegalArgumentException("零奖赏特性须为自身受到对手攻击伤害击倒时的唯一强制效果");
        }
        if (program.triggers().getFirst().effects().stream().anyMatch(e -> e.type().equals("knockout_top_to_hand")))
            KnockoutTopToHand.validateProgram(program,eventPolicy,usageLimit);
        if (eventPolicy instanceof PokemonChangeTriggerPolicy change
            && change.trigger() == EffectTrigger.ON_LEAVE_PLAY && change.watch() == EventPokemonScope.SELF)
            validateDepartedSourceProgram(program);
        // A named selection is only meaningful when its later reference is
        // proven to stay on the same resumable automatic-program path.  The
        // same data-flow verifier is shared with attacks and manual Effects;
        // it does not trust a client-supplied Pokémon identity.
        boolean namedPokemon = AttackPokemonBindings.usesNamedBindings(program);
        if (namedPokemon) {
            AttackPokemonBindings.validate(program, trigger);
            validateAutomaticSelectionBindings(program);
        }
        if (trigger == EffectTrigger.ON_POKEMON_CHECKUP && usageLimit!=null
            && usageLimit.period()==SharedUsageLimit.Period.TURN)
            throw new IllegalArgumentException("回合间不能使用玩家回合共享次数");
        if ((trigger == EffectTrigger.ON_POKEMON_CHECKUP || trigger == EffectTrigger.ON_DAMAGE || trigger == EffectTrigger.ON_KNOCKOUT
            || trigger == EffectTrigger.ON_TURN_START || trigger == EffectTrigger.ON_TURN_END || trigger == EffectTrigger.ON_ENTER_PLAY
            || trigger == EffectTrigger.ON_EVOLVE || trigger == EffectTrigger.ON_RETREAT || trigger == EffectTrigger.ON_SWITCH || trigger == EffectTrigger.ON_ACTIVE_TO_BENCH
            || trigger == EffectTrigger.ON_LEAVE_PLAY || trigger == EffectTrigger.ON_CARD_PLAYED) && eventPolicy.usage()==EnergyTriggerPolicy.Usage.ONCE_PER_GAME
            && (usageLimit==null || usageLimit.period()!=SharedUsageLimit.Period.GAME))
            throw new IllegalArgumentException("此事件的整局一次必须声明GAME共享组，不能依赖在场实例刷新次数");
        boolean namedCounts=AttackCountBindings.usesCounts(program);
        AttackCountBindings.validateAutomatic(program,trigger);
        for (var effect : program.triggers().getFirst().effects()) validateDeferredConditions(effect);
        EffectResolutionPreflight.validate(program, trigger, condition -> {
            if(condition.type()==ConditionType.BOUND_COUNT) {
                TrainerProgramRules.condition(condition);
                return;
            }
            // Shared scalar/enum validation; SELF means this public Ability source, unlike Trainer SELF.
            var args = new LinkedHashMap<>(condition.arguments());
            var conditionTarget=EffectTarget.fromWire(args.getOrDefault("target","SELF"));
            validateEventTarget(conditionTarget,eventPolicy);
            if (conditionTarget==EffectTarget.SELF || conditionTarget==EffectTarget.CHOSEN_POKEMON || conditionTarget.eventBound()) args.put("target", "OWN_ACTIVE");
            TrainerProgramRules.condition(new ConditionDefinition(condition.type(), args, condition.children()));
            if (condition.arguments().getOrDefault("target", "SELF").startsWith("SELECT_"))
                throw new IllegalArgumentException("自动特性的条件在选择目标前判定，尚不支持引用尚未选择的宝可梦");
        }, effect -> {
            if(MoveDamageCounterEffect.handles(effect)&&MoveDamageCounterEffect.from(effect).all())throw new IllegalArgumentException("全量转移仅核验攻击后续接");
            if(effect.type().equals("survive_attack_damage")) {
                SurviveAttackDamage.validate(effect);
                if(!(eventPolicy instanceof DamageTriggerPolicy damage)||!damage.lethalOnly())throw new IllegalArgumentException("生存替代仅供致命招式伤害事件");
                return;
            }
            if(effect.type().equals("damage_counter")&&effect.arguments().containsKey("counter_multiplier"))throw new IllegalArgumentException("倍增指示物仅审核攻击后续接");
            if(effect.type().equals("prevent_prize_take")) { EffectSchemaValidator.validate(effect); return; }
            if(effect.type().equals(KnockoutCardsToHand.TYPE)) {KnockoutCardsToHand.validateProgram(program,eventPolicy,usageLimit);return;}
            if(effect.type().equals("knockout_top_to_hand")) {
                KnockoutTopToHand.validateProgram(program,eventPolicy,usageLimit);
                return;
            }
            if(effect.type().equals("damage_counter")&&(effect.arguments().containsKey("per_source_energy_type")||effect.arguments().containsKey("per_own_pokemon_names"))
                &&(!(eventPolicy instanceof DamageTriggerPolicy damage)||damage.watch()!=EventPokemonScope.SELF))
                throw new IllegalArgumentException("按来源能量反击须由自身ON_DAMAGE事件触发");
            if(effect.type().equals("damage_counter")&&!DamageCounterDistribution.handles(effect)&&DamageCounterEffect.from(effect).fromTriggerDamage()&&!(eventPolicy instanceof DamageTriggerPolicy))throw new IllegalArgumentException("伤害事件数量只能由ON_DAMAGE读取");
            if(effect.type().equals("discard_source")) {EffectSchemaValidator.validate(effect);return;}
            if(effect.type().equals("opponent_hand_to_deck")) {OpponentHandShuffle.validate(effect);return;}
            if(effect.type().equals("opponent_choose_discard")) {OpponentHandDiscard.amount(effect);return;}
            if(Set.of("reveal_hand","opponent_hand_basic_to_bench").contains(effect.type())) {
                if(!(eventPolicy instanceof PokemonChangeTriggerPolicy change)
                    ||change.trigger()!=EffectTrigger.ON_EVOLVE||change.watch()!=EventPokemonScope.SELF
                    ||change.source()!=PokemonChangeTriggerPolicy.Source.HAND)
                    throw new IllegalArgumentException("自动公开手牌并放置基础宝可梦仅核验自身从手牌进化时的特性");
                EffectSchemaValidator.validate(effect);
                return;
            }
            if(effect.type().equals("discard_top_deck")) {
                // This immediate zone action uses the captured controller; no Pokémon choice or departed host is needed.
                TopDeckDiscardEffect.from(effect);
                if(effect.arguments().containsKey("bind"))throw new IllegalArgumentException("自动弃置牌库顶尚不支持跨步骤计数绑定");
                return;
            }
            if(Set.of("shuffle_random_hand","discard_random_hand","discard_stadium","draw_until","discard_hand").contains(effect.type())) {
                // Immediate player/field actions have no deferred target or event binding.
                EffectSchemaValidator.validate(effect);
                return;
            }
            if(Set.of("return_to_hand","shuffle_into_deck","discard_pokemon").contains(effect.type()))
                throw new IllegalArgumentException("自动触发的离场替代与最后已知状态尚未接入；不能将主动离场续接当作击倒替代");
            if(PokemonDeckReplacement.handles(effect)) {
                if(!(eventPolicy instanceof PokemonChangeTriggerPolicy change)||change.trigger()!=EffectTrigger.ON_ACTIVE_TO_BENCH
                    ||change.watch()!=EventPokemonScope.SELF||!change.optional()||change.usage()!=EnergyTriggerPolicy.Usage.ONCE_PER_TURN
                    ||!PokemonDeckReplacement.activeToBench(effect))throw new IllegalArgumentException("自动牌库替换仅核验自身离开战斗位后的全能变身");
                PokemonDeckReplacement.validate(effect);return;
            }
            if(ZoneSelectionEffect.handles(effect)) { ZoneSelectionEffect.forProgram(effect); return; }
            // Public Tools use the same resumable, server-owned attachment
            // selection as manual programs. No departed/event host is inferred.
            if(AttachedToolEffect.handles(effect)) { AttachedToolEffect.from(effect); return; }
            if(DamageCounterDistribution.handles(effect)) { DamageCounterDistribution.from(effect); return; }
            if(PokemonEffectSelection.handles(effect) || effect.target()==EffectTarget.CHOSEN_POKEMON) {
                if(effect.target()==EffectTarget.CHOSEN_POKEMON)PokemonSelectionScope.validateBoundEffect(effect);
                else PokemonEffectSelection.validate(effect);
                if(effect.type().equals("knock_out")&&trigger!=EffectTrigger.ON_KNOCKOUT)
                    throw new IllegalArgumentException("此自动事件尚未接入直接击倒组件");
                return;
            }
            if (ProtectionEffect.handles(effect)) {
                ProtectionEffect.from(effect);validateEventTarget(effect.target(),eventPolicy);fixedPokemonTarget(effect.target());return;
            }
            if (effect.type().equals("clear_status")) {
                ClearSpecialConditionsEffect.from(effect);
                // Automatic programs have no implicit source Pokémon target:
                // choose an actual current Active instead of accidentally
                // curing a Benched source or a departed event object.
                if (!Set.of(EffectTarget.OWN_ACTIVE, EffectTarget.OPPONENT_ACTIVE).contains(effect.target())
                    && !(effect.target()==EffectTarget.SELF&&(eventPolicy instanceof TurnEndTriggerPolicy
                        ||eventPolicy instanceof EnergyTriggerPolicy energy&&energy.target()==EnergyTriggerPolicy.Target.SELF)))
                    throw new IllegalArgumentException("自动解除异常状态必须明确指定己方或对方当前战斗位");
                return;
            }
            if (AttachedEnergyEffect.handles(effect)) {
                AttachedEnergyEffect.from(effect);
                validateEventTarget(effect.target(),eventPolicy);
                // Automatic reactions retain their owner and captured event identities while waiting for a choice.
                fixedPokemonTarget(effect.target());
                return;
            }
            if (!Set.of("draw", "heal", "damage_counter", "poison", "burn", "paralyze", "sleep", "confuse", "devolve", "extra_prize_for_trigger_ko", "prevent_prize_take",
                "prevent_evolution", "prevent_attack", "prevent_retreat", "disable_abilities", "disable_tools", "switch_source_with_active").contains(effect.type())
                && !(trigger==EffectTrigger.ON_KNOCKOUT && effect.type().equals("knock_out")))
                throw new IllegalArgumentException("自动特性尚未接入该组件的等待或时序：" + effect.type());
            var target = effect.target();
            validateEventTarget(target,eventPolicy);
            if (target == EffectTarget.SELECT_OWN_POKEMON || target == EffectTarget.SELECT_OPPONENT_POKEMON)
                throw new IllegalArgumentException("自动特性目标选择尚未接入");
            if (effect.type().equals("draw")) {
                int amount = Integer.parseInt(effect.arguments().get("amount"));
                if (amount > 60 || !target.eventBound() && !Set.of(EffectTarget.SELF, EffectTarget.OWN_ACTIVE, EffectTarget.OPPONENT_ACTIVE, EffectTarget.HAND, EffectTarget.DECK).contains(target))
                    throw new IllegalArgumentException("自动特性抽牌数量或目标不支持");
            } else if (effect.type().equals("heal")) {
                HealingEffect.from(effect);
                if(HealingEffect.discardSourceAfterHealing(effect)
                    &&(!(eventPolicy instanceof TurnEndTriggerPolicy end)||end.turn()!=TurnEndTriggerPolicy.Turn.ANY
                        ||end.optional()||end.usage()!=EnergyTriggerPolicy.Usage.NONE))
                    throw new IllegalArgumentException("回复后自弃的工具效果须在双方每个回合结束时强制触发");
                String mode=effect.arguments().getOrDefault("mode","DAMAGE");
                if(mode.equals("ALL")) {
                    boolean sleep=eventPolicy instanceof CheckupTriggerPolicy checkup&&checkup.afterFailedSleepRecovery()&&target==EffectTarget.SELF;
                    boolean evolved=eventPolicy instanceof PokemonChangeTriggerPolicy change&&change.trigger()==EffectTrigger.ON_EVOLVE
                        &&change.watch()==EventPokemonScope.SELF&&change.source()==PokemonChangeTriggerPolicy.Source.HAND
                        &&change.optional()&&HealingEffect.discardEnergyAfterHealing(effect)
                        &&(target==EffectTarget.OWN_ACTIVE&&effect.arguments().keySet().equals(Set.of("mode","pokemon_types","discard_energy_if_healed"))
                            ||target==EffectTarget.ALL_OWN_POKEMON&&effect.arguments().keySet().equals(Set.of("mode","pokemon_stage","discard_energy_if_healed"))
                                &&HealingEffect.stage(effect)==PokemonSelectionScope.Stage.EVOLUTION);
                    if(!sleep&&!evolved)throw new IllegalArgumentException("自动全回复只支持睡眠恢复或从手牌自身进化后的回复弃能");
                } else if(!Set.of("DAMAGE","MINIMUM_HP").contains(mode)||Integer.parseInt(effect.arguments().get("amount"))%10!=0)
                    throw new IllegalArgumentException("自动回复量必须为固定10的倍数");
                fixedPokemonTarget(target);
            } else if (effect.type().equals("damage_counter")) {
                if(DamageCounterEffect.from(effect).extraTurnOnKo())throw new IllegalArgumentException("特性不能使用攻击额外回合组件");
                fixedPokemonTarget(target);
            } else if (Set.of("devolve", "prevent_evolution", "prevent_attack", "prevent_retreat", "disable_abilities", "disable_tools", "switch_source_with_active").contains(effect.type())) {
                fixedPokemonTarget(target);
            } else if(effect.type().equals("extra_prize_for_trigger_ko")) {
                if(!(eventPolicy instanceof KnockoutTriggerPolicy)||target!=EffectTarget.TRIGGER_POKEMON)
                    throw new IllegalArgumentException("额外奖赏须读取击倒事件目标");
            } else if (!target.eventBound() && !Set.of(EffectTarget.SELF, EffectTarget.OWN_ACTIVE, EffectTarget.OPPONENT_ACTIVE).contains(target))
                throw new IllegalArgumentException("自动特性此组件需一个固定公开目标");
            if (effect.type().equals("damage_counter") && DamageCounterEffect.from(effect).amount() > 60)
                throw new IllegalArgumentException("伤害指示物不能超过 60 个");
            if (Set.of("poison", "burn", "paralyze", "sleep", "confuse").contains(effect.type()) && target == EffectTarget.SELF)
                throw new IllegalArgumentException("自动异常状态需明确指定战斗位，不能把备战来源当作战斗位");
        }, true, false, namedPokemon, namedCounts);
    }
    private static void validateEventTarget(EffectTarget target,AbilityEventPolicy policy) {
        if(target==EffectTarget.TRIGGER_POKEMON && (policy.trigger()==EffectTrigger.ON_POKEMON_CHECKUP||policy.trigger()==EffectTrigger.ON_TURN_END))
            throw new IllegalArgumentException("回合边界事件没有唯一的触发宝可梦");
        if(target==EffectTarget.ATTACKING_POKEMON && !(policy instanceof DamageTriggerPolicy)
            && !(policy instanceof KnockoutTriggerPolicy ko && ko.cause()==KnockoutTriggerPolicy.Cause.OPPONENT_ATTACK_DAMAGE))
            throw new IllegalArgumentException("实际攻击者目标必须由伤害或攻击伤害击倒事件明确提供");
    }
    private static void fixedPokemonTarget(EffectTarget target) {
        if (!target.eventBound() && !Set.of(EffectTarget.SELF,EffectTarget.OWN_ACTIVE,EffectTarget.OPPONENT_ACTIVE,
            EffectTarget.OWN_BENCH,EffectTarget.OPPONENT_BENCH,EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON).contains(target))
            throw new IllegalArgumentException("自动特性需要公开固定宝可梦目标或全体区域");
    }
    private static boolean containsSourceDiscard(EffectDefinition effect) {
        return effect.type().equals("discard_source")||HealingEffect.discardSourceAfterHealing(effect)
            ||effect.effects().stream().anyMatch(TriggeredAbilityRules::containsSourceDiscard);
    }
    /**
     * Automatic effects may pause for a server-issued public Pokémon choice.
     * A named empty selector must therefore be consumed by a later
     * {@code with_pokemon}; otherwise it would create a prompt whose result
     * has no rules meaning.  Nested selectors with child effects are valid:
     * their child program itself consumes the selected target.
     */
    private static void validateAutomaticSelectionBindings(EffectProgram program) {
        var referenced = new HashSet<String>();
        for (var binding : program.triggers()) {
            for (var effect : binding.effects()) collectPokemonReferences(effect, referenced);
        }
        for (var binding : program.triggers()) {
            for (var effect : binding.effects()) validateNonDanglingSelector(effect, referenced);
        }
    }

    private static void collectPokemonReferences(EffectDefinition effect, Set<String> referenced) {
        if (AttackPokemonBindings.reference(effect)) referenced.add(effect.arguments().get("ref"));
        for (var child : effect.effects()) collectPokemonReferences(child, referenced);
    }

    private static void validateNonDanglingSelector(EffectDefinition effect, Set<String> referenced) {
        if (PokemonSelectionScope.handles(effect) && effect.arguments().containsKey("bind")
            && effect.effects().isEmpty() && !referenced.contains(effect.arguments().get("bind"))) {
            throw new IllegalArgumentException("自动特性的命名目标选择必须由后续效果引用");
        }
        for (var child : effect.effects()) validateNonDanglingSelector(child, referenced);
    }
    private static void validateDeferredConditions(EffectDefinition effect) {
        for (var condition : effect.conditions()) validateDeferredCondition(condition);
        for (var child : effect.effects()) validateDeferredConditions(child);
    }
    private static void validateDeferredCondition(ConditionDefinition condition) {
        for (var child : condition.children()) validateDeferredCondition(child);
        // Trigger predicates are latched at attachment. Later predicates cannot read a departed source's last-known facts yet.
        var target=EffectTarget.fromWire(condition.arguments().getOrDefault("target","SELF"));
        if ((target==EffectTarget.SELF || target.eventBound()) && Set.of(ConditionType.HAS_ENERGY,
            ConditionType.ENERGY_COUNT, ConditionType.DAMAGE_COUNTER_COUNT, ConditionType.HP_BELOW, ConditionType.HP_ABOVE,
            ConditionType.HAS_STATUS, ConditionType.TARGET_HAS_STATUS, ConditionType.IS_EVOLVED, ConditionType.IS_BASIC,
            ConditionType.IS_EX, ConditionType.IS_V, ConditionType.IS_GX, ConditionType.IS_VSTAR, ConditionType.IS_LEGACY_EX, ConditionType.IS_VMAX, ConditionType.IS_TERA, ConditionType.IS_TYPE,ConditionType.IS_FAMILY, ConditionType.BECAME_ACTIVE_THIS_TURN, ConditionType.KNOCKED_OUT_BY_ATTACK_LAST_TURN,ConditionType.KNOCKED_OUT_LAST_TURN, ConditionType.HEALED_THIS_TURN, ConditionType.HAS_TOOL, ConditionType.HAS_RULE_BOX, ConditionType.IS_ACTIVE).contains(condition.type()))
            throw new IllegalArgumentException("延后结算中依赖来源或事件对象状态的条件尚需最后已知状态支持；可在触发条件中声明");
    }

    /**
     * A departing Pokémon can retain its already-captured trigger identity,
     * but this first LKI slice deliberately permits only text independent of
     * the vanished in-play object.  It is therefore useful for effects such
     * as drawing/searching for the controller, without pretending that HP,
     * attachments, position, or a re-entered card can still be read.
     */
    private static void validateDepartedSourceProgram(EffectProgram program) {
        for (var binding : program.triggers()) {
            for (var condition : binding.conditions()) validateDepartedSourceCondition(condition);
            for (var effect : binding.effects()) validateDepartedSourceEffect(effect);
        }
    }

    private static void validateDepartedSourceEffect(EffectDefinition effect) {
        if (!Set.of("conditional", "coin_flip", "repeat", "repeat_per_heads", "choice",
            "draw", "discard", "search_deck", "search_discard_pile").contains(effect.type()))
            throw new IllegalArgumentException("离场来源最后已知信息当前只支持不依赖场上来源的区域步骤：" + effect.type());
        for (var condition : effect.conditions()) validateDepartedSourceCondition(condition);
        if (Set.of("draw", "discard", "search_deck", "search_discard_pile").contains(effect.type())
            && !Set.of(EffectTarget.HAND, EffectTarget.DECK, EffectTarget.DISCARD, EffectTarget.LOST_ZONE).contains(effect.target()))
            throw new IllegalArgumentException("离场来源的区域步骤必须明确指定手牌、牌库、弃牌区或放逐区，不能引用离场对象");
        if (effect.type().equals("search_deck") && "EVOLVE_SELF".equals(effect.arguments().get("destination")))
            throw new IllegalArgumentException("离场来源已不在场上，不能检索进化“这只宝可梦”");
        for (var child : effect.effects()) validateDepartedSourceEffect(child);
    }

    private static void validateDepartedSourceCondition(ConditionDefinition condition) {
        for (var child : condition.children()) validateDepartedSourceCondition(child);
        if (Set.of(ConditionType.HAS_ENERGY, ConditionType.ENERGY_COUNT, ConditionType.DAMAGE_COUNTER_COUNT,
            ConditionType.HP_BELOW, ConditionType.HP_ABOVE, ConditionType.HAS_STATUS, ConditionType.TARGET_HAS_STATUS,
            ConditionType.IS_EVOLVED, ConditionType.IS_BASIC, ConditionType.IS_EX, ConditionType.IS_V,
            ConditionType.IS_GX, ConditionType.IS_VSTAR, ConditionType.IS_LEGACY_EX, ConditionType.IS_VMAX, ConditionType.IS_TERA, ConditionType.IS_TYPE,ConditionType.IS_FAMILY, ConditionType.BECAME_ACTIVE_THIS_TURN, ConditionType.KNOCKED_OUT_BY_ATTACK_LAST_TURN,ConditionType.KNOCKED_OUT_LAST_TURN, ConditionType.HEALED_THIS_TURN, ConditionType.HAS_TOOL, ConditionType.HAS_RULE_BOX, ConditionType.IS_ACTIVE).contains(condition.type())) {
            var target = EffectTarget.fromWire(condition.arguments().getOrDefault("target", "SELF"));
            if (target == EffectTarget.SELF || target.eventBound() || target == EffectTarget.CHOSEN_POKEMON)
                throw new IllegalArgumentException("离场来源最后已知信息尚不能读取已离场宝可梦状态");
        }
    }
}
