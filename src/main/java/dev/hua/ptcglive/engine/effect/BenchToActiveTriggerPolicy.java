package dev.hua.ptcglive.engine.effect;

import com.google.gson.JsonObject;
import java.util.Set;

/** An optional incoming Pokemon's own-turn transition, never an outgoing retreat event. */
public record BenchToActiveTriggerPolicy() implements AbilityEventPolicy {
    @Override public EffectTrigger trigger() { return EffectTrigger.ON_BENCH_TO_ACTIVE; }
    @Override public boolean optional() { return true; }
    @Override public EnergyTriggerPolicy.Usage usage() { return EnergyTriggerPolicy.Usage.ONCE_PER_TURN; }

    public static BenchToActiveTriggerPolicy parse(JsonObject json) {
        if (!json.keySet().equals(Set.of("trigger", "watch", "turn", "optional", "usage")))
            throw new IllegalArgumentException("登上战斗位事件必须明确自身、己方回合、可选及每回合一次");
        for (String key : Set.of("trigger", "watch", "turn", "usage"))
            if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isString())
                throw new IllegalArgumentException("登上战斗位事件枚举必须为字符串");
        if (!json.get("optional").isJsonPrimitive() || !json.getAsJsonPrimitive("optional").isBoolean()
            || !json.get("optional").getAsBoolean() || !"SELF".equals(json.get("watch").getAsString())
            || !"OWN".equals(json.get("turn").getAsString()) || !"ONCE_PER_TURN".equals(json.get("usage").getAsString())
            || !"ON_BENCH_TO_ACTIVE".equals(json.get("trigger").getAsString()))
            throw new IllegalArgumentException("登上战斗位事件仅核验自身在己方回合可选发动一次");
        return new BenchToActiveTriggerPolicy();
    }

    public static void validateProgram(EffectProgram program, SharedUsageLimit usage) {
        if (usage != null || program.triggers().size() != 1)
            throw new IllegalArgumentException("登上战斗位特性不接受共享次数或多个入口");
        var binding = program.triggers().getFirst();
        if (binding.trigger() != EffectTrigger.ON_BENCH_TO_ACTIVE || !binding.conditions().isEmpty()
            || binding.effects().size() != 1)
            throw new IllegalArgumentException("登上战斗位特性须为单一已审核操作");
        var effect=binding.effects().getFirst();
        if(effect.type().equals("damage_counter")&&effect.target()==EffectTarget.SELECT_OPPONENT_POKEMON
            &&effect.arguments().equals(java.util.Map.of("amount","2"))) {PokemonEffectSelection.validate(effect);return;}
        if(effect.type().equals("gust")&&effect.target()==EffectTarget.SELECT_OPPONENT_POKEMON
            &&effect.arguments().equals(java.util.Map.of("basic_pokemon_only","true"))) {PokemonEffectSelection.validate(effect);return;}
        if(AttachedEnergyEffect.otherOwnMetalToSelf(effect)) {AttachedEnergyEffect.from(effect);return;}
        var attach=AttachEnergyEffect.from(effect);
        if(effect.target()!=EffectTarget.SELF||attach.source()!=EffectTargetResolution.Zone.DECK
            ||attach.distribution()!=AttachEnergyEffect.Distribution.SAME_TARGET||attach.policy().minCards()!=0
            ||attach.policy().maxCards()!=3||!attach.policy().basicEnergyOnly()
            ||!attach.basicEnergyTypes().equals(Set.of(dev.hua.ptcglive.engine.field.EnergyType.GRASS)))
            throw new IllegalArgumentException("登上战斗位检索附能只核验牌库最多3张基本草能量附于自身");
    }
}
