package dev.hua.ptcglive.engine.effect;
import java.util.Set;

/** Only the deck top is inspected; the exchange destination remains an unknown Prize slot. */
public final class TopPrizeSwap {
    public static final String INSPECT="inspect_top_prize_swap", RESOLVE="resolve_top_prize_swap";
    private TopPrizeSwap() { }
    public static void validate(EffectDefinition effect) {
        boolean inspect=effect.type().equals(INSPECT);
        if(!inspect&&!effect.type().equals(RESOLVE)||effect.target()!=(inspect?EffectTarget.DECK:EffectTarget.SELF)
            ||!effect.arguments().keySet().equals(inspect?Set.of():Set.of("position"))
            ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
            throw new IllegalArgumentException("阿尔宙斯手机须先私下查看顶牌，再选择暗面奖赏位置交换");
        if(!inspect) {int position=Integer.parseInt(effect.arguments().get("position"));
            if(position<0||position>=60)throw new IllegalArgumentException("奖赏位置无效");}
    }
}
