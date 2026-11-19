package dev.hua.ptcglive.engine.effect;

import java.util.*;

/** One-use server ledger of confirmed attack coins. It never rolls additional coins. */
public final class AttackCoinResults {
    record Entry(String instruction,EffectDefinition effect,EffectExecutionContext.CoinResult result) { }
    private final List<Entry> entries;
    private int position;
    AttackCoinResults(List<Entry> entries){this.entries=List.copyOf(entries);}
    public EffectExecutionContext.CoinResult take(EffectDefinition source) {
        if(position>=entries.size())throw new IllegalStateException("No confirmed attack coin remains for this instruction");
        var next=entries.get(position);
        if(!next.effect().equals(source))throw new IllegalStateException("Attack coin execution differs from its confirmed plan: "+next.instruction());
        position++;
        return next.result();
    }
    public void requireConsumed() {
        if(position!=entries.size())throw new IllegalStateException("Attack finished without consuming all confirmed coin results");
    }
}
