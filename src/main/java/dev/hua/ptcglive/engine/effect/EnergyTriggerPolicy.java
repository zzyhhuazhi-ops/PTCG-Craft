package dev.hua.ptcglive.engine.effect;

import com.google.gson.JsonObject;
import java.util.*;

/** Event matching is explicit data, not an assumption that every attachment activates every Ability. */
public record EnergyTriggerPolicy(Target target, Source source, Cause cause, boolean optional, Usage usage, boolean basicOnly,boolean ownHandOnly,boolean targetHandOnly,String nonstackGroup) implements AbilityEventPolicy {
    public EnergyTriggerPolicy(Target target,Source source,Cause cause,boolean optional,Usage usage,boolean basicOnly,boolean ownHandOnly) {this(target,source,cause,optional,usage,basicOnly,ownHandOnly,false,null);}
    public EnergyTriggerPolicy(Target target,Source source,Cause cause,boolean optional,Usage usage,boolean basicOnly) {this(target,source,cause,optional,usage,basicOnly,false);}
    @Override public EffectTrigger trigger() { return EffectTrigger.ON_ENERGY_ATTACHED; }
    public enum Target { SELF, OWN_FIELD, OPPONENT_FIELD, ANY_FIELD }
    public enum Source { HAND, DECK, DISCARD, PRIZE, ANY }
    public enum Cause { MANUAL, EFFECT, ANY }
    public enum Usage { NONE, ONCE_PER_TURN, ONCE_PER_GAME }
    public EnergyTriggerPolicy { Objects.requireNonNull(target); Objects.requireNonNull(source); Objects.requireNonNull(cause); Objects.requireNonNull(usage);if((ownHandOnly||targetHandOnly)&&source!=Source.HAND)throw new IllegalArgumentException("手牌来源限制须为HAND");if(nonstackGroup!=null&&!nonstackGroup.matches("[a-z][a-z0-9-]{0,63}"))throw new IllegalArgumentException("无效的附能触发非叠加组"); }
    public static EnergyTriggerPolicy parse(JsonObject json) {
        if (!Set.of("target", "source", "cause", "optional", "usage", "basicOnly", "ownHandOnly", "targetHandOnly", "nonstackGroup").containsAll(json.keySet()))
            throw new IllegalArgumentException("附能触发策略含未支持字段");
        return new EnergyTriggerPolicy(Target.valueOf(required(json,"target")), Source.valueOf(required(json,"source")),
            Cause.valueOf(required(json,"cause")), bool(json,"optional",true), Usage.valueOf(required(json,"usage")), bool(json,"basicOnly",false),bool(json,"ownHandOnly",false),bool(json,"targetHandOnly",false),json.has("nonstackGroup")?required(json,"nonstackGroup"):null);
    }
    public boolean matches(UUID owner, UUID pokemonId, UUID targetOwner, UUID targetId, Source origin, Cause reason, boolean basic) {
        return (source == Source.ANY || source == origin) && (cause == Cause.ANY || cause == reason) && (!basicOnly || basic)
            && EventPokemonScope.valueOf(target.name()).matches(owner,pokemonId,targetOwner,targetId);
    }
    private static String required(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) throw new IllegalArgumentException("附能触发策略必须明确 " + key);
        return json.get(key).getAsString();
    }
    private static boolean bool(JsonObject json, String key, boolean required) {
        if (!json.has(key) && !required) return false;
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isBoolean())
            throw new IllegalArgumentException("附能触发策略的 " + key + " 必须为布尔值");
        return json.get(key).getAsBoolean();
    }
}
