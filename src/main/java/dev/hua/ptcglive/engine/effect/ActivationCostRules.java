package dev.hua.ptcglive.engine.effect;

import java.util.*;

/** Mandatory hand-discard activation cost with an optional audited Energy filter, checked before play and paid before any reward. */
public final class ActivationCostRules {
    private ActivationCostRules() { }
    public static int handCost(EffectDefinition effect) {
        if (!effect.arguments().containsKey("as_cost")) {
            if(effect.arguments().containsKey("requires_effect"))throw new IllegalArgumentException("requires_effect只能限定发动成本");
            return 0;
        }
        if (!effect.type().equals("discard") || effect.target()!=EffectTarget.HAND
            || !effect.arguments().keySet().containsAll(Set.of("amount","minimum","as_cost"))
            || !Set.of("amount","minimum","as_cost","card_category","trainer_category","basic_energy_only","energy_type","requires_effect","destination","definition_ids","battle_style","pokemon_types","bind","count_mode","required_definition_groups").containsAll(effect.arguments().keySet())
            || !"true".equals(effect.arguments().get("as_cost")) || !effect.conditions().isEmpty() || !effect.effects().isEmpty())
            throw new IllegalArgumentException("弃手牌成本必须是无条件的 HAND discard，明确相同的 amount/minimum 和 as_cost:true");
        var args=effect.arguments();
        if(args.containsKey("required_definition_groups")) {
            var grouped=new LinkedHashMap<>(args);grouped.remove("required_definition_groups");
            if(!grouped.equals(Map.of("amount","3","minimum","3","as_cost","true","card_category","TRAINER")))
                throw new IllegalArgumentException("三位姐妹成本须弃置三组指定身份的训练家各一张");
            definitionGroups(effect);
        }
        if(args.containsKey("bind")||args.containsKey("count_mode")) {
            var counted=new LinkedHashMap<>(args);AttackCountBindings.name(counted.remove("bind"));
            String mode=counted.getOrDefault("count_mode","");
            if(!Set.of("ENERGY_CARDS","TEAM_AQUA_POKEMON","TEAM_MAGMA_POKEMON").contains(mode)
                ||!counted.equals(Map.of("amount","1","minimum","1","as_cost","true","count_mode",mode)))
                throw new IllegalArgumentException("能量卡计数成本须恰好弃置一张手牌");
        }
        if(args.containsKey("battle_style")) {
            if(!args.equals(Map.of("amount","1","minimum","1","as_cost","true","battle_style","SINGLE_STRIKE")))
                throw new IllegalArgumentException("战斗风格发动成本须恰好弃置一张一击卡");
        }
        var destinationArgs=new LinkedHashMap<>(args);destinationArgs.remove("requires_effect");
        if(args.containsKey("destination")&&!ZoneSelectionEffect.maintenanceShuffle(effect)&&!"LOST_ZONE".equals(args.get("destination"))
            &&!("DECK_BOTTOM".equals(args.get("destination"))&&destinationArgs.equals(Map.of("amount","1","minimum","1","as_cost","true","destination","DECK_BOTTOM"))))
            throw new IllegalArgumentException("发动成本的特殊目的地只支持放逐或已审核的一张手牌置底");
        // Printed mandatory discard may itself be an effect (Team Plasma Grunt), even with an empty deck.
        boolean standaloneDiscard=args.containsKey("definition_ids")&&args.equals(Map.of("amount","1","minimum","1","as_cost","true",
            "definition_ids",args.get("definition_ids"),"requires_effect","false"));
        boolean namedTrainer="TRAINER".equals(args.get("card_category"))&&(args.containsKey("definition_ids")||args.containsKey("required_definition_groups"));
        boolean item=args.equals(Map.of("amount","1","minimum","1","as_cost","true","card_category","TRAINER","trainer_category","ITEM"));
        boolean darknessPokemon=args.equals(Map.of("amount","1","minimum","1","as_cost","true","requires_effect","true","card_category","POKEMON","pokemon_types","DARKNESS"));
        if(args.containsKey("pokemon_types")&&!darknessPokemon)throw new IllegalArgumentException("宝可梦属性成本仅核验弃置1张恶宝可梦并要求后续效果可执行");
        if(args.containsKey("trainer_category")&&!item)throw new IllegalArgumentException("物品发动成本须恰好弃置一张其他物品卡");
        if(args.containsKey("card_category")&&!"ENERGY".equals(args.get("card_category"))&&!namedTrainer&&!item&&!darknessPokemon)
            throw new IllegalArgumentException("发动成本筛选只支持能量或明确身份的训练家");
        if(args.containsKey("definition_ids")) {
            if(!(namedTrainer||standaloneDiscard)||args.containsKey("destination")||!"1".equals(args.get("amount")))
                throw new IllegalArgumentException("指定身份发动成本须恰好弃置一张训练家");
            if(standaloneDiscard) {
                if(EffectCardSelectionSpec.from(effect).allowedDefinitionIds().isEmpty())throw new IllegalArgumentException("独立弃牌效果必须有明确卡牌身份");
            } else TurnHistoryCondition.values(args.get("definition_ids"));
        }
        if(args.containsKey("basic_energy_only")&&(!"true".equals(args.get("basic_energy_only"))||!"ENERGY".equals(args.get("card_category"))))
            throw new IllegalArgumentException("基础能量成本必须明确ENERGY和basic_energy_only:true");
        if(args.containsKey("energy_type")) {
            if(!"true".equals(args.get("basic_energy_only")))throw new IllegalArgumentException("手牌属性成本必须限定基础能量");
            if(!"FIRE|FIGHTING".equals(args.get("energy_type")))dev.hua.ptcglive.engine.field.EnergyType.valueOf(args.get("energy_type"));
        }
        if(args.containsKey("requires_effect")&&!"true".equals(args.get("requires_effect"))&&!standaloneDiscard)
            throw new IllegalArgumentException("独立弃牌效果须明确一种指定身份的单张弃牌");
        int amount=Integer.parseInt(effect.arguments().get("amount"));
        if(amount<1||amount>60||amount!=Integer.parseInt(effect.arguments().get("minimum")))
            throw new IllegalArgumentException("发动成本必须完整支付1至60张，不能按不足数量支付");
        return amount;
    }
    public static boolean contains(EffectDefinition effect) {
        return effect.arguments().containsKey("as_cost") || effect.effects().stream().anyMatch(ActivationCostRules::contains);
    }
    public static List<Set<String>> definitionGroups(EffectDefinition effect) {
        String value=effect.arguments().get("required_definition_groups");if(value==null)return List.of();
        var groups=Arrays.stream(value.split(";",-1)).map(TurnHistoryCondition::values).toList();
        var all=new HashSet<String>();
        if(groups.size()!=3||groups.stream().anyMatch(g->g.isEmpty()||g.stream().anyMatch(id->!all.add(id))))
            throw new IllegalArgumentException("三位姐妹须为三组互不重叠的印刷身份");
        return groups;
    }
    public static boolean validGroups(EffectDefinition effect,List<String> definitions,boolean complete) {
        var groups=definitionGroups(effect);if(groups.isEmpty())return true;
        return (!complete||definitions.size()==groups.size())&&definitions.stream().allMatch(id->groups.stream().anyMatch(g->g.contains(id)))
            &&groups.stream().allMatch(g->definitions.stream().filter(g::contains).count()<=1);
    }
    public static int validate(EffectProgram program, EffectTrigger trigger) {
        int cost=0;
        for(var binding:program.triggers()) for(int i=0;i<binding.effects().size();i++) {
            var effect=binding.effects().get(i);
            if(!contains(effect))continue;
            if(program.triggers().size()!=1 || i!=0 || cost!=0
                || !Set.of(EffectTrigger.ON_CARD_PLAYED,EffectTrigger.ONCE_PER_TURN,EffectTrigger.ONCE_PER_GAME,EffectTrigger.ON_ACTIVATE).contains(trigger))
                throw new IllegalArgumentException("发动成本只能是主动特性或训练家程序的第一个根步骤");
            cost=handCost(effect);
            if(cost==0)throw new IllegalArgumentException("发动成本不能嵌套在分支、选择或重复中");
        }
        for(var binding:program.triggers())for(int i=0;i<binding.effects().size();i++) {
            var effect=binding.effects().get(i);
            if(excludesCost(effect)&&!(cost==2&&trigger==EffectTrigger.ON_CARD_PLAYED&&i>0
                &&effect.arguments().containsKey("exclude_activation_cost")&&effect.effects().isEmpty()))
                throw new IllegalArgumentException("成本排除需要此前已支付的两张手牌成本，不能嵌套或跨程序引用");
        }
        return cost;
    }
    private static boolean excludesCost(EffectDefinition effect) {
        return effect.arguments().containsKey("exclude_activation_cost")||effect.effects().stream().anyMatch(ActivationCostRules::excludesCost);
    }
    /** Validated first root cost, if present; consumers must count matching physical hand cards. */
    public static EffectDefinition costEffect(EffectProgram program,EffectTrigger trigger) {
        return validate(program,trigger)==0?null:program.triggers().getFirst().effects().getFirst();
    }
    public static void requireAffordable(EffectProgram program,EffectTrigger trigger,java.util.function.ToLongFunction<EffectDefinition> candidates) {
        var cost=costEffect(program,trigger);
        if(cost!=null&&candidates.applyAsLong(cost)<handCost(cost))throw new IllegalStateException("符合筛选的手牌不足以完整支付发动成本");
    }
    public static void requireAffordable(EffectProgram program,EffectTrigger trigger,long availableCards) {
        var cost=costEffect(program,trigger);
        if(cost!=null&&cost.arguments().containsKey("card_category"))throw new IllegalArgumentException("带筛选的成本必须提供候选数量校验");
        if(availableCards<validate(program,trigger))throw new IllegalStateException("除正在使用的卡以外，手牌不足以完整支付发动成本");
    }
}
