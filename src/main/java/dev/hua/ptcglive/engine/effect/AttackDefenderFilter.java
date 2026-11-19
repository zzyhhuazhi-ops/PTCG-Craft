package dev.hua.ptcglive.engine.effect;

import java.util.Map;
import java.util.Set;
import java.util.Objects;
import dev.hua.ptcglive.rules.CardDefinition;
import dev.hua.ptcglive.engine.field.PokemonProfile;

/** Live opposing Active card facts for damage bonuses; never the attack's damage type. */
public record AttackDefenderFilter(PokemonTypeFilter types,PokemonFamilyFilter families,Stage stage) {
    public enum Stage { ANY, BASIC, EVOLUTION }
    public static final Set<String> FIELDS=Set.of("defender_pokemon_types","defender_pokemon_families","defender_family_match","defender_stage");
    public static final AttackDefenderFilter ANY=new AttackDefenderFilter(PokemonTypeFilter.ANY,PokemonFamilyFilter.ANY,Stage.ANY);
    public AttackDefenderFilter {Objects.requireNonNull(types);Objects.requireNonNull(families);}
    public static AttackDefenderFilter from(Map<String,String> args) {
        for(String key:FIELDS)if(args.containsKey(key)&&(args.get(key)==null||args.get(key).isBlank()))throw new IllegalArgumentException("受伤方筛选参数不可为空");
        return new AttackDefenderFilter(PokemonTypeFilter.fromWire(args.get("defender_pokemon_types")),PokemonFamilyFilter.fromWire(args.get("defender_pokemon_families"),args.get("defender_family_match")),
            Stage.valueOf(args.getOrDefault("defender_stage","ANY")));
    }
    public boolean restricted(){return !types.types().isEmpty()||!families.families().isEmpty()||stage!=Stage.ANY;}
    public boolean matches(CardDefinition card,PokemonProfile profile){return types.matches(card,profile)&&families.matches(card)
        &&(stage==Stage.ANY||stage==Stage.BASIC==card.basicPokemon());}
}
