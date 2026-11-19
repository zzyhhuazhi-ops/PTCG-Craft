import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernFluteRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static void check(boolean ok,String why){ModernRuleRepair2Test.check(ok,why);}
    public static void main(String[] args){
        var trainer=ModernEnergyRepairTest.DATA.trainer("sv6_en_142").orElseThrow();
        for(int seed:List.of(0,30,80))for(int size:List.of(0,1,3,6))for(int free:List.of(0,1,4))for(boolean select:List.of(false,true)){
            var m=ModernEnergyRepairTest.game(trainer,List.of(),GeneratedRuleBatch8Test.hit(40,false),EnergyType.WATER,seed);
            m.drawCardsByEffect(A,B,18);m.endTurn(A);
            while(m.benchPokemonView(B).size()<5-free)m.playBasicPokemonToBench(B,ModernRuleRepair2Test.hand(m,B,"basic").instanceId());m.endTurn(B);
            m.drawCardsByEffect(A,B,m.privateView(B).deck().size()-size);
            var before=m.privateView(B);var window=before.deck().subList(Math.max(0,size-5),size);var card=ModernRuleRepair2Test.hand(m,A,"trainer");
            if(size==0){CopyAttackTest.reject(()->m.playTrainer(A,card.instanceId(),null));check(m.privateView(A).hand().contains(card),"Empty-deck Flute consumed");continue;}
            m.playTrainer(A,card.instanceId(),null);var reveal=m.effectCardReveal();check(reveal!=null&&new HashSet<>(reveal.cardIds()).equals(new HashSet<>(window.stream().map(CardInstance::definitionId).toList())),"Did not publicly reveal exact top-five window");
            check(m.privateView(B).deck().equals(before.deck()),"Moved/shuffled before reveal");CopyAttackTest.reject(()->m.acknowledgeEffectReveal(B,reveal.id()));m.acknowledgeEffectReveal(A,reveal.id());
            var selected=List.<CardInstance>of();
            var eligible=window.stream().filter(c->m.cardDefinition(c.definitionId()).basicPokemon()).toList();
            if(!eligible.isEmpty()&&free>0){var q=m.effectCardPrompt(A);check(q.minimum()==0&&q.maximum()==Math.min(free,eligible.size())&&new HashSet<>(q.candidates()).equals(new HashSet<>(eligible)),"Flute leaked deep deck or wrong candidates/capacity");
                selected=select?q.candidates().stream().limit(q.maximum()).toList():List.of();var ids=selected.stream().map(CardInstance::instanceId).toList();
                CopyAttackTest.reject(()->m.submitEffectCards(B,q.resolutionId(),q.step(),ids));
                m.submitEffectCards(A,q.resolutionId(),q.step(),ids);CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),ids));
            }
            var expected=new HashSet<>(before.deck());expected.removeAll(selected);
            check(new HashSet<>(m.privateView(B).deck()).equals(expected)&&m.privateView(B).deck().size()==expected.size(),"Flute lost/copied/incorrectly removed cards");
            check(m.benchPokemonView(B).size()==5-free+selected.size()&&m.privateView(B).hand().equals(before.hand()),"Flute moved cards to wrong zone");
            check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A)&&m.privateView(A).discard().contains(card),"Flute stalled or trainer not disposed");
        }
        System.out.println("ModernFluteRepairTest passed: 72 cases; exact public top-five, 0/1/3/6 deck, zero/one/four free Bench slots, optional selection, physical conservation, authority and stale requests");
    }
}
