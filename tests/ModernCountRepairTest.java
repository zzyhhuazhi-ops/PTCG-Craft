import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernCountRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){CopyAttackTest.check(ok,why);}
    static void reject(Runnable action){CopyAttackTest.reject(action);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return ModernRuleRepair2Test.hand(m,p,id);}
    static ServerMatch game(String trainer,List<AbilityDefinition> abilities){return GeneratedRuleBatch8Test.fixture(abilities,EnergyType.GRASS,EnergyType.GRASS,DATA.trainer(trainer).orElseThrow(),GeneratedRuleBatch8Test.hit(40,false));}
    public static void main(String[] args){miriam();skwovet();clive();ortega();System.out.println("ModernCountRepairTest passed: Miriam three, Skwovet three, Clive three, Ortega three; actual moved/revealed counts, zero/max choices, deck order and chooser authority");}
    static void miriam(){
        for(String id:List.of("sv1_en_179","sv1_en_238","sv1_en_251"))for(int count:List.of(0,1,5)){
            var m=game(id,List.of());var trainer=hand(m,A,"trainer");
            check(!m.canPlayTrainer(A,trainer.instanceId(),null),"Miriam usable without discard targets");
            var eligible=m.privateView(A).hand().stream().filter(c->m.cardDefinition(c.definitionId()).category()==CardCategory.POKEMON).limit(5).toList();check(eligible.size()==5,"Miriam fixture");
            var discard=new ArrayList<>(eligible);discard.add(hand(m,A,"energy"));m.discardHandCardsByEffect(A,A,discard.stream().map(CardInstance::instanceId).toList());
            var before=m.privateView(A);m.playTrainer(A,trainer.instanceId(),null);var p=m.effectCardPrompt(A);
            check(new HashSet<>(p.candidates()).equals(new HashSet<>(eligible)),"Miriam non-Pokemon candidate");
            var chosen=eligible.stream().limit(count).toList();m.submitEffectCards(A,p.resolutionId(),p.step(),chosen.stream().map(CardInstance::instanceId).toList());
            int draw=count>0?3:0;check(m.privateView(A).hand().size()==before.hand().size()-1+draw&&m.privateView(A).deck().size()==before.deck().size()+count-draw,"Miriam draw must depend on actual recovery");
            check(m.privateView(A).discard().size()==before.discard().size()+1-count&&!m.hasPendingEffectChoice(),"Miriam conservation/continuation");
        }
    }
    static void skwovet(){
        for(String id:List.of("sv1_en_151","sv1_en_222","sv4-5_en_205"))for(int count:List.of(0,1,6)){
            var ability=DATA.pokemon(id).orElseThrow().abilities().getFirst();var m=game("swsh5_en_124",List.of(ability));UUID active=m.activePokemonView(A).pokemonId();
            var discard=m.privateView(A).hand().stream().skip(count).map(CardInstance::instanceId).toList();m.discardHandCardsByEffect(A,A,discard);var before=m.privateView(A);
            if(count==0){check(!m.canUseAbility(A,active,ability.id()),"Skwovet can draw from empty hand");continue;}
            m.useAbility(A,active,ability.id());var deck=m.privateView(A).deck();
            check(new HashSet<>(deck.subList(0,count)).equals(new HashSet<>(before.hand())),"Skwovet didn't put old hand on bottom");
            check(deck.subList(count,deck.size()).equals(before.deck().subList(0,before.deck().size()-1)),"Skwovet shuffled uninspected deck");
            check(m.privateView(A).hand().equals(List.of(before.deck().getLast()))&&!m.hasPendingEffectChoice(),"Skwovet must draw one for any positive moved count");reject(()->m.useAbility(A,active,ability.id()));
        }
    }
    static void clive(){
        for(String id:List.of("sv4-5_en_078","sv4-5_en_227","sv4-5_en_236"))for(int count:List.of(0,1,3)){
            var m=game(id,List.of());var supporters=m.privateView(B).hand().stream().filter(c->c.definitionId().equals("trainer")).toList();check(supporters.size()>=count,"Clive fixture");
            var excess=supporters.stream().skip(count).map(CardInstance::instanceId).toList();if(!excess.isEmpty())m.discardHandCardsByEffect(A,B,excess);int before=m.privateView(A).hand().size(),deck=m.privateView(A).deck().size();
            m.playTrainer(A,hand(m,A,"trainer").instanceId(),null);var r=m.effectCardReveal();check(r!=null&&r.cardIds().size()==m.privateView(B).hand().size(),"Clive must reveal complete hand");
            m.acknowledgeEffectReveal(r.confirmingPlayer(),r.id());check(m.privateView(A).hand().size()==before-1+Math.min(deck,count*2)&&!m.hasPendingEffectChoice(),"Clive counted tools/non-Supporters or stalled");
        }
    }
    static void ortega(){
        for(String id:List.of("sv3_en_190","sv3_en_219","sv8-5_en_141"))for(boolean draw:List.of(false,true)){
            var m=game(id,List.of());var before=m.privateView(B);m.playTrainer(A,hand(m,A,"trainer").instanceId(),null);var r=m.effectCardReveal();m.acknowledgeEffectReveal(r.confirmingPlayer(),r.id());
            var p=m.effectCardPrompt(A);var selected=p.candidates().getFirst();reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(selected.instanceId())));m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(selected.instanceId()));
            var q=m.effectChoicePrompt(B);check(q.optionsZh().size()==2,"Ortega optional draw chooser");reject(()->m.chooseEffectOption(A,q.resolutionId(),q.step(),0));m.chooseEffectOption(B,q.resolutionId(),q.step(),draw?0:1);
            check(m.privateView(B).deck().getFirst().equals(selected)&&m.privateView(B).hand().size()==before.hand().size()-1+(draw?1:0)&&!m.hasPendingEffectChoice(),"Ortega bottom/draw continuation");
        }
    }
}
