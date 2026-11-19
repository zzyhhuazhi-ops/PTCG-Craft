package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** Player-scoped this-turn Prize bonus for a matching attack-damage Knock Out. */
public record TurnExtraPrizeOnAttackKo(int amount,String attackerNameContains,PokemonFamilyFilter attackerFamilies,
                                       boolean attackerBasicOnly,PokemonFamilyFilter defenderFamilies,boolean opponentActiveOnly) {
    public static final Set<String> NAME_FIELDS=Set.of("amount","attacker_name_contains","opponent_active_only");
    public static final Set<String> FAMILY_FIELDS=Set.of("amount","attacker_families","opponent_active_only");
    public static final Set<String> TOOL_FIELDS=Set.of("amount","attacker_families","attacker_basic_only","defender_families","opponent_active_only");
    public static final Set<String> FIELDS=Set.of("amount","attacker_name_contains","attacker_families","attacker_basic_only","defender_families","opponent_active_only");
    public static TurnExtraPrizeOnAttackKo from(EffectDefinition effect) {
        if(!effect.type().equals("turn_extra_prize_on_attack_ko")||effect.target()!=EffectTarget.SELF
            ||!NAME_FIELDS.equals(effect.arguments().keySet())&&!FAMILY_FIELDS.equals(effect.arguments().keySet())
                &&!TOOL_FIELDS.equals(effect.arguments().keySet())||!effect.effects().isEmpty())
            throw new IllegalArgumentException("本回合额外奖赏须指定SELF及完整攻击击倒条件");
        int amount=Integer.parseInt(effect.arguments().get("amount"));
        String name=effect.arguments().get("attacker_name_contains");
        var families=PokemonFamilyFilter.fromWire(effect.arguments().get("attacker_families"),null);
        boolean basic="true".equals(effect.arguments().get("attacker_basic_only"));
        var defenders=PokemonFamilyFilter.fromWire(effect.arguments().get("defender_families"),null);
        if(amount<1||amount>3||name!=null&&(name.isBlank()||name.length()>120)
            ||name==null&&families.equals(PokemonFamilyFilter.ANY)
            ||TOOL_FIELDS.equals(effect.arguments().keySet())&&(!basic||defenders.equals(PokemonFamilyFilter.ANY))
            ||!"true".equals(effect.arguments().get("opponent_active_only")))
            throw new IllegalArgumentException("本回合额外奖赏参数无效");
        return new TurnExtraPrizeOnAttackKo(amount,name,families,basic,defenders,true);
    }
}
