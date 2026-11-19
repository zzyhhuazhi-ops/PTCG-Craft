package dev.hua.ptcglive.engine.effect;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Complete, non-mutating validation of the current trigger, including branches not taken. */
public final class EffectResolutionPreflight {
    private static final Set<String> COMPOSITE = Set.of("conditional", "coin_flip", "repeat", "repeat_per_heads", "choice", "select_pokemon", "with_pokemon");
    private static final Set<String> LEAVE_PLAY = Set.of("return_to_hand", "shuffle_into_deck", "discard_pokemon");
    private EffectResolutionPreflight() { }

    public static void validate(EffectProgram program, EffectTrigger trigger, Consumer<ConditionDefinition> condition,
                                Consumer<EffectDefinition> atomic) {
        validate(program, trigger, condition, atomic, false);
    }
    /** Only call sites with an actual continuation controller may opt into Choice. */
    public static void validate(EffectProgram program, EffectTrigger trigger, Consumer<ConditionDefinition> condition,
                                Consumer<EffectDefinition> atomic, boolean choices) {
        validate(program,trigger,condition,atomic,choices,false);
    }
    /** Compatibility overload: attacks are the only legacy caller that enables both named coins and named Pokémon. */
    public static void validate(EffectProgram program, EffectTrigger trigger, Consumer<ConditionDefinition> condition,
                                Consumer<EffectDefinition> atomic, boolean choices, boolean attackBindings) {
        validate(program, trigger, condition, atomic, choices, attackBindings, attackBindings);
    }
    /**
     * Named coin results remain attack-only, while a resumable server program
     * may independently opt in to named public Pokémon identities.
     */
    public static void validate(EffectProgram program, EffectTrigger trigger, Consumer<ConditionDefinition> condition,
                                Consumer<EffectDefinition> atomic, boolean choices, boolean namedCoins, boolean namedPokemon) {
        validate(program,trigger,condition,atomic,choices,namedCoins,namedPokemon,namedCoins);
    }
    public static void validate(EffectProgram program, EffectTrigger trigger, Consumer<ConditionDefinition> condition,
                                Consumer<EffectDefinition> atomic, boolean choices, boolean namedCoins, boolean namedPokemon, boolean namedCounts) {
        CoinFlipPolicy.validatePerHeadsScope(program.forTrigger(trigger).stream().flatMap(binding -> binding.effects().stream()).toList());
        long steps = 0;
        for (var binding : program.forTrigger(trigger)) {
            for (var entry : binding.conditions()) { PokemonSelectionScope.requireScope(entry,null);conditions(entry, condition,namedCoins,namedCounts); }
            for (var effect : binding.effects()) {
                steps += validate(effect, condition, atomic, 0, choices, null,namedCoins,namedPokemon,namedCounts,0);
                requireBudget(steps);
            }
        }
        // A leave-play step may safely be followed by player/zone-only text
        // (for example, return a Pokémon and then draw).  It must never leave
        // a later step or condition pointing at the departed in-play object.
        // Nested/branched departures remain closed until their full data-flow
        // is modelled; this check only opens the provably independent case.
        if (!choices && unsafeLeaveContinuation(program.forTrigger(trigger)))
            throw new IllegalStateException("离场后续步骤仍引用已离场宝可梦，整段效果未执行");
        DirectKoLeavePlaySafety.validate(program.forTrigger(trigger).stream().flatMap(binding -> binding.effects().stream()).toList());
    }

