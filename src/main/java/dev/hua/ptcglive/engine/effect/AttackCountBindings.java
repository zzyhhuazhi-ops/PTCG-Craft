package dev.hua.ptcglive.engine.effect;

import java.util.*;

/**
 * “You may … If you do, this attack does N more damage (for each card you discarded in this way).”
 * A cost step of one declared attack may record, under a name, how many cards it actually moved; opt_in records 1
 * when the player accepts. Later text of the same attack reads the count with the BOUND_COUNT condition or
 * damage_per_bound. A name that was never recorded (the optional step was declined or skipped) reads as 0.
 * plan_energy_discard records the declared card or supplied-unit count selected for calculation, without moving cards.
 * Like named coins, counts live for one declared attack only, never for a player, turn or delayed program.
 * <p>
 * “Switch this Pokémon with 1 of your Benched Pokémon. If you do, …” / “Draw a card. If you do, …”: a switch records 1
 * when it actually switched, a draw records the cards actually drawn. A step skipped because it could do nothing (an
 * empty Bench or deck) records nothing, so the dependent text reads 0 and is not done. These after-damage steps may
 * only be read by later after-damage text; opt_in still decides extra damage and so stays before damage.
 */
public final class AttackCountBindings {
    private AttackCountBindings() { }
    /** Effects that may carry {@code bind}: the cards they actually move, whether a switch happened, or acceptance for opt_in. */
    public static final Set<String> BINDABLE = Set.of("opponent_hand_to_deck","heal","shuffle_named_bench_then_damage","turn_prize_face_up","discard_stadium","shuffle_hand","opponent_hand_to_bottom","reveal_hand_cards","hand_to_deck_bottom","evolve_from_hand","reveal_hand", "reveal_deck_window", "plan_energy_discard", "discard_energy", "return_energy", "discard", "search_deck", "search_discard_pile", "discard_hand", "discard_tool", "opt_in", "switch", "switch_source_with_active", "gust", "force_switch", "draw", "attach_energy", "discard_top_deck", "take_prize_cards", "damage_counter", "knock_out");

