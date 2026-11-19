package dev.hua.ptcglive.rules;

import com.google.gson.JsonObject;
import dev.hua.ptcglive.engine.effect.*;
import java.util.*;

/** Several physical hand cards declare one audited Item play, never an extra discard cost. */
public record PairedTrainerPlay(int count, Set<String> definitionIds,EffectProgram program) {
    public PairedTrainerPlay(int count,Set<String> definitionIds){this(count,definitionIds,EffectProgram.EMPTY);}
    /** An explicit combined program leaves the ordinary profile available for single-card use. */
    public boolean allowsSingle(){return !program.isEmpty();}
    public PairedTrainerPlay {
        if((count!=2&&count!=4) || definitionIds==null || definitionIds.isEmpty()
                || definitionIds.stream().anyMatch(id->id==null||id.isBlank()||!id.equals(id.trim())))
            throw new IllegalArgumentException("同时使用须明确两张或四张及非空卡牌编号组");
        definitionIds=Set.copyOf(definitionIds);
        Objects.requireNonNull(program);
        if(count==4&&program.isEmpty())throw new IllegalArgumentException("四张模式须明确独立效果");
        if(!program.isEmpty())TrainerProgramRules.validate(program);
    }
    public static PairedTrainerPlay parse(JsonObject json) {
        return parse(json,EffectRegistry.standard());
    }
    public static PairedTrainerPlay parse(JsonObject json,EffectRegistry registry) {
        if(json==null || !(json.keySet().equals(Set.of("count","definitionIds"))||json.keySet().equals(Set.of("count","definitionIds","effects")))
                || !json.get("count").isJsonPrimitive() || !json.getAsJsonPrimitive("count").isNumber()
                || !Set.of("2","4").contains(json.get("count").getAsString()) || !json.get("definitionIds").isJsonArray())
            throw new IllegalArgumentException("同时使用元数据须为整数count=2或4及definitionIds数组");
        var ids=new LinkedHashSet<String>();
        for(var value:json.getAsJsonArray("definitionIds")) {
            if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||!ids.add(value.getAsString()))
                throw new IllegalArgumentException("同时使用卡牌编号必须为不重复的字符串");
        }
        var program=EffectProgram.EMPTY;
        if(json.has("effects")) {
            if(!json.get("effects").isJsonArray()||json.getAsJsonArray("effects").isEmpty())throw new IllegalArgumentException("多张模式须明确非空效果");
            var effects=new ArrayList<EffectDefinition>();for(var value:json.getAsJsonArray("effects"))effects.add(registry.parse(value.getAsJsonObject()));
            program=new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_CARD_PLAYED,List.of(),effects)));
        }
        return new PairedTrainerPlay(json.get("count").getAsInt(),ids,program);
    }
}
