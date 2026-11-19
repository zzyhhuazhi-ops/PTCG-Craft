package dev.hua.ptcglive.engine.effect;

import java.util.*;
import java.util.function.Predicate;

/** A live self Ability or attached Tool: block/reduce opposing attack damage after Weakness/Resistance, never effects. */
public record PassiveAttackGuard(int reduction,boolean prevent,AttackSourceFilter filter,String nonStackingGroup,PokemonSelectionScope.Position sourcePosition,int damageMinimum) {
    public PassiveAttackGuard(int reduction,boolean prevent,AttackSourceFilter filter) {this(reduction,prevent,filter,null);}
    public PassiveAttackGuard(int reduction,boolean prevent,AttackSourceFilter filter,String nonStackingGroup) {this(reduction,prevent,filter,nonStackingGroup,PokemonSelectionScope.Position.ANY,0);}
    public PassiveAttackGuard {Objects.requireNonNull(filter);Objects.requireNonNull(sourcePosition);}
    private static final Set<String> FIELDS;
    static {var fields=new HashSet<>(AttackSourceFilter.FIELDS);fields.addAll(Set.of("mode","amount","non_stacking_group","source_position","damage_min"));FIELDS=Set.copyOf(fields);}
    public static boolean handles(String type){return type.equals("passive_attack_guard");}
    private static boolean contains(EffectDefinition effect){return handles(effect.type())||effect.effects().stream().anyMatch(PassiveAttackGuard::contains);}
    public static PassiveAttackGuard from(EffectDefinition effect) {
        if(!handles(effect.type())||!Set.of(EffectTarget.SELF,EffectTarget.OWN_BENCH,EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON).contains(effect.target())||!effect.effects().isEmpty()
            ||!FIELDS.containsAll(effect.arguments().keySet()))
            throw new IllegalArgumentException("被动攻击防护必须指定自身或整方场上范围，且只能声明防护模式、数值和攻击者筛选");
        var args=effect.arguments();boolean prevent;
        if("PREVENT".equals(args.get("mode")))prevent=true;
        else if("REDUCE".equals(args.get("mode")))prevent=false;
        else throw new IllegalArgumentException("被动攻击防护须明确PREVENT或REDUCE");
        int amount=0;
        if(prevent){if(args.containsKey("amount"))throw new IllegalArgumentException("伤害免疫不接受amount");}
        else {amount=Integer.parseInt(args.getOrDefault("amount","0"));if(amount<10||amount>1000||amount%10!=0)throw new IllegalArgumentException("伤害减免须为10至1000的10倍数");}
        String group=args.get("non_stacking_group");
        if(group!=null&&(prevent||!group.matches("[a-z][a-z0-9-]{0,63}")))throw new IllegalArgumentException("不叠加组仅适用于减伤，且须为稳定小写标识");
        int minimum=Integer.parseInt(args.getOrDefault("damage_min","0"));
        if(minimum!=0&&(!prevent||minimum<10||minimum>1000||minimum%10!=0))throw new IllegalArgumentException("伤害下限防护须为PREVENT及10至1000的10倍数");
        return new PassiveAttackGuard(amount,prevent,AttackSourceFilter.from(args),group,PokemonSelectionScope.Position.valueOf(args.getOrDefault("source_position","ANY")),minimum);
    }
    public record Values(boolean prevent,int stackingReduction,Map<String,Integer> nonStackingReductions,int damageMinimum) {
        public Values(boolean prevent,int reduction) {this(prevent,reduction,Map.of());}
        public Values(boolean prevent,int reduction,Map<String,Integer> groups){this(prevent,reduction,groups,0);}
        public Values {nonStackingReductions=Map.copyOf(Objects.requireNonNull(nonStackingReductions));if(damageMinimum<0)throw new IllegalArgumentException("伤害下限不能为负数");}
        public static final Values NONE=new Values(false,0);
        public int reduction() {int total=stackingReduction;for(int amount:nonStackingReductions.values())total=Math.addExact(total,amount);return total;}
        public Values plus(Values value){
            var groups=new LinkedHashMap<>(nonStackingReductions);value.nonStackingReductions.forEach((key,amount)->groups.merge(key,amount,Math::max));
            int minimum=damageMinimum==0?value.damageMinimum:value.damageMinimum==0?damageMinimum:Math.min(damageMinimum,value.damageMinimum);
            return new Values(prevent||value.prevent,Math.addExact(stackingReduction,value.stackingReduction),groups,minimum);
        }
        public boolean prevents(int damage){return prevent||damageMinimum>0&&damage>=damageMinimum;}
    }
    public static Values evaluate(EffectProgram program,EffectConditionContext context,Predicate<AttackSourceFilter> matches,Predicate<EffectTarget> applies) {
        return evaluate(program,context,matches,applies,PokemonSelectionScope.Position.ANY);
    }
    /** Recipient conditions and the actual source host's position are deliberately separate facts. */
    public static Values evaluate(EffectProgram program,EffectConditionContext context,Predicate<AttackSourceFilter> matches,Predicate<EffectTarget> applies,PokemonSelectionScope.Position sourcePosition) {
        var result=Values.NONE;
        for(var binding:program.triggers())if(binding.trigger()==EffectTrigger.PASSIVE&&binding.effects().stream().anyMatch(PassiveAttackGuard::contains)&&binding.conditions().stream().allMatch(c->ConditionEvaluator.matches(c,context)))
            for(var effect:binding.effects())result=result.plus(evaluate(effect,context,matches,applies,sourcePosition));
        return result;
    }
    private static Values evaluate(EffectDefinition effect,EffectConditionContext context,Predicate<AttackSourceFilter> matches,Predicate<EffectTarget> applies,PokemonSelectionScope.Position sourcePosition) {
        if(!contains(effect)||!effect.conditions().stream().allMatch(c->ConditionEvaluator.matches(c,context)))return Values.NONE;
        if(effect.type().equals("conditional")){var result=Values.NONE;for(var child:effect.effects())result=result.plus(evaluate(child,context,matches,applies,sourcePosition));return result;}
        if(!handles(effect.type())||!applies.test(effect.target()))return Values.NONE;
        var guard=from(effect);
        if(guard.sourcePosition!=PokemonSelectionScope.Position.ANY&&guard.sourcePosition!=sourcePosition)return Values.NONE;
        return !matches.test(guard.filter)?Values.NONE:guard.damageMinimum>0?new Values(false,0,Map.of(),guard.damageMinimum)
            :guard.nonStackingGroup==null?new Values(guard.prevent,guard.reduction):new Values(false,0,Map.of(guard.nonStackingGroup,guard.reduction));
    }
}
