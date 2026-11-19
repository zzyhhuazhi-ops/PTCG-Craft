import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.effect.ConditionDefinition;
import dev.hua.ptcglive.engine.effect.ConditionEvaluator;
import dev.hua.ptcglive.engine.effect.ConditionType;
import dev.hua.ptcglive.engine.effect.CustomEffectRegistry;
import dev.hua.ptcglive.engine.effect.EffectConditionContext;
import dev.hua.ptcglive.engine.effect.EffectDefinition;
import dev.hua.ptcglive.engine.effect.EffectExecutionContext;
import dev.hua.ptcglive.engine.effect.EffectExecutionCapability;
import dev.hua.ptcglive.engine.effect.EffectExecutionCapabilityCatalog;
import dev.hua.ptcglive.engine.effect.EffectInterpreter;
import dev.hua.ptcglive.engine.effect.EffectProgram;
import dev.hua.ptcglive.engine.effect.EffectRegistry;
import dev.hua.ptcglive.engine.effect.EffectSchemaValidator;
import dev.hua.ptcglive.engine.effect.EffectTarget;
import dev.hua.ptcglive.engine.effect.EffectTargetContext;
import dev.hua.ptcglive.engine.effect.EffectTargetResolver;
import dev.hua.ptcglive.engine.effect.EffectTrigger;
import dev.hua.ptcglive.engine.effect.TriggeredEffects;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Regression contract for the card-agnostic Effect / Condition / Trigger architecture. */
public final class EffectArchitectureTest {
    public static void main(String[] args) {
        registryAndNestedParsing();
        conditions();
        targets();
        interpreterOrderAndAuthority();
        executionCapabilityClassification();
        customHandlersStayExplicit();
        emptyEffectsAreAnOrdinaryAttack();
        System.out.println("EffectArchitectureTest passed: reusable Effect, Condition, Target and Trigger composition is data-driven");
    }

    private static void emptyEffectsAreAnOrdinaryAttack() {
        var json = JsonParser.parseString("""
            {"id":"plain","nameZh":"撞击","energyCost":{"COLORLESS":1},"baseDamage":20,
             "damageType":"COLORLESS","effectSupport":"SUPPORTED","effects":[]}
            """).getAsJsonObject();
        var attack = dev.hua.ptcglive.rules.BundledCombatData.attack(json, EffectRegistry.standard());
        check(attack.effectProgram().isEmpty() && attack.baseDamage() == 20 && attack.energyCost().size() == 1,
            "普通攻击的空 effects 数组未加载为统一基础伤害攻击");
        json.remove("effects");
        check(attack.equals(dev.hua.ptcglive.rules.BundledCombatData.attack(json, EffectRegistry.standard())),
            "省略 effects 和空 effects 应具有相同语义");
    }

