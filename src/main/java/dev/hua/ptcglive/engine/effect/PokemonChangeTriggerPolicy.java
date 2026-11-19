package dev.hua.ptcglive.engine.effect;

import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Shared policy for public field changes.  It deliberately describes only a
 * real play/evolution/retreat/switch action or a reviewed non-Knock-Out
 * leave-play action, never opening setup or a forced replacement after Knock
 * Out. The subject is captured with its in-play entry sequence before any
 * trigger can pause. Leave-play listeners are captured only from sources
 * that remain in play; last-known-information source text is intentionally
 * not implied by this policy.
 */
public record PokemonChangeTriggerPolicy(EffectTrigger trigger, EventPokemonScope watch,
                                         boolean optional, EnergyTriggerPolicy.Usage usage, Source source,
                                         String nonstackGroup, String fromPokemonName, boolean otherPokemonOnly,
                                         boolean subjectOwnTurnOnly, dev.hua.ptcglive.rules.PokemonFamily fromPokemonFamily,
                                         boolean basicBenchEntryOnly) implements AbilityEventPolicy {
    public PokemonChangeTriggerPolicy(EffectTrigger trigger,EventPokemonScope watch,boolean optional,EnergyTriggerPolicy.Usage usage,
            Source source,String nonstackGroup,String fromPokemonName,boolean otherPokemonOnly,boolean subjectOwnTurnOnly,
            dev.hua.ptcglive.rules.PokemonFamily fromPokemonFamily) {
        this(trigger,watch,optional,usage,source,nonstackGroup,fromPokemonName,otherPokemonOnly,subjectOwnTurnOnly,fromPokemonFamily,false);
    }
    public PokemonChangeTriggerPolicy(EffectTrigger trigger,EventPokemonScope watch,boolean optional,EnergyTriggerPolicy.Usage usage,
            Source source,String nonstackGroup,String fromPokemonName,boolean otherPokemonOnly) {
        this(trigger,watch,optional,usage,source,nonstackGroup,fromPokemonName,otherPokemonOnly,false,null);
    }
    public enum Source { ANY, HAND, DECK, DISCARD, PRIZE }
    public PokemonChangeTriggerPolicy(EffectTrigger trigger, EventPokemonScope watch, boolean optional, EnergyTriggerPolicy.Usage usage, Source source, String nonstackGroup) {
        this(trigger, watch, optional, usage, source, nonstackGroup, null, false);
    }
    public PokemonChangeTriggerPolicy(EffectTrigger trigger, EventPokemonScope watch, boolean optional, EnergyTriggerPolicy.Usage usage) {
        this(trigger, watch, optional, usage, Source.ANY, null);
    }
    public PokemonChangeTriggerPolicy(EffectTrigger trigger, EventPokemonScope watch, boolean optional, EnergyTriggerPolicy.Usage usage, Source source) {
        this(trigger, watch, optional, usage, source, null);
    }
    private static final Set<EffectTrigger> SUPPORTED = Set.of(
        EffectTrigger.ON_ENTER_PLAY, EffectTrigger.ON_EVOLVE, EffectTrigger.ON_RETREAT, EffectTrigger.ON_SWITCH, EffectTrigger.ON_ACTIVE_TO_BENCH,
        EffectTrigger.ON_LEAVE_PLAY);

    public PokemonChangeTriggerPolicy {
        if (!SUPPORTED.contains(Objects.requireNonNull(trigger, "trigger")))
            throw new IllegalArgumentException("不是已接入的场上变化事件：" + trigger);
        Objects.requireNonNull(watch, "watch");
        Objects.requireNonNull(usage, "usage");
        Objects.requireNonNull(source, "source");
        if(source==Source.PRIZE&&trigger!=EffectTrigger.ON_ENTER_PLAY)
            throw new IllegalArgumentException("奖赏来源仅用于入场事件");
        if(basicBenchEntryOnly&&(trigger!=EffectTrigger.ON_ENTER_PLAY||source!=Source.HAND||watch!=EventPokemonScope.ANY_FIELD))
            throw new IllegalArgumentException("基础备战入场监听须为任一玩家从手牌入场事件");
        if(subjectOwnTurnOnly&&trigger!=EffectTrigger.ON_ACTIVE_TO_BENCH&&trigger!=EffectTrigger.ON_EVOLVE)
            throw new IllegalArgumentException("目标自身回合限制只核验离开战斗位和进化事件");
        if(fromPokemonFamily!=null&&(trigger!=EffectTrigger.ON_EVOLVE||source!=Source.HAND
            ||fromPokemonFamily!=dev.hua.ptcglive.rules.PokemonFamily.V||!subjectOwnTurnOnly))
            throw new IllegalArgumentException("进化前族筛选仅核验自身回合从手牌进化的宝可梦V");
        if ((fromPokemonName != null || otherPokemonOnly) && (trigger != EffectTrigger.ON_EVOLVE
            || source != Source.HAND || watch != EventPokemonScope.OWN_FIELD || fromPokemonName == null
            || fromPokemonName.isBlank() || !otherPokemonOnly))
            throw new IllegalArgumentException("进化前名称筛选须监听己方其他宝可梦从手牌进化");
        if(nonstackGroup!=null&&!nonstackGroup.matches("[a-z][a-z0-9-]{0,63}"))
            throw new IllegalArgumentException("场上变化事件 nonstackGroup 无效");
        if (source != Source.ANY && trigger != EffectTrigger.ON_ENTER_PLAY && trigger != EffectTrigger.ON_EVOLVE)
            throw new IllegalArgumentException("来源区域筛选仅用于入场或进化事件");
    }

    public boolean matches(UUID controller, UUID sourcePokemon, UUID subjectOwner, UUID subjectPokemon) {
        return matches(controller, sourcePokemon, subjectOwner, subjectPokemon, Source.ANY);
    }
    public boolean matches(UUID controller, UUID sourcePokemon, UUID subjectOwner, UUID subjectPokemon, Source actualSource) {
        return (source == Source.ANY || source == actualSource) && (!otherPokemonOnly || !sourcePokemon.equals(subjectPokemon))
            && watch.matches(controller, sourcePokemon, subjectOwner, subjectPokemon);
    }

    public static PokemonChangeTriggerPolicy parse(JsonObject json, EffectTrigger trigger) {
        if (!SUPPORTED.contains(trigger) || !Set.of("trigger", "watch", "optional", "usage", "source", "nonstackGroup", "fromPokemonName", "otherPokemonOnly", "subjectOwnTurnOnly", "fromPokemonFamily", "basicBenchEntryOnly").containsAll(json.keySet())
            || !json.has("watch") || !json.get("watch").isJsonPrimitive()
            || !json.has("optional") || !json.get("optional").isJsonPrimitive()
            || !json.getAsJsonPrimitive("optional").isBoolean()
            || !json.has("usage") || !json.get("usage").isJsonPrimitive())
            throw new IllegalArgumentException("场上变化事件必须明确 watch、optional、usage，不能忽略未知字段");
        if(json.has("subjectOwnTurnOnly")&&(!json.get("subjectOwnTurnOnly").isJsonPrimitive()||!json.getAsJsonPrimitive("subjectOwnTurnOnly").isBoolean()))
            throw new IllegalArgumentException("目标自身回合限制须为布尔值");
        if(json.has("basicBenchEntryOnly")&&(!json.get("basicBenchEntryOnly").isJsonPrimitive()||!json.getAsJsonPrimitive("basicBenchEntryOnly").isBoolean()))
            throw new IllegalArgumentException("基础备战入场限定须为布尔值");
        return new PokemonChangeTriggerPolicy(trigger, EventPokemonScope.valueOf(json.get("watch").getAsString()),
            json.get("optional").getAsBoolean(), EnergyTriggerPolicy.Usage.valueOf(json.get("usage").getAsString()),
            json.has("source") ? Source.valueOf(json.get("source").getAsString()) : Source.ANY,
            json.has("nonstackGroup") ? json.get("nonstackGroup").getAsString() : null,
            json.has("fromPokemonName") ? json.get("fromPokemonName").getAsString() : null,
            json.has("otherPokemonOnly") && json.get("otherPokemonOnly").getAsBoolean(),
            json.has("subjectOwnTurnOnly")&&json.get("subjectOwnTurnOnly").getAsBoolean(),
            json.has("fromPokemonFamily")?dev.hua.ptcglive.rules.PokemonFamily.valueOf(json.get("fromPokemonFamily").getAsString()):null,
            json.has("basicBenchEntryOnly")&&json.get("basicBenchEntryOnly").getAsBoolean());
    }
}
