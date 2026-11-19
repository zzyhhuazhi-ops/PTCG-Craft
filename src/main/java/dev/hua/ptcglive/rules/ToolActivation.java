package dev.hua.ptcglive.rules;

import dev.hua.ptcglive.engine.effect.*;
import java.util.Objects;

/** Player-activated power printed on an attached Tool. */
public record ToolActivation(EffectProgram program, SharedUsageLimit usageLimit,
                             boolean holderBasicOnly, PokemonFamilyFilter holderFamilies) {
    public ToolActivation {
        Objects.requireNonNull(program,"工具主动效果程序");
        Objects.requireNonNull(usageLimit,"工具主动效果共享次数");
        Objects.requireNonNull(holderFamilies,"工具主动效果持有者家族");
        if(!usageLimit.group().equals(SharedUsageLimit.VSTAR)||usageLimit.period()!=SharedUsageLimit.Period.GAME)
            throw new IllegalArgumentException("工具授予的力量目前只核验整局一次的VSTAR力量");
        if(holderFamilies.equals(PokemonFamilyFilter.ANY))
            throw new IllegalArgumentException("工具VSTAR力量须明确宝可梦家族范围");
        ResumableProgramRules.validate(program,false,EffectTrigger.ON_ACTIVATE);
    }
}
