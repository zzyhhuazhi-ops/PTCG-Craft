package dev.hua.ptcglive.engine.effect;
import java.util.Set;

/** Audited temporary Weakness replacement applied after attack damage. */
public final class WeaknessOverride {
    private WeaknessOverride() { }
    public static final Set<String> FIELDS=Set.of("energy_type","multiplier","duration");
    public static void validate(EffectDefinition effect) {
        if(effect.type().equals("weakness_override")&&effect.target()==EffectTarget.OPPONENT_ACTIVE&&effect.effects().isEmpty()
            &&effect.arguments().keySet().equals(FIELDS)&&"LEAVES_ACTIVE".equals(effect.arguments().get("duration"))
            &&"PRESERVE".equals(effect.arguments().get("multiplier"))
            &&Set.of("GRASS","FIRE","WATER","LIGHTNING","PSYCHIC","FIGHTING","DARKNESS","METAL","DRAGON").contains(effect.arguments().get("energy_type")))return;
        if(!effect.type().equals("weakness_override")||!Set.of(EffectTarget.SELF,EffectTarget.OPPONENT_ACTIVE).contains(effect.target())
            ||!effect.effects().isEmpty()||!effect.arguments().keySet().equals(FIELDS)
            ||!Set.of("OWNER_NEXT_TURN_START","OWNER_NEXT_TURN_END").contains(effect.arguments().get("duration"))
            ||!validTypeAndMultiplier(effect.arguments().get("energy_type"),effect.arguments().get("multiplier")))
            throw new IllegalArgumentException("弱点改写须指定SELF或对方战斗位、属性/无弱点、倍率及明确期限");
    }
    private static boolean validTypeAndMultiplier(String type,String multiplier) {
        if("NONE".equals(type))return "0".equals(multiplier);
        try {dev.hua.ptcglive.engine.field.EnergyType.valueOf(type);return "2".equals(multiplier);}
        catch(RuntimeException invalid){return false;}
    }
}
