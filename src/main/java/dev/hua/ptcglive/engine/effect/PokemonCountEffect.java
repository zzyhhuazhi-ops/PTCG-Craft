package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.CardDefinition;
import java.util.*;

/** Counts current top cards in one explicit public field range, never cards under an Evolution. */
public record PokemonCountEffect(PokemonFamilyFilter families,String attackId,Mode mode,PokemonTypeFilter types,Set<String> names,int maximumHpEquals,dev.hua.ptcglive.engine.field.EnergyType attachedEnergyType,String nameContains,int minimumDamageCounters) {
    public PokemonCountEffect(PokemonFamilyFilter families,String attackId,Mode mode,PokemonTypeFilter types,Set<String> names,int maximumHpEquals,dev.hua.ptcglive.engine.field.EnergyType attachedEnergyType,String nameContains){this(families,attackId,mode,types,names,maximumHpEquals,attachedEnergyType,nameContains,0);}
    public PokemonCountEffect(PokemonFamilyFilter families,String attackId,Mode mode,PokemonTypeFilter types,Set<String> names,int maximumHpEquals){this(families,attackId,mode,types,names,maximumHpEquals,null,null);}
    public PokemonCountEffect(PokemonFamilyFilter families,String attackId,Mode mode,PokemonTypeFilter types,Set<String> names){this(families,attackId,mode,types,names,0);}
    public PokemonCountEffect(PokemonFamilyFilter families,String attackId,Mode mode,PokemonTypeFilter types){this(families,attackId,mode,types,Set.of());}
    public PokemonCountEffect {names=Set.copyOf(names);if(minimumDamageCounters<0||minimumDamageCounters>60||minimumDamageCounters>0&&mode!=Mode.POKEMON)throw new IllegalArgumentException("伤害指示物门槛仅用于宝可梦只数，且为1至60；0表示未筛选");if(nameContains!=null&&(nameContains.isBlank()||!nameContains.equals(nameContains.trim())||nameContains.length()>64))throw new IllegalArgumentException("印刷名称子串须为1至64个字符，不能含首尾空白");if(maximumHpEquals<0||maximumHpEquals>1000||maximumHpEquals%10!=0||maximumHpEquals>0&&mode!=Mode.POKEMON)throw new IllegalArgumentException("最大HP精确筛选仅用于宝可梦只数，且为10至1000的10倍数");}
    public PokemonCountEffect(PokemonFamilyFilter families,String attackId,Mode mode){this(families,attackId,mode,PokemonTypeFilter.ANY);}
    public enum Mode { POKEMON, DISTINCT_TYPES, DAMAGE_COUNTERS, ATTACHED_TOOLS, ATTACHED_ENERGY_UNITS }
    public static final Set<String> FIELDS=Set.of("amount","pokemon_families","family_match","attack_id","count_mode","pokemon_types","pokemon_names","maximum_hp_equals","attached_energy_type","name_contains","damage_counters_min","pokemon_stage","has_abilities");
    public static PokemonCountEffect from(EffectDefinition effect) {
        if(!effect.type().equals("damage_per_pokemon")||!effect.effects().isEmpty()||!FIELDS.containsAll(effect.arguments().keySet())
            ||!Set.of(EffectTarget.OWN_BENCH,EffectTarget.OPPONENT_BENCH,EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON).contains(effect.target()))
            throw new IllegalArgumentException("宝可梦计数伤害须指定公开整方或备战范围，且仅包含已核验筛选");
        var args=effect.arguments();String attack=args.get("attack_id");
        if(attack!=null)AttackCountBindings.name(attack);
        Mode mode=Mode.valueOf(args.getOrDefault("count_mode","POKEMON"));
        if(mode==Mode.ATTACHED_ENERGY_UNITS&&!args.containsKey("attached_energy_type"))throw new IllegalArgumentException("附着能量单位计数须指定属性");
        if(args.containsKey("pokemon_stage")) {
            String stage=args.get("pokemon_stage");
            if(!Set.of("0","1","2","BASIC","EVOLUTION").contains(stage)||mode!=Mode.POKEMON)
                throw new IllegalArgumentException("攻击宝可梦阶段筛选须为BASIC、EVOLUTION或0至2并按宝可梦只数计数");
        }
        if(args.containsKey("has_abilities")&&!"true".equals(args.get("has_abilities")))throw new IllegalArgumentException("特性计数筛选须明确true");
        int amount=Integer.parseInt(args.getOrDefault("amount","0"));
        if(amount<10||amount>1000||amount%10!=0)throw new IllegalArgumentException("宝可梦计数伤害须为10至1000的10倍数");
        int hp=0;
        if(args.containsKey("maximum_hp_equals")){hp=Integer.parseInt(args.get("maximum_hp_equals"));if(hp<=0)throw new IllegalArgumentException("最大HP精确筛选必须为正数");}
        int counters=0;
        if(args.containsKey("damage_counters_min")){counters=Integer.parseInt(args.get("damage_counters_min"));if(counters<=0)throw new IllegalArgumentException("伤害指示物门槛必须为正数");}
        return new PokemonCountEffect(PokemonFamilyFilter.fromWire(args.get("pokemon_families"),args.get("family_match")),attack,mode,PokemonTypeFilter.fromWire(args.get("pokemon_types")),names(args),hp,args.containsKey("attached_energy_type")?dev.hua.ptcglive.engine.field.EnergyType.valueOf(args.get("attached_energy_type")):null,args.get("name_contains"),counters);
    }
    public static Set<String> names(Map<String,String> arguments) {
        return arguments.containsKey("pokemon_names")?TurnHistoryCondition.values(arguments.get("pokemon_names")):Set.of();
    }
    /** Shared printed-name/family query for server-owned field counts, including dynamic coin counts. */
    public static PokemonCountEffect nameQuery(Map<String,String> arguments) {
        return fieldQuery(arguments);
    }
    /** Shared field-count query for names, printed families, and printed/current Pokémon types. */
    public static PokemonCountEffect fieldQuery(Map<String,String> arguments) {
        return new PokemonCountEffect(PokemonFamilyFilter.fromWire(arguments.get("pokemon_families"),arguments.get("family_match")),
            null,Mode.POKEMON,PokemonTypeFilter.fromWire(arguments.get("pokemon_types")),names(arguments),0,null,arguments.get("name_contains"),
            arguments.containsKey("damage_counters_min")?Integer.parseInt(arguments.get("damage_counters_min")):0);
    }
    public static void validateNameCountTarget(EffectTarget target) {
        if(!Set.of(EffectTarget.SELF,EffectTarget.OWN_BENCH,EffectTarget.OPPONENT_BENCH,EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON).contains(target))
            throw new IllegalArgumentException("场上名称计数仅支持整方场上或备战范围；SELF表示己方场上");
    }
    public boolean matches(CardDefinition definition,PokemonProfile profile) {
        if(maximumHpEquals>0)throw new IllegalStateException("最大HP计数须传入服务器当前最大HP");
        return matches(definition,profile,0);
    }
    public boolean matches(CardDefinition definition,PokemonProfile profile,int currentMaximumHp) {
        if(attachedEnergyType!=null)throw new IllegalStateException("附能属性筛选须传入服务器当前供给数量");
        return matches(definition,profile,currentMaximumHp,0);
    }
    /** Counts the host once if it supplies at least one matching unit, regardless of Energy cards/units count. */
    public boolean matches(CardDefinition definition,PokemonProfile profile,int currentMaximumHp,int matchingEnergyUnits) {
        if(minimumDamageCounters>0)throw new IllegalStateException("受伤宝可梦计数须传入服务器当前伤害指示物数量");
        return matches(definition,profile,currentMaximumHp,matchingEnergyUnits,0);
    }
    /** Damage is a live host predicate: several counters still count this Pokemon only once. */
    public boolean matches(CardDefinition definition,PokemonProfile profile,int currentMaximumHp,int matchingEnergyUnits,int currentDamageCounters) {
        return matches(definition,profile,currentMaximumHp,matchingEnergyUnits,currentDamageCounters,null);
    }
    public boolean matches(CardDefinition definition,PokemonProfile profile,int currentMaximumHp,int matchingEnergyUnits,int currentDamageCounters,Set<dev.hua.ptcglive.engine.field.EnergyType> currentTypes) {
        if(currentDamageCounters<0)throw new IllegalArgumentException("服务器伤害指示物数量不能为负数");
        if(matchingEnergyUnits<0)throw new IllegalArgumentException("服务器供能数量不能为负数");
        boolean typeMatch=currentTypes==null?types.matches(definition,profile):types.types().isEmpty()||currentTypes.stream().anyMatch(types.types()::contains);
        return (minimumDamageCounters==0||currentDamageCounters>=minimumDamageCounters)&&(attachedEnergyType==null||matchingEnergyUnits>0)&&(nameContains==null||profile.pokemonName()!=null&&profile.pokemonName().contains(nameContains))&&(maximumHpEquals==0||maximumHpEquals==currentMaximumHp)&&(names.isEmpty()||profile.pokemonName()!=null&&names.contains(profile.pokemonName()))&&families.matches(definition)&&typeMatch&&(attackId==null||profile.attacks().stream().anyMatch(a->a.id().equals(attackId)));
    }
}
