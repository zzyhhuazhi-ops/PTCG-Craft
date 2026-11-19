package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.TrainerCategory;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Stable, data-only filter for private card selections such as SearchDeckEffect. */
public record EffectCardSelectionSpec(int minimum, int maximum, CardCategory category,
                                      TrainerCategory trainerCategory, boolean basicPokemonOnly,
                                      RuleBoxFilter ruleBoxFilter, PokemonFamilyFilter pokemonFamilyFilter,
                                      PokemonTypeFilter pokemonTypeFilter, Set<String> allowedDefinitionIds) {
    public EffectCardSelectionSpec(int minimum, int maximum, CardCategory category, TrainerCategory trainerCategory,
                                   boolean basicPokemonOnly, Set<String> allowedDefinitionIds) {
        this(minimum, maximum, category, trainerCategory, basicPokemonOnly, RuleBoxFilter.ANY, PokemonFamilyFilter.ANY, PokemonTypeFilter.ANY, allowedDefinitionIds);
    }
    public EffectCardSelectionSpec(int minimum, int maximum, CardCategory category, TrainerCategory trainerCategory,
                                   boolean basicPokemonOnly, RuleBoxFilter ruleBoxFilter, Set<String> allowedDefinitionIds) {
        this(minimum, maximum, category, trainerCategory, basicPokemonOnly, ruleBoxFilter, PokemonFamilyFilter.ANY, PokemonTypeFilter.ANY, allowedDefinitionIds);
    }
    public EffectCardSelectionSpec(int minimum, int maximum, CardCategory category, TrainerCategory trainerCategory,
                                   boolean basicPokemonOnly, RuleBoxFilter ruleBoxFilter, PokemonFamilyFilter pokemonFamilyFilter,
                                   Set<String> allowedDefinitionIds) {
        this(minimum, maximum, category, trainerCategory, basicPokemonOnly, ruleBoxFilter, pokemonFamilyFilter, PokemonTypeFilter.ANY, allowedDefinitionIds);
    }
    public EffectCardSelectionSpec {
        if (minimum < 0 || maximum < minimum || maximum > 60) throw new IllegalArgumentException("Effect 卡牌选择数量范围无效");
        ruleBoxFilter = java.util.Objects.requireNonNull(ruleBoxFilter, "Rule Box 筛选");
        pokemonFamilyFilter = java.util.Objects.requireNonNull(pokemonFamilyFilter, "宝可梦家族筛选");
        pokemonTypeFilter = java.util.Objects.requireNonNull(pokemonTypeFilter, "宝可梦属性筛选");
        if (trainerCategory != null && category != CardCategory.TRAINER)
            throw new IllegalArgumentException("Effect 训练家筛选必须限定 TRAINER 类别");
        if (basicPokemonOnly && category != null && category != CardCategory.POKEMON)
            throw new IllegalArgumentException("Effect 基础宝可梦筛选不能与非宝可梦类别组合");
        if (ruleBoxFilter != RuleBoxFilter.ANY && category != CardCategory.POKEMON)
            throw new IllegalArgumentException("Rule Box 筛选必须明确限定 POKEMON 类别");
        if (!pokemonFamilyFilter.equals(PokemonFamilyFilter.ANY) && category != CardCategory.POKEMON)
            throw new IllegalArgumentException("宝可梦家族筛选必须明确限定 POKEMON 类别");
        if (!pokemonTypeFilter.equals(PokemonTypeFilter.ANY) && category != CardCategory.POKEMON)
            throw new IllegalArgumentException("宝可梦属性筛选必须明确限定 POKEMON 类别");
        allowedDefinitionIds = Set.copyOf(allowedDefinitionIds == null ? Set.of() : allowedDefinitionIds);
    }
    /**
     * JSON fields are scalar by design: amount, minimum, card_category,
     * trainer_category, basic_pokemon_only, rule_box, pokemon_families,
     * family_match, pokemon_types and definition_ids (pipe-separated).
     * That keeps the generic registry independent of arbitrary card-specific
     * Java types while still allowing reviewed filters to be expressed in data.
     */
    public static EffectCardSelectionSpec from(EffectDefinition effect) {
        Map<String, String> args = effect.arguments();
        int maximum = integer(args, "amount", 1, 60);
        int minimum = args.containsKey("minimum") ? integer(args, "minimum", 0, maximum)
            : effect.type().equals("search_deck") ? 0 : maximum;
        CardCategory category = args.containsKey("card_category") ? enumValue(CardCategory.class, args.get("card_category"), "card_category") : null;
        TrainerCategory trainer = args.containsKey("trainer_category") ? enumValue(TrainerCategory.class, args.get("trainer_category"), "trainer_category") : null;
        boolean basic = booleanValue(args, "basic_pokemon_only");
        RuleBoxFilter ruleBox = RuleBoxFilter.fromWire(args.get("rule_box"));
        PokemonFamilyFilter families = PokemonFamilyFilter.fromWire(args.get("pokemon_families"), args.get("family_match"));
        PokemonTypeFilter types = PokemonTypeFilter.fromWire(args.get("pokemon_types"));
        var ids = new LinkedHashSet<String>();
        String rawIds = args.get("definition_ids");
        if (rawIds != null && !rawIds.isBlank()) for (String id : rawIds.split("\\|", -1)) {
            String value = id.trim();
            if (value.isEmpty()) throw new IllegalArgumentException("definition_ids 包含空卡牌编号");
            ids.add(value);
        }
        return new EffectCardSelectionSpec(minimum, maximum, category, trainer, basic, ruleBox, families, types, ids);
    }
    private static int integer(Map<String, String> args, String key, int min, int max) {
        try {
            int value = Integer.parseInt(args.get(key));
            if (value < min || value > max) throw new IllegalArgumentException("Effect 参数超出范围：" + key);
            return value;
        } catch (NumberFormatException invalid) { throw new IllegalArgumentException("Effect 参数不是整数：" + key, invalid); }
    }
    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String key) {
        try { return Enum.valueOf(type, value.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (Exception invalid) { throw new IllegalArgumentException("Effect 参数无效：" + key, invalid); }
    }
    private static boolean booleanValue(Map<String, String> args, String key) {
        if (!args.containsKey(key)) return false;
        String value = args.get(key);
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException("Effect 布尔参数无效：" + key);
    }
}
