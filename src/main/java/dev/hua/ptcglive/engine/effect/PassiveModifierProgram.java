package dev.hua.ptcglive.engine.effect;

import java.util.*;
import java.util.function.Predicate;
import dev.hua.ptcglive.engine.field.EnergyType;

/** Pure queries over live sources. These modifiers never create lingering state or perform actions. */
public final class PassiveModifierProgram {
    public enum Source { TOOL, STADIUM, ENERGY, ABILITY }
    public record Values(int retreatCostChange,int attackCostChange,int damageDealtChange,int damageReceivedChange,int toolCapacity,int hitPointsChange,int prizeCardsChange,int activeDamageDealtChange,boolean attackProhibited,boolean freeRetreat,int opponentDamageDealtChange,int opponentDamageReceivedChange,boolean ignoreColorlessAttackCost,boolean ignoreAllAttackCost,Map<EnergyType,Integer> typedAttackCostChanges,int ownActiveDamageDealtChange,int baseHitPoints) {
        public Values {typedAttackCostChanges=Map.copyOf(typedAttackCostChanges);}
        public Values(int retreat,int attackCost,int dealt,int received,int tools,int hitPoints,int prizes,int activeDealt,boolean prohibited,boolean freeRetreat,int opponentDealt,int opponentReceived,boolean ignoreColorless,boolean ignoreAll,Map<EnergyType,Integer> typed) {this(retreat,attackCost,dealt,received,tools,hitPoints,prizes,activeDealt,prohibited,freeRetreat,opponentDealt,opponentReceived,ignoreColorless,ignoreAll,typed,0,0);}
        public Values(int retreat,int attackCost,int dealt,int received,int tools,int hitPoints,int prizes,int activeDealt,boolean prohibited,boolean freeRetreat,int opponentDealt,int opponentReceived,boolean ignoreColorless,boolean ignoreAll) {this(retreat,attackCost,dealt,received,tools,hitPoints,prizes,activeDealt,prohibited,freeRetreat,opponentDealt,opponentReceived,ignoreColorless,ignoreAll,Map.of());}
        public Values(int retreatCostChange,int attackCostChange,int damageDealtChange,int damageReceivedChange,int toolCapacity,int hitPointsChange,int prizeCardsChange,int activeDamageDealtChange,boolean attackProhibited,boolean freeRetreat,int opponentDamageDealtChange,int opponentDamageReceivedChange,boolean ignoreColorlessAttackCost) {this(retreatCostChange,attackCostChange,damageDealtChange,damageReceivedChange,toolCapacity,hitPointsChange,prizeCardsChange,activeDamageDealtChange,attackProhibited,freeRetreat,opponentDamageDealtChange,opponentDamageReceivedChange,ignoreColorlessAttackCost,false);}
        public Values(int retreat,int attackCost,int dealt,int received,int tools,int hitPoints,int prizes,int activeDealt,boolean prohibited,boolean freeRetreat,int opponentDealt,int opponentReceived) {this(retreat,attackCost,dealt,received,tools,hitPoints,prizes,activeDealt,prohibited,freeRetreat,opponentDealt,opponentReceived,false);}
        public Values(int retreat,int attackCost,int dealt,int received,int tools,int hitPoints,int prizes,int activeDealt,boolean prohibited,boolean freeRetreat) {this(retreat,attackCost,dealt,received,tools,hitPoints,prizes,activeDealt,prohibited,freeRetreat,0,0,false);}
        public Values(int retreat,int attackCost,int dealt,int received,int tools,int hitPoints,int prizes,int activeDealt,boolean prohibited) {this(retreat,attackCost,dealt,received,tools,hitPoints,prizes,activeDealt,prohibited,false);}
        public Values(int retreat,int attackCost,int dealt,int received,int tools,int hitPoints,int prizes) {this(retreat,attackCost,dealt,received,tools,hitPoints,prizes,0,false);}
        public Values(int retreat,int dealt,int received,int tools) {this(retreat,0,dealt,received,tools,0,0);}
        public Values(int retreat,int dealt,int received,int tools,int hitPoints) {this(retreat,0,dealt,received,tools,hitPoints,0);}
        public Values(int retreat,int attackCost,int dealt,int received,int tools,int hitPoints) {this(retreat,attackCost,dealt,received,tools,hitPoints,0);}
        public Values(boolean ignoreColorlessAttackCost) {this(0,0,0,0,0,0,0,0,false,false,0,0,ignoreColorlessAttackCost);}
        public static final Values NONE=new Values(0,0,0,0,0,0,0);
        public Values plus(Values other) {
            var typed=new EnumMap<EnergyType,Integer>(EnergyType.class);typed.putAll(typedAttackCostChanges);
            other.typedAttackCostChanges.forEach((type,amount)->typed.merge(type,amount,Math::addExact));
            return new Values(Math.addExact(retreatCostChange,other.retreatCostChange),Math.addExact(attackCostChange,other.attackCostChange),
                Math.addExact(damageDealtChange,other.damageDealtChange),Math.addExact(damageReceivedChange,other.damageReceivedChange),
                Math.max(toolCapacity,other.toolCapacity),Math.addExact(hitPointsChange,other.hitPointsChange),Math.addExact(prizeCardsChange,other.prizeCardsChange),
                Math.addExact(activeDamageDealtChange,other.activeDamageDealtChange),attackProhibited||other.attackProhibited,freeRetreat||other.freeRetreat,
                Math.addExact(opponentDamageDealtChange,other.opponentDamageDealtChange),Math.addExact(opponentDamageReceivedChange,other.opponentDamageReceivedChange),
                ignoreColorlessAttackCost||other.ignoreColorlessAttackCost,ignoreAllAttackCost||other.ignoreAllAttackCost,typed,Math.addExact(ownActiveDamageDealtChange,other.ownActiveDamageDealtChange),Math.max(baseHitPoints,other.baseHitPoints));
        }
        /** Scope is decided per actual damage recipient, including an attack's own Bench damage. */
        public int damageDealtChange(boolean opposingOwners) {return Math.addExact(damageDealtChange,opposingOwners?opponentDamageDealtChange:0);}
        public int damageReceivedChange(boolean opposingOwners) {return Math.addExact(damageReceivedChange,opposingOwners?opponentDamageReceivedChange:0);}
    }
    private static final Set<EffectTarget> TARGETS=Set.of(EffectTarget.SELF,EffectTarget.OWN_ACTIVE,EffectTarget.OPPONENT_ACTIVE,
        EffectTarget.OWN_BENCH,EffectTarget.OPPONENT_BENCH,EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON);
    private PassiveModifierProgram() { }
    public static boolean handles(String type) {return Set.of("free_retreat","ignore_colorless_attack_cost","ignore_all_attack_cost","retreat_cost_modifier","attack_cost_modifier","attack_damage_modifier","tool_capacity","special_energy_override","hit_points_modifier","prize_card_modifier").contains(type);}
    public static void validateModifier(EffectDefinition effect) {
        if(!handles(effect.type())||!TARGETS.contains(effect.target())||!effect.effects().isEmpty())
            throw new IllegalArgumentException("持续修正须指定固定场上范围，不能选择目标或包含动作");
        if(Set.of("free_retreat","ignore_colorless_attack_cost","ignore_all_attack_cost").contains(effect.type())) {
            if(effect.type().equals("ignore_all_attack_cost")) {
                if(!effect.arguments().isEmpty()&&(!effect.arguments().keySet().equals(Set.of("attack_id"))||!effect.arguments().get("attack_id").matches("[a-z0-9]+(?:-[a-z0-9]+)*")))
                    throw new IllegalArgumentException("无视全部招式费用须指定有效attack_id");
            } else if(!effect.arguments().isEmpty())throw new IllegalArgumentException("无撤退费用不接受数值或额外参数");
            return;
        }
        var allowed=switch(effect.type()) {
            case "attack_damage_modifier" -> Set.of("amount","source_active_only","source_benched_only","direction","per_opponent_prize_taken","per_own_prize_taken","both_active_only","opponent_active_only","opponent_only","defender_pokemon_types","defender_pokemon_families","defender_family_match","defender_stage","nonstack_group");
            case "attack_cost_modifier" -> Set.of("amount","energy_type","per_opponent_family","per_opponent_bench","per_opponent_prize_taken","per_discard_definition_ids","source_active_only","nonstack_group");
            case "retreat_cost_modifier" -> Set.of("amount","source_active_only","source_benched_only");
            case "prize_card_modifier" -> Set.of("amount","opponent_attack_damage_only");
            case "special_energy_override" -> Set.of("amount","energy_type","suppress_other_effects");
            case "hit_points_modifier" -> Set.of("amount","set_base_hp","per_attached_energy","max_energy","card_kind","minimum_energy_units","energy_type","per_opponent_prize_taken","nonstack_group");
            default -> Set.of("amount");
        };
        if(!allowed.containsAll(effect.arguments().keySet()))throw new IllegalArgumentException("持续修正包含未支持参数");
        if(effect.arguments().containsKey("both_active_only")&&!(effect.arguments().equals(Map.of("amount","50","direction","DEALT","both_active_only","true"))&&effect.target()==EffectTarget.SELF
            ||effect.arguments().equals(Map.of("amount","10","direction","DEALT","both_active_only","true"))&&effect.target()==EffectTarget.ALL_OWN_POKEMON))
            throw new IllegalArgumentException("双方战斗位持续加伤仅核验自身50点道具或全场10点特性");
        if(effect.arguments().containsKey("set_base_hp")&&(!effect.arguments().equals(Map.of("amount","300","set_base_hp","true"))||effect.target()!=EffectTarget.SELF))
            throw new IllegalArgumentException("最大HP基值替换仅核验自身300HP");
        if(effect.arguments().containsKey("opponent_attack_damage_only")
            &&(!"true".equals(effect.arguments().get("opponent_attack_damage_only"))||!Set.of(EffectTarget.SELF,EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON).contains(effect.target())))
            throw new IllegalArgumentException("招式伤害昏厥奖赏修正须明确限定宿主或整方范围和对手招式伤害");
        if(effect.arguments().containsKey("per_own_prize_taken")&&(!"true".equals(effect.arguments().get("per_own_prize_taken"))
            ||!"DEALT".equals(effect.arguments().get("direction"))||!"true".equals(effect.arguments().get("opponent_active_only"))
            ||effect.arguments().containsKey("per_opponent_prize_taken")))throw new IllegalArgumentException("己方已获奖赏仅用于对对手战斗位加伤");
        if(effect.type().equals("attack_cost_modifier")&&effect.arguments().containsKey("energy_type")
            &&(!"-1".equals(effect.arguments().get("amount"))||!Set.of("FIGHTING","LIGHTNING").contains(effect.arguments().get("energy_type"))
                ||!effect.arguments().keySet().equals(Set.of("amount","energy_type"))))throw new IllegalArgumentException("属性费用修正仅核验减少一个斗或雷费用");
        if(effect.arguments().containsKey("nonstack_group")&&!effect.arguments().get("nonstack_group").matches("[a-z0-9]+(?:-[a-z0-9]+)*"))
            throw new IllegalArgumentException("持续修正不叠加组无效");
        if(effect.arguments().containsKey("opponent_only")&&(!"true".equals(effect.arguments().get("opponent_only"))
            ||effect.arguments().containsKey("opponent_active_only")))
            throw new IllegalArgumentException("对手范围须明确为true，不能与仅对手战斗位混用");
        if(effect.arguments().containsKey("per_opponent_prize_taken")
            &&(!"true".equals(effect.arguments().get("per_opponent_prize_taken"))
                ||!Set.of("attack_cost_modifier","hit_points_modifier").contains(effect.type())&&!"DEALT".equals(effect.arguments().get("direction"))))
            throw new IllegalArgumentException("奖赏计数须为true，且只能用于攻击费用或DEALT伤害");
        if(effect.arguments().containsKey("per_opponent_bench")
            &&(!effect.type().equals("attack_cost_modifier")||!"true".equals(effect.arguments().get("per_opponent_bench"))))
            throw new IllegalArgumentException("对手备战区计数只允许攻击费用修正并须明确true");
        if(effect.arguments().containsKey("opponent_active_only")
            &&(!"true".equals(effect.arguments().get("opponent_active_only"))||!"DEALT".equals(effect.arguments().get("direction"))))
            throw new IllegalArgumentException("仅对手战斗位加伤须为true且为DEALT");
        if(effect.arguments().keySet().stream().anyMatch(AttackDefenderFilter.FIELDS::contains)) {
            if(!effect.type().equals("attack_damage_modifier")||!"DEALT".equals(effect.arguments().get("direction"))
                ||!"true".equals(effect.arguments().get("opponent_active_only"))
                ||!Set.of(EffectTarget.SELF,EffectTarget.OWN_ACTIVE,EffectTarget.OWN_BENCH,EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON).contains(effect.target()))
                throw new IllegalArgumentException("受伤方筛选仅适用于己方攻击者对对手战斗位的加伤");
            AttackDefenderFilter.from(effect.arguments());
        }
        if(effect.arguments().containsKey("per_opponent_family"))
            PokemonFamilyFilter.fromWire(effect.arguments().get("per_opponent_family"),"ANY");
        if(effect.arguments().containsKey("per_discard_definition_ids"))TurnHistoryCondition.values(effect.arguments().get("per_discard_definition_ids"));
        if(effect.arguments().containsKey("source_active_only") && !"true".equals(effect.arguments().get("source_active_only")))
            throw new IllegalArgumentException("来源战斗位限制须明确为true");
        if(effect.arguments().containsKey("source_benched_only") && (!"true".equals(effect.arguments().get("source_benched_only")) || effect.arguments().containsKey("source_active_only")))
            throw new IllegalArgumentException("来源备战位限制须明确为true，不能与来源战斗位混用");
        boolean energyScaled=effect.type().equals("hit_points_modifier")&&effect.arguments().containsKey("per_attached_energy");
        if(!effect.arguments().containsKey("amount")&&!energyScaled)
            throw new IllegalArgumentException("持续修正须提供amount，或为HP修正提供per_attached_energy");
        int amount=effect.arguments().containsKey("amount")?integer(effect.arguments().get("amount"),"持续修正amount"):0;
        switch(effect.type()) {
            case "retreat_cost_modifier" -> {if(amount==0||amount < -10||amount > 10)throw new IllegalArgumentException("撤退修正须为-10至10的非零整数");}
            case "attack_cost_modifier" -> {if(amount==0||amount < -10||amount > 10)throw new IllegalArgumentException("攻击无色费用修正须为-10至10的非零整数");}
            case "attack_damage_modifier" -> {
                if(amount==0||amount < -1000||amount > 1000||amount%10!=0)throw new IllegalArgumentException("伤害修正须为-1000至1000的非零10倍数");
                if(!Set.of("DEALT","RECEIVED").contains(effect.arguments().getOrDefault("direction","")))throw new IllegalArgumentException("伤害修正须明确DEALT或RECEIVED");
            }
            case "tool_capacity" -> {if(amount<1||amount>4)throw new IllegalArgumentException("工具容量须为1至4");}
            case "prize_card_modifier" -> {if(amount==0||amount < -2||amount > 3)throw new IllegalArgumentException("奖赏修正须为-2至3的非零整数");}
            case "hit_points_modifier" -> {
                if(effect.arguments().containsKey("minimum_energy_units")) {
                    int minimum=integer(effect.arguments().get("minimum_energy_units"),"HP能量门槛");
                    if(minimum<1||minimum>60||energyScaled||effect.target()!=EffectTarget.SELF)throw new IllegalArgumentException("HP能量门槛须为自身1至60单位，不能混用逐张加成");
                    dev.hua.ptcglive.engine.field.EnergyType.valueOf(effect.arguments().getOrDefault("energy_type",""));
                } else if(effect.arguments().containsKey("energy_type")&&!energyScaled)throw new IllegalArgumentException("HP能量属性须搭配单位门槛或逐能量加成");
                if(amount!=0&&(amount < -1000||amount > 1000||amount%10!=0))throw new IllegalArgumentException("HP 修正amount须为-1000至1000的10倍数");
                if(energyScaled) {
                    int per=integer(effect.arguments().get("per_attached_energy"),"HP 每张附着能量修正");
                    if(per==0||per < -1000||per > 1000||per%10!=0)throw new IllegalArgumentException("HP 每张附着能量修正须为-1000至1000的非零10倍数");
                    if(effect.arguments().containsKey("max_energy")) {
                        int max=integer(effect.arguments().get("max_energy"),"HP 每张附着能量上限");
                        if(max<1||max>60)throw new IllegalArgumentException("HP 每张附着能量上限须为1至60");
                    }
                    if(effect.arguments().containsKey("card_kind"))try {EnergyCountQuery.CardKind.valueOf(effect.arguments().get("card_kind"));}
                    catch(RuntimeException invalid) {throw new IllegalArgumentException("HP 每张附着能量的card_kind无效",invalid);}
                    if(effect.arguments().containsKey("energy_type"))dev.hua.ptcglive.engine.field.EnergyType.valueOf(effect.arguments().get("energy_type"));
                } else if(effect.arguments().containsKey("max_energy")||effect.arguments().containsKey("card_kind"))
                    throw new IllegalArgumentException("max_energy和card_kind须与per_attached_energy一起使用");
                if(amount==0&&!energyScaled)throw new IllegalArgumentException("HP 修正不能为0");
            }
            case "special_energy_override" -> {
                if(amount<1||amount>4||!Set.of(EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON).contains(effect.target())
                    ||!"true".equals(effect.arguments().get("suppress_other_effects"))||!effect.conditions().isEmpty())
                    throw new IllegalArgumentException("特殊能量替换目前只支持无条件整方场上的1至4单位供能及关闭其它效果");
                dev.hua.ptcglive.engine.field.EnergyType.valueOf(effect.arguments().getOrDefault("energy_type",""));
            }
            default -> throw new IllegalArgumentException("未知持续修正");
        }
    }
    public static void validate(EffectProgram program,Source source) {
        Objects.requireNonNull(program);Objects.requireNonNull(source);int[] budget={4096};
        if(program.triggers().stream().flatMap(b->b.effects().stream()).anyMatch(AbilitySuppressionProgram::basicZoneStadium)
            &&!AbilitySuppressionProgram.basicZoneStadium(program))
            throw new IllegalArgumentException("基础宝可梦跨区域特性压制须为唯一无条件被动效果");
        if(ToolGrantedAttack.fromProgram(program)!=null) {
            if(source!=Source.TOOL)throw new IllegalArgumentException("道具招式只能由所附道具授予");
            return;
        }
        if(BorrowedAttackProgram.contains(program)) {
            BorrowedAttackProgram.validate(program,source);return;
        }
        if(AttackCoinRetry.contains(program)) {
            var permission=AttackCoinRetry.fromProgram(program);
            if(source!=Source.TOOL&&(source!=Source.STADIUM||permission.pokemonType()!=null))
                throw new IllegalArgumentException("攻击重投须由所附道具或不限定属性的场地授予");
            return;
        }
        if(AttackTailsDraw.contains(program)) {
            AttackTailsDraw.fromProgram(program);
            if(source!=Source.TOOL)throw new IllegalArgumentException("攻击反面回合末抽牌须来自所附道具");
            return;
        }
        var energyScopes=EnumSet.noneOf(EffectTarget.class);
        for(var binding:program.triggers()) {
            if(binding.trigger()!=EffectTrigger.PASSIVE)throw new IllegalArgumentException("持续来源目前只支持PASSIVE查询，触发动作尚未接入");
            if(binding.effects().stream().anyMatch(e->Set.of("add_pokemon_families","prize_energy_attach","knockout_prizes_to_discard","knockout_pokemon_to_lost_zone","discard_from_field_to_hand").contains(e.type()))&&!binding.conditions().isEmpty())
                throw new IllegalArgumentException("场上自弃回手不能依赖触发层条件");
            if(binding.effects().stream().anyMatch(e->Set.of("discard_replacing_stadium","inherit_poison_on_own_trainer_switch").contains(e.type()))&&!binding.conditions().isEmpty())
                throw new IllegalArgumentException("场地替换弃置须为无条件根层规则");
            binding.conditions().forEach(PassiveModifierProgram::condition);
            if(source==Source.ENERGY&&binding.effects().stream().anyMatch(PassiveModifierProgram::energyGuard))binding.conditions().forEach(PassiveModifierProgram::energyGuardCondition);
            if(binding.effects().stream().anyMatch(AbilitySuppressionProgram::contains)) {
                binding.conditions().forEach(AbilitySuppressionProgram::validateDependency);
                if(source==Source.ABILITY&&(!binding.conditions().isEmpty()||binding.effects().stream().anyMatch(e->AbilitySuppressionProgram.contains(e)&&!AbilitySuppressionProgram.ruleBoxSource(e)&&!AbilitySuppressionProgram.zoneSource(e)&&!AbilitySuppressionProgram.acyclicActiveSource(e))))
                    throw new IllegalArgumentException("特性压制来源当前须为不含额外依赖的根层规则");
            }
            binding.effects().forEach(effect->validate(effect,source,0,budget));
            if(binding.effects().stream().anyMatch(PassiveModifierProgram::hasHitPoints))binding.conditions().forEach(PassiveModifierProgram::hpCondition);
            for(var effect:binding.effects())if(effect.type().equals("special_energy_override")) {
                if(!binding.conditions().isEmpty())throw new IllegalArgumentException("特殊能量替换不能依赖触发层条件");
                if(!energyScopes.add(effect.target()))throw new IllegalArgumentException("同一范围的特殊能量替换重复，优先级未定义");
            }
        }
    }
    private static void validate(EffectDefinition effect,Source source,int depth,int[] budget) {
        if(depth>32||--budget[0]<0)throw new IllegalArgumentException("持续修正嵌套或数量超过安全上限");
        EffectSchemaValidator.validate(effect);effect.conditions().forEach(PassiveModifierProgram::condition);
        if(AbilitySuppressionProgram.contains(effect))effect.conditions().forEach(AbilitySuppressionProgram::validateDependency);
        if(hasHitPoints(effect))effect.conditions().forEach(PassiveModifierProgram::hpCondition);
        if(source==Source.ENERGY&&energyGuard(effect))effect.conditions().forEach(PassiveModifierProgram::energyGuardCondition);
        if(effect.type().equals("conditional")) {
            if(!effect.arguments().isEmpty()||effect.target()!=EffectTarget.SELF)throw new IllegalArgumentException("持续条件容器不接受目标或额外参数");
            effect.effects().forEach(child->validate(child,source,depth+1,budget));return;
        }
        if(effect.type().equals("prevent_opponent_attack_ability_counters")) {
            if(source!=Source.TOOL)throw new IllegalArgumentException("伤害指示物防护只允许宿主道具来源");
            PassiveRuleProgram.validate(effect);return;
        }
        if(effect.type().equals("bench_attack_damage_bonus")) {
            boolean ability=source==Source.ABILITY&&effect.target()==EffectTarget.ALL_OWN_POKEMON;
            boolean tool=source==Source.TOOL&&effect.target()==EffectTarget.SELF;
            if((!ability&&!tool)||depth!=0)throw new IllegalArgumentException("备战加伤须来自整方被动特性或宿主道具根规则");
            PassiveRuleProgram.validate(effect);return;
        }
        if(Set.of("attack_weakness_multiplier","bench_weakness_resistance").contains(effect.type())) {
            if(source!=Source.TOOL||depth!=0)throw new IllegalArgumentException("攻击弱点规则仅允许宿主道具根层");
            PassiveRuleProgram.validate(effect);return;
        }
        if (AbilitySuppressionProgram.handles(effect.type())) {
            if(AbilitySuppressionProgram.basicZoneStadium(effect)&&(source!=Source.STADIUM||depth!=0))
                throw new IllegalArgumentException("基础宝可梦跨区域特性压制只允许场地根效果");
            if(source==Source.ABILITY&&!AbilitySuppressionProgram.ruleBoxSource(effect)&&!AbilitySuppressionProgram.zoneSource(effect)&&!AbilitySuppressionProgram.acyclicActiveSource(effect))
                throw new IllegalArgumentException("特性压制目前仅支持战斗位规则宝可梦压制对方基础非规则宝可梦，其它来源循环尚未核验");
            if(source!=Source.ABILITY&&(effect.arguments().containsKey("source_active_only")||effect.arguments().containsKey("source_rule_box_only")))
                throw new IllegalArgumentException("来源位置和规则框约束仅供特性来源");
            if(source!=Source.ABILITY&&AbilitySuppressionProgram.zoneSource(effect))throw new IllegalArgumentException("跨区域移除仅由独立的进化特性来源提供");
            AbilitySuppressionProgram.validateShape(effect);
            return;
        }
        if(PassiveAttackGuard.handles(effect.type())) {
            if(source!=Source.ABILITY&&source!=Source.TOOL&&source!=Source.ENERGY&&source!=Source.STADIUM)throw new IllegalArgumentException("被动攻击防护来源无效");
            if((source==Source.TOOL||source==Source.ENERGY)&&effect.target()!=EffectTarget.SELF)throw new IllegalArgumentException("工具防护目前仅作用于所附宝可梦");
            if(source==Source.STADIUM&&effect.arguments().containsKey("source_position"))throw new IllegalArgumentException("场地防护没有宝可梦来源位置");
            if(source==Source.STADIUM&&(depth!=0||!Set.of(EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON).contains(effect.target())))
                throw new IllegalArgumentException("竞技场攻击防护只允许根层整方场上范围");
            PassiveAttackGuard.from(effect);return;
        }
        if(source==Source.TOOL&&depth==0&&ClearSpecialConditionsEffect.onAttach(effect)
            &&effect.target()==EffectTarget.SELF) {
            ClearSpecialConditionsEffect.from(effect);return;
        }
        if(PassiveRuleProgram.handles(effect.type())) {
            if(effect.type().equals("no_weakness")&&effect.arguments().containsKey("restored_only")) {
                if(source!=Source.ABILITY||depth!=0)throw new IllegalArgumentException("复原屏障只核验被动特性根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("flare_gear")) {
                if(source!=Source.TOOL||depth!=0)throw new IllegalArgumentException("闪焰队装备只接受道具根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("cafe_master_turn_continues")) {
                if(source!=Source.ABILITY||depth!=0)throw new IllegalArgumentException("追加点单只接受被动特性根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("add_pokemon_families")) {
                if(source!=Source.TOOL||depth!=0)throw new IllegalArgumentException("等离子团身份附加只允许道具根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("prize_energy_attach")) {
                if(source!=Source.ENERGY||depth!=0)throw new IllegalArgumentException("奖赏入手前附能只接受能量卡的根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("knockout_prizes_to_discard")) {
                if(source!=Source.TOOL||depth!=0)throw new IllegalArgumentException("击倒奖赏弃置只接受道具根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("knockout_pokemon_to_lost_zone")) {
                if(source!=Source.STADIUM||depth!=0)throw new IllegalArgumentException("击倒宝可梦送入放逐区须为场地根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("discard_from_field_to_hand")) {
                if(!Set.of(Source.TOOL,Source.ENERGY).contains(source)||depth!=0)
                    throw new IllegalArgumentException("场上自弃回手只接受附着道具或能量根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("retreat_energy_to_hand")) {
                if(depth!=0||!(source==Source.TOOL&&"ANY".equals(effect.arguments().get("energy_kind"))
                    ||source==Source.STADIUM&&"BASIC".equals(effect.arguments().get("energy_kind"))))
                    throw new IllegalArgumentException("撤退能量回手只允许已审核道具全能量或场地基本能量根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("tool_hand_play_guard")) {
                if(source!=Source.TOOL||depth!=0)throw new IllegalArgumentException("道具从手牌使用限制须为道具根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("pokemon_type_override")) {
                if(source!=Source.TOOL||depth!=0)throw new IllegalArgumentException("属性记忆碟只允许附着道具根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("prevent_checkup_recovery")&&effect.target()==EffectTarget.SELF) {
                if(source!=Source.STADIUM||depth!=0||!effect.conditions().isEmpty())throw new IllegalArgumentException("双方灼伤检查限制只允许场地根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("prevent_self_attack_damage")) {
                if(source!=Source.TOOL||depth!=0)throw new IllegalArgumentException("自身招式反伤保护只允许附着道具根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("prevent_item_supporter_effects")) {
                if(source!=Source.STADIUM||depth!=0)throw new IllegalArgumentException("竞技场本体防护只允许竞技场根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("no_resistance")) {
                if(source!=Source.STADIUM||depth!=0)throw new IllegalArgumentException("全场无抗性只允许竞技场根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("special_condition_immunity")&&effect.target()!=EffectTarget.SELF) {
                if(source!=Source.STADIUM||depth!=0)throw new IllegalArgumentException("整方异常状态免疫只允许竞技场根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(source==Source.STADIUM&&depth==0&&effect.type().equals("bench_limit")) {
                if(!Set.of(EffectTarget.SELF,EffectTarget.OWN_BENCH).contains(effect.target()))throw new IllegalArgumentException("场地备战上限须作用于双方或朝向的一侧");
                PassiveRuleProgram.validate(effect);return;
            }
            if(source==Source.STADIUM&&depth==0&&effect.type().equals("no_weakness")
                &&Set.of(EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON).contains(effect.target())) {
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("stadium_attack_guard")) {
                if(source!=Source.STADIUM||depth!=0)throw new IllegalArgumentException("竞技场攻击防护只允许场地根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("disable_all_tools")) {
                if(source!=Source.STADIUM||depth!=0)throw new IllegalArgumentException("全场道具失效只允许竞技场根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("disable_opponent_active_tools")) {
                if(source!=Source.TOOL||depth!=0)throw new IllegalArgumentException("战斗位道具干扰只允许工具根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("add_pokemon_types")) {
                if(source!=Source.ABILITY||depth!=0)throw new IllegalArgumentException("追加宝可梦属性只允许自身被动特性根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("weakness_override_aura")) {
                if(source!=Source.ABILITY||depth!=0)throw new IllegalArgumentException("弱点覆盖光环只允许自身被动特性根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("suppress_self_knockout_abilities")) {
                if(source!=Source.ABILITY||depth!=0)throw new IllegalArgumentException("自昏厥特性压制只允许被动特性根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("prize_reduction_aura")) {
                boolean legacyEnergy=source==Source.ENERGY&&effect.target()==EffectTarget.SELF
                    &&"legacy-energy".equals(effect.arguments().get("nonstack_group"));
                if(source!=Source.ABILITY&&!legacyEnergy||depth!=0)throw new IllegalArgumentException("奖赏减少光环只允许被动特性或已审核特殊能量根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("setup_active_override")||effect.type().equals("basic_energy_multiplier_aura")) {
                if(source!=Source.ABILITY||depth!=0)throw new IllegalArgumentException("此持续规则只允许宝可梦被动特性根层");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("bench_entry_damage_counters")) {
                if(source!=Source.STADIUM||depth!=0)throw new IllegalArgumentException("备战入场指示物只允许场地根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("checkup_poison_bonus")) {
                if(!Set.of(Source.STADIUM,Source.ABILITY).contains(source)||depth!=0)throw new IllegalArgumentException("检查中毒加成只允许场地或特性根规则");
                PassiveRuleProgram.validate(effect);return;
            }
            if(effect.type().equals("tera_bench_expansion")||effect.type().equals("energy_special_condition_immunity")||effect.type().equals("festival_ground")||effect.type().equals("discard_replacing_stadium")||effect.type().equals("inherit_poison_on_own_trainer_switch")) {
                if(source!=Source.STADIUM||depth!=0)throw new IllegalArgumentException("此持续规则只允许场地根层");
                PassiveRuleProgram.validate(effect);return;
            }
            if(PassiveRuleProgram.grassEvolutionTiming(effect)) {
                if(source!=Source.STADIUM||depth!=0)throw new IllegalArgumentException("草进化时点覆盖只允许场地根层");
                return;
            }
            if(source==Source.STADIUM && depth==0 && Set.of("prevent_healing","sleep_recovery_two_coins","prevent_bench_damage_counters","retain_confusion_on_evolution", "retain_conditions_on_evolution").contains(effect.type())) {
                PassiveRuleProgram.validate(effect);return;
            }
            if(source==Source.TOOL&&(PassiveRuleProgram.selfAbilityEffectsGuard(effect)||PassiveRuleProgram.plasmaMirrorGuard(effect)||PassiveRuleProgram.selfSupporterEffectsGuard(effect)||PassiveRuleProgram.selfAttackEffectsGuard(effect)||PassiveRuleProgram.activeToolFieldAttackGuard(effect))) {
                PassiveRuleProgram.validate(effect);return;
            }
            if(source==Source.TOOL&&effect.target()==EffectTarget.SELF&&Set.of("skip_evolution_turn_end","attack_while_asleep_or_paralyzed","retreat_while_asleep_or_paralyzed","special_condition_immunity","no_weakness","prevent_deck_discard").contains(effect.type())) {
                PassiveRuleProgram.validate(effect);return;
            }
            if(source==Source.ENERGY) {
                if(effect.target()!=EffectTarget.SELF||!Set.of("passive_effect_guard","no_weakness","special_condition_immunity").contains(effect.type()))throw new IllegalArgumentException("特殊能量持续规则目前只支持宿主防护及无弱点");
            } else if(source!=Source.ABILITY)throw new IllegalArgumentException("此持续规则尚未接入指定来源");
            PassiveRuleProgram.validate(effect);return;
        }
        if(effect.type().equals("unrecoverable_from_discard")) {
            boolean stadium=source==Source.STADIUM&&effect.target()==EffectTarget.SELF&&effect.arguments().isEmpty();
            boolean ability=source==Source.ABILITY&&DiscardRecoveryRule.abilityMarker(effect);
            if(depth!=0||!stadium&&!ability||!effect.conditions().isEmpty()||!effect.effects().isEmpty())throw new IllegalArgumentException("弃牌不可回收须为场地标记或限制对手特性与训练家的被动特性");
            return;
        }
        if(LegendaryStadiumRule.handles(effect.type())) {
            if(source!=Source.STADIUM||depth!=0)throw new IllegalArgumentException("拼合规则仅属于竞技场根程序");
            LegendaryStadiumRule.validate(effect);return;
        }
        if(effect.type().equals("return_after_own_attack_discard")) {
            if(source!=Source.ENERGY||depth!=0)throw new IllegalArgumentException("弃能回手仅属于特殊能量根程序");return;
        }
        if(effect.type().equals("reattach_after_own_attack_discard")) {
            if(source!=Source.ENERGY||depth!=0||effect.target()!=EffectTarget.SELF
                ||!effect.arguments().isEmpty()||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
                throw new IllegalArgumentException("攻击弃置后重新附着只允许特殊能量自身根规则");
            return;
        }
        if(effect.type().equals("prevent_attack")) {
            if(source==Source.STADIUM&&depth==0&&Set.of(EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON).contains(effect.target())
                &&effect.arguments().isEmpty()&&effect.effects().isEmpty()&&effect.conditions().size()==1
                &&effect.conditions().getFirst().type()==ConditionType.HP_BELOW
                &&effect.conditions().getFirst().arguments().equals(Map.of("target","SELF","value","41")))return;
            boolean self=effect.target()==EffectTarget.SELF&&effect.arguments().isEmpty();
            boolean opponent=effect.target()==EffectTarget.ALL_OPPONENT_POKEMON
                &&(effect.arguments().isEmpty()||effect.arguments().equals(java.util.Map.of("source_active_only","true")));
            if(source!=Source.ABILITY||opponent&&depth!=0||!self&&!opponent||!effect.effects().isEmpty())throw new IllegalArgumentException("持续攻击限制须为自身或特性作用于对手全场");
            return;
        }
        if(effect.type().equals("prevent_retreat")) {
            if(source!=Source.ABILITY||depth!=0||effect.target()!=EffectTarget.OPPONENT_ACTIVE
                ||!effect.arguments().equals(java.util.Map.of("source_active_only","true"))||!effect.effects().isEmpty())
                throw new IllegalArgumentException("持续撤退限制须由战斗位特性作用于对手战斗位");
            return;
        }
        if(effect.type().equals("attack_cost_override")) {
            if(source!=Source.ABILITY||depth!=0)throw new IllegalArgumentException("攻击费用覆盖只允许宝可梦自身被动特性根层");
            AttackCostOverride.from(effect);return;
        }
        if(BorrowedAttackProgram.handles(effect.type())) {
            if(source!=Source.ABILITY||depth!=0)throw new IllegalArgumentException("借招须为特性根层，不能由附着卡或嵌套效果授予");
            BorrowedAttackProgram.validate(effect);return;
        }
        validateModifier(effect);
        if(effect.arguments().containsKey("opponent_attack_damage_only")&&effect.target()!=EffectTarget.SELF&&source!=Source.STADIUM)
            throw new IllegalArgumentException("整方招式伤害昏厥奖赏修正须来自场地");
        if(effect.arguments().containsKey("both_active_only")&&(effect.target()==EffectTarget.SELF?source!=Source.TOOL:source!=Source.ABILITY)
            ||effect.arguments().containsKey("set_base_hp")&&source!=Source.TOOL)
            throw new IllegalArgumentException("双方战斗位加伤和HP基值替换仅核验附着道具");
        if(effect.arguments().containsKey("per_own_prize_taken")&&(source!=Source.TOOL||effect.target()!=EffectTarget.SELF))
            throw new IllegalArgumentException("己方已获奖赏加伤仅核验宿主道具");
        if(effect.arguments().containsKey("minimum_energy_units")&&source!=Source.ABILITY)throw new IllegalArgumentException("供能门槛HP目前只支持自身特性");
        if((effect.arguments().containsKey("per_opponent_family")||effect.arguments().containsKey("per_opponent_bench")||effect.arguments().containsKey("per_opponent_prize_taken")||effect.arguments().containsKey("per_discard_definition_ids"))
            &&(source!=Source.ABILITY||effect.target()!=EffectTarget.SELF))
            throw new IllegalArgumentException("场面计数持续修正当前须由特性作用于自身");
        boolean activeToolRetreat=source==Source.TOOL&&effect.type().equals("retreat_cost_modifier")&&effect.arguments().containsKey("source_active_only");
        if((effect.arguments().containsKey("source_active_only")||effect.arguments().containsKey("source_benched_only")) && source!=Source.ABILITY&&!activeToolRetreat)
            throw new IllegalArgumentException("来源战斗位限制仅供宝可梦被动特性使用");
        if(effect.type().equals("special_energy_override")&&(source!=Source.STADIUM||depth!=0))
            throw new IllegalArgumentException("特殊能量替换目前仅由场地根层提供；其它来源或嵌套依赖尚未实现");
        if(source==Source.STADIUM&&effect.target()==EffectTarget.SELF)throw new IllegalArgumentException("场地没有附着宝可梦，须明确作用场上范围");
    }
    private static boolean energyGuard(EffectDefinition effect) {
        return PassiveRuleProgram.handles(effect.type())||PassiveAttackGuard.handles(effect.type())||effect.effects().stream().anyMatch(PassiveModifierProgram::energyGuard);
    }
    private static void energyGuardCondition(ConditionDefinition condition) {
        if(condition.children().isEmpty()&&!Set.of(ConditionType.IS_TYPE,ConditionType.IS_ACTIVE,ConditionType.IS_BASIC,ConditionType.IS_EVOLVED,ConditionType.IS_FAMILY,ConditionType.HAS_RULE_BOX).contains(condition.type()))
            throw new IllegalArgumentException("能量防护只读取当前印刷宿主信息和位置，不能依赖特性、保护或动态数值");
        condition.children().forEach(PassiveModifierProgram::energyGuardCondition);
    }
    static void condition(ConditionDefinition condition) {
        EffectSchemaValidator.validate(condition);
        if(!condition.children().isEmpty()) {condition.children().forEach(PassiveModifierProgram::condition);return;}
        var target=EffectTarget.fromWire(condition.arguments().getOrDefault("target","SELF"));
        boolean opponentFact=target==EffectTarget.OPPONENT_ACTIVE
            &&Set.of(ConditionType.HAND_SIZE,ConditionType.PRIZE_COUNT,ConditionType.IS_EX,ConditionType.IS_EVOLVED,
                ConditionType.HAS_ABILITIES,ConditionType.DISCARD_COUNT).contains(condition.type());
        boolean opponentFieldCount=target==EffectTarget.ALL_OPPONENT_POKEMON&&condition.type()==ConditionType.POKEMON_COUNT
            &&Set.of("EX","V").contains(condition.arguments().get("pokemon_families"));
        if(condition.type()==ConditionType.COIN_RESULT||condition.type()==ConditionType.CUSTOM
            ||target!=EffectTarget.SELF&&!opponentFact&&!opponentFieldCount)
            throw new IllegalArgumentException("持续修正条件目前须读取被修正宝可梦SELF的当前状态，不支持选择、硬币或其它对象");
        ResumableProgramRules.condition(condition,true);
    }
    public static Values evaluate(EffectProgram program,EffectConditionContext state,Predicate<EffectTarget> applies) {
        return evaluate(program,state,applies,false);
    }
    private static boolean hasHitPoints(EffectDefinition effect) {
        return effect.type().equals("hit_points_modifier")||effect.effects().stream().anyMatch(PassiveModifierProgram::hasHitPoints);
    }
    private static void hpCondition(ConditionDefinition condition) {
        if(condition.children().isEmpty()&&!Set.of(ConditionType.IS_BASIC,ConditionType.IS_EVOLVED,ConditionType.IS_STAGE,ConditionType.IS_ACTIVE,ConditionType.HAS_ENERGY,ConditionType.RETREAT_COST,
            ConditionType.IS_EX,ConditionType.IS_V,ConditionType.IS_GX,ConditionType.IS_VSTAR,ConditionType.IS_LEGACY_EX,ConditionType.IS_VMAX,ConditionType.IS_TERA,ConditionType.IS_TYPE,ConditionType.IS_FAMILY,ConditionType.ALL_POKEMON_FAMILY,ConditionType.NAME_EQUALS,ConditionType.NAME_CONTAINS,ConditionType.HAS_RULE_BOX).contains(condition.type()))
            throw new IllegalArgumentException("HP 上限目前只支持卡牌阶段、类别及战斗位条件；HP、供能、区域数量及其它动态依赖尚未支持，不能按印刷值代替");
        condition.children().forEach(PassiveModifierProgram::hpCondition);
    }
    /** Pure capability check: avoids resolving suppression for abilities that cannot modify this quantity. */
    public static boolean hasValues(EffectProgram program,boolean hpOnly) {
        return program.triggers().stream().filter(b->b.trigger()==EffectTrigger.PASSIVE)
            .anyMatch(b->b.effects().stream().anyMatch(e->relevant(e,hpOnly)));
    }
    private static boolean relevant(EffectDefinition effect,boolean hpOnly) {
        return effect.type().equals("conditional")?effect.effects().stream().anyMatch(e->relevant(e,hpOnly))
            :!ToolGrantedAttack.handles(effect)&&!AttackCoinRetry.handles(effect)&&!AttackTailsDraw.handles(effect)&&!BorrowedAttackProgram.handles(effect.type())&&!AbilitySuppressionProgram.handles(effect.type())&&!PassiveAttackGuard.handles(effect.type())&&!PassiveRuleProgram.handles(effect.type())&&!Set.of("prevent_retreat","special_energy_override","unrecoverable_from_discard","reattach_after_own_attack_discard","return_after_own_attack_discard","legendary_stadium").contains(effect.type())&&effect.type().equals("hit_points_modifier")==hpOnly;
    }
    /** Separate HP layer: never evaluate conditions on unrelated modifier branches. */
    public static Values evaluate(EffectProgram program,EffectConditionContext state,Predicate<EffectTarget> applies,boolean hpOnly) {
        return evaluate(program,state,applies,hpOnly,false);
    }
    public static Values evaluate(EffectProgram program,EffectConditionContext state,Predicate<EffectTarget> applies,boolean hpOnly,boolean sourceActive) {
        Values result=Values.NONE;
        for(var binding:program.triggers()) {
            if(binding.trigger()!=EffectTrigger.PASSIVE)throw new IllegalStateException("持续来源包含未校验触发");
            if(binding.effects().stream().noneMatch(e->relevant(e,hpOnly)))continue;
            if(binding.conditions().stream().allMatch(c->ConditionEvaluator.matches(c,state)))
                for(var effect:binding.effects())result=result.plus(evaluate(effect,state,applies,hpOnly,sourceActive));
        }
        return result;
    }
    private static Values evaluate(EffectDefinition effect,EffectConditionContext state,Predicate<EffectTarget> applies,boolean hpOnly,boolean sourceActive) {
        // Energy-card overrides are a separate query layer, not effects placed on the host Pokémon.
        if(!relevant(effect,hpOnly))return Values.NONE;
        if(effect.arguments().containsKey("source_active_only")&&!sourceActive)return Values.NONE;
        if(effect.arguments().containsKey("source_benched_only")&&sourceActive)return Values.NONE;
        if(!effect.conditions().stream().allMatch(c->ConditionEvaluator.matches(c,state)))return Values.NONE;
        if(effect.type().equals("conditional")) {
            Values result=Values.NONE;for(var child:effect.effects())result=result.plus(evaluate(child,state,applies,hpOnly,sourceActive));return result;
        }
        if(!applies.test(effect.target()))return Values.NONE;
        if(effect.arguments().containsKey("opponent_attack_damage_only")&&!state.selfKnockedOutByOpponentAttackDamage())return Values.NONE;
        if(effect.type().equals("attack_damage_modifier")) {var defender=AttackDefenderFilter.from(effect.arguments());if(defender.restricted()&&!state.opponentActiveMatches(defender))return Values.NONE;}
        if(effect.type().equals("hit_points_modifier")&&effect.arguments().containsKey("minimum_energy_units")) {
            var query=new EnergyCountQuery(dev.hua.ptcglive.engine.field.EnergyType.valueOf(effect.arguments().get("energy_type")),EnergyCountQuery.Mode.UNITS,EnergyCountQuery.CardKind.ANY);
            if(state.energyUnitsForHitPoints(query)<integer(effect.arguments().get("minimum_energy_units"),"HP能量门槛"))return Values.NONE;
        }
        int amount=effect.arguments().containsKey("amount")?integer(effect.arguments().get("amount"),"持续修正amount"):0;
        if(effect.arguments().containsKey("per_opponent_family"))amount=Math.multiplyExact(amount,
            state.opponentPokemonCount(PokemonFamilyFilter.fromWire(effect.arguments().get("per_opponent_family"),"ANY")));
        if(effect.arguments().containsKey("per_opponent_bench"))amount=Math.multiplyExact(amount,state.opponentBenchCount());
        if(effect.arguments().containsKey("per_opponent_prize_taken"))amount=Math.multiplyExact(amount,state.opponentPrizeCardsTaken());
        if(effect.arguments().containsKey("per_own_prize_taken"))amount=Math.multiplyExact(amount,state.ownPrizeCardsTaken());
        if(effect.arguments().containsKey("per_discard_definition_ids"))amount=Math.multiplyExact(amount,
            state.discardDefinitionCount(TurnHistoryCondition.values(effect.arguments().get("per_discard_definition_ids"))));
        if(effect.type().equals("hit_points_modifier")&&effect.arguments().containsKey("per_attached_energy")) {
            var typed=effect.arguments().containsKey("energy_type");
            var query=new EnergyCountQuery(typed?dev.hua.ptcglive.engine.field.EnergyType.valueOf(effect.arguments().get("energy_type")):null,typed?EnergyCountQuery.Mode.UNITS:EnergyCountQuery.Mode.CARDS,
                effect.arguments().containsKey("card_kind")?EnergyCountQuery.CardKind.valueOf(effect.arguments().get("card_kind")):EnergyCountQuery.CardKind.ANY);
            int attached=typed?state.energyUnitsForHitPoints(query):state.printedAttachedEnergyCards(query);
            if(effect.arguments().containsKey("max_energy"))attached=Math.min(attached,integer(effect.arguments().get("max_energy"),"HP 每张附着能量上限"));
            amount=Math.addExact(amount,Math.multiplyExact(integer(effect.arguments().get("per_attached_energy"),"HP 每张附着能量修正"),attached));
        }
        return switch(effect.type()) {
            case "free_retreat" -> new Values(0,0,0,0,0,0,0,0,false,true);
            case "ignore_colorless_attack_cost" -> new Values(true);
            case "ignore_all_attack_cost" -> new Values(0,0,0,0,0,0,0,0,false,false,0,0,false,true);
            case "retreat_cost_modifier" -> new Values(amount,0,0,0);
            case "attack_cost_modifier" -> effect.arguments().containsKey("energy_type")
                ?new Values(0,0,0,0,0,0,0,0,false,false,0,0,false,false,Map.of(EnergyType.valueOf(effect.arguments().get("energy_type")),amount))
                :new Values(0,amount,0,0,0,0);
            case "attack_damage_modifier" -> effect.arguments().containsKey("both_active_only")
                ?new Values(0,0,0,0,0,0,0,amount,false,false,0,0,false,false,Map.of(),amount,0)
                :effect.arguments().containsKey("opponent_only")
                ?new Values(0,0,0,0,0,0,0,0,false,false,effect.arguments().get("direction").equals("DEALT")?amount:0,effect.arguments().get("direction").equals("RECEIVED")?amount:0)
                :effect.arguments().containsKey("opponent_active_only")?new Values(0,0,0,0,0,0,0,amount,false)
                :effect.arguments().get("direction").equals("DEALT")?new Values(0,amount,0,0):new Values(0,0,amount,0);
            case "prevent_attack" -> new Values(0,0,0,0,0,0,0,0,true);
            case "tool_capacity" -> new Values(0,0,0,amount);
            case "hit_points_modifier" -> effect.arguments().containsKey("set_base_hp")
                ?new Values(0,0,0,0,0,0,0,0,false,false,0,0,false,false,Map.of(),0,amount):new Values(0,0,0,0,amount);
            case "prize_card_modifier" -> new Values(0,0,0,0,0,0,amount);
            default -> throw new IllegalStateException("未接入的持续效果："+effect.type());
        };
    }
    private static int integer(String raw,String label) {
        try {return Integer.parseInt(raw);}
        catch(RuntimeException invalid) {throw new IllegalArgumentException(label+"须为整数",invalid);}
    }
    /** Validated Stadium-only, unconditional override; cannot recursively depend on energy or modifiers. */
    public static Optional<dev.hua.ptcglive.engine.field.EnergyProfile> specialEnergyOverride(EffectProgram program,boolean own) {
        var scope=own?EffectTarget.ALL_OWN_POKEMON:EffectTarget.ALL_OPPONENT_POKEMON;
        for(var binding:program.triggers())for(var effect:binding.effects())
            if(effect.type().equals("special_energy_override")&&effect.target()==scope)
                return Optional.of(dev.hua.ptcglive.engine.field.EnergyProfile.fixed(
                    dev.hua.ptcglive.engine.field.EnergyType.valueOf(effect.arguments().get("energy_type")),Integer.parseInt(effect.arguments().get("amount"))));
        return Optional.empty();
    }
    /** One printed non-stacking Ability group per controller contributes to a target query. */
    public static String nonstackGroup(EffectProgram program) {
        String found=null;
        for(var binding:program.triggers())for(var effect:binding.effects()) {
            String group=effect.arguments().get("nonstack_group");
            if(group==null)continue;
            if(found!=null&&!found.equals(group))throw new IllegalArgumentException("同一持续程序不能声明多个不叠加组");
            found=group;
        }
        return found;
    }
}
