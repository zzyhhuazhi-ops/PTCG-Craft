package dev.hua.ptcglive.engine.effect;

import com.google.gson.JsonObject;
import java.util.*;

/** A turn boundary belongs to the ending player, not to Pokémon Checkup. */
public record TurnEndTriggerPolicy(Turn turn,boolean optional,EnergyTriggerPolicy.Usage usage,boolean sourceAttachedThisTurn) implements AbilityEventPolicy {
    public TurnEndTriggerPolicy(Turn turn,boolean optional,EnergyTriggerPolicy.Usage usage) {this(turn,optional,usage,false);}
    public enum Turn { OWN, OPPONENT, ANY }
    public TurnEndTriggerPolicy {
        Objects.requireNonNull(turn);Objects.requireNonNull(usage);
        if(sourceAttachedThisTurn&&(turn!=Turn.ANY||optional||usage!=EnergyTriggerPolicy.Usage.NONE))
            throw new IllegalArgumentException("附着当回合结束须为任意回合的强制不限次事件");
    }
    @Override public EffectTrigger trigger(){return EffectTrigger.ON_TURN_END;}
    public boolean matches(UUID controller,UUID endingPlayer) {
        return turn==Turn.ANY || (controller.equals(endingPlayer)==(turn==Turn.OWN));
    }
    public static TurnEndTriggerPolicy parse(JsonObject json) {
        if(!Set.of("trigger","turn","optional","usage","sourceAttachedThisTurn").containsAll(json.keySet())
            ||!json.keySet().containsAll(Set.of("trigger","turn","optional","usage"))
            ||!json.get("optional").isJsonPrimitive()||!json.getAsJsonPrimitive("optional").isBoolean())
            throw new IllegalArgumentException("回合末事件必须明确 turn、optional、usage，不能忽略未知字段");
        if(json.has("sourceAttachedThisTurn")&&(!json.get("sourceAttachedThisTurn").isJsonPrimitive()||!json.getAsJsonPrimitive("sourceAttachedThisTurn").isBoolean()))
            throw new IllegalArgumentException("sourceAttachedThisTurn必须是布尔值");
        return new TurnEndTriggerPolicy(Turn.valueOf(json.get("turn").getAsString()),json.get("optional").getAsBoolean(),
            EnergyTriggerPolicy.Usage.valueOf(json.get("usage").getAsString()),json.has("sourceAttachedThisTurn")&&json.get("sourceAttachedThisTurn").getAsBoolean());
    }
}
