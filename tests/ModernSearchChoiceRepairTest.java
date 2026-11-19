import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernSearchChoiceRepairTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static void reveal(ServerMatch m){if(m.effectCardReveal()!=null){var q=m.effectCardReveal();m.acknowledgeEffectReveal(B,q.id());}}
    public static void main(String[] args){farigiraf();eevee();loveBall();System.out.println("ModernSearchChoiceRepairTest passed: either-player hand reset, Eevee actual distinct printed types including dual-type exclusion and evolution, Love Ball exact opposing public name, reveal/privacy, rejected requests and conservation");}
    static void farigiraf(){
        for(String id:List.of("sv2_en_155","sv2_en_228"))for(UUID owner:List.of(A,B))for(int handCount:List.of(0,2,8))for(int deckCount:List.of(0,1,8)){
            var a=ModernWindowDamageRepairTest.attack(id,0);var m=ModernWindowDamageRepairTest.game(a,0,false);
            m.drawCardsByEffect(A,owner,m.privateView(owner).deck().size()-deckCount);
            var toDiscard=m.privateView(owner).hand().stream().skip(handCount).map(CardInstance::instanceId).toList();if(!toDiscard.isEmpty())m.discardHandCardsByEffect(A,owner,toDiscard);
            var before=m.privateView(owner);var other=m.privateView(owner.equals(A)?B:A);m.attack(A,a.id());var q=m.effectChoicePrompt(A);m.chooseEffectOption(A,q.resolutionId(),q.step(),owner.equals(A)?0:1);
            int draws=Math.min(4+(owner.equals(B)?1:0),before.hand().size()+before.deck().size());
            check(m.privateView(owner).hand().size()==draws&&m.privateView(owner).deck().size()==before.hand().size()+before.deck().size()-draws,"Farigiraf wrong selected player/short-deck draw");
            var pool=new HashSet<>(before.hand());pool.addAll(before.deck());var after=new HashSet<>(m.privateView(owner).hand());after.addAll(m.privateView(owner).deck());check(pool.equals(after)&&m.privateView(owner).discard().equals(before.discard())&&!m.hasPendingEffectChoice(),"Farigiraf lost/duplicated cards or stalled");
            if(owner.equals(B))check(m.privateView(A).hand().equals(other.hand())&&m.privateView(A).deck().equals(other.deck()),"Farigiraf changed unchosen player");
        }
    }
    static ServerMatch game(TrainerProfile trainer,AttackDefinition attack,boolean dual,int seed){
        return ModernEnergyRepairTest.game(trainer,List.of(),attack,EnergyType.PSYCHIC,seed,Map.of("basic","Same","evolved","Same","wrongmon","Same ex"),
            Map.of("basic",Set.of(EnergyType.PSYCHIC),"evolved",dual?Set.of(EnergyType.FIRE,EnergyType.WATER):Set.of(EnergyType.DARKNESS),"wrongmon",Set.of(EnergyType.FIRE)));
    }
    static void eevee(){var a=ModernWindowDamageRepairTest.attack("sv3-5_en_133",0);
        for(boolean dual:List.of(false,true))for(int take:List.of(0,1,2,3)){
            if(dual&&take==3)continue;ServerMatch found=null;
            for(int seed=0;seed<100;seed++){var candidate=game(TrainerPlayLockTest.item(),a,dual,seed);if(List.of("basic","evolved","wrongmon").stream().allMatch(id->candidate.privateView(A).deck().stream().anyMatch(c->c.definitionId().equals(id)))&&candidate.privateView(A).deck().stream().filter(c->c.definitionId().equals("basic")).count()>=2){found=candidate;break;}}
            check(found!=null,"Eevee fixture");var m=found;ModernRuleRepair2Test.pay(m,A,m.activePokemonView(A).pokemonId(),"energy",1);var before=m.privateView(A);m.attack(A,a.id());var q=m.effectCardPrompt(A);
            var basics=q.candidates().stream().filter(c->c.definitionId().equals("basic")).limit(2).toList();CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),basics.stream().map(CardInstance::instanceId).toList()));check(m.effectCardReveal()==null&&m.privateView(A).equals(before),"Illegal same-type selection revealed or moved cards");
            var evolution=q.candidates().stream().filter(c->c.definitionId().equals("evolved")).findFirst().orElseThrow();var fire=q.candidates().stream().filter(c->c.definitionId().equals("wrongmon")).findFirst().orElseThrow();
            if(dual){CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(evolution.instanceId(),fire.instanceId())));m.toggleEffectCard(A,q.resolutionId(),q.step(),evolution.instanceId());check(m.effectCardPrompt(A).candidates().stream().noneMatch(c->c.definitionId().equals("wrongmon")),"Dual type not excluded from live legal choices");m.toggleEffectCard(A,q.resolutionId(),q.step(),evolution.instanceId());}
            var chosen=List.of(basics.getFirst(),evolution,fire).subList(0,take);var ids=chosen.stream().map(CardInstance::instanceId).toList();m.submitEffectCards(A,q.resolutionId(),q.step(),ids);
            if(take>0){check(m.effectCardReveal()!=null&&m.privateView(A).equals(before),"Search must reveal before committing");reveal(m);}
            check(m.privateView(A).hand().containsAll(chosen)&&m.privateView(A).hand().size()==before.hand().size()+take&&m.privateView(A).deck().size()==before.deck().size()-take&&!m.hasPendingEffectChoice(),"Eevee wrong actual selection/continuation");
        }
    }
    static void loveBall(){var trainer=DATA.trainer("sv6_en_156").orElseThrow();
        for(boolean take:List.of(false,true)){
            var m=game(trainer,GeneratedRuleBatch8Test.hit(10,false),false,0);var card=ModernRuleRepair2Test.hand(m,A,"trainer");var before=m.privateView(A);m.playTrainer(A,card.instanceId(),null);var q=m.effectCardPrompt(A);
            check(q.candidates().stream().allMatch(c->Set.of("basic","evolved").contains(c.definitionId()))&&q.candidates().stream().anyMatch(c->c.definitionId().equals("evolved")),"Love Ball must use exact public name, include different matching print/stage, exclude name suffix");
            var chosen=take?List.of(q.candidates().getFirst().instanceId()):List.<UUID>of();CopyAttackTest.reject(()->m.submitEffectCards(B,q.resolutionId(),q.step(),chosen));m.submitEffectCards(A,q.resolutionId(),q.step(),chosen);reveal(m);
            check(m.privateView(A).hand().size()==before.hand().size()-1+chosen.size()&&m.privateView(A).discard().contains(card)&&m.privateView(A).deck().size()==before.deck().size()-chosen.size()&&!m.hasPendingEffectChoice(),"Love Ball transfer/continuation");
        }
    }
}
