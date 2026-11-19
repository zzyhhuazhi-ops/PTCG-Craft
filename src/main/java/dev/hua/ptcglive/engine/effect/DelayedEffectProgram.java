package dev.hua.ptcglive.engine.effect;

import java.util.*;

/** A future program applied to one Pokémon now, not a future lookup of an Active Spot. */
public record DelayedEffectProgram(When when, EffectProgram program) {
    public enum When { OWN_NEXT_TURN_END, OPPONENT_NEXT_TURN_END }
    private static final Set<String> BODY = Set.of("conditional","choice","repeat","repeat_per_heads","coin_flip",
        "draw","discard_hand","heal","damage_counter","knock_out","discard_pokemon","poison","burn","paralyze","sleep","confuse");
    private static final Set<ConditionType> PLAYER_CONDITIONS = Set.of(ConditionType.BENCH_COUNT,
        ConditionType.HAND_SIZE,ConditionType.PRIZE_COUNT,ConditionType.DISCARD_COUNT,ConditionType.TURN_STATE,ConditionType.COIN_RESULT);

    public static DelayedEffectProgram from(EffectDefinition effect) {
        if(!effect.type().equals("delayed_effect") || !effect.arguments().keySet().equals(Set.of("when","clear"))
            || !effect.arguments().get("clear").equals("BENCH_EVOLVE_LEAVE") || effect.effects().isEmpty()
            || !Set.of(EffectTarget.SELF,EffectTarget.OWN_ACTIVE,EffectTarget.OPPONENT_ACTIVE).contains(effect.target()))
            throw new IllegalArgumentException("延迟效果必须声明单一战斗宝可梦、到期边界、清除策略及后续程序");
        When when;
        try {when=When.valueOf(effect.arguments().get("when"));}
        catch(IllegalArgumentException invalid){throw new IllegalArgumentException("延迟效果的到期边界尚未支持");}
        var mapped=effect.effects().stream().map(child->validateAndMap(child,false,0)).toList();
        // Reuse the ordinary server grammar and step budget; event targets are statically mapped only for validation.
        ResumableProgramRules.validate(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.AFTER_ATTACK,List.of(),mapped))),true,EffectTrigger.AFTER_ATTACK);
        return new DelayedEffectProgram(when,new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_TURN_END,List.of(),effect.effects()))));
    }
    private static EffectDefinition validateAndMap(EffectDefinition effect,boolean coin,int depth) {
        if(effect.arguments().containsKey("bind"))throw new IllegalArgumentException("未来延迟程序不能定义当前攻击硬币绑定");
        if(depth>32||!BODY.contains(effect.type()))throw new IllegalArgumentException("延迟程序尚未支持此步骤："+effect.type());
        boolean composite=Set.of("conditional","choice","repeat","repeat_per_heads","coin_flip").contains(effect.type());
        if(!composite) {
            if(effect.type().equals("draw")||effect.type().equals("discard_hand")) {
                if(!Set.of(EffectTarget.SELF,EffectTarget.HAND,EffectTarget.DECK).contains(effect.target()))
                    throw new IllegalArgumentException("延迟手牌步骤须明确属于原控制者");
            } else if(effect.target()!=EffectTarget.TRIGGER_POKEMON)
                throw new IllegalArgumentException("延迟宝可梦步骤必须绑定原 TRIGGER_POKEMON，不得改找新的战斗位");
        } else if(effect.target()!=EffectTarget.SELF)throw new IllegalArgumentException("延迟容器不能另行指定目标");
        return new EffectDefinition(effect.type(),map(effect.target()),effect.arguments(),
            effect.conditions().stream().map(c->validateAndMap(c,coin)).toList(),
            effect.effects().stream().map(child->validateAndMap(child,coin||effect.type().equals("coin_flip"),depth+1)).toList());
    }
    private static ConditionDefinition validateAndMap(ConditionDefinition condition,boolean coin) {
        if(condition.arguments().containsKey("coin"))throw new IllegalArgumentException("未来延迟程序不能继承当前攻击命名硬币");
        if(condition.type()==ConditionType.COIN_RESULT&&!coin)throw new IllegalArgumentException("延迟条件不能继承先前攻击的硬币结果");
        var args=new LinkedHashMap<>(condition.arguments());
        if(condition.children().isEmpty()) {
            var target=EffectTarget.fromWire(args.getOrDefault("target","SELF"));
            if(PLAYER_CONDITIONS.contains(condition.type())) {
                if(!Set.of(EffectTarget.SELF,EffectTarget.OWN_ACTIVE,EffectTarget.OPPONENT_ACTIVE).contains(target))
                    throw new IllegalArgumentException("延迟玩家条件缺少明确控制者");
            } else if(target!=EffectTarget.TRIGGER_POKEMON)throw new IllegalArgumentException("延迟宝可梦条件须绑定原目标");
            if(target==EffectTarget.TRIGGER_POKEMON)args.put("target",EffectTarget.OWN_ACTIVE.name());
        }
        return new ConditionDefinition(condition.type(),args,condition.children().stream().map(c->validateAndMap(c,coin)).toList());
    }
    private static EffectTarget map(EffectTarget target){return target==EffectTarget.TRIGGER_POKEMON?EffectTarget.OWN_ACTIVE:target;}
}