    private static void registryAndNestedParsing() {
        EffectRegistry registry = EffectRegistry.standard();
        for (String type : List.of("damage", "heal", "draw", "discard", "search_deck", "search_discard_pile", "attach_energy", "attach_tool",
            "discard_energy", "return_energy", "move_energy", "move_damage_counter", "discard_tool", "return_tool", "switch", "force_switch", "gust", "retreat", "poison", "burn", "paralyze", "sleep",
            "confuse", "clear_status", "coin_flip", "damage_counter", "bench_damage", "self_damage", "recoil", "damage_multiplier",
            "damage_per_energy", "damage_per_damage_counter", "damage_per_bench", "damage_per_prize", "damage_per_bound", "damage_per_hand_card", "opt_in", "retaliate", "prevent_trainer_play", "prevent_damage", "reduce_damage", "reduce_attack_damage", "boost_attack_damage", "increase_damage_taken", "attack_coin_gate", "lock_opponent_attack", "lock_this_attack",
            "return_to_hand", "shuffle_into_deck", "discard_pokemon", "knock_out", "devolve", "prevent_evolution", "prevent_attack", "prevent_retreat", "disable_abilities", "disable_tools", "discard_stadium", "discard_top_deck", "discard_random_hand", "shuffle_random_hand", "draw_until", "shuffle_hand", "reveal_hand", "copy_attack", "conditional", "choice", "repeat", "repeat_per_heads", "custom"))
            check(registry.supports(type), "标准 Effect Registry 缺少 " + type);
        EffectDefinition flip = registry.parse(JsonParser.parseString("""
            {"type":"coin_flip","flips":1,"heads":[
              {"type":"damage","target":"OPPONENT_ACTIVE","amount":30}
            ]}
            """).getAsJsonObject());
        check(flip.type().equals("coin_flip") && flip.effects().size() == 1
            && flip.effects().getFirst().type().equals("conditional")
            && flip.effects().getFirst().conditions().getFirst().type() == ConditionType.COIN_RESULT,
            "硬币正面嵌套 Effect 没有标准化为条件分支");
        rejects(() -> registry.parse(JsonParser.parseString("{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\"}").getAsJsonObject()));
        rejects(() -> registry.parse(JsonParser.parseString("{\"type\":\"heal\",\"target\":\"DECK\",\"amount\":20}").getAsJsonObject()));
        rejects(() -> EffectSchemaValidator.validate(registry.parse(JsonParser.parseString("{\"type\":\"devolve\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":3}").getAsJsonObject())));
        rejects(() -> registry.parse(JsonParser.parseString("{\"type\":\"prevent_evolution\",\"target\":\"OPPONENT_ACTIVE\",\"duration\":\"FOREVER\"}").getAsJsonObject()));
        rejects(() -> registry.parse(JsonParser.parseString("{\"type\":\"prevent_attack\",\"target\":\"OPPONENT_ACTIVE\",\"duration\":\"FOREVER\"}").getAsJsonObject()));
        rejects(() -> registry.parse(JsonParser.parseString("{\"type\":\"prevent_retreat\",\"target\":\"OPPONENT_ACTIVE\",\"effects\":[{\"type\":\"draw\",\"amount\":1}]}").getAsJsonObject()));
        rejects(() -> registry.parse(JsonParser.parseString("{\"type\":\"disable_abilities\",\"target\":\"OPPONENT_ACTIVE\",\"duration\":\"FOREVER\"}").getAsJsonObject()));
        rejects(() -> registry.parse(JsonParser.parseString("{\"type\":\"disable_tools\",\"target\":\"OPPONENT_ACTIVE\",\"effects\":[{\"type\":\"draw\",\"amount\":1}]}").getAsJsonObject()));
    }

    private static void conditions() {
        var context = new FakeContext(1, 1);
        var enoughEnergy = new ConditionDefinition(ConditionType.ENERGY_COUNT,
            Map.of("value", "2", "comparison", ">=", "target", "SELF"), List.of());
        var burned = new ConditionDefinition(ConditionType.HAS_STATUS,
            Map.of("status", "BURNED", "target", "SELF"), List.of());
        var notBurned = new ConditionDefinition(ConditionType.NOT, Map.of(), List.of(burned));
        ConditionDefinition nested = new ConditionDefinition(ConditionType.AND, Map.of(), List.of(
            enoughEnergy, notBurned));
        check(ConditionEvaluator.matches(nested, context), "AND / NOT / 数值条件没有按服务器状态组合");
        ConditionDefinition status = new ConditionDefinition(ConditionType.TARGET_HAS_STATUS,
            Map.of("status", "POISONED", "target", "OPPONENT_ACTIVE"), List.of());
        check(ConditionEvaluator.matches(status, context), "目标异常状态条件没有使用统一 Target");
        check(!ConditionEvaluator.matches(new ConditionDefinition(ConditionType.OR, Map.of(), List.of(
            new ConditionDefinition(ConditionType.HP_BELOW, Map.of("value", "20"), List.of()),
            new ConditionDefinition(ConditionType.IS_VSTAR, Map.of(), List.of()))), context), "错误 OR 条件被接受");
    }

    private static void interpreterOrderAndAuthority() {
        EffectRegistry registry = EffectRegistry.standard();
        EffectDefinition coin = registry.parse(JsonParser.parseString("""
            {"type":"coin_flip","flips":1,"heads":[{"type":"damage","target":"OPPONENT_ACTIVE","amount":30}],
             "tails":[{"type":"draw","target":"SELF","amount":2}]}
            """).getAsJsonObject());
        EffectDefinition repeated = registry.parse(JsonParser.parseString("""
            {"type":"repeat","count":2,"effects":[{"type":"damage_counter","target":"OPPONENT_ACTIVE","amount":1}]}
            """).getAsJsonObject());
        EffectDefinition choice = registry.parse(JsonParser.parseString("""
            {"type":"choice","effects":[{"type":"heal","target":"SELF","amount":10},{"type":"draw","target":"SELF","amount":1}]}
            """).getAsJsonObject());
        EffectProgram program = new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_ATTACK, List.of(), List.of(coin, repeated, choice))));
        var context = new FakeContext(1, 1);
        new EffectInterpreter(context).execute(program, EffectTrigger.ON_ATTACK);
        check(context.atomic.equals(List.of("damage", "damage_counter", "damage_counter", "draw")),
            "通用 Effect 解释器没有按硬币分支、Repeat 和服务器选择顺序执行");
        check(context.flips == 1 && context.choices == 1, "硬币或 Choice 没有经过服务器执行上下文");
    }