    private static long validate(EffectDefinition effect, Consumer<ConditionDefinition> condition,
                                 Consumer<EffectDefinition> atomic, int depth, boolean choices, PokemonSelectionScope.Position enclosing,
                                 boolean namedCoins, boolean namedPokemon, boolean namedCounts, int enclosingCoinMaximum) {
        if (depth > 32) throw new IllegalArgumentException("Effect 嵌套过深");
        EffectSchemaValidator.validate(effect);
        if(!namedCoins&&effect.type().equals("coin_flip")&&effect.arguments().containsKey("bind"))
            throw new IllegalStateException("此效果入口不允许命名攻击硬币");
        // Named counts (“If you do …”) belong to one declared attack, like named coins.
        if(!namedCounts&&(AttackCountBindings.bound(effect)!=null||AttackCountBindings.reference(effect)!=null))
            throw new IllegalStateException("此效果入口不允许攻击计数");
        if(!namedPokemon&&(AttackPokemonBindings.reference(effect)||PokemonSelectionScope.handles(effect)&&effect.arguments().containsKey("bind")))
            throw new IllegalStateException("此效果入口不允许命名宝可梦");
        if ((effect.type().equals("choice") || DrawCount.upTo(effect) || PokemonSelectionScope.handles(effect)) && !choices)
            throw new IllegalStateException("此效果入口尚未接入可暂停的选择流程");
        PokemonSelectionScope.requireScope(effect,enclosing);
        for (var entry : effect.conditions()) { PokemonSelectionScope.requireScope(entry,enclosing);conditions(entry, condition,namedCoins,namedCounts); }
        // Future steps have their own time, target and coin scope. Validate now, execute only the scheduling atom.
        if(effect.type().equals("delayed_effect")) {DelayedEffectProgram.from(effect);atomic.accept(effect);return 1;}
        // The container still needs its integration's explicit opt-in. Other contexts fail before any prefix.
        if(PokemonSelectionScope.handles(effect))atomic.accept(effect);
        if (!COMPOSITE.contains(effect.type())) {
            if (!effect.effects().isEmpty()) throw new IllegalArgumentException("原子 Effect 不能悄悄忽略嵌套效果");
            Set<String> allowed = switch (effect.type()) {
                case DistinctBenchEnergy.TYPE -> Set.of("target_count","distinct_energy_types");
                case "extra_turn" -> Set.of("skip_checkup");
                case "discard_recover_named_supporter" -> Set.of("definition_ids","hand_discard_cost");
                case "modify_damage_taken" -> AttackDamageReceivedModifier.FIELDS;
                case "damage_counter" -> DamageCounterDistribution.handles(effect) ? DamageCounterDistribution.FIELDS : Set.of("amount","remaining_hp","per_opponent_prize_taken","target_count","position","from_trigger_damage","extra_turn_on_ko","previous_turn_attack_ids","bind","bind_mode","counter_multiplier","per_source_energy_type","per_own_pokemon_names","per_discard_basic_energy_type","per_hand_card","basic_pokemon_only","status","damage_counters_min","has_abilities","except_name_contains","except_name","pokemon_families");
                case "heal" -> HealingEffect.FIELDS;
                case "take_prize_cards" -> TakePrizeCards.FIELDS;
                case "opponent_hand_to_bottom" -> OpponentHandBottom.FIELDS;
                case "opponent_hand_to_deck" -> OpponentHandShuffle.FIELDS;
                case "opponent_hand_basic_to_bench" -> Set.of("hp_max","amount","minimum","switch_in");
                case "hand_to_deck_bottom" -> HandToDeckBottom.FIELDS;
                case "knock_out" -> Set.of("remaining_hp_max","bind");
                case "devolve" -> Set.of("amount","destination");
                case "weakness_override" -> WeaknessOverride.FIELDS;
                case "grant_star_cipher_ability" -> Set.of();
                case KnockoutCardsToHand.TYPE -> KnockoutCardsToHand.FIELDS;
                case "knockout_top_to_hand" -> Set.of();
                case "attack_coin_gate" -> AttackCoinGate.FIELDS;
                case "lock_opponent_attack" -> Set.of();
                case "damage_per_energy" -> EnergyCountQuery.EFFECT_FIELDS;
                case "discard_top_deck" -> TopDeckDiscardEffect.FIELDS;
                case "discard_top_attach_energy" -> Set.of("top_count", "card_category");
                case "recover_all_discard" -> DiscardPileEffects.RECOVERY_FIELDS;
                case "damage_per_discard_card" -> DiscardPileEffects.DAMAGE_FIELDS;
                case "damage_per_pokemon" -> PokemonCountEffect.FIELDS;
                case "discard_random_hand" -> Set.of("amount","mode","destination");
                case "damage" -> Set.of("amount","mode");
                case "self_damage", "recoil" -> Set.of("amount", "amount_mode");
                case "shuffle_random_hand", "reduce_damage", "increase_attack_cost", "increase_retreat_cost", "reduce_attack_damage", "increase_damage_taken", "damage_multiplier",
                    "damage_per_bench" -> Set.of("amount");
                case "boost_attack_damage" -> Set.of("amount", "attack_id");
                case "damage_per_damage_counter" -> Set.of("amount","pokemon_types","pokemon_names");
                case "prevent_small_attack_damage" -> Set.of("amount");
                case "next_turn_attack_base" -> Set.of("attack_id","amount");
                case "evolve_each_bench_from_deck" -> Set.of();
                case "evolve_selected_from_deck" -> Set.of("target_count","minimum","pokemon_type","target_stage","evolutions","has_abilities","bench_only","evolution_timing","excluded_host_family","evolution_names","evolution_family","bind_pokemon");
                case "flustered_leap" -> Set.of();
                case "win_game" -> Set.of();
                case TopPrizeSwap.RESOLVE -> Set.of("position");
                case PrizeSourceExchange.TYPE -> Set.of("prize_filter");
                case "reset_prizes" -> Set.of("destination");
                case "turn_supporter_limit" -> Set.of("amount");
                case "draw_until" -> DrawCount.FIELDS;
                // PokemonEffectSelection.validateBenchDamage decides which forms may carry target_count.
                case "bench_damage" -> Set.of("amount", "target_count", "amount_mode", "pokemon_names", "has_tool", "damaged_only");
                case DragonLauncherEffect.TYPE -> DragonLauncherEffect.FIELDS;
                case "copy_attack_from_opponent_deck_top" -> Set.of("top_count","cost_policy");
                case "copy_attack_from_own_deck_top" -> Set.of("top_count","cost_policy","discard","rule_box");
                case "damage_per_bound" -> Set.of("bind", "amount");
                case "target_filter" -> Set.of("damage_counters_min", "exclude_attacker");
                case "damage_per_tails" -> Set.of("coin", "amount");
                case "damage_per_special_condition" -> Set.of("amount");
                case "repeat_target_damage" -> Set.of("count", "amount");
                case "damage_per_hand_card" -> Set.of("amount", "card_category", "trainer_category", "limit");
                case "opt_in" -> Set.of("bind");
                // “Draw a card. If you do, …” / “Switch … If you do, …”: bind records what actually happened (attacks only, checked above).
                case "draw" -> DrawCount.FIELDS;
                case "draw_bottom" -> Set.of("amount");
                case "reveal_hand" -> HandRevealEffect.FIELDS;
                case "discard_hand" -> Set.of("bind","trainer_categories");
                case "evolve_from_hand" -> HandEvolutionEffect.FIELDS;
                case "switch" -> Set.of("bind", "pokemon_name_contains", "source_only_if_benched");
                case "force_switch" -> Set.of("bind", "pokemon_name_contains");
                case "gust" -> Set.of("bind", "pokemon_name_contains", "pokemon_families", "basic_pokemon_only", "remaining_hp_max");
                case "switch_source_with_active" -> Set.of("bind");
                case "retaliate" -> Set.of("amount", "mode");
                case "extra_prize_on_attack_ko" -> Set.of("amount","basic_only");
                case "extra_prize_for_trigger_ko" -> Set.of("amount","nonstack_group");
                case "prevent_prize_take" -> Set.of("all_prizes","attacker_filter");
                case "prevent_hand_energy" -> HandEnergyLock.FIELDS;
                case "trainer_attempt_coin_gate" -> Set.of("scope","source","on_tails","duration");
                case "discard_opponent_stadium_and_lock", "use_top_supporter" -> Set.of();
                case "hand_energy_counter_trap" -> Set.of("amount");
                case "hand_energy_end_turn_trap" -> Set.of();
                case DevolutionSpray.TYPE -> Set.of();
                case DiscardedDragonEnergy.TYPE -> Set.of();
                case DamagePump.TYPE -> Set.of();
                case WitchDomain.TYPE -> Set.of("maximum","destination");
                case RandomHandSupporterReveal.TYPE -> Set.of();
                case TopEnergyReveal.TYPE -> TopEnergyReveal.FIELDS;
                case "reveal_until_supporter" -> Set.of();
                case "discard_bench_until", "reveal_top_take_pokemon" -> Set.of("amount");
                case "turn_prize_face_up" -> Set.of("all","amount","minimum","bind");
                case "discard_hand_keep" -> Set.of("amount","minimum");
                case "return_source_to_bottom", "return_source_to_hand", "shuffle_source_into_deck", "unrecoverable_from_discard" -> Set.of();
                case "discard_hand_at_turn_end" -> Set.of("minimum");
                case "draw_until_at_turn_end", "shuffle_hand_keep_at_turn_end", "shuffle_hand_keep" -> Set.of("amount");
            case "prevent_trainer_play" -> Set.of("trainer_category");
                case "poison", "burn", "confuse" -> Set.of("counters");
                case "damage_per_prize" -> PrizeCountEffect.FIELDS;
                case "inspect_top_deck" -> TopCardInspection.FIELDS;
                case "inspect_prize" -> Set.of("amount","minimum");
                case "replace_pokemon_from_deck" -> PokemonDeckReplacement.FIELDS;
                case "replace_basic_from_deck" -> BasicDeckReplacement.FIELDS;
                case "replace_basic_from_discard" -> PokemonDiscardReplacement.FIELDS;
                case "split_basic_energy_search" -> Set.of("amount","different_types","destinations");
                case "discard_reorder_top" -> Set.of("amount","minimum_discard");
                case BlainesQuiz.SELECT, CedricQuiz.SELECT -> Set.of();
                case "guess_pokemon_hp" -> Set.of("opponent_draw","owner_draw");
                case TrainerRecoveryBargain.TYPE -> Set.of();
                case TrainerRecoveryBargain.RESOLVE -> Set.of("allow");
                case "top_distinct_category_search" -> Set.of("top_count","categories","amount_each","minimum_each","remainder");
                case FieldToolStadiumDiscard.TYPE -> Set.of();
                case "tool_or_stadium_to_lost_zone" -> Set.of("include_special_energy");
                case "sleep_recovery_two_coins_until_cleared" -> Set.of();
                case "resolve_hp_guess" -> Set.of("guessed_hp","opponent_draw","owner_draw");
                case "rock_paper_scissors" -> Set.of("winner_draw");
                case "swap_hand_top_deck" -> HandTopDeckSwap.FIELDS;
                case "swap_prizes_hand" -> Set.of("amount","minimum");
                case "prize_random_hand_swap" -> Set.of("amount");
                case "opponent_choose_discard" -> OpponentHandDiscard.FIELDS;
                case "turn_attack_bonus" -> TurnAttackBonus.FIELDS;
                case "turn_damage_reduction" -> TurnDamageReduction.FIELDS;
                case "turn_attack_lock" -> TurnAttackLock.FIELDS;
                case "force_opponent_coin_tails" -> Set.of("duration");
                case "opponent_top_basic_to_bench" -> Set.of("top_count","minimum","amount");
                case "opponent_choose_deck_top" -> Set.of("top_count","amount");
                case "next_turn_extra_prize_if_defender_ko" -> Set.of("amount");
                case "shuffle_both_active_into_deck" -> Set.of("acting_player_selects_first");
                case "use_opponent_hand_supporter" -> Set.of("optional");
                case "move_all_bench_damage_to_active", "redistribute_opponent_damage_counters" -> Set.of("optional");
                case "shuffle_named_bench_then_damage" -> Set.of("pokemon_names","bind");
                case "shuffle_unchosen_opponent_bench" -> Set.of("keep","chooser");
                case "field_attack_protection" -> FieldAttackProtection.FIELDS;
                case "turn_ignore_defender_damage_effects" -> Set.of("pokemon_families");
                case "turn_choose_next_coin" -> Set.of();
                case "turn_gx_permission" -> Set.of("pokemon_name","pokemon_type");
                case "remove_attack_effects" -> Set.of("scope");
                case "turn_ability_suppression" -> Set.of("scope","duration");
                case "turn_extra_prize_on_attack_ko" -> TurnExtraPrizeOnAttackKo.FIELDS;
                case "reveal_hand_cards" -> SelectedHandReveal.FIELDS;
                case "shuffle_revealed_hand_cards" -> ShuffleRevealedHandCards.FIELDS;
                case "per_target_coin_damage" -> Set.of("amount");
                case "search_deck", "reorder_top_deck", "discard", "search_discard_pile" -> ZoneSelectionEffect.FIELDS;
                case "attach_energy" -> AttachEnergyEffect.FIELDS;
                case "attach_tool" -> AttachToolEffect.FIELDS;
                case "retreat" -> RetreatEffect.FIELDS;
                case "reveal_deck_window" -> AttackDeckWindow.REVEAL_FIELDS;
                case "resolve_deck_window" -> AttackDeckWindow.RESOLVE_FIELDS;
                case "plan_energy_discard" -> PlannedEnergyDiscard.PLAN_FIELDS;
                case "commit_energy_discard" -> Set.of("ref");
                case "discard_energy", "return_energy", "attached_energy_to_lost_zone", "move_energy" -> AttachedEnergyEffect.FIELDS;
                case "shuffle_hand", "discard_stadium" -> Set.of("bind");
                case "shuffle_both_hands_bottom_draw_prizes" -> Set.of("own_draw", "opponent_draw");
                case "move_damage_counter" -> MoveDamageCounterEffect.FIELDS;
                case "survive_attack_damage" -> Set.of("remaining_hp");
                case "clear_status" -> ClearSpecialConditionsEffect.FIELDS;
                case "discard_tool", "return_tool" -> AttachedToolEffect.FIELDS;
                case "return_to_hand" -> Set.of("discard_attached");
                case "prevent_damage", "prevent_effects" -> ProtectionEffect.FIELDS;
                case "protect_on_attack_damage_ko" -> AttackDamageKoProtection.FIELDS;
                case "no_prizes_if_ko_next_opponent_turn" -> Set.of("duration");
                case "survive_next_turn_attack_damage" -> Set.of("remaining_hp","requires_full_hp","duration");
                case "prevent_attack" -> AttackLockEffect.FIELDS;
                case "prevent_evolution" -> EvolutionLockEffect.FIELDS;
                default -> Set.of();
            };
            arguments(effect, allowed);
            if(effect.type().equals("heal"))HealingEffect.from(effect);
            atomic.accept(effect);
            return 1;
        }
        arguments(effect, switch (effect.type()) {
            case "coin_flip" -> CoinFlipPolicy.FIELDS;
            case "repeat" -> Set.of("count");
            case "repeat_per_heads" -> Set.of();
            case "select_pokemon" -> PokemonSelectionScope.FIELDS;
            case "with_pokemon" -> Set.of("ref","position");
            case "choice" -> Set.of("optional","chooser","require_usable_option");
            default -> Set.of();
        });
        long children = 0;
        for (var child : effect.effects()) {
            children += validate(child, condition, atomic, depth + 1, choices,
                PokemonSelectionScope.handles(effect)||AttackPokemonBindings.reference(effect)?PokemonSelectionScope.position(effect):enclosing,namedCoins,namedPokemon,namedCounts,
                effect.type().equals("coin_flip") ? CoinFlipPolicy.from(effect).maximum() : enclosingCoinMaximum);
            requireBudget(children);
        }
        // Zero denotes an unbounded until-tails sequence: validate its repeated branch once, never invent a coin cap.
        long count = effect.type().equals("repeat") ? Integer.parseInt(effect.arguments().get("count"))
            : effect.type().equals("repeat_per_heads") ? Math.max(1,enclosingCoinMaximum) : 1;
        long steps = count * (children + 1);
        requireBudget(steps);
        return steps;
    }

