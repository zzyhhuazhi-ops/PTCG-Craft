package dev.hua.ptcglive.engine.effect;

import com.google.gson.JsonObject;
import java.util.Set;

/** Event metadata is independent of a particular card or its reusable effects. */
public sealed interface AbilityEventPolicy permits EnergyTriggerPolicy, CheckupTriggerPolicy, DamageTriggerPolicy, KnockoutTriggerPolicy, TurnEndTriggerPolicy, TurnStartTriggerPolicy, PokemonChangeTriggerPolicy, CardPlayedTriggerPolicy, DeckDiscardTriggerPolicy, HandDiscardTriggerPolicy, BenchToActiveTriggerPolicy, PrizeTakenTriggerPolicy {
    EffectTrigger trigger();
    boolean optional();
    EnergyTriggerPolicy.Usage usage();
    static AbilityEventPolicy parse(JsonObject json) {
        var trigger = json.has("trigger") ? EffectTrigger.valueOf(json.get("trigger").getAsString()) : EffectTrigger.ON_ENERGY_ATTACHED;
        if (trigger == EffectTrigger.ON_ENERGY_ATTACHED) {
            var legacy = json.deepCopy(); legacy.remove("trigger");
            return EnergyTriggerPolicy.parse(legacy);
        }
        if (trigger == EffectTrigger.ON_BENCH_TO_ACTIVE) return BenchToActiveTriggerPolicy.parse(json);
        if (trigger == EffectTrigger.ON_DISCARDED_FROM_DECK) return DeckDiscardTriggerPolicy.parse(json);
        if (trigger == EffectTrigger.ON_DISCARDED_FROM_HAND) return HandDiscardTriggerPolicy.parse(json);
        if (trigger == EffectTrigger.ON_DAMAGE) return DamageTriggerPolicy.parse(json);
        if (trigger == EffectTrigger.ON_KNOCKOUT) return KnockoutTriggerPolicy.parse(json);
        if (trigger == EffectTrigger.ON_CARD_PLAYED) return CardPlayedTriggerPolicy.parse(json);
        if (trigger == EffectTrigger.ON_PRIZE_TAKEN) return PrizeTakenTriggerPolicy.parse(json);
        if (trigger == EffectTrigger.ON_TURN_END) return TurnEndTriggerPolicy.parse(json);
        if (trigger == EffectTrigger.ON_TURN_START) return TurnStartTriggerPolicy.parse(json);
        if (java.util.Set.of(EffectTrigger.ON_ENTER_PLAY, EffectTrigger.ON_EVOLVE, EffectTrigger.ON_RETREAT, EffectTrigger.ON_SWITCH, EffectTrigger.ON_ACTIVE_TO_BENCH, EffectTrigger.ON_LEAVE_PLAY).contains(trigger))
            return PokemonChangeTriggerPolicy.parse(json, trigger);
        if (trigger != EffectTrigger.ON_POKEMON_CHECKUP) throw new IllegalArgumentException("尚未接入此自动事件："+trigger);
        if (!Set.of("trigger","optional","usage","afterFailedSleepRecovery").containsAll(json.keySet())
            || !json.has("optional") || !json.get("optional").isJsonPrimitive() || !json.getAsJsonPrimitive("optional").isBoolean()
            || !json.has("usage")) throw new IllegalArgumentException("回合间事件必须明确 optional 和 usage，且不能包含其它事件字段");
        if(json.has("afterFailedSleepRecovery")&&(!json.get("afterFailedSleepRecovery").isJsonPrimitive()
            ||!json.getAsJsonPrimitive("afterFailedSleepRecovery").isBoolean()||!json.get("afterFailedSleepRecovery").getAsBoolean()))
            throw new IllegalArgumentException("睡眠恢复失败事件必须明确 afterFailedSleepRecovery: true");
        return new CheckupTriggerPolicy(json.get("optional").getAsBoolean(),EnergyTriggerPolicy.Usage.valueOf(json.get("usage").getAsString()),json.has("afterFailedSleepRecovery"));
    }
}
