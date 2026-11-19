package dev.hua.ptcglive.engine.effect;

/** An attached physical Energy remains in play until a replacement is selected from the deck. */
public final class EnergyDeckSwap {
    public static final String SELECT="swap_attached_energy_with_deck", RESOLVE="resolve_energy_deck_swap";
    private EnergyDeckSwap() { }
    public static boolean handles(EffectDefinition effect){return effect.type().equals(SELECT)||effect.type().equals(RESOLVE);}
    public static void validate(EffectDefinition effect){
        if(!handles(effect)||effect.target()!=(effect.type().equals(SELECT)?EffectTarget.ALL_OWN_POKEMON:EffectTarget.DECK)
            ||!effect.arguments().isEmpty()||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
            throw new IllegalArgumentException("能量交换须先选己方1张附着能量，再从牌库选择1张基本能量完成交换");
    }
}
