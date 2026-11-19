package dev.hua.ptcglive.engine.effect;

/** Server-captured in-play identities, not definition IDs, mutable field slots or client-selected targets. */
public record EffectEventBindings(EffectTargetResolution.PokemonTarget triggerPokemon, long triggerEntrySequence,
                                  EffectTargetResolution.PokemonTarget attackingPokemon, long attackerEntrySequence,
                                  EffectTargetResolution.PokemonTarget sourcePokemon, long sourceEntrySequence, int attackDamage) {
    public static final EffectEventBindings NONE = new EffectEventBindings(null,0,null,0);
    public EffectEventBindings(EffectTargetResolution.PokemonTarget triggerPokemon,long triggerEntrySequence,
                               EffectTargetResolution.PokemonTarget attackingPokemon,long attackerEntrySequence) {
        this(triggerPokemon,triggerEntrySequence,attackingPokemon,attackerEntrySequence,null,0);
    }
    public EffectEventBindings(EffectTargetResolution.PokemonTarget trigger,long triggerEntry,EffectTargetResolution.PokemonTarget attacker,long attackerEntry,EffectTargetResolution.PokemonTarget source,long sourceEntry) { this(trigger,triggerEntry,attacker,attackerEntry,source,sourceEntry,0); }
    public EffectEventBindings withAttackDamage(int amount) { return new EffectEventBindings(triggerPokemon,triggerEntrySequence,attackingPokemon,attackerEntrySequence,sourcePokemon,sourceEntrySequence,amount); }
    public EffectEventBindings {
        if(attackDamage<0||attackDamage%10!=0)throw new IllegalArgumentException("事件伤害必须为非负10倍数");
        if(triggerPokemon==null ? triggerEntrySequence!=0 : triggerEntrySequence<=0)
            throw new IllegalArgumentException("触发对象必须绑定其实际入场序号");
        if(attackingPokemon==null ? attackerEntrySequence!=0 : attackerEntrySequence<=0)
            throw new IllegalArgumentException("攻击者必须绑定其实际入场序号");
        if(sourcePokemon==null ? sourceEntrySequence!=0 : sourceEntrySequence<=0)
            throw new IllegalArgumentException("特性来源必须绑定其实际入场序号");
    }
    public EffectEventBindings withSource(EffectTargetResolution.PokemonTarget source,long entrySequence) {
        java.util.Objects.requireNonNull(source,"特性来源");
        if(sourcePokemon!=null && (!sourcePokemon.equals(source)||sourceEntrySequence!=entrySequence))
            throw new IllegalStateException("已捕获的特性来源不能重新绑定");
        return new EffectEventBindings(triggerPokemon,triggerEntrySequence,attackingPokemon,attackerEntrySequence,source,entrySequence,attackDamage);
    }
    public long entrySequence(EffectTarget target) {
        required(target);
        return switch(target) {
            case SELF -> sourceEntrySequence;
            case TRIGGER_POKEMON -> triggerEntrySequence;
            case ATTACKING_POKEMON -> attackerEntrySequence;
            default -> throw new IllegalArgumentException("不是已捕获的宝可梦目标："+target);
        };
    }
    public EffectTargetResolution.PokemonTarget required(EffectTarget target) {
        var value=switch(target) {
            case SELF -> sourcePokemon;
            case TRIGGER_POKEMON -> triggerPokemon;
            case ATTACKING_POKEMON -> attackingPokemon;
            default -> throw new IllegalArgumentException("不是事件绑定目标："+target);
        };
        if(value==null)throw new IllegalStateException("当前服务器事件没有绑定该目标："+target);
        return value;
    }
}