    private static void conditions(ConditionDefinition condition, Consumer<ConditionDefinition> validator,boolean attackBindings,boolean namedCounts) {
        EffectSchemaValidator.validate(condition);
        if(!attackBindings&&AttackCoinBindings.references(condition))throw new IllegalStateException("此效果入口不允许引用命名攻击硬币");
        if(!namedCounts&&AttackCountBindings.references(condition))throw new IllegalStateException("此效果入口不允许引用攻击计数");
        // Do not let AND/OR short-circuit validation of a malformed later operand.
        for (var child : condition.children()) conditions(child, validator,attackBindings,namedCounts);
        if (condition.children().isEmpty()) validator.accept(condition);
    }
    private static boolean leavesPlay(EffectDefinition effect) {
        return LEAVE_PLAY.contains(effect.type()) || effect.effects().stream().anyMatch(EffectResolutionPreflight::leavesPlay);
    }
    private static boolean unsafeLeaveContinuation(List<TriggeredEffects> bindings) {
        var entries = new ArrayList<EffectDefinition>();
        var preconditions = new ArrayList<List<ConditionDefinition>>();
        for (var binding : bindings) for (var effect : binding.effects()) {
            entries.add(effect); preconditions.add(binding.conditions());
        }
        for (int index = 0; index < entries.size(); index++) {
            EffectDefinition leaving = entries.get(index);
            if (!leavesPlay(leaving)) continue;
            // A departure behind a branch/repeat/choice has no single static
            // continuation path, so leave it unsupported rather than guessing.
            if (!LEAVE_PLAY.contains(leaving.type())) return true;
            EffectTarget departed = leaving.target();
            for (int later = index + 1; later < entries.size(); later++) {
                if (conditionsReferenceDepartedPokemon(preconditions.get(later), departed)
                    || referencesDepartedPokemon(entries.get(later), departed)) return true;
            }
        }
        return false;
    }
    private static boolean referencesDepartedPokemon(EffectDefinition effect, EffectTarget departed) {
        if (pokemonTargetEffect(effect.type()) && effect.target() == departed) return true;
        if (effect.type().equals("move_damage_counter")
            && EffectTarget.fromWire(effect.arguments().getOrDefault("destination", "SELF")) == departed) return true;
        return conditionsReferenceDepartedPokemon(effect.conditions(), departed)
            || effect.effects().stream().anyMatch(child -> referencesDepartedPokemon(child, departed));
    }
    private static boolean conditionsReferenceDepartedPokemon(List<ConditionDefinition> conditions, EffectTarget departed) {
        return conditions.stream().anyMatch(condition -> conditionReferencesDepartedPokemon(condition, departed));
    }
    private static boolean conditionReferencesDepartedPokemon(ConditionDefinition condition, EffectTarget departed) {
        boolean pokemonCondition = switch (condition.type()) {
            case HAS_ENERGY, HAS_EXTRA_ATTACK_ENERGY, ENERGY_COUNT, DAMAGE_COUNTER_COUNT, HP_BELOW, HP_ABOVE, HAS_STATUS, TARGET_HAS_STATUS,
                IS_EVOLVED, EVOLVED_THIS_TURN, IS_STAGE, IS_BASIC, IS_EX, IS_V, IS_GX, IS_VSTAR, IS_LEGACY_EX, IS_VMAX, IS_TERA, IS_TYPE, IS_FAMILY, BECAME_ACTIVE_THIS_TURN, RETREATED_LAST_OPPONENT_TURN, HEALED_THIS_TURN, HAS_TOOL, HAS_RESISTANCE, WEAK_TO_OPPONENT_ACTIVE, HAS_RULE_BOX, IS_ACTIVE -> true;
            default -> false;
        };
        return pokemonCondition && EffectTarget.fromWire(condition.arguments().getOrDefault("target", "SELF")) == departed
            || condition.children().stream().anyMatch(child -> conditionReferencesDepartedPokemon(child, departed));
    }
    private static boolean pokemonTargetEffect(String type) {
        return Set.of("damage", "heal", "poison", "burn", "paralyze", "sleep", "confuse", "damage_counter",
            "move_damage_counter", "bench_damage", "self_damage", "recoil", "damage_multiplier", "damage_per_energy",
            "damage_per_damage_counter", "damage_per_bench", "damage_per_prize", "damage_per_bound", "damage_per_tails", "damage_per_special_condition", "prevent_damage", "prevent_effects", "protect_on_attack_damage_ko", "no_prizes_if_ko_next_opponent_turn", "survive_next_turn_attack_damage", "reduce_damage", "modify_damage_taken", "weakness_override", "grant_star_cipher_ability", "increase_attack_cost", "increase_retreat_cost", "reduce_attack_damage", "boost_attack_damage", "increase_damage_taken", "attack_coin_gate", "lock_opponent_attack", "extra_prize_for_trigger_ko", "prevent_prize_take",
            "return_to_hand", "shuffle_into_deck", "discard_pokemon", "devolve", "prevent_evolution", "prevent_attack", "prevent_retreat", "disable_abilities", "disable_tools", "knock_out", "copy_attack", "attach_energy", "attach_tool", "clear_status",
            "plan_energy_discard", "discard_energy", "return_energy", "attached_energy_to_lost_zone", "move_energy", "discard_tool", "return_tool", "switch", "force_switch", "gust",
            "retreat", "select_pokemon", "with_pokemon").contains(type);
    }
    private static void arguments(EffectDefinition effect, Set<String> allowed) {
        if (!allowed.containsAll(effect.arguments().keySet()))
            throw new IllegalArgumentException("Effect 包含尚未支持或拼写错误的参数：" + effect.type());
    }
    private static void requireBudget(long steps) {
        if (steps > 4096) throw new IllegalArgumentException("Effect 连续结算超过安全步数上限");
    }
}
