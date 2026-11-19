import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernPokemonExchangeRepairTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static void acknowledge(ServerMatch m){var r=m.effectCardReveal();check(r!=null,"Perrin omitted public selected-card reveal");m.acknowledgeEffectReveal(r.confirmingPlayer(),r.id());}
    public static void main(String[] args){
        for(String id:List.of("sv6_en_160","sv6_en_209","sv6_en_220"))for(int returned=0;returned<=2;returned++)for(int taken=0;taken<=returned;taken++)for(boolean same:List.of(false,true)){
            var m=ModernEnergyRepairTest.game(BundledCombatData.load().trainer(id).orElseThrow(),List.of(),GeneratedRuleBatch8Test.hit(10,false),EnergyType.PSYCHIC,0);
            var trainer=ModernRuleRepair2Test.hand(m,A,"trainer");var before=m.privateView(A);int random=m.randomTranscript().size();
            m.playTrainer(A,trainer.instanceId(),null);var q=m.effectCardPrompt(A);check(q.minimum()==0&&q.maximum()==2&&q.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).category()==CardCategory.POKEMON),"Perrin wrong hand filter/count");
            var chosen=q.candidates().stream().limit(returned).toList();CopyAttackTest.reject(()->m.submitEffectCards(B,q.resolutionId(),q.step(),chosen.stream().map(CardInstance::instanceId).toList()));m.submitEffectCards(A,q.resolutionId(),q.step(),chosen.stream().map(CardInstance::instanceId).toList());
            if(returned>0){check(m.privateView(A).hand().containsAll(chosen),"Perrin moved cards before public reveal");acknowledge(m);var p=m.effectCardPrompt(A);
                check(p.minimum()==0&&p.maximum()==returned&&m.privateView(A).deck().containsAll(chosen)&&m.randomTranscript().size()==random,"Perrin wrong actual count or shuffled before search");
                if(returned==1){var tooMany=p.candidates().stream().limit(2).map(CardInstance::instanceId).toList();CopyAttackTest.reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),tooMany));}
                var selected=(same?chosen:p.candidates()).stream().limit(taken).toList();m.submitEffectCards(A,p.resolutionId(),p.step(),selected.stream().map(CardInstance::instanceId).toList());if(taken>0)acknowledge(m);
                check(m.privateView(A).hand().containsAll(selected)&&m.randomTranscript().size()>random,"Perrin failed return/search/shuffle");
            }else check(m.randomTranscript().size()==random,"Perrin shuffled despite choosing zero");
            var after=m.privateView(A);check(after.hand().size()==before.hand().size()-1-returned+taken&&after.deck().size()==before.deck().size()+returned-taken&&after.discard().contains(trainer)&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A),"Perrin asset count or continuation");
            var all=new ArrayList<CardInstance>();all.addAll(after.hand());all.addAll(after.deck());all.addAll(after.discard());check(all.stream().map(CardInstance::instanceId).distinct().count()==all.size(),"Perrin duplicated instance");
            CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),chosen.stream().map(CardInstance::instanceId).toList()));
        }
        System.out.println("ModernPokemonExchangeRepairTest passed: three prints, 0-2 returned/taken, exact actual count, both public reveals, return same card, single final shuffle, authority/replay and physical conservation");
    }
}
