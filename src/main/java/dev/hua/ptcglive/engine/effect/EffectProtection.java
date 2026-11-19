package dev.hua.ptcglive.engine.effect;

import java.util.Objects;
import java.util.UUID;

/** Damage and other effects are distinct impacts even when they have the same card origin. */
public record EffectProtection(Source source, Relation relation, Scope scope, AttackSourceFilter attackerFilter) {
    public EffectProtection(Source source, Relation relation, Scope scope){this(source,relation,scope,AttackSourceFilter.ANY);}
    public enum Source { ATTACK, ABILITY, TRAINER, ENERGY, ANY_CARD }
    public enum Relation { OPPONENT, OWN, ANY }
    public enum Scope { DAMAGE_ONLY, EFFECTS_ONLY, DAMAGE_AND_EFFECTS }
    public enum Impact { DAMAGE, OTHER_EFFECTS }
    public EffectProtection { Objects.requireNonNull(source); Objects.requireNonNull(relation); Objects.requireNonNull(scope); Objects.requireNonNull(attackerFilter); }
    public boolean blocks(EffectOrigin origin, UUID protectedOwner, Impact impact) {
        return blocks(origin,protectedOwner,impact,filter->filter.equals(AttackSourceFilter.ANY));
    }
    public boolean blocks(EffectOrigin origin, UUID protectedOwner, Impact impact, java.util.function.Predicate<AttackSourceFilter> matchesAttacker) {
        Objects.requireNonNull(origin); Objects.requireNonNull(protectedOwner); Objects.requireNonNull(impact);
        if (origin.kind() == EffectOrigin.Kind.RULE) return false;
        if (source != Source.ANY_CARD && !source.name().equals(origin.kind().name())) return false;
        boolean own = origin.controller().equals(protectedOwner);
        if (relation == Relation.OPPONENT && own || relation == Relation.OWN && !own) return false;
        if(!attackerFilter.equals(AttackSourceFilter.ANY)&&!matchesAttacker.test(attackerFilter))return false;
        return scope == Scope.DAMAGE_AND_EFFECTS || scope == (impact == Impact.DAMAGE ? Scope.DAMAGE_ONLY : Scope.EFFECTS_ONLY);
    }
    public String labelZh() {
        return "持续防护：" + switch (relation) {case OPPONENT -> "对手";case OWN -> "己方";case ANY -> "双方";}
            + switch (source) {case ATTACK -> "攻击";case ABILITY -> "特性";case TRAINER -> "训练家";case ENERGY -> "能量";case ANY_CARD -> "卡牌";}
            + switch (scope) {case DAMAGE_ONLY -> "造成的伤害";case EFFECTS_ONLY -> "施加的效果（不含伤害）";case DAMAGE_AND_EFFECTS -> "造成的伤害与效果";};
    }
}
