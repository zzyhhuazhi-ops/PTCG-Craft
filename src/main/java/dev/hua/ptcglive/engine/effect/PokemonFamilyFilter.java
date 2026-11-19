package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.rules.CardDefinition;
import dev.hua.ptcglive.rules.PokemonFamily;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Audited printed Pokémon-family selector for private-zone card choices.
 * Family membership is supplied by card data; it is never inferred from a
 * card name, era, image, Rule Box, or another family marker.
 */
public record PokemonFamilyFilter(Set<PokemonFamily> families, Match match) {
    public enum Match { ANY, ALL, NONE }
    public static final PokemonFamilyFilter ANY = new PokemonFamilyFilter(Set.of(), Match.ANY);

    public PokemonFamilyFilter {
        families = Set.copyOf(Objects.requireNonNull(families, "宝可梦家族筛选"));
        match = Objects.requireNonNull(match, "宝可梦家族匹配方式");
        if (families.isEmpty() && match != Match.ANY)
            throw new IllegalArgumentException("空宝可梦家族列表只能使用 ANY 匹配");
    }

    public static PokemonFamilyFilter fromWire(String rawFamilies, String rawMatch) {
        var parsed = new LinkedHashSet<PokemonFamily>();
        if (rawFamilies != null && !rawFamilies.isBlank()) for (String raw : rawFamilies.split("\\|", -1)) {
            String value = raw.trim();
            if (value.isEmpty()) throw new IllegalArgumentException("pokemon_families 包含空家族标记");
            try { parsed.add(PokemonFamily.valueOf(value.toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("未知宝可梦家族标记：" + value, invalid); }
        }
        Match match = match(rawMatch);
        if (parsed.isEmpty() && rawMatch != null && !rawMatch.isBlank())
            throw new IllegalArgumentException("family_match 需要至少一个 pokemon_families 标记");
        return parsed.isEmpty() ? ANY : new PokemonFamilyFilter(parsed, match);
    }

    public boolean matches(CardDefinition definition) {
        Objects.requireNonNull(definition, "宝可梦家族筛选候选");
        return switch (match) {
            case ANY -> families.isEmpty() || definition.pokemonFamilies().stream().anyMatch(families::contains);
            case ALL -> definition.pokemonFamilies().containsAll(families);
            case NONE -> definition.pokemonFamilies().stream().noneMatch(families::contains);
        };
    }

    private static Match match(String raw) {
        if (raw == null || raw.isBlank()) return Match.ANY;
        try { return Match.valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("family_match 必须为 ANY、ALL 或 NONE", invalid); }
    }
}
