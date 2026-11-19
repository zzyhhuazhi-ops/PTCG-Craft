package dev.hua.ptcglive.engine.effect;

import java.util.*;
import dev.hua.ptcglive.rules.CardDefinition;
import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.PokemonFamily;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.engine.field.SpecialCondition;

/** Current attacking card facts, supplied only by the match authority. */
public record AttackSourceFilter(Kind kind, Set<String> excludedNames,
                                 PokemonTypeFilter pokemonTypes, PokemonTypeFilter excludedPokemonTypes,
                                 Integer maximumEnergyUnits, SpecialCondition status, boolean requiresAbilities, Integer maximumRemainingHp) {
    public enum Kind { ANY, MEGA_EVOLUTION_EX, BASIC, BASIC_EX, EVOLUTION, EX, EX_LEGACY, GX, V, VMAX, VSTAR, TERA, ANCIENT, FUTURE, TEAM_PLASMA, SPECIAL_ENERGY, ULTRA_BEAST_GX_OR_EX_LEGACY }
    public static final AttackSourceFilter ANY=new AttackSourceFilter(Kind.ANY,Set.of());
    public static final Set<String> FIELDS=Set.of("attacker_filter","except_pokemon_names","attacker_pokemon_types","except_attacker_pokemon_types","attacker_energy_max","attacker_status","attacker_has_abilities","attacker_remaining_hp_max");
    public AttackSourceFilter(Kind kind,Set<String> excludedNames){this(kind,excludedNames,PokemonTypeFilter.ANY,PokemonTypeFilter.ANY);}
    public AttackSourceFilter(Kind kind,Set<String> excludedNames,PokemonTypeFilter pokemonTypes,PokemonTypeFilter excludedPokemonTypes){this(kind,excludedNames,pokemonTypes,excludedPokemonTypes,null,null);}
    public AttackSourceFilter(Kind kind,Set<String> excludedNames,PokemonTypeFilter pokemonTypes,PokemonTypeFilter excludedPokemonTypes,Integer maximumEnergyUnits,SpecialCondition status){this(kind,excludedNames,pokemonTypes,excludedPokemonTypes,maximumEnergyUnits,status,false);}
    public AttackSourceFilter(Kind kind,Set<String> excludedNames,PokemonTypeFilter pokemonTypes,PokemonTypeFilter excludedPokemonTypes,Integer maximumEnergyUnits,SpecialCondition status,boolean requiresAbilities){this(kind,excludedNames,pokemonTypes,excludedPokemonTypes,maximumEnergyUnits,status,requiresAbilities,null);}
    public AttackSourceFilter {
        if(maximumRemainingHp!=null&&maximumRemainingHp!=40)throw new IllegalArgumentException("攻击者剩余HP筛选目前仅核验40以下");
        Objects.requireNonNull(kind);excludedNames=Set.copyOf(excludedNames);Objects.requireNonNull(pokemonTypes);Objects.requireNonNull(excludedPokemonTypes);
        if(maximumEnergyUnits!=null&&(maximumEnergyUnits<0||maximumEnergyUnits>240))throw new IllegalArgumentException("攻击者能量上限须为0至240单位");
    }
    /** Actual actor facts at the relevant server checkpoint, never facts from a copied attack's provider. */
    public record Facts(CardDefinition card,String canonicalName,boolean specialEnergyAttached,PokemonProfile profile,
                        int energyUnits,Set<SpecialCondition> conditions,Boolean possessedAbilities,Integer remainingHp) {
        public Facts(CardDefinition card,String canonicalName,boolean specialEnergyAttached,PokemonProfile profile,int energyUnits,Set<SpecialCondition> conditions,Boolean possessedAbilities){this(card,canonicalName,specialEnergyAttached,profile,energyUnits,conditions,possessedAbilities,null);}
        /** Legacy callers have no authoritative Ability-possession proof. */
        public Facts(CardDefinition card,String canonicalName,boolean specialEnergyAttached,PokemonProfile profile,int energyUnits,Set<SpecialCondition> conditions){this(card,canonicalName,specialEnergyAttached,profile,energyUnits,conditions,null);}
        public Facts {Objects.requireNonNull(card);Objects.requireNonNull(profile);conditions=Set.copyOf(conditions);if(energyUnits<0)throw new IllegalArgumentException("能量单位不可为负数");}
    }
    public static AttackSourceFilter from(Map<String,String> values) {
        var kind=Kind.valueOf(values.getOrDefault("attacker_filter","ANY"));
        var names=new LinkedHashSet<String>();
        if(values.containsKey("except_pokemon_names")) {
            if(!values.containsKey("attacker_filter"))throw new IllegalArgumentException("攻击者名称排除必须与attacker_filter一起声明");
            for(String name:values.get("except_pokemon_names").split("\\|",-1)) {
                if(name.isBlank()||!name.equals(name.trim())||!names.add(name))throw new IllegalArgumentException("攻击者名称排除含空白或重复项");
            }
        }
        if(values.containsKey("attacker_has_abilities")&&!"true".equals(values.get("attacker_has_abilities")))
            throw new IllegalArgumentException("攻击者特性筛选须明确true");
        return new AttackSourceFilter(kind,names,types(values,"attacker_pokemon_types"),types(values,"except_attacker_pokemon_types"),
            values.containsKey("attacker_energy_max")?Integer.valueOf(values.get("attacker_energy_max")):null,
            values.containsKey("attacker_status")?SpecialCondition.valueOf(values.get("attacker_status")):null,values.containsKey("attacker_has_abilities"),values.containsKey("attacker_remaining_hp_max")?Integer.valueOf(values.get("attacker_remaining_hp_max")):null);
    }
    private static PokemonTypeFilter types(Map<String,String> values,String key) {
        if(!values.containsKey(key))return PokemonTypeFilter.ANY;
        String raw=values.get(key);
        if(raw==null||raw.isBlank())throw new IllegalArgumentException("攻击者属性筛选不可为空");
        var seen=EnumSet.noneOf(EnergyType.class);
        for(String value:raw.split("\\|",-1)) {
            if(value.isBlank()||!value.equals(value.trim())||!seen.add(EnergyType.valueOf(value)))
                throw new IllegalArgumentException("攻击者属性筛选含空白、重复或未知项");
        }
        return new PokemonTypeFilter(seen);
    }
    public boolean typeRestricted(){return !pokemonTypes.types().isEmpty()||!excludedPokemonTypes.types().isEmpty();}
    public boolean dynamicRestricted(){return maximumEnergyUnits!=null||status!=null||requiresAbilities||maximumRemainingHp!=null;}
    public boolean matches(Facts facts) {
        return matchesStatic(facts.card(),facts.canonicalName(),facts.specialEnergyAttached(),facts.profile())
            &&(maximumEnergyUnits==null||facts.energyUnits()<=maximumEnergyUnits)
            &&(status==null||facts.conditions().contains(status))
            &&(!requiresAbilities||Boolean.TRUE.equals(facts.possessedAbilities()))
            &&(maximumRemainingHp==null||facts.remainingHp()!=null&&facts.remainingHp()<=maximumRemainingHp);
    }
    public boolean matches(CardDefinition card,String canonicalName,boolean specialEnergyAttached) {
        return !dynamicRestricted()&&matchesCard(card,canonicalName,specialEnergyAttached)&&matchesTypes(card.pokemonTypes());
    }
    public boolean matches(CardDefinition card,String canonicalName,boolean specialEnergyAttached,PokemonProfile profile) {
        return !dynamicRestricted()&&matchesStatic(card,canonicalName,specialEnergyAttached,profile);
    }
    private boolean matchesStatic(CardDefinition card,String canonicalName,boolean specialEnergyAttached,PokemonProfile profile) {
        var types=card.pokemonTypes();
        if(types.isEmpty()&&profile.pokemonType()!=null)types=Set.of(profile.pokemonType());
        return matchesCard(card,canonicalName,specialEnergyAttached)&&matchesTypes(types);
    }
    private boolean matchesTypes(Set<EnergyType> current) {
        if(!typeRestricted())return true;
        if(current.isEmpty())return false;
        return (pokemonTypes.types().isEmpty()||!Collections.disjoint(pokemonTypes.types(),current))
            &&Collections.disjoint(excludedPokemonTypes.types(),current);
    }
    private boolean matchesCard(CardDefinition card,String canonicalName,boolean specialEnergyAttached) {
        if(canonicalName!=null&&excludedNames.contains(canonicalName))return false;
        return switch(kind) {
            case ANY -> true;
            case BASIC -> card.basicPokemon();
            case BASIC_EX -> card.basicPokemon()&&card.pokemonFamilies().contains(PokemonFamily.EX);
            case EVOLUTION -> card.category()==CardCategory.POKEMON&&!card.basicPokemon();
            case MEGA_EVOLUTION_EX -> card.pokemonFamilies().contains(PokemonFamily.MEGA_EVOLUTION_EX);
            case EX -> card.pokemonFamilies().contains(PokemonFamily.EX);
            case EX_LEGACY -> card.pokemonFamilies().contains(PokemonFamily.EX_LEGACY);
            case GX -> card.pokemonFamilies().contains(PokemonFamily.GX);
            case V -> card.pokemonFamilies().contains(PokemonFamily.V);
            case VMAX -> card.pokemonFamilies().contains(PokemonFamily.VMAX);
            case VSTAR -> card.pokemonFamilies().contains(PokemonFamily.VSTAR);
            case TERA -> card.pokemonFamilies().contains(PokemonFamily.TERA);
            case ANCIENT -> card.pokemonFamilies().contains(PokemonFamily.ANCIENT);
            case FUTURE -> card.pokemonFamilies().contains(PokemonFamily.FUTURE);
            case TEAM_PLASMA -> card.pokemonFamilies().contains(PokemonFamily.TEAM_PLASMA);
            case SPECIAL_ENERGY -> specialEnergyAttached;
            case ULTRA_BEAST_GX_OR_EX_LEGACY -> card.pokemonFamilies().contains(PokemonFamily.ULTRA_BEAST)
                &&(card.pokemonFamilies().contains(PokemonFamily.GX)||card.pokemonFamilies().contains(PokemonFamily.EX_LEGACY));
        };
    }
}
