package dev.hua.ptcglive.engine.effect;

import com.google.gson.JsonObject;
import java.util.Set;
import java.util.UUID;

/** A physical deck-to-discard event, not a public field Pokemon lifecycle event. */
public record DeckDiscardTriggerPolicy(Turn turn, Cause cause) implements AbilityEventPolicy {
    public enum Turn { OPPONENT }
    public enum Cause { OPPONENT_POKEMON_ATTACK_ABILITY_OR_ITEM_SUPPORTER }
    public enum SourceKind { POKEMON_ATTACK, POKEMON_ABILITY, ITEM, SUPPORTER }
    public DeckDiscardTriggerPolicy {
        java.util.Objects.requireNonNull(turn); java.util.Objects.requireNonNull(cause);
    }
    @Override public EffectTrigger trigger(){return EffectTrigger.ON_DISCARDED_FROM_DECK;}
    @Override public boolean optional(){return false;}
    @Override public EnergyTriggerPolicy.Usage usage(){return EnergyTriggerPolicy.Usage.NONE;}
    /** The caller separately attests physical DECK -> DISCARD and effect (never cost) provenance. */
    public boolean matches(UUID owner,UUID turnPlayer,boolean playerTurn,UUID causalController,SourceKind kind){
        return owner!=null && turnPlayer!=null && causalController!=null && kind!=null && playerTurn
            && !owner.equals(turnPlayer) && !owner.equals(causalController) && turnPlayer.equals(causalController);
    }
    public static DeckDiscardTriggerPolicy parse(JsonObject json){
        if(!json.keySet().equals(Set.of("trigger","turn","cause","optional","usage")))
            throw new IllegalArgumentException("牌库弃置事件须明确trigger、turn、cause、optional、usage且不得添加其他字段");
        for(String key:Set.of("trigger","turn","cause","usage"))
            if(!json.get(key).isJsonPrimitive()||!json.getAsJsonPrimitive(key).isString())
                throw new IllegalArgumentException("牌库弃置事件枚举必须为字符串");
        if(!json.get("optional").isJsonPrimitive()||!json.getAsJsonPrimitive("optional").isBoolean()
            ||json.get("optional").getAsBoolean()||!"NONE".equals(json.get("usage").getAsString())
            ||!"ON_DISCARDED_FROM_DECK".equals(json.get("trigger").getAsString()))
            throw new IllegalArgumentException("牌库弃置事件目前只支持强制、不限次的明确入口");
        return new DeckDiscardTriggerPolicy(Turn.valueOf(json.get("turn").getAsString()),Cause.valueOf(json.get("cause").getAsString()));
    }
    public static void validateProgram(EffectProgram program,SharedUsageLimit usage){
        if(usage!=null||program.triggers().size()!=1)
            throw new IllegalArgumentException("牌库弃置特性不接受共享次数或多入口");
        var binding=program.triggers().getFirst();
        if(binding.trigger()!=EffectTrigger.ON_DISCARDED_FROM_DECK||!binding.conditions().isEmpty()||binding.effects().size()!=1)
            throw new IllegalArgumentException("牌库弃置特性仅核验无条件单步弃顶");
        var effect=binding.effects().getFirst();
        if(!effect.type().equals("discard_top_deck")||effect.target()!=EffectTarget.OPPONENT_ACTIVE
            ||!effect.arguments().keySet().equals(Set.of("amount"))||!effect.effects().isEmpty()||!effect.conditions().isEmpty())
            throw new IllegalArgumentException("牌库弃置来源没有场上宿主；只允许对方牌库固定弃顶");
        TopDeckDiscardEffect.from(effect);
    }
}
