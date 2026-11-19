package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.rules.CardDefinition;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Explicit printed Pokémon-type selector for reviewed private-zone choices. */
public record PokemonTypeFilter(Set<EnergyType> types) {
    public static final PokemonTypeFilter ANY = new PokemonTypeFilter(Set.of());
    public PokemonTypeFilter { types = Set.copyOf(Objects.requireNonNull(types, "宝可梦属性筛选")); }
    public static PokemonTypeFilter fromWire(String raw) {
        if (raw == null || raw.isBlank()) return ANY;
        var types = new LinkedHashSet<EnergyType>();
        for (String item : raw.split("\\|", -1)) {
            String value = item.trim();
            if (value.isEmpty()) throw new IllegalArgumentException("pokemon_types 包含空属性");
            try { types.add(EnergyType.valueOf(value.toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("未知宝可梦属性：" + value, invalid); }
        }
        return new PokemonTypeFilter(types);
    }
    /** Live field queries use the authoritative combat type for older definitions without printed type tags. */
    public boolean matches(CardDefinition definition,dev.hua.ptcglive.engine.field.PokemonProfile profile) {
        Objects.requireNonNull(definition);Objects.requireNonNull(profile);
        return types.isEmpty() || (definition.pokemonTypes().isEmpty()
            ? profile.pokemonType()!=null&&types.contains(profile.pokemonType())
            : definition.pokemonTypes().stream().anyMatch(types::contains));
    }
    public boolean matches(CardDefinition definition) {
        Objects.requireNonNull(definition, "宝可梦属性筛选候选");
        return types.isEmpty() || definition.pokemonTypes().stream().anyMatch(types::contains);
    }
}