    private static void targets() {
        UUID a = new UUID(0, 1), b = new UUID(0, 2), source = new UUID(0, 3), ownActive = new UUID(0, 4), opponentActive = new UUID(0, 5);
        var context = new EffectTargetContext(a, b, source, ownActive, opponentActive,
            List.of(new UUID(0, 6), new UUID(0, 7)), List.of(new UUID(0, 8)));
        var self = EffectTargetResolver.resolve(EffectTarget.SELF, context);
        check(!self.requiresSelection() && self.fixedTargets().size() == 1, "SELF 没有解析为服务器已知来源宝可梦");
        var allOpponent = EffectTargetResolver.resolve(EffectTarget.ALL_OPPONENT_POKEMON, context);
        check(allOpponent.fixedTargets().size() == 2, "全体对方宝可梦目标没有包含战斗位和备战区");
        var deck = EffectTargetResolver.resolve(EffectTarget.DECK, context);
        check(deck.fixedTargets().getFirst() instanceof dev.hua.ptcglive.engine.effect.EffectTargetResolution.ZoneTarget,
            "隐藏区域目标没有保留为服务器区域引用");
        var selection = EffectTargetResolver.resolve(EffectTarget.SELECT_OPPONENT_POKEMON, context);
        check(selection.requiresSelection() && selection.fixedTargets().isEmpty(), "选择目标被错误转换为客户端可伪造的固定实例");
    }

