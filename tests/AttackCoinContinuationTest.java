import dev.hua.ptcglive.engine.effect.*;
import java.util.*;

/** Draft continuation only; actual match/Tool authority must still be integrated. */
public final class AttackCoinContinuationTest {
    static class Context extends AttackCoinSequenceTest.Context {
        boolean eligible=true,denyReservation;int used,prefix,branch,suffix;Map<String,CoinResult> bindings=new HashMap<>();
        Context(boolean...coins){super(coins);}
        public boolean attackCoinRetryAvailable(EffectDefinition e){return eligible&&used==0;}
        public void consumeAttackCoinRetry(EffectDefinition e){if(denyReservation||!attackCoinRetryAvailable(e))throw new IllegalStateException("Unavailable source");used++;}
        public boolean supportsCoinBindings(){return true;}
        public CoinResult namedCoinResult(String name){return bindings.get(name);}
        public void rememberCoinResult(String name,CoinResult result){if(bindings.putIfAbsent(name,result)!=null)throw new IllegalStateException("Duplicate binding");}
        public void executeAtomic(EffectDefinition e){switch(e.type()){case "prefix"->prefix++;case "branch"->branch++;case "suffix"->suffix++;default->throw new AssertionError(e.type());}}
    }
    static EffectDefinition simple(String name){return new EffectDefinition(name,EffectTarget.SELF,Map.of(),List.of(),List.of());}
    static EffectContinuation cursor(Context c) {
        var heads=new EffectDefinition("branch",EffectTarget.SELF,Map.of(),List.of(new ConditionDefinition(ConditionType.COIN_RESULT,Map.of("value","HEADS"),List.of())),List.of());
        var coin=new EffectDefinition("coin_flip",EffectTarget.SELF,Map.of("flips","1","bind","test"),List.of(),List.of(heads));
        return new EffectContinuation(new TriggeredEffects(EffectTrigger.AFTER_ATTACK,List.of(),List.of(simple("prefix"),coin,simple("suffix"))),c);
    }
    static void check(boolean value,String message){AttackCoinSequenceTest.check(value,message);}
    static void reject(Runnable r){AttackCoinSequenceTest.reject(r);}
    public static void main(String[] args){retry();decline();invalid();noEligibility();rejectSynchronous();System.out.println("AttackCoinContinuationTest passed: pause before coin binding/branches, reserve only on retry, unchanged committed prefix, invalid/stale/failed reservation atomicity and no-tool compatibility; match integration is verified separately by AttackCoinMatchTest");}
    static void retry(){var c=new Context(false,true);var s=cursor(c);s.advance();var p=s.pendingCoinChoice();check(p!=null&&c.prefix==1&&c.branch==0&&c.suffix==0&&c.bindings.isEmpty(),"Provisional result already committed");s.advance();check(c.operations.size()==1,"Prompt refresh spent RNG");s.decideCoin(p.step(),AttackCoinSequence.Decision.RETRY);s.advance();check(s.complete()&&c.used==1&&c.prefix==1&&c.branch==1&&c.suffix==1&&c.bindings.get("test").heads()==1,"Retry replayed prefix or retained old branch/binding");reject(()->s.decideCoin(p.step(),AttackCoinSequence.Decision.RETRY));}
    static void decline(){var c=new Context(false);var s=cursor(c);s.advance();s.decideCoin(s.pendingCoinChoice().step(),AttackCoinSequence.Decision.CONTINUE);s.advance();check(s.complete()&&c.used==0&&c.branch==0&&c.prefix==1&&c.suffix==1&&c.bindings.get("test").heads()==0,"Decline consumed Tool or rerolled");}
    static void invalid(){var c=new Context(false,true);var s=cursor(c);s.advance();var p=s.pendingCoinChoice();reject(()->s.decideCoin(p.step()+1,AttackCoinSequence.Decision.RETRY));reject(()->s.decideCoin(p.step(),AttackCoinSequence.Decision.FINISH_SEQUENCE));c.denyReservation=true;reject(()->s.decideCoin(p.step(),AttackCoinSequence.Decision.RETRY));check(s.pendingCoinChoice().equals(p)&&c.operations.size()==1&&c.used==0&&c.bindings.isEmpty(),"Rejected reservation mutated continuation");c.denyReservation=false;s.decideCoin(p.step(),AttackCoinSequence.Decision.RETRY);s.advance();check(s.complete()&&c.used==1,"Valid retry after atomic rejection failed");}
    static void rejectSynchronous(){var c=new Context(false);var coin=new EffectDefinition("coin_flip",EffectTarget.SELF,Map.of("flips","1"),List.of(),List.of(simple("branch")));reject(()->new EffectInterpreter(c).execute(coin));check(c.branch==0&&c.used==0&&c.operations.size()==1,"Synchronous path bypassed confirmation");}
    static void noEligibility(){var c=new Context(true);c.eligible=false;var s=cursor(c);s.advance();check(s.complete()&&s.pendingCoinChoice()==null&&c.used==0&&c.branch==1&&c.operations.equals(List.of("initial")),"Ordinary no-tool execution changed");}
}
