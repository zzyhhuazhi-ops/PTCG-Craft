package dev.hua.ptcglive.collection;

import java.util.*;

/** Read-only search state. Empty facets mean unrestricted; values within a facet are ORed. */
public record CollectionFilters(Map<String, Set<String>> facets, String ownership, String format, String sort, boolean descending) {
    public static final List<String> GROUPS = List.of("era", "set", "kind", "type", "stage", "rarity", "mark", "trait");
    public static final Set<String> OWNERSHIP = Set.of("ALL", "OWNED", "MISSING", "DUPLICATES", "INVENTORY", "DEPOSITED", "NOT_DEPOSITED");
    public static final List<String> SORTS = List.of("EXPANSION", "NAME", "TYPE", "EVOLUTION", "RARITY");
    public static final CollectionFilters DEFAULT = new CollectionFilters(Map.of(), "OWNED", "ALL", "EXPANSION", false);
    public static final CollectionFilters ALL = new CollectionFilters(Map.of(), "ALL", "ALL", "EXPANSION", false);
    public CollectionFilters {
        Objects.requireNonNull(facets);
        if (!OWNERSHIP.contains(ownership) || !SORTS.contains(sort) || format == null || format.length() > 80)
            throw new IllegalArgumentException("检索选项无效");
        var copy = new TreeMap<String, Set<String>>();
        for (var entry : facets.entrySet()) {
            if (!GROUPS.contains(entry.getKey()) || entry.getValue() == null || entry.getValue().size() > 256)
                throw new IllegalArgumentException("筛选分类无效");
            var values = new TreeSet<String>();
            for (String value : entry.getValue()) {
                if (value == null || value.length() > 100 || value.isBlank() || value.codePoints().anyMatch(Character::isISOControl))
                    throw new IllegalArgumentException("筛选值无效");
                values.add(value);
            }
            if (!values.isEmpty()) copy.put(entry.getKey(), Collections.unmodifiableSet(values));
        }
        facets = Collections.unmodifiableMap(copy);
    }
    public boolean selected(String group, String value) { return facets.getOrDefault(group, Set.of()).contains(value); }
    public CollectionFilters toggle(String group, String value) {
        var next = new TreeMap<>(facets); var values = new TreeSet<>(next.getOrDefault(group, Set.of()));
        if (!values.remove(value)) values.add(value); next.put(group, values);
        return new CollectionFilters(next, ownership, format, sort, descending);
    }
    public CollectionFilters withOwnership(String value) { return new CollectionFilters(facets, value, format, sort, descending); }
    public CollectionFilters withFormat(String value) { return new CollectionFilters(facets, ownership, value, sort, descending); }
    public CollectionFilters withSort(String value) { return new CollectionFilters(facets, ownership, format, value, descending); }
    public CollectionFilters reverse() { return new CollectionFilters(facets, ownership, format, sort, !descending); }
    public CollectionFilters clearGroup(String group) { var next = new TreeMap<>(facets); next.remove(group); return new CollectionFilters(next, ownership, format, sort, descending); }
    public boolean acceptsOwnership(int inventory, int collection) {
        int held = inventory + collection;
        return switch (ownership) {
            case "OWNED" -> held > 0; case "MISSING" -> held == 0; case "DUPLICATES" -> held > 1;
            case "INVENTORY" -> inventory > 0; case "DEPOSITED" -> collection > 0;
            case "NOT_DEPOSITED" -> inventory > 0 && collection == 0; default -> true;
        };
    }
    public int activeCount() { return facets.values().stream().mapToInt(Set::size).sum() + (ownership.equals("ALL") ? 0 : 1) + (format.equals("ALL") ? 0 : 1); }
}
