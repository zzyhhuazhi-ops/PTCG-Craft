package dev.hua.ptcglive.engine.effect;

/** Shared automatic-effect contract; the source card kind is carried separately, never inferred from text. */
public interface AutomaticEffectDefinition {
    String id();
    String nameZh();
    EffectProgram effectProgram();
    AbilityEventPolicy eventPolicy();
    SharedUsageLimit usageLimit();
}
