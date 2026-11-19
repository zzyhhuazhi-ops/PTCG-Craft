package dev.hua.ptcglive.engine.effect;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Data-only policy for the common “flip for each …”, retry and per-head
 * patterns.  It asks an EffectExecutionContext for facts and randomness, so
 * neither a card JSON file nor a client can manufacture a result.
 */
public record CoinFlipPolicy(int baseFlips, ConditionType countType, EffectTarget countTarget,
                             Map<String, String> countArguments, int perCount, int maximum, Reroll reroll, boolean untilTails) {
    public enum Reroll { NONE, TAILS_ONCE, ALL_TAILS_ONCE }
    /** Two 60-card decks, at most four supplied units per Energy card. Finite counts must not stop at 20. */
    public static final int FINITE_LIMIT = 480;

    public static final Set<String> FIELDS = Set.of("flips", "bind", "flips_from", "flips_target", "flips_per",
        "flips_max", "pokemon_names", "name_contains", "pokemon_types", "pokemon_families", "family_match",
        "damage_counters_min", "energy_type", "count_mode", "card_kind", "reroll", "until");

    public CoinFlipPolicy(int baseFlips, ConditionType countType, EffectTarget countTarget,
                          Map<String, String> countArguments, int perCount, int maximum, Reroll reroll) {
        this(baseFlips, countType, countTarget, countArguments, perCount, maximum, reroll, false);
    }
    private static final Set<ConditionType> COUNT_TYPES = Set.of(ConditionType.POKEMON_COUNT, ConditionType.ENERGY_COUNT, ConditionType.DAMAGE_COUNTER_COUNT,
        ConditionType.BENCH_COUNT, ConditionType.HAND_SIZE, ConditionType.PRIZE_COUNT, ConditionType.DISCARD_COUNT);
    private static final Set<EffectTarget> COUNT_TARGETS = Set.of(EffectTarget.SELF, EffectTarget.CHOSEN_POKEMON,
        EffectTarget.TRIGGER_POKEMON, EffectTarget.ATTACKING_POKEMON, EffectTarget.OWN_ACTIVE, EffectTarget.OPPONENT_ACTIVE,
        EffectTarget.OWN_BENCH, EffectTarget.OPPONENT_BENCH, EffectTarget.ALL_OWN_POKEMON, EffectTarget.ALL_OPPONENT_POKEMON);

    public CoinFlipPolicy {
        countArguments = Map.copyOf(countArguments);
    }

    public static CoinFlipPolicy from(EffectDefinition effect) {
        if (!effect.type().equals("coin_flip")) throw new IllegalArgumentException("不是硬币效果");
        Map<String, String> arguments = effect.arguments();
        if (!FIELDS.containsAll(arguments.keySet())) throw new IllegalArgumentException("硬币效果包含未知参数");
        if (arguments.containsKey("until")) {
            if (!"TAILS".equalsIgnoreCase(arguments.get("until").trim()))
                throw new IllegalArgumentException("until 目前只支持 TAILS（掷到反面为止）");
            for (String key : arguments.keySet())
                if (!key.equals("until") && !key.equals("bind"))
                    throw new IllegalArgumentException("“掷到反面为止”不能同时声明固定次数、动态次数或重投：" + key);
            return new CoinFlipPolicy(0, null, EffectTarget.SELF, Map.of(), 1, 0, Reroll.NONE, true);
        }
        int base = integer(arguments, "flips", 0, 20);
        String source = arguments.get("flips_from");
        if (source == null && base < 1) throw new IllegalArgumentException("固定硬币次数至少为 1");
        if (source == null && (arguments.containsKey("flips_target") || arguments.containsKey("flips_per")
            || arguments.containsKey("flips_max") || arguments.containsKey("energy_type") || arguments.containsKey("count_mode")
            || arguments.containsKey("card_kind") || arguments.containsKey("pokemon_names") || arguments.containsKey("name_contains"))) throw new IllegalArgumentException("动态硬币参数必须同时提供 flips_from");
        ConditionType type = null;
        EffectTarget target = EffectTarget.SELF;
        Map<String, String> countArguments = Map.of();
        int per = 1;
        int maximum = 20;
        if (source != null) {
            type = ConditionType.fromWire(source);
            if (!COUNT_TYPES.contains(type)) throw new IllegalArgumentException("flips_from 不是已支持的数值来源");
            target = EffectTarget.fromWire(arguments.getOrDefault("flips_target", effect.target().name()));
            if (!COUNT_TARGETS.contains(target)) throw new IllegalArgumentException("动态硬币次数必须读取公开场上或玩家状态");
            per = integer(arguments, "flips_per", 1, 20, 1);
            maximum = integer(arguments, "flips_max", 1, FINITE_LIMIT, FINITE_LIMIT);
            if (maximum < base) throw new IllegalArgumentException("flips_max 不能小于基础投掷次数");
            boolean pokemonFilter=List.of("pokemon_names","name_contains","pokemon_types","pokemon_families","family_match","damage_counters_min")
                .stream().anyMatch(arguments::containsKey);
            if(pokemonFilter&&type!=ConditionType.POKEMON_COUNT)throw new IllegalArgumentException("宝可梦筛选仅用于POKEMON_COUNT");
            if(type==ConditionType.POKEMON_COUNT) {
                if(!pokemonFilter || arguments.containsKey("pokemon_names")&&arguments.containsKey("name_contains")
                    || arguments.containsKey("energy_type") || arguments.containsKey("count_mode") || arguments.containsKey("card_kind"))
                    throw new IllegalArgumentException("场上硬币计数须明确一个宝可梦筛选且不能混用能量筛选");
                PokemonCountEffect.validateNameCountTarget(target);
                var query=new LinkedHashMap<String,String>();
                for(String key:List.of("pokemon_names","name_contains","pokemon_types","pokemon_families","family_match","damage_counters_min"))
                    if(arguments.containsKey(key))query.put(key,arguments.get(key));
                PokemonCountEffect.fieldQuery(query);
                countArguments=query;
            } else if (type == ConditionType.ENERGY_COUNT) {
                var query = new LinkedHashMap<String, String>();
                for (String key : List.of("energy_type", "count_mode", "card_kind"))
                    if (arguments.containsKey(key)) query.put(key, arguments.get(key));
                EnergyCountQuery.from(query);
                countArguments = query;
            } else if (arguments.containsKey("energy_type") || arguments.containsKey("count_mode") || arguments.containsKey("card_kind")) {
                throw new IllegalArgumentException("仅 ENERGY_COUNT 可以筛选能量类型或能量卡类别");
            }
        }
        Reroll reroll;
        try { reroll = Reroll.valueOf(arguments.getOrDefault("reroll", "NONE").trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("未知硬币重投规则", invalid); }
        return new CoinFlipPolicy(base, type, target, countArguments, per, maximum, reroll);
    }

    /** Resolve count from server-owned facts, cap it before any random result is consumed. */
    public int resolvedFlips(EffectConditionContext context) {
        if (untilTails) throw new IllegalStateException("掷到反面没有可预先确定的固定次数");
        int extra = countType == null ? 0 : Math.max(0, context.number(countType, countTarget, countArguments));
        long total = (long) baseFlips + (long) extra * perCount;
        if (total > FINITE_LIMIT && maximum == FINITE_LIMIT)
            throw new IllegalArgumentException("有限硬币次数超过执行容量，不能截断后按完整效果结算");
        return (int) Math.min(maximum, total);
    }

    /** A retry changes the final result but is recorded as a distinct server RNG operation. */
    public EffectExecutionContext.CoinResult flip(EffectExecutionContext context, EffectDefinition source) {
        if (untilTails) {
            if(context.canChooseNextCoinOutcome(source))throw new IllegalStateException("选择硬币结果需要可暂停的服务器交互");
            // One server coin per step; the first tails ends the sequence and is part of the result.
            int heads = 0;
            while(true) {
                if(context.flipCoins(source,1,"until-tails-"+heads).heads()==0)
                    return new EffectExecutionContext.CoinResult(Math.addExact(heads,1),heads);
                heads=Math.addExact(heads,1);
            }
        }
        int flips = resolvedFlips(context);
        if (flips == 0) return new EffectExecutionContext.CoinResult(0, 0);
        if(context.canChooseNextCoinOutcome(source))throw new IllegalStateException("选择硬币结果需要可暂停的服务器交互");
        var initial = context.flipCoins(source, flips, "initial");
        return switch (reroll) {
            case NONE -> initial;
            case TAILS_ONCE -> {
                int tails = flips - initial.heads();
                if (tails == 0) yield initial;
                var retry = context.flipCoins(source, tails, "tails-reroll");
                yield new EffectExecutionContext.CoinResult(flips, initial.heads() + retry.heads());
            }
            case ALL_TAILS_ONCE -> initial.heads() == 0 ? context.flipCoins(source, flips, "all-tails-reroll") : initial;
        };
    }

    /** A per-head repetition must remain lexically inside the CoinFlipEffect that supplies its result. */
    public static void validatePerHeadsScope(List<EffectDefinition> effects) {
        for (EffectDefinition effect : effects) validatePerHeadsScope(effect, false);
    }
    private static void validatePerHeadsScope(EffectDefinition effect, boolean insideCoin) {
        if (effect.type().equals("repeat_per_heads") && !insideCoin)
            throw new IllegalArgumentException("repeat_per_heads 必须位于 coin_flip 的分支内");
        boolean childInsideCoin = insideCoin || effect.type().equals("coin_flip");
        for (EffectDefinition child : effect.effects()) validatePerHeadsScope(child, childInsideCoin);
    }

    private static int integer(Map<String, String> arguments, String key, int min, int max) {
        return integer(arguments, key, min, max, Integer.MIN_VALUE);
    }
    private static int integer(Map<String, String> arguments, String key, int min, int max, int fallback) {
        String raw = arguments.get(key);
        if (raw == null && fallback != Integer.MIN_VALUE) return fallback;
        try {
            int value = Integer.parseInt(raw);
            if (value < min || value > max) throw new IllegalArgumentException(key + " 超出范围");
            return value;
        } catch (NumberFormatException invalid) { throw new IllegalArgumentException(key + " 必须为整数", invalid); }
    }
}
