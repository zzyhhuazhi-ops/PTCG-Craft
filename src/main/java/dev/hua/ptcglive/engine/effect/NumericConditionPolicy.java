package dev.hua.ptcglive.engine.effect;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One data contract for numeric comparisons. In addition to a literal value,
 * player-zone counts can compare against the other player's same count. The
 * opponent fact always comes from the authoritative match context.
 */
public final class NumericConditionPolicy {
    private static final Set<ConditionType> NUMERIC = EnumSet.of(ConditionType.RETREAT_COST, ConditionType.POKEMON_COUNT, ConditionType.ENERGY_COUNT,
        ConditionType.DAMAGE_COUNTER_COUNT, ConditionType.BENCH_COUNT, ConditionType.HAND_SIZE,
        ConditionType.PRIZE_COUNT, ConditionType.DISCARD_COUNT,ConditionType.LOST_ZONE_COUNT,ConditionType.DECK_SIZE);
    private static final Set<ConditionType> OPPONENT_PLAYER_COUNTS = EnumSet.of(ConditionType.ENERGY_COUNT,
        ConditionType.BENCH_COUNT, ConditionType.HAND_SIZE, ConditionType.PRIZE_COUNT, ConditionType.DISCARD_COUNT, ConditionType.LOST_ZONE_COUNT);
    private static final Set<EffectTarget> PUBLIC_COMPARE_TARGETS = EnumSet.of(EffectTarget.SELF,
        EffectTarget.CHOSEN_POKEMON, EffectTarget.TRIGGER_POKEMON, EffectTarget.ATTACKING_POKEMON,
        EffectTarget.OWN_ACTIVE, EffectTarget.OPPONENT_ACTIVE, EffectTarget.OWN_BENCH,
        EffectTarget.OPPONENT_BENCH, EffectTarget.ALL_OWN_POKEMON, EffectTarget.ALL_OPPONENT_POKEMON);

    private NumericConditionPolicy() { }
    public static boolean handles(ConditionType type) { return NUMERIC.contains(type); }

    /** Reject ambiguous values and unsupported hidden/selection comparison targets before an Effect can run. */
    public static void validate(ConditionDefinition condition) {
        if (!handles(condition.type())) return;
        Map<String, String> args = condition.arguments();
        if(condition.type()==ConditionType.RETREAT_COST) {
            if(!allowedArguments(condition.type()).containsAll(args.keySet())
                ||!Set.of(EffectTarget.SELF,EffectTarget.OWN_ACTIVE,EffectTarget.OPPONENT_ACTIVE).contains(target(args))
                ||integer(args.get("value"))<0||integer(args.get("value"))>20)
                throw new IllegalArgumentException("撤退费用条件必须指定自身或战斗位、0至20的value和可选比较符");
        }
        if(condition.type()==ConditionType.POKEMON_COUNT) {
            if(!allowedArguments(condition.type()).containsAll(args.keySet())
                ||!args.containsKey("pokemon_names")&&!args.containsKey("name_contains")
                    &&!args.containsKey("pokemon_families")&&!args.containsKey("pokemon_types")&&!args.containsKey("pokemon_stage")&&!args.containsKey("has_abilities"))
                throw new IllegalArgumentException("场上计数须提供名称、家族、属性或阶段筛选，且不接受相对比较");
            if(args.containsKey("pokemon_stage")) {
                int stage=integer(args.get("pokemon_stage"));
                if(stage<0||stage>2)throw new IllegalArgumentException("场上计数的宝可梦阶段须为0至2");
            }
            PokemonCountEffect.fieldQuery(args);
            if(args.containsKey("has_abilities")&&!"true".equals(args.get("has_abilities")))throw new IllegalArgumentException("场上特性计数须明确true");
            PokemonCountEffect.validateNameCountTarget(target(args));
        }
        if(condition.type()==ConditionType.DISCARD_COUNT&&args.containsKey("ability_id")) {
            if(!allowedArguments(condition.type()).containsAll(args.keySet())||!args.get("ability_id").matches("[a-z0-9]+(?:-[a-z0-9]+)*"))
                throw new IllegalArgumentException("弃牌区特性计数须使用有效印刷特性ID");
            if(!Set.of(EffectTarget.SELF,EffectTarget.OWN_ACTIVE,EffectTarget.OPPONENT_ACTIVE).contains(target(args))
                ||args.containsKey("compare_target")&&!Set.of(EffectTarget.SELF,EffectTarget.OWN_ACTIVE,EffectTarget.OPPONENT_ACTIVE).contains(EffectTarget.fromWire(args.get("compare_target"))))
                throw new IllegalArgumentException("弃牌区特性计数须明确一方玩家");
        }
        if(condition.type()==ConditionType.DISCARD_COUNT&&"POKEMON".equals(args.get("card_category"))
            &&(!Set.of("target","value","comparison","card_category").containsAll(args.keySet())
                ||!Set.of(EffectTarget.SELF,EffectTarget.OPPONENT_ACTIVE).contains(target(args))))
            throw new IllegalArgumentException("弃牌区宝可梦计数须明确一方玩家，不能混合其他筛选");
        if(condition.type()==ConditionType.DISCARD_COUNT&&args.containsKey("name_contains")) {
            if(!allowedArguments(condition.type()).containsAll(args.keySet())||args.get("name_contains").isBlank()
                ||args.get("name_contains").length()>64||!Set.of(EffectTarget.SELF,EffectTarget.OPPONENT_ACTIVE).contains(target(args)))
                throw new IllegalArgumentException("弃牌区名称计数须明确一方玩家及非空名称片段");
        }
        if(condition.type()==ConditionType.DISCARD_COUNT&&args.containsKey("definition_ids")) {
            if(!allowedArguments(condition.type()).containsAll(args.keySet())
                || !args.get("definition_ids").matches("[a-z0-9][a-z0-9_|:-]*"))
                throw new IllegalArgumentException("弃牌区卡牌计数须使用有效卡牌编号");
            if(!Set.of(EffectTarget.SELF,EffectTarget.OWN_ACTIVE,EffectTarget.OPPONENT_ACTIVE).contains(target(args)))
                throw new IllegalArgumentException("弃牌区卡牌计数须明确一方玩家");
        }
        if(condition.type()==ConditionType.DISCARD_COUNT&&args.containsKey("trainer_category")) {
            if(!"TRAINER".equals(args.get("card_category"))
                ||!Set.of("SUPPORTER","STADIUM").contains(args.get("trainer_category"))
                ||!Set.of(EffectTarget.SELF,EffectTarget.OPPONENT_ACTIVE).contains(target(args)))
                throw new IllegalArgumentException("弃牌区训练家计数须明确玩家和支援者或场地分类");
        }
        boolean targetComparison = args.containsKey("compare_target");
        boolean opponentComparison = args.containsKey("compare_to");
        if (targetComparison && opponentComparison)
            throw new IllegalArgumentException("数值条件不能同时声明 compare_target 与 compare_to");
        if ((targetComparison || opponentComparison) == args.containsKey("value"))
            throw new IllegalArgumentException("数值条件必须二选一：value 或相对比较目标");
        if (targetComparison) {
            EffectTarget target = EffectTarget.fromWire(args.get("compare_target"));
            if (!PUBLIC_COMPARE_TARGETS.contains(target))
                throw new IllegalArgumentException("数值条件比较目标必须是服务器可验证的公开目标");
        }
        if (opponentComparison) {
            if (!OPPONENT_PLAYER_COUNTS.contains(condition.type()))
                throw new IllegalArgumentException("compare_to=OPPONENT 仅支持玩家区域数量条件");
            if (!"OPPONENT".equals(args.get("compare_to").trim().toUpperCase(Locale.ROOT)))
                throw new IllegalArgumentException("数值条件的 compare_to 目前仅支持 OPPONENT");
        }
        if (!targetComparison && !opponentComparison) {
            int value = integer(args.get("value"));
            if (value < -10_000 || value > 10_000) throw new IllegalArgumentException("条件 value 超出范围");
        }
        comparison(args.getOrDefault("comparison", ">="));
    }

