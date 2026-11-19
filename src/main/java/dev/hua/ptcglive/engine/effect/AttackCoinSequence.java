package dev.hua.ptcglive.engine.effect;

import java.util.Objects;

/**
 * A server-owned, resumable coin sequence. Nothing depending on the outcome may
 * execute before result() becomes available. This is only the sequence primitive:
 * the match must establish whole-attack scope, eligible source, physical usage,
 * controller and prompt revision before allowing a retry.
 */
public final class AttackCoinSequence {
    public enum Decision { CONTINUE, FINISH_SEQUENCE, RETRY }
    public record Prompt(long revision, int flips, int heads, boolean lastFlip, boolean sequenceFinished) { }
    private final EffectExecutionContext context;
    private final EffectDefinition source;
    private final boolean untilTails;
    private final int fixedFlips;
    private int flips, heads;
    private long revision;
    private boolean lastHead, retried, finishing, complete, running, failed;
    private Prompt prompt;

    public AttackCoinSequence(EffectDefinition source, EffectExecutionContext context) {
        this.source=Objects.requireNonNull(source);this.context=Objects.requireNonNull(context);
        var policy=CoinFlipPolicy.from(source);
        if(policy.reroll()!=CoinFlipPolicy.Reroll.NONE)
            throw new IllegalArgumentException("卡文内置重投尚不能与可选整段重投混用");
        untilTails=policy.untilTails();fixedFlips=untilTails?0:policy.resolvedFlips(context);
    }
    public Prompt prompt(){return prompt;}
    public boolean complete(){return complete;}
    public boolean retried(){return retried;}
    public EffectExecutionContext.CoinResult result() {
        if(!complete||failed)throw new IllegalStateException("硬币结果尚未确认");
        return new EffectExecutionContext.CoinResult(flips,heads);
    }
    private boolean done(){return untilTails?flips>0&&!lastHead:flips>=fixedFlips;}
    /** Repeated UI reads never call this method and never consume randomness. */
    public void advance(boolean mayRetry) {
        if(failed||running)throw new IllegalStateException("硬币续接已失败或正在执行");
        if(complete||prompt!=null)return;
        running=true;
        try {
            while(!done()) {
                var coin=context.flipCoins(source,1,(retried?"attack-retry":"attack-original")+":"+flips);
                if(coin.flips()!=1)throw new IllegalStateException("服务器硬币源返回了错误数量");
                lastHead=coin.heads()==1;flips=Math.addExact(flips,1);if(lastHead)heads=Math.addExact(heads,1);
                if(mayRetry&&!retried&&(!finishing||done())) {
                    prompt=new Prompt(++revision,flips,heads,lastHead,done());return;
                }
            }
            complete=true;
        } catch(RuntimeException failure){failed=true;throw failure;}
        finally{running=false;}
    }
    /** The match validates actor/source/usage before consuming this issued revision. */
    public void decide(long expectedRevision,Decision decision) {
        Objects.requireNonNull(decision);
        if(failed||running||complete||prompt==null||prompt.revision()!=expectedRevision)
            throw new IllegalStateException("硬币选择已完成或已更新");
        if(decision==Decision.RETRY) {
            if(retried)throw new IllegalStateException("同一硬币结果不能再次重投");
            retried=true;flips=0;heads=0;lastHead=false;finishing=false;
        } else if(decision==Decision.FINISH_SEQUENCE) {
            if(prompt.sequenceFinished())throw new IllegalArgumentException("本段硬币已投完");
            finishing=true;
        }
        prompt=null;
    }
}
