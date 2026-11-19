package dev.hua.ptcglive.engine.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One parser registry for all reusable card effects. Card JSON uses stable
 * string IDs and never references Java implementation class names.
 */
public final class EffectRegistry {
    @FunctionalInterface
    public interface Factory { EffectDefinition parse(JsonObject source, EffectRegistry registry); }

    private static final Set<String> RESERVED_EFFECT_KEYS = Set.of("type", "target", "conditions", "effects", "heads", "tails", "options");
    private final Map<String, Factory> factories = new LinkedHashMap<>();
    private final CustomEffectRegistry customEffects;

    public EffectRegistry(CustomEffectRegistry customEffects) {
        this.customEffects = Objects.requireNonNull(customEffects, "CustomEffectRegistry");
    }

    public static EffectRegistry standard() {
        var registry = new EffectRegistry(new CustomEffectRegistry());
        for (String type : List.of("legendary_stadium","return_after_own_attack_discard","remove_attack_effects","prepare_distinct_bench_energy","inspect_top_prize_swap","resolve_top_prize_swap","exchange_prize_with_source","skip_evolution_turn_end","attack_while_asleep_or_paralyzed","retreat_while_asleep_or_paralyzed","discard_hand_at_turn_end","draw_until_at_turn_end","shuffle_hand_keep_at_turn_end","grant_attack","attach_energy_each_named_bench","both_top_energy_damage","choose_player_hand_reset","choose_weakness_override","coin_attack_branch","damage_counter_then_self_damage_if_placed","discard_top_per_energy","discard_top_retreat_cost_damage","draw_until_opponent_hand","field_damage_counters_per_named_pokemon","force_opponent_coin_tails","gust_damage_coin_paralyze","increase_retreat_cost","inspect_both_top_cards","named_previous_attack_damage","opponent_hand_basic_to_bench_then_active","opponent_hand_to_discard","opponent_top_two_one_bottom","optional_discard_exact_energy","prevent_deck_discard","prevent_opponent_discard_recycle","prevent_return_to_hand","prize_pokemon_to_bench_coin_extra_prize","reattach_after_own_attack_discard","redistribute_energy","reorder_either_top_deck","retain_poison_on_evolution","return_opponent_energy_then_attach_hand","search_deck_per_bench","search_matching_opponent_pokemon","self_position_switch","shuffle_both_active_into_deck","shuffle_hand_to_bottom","shuffle_named_bench_then_damage","shuffle_opponent_bench_and_self","return_source_to_bottom", "return_source_to_hand", "shuffle_source_into_deck","discard_hand_keep", "shuffle_hand_keep","sleep_recovery_two_coins","sleep_recovery_two_coins_until_cleared","stadium_effect_guard","top_cards_take_all_or_discard_draw","until_tails_damage_first_tails_confuse","per_target_coin_damage","reveal_hand_cards","shuffle_revealed_hand_cards",
            "bench_restored_from_hand", "discard_self_to_deck_bottom", "discard_bench_until", "devolve_evolutions_to_deck", "discard_top_attach_dragon", "move_own_damage_counters", "reveal_random_hand_supporter", "reveal_top_attach_energy", "reveal_until_supporter", "reveal_top_take_pokemon", "reveal_deck_window", "resolve_deck_window", "damage", "heal", "draw", "discard", "evolve_from_hand", "replace_pokemon_from_deck", "replace_basic_from_discard", "discard_pokemon_to_bench_self", "search_deck", "reorder_top_deck", "inspect_top_deck", "inspect_prize", "swap_hand_top_deck", "swap_prizes_hand", "prize_random_hand_swap", "take_prize_cards", "turn_prize_face_up", "opponent_hand_to_bottom", "opponent_hand_to_deck", "opponent_hand_basic_to_bench", "opponent_top_basic_to_bench", "opponent_choose_deck_top", "search_basic_to_bench_then_move_energy", "self_counter_choice_damage", "redistribute_opponent_damage_counters", "redistribute_damage_counters", "shuffle_unchosen_opponent_bench", "coin_discard_energy_per_tails", "self_damage_per_damage_counter", "damaged_field_damage_except_self", "future_top_five_damage_discard", "use_opponent_hand_supporter", "next_turn_extra_prize_if_defender_ko", "move_all_bench_damage_to_active", "search_deck_to_discard", "draw_per_pokemon", "end_turn", "extra_turn", "discard_hand_until", "draw_per_bench", "checkup_poison_bonus", "inspect_top_to_bottom", "discard_attached_named_tools_then_reduce", "attach_energy_and_damage_host", "damage_counter_then_discard_self", "bench_self_from_hand", "replace_basic_from_deck", "bench_limit", "shuffle_both_hands_bottom_draw_prizes", "search_non_rulebox_per_discard_name", "draw_per_bound", "tool_granted_attack", "coin_shuffle_energy_per_heads", "top_window_damage_discard", "attach_energy_per_prize_taken", "top_card_take_or_discard_draw", "choose_trainer_lock", "move_all_bench_damage_to_opponent_active", "target_filter", "both_players_basic_from_discard_to_bench", "heal_attached_host", "damage_counter_per_named_own_pokemon", "coin_prize_reduction", "hand_to_deck_bottom", "opponent_choose_discard", "search_discard_pile", "attach_energy", "attach_tool", "plan_energy_discard", "commit_energy_discard", "discard_energy", "return_energy", "attached_energy_to_lost_zone",
            "move_energy", "move_damage_counter", "discard_tool", "return_tool", "discard_tools_and_special_energy", "switch", "switch_source_with_active", "force_switch", "gust", "retreat", "poison", "burn", "paralyze", "sleep", "confuse", "clear_status", "survive_attack_damage", "survive_next_turn_attack_damage", "knockout_top_to_hand", "knockout_cards_to_hand",
            "coin_flip", "damage_counter", "bench_damage", "discard_named_bench_multi_target_damage", "bench_attack_damage_bonus", "self_damage", "recoil", "damage_multiplier", "damage_per_energy",
            "damage_from_last_opponent_attack", "damage_per_damage_counter", "damage_per_bench", "damage_per_prize", "damage_per_bound", "damage_per_hand_card", "damage_per_pokemon", "damage_per_tails", "damage_per_special_condition", "repeat_target_damage", "attack_cost_override", "damage_per_discard_card", "opt_in", "retaliate", "prevent_hand_energy", "prevent_trainer_play", "trainer_attempt_coin_gate", "discard_opponent_stadium_and_lock", "hand_energy_counter_trap", "hand_energy_end_turn_trap", "use_top_supporter", "prevent_damage", "prevent_small_attack_damage", "prevent_effects", "protect_on_attack_damage_ko", "no_prizes_if_ko_next_opponent_turn", "reduce_damage", "modify_damage_taken", "ignore_resistance", "weakness_override", "increase_attack_cost", "reduce_attack_damage", "boost_attack_damage", "increase_damage_taken", "next_turn_attack_base", "attack_coin_gate", "lock_opponent_attack", "lock_this_attack", "return_to_hand",
            "shuffle_into_deck", "discard_pokemon", "knock_out", "devolve", "evolve_each_bench_from_deck", "evolve_selected_from_deck", "flustered_leap", "prevent_evolution", "prevent_attack", "prevent_retreat", "disable_abilities", "disable_tools", "disable_all_tools","disable_opponent_active_tools", "prevent_opponent_attack_ability_counters","prevent_bench_damage_counters", "retain_confusion_on_evolution", "retain_conditions_on_evolution", "setup_active_override", "basic_energy_multiplier_aura", "bench_entry_damage_counters", "tool_hand_play_guard", "pokemon_type_override", "add_pokemon_types", "weakness_override_aura", "grant_star_cipher_ability", "suppress_self_knockout_abilities", "prize_reduction_aura", "discard_stadium", "recover_all_discard", "discard_recover_named_supporter", "discard_top_deck", "discard_top_attach_energy", "draw_bottom", "discard_random_hand", "shuffle_random_hand", "draw_until", "extra_prize_on_attack_ko", "extra_prize_for_trigger_ko", "prevent_prize_take", "turn_choose_next_coin","turn_gx_permission","turn_ability_suppression", "turn_supporter_limit", "turn_ignore_defender_damage_effects", "turn_attack_bonus", "turn_damage_reduction", "turn_attack_lock", "field_attack_protection", "turn_extra_prize_on_attack_ko", "discard_hand", "shuffle_hand", "reveal_hand", "copy_attack", "borrow_attacks", "conditional", "choice", "repeat", "repeat_per_heads", "select_pokemon", "with_pokemon",
            "coin_copy_attack", "copy_attack_from_opponent_deck_top", "copy_attack_from_own_deck_top", "reroll_attack_coins", "draw_at_turn_end_after_attack_tails", "flare_gear","cafe_master_turn_continues","inherit_poison_on_own_trainer_switch","discard_replacing_stadium", "add_pokemon_families","prize_energy_attach","knockout_prizes_to_discard","knockout_pokemon_to_lost_zone","discard_from_field_to_hand","retreat_energy_to_hand", "free_retreat", "ignore_colorless_attack_cost", "ignore_all_attack_cost", "no_weakness","no_resistance","prevent_item_supporter_effects","prevent_self_attack_damage", "special_condition_immunity", "evolution_timing_override", "evolution_name_alias", "placement_rule", "festival_attack_twice", "festival_ground", "coin_prevent_attack_damage", "ignore_defender_damage_effects", "energy_special_condition_immunity", "tera_bench_expansion", "stadium_attack_guard", "unrecoverable_from_discard", "split_basic_energy_search", "attach_energy_each_selected", "replace_pokemon_from_discard", "discard_special_energy_each", "discard_reorder_top", "quiz_select_pokemon", "quiz_select_printed_height","guess_pokemon_hp", "top_distinct_category_search", "trainer_recovery_bargain", "resolve_trainer_recovery_bargain", "resolve_hp_guess", "rock_paper_scissors", "swap_attached_energy_with_deck", "resolve_energy_deck_swap", "tool_or_stadium_to_lost_zone", "discard_field_tools_and_stadium", "passive_trainer_play_lock", "passive_pokemon_play_lock", "first_turn_attack_permission", "passive_attack_guard", "prevent_damage_counter_move", "prevent_healing", "attack_weakness_multiplier","bench_weakness_resistance","weakness_multiplier", "passive_effect_guard", "prevent_checkup_recovery", "retreat_attempt_coin_gate", "retreat_cost_modifier", "attack_cost_modifier", "attack_damage_modifier", "tool_capacity", "special_energy_override", "hit_points_modifier", "prize_card_modifier", "suppress_abilities", "discard_source", "delayed_effect", "win_game", "reset_prizes", "custom"))
            registry.register(type, EffectRegistry::parseGeneric);
        registry.register("prevent_retreat_poisoned", EffectRegistry::parseGeneric);
        return registry;
    }

