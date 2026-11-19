package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** Ditto's reviewed first-turn replacement: discard the old Pokemon and attachments, then occupy its place. */
public final class BasicDeckReplacement {
    private BasicDeckReplacement() { }

    public static final Set<String> FIELDS=Set.of("amount","minimum","card_category","basic_pokemon_only","excluded_name","discard_old_card");

    public static boolean handles(EffectDefinition effect) {return effect.type().equals("replace_basic_from_deck");}

    public static void validate(EffectDefinition effect) {
        if(!handles(effect)||effect.target()!=EffectTarget.SELF||!effect.conditions().isEmpty()||!effect.effects().isEmpty()
            ||!effect.arguments().keySet().equals(FIELDS)||!"1".equals(effect.arguments().get("amount"))
            ||!"1".equals(effect.arguments().get("minimum"))||!"POKEMON".equals(effect.arguments().get("card_category"))
            ||!"true".equals(effect.arguments().get("basic_pokemon_only"))||!"Ditto".equals(effect.arguments().get("excluded_name"))
            ||!"true".equals(effect.arguments().get("discard_old_card")))
            throw new IllegalArgumentException("变身启动须从己方牌库必选1张非百变怪基础宝可梦，弃置原宝可梦及附着卡后原位上场");
    }
}
