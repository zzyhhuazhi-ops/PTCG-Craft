package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** Each addressed player chooses their own private hand cards. SELF also supports Hand Trimmer's second step. */
public final class OpponentHandDiscard {
    private OpponentHandDiscard() { }
    public static final Set<String> FIELDS=Set.of("amount","until_hand_size","any_number");
    public static boolean variable(EffectDefinition effect) {
        return effect.type().equals("opponent_choose_discard")&&effect.target()==EffectTarget.SELF
            &&effect.arguments().equals(java.util.Map.of("until_hand_size","4","any_number","true"));
    }
    public static int amount(EffectDefinition effect) {
        if(variable(effect)&&effect.effects().isEmpty())return 4;
        boolean own=effect.target()==EffectTarget.SELF&&(effect.arguments().equals(java.util.Map.of("until_hand_size","5"))
            ||effect.arguments().equals(java.util.Map.of("amount","2")));
        if(!effect.type().equals("opponent_choose_discard")||effect.target()!=EffectTarget.OPPONENT_ACTIVE&&!own
            ||!effect.effects().isEmpty()||effect.arguments().size()!=1
            ||!FIELDS.containsAll(effect.arguments().keySet()))
            throw new IllegalArgumentException("对手自选弃手牌须指定amount或until_hand_size");
        int count=Integer.parseInt(effect.arguments().values().iterator().next());
        if(count<1||count>60)throw new IllegalArgumentException("对手自选弃手牌数量须为1至60");
        return count;
    }
    public static int selectionCount(EffectDefinition effect,int handSize) {
        int value=amount(effect);
        return effect.arguments().containsKey("until_hand_size")?Math.max(0,handSize-value):Math.min(handSize,value);
    }
    public static int selectionMaximum(EffectDefinition effect,int handSize) {
        return variable(effect)?handSize:selectionCount(effect,handSize);
    }
}
