package dev.hua.ptcglive.engine.effect;

import java.util.Set;
import java.util.function.Predicate;

/** Live, read-only rules; they disappear with their source instead of placing lingering effects. */
public final class PassiveRuleProgram {
    private PassiveRuleProgram() { }
    /** A Tool on its own Pokémon may block effects of an opponent's hand-played Supporter. */
    public static boolean selfSupporterEffectsGuard(EffectDefinition effect) {
        return effect.type().equals("passive_effect_guard")&&effect.target()==EffectTarget.SELF
            &&effect.arguments().equals(java.util.Map.of("source","TRAINER","from","OPPONENT",
                "scope","EFFECTS_ONLY","trainer_categories","SUPPORTER"))
            &&effect.conditions().isEmpty()&&effect.effects().isEmpty();
    }
    public static boolean selfAbilityEffectsGuard(EffectDefinition effect) {
        return effect.type().equals("passive_effect_guard")&&effect.target()==EffectTarget.SELF
            &&effect.arguments().equals(java.util.Map.of("source","ABILITY","from","OPPONENT","scope","DAMAGE_AND_EFFECTS"))
            &&effect.conditions().isEmpty()&&effect.effects().isEmpty();
    }
    public static boolean selfAttackEffectsGuard(EffectDefinition effect) {
        return effect.type().equals("passive_effect_guard")&&effect.target()==EffectTarget.SELF
            &&effect.arguments().equals(java.util.Map.of("source","ATTACK","from","OPPONENT","scope","EFFECTS_ONLY"))
            &&effect.conditions().isEmpty()&&effect.effects().isEmpty();
    }
    public static boolean activeToolFieldAttackGuard(EffectDefinition effect) {
        return effect.type().equals("passive_effect_guard")&&effect.target()==EffectTarget.ALL_OWN_POKEMON
            &&effect.arguments().equals(java.util.Map.of("source","ATTACK","from","OPPONENT","scope","EFFECTS_ONLY","source_active_only","true"))
            &&effect.conditions().isEmpty()&&effect.effects().isEmpty();
    }
    public static boolean plasmaMirrorGuard(EffectDefinition effect) {
        var excluded=new ConditionDefinition(ConditionType.NOT,java.util.Map.of(),java.util.List.of(
            new ConditionDefinition(ConditionType.IS_LEGACY_EX,java.util.Map.of("target","SELF"),java.util.List.of())));
        return effect.type().equals("passive_effect_guard")&&effect.target()==EffectTarget.SELF
            &&effect.arguments().equals(java.util.Map.of("source","ATTACK","from","OPPONENT","scope","DAMAGE_AND_EFFECTS","attacker_filter","TEAM_PLASMA"))
            &&effect.conditions().equals(java.util.List.of(excluded))&&effect.effects().isEmpty();
    }
    public static boolean grassEvolutionTiming(EffectDefinition effect) {
        return effect.type().equals("evolution_timing_override")&&effect.target()==EffectTarget.SELF
            &&effect.arguments().size()==1&&Set.of("GRASS_EXCEPT_FIRST_TURN","GRASS_ALL_TIMING").contains(effect.arguments().getOrDefault("mode",""))
            &&effect.conditions().isEmpty()&&effect.effects().isEmpty();
    }
    public static boolean handles(String type) { return Set.of("flare_gear","cafe_master_turn_continues","inherit_poison_on_own_trainer_switch","discard_replacing_stadium","add_pokemon_families","prize_energy_attach","knockout_prizes_to_discard","knockout_pokemon_to_lost_zone","discard_from_field_to_hand","retreat_energy_to_hand","skip_evolution_turn_end","attack_while_asleep_or_paralyzed","retreat_while_asleep_or_paralyzed","retreat_attempt_coin_gate","prevent_damage_counter_move","prevent_healing","prevent_return_to_hand","prevent_deck_discard","checkup_poison_bonus","sleep_recovery_two_coins","bench_limit","attack_weakness_multiplier","bench_weakness_resistance","weakness_multiplier","passive_effect_guard","prevent_checkup_recovery","no_weakness","no_resistance","prevent_item_supporter_effects","prevent_self_attack_damage","special_condition_immunity","evolution_timing_override","evolution_name_alias","placement_rule","festival_attack_twice","festival_ground","coin_prevent_attack_damage","ignore_defender_damage_effects","energy_special_condition_immunity","tera_bench_expansion","stadium_attack_guard","passive_trainer_play_lock","passive_pokemon_play_lock","first_turn_attack_permission","disable_all_tools","disable_opponent_active_tools","prevent_opponent_attack_ability_counters","prevent_bench_damage_counters","bench_attack_damage_bonus","retain_confusion_on_evolution","retain_conditions_on_evolution","retain_poison_on_evolution","setup_active_override","basic_energy_multiplier_aura","bench_entry_damage_counters","tool_hand_play_guard", "pokemon_type_override","add_pokemon_types","weakness_override_aura","suppress_self_knockout_abilities","prize_reduction_aura","coin_prize_reduction").contains(type); }
    public static void validate(EffectDefinition effect) {
        if(!handles(effect.type())||!effect.effects().isEmpty())throw new IllegalArgumentException("持续规则不能包含动作");
        if(Set.of("flare_gear","cafe_master_turn_continues","prize_energy_attach","knockout_prizes_to_discard","knockout_pokemon_to_lost_zone","discard_from_field_to_hand").contains(effect.type())) {
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty()||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("场上自弃回手须为自身无条件替代规则");
            return;
        }
        if(effect.type().equals("add_pokemon_families")) {
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().equals(java.util.Map.of("pokemon_families","TEAM_PLASMA"))||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("等离子团徽章须为自身无条件身份附加");
            return;
        }
        if(effect.type().equals("disable_opponent_active_tools")) {
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty()||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("道具干扰须为自身战斗位的无条件持续规则");
            return;
        }
        if(effect.type().equals("retreat_energy_to_hand")) {
            if(effect.target()!=EffectTarget.SELF||!effect.conditions().isEmpty()
                ||!effect.arguments().keySet().equals(Set.of("energy_kind"))
                ||!Set.of("ANY","BASIC").contains(effect.arguments().get("energy_kind")))
                throw new IllegalArgumentException("撤退费用回手须为无条件规则，且明确基本能量或所有能量");
            return;
        }
        if(effect.type().equals("tool_hand_play_guard")) {
            if(effect.target()!=EffectTarget.SELF||!effect.conditions().isEmpty()
                ||!effect.arguments().equals(java.util.Map.of("pokemon_families","VMAX","remaining_hp_max","100")))
                throw new IllegalArgumentException("英雄奖牌只核验手牌使用时VMAX剩余HP不高于100的限制");
            return;
        }
        if(effect.type().equals("pokemon_type_override")) {
            if(effect.target()!=EffectTarget.SELF||!effect.conditions().isEmpty()
                ||!effect.arguments().keySet().equals(Set.of("pokemon_name","pokemon_type"))
                ||!"Silvally-GX".equals(effect.arguments().get("pokemon_name"))
                ||!Set.of("FIGHTING","PSYCHIC","LIGHTNING","FIRE","GRASS","WATER").contains(effect.arguments().get("pokemon_type")))
                throw new IllegalArgumentException("属性记忆碟只核验银伴战兽GX及六种指定属性");
            return;
        }
        if(Set.of("skip_evolution_turn_end","attack_while_asleep_or_paralyzed","retreat_while_asleep_or_paralyzed","prevent_self_attack_damage").contains(effect.type())) {
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty()||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("异常状态行动许可须为无条件宿主规则");
            return;
        }
        if(effect.type().equals("attack_weakness_multiplier")||effect.type().equals("bench_weakness_resistance")) {
            var expected=effect.type().equals("attack_weakness_multiplier")?java.util.Map.of("amount","3"):java.util.Map.<String,String>of();
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().equals(expected)||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("攻击道具弱点规则参数无效");
            return;
        }
        if(effect.type().equals("bench_attack_damage_bonus")) {
            if(effect.target()==EffectTarget.SELF&&effect.arguments().equals(java.util.Map.of("amount","30","defender_pokemon_families","V|GX"))&&effect.conditions().isEmpty())return;
            if(effect.target()!=EffectTarget.ALL_OWN_POKEMON||!effect.arguments().equals(java.util.Map.of(
                "amount","30","attacker_battle_style","RAPID_STRIKE",
                "defender_pokemon_families","V|GX","nonstack_group","throwing-coach"))
                ||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("投掷教练只支持己方连击宝可梦对对手备战 V/GX 的不叠加 30 点攻击加伤");
            return;
        }
        if(effect.type().equals("coin_prevent_attack_damage")||effect.type().equals("ignore_defender_damage_effects")) {
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty())throw new IllegalArgumentException("自身攻击规则不接受参数");
            return;
        }
        if(effect.type().equals("evolution_name_alias")) {
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().keySet().equals(Set.of("pokemon_name","pokemon_families"))
                ||effect.arguments().get("pokemon_name").isBlank())throw new IllegalArgumentException("进化名称别名须声明名称与目标族标记");
            PokemonFamilyFilter.fromWire(effect.arguments().get("pokemon_families"),null);return;
        }
        if(effect.type().equals("placement_rule")) {
            if(effect.target()!=EffectTarget.SELF||!Set.of("ZERO_TO_HERO_ONLY","EMERGENCY_ROTATION","SWELLING_FLASH","EXCITED_DIVE","EMERGENCY_ENTRY").contains(effect.arguments().get("mode"))
                ||!effect.arguments().keySet().equals(Set.of("mode"))||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("特殊上场规则模式无效");
            return;
        }
        if(effect.type().equals("festival_attack_twice")) {
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().keySet().equals(Set.of("stadium_name"))||effect.arguments().get("stadium_name").isBlank()||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("连续攻击规则须声明竞技场名称");
            return;
        }
        if(Set.of("festival_ground","flare_gear","cafe_master_turn_continues","inherit_poison_on_own_trainer_switch","discard_replacing_stadium").contains(effect.type())) {
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty()||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("庆典场地标记只接受无条件SELF根规则");
            return;
        }
        if(effect.type().equals("prevent_return_to_hand")) {
            if(effect.target()!=EffectTarget.ALL_OPPONENT_POKEMON||!effect.arguments().isEmpty()||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("回手封锁须为无条件的对方全场规则");
            return;
        }
        if(effect.type().equals("prevent_deck_discard")) {
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().equals(java.util.Map.of(
                "source_active_only","true","from","OPPONENT","sources","ATTACK|ABILITY|ITEM|TOOL|SUPPORTER"))
                ||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("牌库弃置防护须为战斗位所附工具阻止对手卡牌来源");
            return;
        }
        if(effect.type().equals("checkup_poison_bonus")) {
            boolean stadium=Set.of(EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON).contains(effect.target())
                &&(effect.arguments().equals(java.util.Map.of("amount","2","excluded_type","DARKNESS"))
                    ||effect.arguments().equals(java.util.Map.of("amount","2")));
            boolean ability=effect.target()==EffectTarget.ALL_OPPONENT_POKEMON
                &&effect.arguments().equals(java.util.Map.of("amount","5","source_active_only","true"));
            if(!stadium&&!ability||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("检查中毒加成须为全场范围、2个指示物并排除恶属性");
            return;
        }
        if(effect.type().equals("sleep_recovery_two_coins")) {
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty()||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("睡眠恢复双硬币须为无条件自身持续规则");
            return;
        }
        if(effect.type().equals("energy_special_condition_immunity")) {
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().equals(java.util.Map.of("scope","ALL_IN_PLAY"))||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("附能异常免疫须为全场竞技场规则");
            return;
        }
        if(effect.type().equals("tera_bench_expansion")) {
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().keySet().equals(Set.of("expanded_limit","normal_limit","leave_limit"))||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("太晶备战区扩张须声明三个上限");
            for(String key:Set.of("expanded_limit","normal_limit","leave_limit"))Integer.parseInt(effect.arguments().get(key));
            return;
        }
        if(effect.type().equals("stadium_attack_guard")) {
            boolean bench=effect.arguments().equals(java.util.Map.of("bench_only","true","scope","DAMAGE_ONLY","from","ANY"))
                ||effect.arguments().equals(java.util.Map.of("bench_only","true","scope","DAMAGE_AND_EFFECTS","from","OPPONENT"));
            if(bench&&effect.target()==EffectTarget.SELF&&effect.conditions().isEmpty())return;
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().equals(java.util.Map.of(
                "recipient_rule_box","NONE","attacker_families","EX|V"))||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("竞技场攻击防护须明确保护非规则宝可梦并限定ex与V攻击者");
            return;
        }
        if(effect.type().equals("disable_all_tools")) {
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty()||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("全场道具失效只接受竞技场SELF根规则");
            return;
        }
        if(effect.type().equals("prevent_opponent_attack_ability_counters")) {
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty())throw new IllegalArgumentException("伤害指示物防护只允许无参数宿主规则");
            return;
        }
        if(effect.type().equals("prevent_bench_damage_counters")) {
            boolean stadium=effect.target()==EffectTarget.SELF&&effect.arguments().isEmpty();
            boolean ability=effect.target()==EffectTarget.OWN_BENCH&&effect.arguments().equals(java.util.Map.of("attacker_filter","BASIC"));
            if((!stadium&&!ability)||!effect.conditions().isEmpty())throw new IllegalArgumentException("备战区指示物防护须为场地全局规则或基础攻击者筛选特性");
            return;
        }
        if(Set.of("retain_confusion_on_evolution","retain_conditions_on_evolution").contains(effect.type())) {
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty()||!effect.conditions().isEmpty())throw new IllegalArgumentException("竞技场全局规则只接受无条件SELF根规则");
            return;
        }
        if(effect.type().equals("retain_poison_on_evolution")) {
            if(effect.target()!=EffectTarget.ALL_OPPONENT_POKEMON||!effect.arguments().isEmpty()||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("中毒进退化保留规则只接受无条件对手全场范围");
            return;
        }
        if(effect.type().equals("setup_active_override")) {
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty()||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("开局战斗位例外只接受无条件SELF根规则");
            return;
        }
        if(effect.type().equals("first_turn_attack_permission")) {
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty()||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("首回合攻击许可只接受无条件SELF持续规则");
            return;
        }
        if(effect.type().equals("passive_trainer_play_lock")) {
            if(effect.target()!=EffectTarget.OPPONENT_ACTIVE
                ||!effect.arguments().keySet().stream().allMatch(Set.of("trainer_categories","source_active_only")::contains)
                ||!effect.arguments().containsKey("trainer_categories")
                ||effect.arguments().containsKey("source_active_only")&&!"true".equals(effect.arguments().get("source_active_only")))
                throw new IllegalArgumentException("训练家使用封锁参数无效");
            var categories=Set.of(effect.arguments().get("trainer_categories").split("\\|",-1));
            if(categories.isEmpty()||!Set.of("ITEM","TOOL","STADIUM","ACE_SPEC").containsAll(categories))
                throw new IllegalArgumentException("训练家使用封锁种类无效");
            return;
        }
        if(effect.type().equals("passive_pokemon_play_lock")) {
            if(effect.target()!=EffectTarget.OPPONENT_ACTIVE||!effect.arguments().equals(java.util.Map.of(
                "source_active_only","true","has_abilities","true","except_name_contains","Team Rocket's"))||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("宝可梦使用封锁只接受战斗位来源、带特性目标及火箭队名称例外");
            return;
        }
        if(effect.type().equals("basic_energy_multiplier_aura")) {
            if(effect.target()!=EffectTarget.ALL_OWN_POKEMON||!effect.arguments().equals(java.util.Map.of(
                "energy_type","GRASS","units","2","nonstack_group","wild-growth"))||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("基础能量倍增光环须明确草能、2单位及不叠加组");
            return;
        }
        if(effect.type().equals("bench_entry_damage_counters")) {
            if(effect.target()!=EffectTarget.SELF||(!effect.arguments().equals(java.util.Map.of("amount","2"))
                &&!effect.arguments().equals(java.util.Map.of("amount","2","excluded_type","DARKNESS")))||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("备战入场指示物场地须明确2个指示物，可选排除恶属性");
            return;
        }
        if(effect.type().equals("add_pokemon_types")) {
            boolean toolCondition=effect.conditions().size()==1&&effect.conditions().getFirst().type()==ConditionType.HAS_TOOL_DEFINITION
                &&EffectTarget.fromWire(effect.conditions().getFirst().arguments().getOrDefault("target","SELF"))==EffectTarget.SELF
                &&effect.conditions().getFirst().arguments().containsKey("definition_ids");
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().keySet().equals(Set.of("pokemon_types"))||!effect.conditions().isEmpty()&&!toolCondition
                ||PokemonTypeFilter.fromWire(effect.arguments().get("pokemon_types")).types().size()<2)
                throw new IllegalArgumentException("追加宝可梦属性须为自身至少两种明确属性");
            return;
        }
        if(effect.type().equals("weakness_override_aura")) {
            if(effect.target()!=EffectTarget.ALL_OPPONENT_POKEMON||!effect.arguments().keySet().equals(Set.of("pokemon_type","weakness_type","multiplier"))
                ||!"2".equals(effect.arguments().get("multiplier"))||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("弱点覆盖光环须指定对方全场、对象属性、新弱点和二倍倍率");
            dev.hua.ptcglive.engine.field.EnergyType.valueOf(effect.arguments().get("pokemon_type"));
            dev.hua.ptcglive.engine.field.EnergyType.valueOf(effect.arguments().get("weakness_type"));return;
        }
        if(effect.type().equals("suppress_self_knockout_abilities")) {
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty()||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("自昏厥特性压制只接受无条件SELF根规则");
            return;
        }
        if(effect.type().equals("prize_reduction_aura")) {
            boolean shadow=effect.target()==EffectTarget.ALL_OWN_POKEMON&&effect.arguments().equals(java.util.Map.of(
                "pokemon_type","DARKNESS","attacker_filter","EX","amount","1","nonstack_group","shadowy-concealment"))&&effect.conditions().isEmpty();
            boolean self=effect.target()==EffectTarget.SELF&&effect.arguments().equals(java.util.Map.of(
                "amount","1","nonstack_group","oh-no-you-dont"))&&effect.conditions().size()==1
                &&effect.conditions().getFirst().type()==ConditionType.POKEMON_COUNT;
            boolean legacyEnergy=effect.target()==EffectTarget.SELF&&effect.arguments().equals(java.util.Map.of(
                "amount","1","nonstack_group","legacy-energy","once_per_game","true"))&&effect.conditions().isEmpty();
            if(!shadow&&!self&&!legacyEnergy)
                throw new IllegalArgumentException("奖赏减少光环只接受已审核的恶属性、对方ex攻击者及不叠加组");
            return;
        }
        if(effect.type().equals("coin_prize_reduction")) {
            if(effect.target()!=EffectTarget.SELF
                ||!effect.arguments().equals(java.util.Map.of("amount","1","source_active_only","true"))
                    &&!effect.arguments().equals(java.util.Map.of("all_prizes","true"))
                ||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("掷币奖赏效果仅支持战斗位自身少1张，或自身在任意位置昏厥时阻止全部奖赏");
            return;
        }
        if(effect.type().equals("retreat_attempt_coin_gate")) {
            if(effect.target()!=EffectTarget.OPPONENT_ACTIVE||!effect.arguments().isEmpty()||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("撤退尝试掷币只接受对方战斗位，不接受参数或条件");
            return;
        }
        if(effect.type().equals("prevent_damage_counter_move")) {
            if(!Set.of(EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON).contains(effect.target())||!effect.arguments().isEmpty())
                throw new IllegalArgumentException("持续禁止转移须明确整方场上范围，不能带参数");
            return;
        }
        if(effect.type().equals("prevent_healing")) {
            boolean active=effect.target()==EffectTarget.OPPONENT_ACTIVE&&effect.arguments().isEmpty();
            boolean field=Set.of(EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON).contains(effect.target())
                &&(effect.arguments().isEmpty()||effect.arguments().equals(java.util.Map.of("source_active_only","true")));
            if(!active&&!field)throw new IllegalArgumentException("持续禁止回复须作用对手战斗位，或由战斗位来源作用于整方场上");
            return;
        }
        if(effect.type().equals("bench_limit")) {
            if(effect.target()==EffectTarget.OWN_BENCH&&effect.arguments().equals(java.util.Map.of("amount","3"))&&effect.conditions().isEmpty())return;
            if(effect.target()==EffectTarget.SELF&&effect.arguments().equals(java.util.Map.of("amount","8","leave_limit","5"))&&effect.conditions().isEmpty())return;
            if(effect.target()==EffectTarget.SELF&&effect.arguments().equals(java.util.Map.of("amount","4"))&&effect.conditions().isEmpty())return;
            if(effect.target()!=EffectTarget.OPPONENT_BENCH||!effect.arguments().equals(java.util.Map.of("amount","3","source_active_only","true")))
                throw new IllegalArgumentException("备战区上限目前只核验战斗位来源将对手备战区限制为3");
            return;
        }
        if(effect.type().equals("weakness_multiplier")) {
            if(!Set.of(EffectTarget.OWN_ACTIVE,EffectTarget.OPPONENT_ACTIVE).contains(effect.target())
                ||!Set.of("3","4").contains(effect.arguments().get("amount"))||effect.arguments().size()!=1)
                throw new IllegalArgumentException("持续弱点替换目前只核验战斗位三倍或四倍弱点");
            return;
        }
        if(effect.type().equals("special_condition_immunity")) {
            if(!Set.of(EffectTarget.SELF,EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON).contains(effect.target()))throw new IllegalArgumentException("持续异常状态免疫须为自身或明确整方范围");
            immuneConditions(effect);return;
        }
        if(Set.of("no_resistance","prevent_item_supporter_effects").contains(effect.type())) {
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty()||!effect.conditions().isEmpty())
                throw new IllegalArgumentException("全场无抗性须为无条件场地根规则");
            return;
        }
        if(effect.type().equals("no_weakness")) {
            if(effect.target()==EffectTarget.ALL_OWN_POKEMON&&effect.arguments().equals(java.util.Map.of("restored_only","true"))&&effect.conditions().isEmpty())return;
            boolean self=effect.target()==EffectTarget.SELF&&effect.conditions().isEmpty();
            boolean ownField=effect.target()==EffectTarget.ALL_OWN_POKEMON&&effect.conditions().isEmpty();
            boolean basicSelf=effect.target()==EffectTarget.SELF&&effect.conditions().size()==1
                &&effect.conditions().getFirst().type()==ConditionType.IS_BASIC
                &&EffectTarget.fromWire(effect.conditions().getFirst().arguments().getOrDefault("target","SELF"))==EffectTarget.SELF;
            boolean typedSelf=effect.target()==EffectTarget.SELF&&effect.conditions().size()==1
                &&effect.conditions().getFirst().type()==ConditionType.IS_TYPE
                &&EffectTarget.fromWire(effect.conditions().getFirst().arguments().getOrDefault("target","SELF"))==EffectTarget.SELF
                &&effect.conditions().getFirst().arguments().containsKey("pokemon_type");
            boolean typedField=Set.of(EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON).contains(effect.target())
                &&effect.conditions().size()==1&&effect.conditions().getFirst().type()==ConditionType.IS_TYPE
                &&"SELF".equals(effect.conditions().getFirst().arguments().getOrDefault("target","SELF"))
                &&effect.conditions().getFirst().arguments().containsKey("pokemon_type");
            if(typedSelf||typedField)dev.hua.ptcglive.engine.field.EnergyType.valueOf(effect.conditions().getFirst().arguments().get("pokemon_type"));
            boolean energyField=Set.of(EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON).contains(effect.target())
                &&effect.conditions().size()==1&&effect.conditions().getFirst().type()==ConditionType.ENERGY_COUNT
                &&"SELF".equals(effect.conditions().getFirst().arguments().getOrDefault("target","SELF"));
            if(energyField)EnergyCountQuery.from(effect.conditions().getFirst().arguments());
            if(!self&&!ownField&&!basicSelf&&!typedSelf&&!typedField&&!energyField||!effect.arguments().isEmpty())throw new IllegalArgumentException("无弱点规则须为审核过的自身或全场筛选");
            return;
        }
        if(effect.type().equals("evolution_timing_override")) {
            if(grassEvolutionTiming(effect))return;
            boolean unconditional=effect.conditions().isEmpty();
            boolean opponentEx=effect.conditions().size()==1&&effect.conditions().getFirst().type()==ConditionType.IS_EX
                &&EffectTarget.fromWire(effect.conditions().getFirst().arguments().getOrDefault("target","SELF"))==EffectTarget.OPPONENT_ACTIVE;
            boolean namedOwnField=effect.conditions().size()==1&&effect.conditions().getFirst().type()==ConditionType.POKEMON_COUNT
                &&EffectTarget.fromWire(effect.conditions().getFirst().arguments().getOrDefault("target","SELF"))==EffectTarget.SELF
                &&"1".equals(effect.conditions().getFirst().arguments().get("value"))
                &&">=".equals(effect.conditions().getFirst().arguments().get("comparison"))
                &&effect.conditions().getFirst().arguments().containsKey("pokemon_names");
            boolean activeSelf=effect.conditions().size()==1&&effect.conditions().getFirst().type()==ConditionType.IS_ACTIVE
                &&EffectTarget.fromWire(effect.conditions().getFirst().arguments().getOrDefault("target","SELF"))==EffectTarget.SELF;
            boolean secondPlayerFirstTurn=effect.conditions().size()==1&&effect.conditions().getFirst().type()==ConditionType.TURN_STATE
                &&EffectTarget.fromWire(effect.conditions().getFirst().arguments().getOrDefault("target","SELF"))==EffectTarget.SELF
                &&"SECOND_PLAYER_FIRST_TURN".equals(effect.conditions().getFirst().arguments().get("value"));
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty()||(!unconditional&&!opponentEx&&!namedOwnField&&!activeSelf&&!secondPlayerFirstTurn))
                throw new IllegalArgumentException("进化时点覆盖条件尚未审核支持");
            return;
        }
        if(effect.type().equals("prevent_checkup_recovery")) {
            if(!Set.of(EffectTarget.SELF,EffectTarget.OPPONENT_ACTIVE).contains(effect.target())||!effect.arguments().equals(java.util.Map.of("status","BURNED")))
                throw new IllegalArgumentException("检查恢复限制目前只接受对手战斗位灼伤");
        } else {
            var fields=new java.util.HashSet<>(Set.of("source","from","scope","trainer_categories","source_active_only"));
            fields.addAll(AttackSourceFilter.FIELDS);
            boolean stadiumWall=effect.target()==EffectTarget.ALL_OWN_POKEMON&&"TRAINER".equals(effect.arguments().get("source"))
                &&"ANY".equals(effect.arguments().get("from"))&&"EFFECTS_ONLY".equals(effect.arguments().get("scope"))
                &&"STADIUM".equals(effect.arguments().get("trainer_categories"));
            if(!Set.of(EffectTarget.SELF,EffectTarget.OWN_BENCH,EffectTarget.ALL_OWN_POKEMON).contains(effect.target())
                ||!effect.arguments().keySet().containsAll(Set.of("source","from","scope"))
                ||!fields.containsAll(effect.arguments().keySet())
                ||!"OPPONENT".equals(effect.arguments().get("from"))&&!stadiumWall)
                throw new IllegalArgumentException("被动效果防护须明确己方范围、来源和对手关系");
            var source=effect.arguments().get("source");var scope=effect.arguments().get("scope");
            var filter=AttackSourceFilter.from(effect.arguments());
            if(!"ATTACK".equals(source)&&effect.arguments().keySet().stream().anyMatch(AttackSourceFilter.FIELDS::contains))
                throw new IllegalArgumentException("攻击者筛选仅适用于攻击来源防护");
            boolean trainer="TRAINER".equals(source)&&"DAMAGE_AND_EFFECTS".equals(scope)&&effect.target()==EffectTarget.SELF
                &&"ITEM|SUPPORTER".equals(effect.arguments().get("trainer_categories"));
            boolean selfSupporterWall=selfSupporterEffectsGuard(effect);
            boolean activeSupporterWall="TRAINER".equals(source)&&"EFFECTS_ONLY".equals(scope)&&effect.target()==EffectTarget.ALL_OWN_POKEMON
                &&"SUPPORTER".equals(effect.arguments().get("trainer_categories"))&&"true".equals(effect.arguments().get("source_active_only"));
            boolean benchSupporterWall="TRAINER".equals(source)&&"EFFECTS_ONLY".equals(scope)&&effect.target()==EffectTarget.OWN_BENCH
                &&"SUPPORTER".equals(effect.arguments().get("trainer_categories"));
            if(!trainer&&!selfSupporterWall&&!activeSupporterWall&&!benchSupporterWall&&!stadiumWall&&effect.arguments().containsKey("trainer_categories"))throw new IllegalArgumentException("训练家效果防护只接受已审核的训练家类别与己方场上范围");
            if(!trainer&&!selfSupporterWall&&!activeSupporterWall&&!benchSupporterWall&&!stadiumWall&&!("ATTACK".equals(source)&&"EFFECTS_ONLY".equals(scope))
                && !("ATTACK".equals(source)&&"DAMAGE_AND_EFFECTS".equals(scope)
                    &&effect.target()==EffectTarget.SELF&&(filter.kind()!=AttackSourceFilter.Kind.ANY||filter.typeRestricted()))
                && !("ABILITY".equals(source)&&"DAMAGE_AND_EFFECTS".equals(scope)&&effect.target()==EffectTarget.SELF))
                throw new IllegalArgumentException("未审核的持续效果防护组合");
        }
    }
    public static java.util.Set<dev.hua.ptcglive.engine.field.SpecialCondition> immuneConditions(EffectDefinition effect) {
        if(!effect.type().equals("special_condition_immunity")||!effect.effects().isEmpty())throw new IllegalArgumentException("不是持续异常状态免疫");
        return ClearSpecialConditionsEffect.from(new EffectDefinition("clear_status",effect.target(),effect.arguments(),effect.conditions(),effect.effects())).conditions();
    }
    public static EffectProtection protection(EffectDefinition effect) {
        validate(effect);
        if(!effect.type().equals("passive_effect_guard"))throw new IllegalArgumentException("不是效果防护");
        return new EffectProtection(EffectProtection.Source.valueOf(effect.arguments().get("source")),EffectProtection.Relation.valueOf(effect.arguments().get("from")),EffectProtection.Scope.valueOf(effect.arguments().get("scope")),AttackSourceFilter.from(effect.arguments()));
    }
    // prevent_retreat has both an active operation and a separately validated passive form.
    // Query that form only inside PASSIVE bindings; do not reclassify the active operation.
    private static boolean queryable(EffectDefinition effect) {
        return handles(effect.type())||effect.type().equals("prevent_retreat");
    }
    private static boolean contains(EffectDefinition effect,Predicate<EffectDefinition> rule) {
        return queryable(effect)&&rule.test(effect)||effect.effects().stream().anyMatch(e->contains(e,rule));
    }
    public static boolean containsGuard(EffectProgram program) {
        return program.triggers().stream().filter(b->b.trigger()==EffectTrigger.PASSIVE).flatMap(b->b.effects().stream()).anyMatch(e->contains(e,x->x.type().equals("passive_effect_guard")));
    }
    public static boolean matches(EffectProgram program,EffectConditionContext context,Predicate<EffectDefinition> rule) {
        for(var binding:program.triggers())if(binding.trigger()==EffectTrigger.PASSIVE
            &&binding.effects().stream().anyMatch(e->contains(e,rule))
            &&binding.conditions().stream().allMatch(c->ConditionEvaluator.matches(c,context)))
            for(var effect:binding.effects())if(matches(effect,context,rule))return true;
        return false;
    }
    private static boolean matches(EffectDefinition effect,EffectConditionContext context,Predicate<EffectDefinition> rule) {
        if(!contains(effect,rule)||!effect.conditions().stream().allMatch(c->ConditionEvaluator.matches(c,context)))return false;
        if(effect.type().equals("conditional"))return effect.effects().stream().anyMatch(e->matches(e,context,rule));
        return queryable(effect)&&rule.test(effect);
    }
}
