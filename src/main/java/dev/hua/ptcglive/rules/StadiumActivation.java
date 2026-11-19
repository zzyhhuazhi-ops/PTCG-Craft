package dev.hua.ptcglive.rules;

import dev.hua.ptcglive.engine.effect.*;
import java.util.Objects;

/** Optional activation of this Stadium's current stay in play, once per player during their own turn. */
public record StadiumActivation(EffectProgram program, boolean endsTurn) {
    public StadiumActivation(EffectProgram program) { this(program,false); }
    public StadiumActivation {
        Objects.requireNonNull(program);
        ResumableProgramRules.validate(program,false,EffectTrigger.ON_ACTIVATE);
    }
}
