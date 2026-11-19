package dev.hua.ptcglive.engine.effect;

import java.util.*;

/** A Tool latches the final attack result, then schedules one draw for this turn. */
public final class AttackTailsDraw {
    public static final String TYPE="draw_at_turn_end_after_attack_tails";
    private AttackTailsDraw() { }
    public static boolean handles(EffectDefinition effect){return effect.type().equals(TYPE);}
    public static void validate(EffectDefinition effect){
        if(!handles(effect)||effect.target()!=EffectTarget.SELF||!effect.arguments().equals(Map.of("amount","3"))
            ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
            throw new IllegalArgumentException("打空保险须为所附宝可梦的最终攻击反面结果，回合末抽3张");
    }
    private static boolean contains(EffectDefinition effect){return handles(effect)||effect.effects().stream().anyMatch(AttackTailsDraw::contains);}
    public static boolean contains(EffectProgram program){return program.triggers().stream().flatMap(b->b.effects().stream()).anyMatch(AttackTailsDraw::contains);}
    public static boolean fromProgram(EffectProgram program){
        if(!contains(program))return false;
        if(program.triggers().size()!=1)throw new IllegalArgumentException("打空保险须为单独的持续程序");
        var binding=program.triggers().getFirst();
        if(binding.trigger()!=EffectTrigger.PASSIVE||!binding.conditions().isEmpty()||binding.effects().size()!=1)
            throw new IllegalArgumentException("打空保险须为唯一无条件PASSIVE效果");
        validate(binding.effects().getFirst());return true;
    }
    public static EffectProgram scheduledProgram(){return new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_TURN_END,List.of(),
        List.of(new EffectDefinition("draw",EffectTarget.SELF,Map.of("amount","3"),List.of(),List.of())))));}
}
