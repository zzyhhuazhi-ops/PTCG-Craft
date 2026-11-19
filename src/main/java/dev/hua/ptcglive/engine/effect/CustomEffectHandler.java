package dev.hua.ptcglive.engine.effect;

/**
 * Last-resort server-side implementation for an audited text that cannot be
 * represented by standard components. It is intentionally card-text agnostic.
 */
@FunctionalInterface
public interface CustomEffectHandler {
    void validate(EffectDefinition effect);
}
