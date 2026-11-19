package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** Player-scoped this-turn damage modifier. It also applies to matching Pokemon that enter play later. */
public record TurnAttackBonus(int amount,PokemonFamilyFilter recipients,boolean perOpponentPrizeTaken,PokemonTypeFilter recipientTypes,AttackDefenderFilter defenders,boolean includeOwnActive) {
    public TurnAttackBonus(int amount,PokemonFamilyFilter recipients,boolean perOpponentPrizeTaken){this(amount,recipients,perOpponentPrizeTaken,PokemonTypeFilter.ANY,AttackDefenderFilter.ANY,false);}
    public static final Set<String> FIELDS=Set.of("amount","pokemon_families","family_match","per_opponent_prize_taken","pokemon_types","defender_pokemon_types","defender_pokemon_families","defender_family_match","source_only","include_own_active");
    public static boolean sourceOnly(EffectDefinition effect) {return "true".equals(effect.arguments().get("source_only"));}
    public static TurnAttackBonus from(EffectDefinition effect) {
        if(!effect.type().equals("turn_attack_bonus")||effect.target()!=EffectTarget.SELF||!FIELDS.containsAll(effect.arguments().keySet())||!effect.effects().isEmpty())
            throw new IllegalArgumentException("本回合玩家加伤必须指定SELF及明确参数");
        int amount=Integer.parseInt(effect.arguments().get("amount"));
        if(effect.arguments().containsKey("source_only")&&(!sourceOnly(effect)||!effect.arguments().keySet().equals(Set.of("amount","source_only"))))
            throw new IllegalArgumentException("来源限定的本回合加伤只接受固定加伤与source_only=true");
        if(amount<10||amount>1000||amount%10!=0)throw new IllegalArgumentException("玩家本回合加伤须为10至1000的10倍数");
        if(effect.arguments().containsKey("per_opponent_prize_taken")&&!"true".equals(effect.arguments().get("per_opponent_prize_taken")))
            throw new IllegalArgumentException("按对手已取奖赏加伤必须明确true");
        if(effect.arguments().containsKey("pokemon_types")&&effect.arguments().get("pokemon_types").isBlank())throw new IllegalArgumentException("攻击者属性不可为空");
        boolean own=effect.arguments().containsKey("include_own_active");
        if(own&&(!"true".equals(effect.arguments().get("include_own_active"))||amount!=10
            ||!Set.of("amount","per_opponent_prize_taken","include_own_active").containsAll(effect.arguments().keySet())))
            throw new IllegalArgumentException("双方战斗位加伤仅核验力量增强与艾莉丝的10点及已取奖赏倍率");
        return new TurnAttackBonus(amount,PokemonFamilyFilter.fromWire(effect.arguments().get("pokemon_families"),effect.arguments().get("family_match")),effect.arguments().containsKey("per_opponent_prize_taken"),PokemonTypeFilter.fromWire(effect.arguments().get("pokemon_types")),AttackDefenderFilter.from(effect.arguments()),own);
    }
}
