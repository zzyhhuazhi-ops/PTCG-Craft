import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernLifeboatRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static void check(boolean ok,String why){ModernRuleRepair2Test.check(ok,why);}
    public static void main(String[] args){
        var ability=ModernEnergyRepairTest.DATA.pokemon("sv4_en_036").orElseThrow().abilities().getFirst();
        for(boolean ownCards:List.of(false,true))for(boolean otherCards:List.of(false,true))for(int full:List.of(0,1,2))for(boolean accept:List.of(false,true)){
            var m=ModernEnergyRepairTest.game(ModernEnergyRepairTest.DATA.trainer("swsh5_en_124").orElseThrow(),List.of(ability),GeneratedRuleBatch8Test.hit(40,false),EnergyType.WATER,0);
            m.drawCardsByEffect(A,B,18);var own=ModernRuleRepair2Test.hand(m,A,"basic");var other=ModernRuleRepair2Test.hand(m,B,"basic");
            if(ownCards)m.discardHandCardsByEffect(A,A,List.of(own.instanceId()));if(otherCards)m.discardHandCardsByEffect(A,B,List.of(other.instanceId()));
            if(full==1)while(m.benchPokemonView(A).size()<5)m.playBasicPokemonToBench(A,ModernRuleRepair2Test.hand(m,A,"basic").instanceId());
            m.endTurn(A);if(full==2)while(m.benchPokemonView(B).size()<5)m.playBasicPokemonToBench(B,ModernRuleRepair2Test.hand(m,B,"basic").instanceId());m.endTurn(B);
            int ownBefore=m.benchPokemonView(A).size(),otherBefore=m.benchPokemonView(B).size();
            m.evolve(A,m.activePokemonView(A).pokemonId(),ModernRuleRepair2Test.hand(m,A,"evolved").instanceId());
            var trigger=m.triggerOrderPrompt();
            if(trigger==null){check(!(ownCards&&full!=1||otherCards&&full!=2),"Missing usable Lifeboat offer: own="+ownCards+",opponent="+otherCards+",full="+full);continue;}
            check(trigger.optionalDecision(),"Lifeboat must be optional");m.decideTriggeredEffect(A,trigger.id(),accept);
            if(accept&&otherCards&&full!=2){var q=m.effectCardPrompt(B);check(q.minimum()==1&&q.candidates().equals(List.of(other)),"Opponent must choose own Basic discard");
                CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(other.instanceId())));CopyAttackTest.reject(()->m.submitEffectCards(B,q.resolutionId(),q.step(),List.of()));
                m.submitEffectCards(B,q.resolutionId(),q.step(),List.of(other.instanceId()));
                CopyAttackTest.reject(()->m.submitEffectCards(B,q.resolutionId(),q.step(),List.of(other.instanceId())));
            }
            if(accept&&ownCards&&full!=1){var q=m.effectCardPrompt(A);check(q.minimum()==1&&q.candidates().equals(List.of(own)),"Own step must follow opponent and use own discard");m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(own.instanceId()));}
            check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A),"Lifeboat stalled/ended turn");
            check(m.benchPokemonView(A).size()==ownBefore+(accept&&ownCards&&full!=1?1:0)&&m.benchPokemonView(B).size()==otherBefore+(accept&&otherCards&&full!=2?1:0),"Lifeboat placed incorrect side/count");
        }
        System.out.println("ModernLifeboatRepairTest passed: 24 scenarios; optional trigger, opponent chooses first, mandatory exact Basic, empty discard/full Bench, both owners and authority/stale submissions");
    }
}
