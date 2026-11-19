import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/** The same authoritative action list must be valid for players and every AI difficulty. */
public final class AiStatusLegalityTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        for(var level:AiDifficulty.values())for(UUID seat:List.of(A,B))for(var status:List.of(SpecialCondition.ASLEEP,SpecialCondition.PARALYZED)){
            var m=AiCardOperationTest.attackGame(GeneratedRuleBatch8Test.hit(90,false),List.of());
            if(seat.equals(B))m.endTurn(A);
            m.applySpecialConditionByEffect(seat,seat,status);
            var session=MatchSession.multiplayer(m,A,B);var view=AiPracticeScenarios.view(session,seat);
            check(!m.mayAttack(seat)&&m.activeAttacks(seat).isEmpty(),"Server offered status-blocked attack");
            check(view.snapshot().options().stream().noneMatch(o->o.enabled()&&o.type()==ActionType.ATTACK),"AI action list ignored "+status);
            var random=m.randomTranscript();var before=m.clientView(null);
            GeneratedRuleBatch8Test.reject(()->m.attack(seat,"hit"));
            check(m.randomTranscript().equals(random)&&m.clientView(null).equals(before),"Rejected attack changed the match");
            var decision=AiPolicy.choose(view,73,Set.of(),level).orElseThrow();
            check(view.snapshot().options().get(decision.option()).type()!=ActionType.ATTACK,"AI selected a blocked attack");
            session.submit(seat,seat,decision.revision(),decision.option());
            AiCardOperationTest.settle(session,level);AiCardOperationTest.finish(session,level);
            check(m.phase()==MatchPhase.FINISHED,"Status scenario did not finish");
        }
        for(var status:List.of(SpecialCondition.CONFUSED,SpecialCondition.POISONED,SpecialCondition.BURNED)){
            var m=AiCardOperationTest.attackGame(GeneratedRuleBatch8Test.hit(90,false),List.of());m.applySpecialConditionByEffect(A,A,status);
            check(MatchSession.multiplayer(m,A,B).view(A,A).options().stream().anyMatch(o->o.enabled()&&o.type()==ActionType.ATTACK),"Nonblocking status prevented declaration: "+status);
        }
        System.out.println("AiStatusLegalityTest passed: Asleep/Paralyzed excluded on both seats and all four policies; 16 full matches; Confusion/Poison/Burn remain declarable");
    }
}
