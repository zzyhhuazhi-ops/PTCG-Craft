package dev.hua.ptcglive.engine.effect;

/** Remove a contiguous suffix of Evolution cards from one already selected own Pokémon. */
public final class DevolutionSpray {
    private DevolutionSpray() { }
    public static final String TYPE="devolve_evolutions_to_deck";
    public static void validate(EffectDefinition effect) {
        if(!effect.type().equals(TYPE)||effect.target()!=EffectTarget.CHOSEN_POKEMON||!effect.arguments().isEmpty()
            ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
            throw new IllegalArgumentException("Devolution Spray Z requires a chosen own Pokémon and contiguous Evolution cards");
    }
}
