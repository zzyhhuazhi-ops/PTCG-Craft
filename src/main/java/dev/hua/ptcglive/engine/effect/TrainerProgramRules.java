package dev.hua.ptcglive.engine.effect;

import java.util.Optional;

/** Compatibility entry point: Trainer SELF is a player, never a Pokémon. */
public final class TrainerProgramRules {
    private TrainerProgramRules() { }
    public static Optional<String> unsupportedReason(EffectProgram program) { return ResumableProgramRules.unsupportedReason(program, false); }
    public static void validate(EffectProgram program) { ResumableProgramRules.validate(program, false); }
    public static boolean hasChoices(EffectProgram program) { return ResumableProgramRules.hasChoices(program); }
    public static EffectTarget selectionTarget(EffectProgram program) { return ResumableProgramRules.selectionTarget(program); }
    static void condition(ConditionDefinition condition) { ResumableProgramRules.condition(condition, false); }
}
