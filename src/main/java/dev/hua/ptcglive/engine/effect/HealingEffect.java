package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/**
 * Reusable healing parameters. MINIMUM_HP restores a floor, never damages a healthier Pokémon.
 * DAMAGE_DEALT (“Heal from this Pokémon the same amount of damage you did to your
 * opponent's Active Pokémon.”) has no printed amount: the server reads the damage
 * this attack actually placed on the opponent's Active Pokémon.
 */
public record HealingEffect(int amount, Mode mode) {
    public enum Mode { DAMAGE, MINIMUM_HP, DAMAGE_DEALT, ALL }
    public static final Set<String> FIELDS=Set.of("extra_per_discard_definition_ids","energy_type","bind","amount","mode","pokemon_stage","pokemon_types","pokemon_families","family_match","target_count","minimum_targets","discard_energy_if_healed","discard_source_if_healed");
    public HealingEffect {
        if(mode==null)throw new IllegalArgumentException("回复方式不能为空");
        if(mode==Mode.DAMAGE_DEALT||mode==Mode.ALL) {
            if(amount!=0)throw new IllegalArgumentException("ALL／DAMAGE_DEALT 的回复量由当前状态决定，不能另写 amount");
        } else if(amount<=0||amount>1000||amount%10!=0)throw new IllegalArgumentException("回复量／剩余HP须为10至1000内的10的倍数");
    }
    public static HealingEffect from(EffectDefinition effect) {
        if(!effect.type().equals("heal")||!FIELDS.containsAll(effect.arguments().keySet()))throw new IllegalArgumentException("回复组件含不支持字段");
        Mode mode;
        try { mode=Mode.valueOf(effect.arguments().getOrDefault("mode","DAMAGE")); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("未知回复方式："+effect.arguments().get("mode"),invalid); }
        family(effect);stage(effect);types(effect);
        if(effect.arguments().containsKey("extra_per_discard_definition_ids")
            &&(mode!=Mode.DAMAGE||effect.target()!=EffectTarget.OWN_ACTIVE
                ||TurnHistoryCondition.values(effect.arguments().get("extra_per_discard_definition_ids")).isEmpty()))
            throw new IllegalArgumentException("弃牌同名卡加算回复须为己方战斗宝可梦的固定回复");
        if(effect.arguments().containsKey("energy_type"))dev.hua.ptcglive.engine.field.EnergyType.valueOf(effect.arguments().get("energy_type"));
        if(effect.arguments().containsKey("bind")) {
            AttackCountBindings.name(effect.arguments().get("bind"));
        }
        if(effect.arguments().containsKey("target_count")||effect.arguments().containsKey("minimum_targets")) {
            if(effect.target()!=EffectTarget.SELECT_OWN_POKEMON||!effect.arguments().containsKey("target_count")
                ||!"1".equals(effect.arguments().get("minimum_targets"))||mode!=Mode.DAMAGE)
                throw new IllegalArgumentException("可提前结束的回复选择须指定己方、target_count、minimum_targets:1及DAMAGE模式");
            int count=Integer.parseInt(effect.arguments().get("target_count"));
            if(count<2||count>5)throw new IllegalArgumentException("回复选择上限须为2至5");
        }
        if(effect.arguments().containsKey("discard_energy_if_healed")&&(!"true".equals(effect.arguments().get("discard_energy_if_healed"))||mode!=Mode.ALL))
            throw new IllegalArgumentException("回复后弃能须明确为true并使用ALL回复模式");
        if(effect.arguments().containsKey("discard_source_if_healed")
            &&(effect.target()!=EffectTarget.SELF||mode!=Mode.DAMAGE
                ||!effect.arguments().keySet().equals(Set.of("amount","discard_source_if_healed"))
                ||!"true".equals(effect.arguments().get("discard_source_if_healed"))))
            throw new IllegalArgumentException("回复后自弃仅支持自身固定回复与实际回复后弃置来源卡");
        if(mode==Mode.DAMAGE_DEALT||mode==Mode.ALL) {
            if(effect.arguments().containsKey("amount"))throw new IllegalArgumentException("ALL／DAMAGE_DEALT 的回复量由当前状态决定，不能另写 amount");
            return new HealingEffect(0,mode);
        }
        return new HealingEffect(Integer.parseInt(effect.arguments().get("amount")),mode);
    }
    public boolean readsAttackDamage() { return mode==Mode.DAMAGE_DEALT; }
    public static PokemonTypeFilter types(EffectDefinition effect) {return PokemonTypeFilter.fromWire(effect.arguments().get("pokemon_types"));}
    public static PokemonFamilyFilter family(EffectDefinition effect) {return PokemonFamilyFilter.fromWire(effect.arguments().get("pokemon_families"),effect.arguments().get("family_match"));}
    public static PokemonSelectionScope.Stage stage(EffectDefinition effect) {
        String raw=effect.arguments().get("pokemon_stage");return raw==null?null:PokemonSelectionScope.Stage.valueOf(raw);
    }
    public static boolean boundedTargets(EffectDefinition effect) {return effect.type().equals("heal")&&"1".equals(effect.arguments().get("minimum_targets"));}
    public static boolean discardEnergyAfterHealing(EffectDefinition effect) {return "true".equals(effect.arguments().get("discard_energy_if_healed"));}
    public static boolean discardSourceAfterHealing(EffectDefinition effect) {return "true".equals(effect.arguments().get("discard_source_if_healed"));}
    public int damageToHeal(int hitPoints,int damage) {
        if(mode==Mode.DAMAGE_DEALT)throw new IllegalStateException("DAMAGE_DEALT 回复需要本次攻击造成的伤害");
        return mode==Mode.ALL?damage:mode==Mode.DAMAGE ? Math.min(amount,damage) : Math.max(0,damage-Math.max(0,hitPoints-amount));
    }
    /** @param attackDamageDealt damage this attack placed on the opponent's Active Pokémon (only read by DAMAGE_DEALT) */
    public int damageToHeal(int hitPoints,int damage,int attackDamageDealt) {
        return mode==Mode.DAMAGE_DEALT ? Math.min(Math.max(0,attackDamageDealt),damage) : damageToHeal(hitPoints,damage);
    }
}
