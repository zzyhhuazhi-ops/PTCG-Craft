package dev.hua.ptcglive.engine.effect;

import java.util.*;

/** Source-card disposition and the printed current-turn hand check; neither belongs to a Pokémon. */
public final class TrainerEndEffects {
    private TrainerEndEffects() { }
    public static void validate(EffectDefinition effect) {
        boolean valid=switch(effect.type()) {
            case "shuffle_source_into_deck", "return_source_to_hand", "return_source_to_bottom" -> effect.arguments().isEmpty();
            case "discard_hand_at_turn_end" -> effect.arguments().equals(Map.of("minimum","5"));
            case "draw_until_at_turn_end" -> effect.arguments().equals(Map.of("amount","8"));
            case "shuffle_hand_keep_at_turn_end", "shuffle_hand_keep" -> effect.arguments().equals(Map.of("amount","2"));
            case "discard_hand_keep" -> effect.arguments().equals(Map.of("amount","1","minimum","1"))
                ||effect.arguments().equals(Map.of("amount","4","minimum","0"));
            default -> false;
        };
        if(!valid||effect.target()!=EffectTarget.SELF||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
            throw new IllegalArgumentException("训练家后续效果参数不符合已核验的自身回库／本回合末手牌处理");
    }
    public static EffectProgram scheduledProgram(EffectDefinition marker) {
        validate(marker);
        if(marker.type().equals("discard_hand_at_turn_end"))return handCheckProgram();
        String type=switch(marker.type()) {
            case "draw_until_at_turn_end" -> "draw_until";
            case "shuffle_hand_keep_at_turn_end" -> "shuffle_hand_keep";
            default -> throw new IllegalArgumentException("此效果不能安排回合结束结算");
        };
        var effect=new EffectDefinition(type,EffectTarget.SELF,marker.arguments(),List.of(),List.of());
        return new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_TURN_END,List.of(),List.of(effect))));
    }
    public static EffectProgram handCheckProgram() {
        var condition=new ConditionDefinition(ConditionType.HAND_SIZE,Map.of("target","SELF","value","5","comparison",">="),List.of());
        var discard=new EffectDefinition("discard_hand",EffectTarget.SELF,Map.of(),List.of(),List.of());
        var check=new EffectDefinition("conditional",EffectTarget.SELF,Map.of(),List.of(condition),List.of(discard));
        return new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_TURN_END,List.of(),List.of(check))));
    }
}
