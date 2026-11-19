package dev.hua.ptcglive.engine.effect;

/** Announce only a hand Pokémon's name, then let its opponent guess the printed height. */
public final class CedricQuiz {
    private CedricQuiz() { }
    public static final String SELECT = "quiz_select_printed_height";
    public static void validate(EffectDefinition effect) {
        if (!effect.type().equals(SELECT) || effect.target() != EffectTarget.HAND
            || !effect.arguments().isEmpty() || !effect.conditions().isEmpty() || !effect.effects().isEmpty())
            throw new IllegalArgumentException("身高问答须采用完整的服务器交互步骤");
    }
}
