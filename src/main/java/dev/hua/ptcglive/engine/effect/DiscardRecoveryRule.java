package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.CardEffectDestination;
import dev.hua.ptcglive.rules.CardCategory;
import java.util.Map;
import java.util.Set;

/** Printed discard restrictions distinguish the receiving zone and physical card category. */
public final class DiscardRecoveryRule {
    private DiscardRecoveryRule() { }
    public static boolean abilityMarker(EffectDefinition effect) {
        return effect.type().equals("unrecoverable_from_discard")&&effect.target()==EffectTarget.ALL_OPPONENT_POKEMON
            &&effect.conditions().isEmpty()&&effect.effects().isEmpty()
            &&(effect.arguments().equals(Map.of("sources","ABILITY|TRAINER","destination","HAND"))
                ||effect.arguments().equals(Map.of("sources","ITEM|SUPPORTER","destination","DECK","card_category","TRAINER")));
    }
    public static boolean blocks(EffectDefinition effect,CardEffectDestination destination,CardCategory category,boolean itemOrSupporterSource) {
        if(!abilityMarker(effect))return false;
        return "HAND".equals(effect.arguments().get("destination"))?destination==CardEffectDestination.HAND
            :itemOrSupporterSource&&category==CardCategory.TRAINER&&Set.of(CardEffectDestination.DECK_TOP,CardEffectDestination.DECK_BOTTOM,CardEffectDestination.DECK_SHUFFLED).contains(destination);
    }
}
