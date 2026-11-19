package dev.hua.ptcglive.engine.effect;

import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A start-of-turn boundary belongs to the player whose normal draw has just
 * completed.  It is deliberately distinct from Checkup, which has no active
 * player.  The server queues all matching effects before it accepts ordinary
 * turn actions.
 */
public record TurnStartTriggerPolicy(Turn turn, boolean optional, EnergyTriggerPolicy.Usage usage) implements AbilityEventPolicy {
    public enum Turn { OWN, OPPONENT, ANY }

    public TurnStartTriggerPolicy {
        Objects.requireNonNull(turn, "turn");
        Objects.requireNonNull(usage, "usage");
    }

    @Override public EffectTrigger trigger() { return EffectTrigger.ON_TURN_START; }

    public boolean matches(UUID controller, UUID startingPlayer) {
        return turn == Turn.ANY || (controller.equals(startingPlayer) == (turn == Turn.OWN));
    }

    public static TurnStartTriggerPolicy parse(JsonObject json) {
        if (!json.keySet().equals(Set.of("trigger", "turn", "optional", "usage"))
            || !json.has("turn") || !json.get("turn").isJsonPrimitive()
            || !json.has("optional") || !json.get("optional").isJsonPrimitive()
            || !json.getAsJsonPrimitive("optional").isBoolean()
            || !json.has("usage") || !json.get("usage").isJsonPrimitive())
            throw new IllegalArgumentException("回合开始事件必须明确 turn、optional、usage，不能忽略未知字段");
        return new TurnStartTriggerPolicy(Turn.valueOf(json.get("turn").getAsString()),
            json.get("optional").getAsBoolean(), EnergyTriggerPolicy.Usage.valueOf(json.get("usage").getAsString()));
    }
}
