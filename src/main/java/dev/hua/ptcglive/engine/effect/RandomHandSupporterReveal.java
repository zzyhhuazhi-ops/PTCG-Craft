package dev.hua.ptcglive.engine.effect;

/** Tormenting Spray samples one hidden card before deciding whether it is discarded. */
public final class RandomHandSupporterReveal {
    private RandomHandSupporterReveal() { }
    public static final String TYPE="reveal_random_hand_supporter";
    public static void validate(EffectDefinition effect) {
        if(!effect.type().equals(TYPE)||effect.target()!=EffectTarget.OPPONENT_ACTIVE
            ||!effect.arguments().isEmpty()||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
            throw new IllegalArgumentException("Random hand reveal requires one opponent card and the printed Supporter filter");
    }
}
