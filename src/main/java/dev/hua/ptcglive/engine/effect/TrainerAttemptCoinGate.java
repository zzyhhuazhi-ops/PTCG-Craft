package dev.hua.ptcglive.engine.effect;
import java.util.Map;
/** Quaking Fist acts on the opposing player's next-turn Trainer declaration, before use or costs. */
public final class TrainerAttemptCoinGate {
    private TrainerAttemptCoinGate() { }
    public static void validate(EffectDefinition effect) {
        if(!effect.type().equals("trainer_attempt_coin_gate")||effect.target()!=EffectTarget.OPPONENT_ACTIVE||!effect.effects().isEmpty()
            ||!effect.arguments().equals(Map.of("scope","PLAYER","source","HAND","on_tails","DISCARD_INSTEAD_OF_USE","duration","OPPONENT_NEXT_TURN")))
            throw new IllegalArgumentException("训练家尝试判定须指定对手玩家、手牌来源、反面弃置且不视为使用，以及下个对手回合期限");
    }
}
