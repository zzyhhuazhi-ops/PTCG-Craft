package dev.hua.ptcglive.engine.effect;

import java.util.*;

/** Named coin results live for one declared attack, never for a player, turn or delayed program. */
public final class AttackCoinBindings {
    private AttackCoinBindings() { }
    public static String name(String value) {
        if(value==null||!value.matches("[a-z][a-z0-9_-]{0,63}"))
            throw new IllegalArgumentException("攻击硬币名称须为1至64位小写字母、数字、下划线或短横线，且以字母开头");
        return value;
    }
    public static final class Memory {
        private final Map<String,EffectExecutionContext.CoinResult> results=new LinkedHashMap<>();
        public EffectExecutionContext.CoinResult get(String key){return results.get(name(key));}
        public void put(String key,EffectExecutionContext.CoinResult result){
            name(key);Objects.requireNonNull(result);
            if(results.containsKey(key)||results.size()>=64)throw new IllegalStateException("攻击硬币结果不能覆盖或超过64组");
            results.put(key,result);
        }
    }
    public static boolean references(ConditionDefinition condition){
        return condition.arguments().containsKey("coin")||condition.children().stream().anyMatch(AttackCoinBindings::references);
    }
    public static void validate(EffectProgram program){
        validateHeadsAttachmentBindings(program);
        var flow=new Flow();Set<String> names=new LinkedHashSet<>();
        // Execution phase order, not the order in which JSON happens to list the bindings.
        for(var phase:List.of(EffectTrigger.BEFORE_ATTACK,EffectTrigger.ON_ATTACK,EffectTrigger.AFTER_ATTACK))
            for(var binding:program.forTrigger(phase)){
                for(var c:binding.conditions())flow.condition(c,names,false);
                names=flow.sequence(binding.effects(),names,false,0);
            }
    }
    private static void validateHeadsAttachmentBindings(EffectProgram program) {
        Set<String> definite=new LinkedHashSet<>();
        int[] budget={4096};
        for(var phase:List.of(EffectTrigger.BEFORE_ATTACK,EffectTrigger.ON_ATTACK,EffectTrigger.AFTER_ATTACK))
            for(var binding:program.forTrigger(phase)) {
                var local=new LinkedHashSet<>(definite);
                definiteCoinSequence(binding.effects(),local,0,budget);
                if(binding.conditions().isEmpty())definite=local;
            }
    }
    private static void definiteCoinSequence(List<EffectDefinition> effects,Set<String> definite,int depth,int[] budget) {
        if(depth>32)throw new IllegalArgumentException("硬币附能数据流嵌套过深");
        for(var effect:effects) {
            if(--budget[0]<0)throw new IllegalArgumentException("硬币附能数据流超出安全步数");
            if(effect.type().equals("delayed_effect"))continue;
            if((AttachEnergyEffect.coinHeads(effect)||AttachedEnergyEffect.coinHeads(effect)||OpponentHandShuffle.handles(effect)&&effect.arguments().containsKey("coin"))&&!definite.contains(name(effect.arguments().get("coin"))))
                throw new IllegalArgumentException("选择数量所用硬币必须由此前同一路径实际生成，不能引用可跳过的或其他分支的结果");
            var local=new LinkedHashSet<>(definite);
            if(effect.type().equals("coin_flip")&&effect.arguments().containsKey("bind"))local.add(name(effect.arguments().get("bind")));
            if(effect.type().equals("choice")) {
                for(var child:effect.effects())definiteCoinSequence(List.of(child),new LinkedHashSet<>(local),depth+1,budget);
            } else definiteCoinSequence(effect.effects(),local,depth+1,budget);
            if(effect.conditions().isEmpty()&&!Set.of("conditional","choice","repeat_per_heads").contains(effect.type()))definite.addAll(local);
        }
    }
    private static final class Flow {
        int budget=4096;
        void condition(ConditionDefinition c,Set<String> names,boolean lexical){
            if(c.type()==ConditionType.COIN_RESULT){
                String key=c.arguments().get("coin");
                if(key!=null){if(!names.contains(name(key)))throw new IllegalArgumentException("攻击硬币引用尚未定义或不在此前路径中："+key);}
                else if(!lexical)throw new IllegalArgumentException("未命名硬币条件须处于对应coin_flip子树");
            }
            c.children().forEach(child->condition(child,names,lexical));
        }
        Set<String> sequence(List<EffectDefinition> effects,Set<String> input,boolean lexical,int depth){
            Set<String> names=new LinkedHashSet<>(input);
            for(var effect:effects)names=step(effect,names,lexical,depth);
            return names;
        }
        Set<String> step(EffectDefinition effect,Set<String> input,boolean lexical,int depth){
            if(depth>32||--budget<0)throw new IllegalArgumentException("攻击硬币数据流超出安全步数或深度");
            effect.conditions().forEach(c->condition(c,input,lexical));
            Set<String> names=new LinkedHashSet<>(input);
            if(effect.type().equals("delayed_effect")){DelayedEffectProgram.from(effect);return names;}
            if(effect.type().equals("coin_flip")&&effect.arguments().containsKey("bind")){
                String key=name(effect.arguments().get("bind"));
                if(!names.add(key))throw new IllegalArgumentException("同一攻击路径重复定义硬币："+key);
                if(names.size()>64)throw new IllegalArgumentException("一次攻击最多绑定64组硬币");
            }
            if(effect.type().equals("choice")){
                // Sibling options are exclusive; one must not read a definition from another.
                for(var child:effect.effects())names.addAll(step(child,input,lexical,depth+1));
            }else{
                int count=effect.type().equals("repeat")?Integer.parseInt(effect.arguments().get("count"))
                    :effect.type().equals("repeat_per_heads")?20:1;
                for(int i=0;i<count;i++)names=sequence(effect.effects(),names,lexical||effect.type().equals("coin_flip"),depth+1);
            }
            if(names.size()>64)throw new IllegalArgumentException("一次攻击最多绑定64组硬币");
            // These are may-definitions: a skipped conditional is UNAVAILABLE at runtime, not TAILS.
            return names;
        }
    }
}
