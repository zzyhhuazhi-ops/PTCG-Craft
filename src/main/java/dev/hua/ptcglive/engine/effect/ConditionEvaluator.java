package dev.hua.ptcglive.engine.effect;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** Stateless evaluator for nested AND/OR/NOT and the reusable atomic condition set. */
public final class ConditionEvaluator {
    private ConditionEvaluator() { }

    public static boolean matches(ConditionDefinition condition, EffectConditionContext context) {
        return matches(condition,context,candidate -> !(context instanceof EffectExecutionContext execution) || execution.conditionAvailable(candidate));
    }
    /** Absent targets are unknown, not false facts; a known OR alternative can still succeed. */
    public static boolean matches(ConditionDefinition condition, EffectConditionContext context, Predicate<ConditionDefinition> available) {
        Objects.requireNonNull(condition, "条件");
        Objects.requireNonNull(context, "条件上下文");
        return evaluate(condition,context,Objects.requireNonNull(available))==Truth.TRUE;
    }
    private enum Truth { TRUE, FALSE, UNAVAILABLE }
    private static Truth evaluate(ConditionDefinition condition,EffectConditionContext context,Predicate<ConditionDefinition> available) {
        if(condition.type()==ConditionType.NOT)return switch(evaluate(condition.children().getFirst(),context,available)) {
            case TRUE -> Truth.FALSE;
            case FALSE -> Truth.TRUE;
            case UNAVAILABLE -> Truth.UNAVAILABLE;
        };
        if(condition.type()==ConditionType.AND||condition.type()==ConditionType.OR) {
            boolean conjunction=condition.type()==ConditionType.AND,unknown=false;
            for(var child:condition.children()) {
                var value=evaluate(child,context,available);
                if(value==Truth.UNAVAILABLE)unknown=true;
                else if(conjunction&&value==Truth.FALSE)return Truth.FALSE;
                else if(!conjunction&&value==Truth.TRUE)return Truth.TRUE;
            }
            return unknown?Truth.UNAVAILABLE:conjunction?Truth.TRUE:Truth.FALSE;
        }
        if(!available.test(condition))return Truth.UNAVAILABLE;
        if(condition.type()==ConditionType.COIN_RESULT&&condition.arguments().containsKey("coin")) {
            var result=context.namedCoinResult(condition.arguments().get("coin"));
            if(condition.arguments().containsKey("tails"))
                return result==null?Truth.UNAVAILABLE:result.flips()-result.heads()==integer(condition.arguments(),"tails")?Truth.TRUE:Truth.FALSE;
            return result==null?Truth.UNAVAILABLE:result.matches(required(condition.arguments(),"value"))?Truth.TRUE:Truth.FALSE;
        }
        return atomicMatches(condition,context)?Truth.TRUE:Truth.FALSE;
    }
    private static boolean atomicMatches(ConditionDefinition condition,EffectConditionContext context) {
        return switch (condition.type()) {
            case AND, OR, NOT -> throw new IllegalStateException("逻辑条件必须递归求值");
            case HAS_STATUS, TARGET_HAS_STATUS -> hasStatus(context, target(condition), required(condition.arguments(), "status"));
            case COIN_RESULT -> context.coinResult(required(condition.arguments(), "value"));
            case BOUND_COUNT -> NumericConditionPolicy.comparison(condition.arguments().getOrDefault("comparison", ">="))
                .test(context.boundCount(required(condition.arguments(), "bind")), integer(condition.arguments(), "value"));
            case HP_BELOW -> context.number(condition.type(), target(condition), condition.arguments()) < integer(condition.arguments(), "value");
            case HP_ABOVE -> context.number(condition.type(), target(condition), condition.arguments()) > integer(condition.arguments(), "value");
            case HAS_EXTRA_ATTACK_ENERGY -> context.hasExtraAttackEnergy(ExtraAttackEnergyRequirement.from(condition));
            case HAS_ENERGY -> context.number(condition.type(), target(condition), condition.arguments()) > 0;
            case RETREAT_COST, POKEMON_COUNT, ENERGY_COUNT, DAMAGE_COUNTER_COUNT, BENCH_COUNT, HAND_SIZE, PRIZE_COUNT, DISCARD_COUNT, LOST_ZONE_COUNT, DECK_SIZE ->
                NumericConditionPolicy.matches(condition, context);
            case ATTACK_DID_DAMAGE, POKEMON_ATTACKED_LAST_OWN_TURN, CARD_PLAYED_THIS_TURN, NAME_EQUALS, NAME_CONTAINS, ALL_POKEMON_NAME_CONTAINS, IS_EVOLVED, EVOLVED_THIS_TURN, IS_STAGE, IS_BASIC, IS_EX, IS_V, IS_GX, IS_VSTAR, IS_LEGACY_EX, IS_VMAX, IS_TERA, IS_TYPE, IS_FAMILY, ALL_POKEMON_FAMILY, BECAME_ACTIVE_THIS_TURN, BECAME_ACTIVE_LAST_OPPONENT_TURN, RETREATED_LAST_OPPONENT_TURN, KNOCKED_OUT_BY_ATTACK_LAST_TURN, KNOCKED_OUT_LAST_TURN, HEALED_THIS_TURN, HAS_ABILITIES, HAS_ABILITY, HAS_OTHER_SPECIAL_ENERGY, FIELDS_SHARE_POKEMON_TYPE, ALL_BENCH_DAMAGED, HAS_TOOL_DEFINITION, TOOL_ATTACHED_THIS_TURN, HAS_ATTACK, HAS_TOOL, HAS_RESISTANCE, WEAK_TO_OPPONENT_ACTIVE, HAS_RULE_BOX, IS_ACTIVE, STADIUM_IN_PLAY, TURN_STATE ->
                context.flag(condition.type(), target(condition), condition.arguments());
            case OPPONENT_USED_LIMIT_GROUP, OWN_USED_LIMIT_GROUP -> context.flag(condition.type(), target(condition), condition.arguments());
            case CUSTOM -> context.customCondition(condition);
        };
    }

    /** “… is affected by a Special Condition”: status ANY matches any one of the five Special Conditions. */
    public static final String ANY_STATUS = "ANY";
    private static boolean hasStatus(EffectConditionContext context, EffectTarget target, String status) {
        if (!ANY_STATUS.equals(status)) return context.hasStatus(target, status);
        for (var condition : dev.hua.ptcglive.engine.field.SpecialCondition.values())
            if (context.hasStatus(target, condition.name())) return true;
        return false;
    }
    private static EffectTarget target(ConditionDefinition condition) {
        return EffectTarget.fromWire(condition.arguments().getOrDefault("target", "SELF"));
    }
    private static String required(Map<String, String> arguments, String key) {
        String value = arguments.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("条件缺少参数：" + key);
        return value;
    }
    private static int integer(Map<String, String> arguments, String key) {
        try { return Integer.parseInt(required(arguments, key)); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("条件参数不是整数：" + key, invalid); }
    }
}
