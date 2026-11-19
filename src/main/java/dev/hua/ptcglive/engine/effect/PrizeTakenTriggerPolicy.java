package dev.hua.ptcglive.engine.effect;

import com.google.gson.JsonObject;
import java.util.Set;

/** Trigger while a face-down Prize is known to its owner but before it enters that player's hand. */
public record PrizeTakenTriggerPolicy(boolean faceDownOnly,boolean ownTurnOnly,boolean optional,
                                      EnergyTriggerPolicy.Usage usage) implements AbilityEventPolicy {
    @Override public EffectTrigger trigger() {return EffectTrigger.ON_PRIZE_TAKEN;}

    public PrizeTakenTriggerPolicy {
        if(!faceDownOnly||!ownTurnOnly||!optional||usage!=EnergyTriggerPolicy.Usage.NONE)
            throw new IllegalArgumentException("奖赏取出事件目前只核验己方回合从暗面奖赏取出、入手前可选且不限次");
    }

    public static PrizeTakenTriggerPolicy parse(JsonObject json) {
        if(!json.keySet().equals(Set.of("trigger","face_down_only","own_turn_only","optional","usage"))
            ||!json.has("face_down_only")||!json.get("face_down_only").getAsBoolean()
            ||!json.has("own_turn_only")||!json.get("own_turn_only").getAsBoolean()
            ||!json.has("optional")||!json.get("optional").getAsBoolean())
            throw new IllegalArgumentException("奖赏取出事件字段无效");
        return new PrizeTakenTriggerPolicy(true,true,true,EnergyTriggerPolicy.Usage.valueOf(json.get("usage").getAsString()));
    }
}
