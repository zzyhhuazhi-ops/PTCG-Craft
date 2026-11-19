package dev.hua.ptcglive.rules;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import dev.hua.ptcglive.engine.field.EnergyType;

/** Versioned rules data for a card; art IDs alone are intentionally not playable card definitions. */
public record CardDefinition(
    String id,
    String nameZh,
    CardCategory category,
    TrainerCategory trainerCategory,
    String regulationMark,
    Set<String> regions,
    boolean basicPokemon,
    boolean basicEnergy,
    int copyLimit,
    Map<String, Integer> specialDeckLimits,
    EffectSupport effectSupport,
    String unsupportedReasonZh,
    Set<PokemonFamily> pokemonFamilies,
    Set<EnergyType> pokemonTypes,
    Set<BattleStyle> battleStyles
) {
    /** Older Pokémon data stored its printed Battle Style among Pokémon-family markers. */
    public CardDefinition(String id, String nameZh, CardCategory category, TrainerCategory trainerCategory,
                          String regulationMark, Set<String> regions, boolean basicPokemon, boolean basicEnergy,
                          int copyLimit, Map<String, Integer> specialDeckLimits, EffectSupport effectSupport,
                          String unsupportedReasonZh, Set<PokemonFamily> pokemonFamilies, Set<EnergyType> pokemonTypes) {
        this(id,nameZh,category,trainerCategory,regulationMark,regions,basicPokemon,basicEnergy,copyLimit,
            specialDeckLimits,effectSupport,unsupportedReasonZh,pokemonFamilies,pokemonTypes,BattleStyle.fromPokemonFamilies(pokemonFamilies));
    }
    /** Compatibility constructor for rule packages created before family metadata existed. */
    public CardDefinition(String id, String nameZh, CardCategory category, TrainerCategory trainerCategory,
                          String regulationMark, Set<String> regions, boolean basicPokemon, boolean basicEnergy,
                          int copyLimit, Map<String, Integer> specialDeckLimits, EffectSupport effectSupport,
                          String unsupportedReasonZh) {
        this(id, nameZh, category, trainerCategory, regulationMark, regions, basicPokemon, basicEnergy, copyLimit,
            specialDeckLimits, effectSupport, unsupportedReasonZh, Set.of(), Set.of());
    }
    /** Compatibility constructor for rule packages created before typed Pokémon metadata existed. */
    public CardDefinition(String id, String nameZh, CardCategory category, TrainerCategory trainerCategory,
                          String regulationMark, Set<String> regions, boolean basicPokemon, boolean basicEnergy,
                          int copyLimit, Map<String, Integer> specialDeckLimits, EffectSupport effectSupport,
                          String unsupportedReasonZh, Set<PokemonFamily> pokemonFamilies) {
        this(id, nameZh, category, trainerCategory, regulationMark, regions, basicPokemon, basicEnergy, copyLimit,
            specialDeckLimits, effectSupport, unsupportedReasonZh, pokemonFamilies, Set.of());
    }
    public CardDefinition {
        require(id, "卡牌编号");
        require(nameZh, "中文名称");
        Objects.requireNonNull(category, "卡牌类别");
        Objects.requireNonNull(trainerCategory, "训练家类别");
        require(regulationMark, "规则标记");
        regions = Set.copyOf(regions);
        specialDeckLimits = Map.copyOf(specialDeckLimits);
        pokemonFamilies = Set.copyOf(Objects.requireNonNull(pokemonFamilies, "宝可梦族标记"));
        pokemonTypes = Set.copyOf(Objects.requireNonNull(pokemonTypes, "宝可梦属性标记"));
        battleStyles = Set.copyOf(Objects.requireNonNull(battleStyles, "卡牌战斗风格标记"));
        Objects.requireNonNull(effectSupport, "效果支持状态");
        if (copyLimit < 1) throw new IllegalArgumentException("同名上限必须至少为 1");
        if (basicPokemon && category != CardCategory.POKEMON) throw new IllegalArgumentException("只有宝可梦可以是基础宝可梦");
        if (basicEnergy && category != CardCategory.ENERGY) throw new IllegalArgumentException("只有能量可以是基础能量");
        if (trainerCategory != TrainerCategory.NONE && category != CardCategory.TRAINER)
            throw new IllegalArgumentException("训练家子类别只能用于训练家卡");
        if (!pokemonFamilies.isEmpty() && category != CardCategory.POKEMON)
            throw new IllegalArgumentException("宝可梦族标记只能用于宝可梦卡");
        if (!pokemonTypes.isEmpty() && category != CardCategory.POKEMON)
            throw new IllegalArgumentException("宝可梦属性标记只能用于宝可梦卡");
        if (effectSupport == EffectSupport.UNSUPPORTED && (unsupportedReasonZh == null || unsupportedReasonZh.isBlank()))
            throw new IllegalArgumentException("未支持卡牌必须说明原因");
    }

    private static void require(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
    }
}