    public void register(String type, Factory factory) {
        String normalized = normalizeType(type);
        if (factories.putIfAbsent(normalized, Objects.requireNonNull(factory, "Effect Factory")) != null)
            throw new IllegalArgumentException("重复 Effect 类型：" + normalized);
    }

    public boolean supports(String type) { return factories.containsKey(normalizeType(type)); }
    public Set<String> supportedTypes() { return Set.copyOf(factories.keySet()); }
    public CustomEffectRegistry customEffects() { return customEffects; }

    public EffectDefinition parse(JsonObject source) {
        Objects.requireNonNull(source, "Effect JSON");
        String type = normalizeType(requiredString(source, "type", "Effect 类型"));
        Factory factory = factories.get(type);
        if (factory == null) throw new IllegalArgumentException("未注册 Effect 类型：" + type);
        return factory.parse(source, this);
    }

    private static EffectDefinition parseGeneric(JsonObject source, EffectRegistry registry) {
        String type = normalizeType(requiredString(source, "type", "Effect 类型"));
        EffectTarget target = source.has("target") ? EffectTarget.fromWire(requiredString(source, "target", "Effect 目标")) : EffectTarget.SELF;
        Map<String, String> arguments = primitiveArguments(source, RESERVED_EFFECT_KEYS, "Effect 参数");
        List<ConditionDefinition> conditions = parseConditions(source, "conditions");
        List<EffectDefinition> nested = parseEffects(source, "effects", registry);
        if (source.has("heads")) nested.add(branch("HEADS", parseEffects(source, "heads", registry)));
        if (source.has("tails")) nested.add(branch("TAILS", parseEffects(source, "tails", registry)));
        if (source.has("options")) nested.addAll(parseEffects(source, "options", registry));
        if (type.equals("custom")) {
            String handler = arguments.get("handler");
            if (handler == null || !handler.matches("[a-z][a-z0-9_-]*"))
                throw new IllegalArgumentException("CustomEffect 必须提供稳定 handler");
            CustomEffectHandler implementation = registry.customEffects.find(handler)
                .orElseThrow(() -> new IllegalArgumentException("CustomEffect handler 尚未注册：" + handler));
            EffectDefinition effect = new EffectDefinition(type, target, arguments, conditions, nested);
            implementation.validate(effect);
            EffectSchemaValidator.validate(effect);
            return effect;
        }
        EffectDefinition effect = new EffectDefinition(type, target, arguments, conditions, nested);
        EffectSchemaValidator.validate(effect);
        return effect;
    }

