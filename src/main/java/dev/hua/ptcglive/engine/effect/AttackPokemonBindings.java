package dev.hua.ptcglive.engine.effect;

import java.util.*;

/** Data-only aliases for server-selected Pokémon incarnations; attacks may additionally share names across their three phases. */
public final class AttackPokemonBindings {
    private AttackPokemonBindings() { }
    public static boolean reference(EffectDefinition effect){return effect.type().equals("with_pokemon");}
    public static boolean usesNamedBindings(EffectProgram program) {
        return program.triggers().stream().flatMap(binding -> binding.effects().stream()).anyMatch(AttackPokemonBindings::usesNamedBindings);
    }
    private static boolean usesNamedBindings(EffectDefinition effect) {
        return reference(effect) || MoveDamageCounterEffect.handles(effect)&&effect.arguments().containsKey("source_ref") || PokemonSelectionScope.handles(effect) && effect.arguments().containsKey("bind")
            ||effect.arguments().containsKey("bind_pokemon")
            || effect.effects().stream().anyMatch(AttackPokemonBindings::usesNamedBindings);
    }
    public static String name(String name){
        if(name==null||!name.matches("[a-z][a-z0-9_-]{0,63}"))throw new IllegalArgumentException("宝可梦绑定名须为1至64位小写开头的字母、数字、下划线或短横线");
        return name;
    }
    public static void validateReference(EffectDefinition effect){
        if(!reference(effect)||effect.target()!=EffectTarget.SELF||effect.effects().isEmpty()
            ||!Set.of("ref","position").containsAll(effect.arguments().keySet()))
            throw new IllegalArgumentException("with_pokemon须声明ref、可选position和后续效果，不另设目标");
        name(effect.arguments().get("ref"));PokemonSelectionScope.position(effect);
    }
    public static void validate(EffectProgram program){
        var flow=new Flow();Map<String,PokemonSelectionScope.Position> names=new LinkedHashMap<>();
        for(var phase:List.of(EffectTrigger.BEFORE_ATTACK,EffectTrigger.ON_ATTACK,EffectTrigger.AFTER_ATTACK))
            for(var binding:program.forTrigger(phase))names=flow.sequence(binding.effects(),names,0);
    }
    /** Validates the one resumable entry of an activated Ability or Trainer. */
    public static void validate(EffectProgram program, EffectTrigger trigger) {
        var flow = new Flow();
        Map<String,PokemonSelectionScope.Position> names = new LinkedHashMap<>();
        for (var binding : program.forTrigger(trigger)) names = flow.sequence(binding.effects(), names, 0);
    }
    private static final class Flow {
        int budget=4096;
        Map<String,PokemonSelectionScope.Position> sequence(List<EffectDefinition> effects,Map<String,PokemonSelectionScope.Position> input,int depth){
            var names=new LinkedHashMap<>(input);
            for(var effect:effects)names=step(effect,names,depth);
            return names;
        }
        LinkedHashMap<String,PokemonSelectionScope.Position> step(EffectDefinition effect,Map<String,PokemonSelectionScope.Position> input,int depth){
            if(depth>32||--budget<0)throw new IllegalArgumentException("攻击目标数据流超出安全深度或步数");
            var names=new LinkedHashMap<>(input);
            if(effect.type().equals("delayed_effect")){DelayedEffectProgram.from(effect);return names;}
            if(MoveDamageCounterEffect.handles(effect)&&effect.arguments().containsKey("source_ref")) {
                String ref=name(effect.arguments().get("source_ref"));
                if(!input.containsKey(ref))throw new IllegalArgumentException("伤害指示物来源引用尚未定义："+ref);
            }
            if(PokemonSelectionScope.handles(effect)&&effect.arguments().containsKey("bind")){
                String name=name(effect.arguments().get("bind"));
                if(names.putIfAbsent(name,PokemonSelectionScope.position(effect))!=null)throw new IllegalArgumentException("同一攻击路径重复定义宝可梦："+name);
            }
            if(effect.arguments().containsKey("bind_pokemon")) {
                boolean evolution=TrainerDeckEvolution.handles(effect);
                String name=evolution?TrainerDeckEvolution.successBinding(effect):ZoneSelectionEffect.newlyBenchedPokemonBinding(effect);
                if(names.putIfAbsent(name,evolution?PokemonSelectionScope.Position.ANY:PokemonSelectionScope.Position.BENCH)!=null)
                    throw new IllegalArgumentException("同一效果路径重复定义宝可梦："+name);
            }
            if(reference(effect)){
                validateReference(effect);String name=effect.arguments().get("ref");
                if(!names.containsKey(name))throw new IllegalArgumentException("宝可梦引用尚未定义或不在此前路径："+name);
                var asserted=PokemonSelectionScope.position(effect);
                if(asserted!=PokemonSelectionScope.Position.ANY&&names.get(name)!=asserted)
                    throw new IllegalArgumentException("宝可梦引用position不能比原选择范围更窄："+name);
            }
            if(effect.type().equals("choice")){
                for(var child:effect.effects())for(var entry:step(child,input,depth+1).entrySet())
                    names.merge(entry.getKey(),entry.getValue(),(left,right)->left==right?left:PokemonSelectionScope.Position.ANY);
            }else{
                int count=effect.type().equals("repeat")?Integer.parseInt(effect.arguments().get("count"))
                    :effect.type().equals("repeat_per_heads")?20:1;
                for(int i=0;i<count;i++)names=new LinkedHashMap<>(sequence(effect.effects(),names,depth+1));
            }
            if(names.size()>64)throw new IllegalArgumentException("一次攻击最多绑定64个宝可梦名称");
            return names;
        }
    }
}
