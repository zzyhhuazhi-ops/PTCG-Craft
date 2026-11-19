package dev.hua.ptcglive.engine.effect;

import com.google.gson.JsonObject;
import java.util.*;

/** Actual attack damage to this source, or an explicitly named own Active watched by an in-play Ability. */
public record DamageTriggerPolicy(boolean activeOnly, boolean lethalOnly, boolean optional,
                                  EnergyTriggerPolicy.Usage usage, boolean opponentOnly, boolean fullHpOnly, EventPokemonScope watch, Set<String> pokemonNames, int minimumDamage, Set<dev.hua.ptcglive.rules.PokemonFamily> attackerFamilies,
                                  boolean requiresWeaknessToOpponentActive, Set<dev.hua.ptcglive.engine.field.EnergyType> attackerPokemonTypes) implements AbilityEventPolicy {
    public DamageTriggerPolicy(boolean activeOnly,boolean lethalOnly,boolean optional,EnergyTriggerPolicy.Usage usage,boolean opponentOnly,boolean fullHpOnly,EventPokemonScope watch,Set<String> pokemonNames) {this(activeOnly,lethalOnly,optional,usage,opponentOnly,fullHpOnly,watch,pokemonNames,10,Set.of(),false,Set.of());}
    public DamageTriggerPolicy(boolean activeOnly,boolean lethalOnly,boolean optional,EnergyTriggerPolicy.Usage usage,boolean opponentOnly,boolean fullHpOnly) {this(activeOnly,lethalOnly,optional,usage,opponentOnly,fullHpOnly,EventPokemonScope.SELF,Set.of());}
    public DamageTriggerPolicy(boolean activeOnly,boolean lethalOnly,boolean optional,EnergyTriggerPolicy.Usage usage,boolean opponentOnly) { this(activeOnly,lethalOnly,optional,usage,opponentOnly,false); }
    public DamageTriggerPolicy(boolean activeOnly,boolean lethalOnly,boolean optional,EnergyTriggerPolicy.Usage usage) { this(activeOnly,lethalOnly,optional,usage,true); }
    public DamageTriggerPolicy {
        Objects.requireNonNull(usage);Objects.requireNonNull(watch);pokemonNames=Set.copyOf(pokemonNames);attackerFamilies=Set.copyOf(attackerFamilies);attackerPokemonTypes=Set.copyOf(attackerPokemonTypes);
        if(minimumDamage<10||minimumDamage>10000||minimumDamage%10!=0)throw new IllegalArgumentException("实际伤害下限须为10至10000的10倍数");
        if(watch==EventPokemonScope.SELF) {
            if(!pokemonNames.isEmpty())throw new IllegalArgumentException("自身受伤监听不能混用名称筛选");
        } else if(watch!=EventPokemonScope.OWN_FIELD||!activeOnly||!opponentOnly||lethalOnly||fullHpOnly||optional
            ||minimumDamage!=10||!attackerFamilies.isEmpty()||!attackerPokemonTypes.isEmpty()||usage!=EnergyTriggerPolicy.Usage.NONE||pokemonNames.size()>8
            ||pokemonNames.stream().anyMatch(n->n.isBlank()||!n.equals(n.strip())))
            throw new IllegalArgumentException("场上受伤监听仅核验己方指定名称战斗宝可梦受到对方招式伤害，每次强制反击");
        if(requiresWeaknessToOpponentActive && (!activeOnly || !opponentOnly || lethalOnly || optional
            ||usage!=EnergyTriggerPolicy.Usage.NONE || watch!=EventPokemonScope.SELF || !pokemonNames.isEmpty()
            ||!attackerFamilies.isEmpty() || !attackerPokemonTypes.isEmpty() || minimumDamage!=10))
            throw new IllegalArgumentException("弱点对方战斗位触发必须是自身战斗位、对手招式、非致命、强制且不带其它筛选");
    }
    public boolean watches(UUID source,UUID subject,String printedName) {
        return watch==EventPokemonScope.SELF?source.equals(subject):pokemonNames.isEmpty()||printedName!=null&&pokemonNames.contains(printedName);
    }
    @Override public EffectTrigger trigger() { return EffectTrigger.ON_DAMAGE; }
    public boolean matches(boolean wasActive, int actualDamage, boolean damageWouldKnockOut) {
        return matches(wasActive,actualDamage,damageWouldKnockOut,false);
    }
    public boolean matches(boolean wasActive,int actualDamage,boolean damageWouldKnockOut,boolean hadFullHp) {
        return (!fullHpOnly || hadFullHp) && actualDamage>=minimumDamage && (!activeOnly || wasActive) && (!lethalOnly || damageWouldKnockOut);
    }
    public boolean matchesAttacker(dev.hua.ptcglive.rules.CardDefinition card) {
        return (attackerFamilies.isEmpty()||!Collections.disjoint(attackerFamilies,card.pokemonFamilies()))
            &&(attackerPokemonTypes.isEmpty()||!Collections.disjoint(attackerPokemonTypes,card.pokemonTypes()));
    }
    public static DamageTriggerPolicy parse(JsonObject json) {
        if (!Set.of("trigger","from","activeOnly","lethalOnly","optional","usage","fullHpOnly","watch","pokemonNames","minimumDamage","attackerFamilies","attackerPokemonTypes","requiresWeaknessToOpponentActive").containsAll(json.keySet())
            || !json.has("from") || !Set.of("OPPONENT_ATTACK","ANY_ATTACK").contains(json.get("from").getAsString()) || !json.has("usage"))
            throw new IllegalArgumentException("伤害触发须明确OPPONENT_ATTACK来源与usage，不能混用其它事件字段");
        if(json.has("watch")&&(!json.get("watch").isJsonPrimitive()||!json.getAsJsonPrimitive("watch").isString()))throw new IllegalArgumentException("受伤监听范围必须为字符串");
        var watch=json.has("watch")?EventPokemonScope.valueOf(json.get("watch").getAsString()):EventPokemonScope.SELF;
        var names=new LinkedHashSet<String>();
        if(json.has("pokemonNames")) {
            if(!json.has("watch")||watch!=EventPokemonScope.OWN_FIELD||!json.get("pokemonNames").isJsonArray())throw new IllegalArgumentException("受伤名称列表必须配合OWN_FIELD");
            for(var name:json.getAsJsonArray("pokemonNames")) {
                if(!name.isJsonPrimitive()||!name.getAsJsonPrimitive().isString()||!names.add(name.getAsString()))throw new IllegalArgumentException("受伤名称必须是互不重复的字符串");
            }
        }
        int minimum=10;
        if(json.has("minimumDamage")) {
            var value=json.get("minimumDamage");
            if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("实际伤害下限必须为整数");
            try {minimum=value.getAsBigDecimal().intValueExact();}catch(ArithmeticException invalid){throw new IllegalArgumentException("实际伤害下限必须为整数",invalid);}
        }
        var families=EnumSet.noneOf(dev.hua.ptcglive.rules.PokemonFamily.class);
        if(json.has("attackerFamilies")) {
            if(!json.get("attackerFamilies").isJsonArray()||json.getAsJsonArray("attackerFamilies").isEmpty())throw new IllegalArgumentException("攻击者印刷家族须为非空列表");
            for(var family:json.getAsJsonArray("attackerFamilies")) {
                if(!family.isJsonPrimitive()||!family.getAsJsonPrimitive().isString()||!families.add(dev.hua.ptcglive.rules.PokemonFamily.valueOf(family.getAsString())))throw new IllegalArgumentException("攻击者家族须为互不重复的已审核标记");
            }
        }
        var types=EnumSet.noneOf(dev.hua.ptcglive.engine.field.EnergyType.class);
        if(json.has("attackerPokemonTypes")) {
            if(!json.get("attackerPokemonTypes").isJsonArray()||json.getAsJsonArray("attackerPokemonTypes").isEmpty())throw new IllegalArgumentException("攻击者属性须为非空列表");
            for(var type:json.getAsJsonArray("attackerPokemonTypes"))
                if(!type.isJsonPrimitive()||!type.getAsJsonPrimitive().isString()||!types.add(dev.hua.ptcglive.engine.field.EnergyType.valueOf(type.getAsString())))throw new IllegalArgumentException("攻击者属性须互不重复");
        }
        boolean weakness=json.has("requiresWeaknessToOpponentActive")&&bool(json,"requiresWeaknessToOpponentActive");
        return new DamageTriggerPolicy(bool(json,"activeOnly"),bool(json,"lethalOnly"),bool(json,"optional"),EnergyTriggerPolicy.Usage.valueOf(json.get("usage").getAsString()),json.get("from").getAsString().equals("OPPONENT_ATTACK"),json.has("fullHpOnly")&&bool(json,"fullHpOnly"),watch,names,minimum,families,weakness,types);
    }
    private static boolean bool(JsonObject json,String field) {
        if(!json.has(field)||!json.get(field).isJsonPrimitive()||!json.getAsJsonPrimitive(field).isBoolean())
            throw new IllegalArgumentException("伤害触发必须明确布尔值："+field);
        return json.get(field).getAsBoolean();
    }
}