    private static EffectDefinition branch(String result, List<EffectDefinition> effects) {
        if (effects.isEmpty()) throw new IllegalArgumentException("硬币分支不能为空");
        return new EffectDefinition("conditional", EffectTarget.SELF, Map.of(),
            List.of(new ConditionDefinition(ConditionType.COIN_RESULT, Map.of("value", result), List.of())), effects);
    }

    private static List<EffectDefinition> parseEffects(JsonObject source, String field, EffectRegistry registry) {
        if (!source.has(field)) return new ArrayList<>();
        JsonArray values = source.getAsJsonArray(field);
        var effects = new ArrayList<EffectDefinition>();
        for (JsonElement element : values) effects.add(registry.parse(element.getAsJsonObject()));
        return effects;
    }

    public static ConditionDefinition parseCondition(JsonObject source) {
        ConditionType type = ConditionType.fromWire(requiredString(source, "type", "条件类型"));
        Map<String, String> arguments = primitiveArguments(source, Set.of("type", "conditions", "children"), "条件参数");
        JsonArray rawChildren = source.has("conditions") ? source.getAsJsonArray("conditions")
            : source.has("children") ? source.getAsJsonArray("children") : null;
        var children = new ArrayList<ConditionDefinition>();
        if (rawChildren != null) for (JsonElement child : rawChildren) children.add(parseCondition(child.getAsJsonObject()));
        return new ConditionDefinition(type, arguments, children);
    }

