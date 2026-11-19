import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch18Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){poison();sleep();center();peeking();thirdEye();birch();System.out.println("NonPokemonBatch18Test passed: global poison including Darkness, two-coin sleep both players and removal, Bench-only once-per-turn healing, optional revealed hand cycling, actual discard/draw count, both shuffle-coin branches");}
    static void poison(){
        for(EnergyType type:List.of(EnergyType.GRASS,EnergyType.DARKNESS)){
            var m=NonPokemonBatch2Test.game("bw8_en_126",type,1);m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);
            m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);m.applySpecialConditionByEffect(A,B,SpecialCondition.POISONED);m.endTurn(A);
            check(m.activePokemonView(A).damage()==30&&m.activePokemonView(B).damage()==30,"Virbank did not add two counters to both poisoned Pokemon including Darkness");
            m.discardStadiumByEffect(B);m.endTurn(B);check(m.activePokemonView(A).damage()==40&&m.activePokemonView(B).damage()==40,"Virbank continued after leaving play");
        }
    }
    static void sleep(){
        var m=NonPokemonBatch2Test.game("sm11_en_207",EnergyType.GRASS,1);m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);
        for(UUID p:List.of(A,B))m.applySpecialConditionByEffect(A,p,SpecialCondition.ASLEEP);int before=m.randomTranscript().size();m.endTurn(A);
        var coins=m.randomTranscript().subList(before,m.randomTranscript().size()).stream().filter(e->e.operation().startsWith("checkup-asleep:")).toList();check(coins.size()==4,"Forest did not flip two coins for each asleep Pokemon");
        for(UUID p:List.of(A,B)){boolean tails=coins.stream().filter(e->e.operation().contains(p.toString())).anyMatch(e->e.result()!=0);check(m.activePokemonView(p).conditions().contains(SpecialCondition.ASLEEP)==tails,"Forest recovered with a tails");}
        m.discardStadiumByEffect(B);for(UUID p:List.of(A,B))m.applySpecialConditionByEffect(B,p,SpecialCondition.ASLEEP);before=m.randomTranscript().size();m.endTurn(B);
        check(m.randomTranscript().subList(before,m.randomTranscript().size()).stream().filter(e->e.operation().startsWith("checkup-asleep:")).count()==2,"Forest sleep rule survived removal");
    }
    static void center(){
        var m=NonPokemonBatch2Test.game("bw4_en_090",EnergyType.GRASS,1);m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);var active=m.activePokemonView(A).pokemonId();var bench=m.benchPokemonView(A).getFirst().pokemonId();m.placeDamageCountersByEffect(A,A,active,3);
        check(!m.canActivateStadium(A),"Pokemon Center healed only-damaged Active");m.placeDamageCountersByEffect(A,A,bench,3);m.activateStadium(A,m.clientView(A).stadium().instanceId(),m.stadiumEntrySequence());var q=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,q.resolutionId(),q.step(),bench);NonPokemonBatch2Test.settle(m);
        check(m.benchPokemonView(A).getFirst().damage()==10&&m.activePokemonView(A).damage()==30&&!m.canActivateStadium(A),"Pokemon Center target/amount/allowance wrong");
    }
    static void peeking(){
        for(boolean decline:List.of(false,true)){
            var m=NonPokemonBatch2Test.game("sm4_en_097",EnergyType.GRASS,1);var before=List.copyOf(m.privateView(B).hand());m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);NonPokemonBatch14Test.reveal(m);var q=m.effectChoicePrompt(A);m.chooseEffectOption(A,q.resolutionId(),q.step(),decline?1:0);NonPokemonBatch2Test.settle(m);
            check(m.privateView(B).hand().size()==before.size(),"Peeking Red Card lost actual hand count");if(decline)check(m.privateView(B).hand().equals(before),"Declined Red Card moved cards");
        }
    }
    static void thirdEye(){
        for(int count:List.of(0,2)){
            var m=NonPokemonBatch2Test.game("xy9_en_108",EnergyType.GRASS,1);int own=m.privateView(A).hand().size();m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);NonPokemonBatch14Test.reveal(m);var q=m.effectCardPrompt(A);var ids=q.candidates().stream().limit(count).map(CardInstance::instanceId).toList();m.submitEffectCards(A,q.resolutionId(),q.step(),ids);NonPokemonBatch2Test.settle(m);
            check(m.privateView(A).hand().size()==own-1&&m.privateView(A).discard().stream().map(CardInstance::instanceId).toList().containsAll(ids),"Third Eye draw did not equal actual optional discard");
        }
    }
    static void birch(){
        boolean heads=false,tails=false;for(int seed=0;seed<40&&!(heads&&tails);seed++){
            var m=GeneratedRuleBatch23Test.fixture(NonPokemonBatch2Test.DATA.trainer("xy5_en_134").orElseThrow(),seed,EnergyType.GRASS);int before=m.randomTranscript().size();m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);NonPokemonBatch2Test.settle(m);boolean head=GeneratedRuleBatch23Test.coinResult(m,before)==0;heads|=head;tails|=!head;check(m.privateView(A).hand().size()==(head?7:4),"Birch wrong coin draw count");
        }check(heads&&tails,"Birch fixture missed a coin branch");
    }
}
