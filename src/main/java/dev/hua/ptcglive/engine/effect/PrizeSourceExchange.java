package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** These effects exchange a played physical Trainer with an inspected face-down Prize. */
public final class PrizeSourceExchange {
    public static final String TYPE="exchange_prize_with_source";
    private PrizeSourceExchange() { }
    public static void validate(EffectDefinition effect) {
        if(!effect.type().equals(TYPE)||effect.target()!=EffectTarget.SELF
            ||!effect.arguments().keySet().equals(Set.of("prize_filter"))
            ||!Set.of("ANY","ULTRA_BEAST","BASIC_POKEMON").contains(effect.arguments().get("prize_filter"))
            ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
            throw new IllegalArgumentException("奖赏交换须明确全部卡、究极异兽或基础宝可梦筛选，且只交换使用者的暗面奖赏");
    }
    public static boolean mandatory(EffectDefinition effect){return "ANY".equals(effect.arguments().get("prize_filter"));}
}
