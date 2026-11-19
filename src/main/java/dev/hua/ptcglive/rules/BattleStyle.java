package dev.hua.ptcglive.rules;

/** Printed markers apply to Pokémon, Trainers and Energy alike. */
public enum BattleStyle {
    SINGLE_STRIKE, RAPID_STRIKE, FUSION_STRIKE, ANCIENT, FUTURE, ULTRA_BEAST;
    public static java.util.Set<BattleStyle> fromPokemonFamilies(java.util.Set<PokemonFamily> families) {
        var result=java.util.EnumSet.noneOf(BattleStyle.class);
        for(var style:values())if(families.contains(PokemonFamily.valueOf(style.name())))result.add(style);
        return java.util.Set.copyOf(result);
    }
}
