package dev.hua.ptcglive.engine.effect;

/** The knocked-out Pokémon's printed top card alone returns to its owner's hand. */
public final class KnockoutTopToHand {
    private KnockoutTopToHand() { }

    public static void validate(EffectDefinition effect) {
        if (!effect.type().equals("knockout_top_to_hand") || effect.target() != EffectTarget.SELF
            || !effect.arguments().isEmpty() || !effect.conditions().isEmpty() || !effect.effects().isEmpty())
            throw new IllegalArgumentException("击倒后回手只接受自身顶层卡，无参数、条件或子效果");
    }

    public static void validateProgram(EffectProgram program, AbilityEventPolicy eventPolicy,
                                       SharedUsageLimit usageLimit) {
        if (!(eventPolicy instanceof KnockoutTriggerPolicy policy)
            || policy.cause() != KnockoutTriggerPolicy.Cause.OPPONENT_ATTACK_DAMAGE
            || policy.watch() != EventPokemonScope.SELF || policy.activeOnly() || policy.optional()
            || policy.fullHpOnly() || policy.usage() != EnergyTriggerPolicy.Usage.NONE
            || usageLimit != null || program.triggers().size() != 1)
            throw new IllegalArgumentException("击倒顶层卡回手仅限自身被对手招式伤害击倒时强制触发");
        var binding = program.triggers().getFirst();
        if (binding.trigger() != EffectTrigger.ON_KNOCKOUT || !binding.conditions().isEmpty()
            || binding.effects().size() != 1)
            throw new IllegalArgumentException("击倒顶层卡回手须为无附加条件的唯一击倒效果");
        validate(binding.effects().getFirst());
    }
}