    public static boolean matches(ConditionDefinition condition, EffectConditionContext context) {
        validate(condition);
        Map<String, String> args = condition.arguments();
        int left = context.number(condition.type(), target(args), args);
        int right = args.containsKey("compare_target")
            ? context.number(condition.type(), EffectTarget.fromWire(args.get("compare_target")), args)
            : args.containsKey("compare_to") ? context.opponentNumber(condition.type(), args) : integer(args.get("value"));
        return comparison(args.getOrDefault("comparison", ">=")).test(left, right);
    }

    public static Set<String> allowedArguments(ConditionType type) {
        if(type==ConditionType.RETREAT_COST)return Set.of("target","value","comparison");
        if(type==ConditionType.POKEMON_COUNT)return Set.of("target","value","comparison","pokemon_names","name_contains","pokemon_families","family_match","pokemon_types","pokemon_stage","damage_counters_min","has_abilities");
        if(type==ConditionType.DISCARD_COUNT)return Set.of("target","value","comparison","compare_target","compare_to","ability_id","definition_ids","name_contains","card_category","trainer_category","basic_energy_only","energy_type");
        if (type == ConditionType.ENERGY_COUNT)
            return Set.of("target", "value", "comparison", "compare_target", "compare_to", "energy_type", "count_mode", "card_kind", "definition_ids");
        return Set.of("target", "value", "comparison", "compare_target", "compare_to");
    }

    public static Comparison comparison(String raw) {
        try { return Comparison.valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) {
            return switch (raw.trim()) {
                case "=", "==" -> Comparison.EQUALS;
                case ">" -> Comparison.GREATER_THAN;
                case ">=" -> Comparison.AT_LEAST;
                case "<" -> Comparison.LESS_THAN;
                case "<=" -> Comparison.AT_MOST;
                default -> throw new IllegalArgumentException("未知条件比较符：" + raw);
            };
        }
    }

    private static EffectTarget target(Map<String, String> args) {
        return EffectTarget.fromWire(args.getOrDefault("target", "SELF"));
    }
    private static int integer(String raw) {
        try { return Integer.parseInt(raw); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("条件参数不是整数：value", invalid); }
    }

    public enum Comparison {
        EQUALS { public boolean test(int left, int right) { return left == right; } },
        GREATER_THAN { public boolean test(int left, int right) { return left > right; } },
        AT_LEAST { public boolean test(int left, int right) { return left >= right; } },
        LESS_THAN { public boolean test(int left, int right) { return left < right; } },
        AT_MOST { public boolean test(int left, int right) { return left <= right; } };
        public abstract boolean test(int left, int right);
    }
}
