package dev.hua.ptcglive.engine.effect;

/** Damage Pump moves one or two counters from one own Pokémon to other own Pokémon. */
public final class DamagePump {
    private DamagePump() { }
    public static final String TYPE="move_own_damage_counters";
    public static void validate(EffectDefinition effect) {
        if(!effect.type().equals(TYPE)||effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty()
            ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
            throw new IllegalArgumentException("Damage Pump requires the reviewed single-source, two-counter movement");
    }
}
