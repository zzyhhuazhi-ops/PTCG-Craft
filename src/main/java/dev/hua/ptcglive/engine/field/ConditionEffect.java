package dev.hua.ptcglive.engine.field;

import java.util.Objects;

/** A supported attack effect opcode. No client supplies effect payloads. */
public record ConditionEffect(Target target, SpecialCondition condition, CoinCondition coinCondition) {
    public enum Target { SELF, DEFENDER }
    /** A server-only coin gate for one condition clause, such as “flip a coin; if heads, Burned”. */
    public record CoinCondition(int flips, int requiredHeads) {
        public CoinCondition {
            if (flips < 1 || flips > 20 || requiredHeads < 1 || requiredHeads > flips)
                throw new IllegalArgumentException("异常状态硬币条件参数无效");
        }
    }
    public ConditionEffect { Objects.requireNonNull(target); Objects.requireNonNull(condition); }
    public ConditionEffect(Target target, SpecialCondition condition) { this(target, condition, null); }
    /** Resolves the printed coin requirement through the authoritative match random source. */
    public boolean resolves(dev.hua.ptcglive.engine.ServerRandom random, String attackId, int conditionIndex) {
        return resolves(random,attackId,conditionIndex,false);
    }
    public boolean resolves(dev.hua.ptcglive.engine.ServerRandom random, String attackId, int conditionIndex,boolean forceTails) {
        if (coinCondition == null) return true;
        int heads = 0;
        for (int flip = 0; flip < coinCondition.flips(); flip++)
            if (random.coinHeads("attack-condition:" + attackId + ":" + conditionIndex + ":" + flip, forceTails)) heads++;
        return heads >= coinCondition.requiredHeads();
    }
}
