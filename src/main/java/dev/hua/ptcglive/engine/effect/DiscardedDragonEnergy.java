package dev.hua.ptcglive.engine.effect;

/** N's Resolve discards six top cards, then attaches all their Basic Energy to one own Benched Dragon. */
public final class DiscardedDragonEnergy {
    private DiscardedDragonEnergy() { }
    public static final String TYPE="discard_top_attach_dragon";
    public static void validate(EffectDefinition effect) {
        if(!effect.type().equals(TYPE)||effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty()
            ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
            throw new IllegalArgumentException("N's Resolve requires its reviewed six-card Basic Energy batch and single Benched Dragon");
    }
}
