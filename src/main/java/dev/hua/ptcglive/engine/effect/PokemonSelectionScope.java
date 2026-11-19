package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** Lexical data contract: CHOSEN_POKEMON means the nearest enclosing server selection. */
public final class PokemonSelectionScope {
    public static final Set<String> FIELDS=Set.of("position","stage","bind","target_count","rule_box","pokemon_families","family_match","pokemon_types","pokemon_names","pokemon_name_contains","pokemon_name_excludes","remaining_hp_min","remaining_hp_max","damage_counters_min","damage_counters_exact","least_hp_remaining","exclude_source","exclude_ref","energy_units_min","energy_cards_min","energy_type");
    private PokemonSelectionScope() { }
    public enum Position { ANY, ACTIVE, BENCH }
    /** Printed evolution stage, resolved only from audited CardDefinition/PokémonProfile data on the server. */
    public enum Stage { BASIC, EVOLUTION, STAGE1, STAGE2 }
    public static boolean handles(EffectDefinition effect) { return effect.type().equals("select_pokemon"); }
    public static Position position(EffectDefinition effect) {
        try { return Position.valueOf(effect.arguments().getOrDefault("position","ANY")); }
        catch(IllegalArgumentException invalid) { throw new IllegalArgumentException("宝可梦选择position须为ANY、ACTIVE或BENCH",invalid); }
    }
    public static Stage stage(EffectDefinition effect) {
        String raw=effect.arguments().get("stage");
        if(raw==null)return null;
        try { return Stage.valueOf(raw); }
        catch(IllegalArgumentException invalid) { throw new IllegalArgumentException("宝可梦选择stage须为BASIC、EVOLUTION、STAGE1或STAGE2",invalid); }
    }
    public static void validate(EffectDefinition effect) {
        if(!handles(effect) || !Set.of(EffectTarget.SELECT_OWN_POKEMON,EffectTarget.SELECT_OPPONENT_POKEMON,EffectTarget.SELECT_ANY_POKEMON).contains(effect.target()))
            throw new IllegalArgumentException("select_pokemon必须声明选择己方、对方或任一方宝可梦");
        if(effect.arguments().containsKey("bind"))AttackPokemonBindings.name(effect.arguments().get("bind"));
        else if(effect.effects().isEmpty())throw new IllegalArgumentException("未命名select_pokemon须包含后续效果");
        if(effect.arguments().containsKey("exclude_ref"))AttackPokemonBindings.name(effect.arguments().get("exclude_ref"));
        if(!FIELDS.containsAll(effect.arguments().keySet()))throw new IllegalArgumentException("select_pokemon包含未知参数");
        if(effect.arguments().containsKey("pokemon_names"))TurnHistoryCondition.values(effect.arguments().get("pokemon_names"));
        if(effect.arguments().containsKey("energy_cards_min")&&!"1".equals(effect.arguments().get("energy_cards_min")))
            throw new IllegalArgumentException("附着卡筛选只核验至少1张能量卡");
        if(effect.arguments().containsKey("energy_units_min")&&!Set.of("1","2").contains(effect.arguments().get("energy_units_min")))
            throw new IllegalArgumentException("附能筛选须为已审核的至少1或2个能量");
        if(effect.arguments().containsKey("energy_type")) {
            if(!effect.arguments().containsKey("energy_units_min"))throw new IllegalArgumentException("能量属性筛选须同时声明供能下限");
            dev.hua.ptcglive.engine.field.EnergyType.valueOf(effect.arguments().get("energy_type"));
        }
        if(effect.arguments().containsKey("target_count")) {
            int count=Integer.parseInt(effect.arguments().get("target_count"));
            if(count<2||count>6||effect.arguments().containsKey("bind"))throw new IllegalArgumentException("多目标select_pokemon须选择2至6只且不能绑定单一目标");
        }
        if(effect.arguments().containsKey("pokemon_name_contains")&&effect.arguments().get("pokemon_name_contains").isBlank())
            throw new IllegalArgumentException("宝可梦名称筛选不能为空");
        if(effect.arguments().containsKey("pokemon_name_excludes")&&effect.arguments().get("pokemon_name_excludes").isBlank())
            throw new IllegalArgumentException("排除宝可梦名称不能为空");
        position(effect);stage(effect);ruleBox(effect);family(effect);types(effect);minimumDamageCounters(effect);exactDamageCounters(effect);
        int minHp=minimumRemainingHp(effect),maxHp=maximumRemainingHp(effect);
        if(maxHp>0&&minHp>maxHp)throw new IllegalArgumentException("剩余HP选择下限不能高于上限");
        for(String key:Set.of("least_hp_remaining","exclude_source"))if(effect.arguments().containsKey(key)&&!"true".equals(effect.arguments().get(key)))
            throw new IllegalArgumentException(key+" 必须明确为 true");
        if(effect.arguments().containsKey("least_hp_remaining")&&effect.target()!=EffectTarget.SELECT_ANY_POKEMON)
            throw new IllegalArgumentException("最低剩余HP筛选必须选择双方场上宝可梦");
    }
    public static int exactDamageCounters(EffectDefinition effect) {
        if(!effect.arguments().containsKey("damage_counters_exact"))return -1;
        int value=Integer.parseInt(effect.arguments().get("damage_counters_exact"));
        if(value<0||value>60||effect.arguments().containsKey("damage_counters_min"))
            throw new IllegalArgumentException("精确伤害指示物须为0至60且不能混用下限");
        return value;
    }
    public static int minimumDamageCounters(EffectDefinition effect) {
        if(!effect.arguments().containsKey("damage_counters_min"))return 0;
        int value=Integer.parseInt(effect.arguments().get("damage_counters_min"));
        if(value<1||value>60)throw new IllegalArgumentException("选择目标的伤害指示物下限须为1至60");
        return value;
    }
    public static int maximumRemainingHp(EffectDefinition effect) {
        if(!effect.arguments().containsKey("remaining_hp_max"))return 0;
        int value=Integer.parseInt(effect.arguments().get("remaining_hp_max"));
        if(value<10||value>1000||value%10!=0)throw new IllegalArgumentException("剩余HP选择上限须为10至1000的10倍数");
        return value;
    }
    public static int minimumRemainingHp(EffectDefinition effect) {
        if(!effect.arguments().containsKey("remaining_hp_min"))return 0;
        int value=Integer.parseInt(effect.arguments().get("remaining_hp_min"));
        if(value<10||value>1000||value%10!=0)throw new IllegalArgumentException("剩余HP选择下限须为10至1000的10倍数");
        return value;
    }
    public static PokemonFamilyFilter family(EffectDefinition effect){return PokemonFamilyFilter.fromWire(effect.arguments().get("pokemon_families"),effect.arguments().get("family_match"));}
    public static PokemonTypeFilter types(EffectDefinition effect){return PokemonTypeFilter.fromWire(effect.arguments().get("pokemon_types"));}
    public static RuleBoxFilter ruleBox(EffectDefinition effect) {
        return RuleBoxFilter.fromWire(effect.arguments().get("rule_box"));
    }
    public static boolean usesChosen(ConditionDefinition condition) {
        return EffectTarget.fromWire(condition.arguments().getOrDefault("target","SELF"))==EffectTarget.CHOSEN_POKEMON
            || condition.children().stream().anyMatch(PokemonSelectionScope::usesChosen);
    }
    public static void requireScope(ConditionDefinition condition,Position enclosing) {
        if(enclosing==null && usesChosen(condition))throw new IllegalArgumentException("CHOSEN_POKEMON条件必须位于已选宝可梦作用域内");
    }
    public static void requireScope(EffectDefinition effect,Position enclosing) {
        if(effect.target()!=EffectTarget.CHOSEN_POKEMON)return;
        if(enclosing==null)throw new IllegalArgumentException("CHOSEN_POKEMON效果必须位于已选宝可梦作用域内");
        if(MoveDamageCounterEffect.handles(effect)&&MoveDamageCounterEffect.from(effect).all()&&enclosing!=Position.BENCH)
            throw new IllegalArgumentException("全量转移来源须显式选择备战宝可梦");
        if(effect.type().equals("bench_damage")&&enclosing!=Position.BENCH)throw new IllegalArgumentException("已选备战伤害必须显式选择BENCH");
        if(effect.type().equals("clear_status")&&enclosing==Position.BENCH)throw new IllegalArgumentException("备战区限定的解除状态没有可执行对象");
        if(Set.of("poison","burn","sleep","paralyze","confuse").contains(effect.type()) && enclosing!=Position.ACTIVE)
            throw new IllegalArgumentException("对已选对象施加异常状态必须显式选择ACTIVE战斗位");
    }
    public static void validateBoundEffect(EffectDefinition effect) {
        if(effect.target()!=EffectTarget.CHOSEN_POKEMON)throw new IllegalArgumentException("不是已选目标效果");
        if(effect.type().equals(DevolutionSpray.TYPE)){DevolutionSpray.validate(effect);return;}
        if(PokemonDiscardReplacement.handles(effect)){PokemonDiscardReplacement.validate(effect);return;}
        if(PokemonDeckReplacement.selectedBasic(effect)){PokemonDeckReplacement.validate(effect);return;}
        if(MoveDamageCounterEffect.handles(effect)){MoveDamageCounterEffect.from(effect);return;}
        // Attached cards are selected in a later server-issued transaction.
        // They do not need (and must not gain) a second Pokémon-selection
        // contract merely because their public host was bound earlier.
        if (AttachedEnergyEffect.handles(effect)) { AttachedEnergyEffect.from(effect); return; }
        if (AttachedToolEffect.handles(effect)) { AttachedToolEffect.from(effect); return; }
        if (effect.type().equals("attach_energy")) { AttachEnergyEffect.from(effect); return; }
        if (effect.type().equals("attach_tool")) { AttachToolEffect.from(effect); return; }
        if (ProtectionEffect.handles(effect)) { ProtectionEffect.from(effect); return; }
        // A scoped return consumes the already selected host, including its entire evolution stack.
        // In particular, coin-gated returns choose after the coin result, without a second selection.
        if (effect.type().equals("return_to_hand")) {
            if (!Set.of("discard_attached").containsAll(effect.arguments().keySet()) || !effect.effects().isEmpty()
                || effect.arguments().containsKey("discard_attached") && !"true".equals(effect.arguments().get("discard_attached")))
                throw new IllegalArgumentException("已选宝可梦返回手牌仅支持可选的弃置附着卡标记");
            return;
        }
        if (effect.type().equals("discard_pokemon")||effect.type().equals("shuffle_into_deck")) {
            if (!effect.arguments().isEmpty() || !effect.conditions().isEmpty() || !effect.effects().isEmpty())
                throw new IllegalArgumentException("已选宝可梦离场只接受无附加参数的服务器目标");
            return;
        }
        PokemonEffectSelection.validate(new EffectDefinition(effect.type(),effect.type().equals("gust")?EffectTarget.SELECT_OPPONENT_POKEMON:EffectTarget.SELECT_OWN_POKEMON,
            effect.arguments(),effect.conditions(),effect.effects()));
    }
}
