package dev.hua.ptcglive.engine.effect;

/** Will reserves no side when played; the controller chooses at the next eligible coin. */
public final class TurnCoinChoice {
    private TurnCoinChoice() { }
    public static void validate(EffectDefinition effect) {
        if (!effect.type().equals("turn_choose_next_coin") || effect.target()!=EffectTarget.SELF
                || !effect.arguments().isEmpty() || !effect.conditions().isEmpty() || !effect.effects().isEmpty())
            throw new IllegalArgumentException("一树只设置本回合下次招式、特性或训练家投币时选择第一个结果");
    }
}
