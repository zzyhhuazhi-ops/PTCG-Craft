package dev.hua.ptcglive.engine.effect;

import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.Set;

/** A player-owned qualification, independent of card name, instance and in-play identity. */
public record SharedUsageLimit(String group, Period period) {
    public enum Period { TURN, GAME }
    public static final String GX = "gx-attack", VSTAR = "vstar-power";
    public SharedUsageLimit {
        if(group==null || !group.matches("[a-z][a-z0-9-]{0,63}")) throw new IllegalArgumentException("共享资格组编号无效");
        Objects.requireNonNull(period,"共享资格周期");
        if ((group.equals(GX)||group.equals(VSTAR)) && period!=Period.GAME)
            throw new IllegalArgumentException("GX及VSTAR资格必须按整局限制");
    }
    public static SharedUsageLimit parse(JsonObject json) {
        if(!json.keySet().equals(Set.of("group","period"))) throw new IllegalArgumentException("共享资格须且只能声明group和period");
        for(String key:json.keySet()) if(!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isString())
            throw new IllegalArgumentException("共享资格参数必须是字符串："+key);
        Period period;
        try {period=Period.valueOf(json.get("period").getAsString());}
        catch(IllegalArgumentException error){throw new IllegalArgumentException("未知共享资格周期");}
        return new SharedUsageLimit(json.get("group").getAsString(),period);
    }
    public String exhaustedZh() {
        if(group.equals(GX))return "本局已使用GX攻击";
        if(group.equals(VSTAR))return "本局已使用VSTAR力量";
        return (period==Period.GAME?"本局":"本回合")+"已使用同组共享效果";
    }
}
