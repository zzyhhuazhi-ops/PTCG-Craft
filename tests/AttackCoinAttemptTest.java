import dev.hua.ptcglive.engine.effect.*;
import java.util.*;

/** Whole-attempt primitive; no production card is enabled by this test. */
public final class AttackCoinAttemptTest {
    static void check(boolean b,String s){AttackCoinSequenceTest.check(b,s);}
    static void reject(Runnable r){AttackCoinSequenceTest.reject(r);}
    static EffectDefinition coin(Map<String,String> args){return new EffectDefinition("coin_flip",EffectTarget.SELF,args,List.of(),List.of());}
    static EffectDefinition fixed(int n){return coin(Map.of("flips",Integer.toString(n)));}
    static AttackCoinAttempt.Instruction instruction(String id,EffectDefinition e,EffectExecutionContext c){return new AttackCoinAttempt.Instruction(id,e,c);}
    static void decide(AttackCoinAttempt a,AttackCoinAttempt.Decision d){a.decide(a.prompt().revision(),d);a.advance(true);}
    public static void main(String[] args){lateRetry();earlyRetry();conditionalRestart();dynamicCountRestart();untilTails();invalidAndZero();automatic();System.out.println("AttackCoinAttemptTest passed: all-sequence invalidation, early/final retry, conditional branch reevaluation, dynamic counts, until tails, provisional result isolation and immutable final result; this test covers the transaction primitive.");}
    static AttackCoinAttempt two(AttackCoinSequenceTest.Context c) {
        return new AttackCoinAttempt(results->!results.containsKey("first")?instruction("first",fixed(2),c):!results.containsKey("second")?instruction("second",fixed(1),c):null);
    }
    static void lateRetry() {
        var c=new AttackCoinSequenceTest.Context(true,true,true,false,false,false);var a=two(c);a.advance(true);reject(a::results);
        decide(a,AttackCoinAttempt.Decision.FINISH_ATTACK);var p=a.prompt();
        check(p.attemptFinished()&&p.completedSequences()==2&&c.operations.size()==3,"Finish did not cover every original sequence");reject(a::results);
        decide(a,AttackCoinAttempt.Decision.RETRY);var result=a.results();
        check(result.get("first").heads()==0&&result.get("second").heads()==0&&c.operations.size()==6,"Late retry retained first group or retried last group only");
        check(c.operations.get(3).equals("attack-retry:first:0")&&a.retried(),"Retry did not restart original first toss");
        reject(()->a.decide(p.revision(),AttackCoinAttempt.Decision.RETRY));
        try{result.clear();throw new AssertionError("Mutable committed results");}catch(UnsupportedOperationException expected){}
    }
    static void earlyRetry() {
        var c=new AttackCoinSequenceTest.Context(true,false,true,false);var a=two(c);a.advance(true);decide(a,AttackCoinAttempt.Decision.RETRY);
        check(a.complete()&&c.operations.size()==4&&a.results().get("first").heads()==1&&a.results().get("second").heads()==0,"Early retry finished old group or retained old toss");
    }
    static void conditionalRestart() {
        var c=new AttackCoinSequenceTest.Context(true,true,false,false);var seen=new ArrayList<Set<String>>();
        var a=new AttackCoinAttempt(results->{seen.add(Set.copyOf(results.keySet()));
            if(!results.containsKey("gate"))return instruction("gate",fixed(1),c);
            if(results.get("gate").heads()>0&&!results.containsKey("heads-only"))return instruction("heads-only",fixed(2),c);
            return null;
        });
        a.advance(true);decide(a,AttackCoinAttempt.Decision.FINISH_ATTACK);check(a.prompt().attemptFinished()&&c.operations.size()==3,"Conditional first attempt did not finish");
        decide(a,AttackCoinAttempt.Decision.RETRY);
        check(a.results().keySet().equals(Set.of("gate"))&&a.results().get("gate").heads()==0&&c.operations.size()==4,"Conditional retry retained old branch, old result or old flip count");
        check(seen.stream().filter(Set::isEmpty).count()==2,"Planner was not restarted from empty coin memory");
    }
    static void dynamicCountRestart() {
        var c=new AttackCoinSequenceTest.Context(true,true,true,true,false,true,false);
        var a=new AttackCoinAttempt(results->{
            if(!results.containsKey("size"))return instruction("size",fixed(2),c);
            if(!results.containsKey("scaled"))return instruction("scaled",fixed(Math.max(1,results.get("size").heads())),c);
            return null;
        });
        a.advance(true);decide(a,AttackCoinAttempt.Decision.FINISH_ATTACK);check(a.prompt().flips()==2,"Initial dynamic branch count wrong");
        decide(a,AttackCoinAttempt.Decision.RETRY);
        check(a.results().get("size").heads()==1&&a.results().get("scaled").flips()==1,"Restart reused previous attempt's dynamic count");
    }
    static void untilTails() {
        var c=new AttackCoinSequenceTest.Context(true,true,false,true,false,true,false);
        var a=new AttackCoinAttempt(results->!results.containsKey("until")?instruction("until",coin(Map.of("until","TAILS")),c):!results.containsKey("last")?instruction("last",fixed(1),c):null);
        a.advance(true);decide(a,AttackCoinAttempt.Decision.FINISH_ATTACK);check(a.prompt().attemptFinished()&&c.operations.size()==4,"Until tails attempt did not include later sequence");
        decide(a,AttackCoinAttempt.Decision.RETRY);check(a.results().get("until").flips()==1&&a.results().get("last").heads()==1&&c.operations.size()==6,"Until-tails retry used old stopping point or lost later sequence");
    }
    static void invalidAndZero() {
        var c=new AttackCoinSequenceTest.Context(true,false,true);var a=two(c);a.advance(true);var p=a.prompt();
        reject(()->a.decide(p.revision()+1,AttackCoinAttempt.Decision.RETRY));a.advance(true);
        check(a.prompt().equals(p)&&c.operations.size()==1,"Stale choice or refresh mutated attempt");
        decide(a,AttackCoinAttempt.Decision.FINISH_ATTACK);reject(()->a.decide(a.prompt().revision(),AttackCoinAttempt.Decision.FINISH_ATTACK));
        decide(a,AttackCoinAttempt.Decision.CONTINUE);check(a.complete()&&!a.retried(),"Keeping all results counted as retry");
        var empty=new AttackCoinAttempt(results->null);empty.advance(true);check(empty.complete()&&empty.prompt()==null&&empty.results().isEmpty(),"No-coin attack manufactured a prompt");
    }
    static void automatic() {
        var c=new AttackCoinSequenceTest.Context(true,false,true);var a=two(c);a.advance(false);
        check(a.complete()&&a.prompt()==null&&a.results().size()==2&&!a.retried(),"Ineligible source paused attempt");
    }
}
