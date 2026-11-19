package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** One atomic effect's public target, not a card-specific selector or a replacement for allocation. */
public final class PokemonEffectSelection {
    private PokemonEffectSelection() { }
    private static final Set<String> STATUS=Set.of("poison","burn","sleep","paralyze","confuse","clear_status");
    private static final Set<String> SWITCHES=Set.of("switch","force_switch","gust","retreat");
    private static final Set<String> BENCH_DAMAGE=Set.of("bench_damage");
    private static final Set<String> LEAVE_PLAY=Set.of("return_to_hand","shuffle_into_deck","discard_pokemon");
    public static boolean handles(EffectDefinition effect) {
        if(PokemonSelectionScope.handles(effect))return true;
        if(effect.type().equals("evolve_selected_from_deck"))return true;
        return (effect.target()==EffectTarget.SELECT_OWN_POKEMON || effect.target()==EffectTarget.SELECT_OPPONENT_POKEMON)
            && !DamageCounterDistribution.handles(effect)
            && (STATUS.contains(effect.type()) || SWITCHES.contains(effect.type()) || BENCH_DAMAGE.contains(effect.type()) || LEAVE_PLAY.contains(effect.type())
                || Set.of("shuffle_named_bench_then_damage","shuffle_unchosen_opponent_bench","heal","damage_counter","knock_out","devolve","prevent_evolution","prevent_attack","prevent_retreat","disable_abilities","disable_tools").contains(effect.type()));
    }
    public static boolean activeOnly(EffectDefinition effect) { return STATUS.contains(effect.type()); }
    /** A replacement for the Active must always be chosen from a current Bench. */
    public static boolean benchOnly(EffectDefinition effect) { return SWITCHES.contains(effect.type()) || BENCH_DAMAGE.contains(effect.type())
        || effect.type().equals("shuffle_named_bench_then_damage") || effect.type().equals("shuffle_unchosen_opponent_bench") || opponentBenchCounters(effect); }
    /** Switches choose after earlier text resolves; legacy SELECT heal/status targets remain preselected. */
    public static boolean defersSelection(EffectDefinition effect) { return effect.type().equals("evolve_selected_from_deck") || SWITCHES.contains(effect.type()) || BENCH_DAMAGE.contains(effect.type()) || LEAVE_PLAY.contains(effect.type()) || HealingEffect.boundedTargets(effect)
        || effect.type().equals("damage_counter")&&effect.arguments().containsKey("counter_multiplier") || effect.type().equals("shuffle_named_bench_then_damage") || effect.type().equals("shuffle_unchosen_opponent_bench") || opponentBenchCounters(effect); }
    private static boolean opponentBenchCounters(EffectDefinition effect) {
        return effect.type().equals("damage_counter") && effect.target()==EffectTarget.SELECT_OPPONENT_POKEMON
            && "BENCH".equals(effect.arguments().get("position"));
    }
    /** Forced switching follows printed wording: the affected player chooses their replacement. */
    public static boolean chooserIsTargetOwner(EffectDefinition effect) { return effect.type().equals("force_switch")||opponentKeepsBench(effect); }
    public static boolean opponentKeepsBench(EffectDefinition effect) {
        return effect.type().equals("shuffle_unchosen_opponent_bench")
            &&effect.arguments().equals(java.util.Map.of("keep","2","chooser","OPPONENT"));
    }
    /**
     * “This attack (also) does N damage to each of your (opponent's) Benched Pokémon.” names one fixed public
     * group: every Pokémon on that Bench at resolution takes the attack damage, so nobody chooses a target.
     */
    public static boolean groupBenchDamage(EffectDefinition effect) {
        return effect.type().equals("bench_damage")
            && (effect.target()==EffectTarget.OWN_BENCH || effect.target()==EffectTarget.OPPONENT_BENCH);
    }
    /** Shared contract of the selected and the whole-Bench forms of BenchDamageEffect. */
    public static void validateBenchDamage(EffectDefinition effect) {
        if(effect.target()==EffectTarget.CHOSEN_POKEMON) {
            if(!effect.arguments().keySet().equals(Set.of("amount")) || !effect.effects().isEmpty() || !effect.conditions().isEmpty())
                throw new IllegalArgumentException("已选备战伤害只接受amount");
            benchDamageAmount(effect);return;
        }
        if(!groupBenchDamage(effect)) { validate(effect); return; }
        if (!effect.effects().isEmpty() || !effect.conditions().isEmpty()) throw new IllegalArgumentException("BenchDamageEffect 不接受嵌套文本");
        if (!Set.of("amount").equals(effect.arguments().keySet())
            &&!(effect.arguments().keySet().equals(Set.of("amount","pokemon_names"))
                &&!TurnHistoryCondition.values(effect.arguments().get("pokemon_names")).isEmpty())
            &&!(effect.arguments().keySet().equals(Set.of("amount","damaged_only"))
                &&"true".equals(effect.arguments().get("damaged_only")))
            &&!(effect.arguments().keySet().equals(Set.of("amount","has_tool"))
                &&"true".equals(effect.arguments().get("has_tool")))
            &&!(effect.arguments().keySet().equals(Set.of("amount","amount_mode"))
                &&"OPPONENT_PRIZES_TAKEN".equals(effect.arguments().get("amount_mode"))))
            throw new IllegalArgumentException("全体备战伤害只接受固定 amount，或按对手已取得奖赏数倍增");
        benchDamageAmount(effect);
    }
    /**
     * “This attack also does N damage to 2 of your opponent's Benched Pokémon.”: how many different Pokémon one
     * selected bench_damage step chooses (2–5). Absent means 1; only bench_damage may choose more than one.
     */
    public static int targetCount(EffectDefinition effect) {
        if(effect.type().equals("shuffle_unchosen_opponent_bench"))return opponentKeepsBench(effect)?2:3;
        String raw = effect.arguments().get("target_count");
        if (raw == null) return 1;
        if(PokemonSelectionScope.handles(effect)) {PokemonSelectionScope.validate(effect);return Integer.parseInt(raw);}
        if(effect.type().equals("heal")) {HealingEffect.from(effect);return Integer.parseInt(raw);}
        if(effect.type().equals("damage_counter")) {DamageCounterEffect.from(effect);int n=Integer.parseInt(raw);if(n<2||n>6)throw new IllegalArgumentException("指示物目标数须为2至6");return n;}
        if(effect.type().equals("evolve_selected_from_deck")) {EffectSchemaValidator.validate(effect);return Integer.parseInt(raw);}
        if (!effect.type().equals("bench_damage") || groupBenchDamage(effect))
            throw new IllegalArgumentException("只有选择目标的备战伤害可以指定 target_count");
        int count;
        try { count = Integer.parseInt(raw); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("target_count 必须是整数", invalid); }
        if (count < 2 || count > 5) throw new IllegalArgumentException("target_count 必须是 2 至 5（选 1 只时省略）");
        return count;
    }
    private static void benchDamageAmount(EffectDefinition effect) {
        int amount;
        try { amount=Integer.parseInt(effect.arguments().get("amount")); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("BenchDamageEffect 缺少有效伤害数值", invalid); }
        if (amount < 10 || amount > 1000 || amount % 10 != 0)
            throw new IllegalArgumentException("BenchDamageEffect 伤害必须是 10 至 1000 的 10 倍数");
    }
    public static void validate(EffectDefinition effect) {
        if(PokemonSelectionScope.handles(effect)) { PokemonSelectionScope.validate(effect);return; }
        if(!handles(effect))throw new IllegalArgumentException("此效果没有独立宝可梦选择契约");
        if(effect.type().equals("evolve_selected_from_deck")){EffectSchemaValidator.validate(effect);return;}
        if(effect.type().equals("shuffle_named_bench_then_damage")){
            if(effect.target()!=EffectTarget.SELECT_OWN_POKEMON||!effect.arguments().equals(java.util.Map.of("pokemon_names","Combee","bind","raid-return"))||!effect.effects().isEmpty()||!effect.conditions().isEmpty())throw new IllegalArgumentException("突袭指令须选择备战三蜜蜂并记录实际回库");return;
        }
        if(effect.type().equals("shuffle_unchosen_opponent_bench")){
            if(effect.target()!=EffectTarget.SELECT_OPPONENT_POKEMON||(!effect.arguments().equals(java.util.Map.of("keep","3"))&&!opponentKeepsBench(effect))||!effect.effects().isEmpty()||!effect.conditions().isEmpty())throw new IllegalArgumentException("备战洗回仅核验攻击方选3只保留的完整步骤");return;
        }
        if (effect.type().equals("switch") && effect.target()!=EffectTarget.SELECT_OWN_POKEMON)
            throw new IllegalArgumentException("SwitchEffect 必须由效果操控方选择己方备战宝可梦");
        if (effect.type().equals("force_switch") && effect.target()!=EffectTarget.SELECT_OPPONENT_POKEMON)
            throw new IllegalArgumentException("ForceSwitchEffect 必须由受影响方选择自己的备战宝可梦");
        // “Switch 1 of your opponent's Benched Pokémon with their Active Pokémon.” / “Switch in 1 of your opponent's
        // Benched Pokémon to the Active Spot.”: the effect's controller chooses the opponent's Benched Pokémon.
        if (effect.type().equals("gust") && effect.target()!=EffectTarget.SELECT_OPPONENT_POKEMON)
            throw new IllegalArgumentException("GustEffect 必须由效果操控方选择对方的备战宝可梦");
        if (effect.type().equals("retreat")) RetreatEffect.validate(effect);
        if (effect.type().equals("bench_damage")) {
            if (effect.target()!=EffectTarget.SELECT_OWN_POKEMON && effect.target()!=EffectTarget.SELECT_OPPONENT_POKEMON)
                throw new IllegalArgumentException("BenchDamageEffect 必须选择己方或对方的一只备战宝可梦");
            if (!effect.effects().isEmpty()) throw new IllegalArgumentException("BenchDamageEffect 不接受嵌套文本");
            if (!Set.of("amount", "target_count").containsAll(effect.arguments().keySet()))
                throw new IllegalArgumentException("选择目标的备战伤害只接受 amount、target_count");
            benchDamageAmount(effect);
            targetCount(effect);
        }
        // pokemon_name_contains restricts an explicitly named replacement; bind records whether the switch happened.
        if (!effect.type().equals("retreat") && SWITCHES.contains(effect.type())
            && (!(effect.type().equals("gust")
                ? Set.of("bind","pokemon_name_contains","pokemon_families","basic_pokemon_only","remaining_hp_max")
                : effect.type().equals("switch") ? Set.of("bind","pokemon_name_contains","source_only_if_benched") : Set.of("bind","pokemon_name_contains")).containsAll(effect.arguments().keySet()) || !effect.effects().isEmpty()))
            throw new IllegalArgumentException("通用换位效果不接受未审核参数或嵌套文本");
        if(effect.type().equals("gust")&&effect.arguments().containsKey("remaining_hp_max")
            &&!effect.arguments().equals(java.util.Map.of("remaining_hp_max","50")))
            throw new IllegalArgumentException("剩余HP换入仅接受已审核的50HP上限");
        if(effect.type().equals("gust")&&effect.arguments().containsKey("basic_pokemon_only")
            &&!effect.arguments().equals(java.util.Map.of("basic_pokemon_only","true")))
            throw new IllegalArgumentException("基础宝可梦换入仅接受已审核的单一基础筛选");
        if(effect.arguments().containsKey("source_only_if_benched")&&(!effect.type().equals("switch")
            ||!"true".equals(effect.arguments().get("source_only_if_benched"))))
            throw new IllegalArgumentException("按来源位置换位仅接受己方switch与true");
        if (effect.type().equals("gust") && effect.arguments().containsKey("pokemon_families")
            && !Set.of("V","GX|EX_LEGACY","MEGA_EVOLUTION_LEGACY").contains(effect.arguments().get("pokemon_families")))
            throw new IllegalArgumentException("限定家族的 gust 目前只支持宝可梦 V");
        if (!effect.type().equals("retreat") && SWITCHES.contains(effect.type()) && effect.arguments().containsKey("bind"))
            AttackCountBindings.name(effect.arguments().get("bind"));
        if(effect.arguments().containsKey("pokemon_name_contains")
            && effect.arguments().get("pokemon_name_contains").isBlank())
            throw new IllegalArgumentException("换位卡名筛选不能为空");
        if(effect.type().equals("heal")) {
            HealingEffect.from(effect);
        }
        if(effect.type().equals("damage_counter")) {
            DamageCounterEffect.from(effect);targetCount(effect);
        }
    }
}