    private static void executionCapabilityClassification() {
        EffectRegistry registry = EffectRegistry.standard();
        for (String type : registry.supportedTypes()) {
            if (type.equals("custom")) continue; // its handler is intentionally external.
            EffectDefinition candidate = switch (type) {
                case "quiz_select_pokemon", "quiz_select_printed_height" -> new EffectDefinition(type,EffectTarget.HAND,Map.of(),List.of(),List.of());
                case "bench_restored_from_hand" -> new EffectDefinition(type,EffectTarget.HAND,Map.of(),List.of(),List.of());
                case "prepare_distinct_bench_energy" -> registry.parse(JsonParser.parseString("{\"type\":\"prepare_distinct_bench_energy\",\"target\":\"OWN_BENCH\"}").getAsJsonObject());
                case "reveal_deck_window" -> registry.parse(JsonParser.parseString("{\"type\":\"reveal_deck_window\",\"target\":\"DECK\",\"amount\":7,\"position\":\"BOTTOM\",\"attack_id\":\"bug-out\",\"bind\":\"bugs\"}").getAsJsonObject());
                case "resolve_deck_window" -> registry.parse(JsonParser.parseString("{\"type\":\"resolve_deck_window\",\"target\":\"DECK\",\"ref\":\"bugs\",\"shuffle_category\":\"POKEMON\"}").getAsJsonObject());
                case "delayed_effect" -> registry.parse(JsonParser.parseString(DelayedPokemonEffectsTest.delayed(DelayedPokemonEffectsTest.COUNTERS)).getAsJsonObject());
                case "protect_on_attack_damage_ko" -> registry.parse(JsonParser.parseString(AttackDamageKoProtectionTest.GUARD).getAsJsonObject());
                case "discard_source", "discard_hand" -> registry.parse(JsonParser.parseString("{\"type\":\""+type+"\"}").getAsJsonObject());
                case "coin_flip" -> registry.parse(JsonParser.parseString("{\"type\":\"coin_flip\",\"flips\":1,\"heads\":[{\"type\":\"draw\",\"amount\":1}]}" ).getAsJsonObject());
                case "conditional" -> registry.parse(JsonParser.parseString("{\"type\":\"conditional\",\"conditions\":[{\"type\":\"IS_BASIC\"}],\"effects\":[{\"type\":\"draw\",\"amount\":1}]}" ).getAsJsonObject());
                case "choice" -> registry.parse(JsonParser.parseString("{\"type\":\"choice\",\"effects\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"draw\",\"amount\":1}]}" ).getAsJsonObject());
                case "repeat" -> registry.parse(JsonParser.parseString("{\"type\":\"repeat\",\"count\":1,\"effects\":[{\"type\":\"draw\",\"amount\":1}]}" ).getAsJsonObject());
                case "repeat_per_heads" -> registry.parse(JsonParser.parseString("{\"type\":\"coin_flip\",\"flips\":1,\"effects\":[{\"type\":\"repeat_per_heads\",\"effects\":[{\"type\":\"draw\",\"amount\":1}]}]}" ).getAsJsonObject()).effects().getFirst();
                case "select_pokemon" -> registry.parse(JsonParser.parseString("{\"type\":\"select_pokemon\",\"target\":\"SELECT_OWN_POKEMON\",\"effects\":[{\"type\":\"heal\",\"target\":\"CHOSEN_POKEMON\",\"amount\":10}]}").getAsJsonObject());
                case "with_pokemon" -> registry.parse(JsonParser.parseString(AttackPokemonBindingsTest.use("victim",AttackPokemonBindingsTest.counters(1))).getAsJsonObject());
                case "special_condition_immunity" -> registry.parse(JsonParser.parseString("{\"type\":\"special_condition_immunity\",\"statuses\":\"ALL\"}").getAsJsonObject());
                case "no_weakness" -> registry.parse(JsonParser.parseString("{\"type\":\"no_weakness\"}").getAsJsonObject());
                case "free_retreat" -> registry.parse(JsonParser.parseString("{\"type\":\"free_retreat\"}").getAsJsonObject());
                case "retreat_cost_modifier", "attack_cost_modifier", "tool_capacity", "prize_card_modifier" -> registry.parse(JsonParser.parseString("{\"type\":\""+type+"\",\"amount\":1}").getAsJsonObject());
                case "attack_cost_override" -> registry.parse(JsonParser.parseString("{\"type\":\"attack_cost_override\",\"energy_type\":\"GRASS\",\"amount\":1}").getAsJsonObject());
                case "prevent_checkup_recovery" -> registry.parse(JsonParser.parseString("{\"type\":\"prevent_checkup_recovery\",\"target\":\"OPPONENT_ACTIVE\",\"status\":\"BURNED\"}").getAsJsonObject());
                case "passive_effect_guard" -> registry.parse(JsonParser.parseString("{\"type\":\"passive_effect_guard\",\"source\":\"ABILITY\",\"from\":\"OPPONENT\",\"scope\":\"DAMAGE_AND_EFFECTS\"}").getAsJsonObject());
                case "passive_attack_guard" -> registry.parse(JsonParser.parseString("{\"type\":\"passive_attack_guard\",\"mode\":\"PREVENT\",\"attacker_filter\":\"BASIC\"}").getAsJsonObject());
                case "attack_damage_modifier" -> registry.parse(JsonParser.parseString("{\"type\":\"attack_damage_modifier\",\"direction\":\"DEALT\",\"amount\":10}").getAsJsonObject());
                case "hit_points_modifier" -> registry.parse(JsonParser.parseString("{\"type\":\"hit_points_modifier\",\"amount\":10}").getAsJsonObject());
                case "special_energy_override" -> registry.parse(JsonParser.parseString("{\"type\":\"special_energy_override\",\"target\":\"ALL_OWN_POKEMON\",\"amount\":1,\"energy_type\":\"COLORLESS\",\"suppress_other_effects\":true}").getAsJsonObject());
                case "suppress_abilities" -> registry.parse(JsonParser.parseString("{\"type\":\"suppress_abilities\",\"scope\":\"ALL_IN_PLAY\"}").getAsJsonObject());
                case "prevent_effects" -> registry.parse(JsonParser.parseString("{\"type\":\"prevent_effects\",\"target\":\"OWN_ACTIVE\",\"source\":\"ABILITY\",\"from\":\"OPPONENT\",\"scope\":\"EFFECTS_ONLY\",\"duration\":\"SOURCE_NEXT_TURN_START\"}").getAsJsonObject());
                case "discard_energy", "return_energy", "discard_tool", "return_tool" -> registry.parse(JsonParser.parseString("{\"type\":\"" + type + "\",\"target\":\"OWN_ACTIVE\",\"amount\":1,\"minimum\":1}" ).getAsJsonObject());
                case "plan_energy_discard" -> registry.parse(JsonParser.parseString("{\"type\":\"plan_energy_discard\",\"target\":\"SELF\",\"amount\":1,\"minimum\":0,\"bind\":\"chosen\"}").getAsJsonObject());
                case "commit_energy_discard" -> registry.parse(JsonParser.parseString("{\"type\":\"commit_energy_discard\",\"ref\":\"chosen\"}").getAsJsonObject());
                case "move_energy" -> registry.parse(JsonParser.parseString("{\"type\":\"move_energy\",\"target\":\"OWN_ACTIVE\",\"amount\":1,\"minimum\":1,\"destination_scope\":\"OWN_BENCH\"}" ).getAsJsonObject());
                case "move_damage_counter" -> registry.parse(JsonParser.parseString("{\"type\":\"move_damage_counter\",\"target\":\"OWN_ACTIVE\",\"destination\":\"OPPONENT_ACTIVE\",\"amount\":1}" ).getAsJsonObject());
                case "retreat" -> registry.parse(JsonParser.parseString("{\"type\":\"retreat\",\"target\":\"SELECT_OWN_POKEMON\",\"mode\":\"FREE\"}" ).getAsJsonObject());
                case "switch", "force_switch", "gust", "poison", "burn", "paralyze", "sleep", "confuse", "prevent_damage", "knock_out", "return_to_hand", "shuffle_into_deck", "discard_pokemon", "devolve", "prevent_evolution", "prevent_attack", "prevent_retreat", "disable_abilities", "disable_tools" ->
                    registry.parse(JsonParser.parseString("{\"type\":\"" + type + "\",\"target\":\"OPPONENT_ACTIVE\"}" ).getAsJsonObject());
                case "copy_attack" -> registry.parse(JsonParser.parseString("{\"type\":\"copy_attack\",\"target\":\"OPPONENT_ACTIVE\",\"cost_policy\":\"DECLARED_ATTACK\"}").getAsJsonObject());
                case "clear_status" -> registry.parse(JsonParser.parseString("{\"type\":\"clear_status\",\"target\":\"OWN_ACTIVE\",\"statuses\":\"ALL\"}" ).getAsJsonObject());
                case "lock_this_attack" -> registry.parse(JsonParser.parseString("{\"type\":\"lock_this_attack\",\"target\":\"SELF\"}" ).getAsJsonObject());
                case "survive_attack_damage" -> registry.parse(JsonParser.parseString("{\"type\":\"survive_attack_damage\",\"target\":\"SELF\",\"remaining_hp\":10}").getAsJsonObject());
                case "extra_prize_on_attack_ko" -> registry.parse(JsonParser.parseString("{\"type\":\"extra_prize_on_attack_ko\",\"target\":\"SELF\",\"amount\":1,\"basic_only\":true}").getAsJsonObject());
                case "prevent_hand_energy" -> registry.parse(JsonParser.parseString("{\"type\":\"prevent_hand_energy\",\"target\":\"OPPONENT_ACTIVE\",\"scope\":\"DEFENDING_POKEMON\",\"card_kind\":\"ANY\"}").getAsJsonObject());
                case "prevent_trainer_play" -> registry.parse(JsonParser.parseString("{\"type\":\"prevent_trainer_play\",\"target\":\"OPPONENT_ACTIVE\",\"trainer_category\":\"ITEM\"}" ).getAsJsonObject());
                case "replace_basic_from_discard" -> registry.parse(JsonParser.parseString("{\"type\":\"replace_basic_from_discard\",\"target\":\"CHOSEN_POKEMON\",\"amount\":1,\"minimum\":1,\"card_category\":\"POKEMON\",\"basic_pokemon_only\":true,\"preserve_state\":\"ALL\",\"return_old_card\":\"DISCARD\"}").getAsJsonObject());
                case "replace_pokemon_from_deck" -> registry.parse(JsonParser.parseString("{\"type\":\"replace_pokemon_from_deck\",\"target\":\"SELF\",\"amount\":1,\"minimum\":0,\"card_category\":\"POKEMON\",\"preserve_state\":\"ALL\",\"return_old_card\":\"DECK_SHUFFLED\"}").getAsJsonObject());
                case "trainer_attempt_coin_gate" -> registry.parse(JsonParser.parseString("{\"type\":\"trainer_attempt_coin_gate\",\"target\":\"OPPONENT_ACTIVE\",\"scope\":\"PLAYER\",\"source\":\"HAND\",\"on_tails\":\"DISCARD_INSTEAD_OF_USE\",\"duration\":\"OPPONENT_NEXT_TURN\"}").getAsJsonObject());
                case "borrow_attacks" -> registry.parse(JsonParser.parseString("{\"type\":\"borrow_attacks\",\"target\":\"SELF\",\"source_scope\":\"OWN_BENCH\",\"cost_policy\":\"SELECTED_ATTACK\"}").getAsJsonObject());
                case "retaliate" -> registry.parse(JsonParser.parseString("{\"type\":\"retaliate\",\"target\":\"SELF\",\"amount\":1}" ).getAsJsonObject());
                case "opt_in" -> registry.parse(JsonParser.parseString("{\"type\":\"opt_in\",\"bind\":\"more\"}" ).getAsJsonObject());
                case "damage_per_bound" -> registry.parse(JsonParser.parseString("{\"type\":\"damage_per_bound\",\"target\":\"OPPONENT_ACTIVE\",\"bind\":\"paid\",\"amount\":10}" ).getAsJsonObject());
                case "shuffle_hand" -> registry.parse(JsonParser.parseString("{\"type\":\"shuffle_hand\",\"target\":\"SELF\"}" ).getAsJsonObject());
                case "damage_per_hand_card" -> registry.parse(JsonParser.parseString("{\"type\":\"damage_per_hand_card\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":10,\"card_category\":\"TRAINER\"}" ).getAsJsonObject());
                case "evolve_from_hand" -> registry.parse(JsonParser.parseString("{\"type\":\"evolve_from_hand\",\"target\":\"SELF\",\"amount\":1,\"minimum\":1,\"card_category\":\"POKEMON\",\"timing\":\"AFTER_OWNER_FIRST_TURN\",\"bind\":\"evolved\"}").getAsJsonObject());
                case "reveal_hand" -> registry.parse(JsonParser.parseString("{\"type\":\"reveal_hand\",\"target\":\"OPPONENT_ACTIVE\"}" ).getAsJsonObject());
                case "discard_stadium" -> registry.parse(JsonParser.parseString("{\"type\":\"discard_stadium\"}" ).getAsJsonObject());
                case "lock_opponent_attack" -> registry.parse(JsonParser.parseString("{\"type\":\"lock_opponent_attack\",\"target\":\"OPPONENT_ACTIVE\"}" ).getAsJsonObject());
                case "attack_coin_gate" -> registry.parse(JsonParser.parseString("{\"type\":\"attack_coin_gate\",\"target\":\"OPPONENT_ACTIVE\"}" ).getAsJsonObject());
                case "boost_attack_damage" -> registry.parse(JsonParser.parseString("{\"type\":\"boost_attack_damage\",\"target\":\"SELF\",\"amount\":10}" ).getAsJsonObject());
                case "increase_damage_taken" -> registry.parse(JsonParser.parseString("{\"type\":\"increase_damage_taken\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":10}" ).getAsJsonObject());
                case "modify_damage_taken" -> registry.parse(JsonParser.parseString("{\"type\":\"modify_damage_taken\",\"target\":\"SELF\",\"amount\":-100,\"duration\":\"OPPONENT_NEXT_TURN\",\"attacker_filter\":\"EVOLUTION\"}").getAsJsonObject());
                case "ignore_resistance" -> registry.parse(JsonParser.parseString("{\"type\":\"ignore_resistance\"}").getAsJsonObject());
                case "weakness_override" -> registry.parse(JsonParser.parseString("{\"type\":\"weakness_override\",\"target\":\"OPPONENT_ACTIVE\",\"energy_type\":\"LIGHTNING\",\"multiplier\":2,\"duration\":\"OWNER_NEXT_TURN_END\"}").getAsJsonObject());
                case "increase_attack_cost" -> registry.parse(JsonParser.parseString("{\"type\":\"increase_attack_cost\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":2}").getAsJsonObject());
                case "reduce_attack_damage" -> registry.parse(JsonParser.parseString("{\"type\":\"reduce_attack_damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":10}" ).getAsJsonObject());
                case "discard_top_deck", "discard_random_hand", "shuffle_random_hand", "draw_until" -> registry.parse(JsonParser.parseString("{\"type\":\"" + type + "\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}" ).getAsJsonObject());
                case "attach_tool" -> registry.parse(JsonParser.parseString("{\"type\":\"attach_tool\",\"target\":\"OWN_ACTIVE\",\"source\":\"HAND\",\"amount\":1,\"minimum\":1,\"target_scope\":\"OWN_ACTIVE\"}" ).getAsJsonObject());
                case "damage", "heal", "draw", "discard", "search_deck", "search_discard_pile", "attach_energy", "damage_counter", "bench_damage", "self_damage", "recoil", "damage_multiplier", "damage_per_energy", "damage_per_damage_counter", "damage_per_bench", "damage_per_prize", "reduce_damage" ->
                    registry.parse(JsonParser.parseString("{\"type\":\"" + type + "\",\"amount\":1}" ).getAsJsonObject());
                case "inspect_top_deck" -> registry.parse(JsonParser.parseString("{\"type\":\"inspect_top_deck\",\"target\":\"DECK\",\"top_count\":1,\"amount\":1,\"minimum\":0}").getAsJsonObject());
                case "swap_hand_top_deck" -> registry.parse(JsonParser.parseString("{\"type\":\"swap_hand_top_deck\",\"target\":\"HAND\",\"amount\":1,\"minimum\":1}").getAsJsonObject());
                case "damage_from_last_opponent_attack" -> registry.parse(JsonParser.parseString("{\"type\":\"damage_from_last_opponent_attack\",\"target\":\"SELF\"}").getAsJsonObject());
                case "take_prize_cards" -> registry.parse(JsonParser.parseString("{\"type\":\"take_prize_cards\",\"target\":\"SELF\",\"amount\":2,\"bind\":\"prizes\"}").getAsJsonObject());
                case "opponent_hand_to_bottom" -> registry.parse(JsonParser.parseString("{\"type\":\"opponent_hand_to_bottom\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1,\"trainer_category\":\"ITEM\"}").getAsJsonObject());
                case "opponent_choose_discard" -> registry.parse(JsonParser.parseString("{\"type\":\"opponent_choose_discard\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}").getAsJsonObject());
                case "swap_prizes_hand" -> registry.parse(JsonParser.parseString("{\"type\":\"swap_prizes_hand\",\"target\":\"SELF\",\"amount\":3,\"minimum\":1}").getAsJsonObject());
                case "turn_gx_permission" -> registry.parse(JsonParser.parseString("{\"type\":\"turn_gx_permission\",\"target\":\"SELF\",\"pokemon_type\":\"WATER\"}").getAsJsonObject());
                case "turn_attack_bonus" -> registry.parse(JsonParser.parseString("{\"type\":\"turn_attack_bonus\",\"target\":\"SELF\",\"amount\":20}").getAsJsonObject());
                case "turn_damage_reduction" -> registry.parse(JsonParser.parseString("{\"type\":\"turn_damage_reduction\",\"target\":\"SELF\",\"amount\":30,\"pokemon_types\":\"METAL\",\"duration\":\"OPPONENT_NEXT_TURN\"}").getAsJsonObject());
                case "recover_all_discard" -> registry.parse(JsonParser.parseString("{\"type\":\"recover_all_discard\",\"target\":\"DISCARD\",\"card_category\":\"ENERGY\",\"basic_energy_only\":true,\"destination\":\"DECK_SHUFFLED\"}").getAsJsonObject());
                case "damage_per_discard_card" -> registry.parse(JsonParser.parseString("{\"type\":\"damage_per_discard_card\",\"target\":\"OPPONENT_ACTIVE\",\"card_category\":\"TRAINER\",\"trainer_category\":\"SUPPORTER\",\"amount\":40}").getAsJsonObject());
                case "damage_per_pokemon" -> registry.parse(JsonParser.parseString("{\"type\":\"damage_per_pokemon\",\"target\":\"OWN_BENCH\",\"amount\":20}").getAsJsonObject());
                case "prevent_healing" -> registry.parse(JsonParser.parseString("{\"type\":\"prevent_healing\",\"target\":\"OPPONENT_ACTIVE\"}").getAsJsonObject());
                case "prevent_damage_counter_move" -> registry.parse(JsonParser.parseString("{\"type\":\"prevent_damage_counter_move\",\"target\":\"ALL_OWN_POKEMON\"}").getAsJsonObject());
                case "weakness_multiplier" -> registry.parse(JsonParser.parseString("{\"type\":\"weakness_multiplier\",\"target\":\"OWN_ACTIVE\",\"amount\":3}").getAsJsonObject());
                case "reroll_attack_coins" -> registry.parse(JsonParser.parseString("{\"type\":\"reroll_attack_coins\",\"target\":\"SELF\",\"pokemon_type\":\"COLORLESS\"}").getAsJsonObject());
                case "unrecoverable_from_discard" -> registry.parse(JsonParser.parseString("{\"type\":\"unrecoverable_from_discard\",\"target\":\"SELF\"}").getAsJsonObject());
                case "disable_all_tools" -> registry.parse(JsonParser.parseString("{\"type\":\"disable_all_tools\",\"target\":\"SELF\"}").getAsJsonObject());
                case "tera_bench_expansion" -> registry.parse(JsonParser.parseString("{\"type\":\"tera_bench_expansion\",\"target\":\"SELF\",\"expanded_limit\":8,\"normal_limit\":5,\"leave_limit\":5}").getAsJsonObject());
                default -> null;
            };
            // Registered parser vocabulary is larger than the executable core because it also
            // contains data-migration placeholders. Dedicated rule tests own those fixtures.
            if (candidate == null) continue;
            try {
                check(EffectExecutionCapabilityCatalog.assess(candidate).capability() != null, "Effect 没有执行能力分类：" + type);
            } catch (RuntimeException invalidFixture) {
                throw new AssertionError("Effect 执行能力样例无效：" + type, invalidFixture);
            }
        }
        EffectDefinition direct = registry.parse(JsonParser.parseString("{\"type\":\"draw\",\"amount\":1}" ).getAsJsonObject());
        EffectDefinition attack = registry.parse(JsonParser.parseString("{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":10}" ).getAsJsonObject());
        EffectDefinition choice = registry.parse(JsonParser.parseString("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1}" ).getAsJsonObject());
        check(EffectExecutionCapabilityCatalog.assess(direct).capability() == EffectExecutionCapability.DIRECT_SERVER,
            "直接服务器 Effect 被错误分类");
        check(EffectExecutionCapabilityCatalog.assess(attack).capability() == EffectExecutionCapability.REQUIRES_ATTACK_CONTEXT,
            "攻击伤害 Effect 被错误分类");
        check(EffectExecutionCapabilityCatalog.assess(choice).capability() == EffectExecutionCapability.REQUIRES_SELECTION,
            "牌库检索 Effect 被错误分类");
    }

    private static void customHandlersStayExplicit() {
        EffectRegistry registry = EffectRegistry.standard();
        String custom = "{\"type\":\"custom\",\"handler\":\"unique_rule\"}";
        rejects(() -> registry.parse(JsonParser.parseString(custom).getAsJsonObject()));
        CustomEffectRegistry handlers = registry.customEffects();
        handlers.register("unique_rule", effect -> {
            if (effect.effects().size() > 1) throw new IllegalArgumentException("测试 CustomEffect 不能带多分支");
        });
        check(registry.parse(JsonParser.parseString(custom).getAsJsonObject()).isCustom(), "已注册 CustomEffect 未被解析");
    }

    private static final class FakeContext implements EffectExecutionContext {
        final List<String> atomic = new ArrayList<>();
        final int heads;
        int flips;
        int choices;
        FakeContext(int heads, int ignored) { this.heads = heads; }
        @Override public int number(ConditionType type, EffectTarget target, Map<String, String> arguments) {
            return switch (type) {
                case ENERGY_COUNT, HAS_ENERGY -> 2;
                case HP_BELOW, HP_ABOVE -> 50;
                case DAMAGE_COUNTER_COUNT -> 3;
                case BENCH_COUNT -> 2;
                case HAND_SIZE -> 4;
                case PRIZE_COUNT -> 6;
                case DISCARD_COUNT -> 5;
                default -> 0;
            };
        }
        @Override public boolean hasStatus(EffectTarget target, String status) { return target == EffectTarget.OPPONENT_ACTIVE && status.equals("POISONED"); }
        @Override public boolean flag(ConditionType type, EffectTarget target, Map<String, String> arguments) { return false; }
        @Override public boolean coinResult(String expected) { return expected.equals("HEADS") && heads > 0; }
        @Override public boolean customCondition(ConditionDefinition condition) { return false; }
        @Override public CoinResult flipCoins(EffectDefinition source, int flips) { this.flips += flips; return new CoinResult(flips, heads); }
        @Override public int choose(EffectDefinition source, int optionCount) { choices++; return optionCount - 1; }
        @Override public void executeAtomic(EffectDefinition effect) { atomic.add(effect.type()); }
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("未注册 CustomEffect 被错误接受");
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