    public static String name(String value) {
        if(value==null||!value.matches("[a-z][a-z0-9_-]{0,63}"))
            throw new IllegalArgumentException("攻击计数名称须为1至64位小写字母、数字、下划线或短横线，且以字母开头");
        return value;
    }
    /** The count name an effect records, or null. */
    public static String bound(EffectDefinition effect) {
        if(!BINDABLE.contains(effect.type()))return null;
        String raw=effect.arguments().get("bind");
        if(raw==null&&effect.type().equals("attach_energy"))raw=effect.arguments().get("bind_active");
        if(raw==null){
            if(effect.type().equals("opt_in"))throw new IllegalArgumentException("opt_in 必须声明 bind");
            return null;
        }
        return name(raw);
    }
    public static boolean binds(EffectDefinition effect) {
        return bound(effect)!=null||effect.effects().stream().anyMatch(AttackCountBindings::binds);
    }
    public static boolean references(ConditionDefinition condition) {
        return condition.type()==ConditionType.BOUND_COUNT||condition.children().stream().anyMatch(AttackCountBindings::references);
    }
    /** A consumer is distinct from a step's optional bind producer. */
    public static String reference(EffectDefinition effect) {
        if(effect.type().equals("damage_per_bound"))return name(effect.arguments().get("bind"));
        if(Set.of("draw","search_deck").contains(effect.type())&&effect.arguments().containsKey("count_bind"))return name(effect.arguments().get("count_bind"));
        return null;
    }
    public static boolean references(EffectDefinition effect) {
        return reference(effect)!=null||effect.conditions().stream().anyMatch(AttackCountBindings::references)
            ||effect.effects().stream().anyMatch(AttackCountBindings::references);
    }
    public static final class Memory {
        private final Map<String,Integer> counts=new LinkedHashMap<>();
        /** Absent means the step never ran on this path (declined or skipped): nothing was done. */
        public int get(String key){return counts.getOrDefault(name(key),0);}
        public void put(String key,int count){
            name(key);
            if(count<0||count>60)throw new IllegalStateException("攻击计数超出范围");
            if(counts.containsKey(key)||counts.size()>=64)throw new IllegalStateException("攻击计数不能覆盖或超过64组");
            counts.put(key,count);
        }
    }
    /** Every read must follow a step that may have recorded that name, in execution phase order. */
    public static void validate(EffectProgram program){
        var flow=new Flow();Set<String> names=new LinkedHashSet<>();
        for(var phase:List.of(EffectTrigger.BEFORE_ATTACK,EffectTrigger.ON_ATTACK,EffectTrigger.AFTER_ATTACK))
            for(var binding:program.forTrigger(phase)){
                for(var c:binding.conditions())flow.condition(c,names);
                names=flow.sequence(binding.effects(),names,0,phase);
            }
    }
    public static boolean usesCounts(EffectProgram program) {
        return program.triggers().stream().anyMatch(t->t.conditions().stream().anyMatch(AttackCountBindings::references)
            ||t.effects().stream().anyMatch(e->binds(e)||references(e)));
    }
    /** One damage-event cursor may branch on counters actually placed, before unified KO cleanup. */
    public static void validateAutomatic(EffectProgram program,EffectTrigger trigger) {
        if(!usesCounts(program))return;
        if(trigger!=EffectTrigger.ON_DAMAGE&&trigger!=EffectTrigger.ON_ENTER_PLAY)throw new IllegalArgumentException("自动动作计数目前仅核验受伤或入场事件");
        var flow=new Flow();Set<String> names=new LinkedHashSet<>();
        for(var binding:program.forTrigger(trigger)) {
            binding.conditions().forEach(c->flow.condition(c,Set.of()));
            binding.effects().forEach(effect->automaticProducer(effect,trigger));
            names=flow.sequence(binding.effects(),names,0,trigger);
        }
    }
    private static void automaticProducer(EffectDefinition effect,EffectTrigger trigger) {
        if(bound(effect)!=null) {
            if(trigger==EffectTrigger.ON_DAMAGE&&effect.type().equals("damage_counter")) {
                DamageCounterEffect.from(effect);
                if(effect.target()==EffectTarget.SELF&&!Set.of("1","5").contains(effect.arguments().get("amount")))
                    throw new IllegalArgumentException("可选自身指示物计伤只能由招式结算，不能用作自动受伤反应");
            }
            else if(trigger!=EffectTrigger.ON_ENTER_PLAY||!effect.type().equals("switch_source_with_active"))
                throw new IllegalArgumentException("自动计数仅核验实际指示物放置或入场来源换位");
        }
        if(effect.type().equals("damage_per_bound"))throw new IllegalArgumentException("自动计数不能计算招式伤害");
        effect.effects().forEach(child->automaticProducer(child,trigger));
    }
    /** Manual continuations own one isolated count memory for actual draws, attachments and switches. */
    public static void validateManual(EffectProgram program,EffectTrigger trigger) {
        var flow=new Flow();Set<String> names=new LinkedHashSet<>();
        for(var binding:program.forTrigger(trigger)) {
            binding.conditions().forEach(c->flow.condition(c,Set.of()));
            binding.effects().forEach(AttackCountBindings::manualProducer);
            names=flow.sequence(binding.effects(),names,0,trigger);
        }
    }
    private static void manualProducer(EffectDefinition effect) {
        if(bound(effect)!=null&&!Set.of("opponent_hand_to_deck","heal","discard_stadium","shuffle_hand","hand_to_deck_bottom","opponent_hand_to_bottom","evolve_from_hand","reveal_hand","draw","discard","attach_energy","switch","gust","force_switch","take_prize_cards").contains(effect.type())
            &&!(effect.type().equals("return_energy")&&effect.target()==EffectTarget.OPPONENT_ACTIVE
                &&(effect.arguments().keySet().equals(Set.of("amount","minimum","bind"))
                    ||effect.arguments().keySet().equals(Set.of("amount","minimum","bind","count_mode"))&&"UNITS".equals(effect.arguments().get("count_mode")))
                &&"1".equals(effect.arguments().get("amount"))&&"1".equals(effect.arguments().get("minimum")))
            &&!(effect.type().equals("damage_counter")&&effect.target()==EffectTarget.SELF&&Set.of("1","5").contains(effect.arguments().getOrDefault("amount","")))
            &&!(effect.type().equals("damage_counter")&&effect.target()==EffectTarget.SELECT_OPPONENT_POKEMON&&"2".equals(effect.arguments().get("amount")))
            &&!(effect.type().equals("search_deck")&&ZoneSelectionEffect.newlyBenchedPokemonBinding(effect)!=null)&&!ZoneSelectionEffect.boundRecovery(effect)&&!ZoneSelectionEffect.mustardPlacement(effect)&&!ZoneSelectionEffect.typedDiscardPlacement(effect)&&!ZoneSelectionEffect.retainedWindowSearch(effect))
            throw new IllegalArgumentException("主动卡牌计数目前仅支持公开手牌、实际抽牌、手牌弃置、单张基础宝可梦上场、同一目标附能和换位");
        if(effect.type().equals("damage_per_bound"))throw new IllegalArgumentException("主动卡牌计数不能用于攻击伤害");
        effect.effects().forEach(AttackCountBindings::manualProducer);
    }
    private static final class Flow {
        int budget=4096;
        void condition(ConditionDefinition c,Set<String> names){
            if(c.type()==ConditionType.BOUND_COUNT){
                String key=name(c.arguments().get("bind"));
                if(!names.contains(key))throw new IllegalArgumentException("攻击计数引用尚未定义或不在此前路径中："+key);
            }
            c.children().forEach(child->condition(child,names));
        }
        Set<String> sequence(List<EffectDefinition> effects,Set<String> input,int depth,EffectTrigger phase){
            Set<String> names=new LinkedHashSet<>(input);
            for(var effect:effects)names=step(effect,names,depth,phase);
            return names;
        }
        Set<String> step(EffectDefinition effect,Set<String> input,int depth,EffectTrigger phase){
            if(depth>32||--budget<0)throw new IllegalArgumentException("攻击计数数据流超出安全步数或深度");
            effect.conditions().forEach(c->condition(c,input));
            String reference=reference(effect);
            if(reference!=null){
                String key=reference;
                if(!input.contains(key))throw new IllegalArgumentException("攻击计数引用尚未定义或不在此前路径中："+key);
            }
            Set<String> names=new LinkedHashSet<>(input);
            if(effect.type().equals("delayed_effect")){
                if(binds(effect)||references(effect))throw new IllegalArgumentException("延迟效果不能记录或读取攻击计数");
                return names;
            }
            String key=bound(effect);
            if(effect.type().equals("coin_flip")&&effect.arguments().containsKey("bind")){
                String coin=name(effect.arguments().get("bind"));
                if(!names.add(coin))throw new IllegalArgumentException("同一攻击路径重复定义计数："+coin);
            }
            if(key!=null){
                // Accepting “You may do N more damage” must happen before damage is calculated. Other counts may also be
                // recorded after damage; the phase order above already keeps damage from reading a later count.
                if(phase==EffectTrigger.ON_ATTACK||effect.type().equals("opt_in")&&phase!=EffectTrigger.BEFORE_ATTACK)
                    throw new IllegalArgumentException("opt_in 只能在伤害前（BEFORE_ATTACK）记录，伤害计算阶段不能记录攻击计数");
                if(!names.add(key))throw new IllegalArgumentException("同一攻击路径重复定义计数："+key);
                if(names.size()>64)throw new IllegalArgumentException("一次攻击最多记录64组计数");
            }
            if(effect.type().equals("attach_energy")&&effect.arguments().containsKey("bind_active")&&effect.arguments().containsKey("bind")) {
                AttachEnergyEffect.from(effect);
                if(!names.add(name(effect.arguments().get("bind_active")))||names.size()>64)
                    throw new IllegalArgumentException("战斗位附能计数名称重复或超出数量限制");
            }
            if(effect.type().equals("choice")){
                // Sibling options are exclusive; one must not read a definition from another.
                for(var child:effect.effects())names.addAll(step(child,input,depth+1,phase));
            }else if(effect.type().equals("repeat")||effect.type().equals("repeat_per_heads")){
                if(binds(effect) && !(effect.type().equals("repeat") && "1".equals(effect.arguments().get("count"))))
                    throw new IllegalArgumentException("重复效果里不能记录攻击计数");
                names=sequence(effect.effects(),names,depth+1,phase);
            }else names=sequence(effect.effects(),names,depth+1,phase);
            return names;
        }
    }
}
