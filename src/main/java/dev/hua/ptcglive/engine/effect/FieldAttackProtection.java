package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** Source-bound, opponent-turn attack-damage protection for a family across the owner's whole field. */
public record FieldAttackProtection(PokemonFamilyFilter recipients, AttackSourceFilter attackers, boolean sourceActiveBound) {
    public FieldAttackProtection(PokemonFamilyFilter recipients,AttackSourceFilter attackers){this(recipients,attackers,true);}
    public static final Set<String> FIELDS=Set.of("pokemon_families","attacker_filter","scope","duration","source_active_bound");
    public static FieldAttackProtection from(EffectDefinition effect) {
        if(!effect.type().equals("field_attack_protection")||effect.target()!=EffectTarget.SELF
            ||!effect.conditions().isEmpty()||!effect.effects().isEmpty()||!effect.arguments().keySet().equals(FIELDS)
            ||!"DAMAGE_ONLY".equals(effect.arguments().get("scope"))
            ||!"OPPONENT_NEXT_TURN".equals(effect.arguments().get("duration"))
            ||!Set.of("true","false").contains(effect.arguments().get("source_active_bound")))
            throw new IllegalArgumentException("全场攻击防护须为来源战斗位绑定的对手下回合伤害防护");
        var recipients=PokemonFamilyFilter.fromWire(effect.arguments().get("pokemon_families"),null);
        var attackers=AttackSourceFilter.from(effect.arguments());
        boolean bound=Boolean.parseBoolean(effect.arguments().get("source_active_bound"));
        if(bound?(recipients.equals(PokemonFamilyFilter.ANY)||attackers.kind()!=AttackSourceFilter.Kind.EX)
            :(!"ULTRA_BEAST".equals(effect.arguments().get("pokemon_families"))||!attackers.equals(AttackSourceFilter.ANY)))
            throw new IllegalArgumentException("全场防护须为战斗位来源对ex防护，或玩家的究极异兽防护");
        return new FieldAttackProtection(recipients,attackers,bound);
    }
}
