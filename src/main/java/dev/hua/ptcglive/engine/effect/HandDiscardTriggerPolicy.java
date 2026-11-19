package dev.hua.ptcglive.engine.effect;

import com.google.gson.JsonObject;
import java.util.Set;
import java.util.UUID;

/** Amoonguss: a physical hand-to-discard move caused by an opponent's card effect, never a cost. */
public record HandDiscardTriggerPolicy() implements AbilityEventPolicy {
    @Override public EffectTrigger trigger() { return EffectTrigger.ON_DISCARDED_FROM_HAND; }
    @Override public boolean optional() { return false; }
    @Override public EnergyTriggerPolicy.Usage usage() { return EnergyTriggerPolicy.Usage.NONE; }

    /** The server must attest the actual zone move and effect provenance at the point of commitment. */
    public boolean matches(UUID owner, UUID turnPlayer, boolean playerTurn, UUID causalController,
                           DeckDiscardTriggerPolicy.SourceKind kind, boolean handToDiscard, boolean effectNotCost) {
        return owner != null && turnPlayer != null && causalController != null && kind != null
            && handToDiscard && effectNotCost && playerTurn && !owner.equals(turnPlayer)
            && !owner.equals(causalController) && turnPlayer.equals(causalController);
    }

    public static HandDiscardTriggerPolicy parse(JsonObject json) {
        if (!json.keySet().equals(Set.of("trigger", "turn", "cause", "watch", "optional", "usage")))
            throw new IllegalArgumentException("手牌弃置事件须明确自身、对手回合、对手卡牌效果、强制及不限次");
        for (String key : Set.of("trigger", "turn", "cause", "watch", "usage"))
            if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isString())
                throw new IllegalArgumentException("手牌弃置事件枚举必须为字符串");
        if (!json.get("optional").isJsonPrimitive() || !json.getAsJsonPrimitive("optional").isBoolean()
            || json.get("optional").getAsBoolean()
            || !"ON_DISCARDED_FROM_HAND".equals(json.get("trigger").getAsString())
            || !"OPPONENT".equals(json.get("turn").getAsString())
            || !"OPPONENT_EFFECT".equals(json.get("cause").getAsString())
            || !"SELF".equals(json.get("watch").getAsString())
            || !"NONE".equals(json.get("usage").getAsString()))
            throw new IllegalArgumentException("手牌弃置事件仅核验对手回合由其攻击、特性、物品或支援者效果弃置自身");
        return new HandDiscardTriggerPolicy();
    }

    public static void validateProgram(EffectProgram program, SharedUsageLimit usage) {
        if (usage != null || program.triggers().size() != 1)
            throw new IllegalArgumentException("手牌弃置特性不接受共享次数或多个入口");
        var binding = program.triggers().getFirst();
        if (binding.trigger() != EffectTrigger.ON_DISCARDED_FROM_HAND
            || !binding.conditions().isEmpty() || binding.effects().size() != 1)
            throw new IllegalArgumentException("手牌弃置特性仅核验无条件单步弃置对手整手");
        var effect = binding.effects().getFirst();
        if (!effect.type().equals("discard_hand") || effect.target() != EffectTarget.OPPONENT_ACTIVE
            || !effect.arguments().isEmpty() || !effect.conditions().isEmpty() || !effect.effects().isEmpty())
            throw new IllegalArgumentException("手牌弃置来源没有场上宿主；只允许弃置对手整手，且不得支付成本");
        EffectSchemaValidator.validate(effect);
    }
}
