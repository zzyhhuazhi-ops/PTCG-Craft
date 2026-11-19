package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** Player-held protection for matching current and future Pokemon during the next opposing turn. */
public record TurnDamageReduction(int amount, PokemonTypeFilter recipients, AttackSourceFilter attackers) {
    public static final Set<String> FIELDS=Set.of("amount","pokemon_types","attacker_filter","duration");
    public TurnDamageReduction {
        if(amount<10||amount>1000||amount%10!=0)
            throw new IllegalArgumentException("玩家下回合减伤须为10至1000的10倍数");
        if(recipients==null) throw new IllegalArgumentException("玩家下回合减伤目标不能为空");
        if(attackers==null||!Set.of(AttackSourceFilter.Kind.ANY,AttackSourceFilter.Kind.V).contains(attackers.kind()))
            throw new IllegalArgumentException("玩家下回合减伤仅支持所有攻击者或宝可梦V攻击者");
    }
    public static TurnDamageReduction from(EffectDefinition effect) {
        if(!effect.type().equals("turn_damage_reduction")||effect.target()!=EffectTarget.SELF
                ||!FIELDS.containsAll(effect.arguments().keySet())
                ||!effect.arguments().keySet().containsAll(Set.of("amount","duration"))||!effect.effects().isEmpty()
                ||!"OPPONENT_NEXT_TURN".equals(effect.arguments().get("duration")))
            throw new IllegalArgumentException("玩家减伤须明确SELF、amount及OPPONENT_NEXT_TURN，可选宝可梦属性");
        int amount;
        try {amount=Integer.parseInt(effect.arguments().get("amount"));}
        catch(RuntimeException invalid){throw new IllegalArgumentException("玩家减伤amount须为整数",invalid);}
        return new TurnDamageReduction(amount,PokemonTypeFilter.fromWire(effect.arguments().get("pokemon_types")),
            AttackSourceFilter.from(effect.arguments()));
    }
}
