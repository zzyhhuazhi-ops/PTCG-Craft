import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

public final class NonPokemonBatch6Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[] args){devolution();cyllene();System.out.println("NonPokemonBatch6Test passed: devolution preserves host and blocks same-turn evolution; coin recovery uses actual heads and deck-top placement");}
    static void devolution(){
        var m=NonPokemonBatch2Test.game("bw6_en_113",EnergyType.GRASS,1);UUID host=m.activePokemonView(A).pokemonId();
        var evolution=DynamicPassiveModifierTest.hand(m,A,"evolved");m.evolve(A,host,evolution.instanceId());m.endTurn(A);m.endTurn(B);
        var energy=DynamicPassiveModifierTest.hand(m,A,"energy");m.attachEnergy(A,energy.instanceId(),host);m.placeDamageCountersByEffect(A,A,host,2);
        m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),host);NonPokemonBatch2Test.settle(m);
        check(m.privateView(A).hand().contains(evolution)&&m.activePokemonView(A).pokemonId().equals(host),"Devolution moved the wrong card or replaced host identity");
        check(m.activePokemonView(A).damage()==20&&m.activePokemonView(A).attachedEnergyIds().contains(energy.instanceId()),"Devolution lost damage or attached Energy");
        check(!m.canEvolve(A,host,"evolved"),"Devolved Pokemon evolved again on the same turn");
        m.endTurn(A);m.endTurn(B);check(m.canEvolve(A,host,"evolved"),"Devolution lock persisted into next turn");
    }
    static void cyllene(){
        var outcomes=new HashSet<Long>();
        for(int seed=0;seed<40&&outcomes.size()<3;seed++){
            var m=GeneratedRuleBatch23Test.fixture(NonPokemonBatch2Test.DATA.trainer("swsh10_en_138").orElseThrow(),seed,EnergyType.GRASS);
            var source=NonPokemonBatch2Test.trainer(m);var discarded=m.privateView(A).hand().stream().filter(c->!c.equals(source)).limit(3).map(c->c.instanceId()).toList();
            m.discardHandCardsByEffect(A,A,discarded);int before=m.randomTranscript().size();m.playTrainer(A,source.instanceId(),null);
            long heads=m.randomTranscript().subList(before,m.randomTranscript().size()).stream().filter(x->x.operation().startsWith("effect-coin:")&&x.result()==0).count();outcomes.add(heads);
            int chosen=0;
            while(m.hasPendingEffectChoice()){
                var prompt=m.effectCardPrompt(A);check(prompt!=null,"Cyllene did not issue its own discard selection");
                var card=prompt.candidates().getFirst();m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),List.of(card.instanceId()));chosen++;
                check(m.privateView(A).deck().getLast().equals(card),"Cyllene shuffled instead of returning to deck top");
            }
            check(chosen==heads,"Cyllene recovery count differs from actual coin heads");
        }
        check(outcomes.equals(Set.of(0L,1L,2L)),"Coin recovery fixture missed a heads outcome");
    }
}
