import dev.hua.ptcglive.engine.effect.*;
import java.util.*;

/** Real parsed ON_ATTACK programs; planner must not mutate the actual context. */
public final class AttackDamageCoinPlanTest {
    static void check(boolean b,String s){AttackCoinSequenceTest.check(b,s);}
    static final String FIRST="{\"type\":\"coin_flip\",\"flips\":1,\"bind\":\"first\",\"effects\":[]}";
    static class Context extends AttackCoinContinuationTest.Context {
        int committed,damage;AttackCoinResults prepared;
        Context(boolean... coins){super(coins);}
        public void executeAtomic(EffectDefinition e){committed++;if(e.type().equals("damage"))damage+=Integer.parseInt(e.arguments().get("amount"));}
        public CoinResult preparedAttackCoin(EffectDefinition e){return prepared==null?null:prepared.take(e);}
    }
    static AttackCoinAttempt attempt(String json,Context c){return new AttackCoinAttempt(new AttackDamageCoinPlan(BeforeDamageAttackTest.attack("",json,""),c));}
    static void decide(AttackCoinAttempt a,AttackCoinAttempt.Decision d){a.decide(a.prompt().revision(),d);a.advance(true);}
    public static void main(String[] args){conditional();nestedRepeat();multipleBindings();validation();confirmedExecution();ledgerAtomicity();System.out.println("AttackDamageCoinPlanTest passed: parsed conditional/named/nested/repeated planning, final one-use execution without new RNG or duplicate effects, and strict ledger mismatch rejection; this test covers the pure damage phase.");}
    static void conditional() {
        String tail="{\"type\":\"conditional\",\"conditions\":[{\"type\":\"COIN_RESULT\",\"coin\":\"first\",\"value\":\"HEADS\"}],\"effects\":[{\"type\":\"coin_flip\",\"flips\":2,\"effects\":[{\"type\":\"repeat_per_heads\",\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":10}]}]}]}";
        var c=new Context(true,true,true,false);var a=attempt(FIRST+","+tail,c);a.advance(true);decide(a,AttackCoinAttempt.Decision.FINISH_ATTACK);
        check(a.prompt().attemptFinished()&&c.operations.size()==3&&c.committed==0&&c.bindings.isEmpty(),"Planning executed provisional damage or published named result");
        decide(a,AttackCoinAttempt.Decision.RETRY);check(a.results().size()==1&&a.results().get("damage-coin-0").heads()==0&&c.operations.size()==4,"Conditional retry retained old later sequence");
        check(c.committed==0&&c.bindings.isEmpty(),"Completed planning itself mutated match context");
    }
    static void nestedRepeat() {
        String nested="{\"type\":\"coin_flip\",\"flips\":2,\"effects\":[{\"type\":\"repeat_per_heads\",\"effects\":[{\"type\":\"coin_flip\",\"flips\":1,\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":10}]}]}]}";
        var c=new Context(true,true,true,true,false,false);var a=attempt(nested,c);a.advance(true);decide(a,AttackCoinAttempt.Decision.FINISH_ATTACK);
        check(a.prompt().completedSequences()==3&&c.operations.size()==4,"Per-head nested coin occurrences collapsed");
        decide(a,AttackCoinAttempt.Decision.RETRY);check(a.results().size()==1&&c.operations.size()==6&&c.committed==0,"Retry kept original per-head repetition count");
    }
    static void multipleBindings() {
        var c=new Context(false,true,true,false);var a=attempt(FIRST+","+FIRST.replace("first","second"),c);a.advance(true);decide(a,AttackCoinAttempt.Decision.FINISH_ATTACK);decide(a,AttackCoinAttempt.Decision.RETRY);
        check(a.results().get("damage-coin-0").heads()==1&&a.results().get("damage-coin-1").heads()==0&&c.bindings.isEmpty(),"Named coin sequence results cross-contaminated");
    }
    static void validation() {
        var c=new Context();AttackCoinSequenceTest.reject(()->attempt("{\"type\":\"draw\",\"amount\":1},"+FIRST,c));
        check(c.operations.isEmpty()&&c.committed==0,"Invalid damage-phase action was skipped instead of rejected");
    }
    static void confirmedExecution() {
        String damage="{\"type\":\"coin_flip\",\"flips\":2,\"effects\":[{\"type\":\"repeat_per_heads\",\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}]}]},"
            +"{\"type\":\"conditional\",\"conditions\":[{\"type\":\"COIN_RESULT\",\"coin\":\"first\",\"value\":\"HEADS\"}],\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":20}]}";
        var attack=BeforeDamageAttackTest.attack("",FIRST+","+damage,"");var c=new Context(false,true,true,true,false,true);
        var a=new AttackCoinAttempt(new AttackDamageCoinPlan(attack,c));a.advance(true);AttackCoinSequenceTest.reject(a::claimResults);
        decide(a,AttackCoinAttempt.Decision.FINISH_ATTACK);AttackCoinSequenceTest.reject(a::claimResults);decide(a,AttackCoinAttempt.Decision.RETRY);
        check(c.damage==0&&c.committed==0&&c.bindings.isEmpty(),"Provisional attack effects were executed before confirmation");
        c.prepared=a.claimResults();AttackCoinSequenceTest.reject(a::claimResults);var rng=List.copyOf(c.operations);
        new EffectInterpreter(c).execute(attack.effectProgram(),EffectTrigger.ON_ATTACK);c.prepared.requireConsumed();
        check(c.damage==50&&c.committed==2&&c.bindings.get("first").heads()==1,"Final execution used old results or repeated an effect");
        check(c.operations.equals(rng)&&c.used==0,"Confirmed execution rolled another coin or tried to reserve another retry");
        AttackCoinSequenceTest.reject(()->new EffectInterpreter(c).execute(attack.effectProgram(),EffectTrigger.ON_ATTACK));
        check(c.damage==50&&c.operations.equals(rng),"Duplicate execution mutated confirmed effects");
    }
    static void ledgerAtomicity() {
        var attack=BeforeDamageAttackTest.attack("",FIRST+","+FIRST.replace("first","second"),"");var c=new Context(false,true);
        var a=new AttackCoinAttempt(new AttackDamageCoinPlan(attack,c));a.advance(false);var ledger=a.claimResults();
        var effects=attack.effectProgram().forTrigger(EffectTrigger.ON_ATTACK).getFirst().effects();
        AttackCoinSequenceTest.reject(ledger::requireConsumed);
        AttackCoinSequenceTest.reject(()->ledger.take(effects.getLast()));
        check(ledger.take(effects.getFirst()).heads()==0,"Mismatched instruction consumed the first result");
        check(ledger.take(effects.getLast()).heads()==1,"Second confirmed result changed");ledger.requireConsumed();
        AttackCoinSequenceTest.reject(()->ledger.take(effects.getFirst()));
    }
}
