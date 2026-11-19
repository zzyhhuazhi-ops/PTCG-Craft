package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** Select and reveal reviewed hand cards without moving them out of the hand. */
public final class SelectedHandReveal {
    private SelectedHandReveal() { }
    private static final Set<String> NAMED_POKEMON_FIELDS=Set.of("amount","minimum","card_category","pokemon_names","reveal","bind");
    private static final Set<String> RAPID_STRIKE_FIELDS=Set.of("amount","minimum","battle_style","reveal","bind");
    /** Preflight's allowed keys; validate still requires one exact reviewed shape. */
    public static final Set<String> FIELDS=Set.of("amount","minimum","card_category","pokemon_names","battle_style","reveal","bind","count_mode");
    public static boolean rapidStrike(EffectDefinition effect) {
        return effect.type().equals("reveal_hand_cards")&&effect.arguments().containsKey("battle_style");
    }
    public static void validate(EffectDefinition effect) {
        var args=effect.arguments();
        if(!effect.type().equals("reveal_hand_cards")||effect.target()!=EffectTarget.HAND
            ||!effect.conditions().isEmpty()||!effect.effects().isEmpty()
            ||!"60".equals(args.get("amount"))||!"0".equals(args.get("minimum"))
            ||!"true".equals(args.get("reveal")))
            throw new IllegalArgumentException("选手牌展示须明确任意数量、公开展示及计数");
        boolean namedPokemon=args.keySet().equals(NAMED_POKEMON_FIELDS)
            &&"POKEMON".equals(args.get("card_category"))
            &&!TurnHistoryCondition.values(args.get("pokemon_names")).isEmpty();
        boolean rapidStrike=args.keySet().equals(RAPID_STRIKE_FIELDS)
            &&"RAPID_STRIKE".equals(args.get("battle_style"));
        boolean differentTypes=args.keySet().equals(Set.of("amount","minimum","card_category","reveal","bind","count_mode"))
            &&"POKEMON".equals(args.get("card_category"))&&"DISTINCT_TYPES".equals(args.get("count_mode"));
        if(!namedPokemon&&!rapidStrike&&!differentTypes)
            throw new IllegalArgumentException("选手牌展示只支持指定名称的宝可梦或连击卡牌");
        AttackCountBindings.name(args.get("bind"));
    }
}
