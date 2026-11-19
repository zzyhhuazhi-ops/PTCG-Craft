package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** Revealing a hand may record its Pokemon count from the same issued public snapshot. */
public final class HandRevealEffect {
    public static final Set<String> FIELDS=Set.of("bind","card_category","trainer_category");
    private HandRevealEffect() { }
    public static void validate(EffectDefinition effect) {
        if(!effect.type().equals("reveal_hand")||!effect.effects().isEmpty()
            ||!Set.of(EffectTarget.SELF,EffectTarget.HAND,EffectTarget.OPPONENT_ACTIVE).contains(effect.target()))
            throw new IllegalArgumentException("reveal_hand 目标须为己方（SELF/HAND）或对手（OPPONENT_ACTIVE），且无子步骤");
        // Preserve every existing unparameterized hand reveal.
        if(effect.arguments().isEmpty())return;
        if(effect.target()!=EffectTarget.OPPONENT_ACTIVE||!Set.of("bind","card_category").stream().allMatch(effect.arguments()::containsKey)
            ||!FIELDS.containsAll(effect.arguments().keySet()))
            throw new IllegalArgumentException("公开手牌计数须完整声明对手目标、bind及卡牌类别");
        dev.hua.ptcglive.rules.CardCategory category=dev.hua.ptcglive.rules.CardCategory.valueOf(effect.arguments().get("card_category"));
        if(effect.arguments().containsKey("trainer_category")) {
            if(category!=dev.hua.ptcglive.rules.CardCategory.TRAINER)throw new IllegalArgumentException("训练家子类别只能与TRAINER计数一起使用");
            dev.hua.ptcglive.rules.TrainerCategory.valueOf(effect.arguments().get("trainer_category"));
        }
        AttackCountBindings.name(effect.arguments().get("bind"));
    }
    public static String countBinding(EffectDefinition effect) {
        validate(effect);
        return effect.arguments().get("bind");
    }
    public static boolean countMatches(EffectDefinition effect,dev.hua.ptcglive.rules.CardDefinition card) {
        validate(effect);
        if(card.category()!=dev.hua.ptcglive.rules.CardCategory.valueOf(effect.arguments().get("card_category")))return false;
        return !effect.arguments().containsKey("trainer_category")
            ||card.trainerCategory()==dev.hua.ptcglive.rules.TrainerCategory.valueOf(effect.arguments().get("trainer_category"));
    }
}
