package dev.hua.ptcglive.rules;

import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.EnergyProfile;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Versioned, server-only combat facts. Missing entries are intentionally unsupported. */
public final class CombatCatalog {
    private static final CombatCatalog EMPTY = new CombatCatalog(Map.of(), Map.of());
    private final Map<String, PokemonProfile> pokemon;
    private final Map<String, PokemonProfile> fieldPokemon;
    private final Map<String, EnergyType> energy;
    private final Map<String, EnergyProfile> energyProfiles;
    private final Map<String, TrainerProfile> trainers;

    public CombatCatalog(Map<String, PokemonProfile> pokemon, Map<String, EnergyType> energy) {
        this(pokemon, energy, Map.of());
    }
    public CombatCatalog(Map<String, PokemonProfile> pokemon, Map<String, EnergyType> energy, Map<String, TrainerProfile> trainers) {
        this(pokemon, energy, trainers, Map.of());
    }
    /** The fourth map is reserved for reviewed Special Energy provision profiles; legacy Basic Energy inputs stay unchanged. */
    public CombatCatalog(Map<String, PokemonProfile> pokemon, Map<String, EnergyType> energy, Map<String, TrainerProfile> trainers,
                         Map<String, EnergyProfile> specialEnergyProfiles) {
        this.pokemon = copy(pokemon, "宝可梦战斗数据");
        if(this.pokemon.values().stream().anyMatch(p->p.printedRules().isItemPokemon()))
            throw new IllegalArgumentException("物品宝可梦形态必须保留在训练家数据中，不能作为普通宝可梦卡注册");
        var sharedPeriods=new LinkedHashMap<String,dev.hua.ptcglive.engine.effect.SharedUsageLimit.Period>();
        this.pokemon.values().forEach(profile->{
            profile.attacks().forEach(attack->validateSharedPeriod(sharedPeriods,attack.usageLimit()));
            profile.abilities().forEach(ability->validateSharedPeriod(sharedPeriods,ability.usageLimit()));
        });
        var zoneSourceIds=new java.util.HashSet<String>();
        this.pokemon.values().forEach(profile->profile.abilities().forEach(ability->{
            String id=dev.hua.ptcglive.engine.effect.AbilitySuppressionProgram.zoneSourceId(ability.effectProgram());
            if(id!=null) {
                if(profile.evolvesFromDefinitionId()==null)throw new IllegalArgumentException("跨区域移除来源须为进化宝可梦");
                zoneSourceIds.add(id);
            }
        }));
        if(zoneSourceIds.size()>1)throw new IllegalArgumentException("不同自豁免特性的跨区域移除存在未审核循环");
        this.energy = copy(energy, "能量类型数据");
        var profiles = new LinkedHashMap<String, EnergyProfile>();
        this.energy.forEach((id, type) -> profiles.put(id, EnergyProfile.basic(type)));
        copy(specialEnergyProfiles, "特殊能量供给数据").forEach((id, profile) -> {
            if (profiles.putIfAbsent(id, profile) != null) throw new IllegalArgumentException("重复能量供给数据：" + id);
        });
        this.energyProfiles = Map.copyOf(profiles);
        this.trainers = copy(trainers, "训练家效果数据");
        var fieldProfiles=new LinkedHashMap<>(this.pokemon);
        this.trainers.forEach((id,trainer)->{
            var form=trainer.inPlayPokemon();
            if(form==null)return;
            if(fieldProfiles.putIfAbsent(id,form)!=null)throw new IllegalArgumentException("重复场上宝可梦形态："+id);
            form.abilities().forEach(ability->{
                validateSharedPeriod(sharedPeriods,ability.usageLimit());
                if(dev.hua.ptcglive.engine.effect.AbilitySuppressionProgram.zoneSourceId(ability.effectProgram())!=null)
                    throw new IllegalArgumentException("物品宝可梦形态尚不支持跨区域特性移除来源");
            });
        });
        this.fieldPokemon=Map.copyOf(fieldProfiles);
        this.energyProfiles.values().forEach(profile->profile.automaticEffects().forEach(effect->validateSharedPeriod(sharedPeriods,effect.usageLimit())));
        this.trainers.values().forEach(profile->profile.automaticEffects().forEach(effect->validateSharedPeriod(sharedPeriods,effect.usageLimit())));
        for (String id : this.pokemon.keySet()) if (this.energyProfiles.containsKey(id))
            throw new IllegalArgumentException("同一卡牌不能同时是宝可梦和能量：" + id);
        for (String id : this.trainers.keySet()) if (this.energyProfiles.containsKey(id) || this.pokemon.containsKey(id))
            throw new IllegalArgumentException("训练家卡类别冲突：" + id);
    }
    public static CombatCatalog empty() { return EMPTY; }
    private static void validateSharedPeriod(Map<String,dev.hua.ptcglive.engine.effect.SharedUsageLimit.Period> groups,
        dev.hua.ptcglive.engine.effect.SharedUsageLimit limit) {
        if(limit==null)return;
        var previous=groups.putIfAbsent(limit.group(),limit.period());
        if(previous!=null && previous!=limit.period())throw new IllegalArgumentException("共享资格周期冲突："+limit.group());
    }
    public Optional<PokemonProfile> pokemon(String definitionId) { return Optional.ofNullable(pokemon.get(definitionId)); }
    /** Field rules and evolution ancestry only; hidden-zone Pokémon searches must use pokemon(). */
    public Optional<PokemonProfile> fieldPokemon(String definitionId) { return Optional.ofNullable(fieldPokemon.get(definitionId)); }
    public Map<String, PokemonProfile> fieldPokemonProfiles() { return fieldPokemon; }
    /** A derived field-only view. Never store it in CardDatabase or use it for deck/hand/discard filters. */
    public CardDefinition fieldDefinition(CardDefinition definition) {
        var trainer=trainers.get(definition.id());
        if(trainer==null || trainer.inPlayPokemon()==null)return definition;
        if(definition.category()!=CardCategory.TRAINER || definition.trainerCategory()!=TrainerCategory.ITEM)
            throw new IllegalArgumentException("物品宝可梦的印刷卡牌类别不一致："+definition.id());
        var form=trainer.inPlayPokemon();
        return new CardDefinition(definition.id(),definition.nameZh(),CardCategory.POKEMON,TrainerCategory.NONE,
            definition.regulationMark(),definition.regions(),true,false,definition.copyLimit(),definition.specialDeckLimits(),
            definition.effectSupport(),definition.unsupportedReasonZh(),java.util.Set.of(),java.util.Set.of(form.pokemonType()),definition.battleStyles());
    }
    public Optional<EnergyType> energyType(String definitionId) { return Optional.ofNullable(energy.get(definitionId)); }
    public Optional<EnergyProfile> energyProfile(String definitionId) { return Optional.ofNullable(energyProfiles.get(definitionId)); }
    public Optional<TrainerProfile> trainer(String definitionId) { return Optional.ofNullable(trainers.get(definitionId)); }
    /** Immutable read-only views for offline effect coverage analysis; never exposed to network clients. */
    public Map<String, TrainerProfile> trainerProfiles() { return trainers; }
    public Map<String, EnergyProfile> energyProfiles() { return energyProfiles; }
    public boolean isConfigured() { return !pokemon.isEmpty() || !energyProfiles.isEmpty() || !trainers.isEmpty(); }
    public boolean energyRestrictionsRequirePokemonTypes() {
        return energyProfiles.values().stream().anyMatch(profile->!profile.attachmentRule().pokemonTypes().isEmpty());
    }
    public Map<String, PokemonProfile> pokemonProfiles() { return pokemon; }
    private static <T> Map<String, T> copy(Map<String, T> input, String label) {
        var result = new LinkedHashMap<String, T>();
        input.forEach((id, value) -> {
            if (id == null || !id.matches("[a-z0-9_-]+") || value == null) throw new IllegalArgumentException(label + "包含无效条目");
            if (result.putIfAbsent(id, value) != null) throw new IllegalArgumentException(label + "重复卡牌：" + id);
        });
        return Map.copyOf(result);
    }
}
