package dev.hua.ptcglive.engine.effect;

import java.util.*;

/**
 * All coin sequences belonging to one attack attempt, including conditional
 * later sequences. The planner may inspect provisional results to decide the
 * next coin instruction, but MUST NOT mutate game state or publish bindings.
 * The match commits effects only after results() is available. Restarting an
 * attempt drops every provisional result, then evaluates the planner afresh.
 *
 * This is the transaction primitive; compiling arbitrary attack effects into
 * a mutation-free planner and installing it in ServerMatch is separate work.
 */
public final class AttackCoinAttempt {
    public record OutcomePrompt(long revision,String instruction) { }
    private OutcomePrompt outcomePrompt;
    public OutcomePrompt outcomePrompt(){return outcomePrompt;}
    public enum Decision { CONTINUE, FINISH_ATTACK, RETRY }
    public record Instruction(String id,EffectDefinition effect,EffectExecutionContext context) {
        public Instruction {
            if(id==null||id.isBlank())throw new IllegalArgumentException("Coin instruction needs an identity");
            Objects.requireNonNull(effect);Objects.requireNonNull(context);
        }
    }
    @FunctionalInterface public interface Planner {
        /** Null means this attempt has no more coins; input is immutable and server-only. */
        Instruction next(Map<String,EffectExecutionContext.CoinResult> provisional);
    }
    public record Prompt(long revision,String instruction,int flips,int heads,boolean sequenceFinished,
                         boolean attemptFinished,int completedSequences) { }
    private final Planner planner;
    private final LinkedHashMap<String,EffectExecutionContext.CoinResult> provisional=new LinkedHashMap<>();
    private final List<AttackCoinResults.Entry> completedInstructions=new ArrayList<>();
    private Instruction current;
    private boolean untilTails,lastHead,started,retried,finishing,complete,failed,running,claimed;
    private int limit,flips,heads;
    private long revision;
    private Prompt prompt;

    public AttackCoinAttempt(Planner planner){this.planner=Objects.requireNonNull(planner);}
    public Prompt prompt(){return prompt;}
    public boolean complete(){return complete;}
    public boolean retried(){return retried;}
    public Map<String,EffectExecutionContext.CoinResult> results() {
        if(!complete||failed)throw new IllegalStateException("Attack coins are not confirmed");
        return Collections.unmodifiableMap(new LinkedHashMap<>(provisional));
    }
    /** Issued once, only after every attack coin and the final player decision. */
    public AttackCoinResults claimResults() {
        if(!complete||failed||claimed)throw new IllegalStateException("Confirmed attack coin results are unavailable or already claimed");
        claimed=true;
        return new AttackCoinResults(completedInstructions);
    }
    private void finishInstruction() {
        var result=new EffectExecutionContext.CoinResult(flips,heads);
        provisional.put(current.id(),result);
        completedInstructions.add(new AttackCoinResults.Entry(current.id(),current.effect(),result));
    }
    private boolean sequenceDone(){return untilTails?flips>0&&!lastHead:flips>=limit;}
    private void nextInstruction() {
        while(true) {
            current=planner.next(Collections.unmodifiableMap(new LinkedHashMap<>(provisional)));
            if(current==null)return;
            if(provisional.containsKey(current.id()))throw new IllegalArgumentException("Duplicate attack coin instruction: "+current.id());
            var policy=CoinFlipPolicy.from(current.effect());
            if(policy.reroll()!=CoinFlipPolicy.Reroll.NONE)throw new IllegalArgumentException("A printed retry needs an explicit attack-wide policy");
            untilTails=policy.untilTails();limit=untilTails?0:policy.resolvedFlips(current.context());flips=0;heads=0;lastHead=false;
            if(!sequenceDone())return;
            finishInstruction();
        }
    }
    /** Refreshing the issued prompt is read-only. Only controller advancement draws random values. */
    public void advance(boolean mayRetry) {
        if(failed||running)throw new IllegalStateException("Attack coin attempt is failed or running");
        if(complete||prompt!=null||outcomePrompt!=null)return;
        running=true;
        try {
            if(!started){started=true;nextInstruction();}
            while(current!=null) {
                String id=current.id();
                if(current.context().canChooseNextCoinOutcome(current.effect())) {
                    outcomePrompt=new OutcomePrompt(++revision,id);return;
                }
                var coin=current.context().flipCoins(current.effect(),1,
                    (retried?"attack-retry":"attack-original")+":"+id+":"+flips);
                if(coin.flips()!=1)throw new IllegalStateException("Server coin source returned the wrong quantity");
                lastHead=coin.heads()==1;flips=Math.addExact(flips,1);if(lastHead)heads=Math.addExact(heads,1);
                int shownFlips=flips,shownHeads=heads;boolean sequenceFinished=sequenceDone();
                if(sequenceFinished) {
                    finishInstruction();
                    nextInstruction();
                }
                boolean attemptFinished=current==null;
                if(mayRetry&&!retried&&(!finishing||attemptFinished)) {
                    prompt=new Prompt(++revision,id,shownFlips,shownHeads,sequenceFinished,attemptFinished,provisional.size());return;
                }
            }
            complete=true;
        } catch(RuntimeException error){failed=true;throw error;}
        finally{running=false;}
    }
    /** Match authority reserves the physical retry source before accepting RETRY here. */
    public void decideOutcome(long expectedRevision,boolean heads) {
        if(failed||running||complete||outcomePrompt==null||outcomePrompt.revision()!=expectedRevision||current==null)
            throw new IllegalStateException("Attack coin outcome decision is stale or already complete");
        if(!current.context().canChooseNextCoinOutcome(current.effect()))
            throw new IllegalStateException("Coin outcome source is no longer available");
        current.context().chooseNextCoinOutcome(current.effect(),heads);outcomePrompt=null;
    }
    /** Match authority reserves the physical retry source before accepting RETRY here. */
    public void decide(long expectedRevision,Decision decision) {
        Objects.requireNonNull(decision);
        if(failed||running||complete||prompt==null||prompt.revision()!=expectedRevision)
            throw new IllegalStateException("Attack coin decision is stale or already complete");
        if(decision==Decision.FINISH_ATTACK&&prompt.attemptFinished())
            throw new IllegalArgumentException("All attack coins are already rolled");
        if(decision==Decision.RETRY) {
            if(retried)throw new IllegalStateException("An attack cannot retry its retry");
            retried=true;provisional.clear();completedInstructions.clear();current=null;started=false;finishing=false;flips=0;heads=0;lastHead=false;
        } else if(decision==Decision.FINISH_ATTACK)finishing=true;
        prompt=null;
    }
}