    private static List<ConditionDefinition> parseConditions(JsonObject source, String field) {
        if (!source.has(field)) return List.of();
        var result = new ArrayList<ConditionDefinition>();
        for (JsonElement element : source.getAsJsonArray(field)) result.add(parseCondition(element.getAsJsonObject()));
        return List.copyOf(result);
    }

    private static Map<String, String> primitiveArguments(JsonObject source, Set<String> reserved, String label) {
        var result = new LinkedHashMap<String, String>();
        for (var entry : source.entrySet()) {
            if (reserved.contains(entry.getKey())) continue;
            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive()) throw new IllegalArgumentException(label + "只能包含标量：" + entry.getKey());
            result.put(entry.getKey(), value.getAsString());
        }
        return result;
    }

    private static String requiredString(JsonObject source, String key, String label) {
        if (!source.has(key) || source.get(key).isJsonNull() || !source.get(key).isJsonPrimitive())
            throw new IllegalArgumentException(label + "缺失");
        String value = source.get(key).getAsString();
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "为空");
        return value;
    }

    private static String normalizeType(String value) {
        if (value == null) throw new IllegalArgumentException("Effect 类型为空");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9_-]*")) throw new IllegalArgumentException("Effect 类型无效：" + value);
        return normalized;
    }
}
