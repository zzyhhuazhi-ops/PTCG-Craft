package dev.hua.ptcglive.engine.effect;

/** A component's current authoritative execution requirement, independent of UI support. */
public enum EffectExecutionCapability {
    /** The component has a complete no-choice ServerMatch resolver. */
    DIRECT_SERVER,
    /** The component is generic but needs a pending server-issued target/card choice. */
    REQUIRES_SELECTION,
    /** The component needs the unified attack-damage timing/context, not raw HP mutation. */
    REQUIRES_ATTACK_CONTEXT,
    /** Pure live-source queries; never execute them as one-shot atomic effects. */
    REQUIRES_PERSISTENT_SOURCE,
    /** Only an explicitly registered CustomEffect can represent this behavior. */
    CUSTOM_HANDLER
}
