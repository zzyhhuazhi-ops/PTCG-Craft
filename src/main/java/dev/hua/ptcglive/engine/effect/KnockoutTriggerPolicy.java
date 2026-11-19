package dev.hua.ptcglive.engine.effect;

import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Source/field KO listeners; subject location and attacker relation are explicit server-checked data. */
public record KnockoutTriggerPolicy(Cause cause, boolean activeOnly, boolean optional,
                                    EnergyTriggerPolicy.Usage usage, EventPokemonScope watch, boolean fullHpOnly) implements AbilityEventPolicy {
    public KnockoutTriggerPolicy(Cause cause,boolean activeOnly,boolean optional,EnergyTriggerPolicy.Usage usage,EventPokemonScope watch) { this(cause,activeOnly,optional,usage,watch,false); }
    public KnockoutTriggerPolicy(Cause cause,boolean activeOnly,boolean optional,EnergyTriggerPolicy.Usage usage) {
        this(cause,activeOnly,optional,usage,EventPokemonScope.SELF);
    }
    public enum Cause { ANY, ATTACK_DAMAGE, OPPONENT_ATTACK_DAMAGE, SOURCE_ATTACK_DAMAGE }
    public KnockoutTriggerPolicy { Objects.requireNonNull(cause); Objects.requireNonNull(usage); Objects.requireNonNull(watch); if(fullHpOnly&&cause!=Cause.OPPONENT_ATTACK_DAMAGE)throw new IllegalArgumentException("满HP击倒触发必须限定对手招式伤害"); }
    @Override public EffectTrigger trigger() { return EffectTrigger.ON_KNOCKOUT; }
    public boolean matches(boolean active, boolean opponentAttackDamage) {
        return matches(active,opponentAttackDamage,false);
    }
    public boolean matches(boolean active, boolean opponentAttackDamage, boolean hadFullHp) {
        return (!fullHpOnly || hadFullHp) && (!activeOnly || active) && (cause==Cause.ANY || cause==Cause.OPPONENT_ATTACK_DAMAGE&&opponentAttackDamage);
    }
    public boolean matches(UUID owner,UUID source,UUID subjectOwner,UUID subject,boolean active,boolean attackDamage,
                           EffectTargetResolution.PokemonTarget attacker) {
        return matches(owner,source,subjectOwner,subject,active,attackDamage,attacker,false);
    }
    public boolean matches(UUID owner,UUID source,UUID subjectOwner,UUID subject,boolean active,boolean attackDamage,
                           EffectTargetResolution.PokemonTarget attacker,boolean hadFullHp) {
        if(cause==Cause.ATTACK_DAMAGE)return watch.matches(owner,source,subjectOwner,subject)
            &&(!activeOnly||active)&&attackDamage&&attacker!=null;
        if(cause==Cause.SOURCE_ATTACK_DAMAGE) return watch.matches(owner,source,subjectOwner,subject)
            && (!activeOnly||active) && attackDamage && attacker!=null
            && owner.equals(attacker.owner()) && source.equals(attacker.pokemonId());
        return watch.matches(owner,source,subjectOwner,subject) && matches(active,
            attackDamage && attacker!=null && !owner.equals(attacker.owner()),hadFullHp);
    }
    public static KnockoutTriggerPolicy parse(JsonObject json) {
        if(!Set.of("trigger","cause","activeOnly","optional","usage","watch","fullHpOnly").containsAll(json.keySet())
            || !json.has("cause") || !json.has("usage"))
            throw new IllegalArgumentException("击倒触发必须明确cause和usage，不能混用其它事件字段");
        return new KnockoutTriggerPolicy(Cause.valueOf(json.get("cause").getAsString()),bool(json,"activeOnly"),
            bool(json,"optional"),EnergyTriggerPolicy.Usage.valueOf(json.get("usage").getAsString()),
            json.has("watch")?EventPokemonScope.valueOf(json.get("watch").getAsString()):EventPokemonScope.SELF,json.has("fullHpOnly")&&bool(json,"fullHpOnly"));
    }
    private static boolean bool(JsonObject json,String field) {
        if(!json.has(field)||!json.get(field).isJsonPrimitive()||!json.getAsJsonPrimitive(field).isBoolean())
            throw new IllegalArgumentException("击倒触发须明确布尔值："+field);
        return json.get(field).getAsBoolean();
    }
}
