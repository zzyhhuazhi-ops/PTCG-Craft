package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** Replaces only the selected knocked-out host's physical card disposal, after its KO reactions. */
public final class KnockoutCardsToHand {
    private KnockoutCardsToHand() { }
    public static final String TYPE="knockout_cards_to_hand";
    public static final Set<String> FIELDS=Set.of("card_kind","opponent_only");
    public static boolean opponentOnly(EffectDefinition e){return "true".equals(e.arguments().get("opponent_only"));}
    public static void validate(EffectDefinition e) {
        var a=e.arguments();
        if(!e.type().equals(TYPE)||e.target()!=EffectTarget.SELF||!e.conditions().isEmpty()||!e.effects().isEmpty()
            ||!a.keySet().equals(FIELDS)||!Set.of("true","false").contains(a.get("opponent_only"))
            ||!("POKEMON".equals(a.get("card_kind"))||"BASIC_ENERGY".equals(a.get("card_kind"))&&opponentOnly(e)))
            throw new IllegalArgumentException("KO return must explicitly select Pokemon or Basic Energy and its attack-damage cause");
    }
    public static void validateProgram(EffectProgram program,AbilityEventPolicy policy,SharedUsageLimit usage) {
        if(program.triggers().size()!=1||program.triggers().getFirst().effects().size()!=1||!program.triggers().getFirst().conditions().isEmpty())
            throw new IllegalArgumentException("KO disposal return must be the sole mandatory effect");
        var effect=program.triggers().getFirst().effects().getFirst();validate(effect);
        if(!(policy instanceof KnockoutTriggerPolicy ko)||ko.watch()!=EventPokemonScope.SELF||ko.activeOnly()||ko.optional()||ko.fullHpOnly()
            ||ko.usage()!=EnergyTriggerPolicy.Usage.NONE||usage!=null||program.triggers().getFirst().trigger()!=EffectTrigger.ON_KNOCKOUT
            ||ko.cause()!=(opponentOnly(effect)?KnockoutTriggerPolicy.Cause.OPPONENT_ATTACK_DAMAGE:KnockoutTriggerPolicy.Cause.ATTACK_DAMAGE))
            throw new IllegalArgumentException("KO disposal return requires the matching self attack-damage event");
    }
}
