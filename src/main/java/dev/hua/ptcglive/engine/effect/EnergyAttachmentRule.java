package dev.hua.ptcglive.engine.effect;

import com.google.gson.JsonObject;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.CardDefinition;
import java.util.*;

/** Printed host eligibility and the separate continuous discard clause; not an attachment effect. */
public record EnergyAttachmentRule(Set<Stage> stages,Set<EnergyType> pokemonTypes,Set<dev.hua.ptcglive.rules.PokemonFamily> families,String pokemonNameContains,boolean discardIfInvalid) {
    public EnergyAttachmentRule(Set<Stage> stages,Set<EnergyType> pokemonTypes,Set<dev.hua.ptcglive.rules.PokemonFamily> families,boolean discardIfInvalid) {this(stages,pokemonTypes,families,null,discardIfInvalid);}
    public EnergyAttachmentRule(Set<Stage> stages,Set<EnergyType> pokemonTypes,boolean discardIfInvalid) {this(stages,pokemonTypes,Set.of(),null,discardIfInvalid);}
    public enum Stage { BASIC,EVOLUTION }
    public static final EnergyAttachmentRule NONE=new EnergyAttachmentRule(Set.of(),Set.of(),false);
    public EnergyAttachmentRule {
        stages=Set.copyOf(Objects.requireNonNull(stages));pokemonTypes=Set.copyOf(Objects.requireNonNull(pokemonTypes));families=Set.copyOf(Objects.requireNonNull(families));
        if(pokemonNameContains!=null&&(pokemonNameContains.isBlank()||!pokemonNameContains.equals(pokemonNameContains.trim())||pokemonNameContains.length()>64))throw new IllegalArgumentException("特殊能量宿主名称片段须为1至64字符");
        if(discardIfInvalid&&stages.isEmpty()&&pokemonTypes.isEmpty()&&families.isEmpty()&&pokemonNameContains==null)throw new IllegalArgumentException("自弃限制必须指定宿主条件");
    }
    public boolean allows(CardDefinition host,PokemonProfile profile) {
        if(!stages.isEmpty()&&!stages.contains(host.basicPokemon()?Stage.BASIC:Stage.EVOLUTION))return false;
        if(!families.isEmpty()&&Collections.disjoint(families,host.pokemonFamilies()))return false;
        if(pokemonNameContains!=null&&(profile.pokemonName()==null||!profile.pokemonName().contains(pokemonNameContains)))return false;
        if(pokemonTypes.isEmpty())return true;
        if(host.pokemonTypes().isEmpty()&&profile.pokemonType()==null)throw new IllegalStateException("附着限制需要已审核的宝可梦属性，不能从攻击推断");
        return new PokemonTypeFilter(pokemonTypes).matches(host,profile);
    }
    public static EnergyAttachmentRule from(JsonObject value) {
        if(!Set.of("stages","pokemon_types","pokemon_families","pokemon_name_contains","discard_if_invalid").containsAll(value.keySet()))
            throw new IllegalArgumentException("特殊能量附着限制包含未支持字段");
        var stages=new LinkedHashSet<Stage>();var types=new LinkedHashSet<EnergyType>();
        var families=new LinkedHashSet<dev.hua.ptcglive.rules.PokemonFamily>();
        if(value.has("stages")) {
            for(var stage:value.getAsJsonArray("stages"))if(!stages.add(Stage.valueOf(stage.getAsString())))throw new IllegalArgumentException("重复宿主阶段");
            if(stages.isEmpty())throw new IllegalArgumentException("宿主阶段列表不能是空数组");
        }
        if(value.has("pokemon_types")) {
            for(var type:value.getAsJsonArray("pokemon_types"))if(!types.add(EnergyType.valueOf(type.getAsString())))throw new IllegalArgumentException("重复宿主属性");
            if(types.isEmpty())throw new IllegalArgumentException("宿主属性列表不能是空数组");
        }
        if(value.has("pokemon_families")) {
            for(var family:value.getAsJsonArray("pokemon_families"))if(!families.add(dev.hua.ptcglive.rules.PokemonFamily.valueOf(family.getAsString())))throw new IllegalArgumentException("重复宿主家族");
            if(families.isEmpty())throw new IllegalArgumentException("宿主家族列表不能为空");
        }
        String name=value.has("pokemon_name_contains")?value.get("pokemon_name_contains").getAsString():null;
        if(stages.isEmpty()&&types.isEmpty()&&families.isEmpty()&&name==null)throw new IllegalArgumentException("附着限制必须有宿主阶段、属性、家族或名称片段");
        if(value.has("discard_if_invalid")&&(!value.get("discard_if_invalid").isJsonPrimitive()||!value.getAsJsonPrimitive("discard_if_invalid").isBoolean()))
            throw new IllegalArgumentException("discard_if_invalid必须是布尔值");
        return new EnergyAttachmentRule(stages,types,families,name,value.has("discard_if_invalid")&&value.get("discard_if_invalid").getAsBoolean());
    }
}
