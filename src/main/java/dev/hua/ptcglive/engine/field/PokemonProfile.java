package dev.hua.ptcglive.engine.field;

import java.util.LinkedHashMap;
import java.util.List;

/** Server-supplied battle facts required for a Pokémon; absent data cannot be silently guessed. */
public record PokemonProfile(
    int hitPoints,
    int retreatCost,
    String evolvesFromDefinitionId,
    EnergyType weaknessType,
    int weaknessMultiplier,
    EnergyType resistanceType,
    int resistanceAmount,
    int prizeCards,
    List<AttackDefinition> attacks,
    List<AbilityDefinition> abilities,
    EnergyType pokemonType,
    /** Printed card name used by the Evolution rule (canonical English, e.g. "Snivy"); null for legacy data. */
    String pokemonName,
    /**
     * Printed "Evolves from" name. When present, any in-play Pokémon whose {@link #pokemonName} equals it may
     * evolve, regardless of which printing it is (official rule). {@code evolvesFromDefinitionId} then only names
     * one representative printing for stage-depth queries. Null keeps the legacy exact-definition match.
     */
    String evolvesFromName,
    PokemonPlacementRule placementRule,
    PokemonPrintedRules printedRules
) {
    /** Ordinary Pokémon and existing callers retain their original printed rules. */
    public PokemonProfile(int hitPoints,int retreatCost,String evolvesFromDefinitionId,EnergyType weaknessType,
                          int weaknessMultiplier,EnergyType resistanceType,int resistanceAmount,int prizeCards,
                          List<AttackDefinition> attacks,List<AbilityDefinition> abilities,EnergyType pokemonType,
                          String pokemonName,String evolvesFromName,PokemonPlacementRule placementRule) {
        this(hitPoints,retreatCost,evolvesFromDefinitionId,weaknessType,weaknessMultiplier,resistanceType,resistanceAmount,
            prizeCards,attacks,abilities,pokemonType,pokemonName,evolvesFromName,placementRule,PokemonPrintedRules.STANDARD);
    }
    /** Existing profiles have no special printed placement restriction. */
    public PokemonProfile(int hitPoints,int retreatCost,String evolvesFromDefinitionId,EnergyType weaknessType,
                          int weaknessMultiplier,EnergyType resistanceType,int resistanceAmount,int prizeCards,
                          List<AttackDefinition> attacks,List<AbilityDefinition> abilities,EnergyType pokemonType,
                          String pokemonName,String evolvesFromName) {
        this(hitPoints,retreatCost,evolvesFromDefinitionId,weaknessType,weaknessMultiplier,resistanceType,resistanceAmount,
            prizeCards,attacks,abilities,pokemonType,pokemonName,evolvesFromName,PokemonPlacementRule.ANY_EFFECT);
    }
    /** Compatibility constructor for data without printed evolution names (exact definition-ID evolution). */
    public PokemonProfile(int hitPoints,int retreatCost,String evolvesFromDefinitionId,EnergyType weaknessType,
                          int weaknessMultiplier,EnergyType resistanceType,int resistanceAmount,int prizeCards,
                          List<AttackDefinition> attacks,List<AbilityDefinition> abilities,EnergyType pokemonType) {
        this(hitPoints,retreatCost,evolvesFromDefinitionId,weaknessType,weaknessMultiplier,resistanceType,resistanceAmount,
            prizeCards,attacks,abilities,pokemonType,null,null);
    }
    /** Unknown type remains unknown; old damageType fields are not authoritative Pokémon attributes. */
    public PokemonProfile(int hitPoints,int retreatCost,String evolvesFromDefinitionId,EnergyType weaknessType,
                          int weaknessMultiplier,EnergyType resistanceType,int resistanceAmount,int prizeCards,
                          List<AttackDefinition> attacks,List<AbilityDefinition> abilities) {
        this(hitPoints,retreatCost,evolvesFromDefinitionId,weaknessType,weaknessMultiplier,resistanceType,resistanceAmount,
            prizeCards,attacks,abilities,null);
    }
    public PokemonProfile(int hitPoints, int retreatCost, String evolvesFromDefinitionId) {
        this(hitPoints, retreatCost, evolvesFromDefinitionId, null, 0, null, 0, 1, List.of(), List.of());
    }
    /** Compatibility constructor for existing data and callers with no Ability entries. */
    public PokemonProfile(int hitPoints, int retreatCost, String evolvesFromDefinitionId, EnergyType weaknessType,
                          int weaknessMultiplier, EnergyType resistanceType, int resistanceAmount, int prizeCards,
                          List<AttackDefinition> attacks) {
        this(hitPoints, retreatCost, evolvesFromDefinitionId, weaknessType, weaknessMultiplier, resistanceType,
            resistanceAmount, prizeCards, attacks, List.of());
    }
    public PokemonProfile {
        java.util.Objects.requireNonNull(placementRule,"宝可梦上场限制");
        java.util.Objects.requireNonNull(printedRules,"宝可梦卡面规则");
        if (hitPoints < 10 || hitPoints % 10 != 0) throw new IllegalArgumentException("宝可梦 HP 必须是正的 10 的倍数");
        if (retreatCost < 0 || retreatCost > 4) throw new IllegalArgumentException("撤退费用必须在 0 至 4 之间");
        if (evolvesFromDefinitionId != null && evolvesFromDefinitionId.isBlank())
            throw new IllegalArgumentException("进化来源不能为空字符串");
        if (pokemonName != null && pokemonName.isBlank()) throw new IllegalArgumentException("宝可梦卡名不能为空字符串");
        if (evolvesFromName != null && evolvesFromName.isBlank()) throw new IllegalArgumentException("进化来源卡名不能为空字符串");
        // The representative printing keeps stage-depth queries (Rare Candy, stage filters) on the existing ID chain.
        if (evolvesFromName != null && evolvesFromDefinitionId == null)
            throw new IllegalArgumentException("按卡名进化时仍须给出一个代表性的进化来源卡号");
        if (weaknessType == null && weaknessMultiplier != 0) throw new IllegalArgumentException("没有弱点类型时不能设置倍率");
        if (weaknessType != null && weaknessMultiplier < 1) throw new IllegalArgumentException("弱点倍率必须至少为 1");
        if (resistanceType == null && resistanceAmount != 0) throw new IllegalArgumentException("没有抵抗类型时不能设置抵抗数值");
        if (resistanceType != null && resistanceAmount < 0) throw new IllegalArgumentException("抵抗数值不能为负数");
        if (prizeCards < 0 || prizeCards > 3 || prizeCards == 0 && printedRules != PokemonPrintedRules.FOSSIL_ITEM && !printedRules.preventsPrizeTake())
            throw new IllegalArgumentException("击倒奖赏卡数量须为1至3；化石类物品宝可梦可为0");
        var byId = new LinkedHashMap<String, AttackDefinition>();
        for (AttackDefinition attack : attacks) {
            if (byId.putIfAbsent(attack.id(), attack) != null) throw new IllegalArgumentException("重复攻击 ID: " + attack.id());
        }
        attacks = List.copyOf(byId.values());
        var abilitiesById = new LinkedHashMap<String, AbilityDefinition>();
        for (AbilityDefinition ability : abilities) {
            if (abilitiesById.putIfAbsent(ability.id(), ability) != null) throw new IllegalArgumentException("重复特性 ID: " + ability.id());
        }
        abilities = List.copyOf(abilitiesById.values());
    }
    public boolean isEvolution() { return evolvesFromDefinitionId != null; }
    /**
     * Official rule: an Evolution card may be played on a Pokémon whose printed name matches "Evolves from",
     * whichever printing it is. Legacy data without names keeps the exact definition-ID match.
     */
    public boolean evolvesFrom(String sourceDefinitionId, PokemonProfile source) {
        if (evolvesFromName != null && source != null && evolvesFromName.equals(source.pokemonName())) return true;
        return sourceDefinitionId != null && sourceDefinitionId.equals(evolvesFromDefinitionId);
    }
    public java.util.Optional<AttackDefinition> attack(String id) { return attacks.stream().filter(attack -> attack.id().equals(id)).findFirst(); }
    public java.util.Optional<AbilityDefinition> ability(String id) { return abilities.stream().filter(ability -> ability.id().equals(id)).findFirst(); }
}
