package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.field.ContinuousEffect;
import java.util.Set;

/** Versioned, explicit protection contract; absent/unknown duration or source never silently defaults. */
public record ProtectionEffect(EffectProtection policy, ContinuousEffect.Expiry expiry,boolean boundToTopCard) {
    public ProtectionEffect(EffectProtection policy,ContinuousEffect.Expiry expiry){this(policy,expiry,false);}
    private static final Set<String> REQUIRED = Set.of("source", "from", "scope", "duration");
    public static final Set<String> FIELDS;
    static {
        var fields=new java.util.HashSet<>(AttackSourceFilter.FIELDS);
        fields.addAll(REQUIRED);fields.add("bound_to_top_card");FIELDS=Set.copyOf(fields);
    }
    public static boolean handles(EffectDefinition definition) {
        return definition.type().equals("prevent_damage") || definition.type().equals("prevent_effects");
    }
    public static ProtectionEffect from(EffectDefinition definition) {
        if (!handles(definition) || !definition.arguments().keySet().containsAll(REQUIRED) || !FIELDS.containsAll(definition.arguments().keySet()))
            throw new IllegalArgumentException("防护须明确source、from、scope和duration，不能省略或加入未知参数");
        var a=definition.arguments();
        if(a.containsKey("bound_to_top_card")&&(!"true".equals(a.get("bound_to_top_card"))||definition.target()!=EffectTarget.SELF
            ||!"ATTACK".equals(a.get("source"))||!"DAMAGE_ONLY".equals(a.get("scope"))))
            throw new IllegalArgumentException("原卡绑定须明确true，并为SELF的招式伤害防护");
        var filter=AttackSourceFilter.from(a);
        if(a.keySet().stream().anyMatch(AttackSourceFilter.FIELDS::contains)&&!"ATTACK".equals(a.get("source")))
            throw new IllegalArgumentException("攻击者筛选只适用于ATTACK来源防护");
        var scope=EffectProtection.Scope.valueOf(a.get("scope"));
        if (definition.type().equals("prevent_damage") && scope != EffectProtection.Scope.DAMAGE_ONLY)
            throw new IllegalArgumentException("prevent_damage只能声明DAMAGE_ONLY；其它范围使用prevent_effects");
        var expiry=switch (a.get("duration")) {
            case "SOURCE_NEXT_TURN_START" -> ContinuousEffect.Expiry.OWNER_NEXT_TURN_START;
            case "OPPONENT_TURN_END" -> ContinuousEffect.Expiry.OPPONENT_TURN_END;
            case "SOURCE_TURN_END" -> ContinuousEffect.Expiry.OWNER_TURN_END;
            case "LEAVES_PLAY" -> ContinuousEffect.Expiry.LEAVES_PLAY;
            default -> throw new IllegalArgumentException("未知防护到期条件");
        };
        return new ProtectionEffect(new EffectProtection(EffectProtection.Source.valueOf(a.get("source")),
            EffectProtection.Relation.valueOf(a.get("from")),scope,filter),expiry,a.containsKey("bound_to_top_card"));
    }
}
