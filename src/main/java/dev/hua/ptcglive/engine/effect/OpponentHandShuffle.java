package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** The attacker selects revealed opposing hand cards, one per accepted head, and shuffles them back together. */
public final class OpponentHandShuffle {
    public static final String TYPE="opponent_hand_to_deck";
    public static final Set<String> FIELDS=Set.of("coin","amount","card_category","chooser","trainer_category","bind");
    private OpponentHandShuffle() { }
    public static boolean handles(EffectDefinition effect) {return TYPE.equals(effect.type());}
    public static void validate(EffectDefinition effect) {
        boolean energySelection="ENERGY".equals(effect.arguments().get("card_category"))
            &&"SELF".equals(effect.arguments().get("chooser"))
            &&effect.arguments().keySet().equals(Set.of("amount","card_category","chooser"));
        boolean attackSelection=effect.arguments().size()==1
            &&(effect.arguments().containsKey("amount")||effect.arguments().containsKey("coin"));
        boolean privateSelection=opponentChooses(effect)&&effect.arguments().keySet().equals(Set.of("amount","chooser"));
        if(!handles(effect)||effect.target()!=EffectTarget.OPPONENT_ACTIVE||(!attackSelection&&!energySelection&&!privateSelection&&!allItems(effect))
            ||!effect.effects().isEmpty())
            throw new IllegalArgumentException("对手自选手牌洗回须指定固定数量或此前生成的硬币名称");
        if(effect.arguments().containsKey("bind"))AttackCountBindings.name(effect.arguments().get("bind"));
        if(effect.arguments().containsKey("coin"))AttackCoinBindings.name(effect.arguments().get("coin"));
        else {int amount=Integer.parseInt(effect.arguments().get("amount"));if(amount<1||amount>60)throw new IllegalArgumentException("洗回手牌数量须为1至60");}
    }
    public static boolean allItems(EffectDefinition effect) {
        return handles(effect)&&effect.arguments().keySet().equals(Set.of("amount","card_category","trainer_category","bind"))
            &&"60".equals(effect.arguments().get("amount"))&&"TRAINER".equals(effect.arguments().get("card_category"))
            &&"ITEM".equals(effect.arguments().get("trainer_category"));
    }
    public static boolean opponentChooses(EffectDefinition effect) {
        return handles(effect)&&"OPPONENT".equals(effect.arguments().get("chooser"));
    }
    public static boolean energyOnly(EffectDefinition effect) {
        validate(effect);return "ENERGY".equals(effect.arguments().get("card_category"));
    }
    public static int count(EffectDefinition effect,EffectConditionContext context) {
        validate(effect);
        if(effect.arguments().containsKey("amount"))return Integer.parseInt(effect.arguments().get("amount"));
        var result=context.namedCoinResult(effect.arguments().get("coin"));
        if(result==null||result.heads()>60)throw new IllegalStateException("手牌洗回缺少有效的本次攻击硬币结果");
        return result.heads();
    }
}
