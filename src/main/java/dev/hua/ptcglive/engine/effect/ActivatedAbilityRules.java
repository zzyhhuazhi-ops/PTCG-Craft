package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.field.AbilityDefinition;
import java.util.Optional;

/** Card validation, action listing and activation share one explicit timing contract. */
public final class ActivatedAbilityRules {
    private ActivatedAbilityRules() { }
    public static void validate(AbilityDefinition ability) {
        if(RestoredPokemonRules.containsBottom(ability)&&!RestoredPokemonRules.prehistoricCall(ability))
            throw new IllegalArgumentException("原始呼唤须为完整的一次弃牌区自回牌库底特性");
        if (!ability.activated()) throw new IllegalArgumentException("不是主动特性");
        if (RevivingFlameAbility.containsReviveStep(ability) && !RevivingFlameAbility.matches(ability))
            throw new IllegalArgumentException("弃牌区自复活只核验复活火焰的完整附能及结束回合流程");
        if (ability.effectProgram().triggers().stream().flatMap(binding->binding.effects().stream())
            .anyMatch(effect->ZoneSelectionEffect.summoningStar(effect)||effect.type().equals("turn_damage_reduction"))
            && (ability.kind()!=AbilityDefinition.Kind.ACTIVATED_ONCE_PER_GAME
                ||ability.usageLimit()==null||!SharedUsageLimit.VSTAR.equals(ability.usageLimit().group())))
            throw new IllegalArgumentException("星耀汇聚及星耀之盾必须消耗整局共享的 VSTAR 力量");
        if (ability.composed()) ResumableProgramRules.validate(ability.effectProgram(),true,ability.activationTrigger());
    }
    public static Optional<String> unsupportedReason(AbilityDefinition ability) {
        try { validate(ability); return Optional.empty(); }
        catch (IllegalArgumentException | IllegalStateException error) { return Optional.of(error.getMessage()); }
    }
}
