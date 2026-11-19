import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernWholeWindowRepairTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    public static void main(String[] args){
        var attack=ModernWindowDamageRepairTest.attack("sv3_en_179",0);
        for(int size:List.of(0,1,2,3,5,8))for(boolean take:List.of(false,true)){
            var m=ModernWindowDamageRepairTest.game(attack,0,false);m.drawCardsByEffect(A,A,m.privateView(A).deck().size()-size);
            var before=m.privateView(A);var top=ModernWindowDamageRepairTest.top(m,3);var random=m.randomTranscript();m.attack(A,attack.id());
            if(size>0){var q=m.effectCardPrompt(A);check(q.minimum()==0&&q.maximum()==3&&q.candidates().equals(top)&&m.clientView(B).effectSelection()==null&&m.effectCardReveal()==null,"Whole-window bounds or privacy");
                CopyAttackTest.reject(()->m.submitEffectCards(B,q.resolutionId(),q.step(),List.of()));
                if(top.size()>1){CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(top.getFirst().instanceId())));check(m.privateView(A).equals(before)&&m.effectCardPrompt(A).step()==q.step(),"Partial submission consumed cards or resolution");}
                m.toggleEffectCard(A,q.resolutionId(),q.step(),top.getFirst().instanceId());check(m.effectCardPrompt(A).selected().size()==top.size(),"Single click did not select whole group");
                m.toggleEffectCard(A,q.resolutionId(),q.step(),top.getLast().instanceId());check(m.effectCardPrompt(A).selected().isEmpty(),"Single click did not deselect group");
                var ids=take?top.stream().map(CardInstance::instanceId).toList():List.<UUID>of();m.submitEffectCards(A,q.resolutionId(),q.step(),ids);CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),ids));
            }
            int kept=take?top.size():Math.min(3,size-top.size());check(m.privateView(A).deck().size()==size-top.size()-(take?0:kept)&&m.privateView(A).hand().size()==before.hand().size()+kept&&m.privateView(A).discard().size()==before.discard().size()+(take?0:top.size()),"Whole-window short-deck counts");
            check(take?m.privateView(A).hand().containsAll(top):m.privateView(A).discard().containsAll(top),"Wrong physical cards taken/discarded");
            if(!take){var drawn=before.deck().subList(Math.max(0,size-top.size()-kept),size-top.size());check(m.privateView(A).hand().containsAll(drawn),"Did not draw after whole window");}
            check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B)&&m.randomTranscript().equals(random),"Whole-window stalled or shuffled");
        }
        System.out.println("ModernWholeWindowRepairTest passed: Greedent ex whole-group take/discard, short/empty deck, group toggle, partial/wrong-owner/replay rejection and private actual cards");
    }
}
