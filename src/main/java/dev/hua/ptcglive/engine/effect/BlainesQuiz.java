package dev.hua.ptcglive.engine.effect;

/** Private hand selection, public attack declaration, then a name guess from the opponent. */
public final class BlainesQuiz {
    private BlainesQuiz() { }
    public static final String SELECT="quiz_select_pokemon";
    public static void validate(EffectDefinition e){
        if(!e.type().equals(SELECT)||e.target()!=EffectTarget.HAND||!e.arguments().isEmpty()
            ||!e.conditions().isEmpty()||!e.effects().isEmpty())throw new IllegalArgumentException("问答须采用完整的服务器交互步骤");
    }
}
