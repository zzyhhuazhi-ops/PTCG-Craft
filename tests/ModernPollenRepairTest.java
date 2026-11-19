import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

public final class ModernPollenRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static void check(boolean ok,String why){ModernRuleRepair2Test.check(ok,why);}
    public static void main(String[] args){
        var attack=ModernWindowDamageRepairTest.attack("sv5_en_076",0);
        for(int seed:List.of(0,30,80))for(int mode=0;mode<9;mode++){
            var m=ModernShuffleScopeRepairTest.game(attack,seed);m.endTurn(A);m.endTurn(B);
            UUID source=m.activePokemonView(A).pokemonId(),target=m.activePokemonView(B).pokemonId(),bench=m.benchPokemonView(B).getFirst().pokemonId();
            ModernShuffleScopeRepairTest.pay(m,A,source);
            if(mode==7)EventTargetBindingTest.field(m).addProtection(B,B,target,new ProtectionEffect(new EffectProtection(EffectProtection.Source.ATTACK,EffectProtection.Relation.OPPONENT,EffectProtection.Scope.EFFECTS_ONLY),ContinuousEffect.Expiry.LEAVES_PLAY));
            m.attack(A,attack.id());check(m.currentPlayer().equals(B),"Pollen failed to end attack");
            if(mode==1){m.switchActiveByEffect(B,B,bench);m.switchActiveByEffect(B,B,target);}
            if(mode==2)m.evolveByEffect(B,target,ModernRuleRepair2Test.hand(m,B,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
            if(mode==5)m.knockOutPokemonByEffect(B,B,target);
            else {
                m.endTurn(B);
                if(mode==3){UUID ownBench=m.benchPokemonView(A).getFirst().pokemonId();m.switchActiveByEffect(A,A,ownBench);m.movePokemonByEffect(A,A,source,LeavePlayDestination.HAND);}
                if(mode==4){m.endTurn(A);m.endTurn(B);}
                if(mode==6){int missing=490-m.activePokemonView(B).damage();EventTargetBindingTest.field(m).placeDamageCounters(B,target,missing/10);m.applySpecialConditionByEffect(A,B,SpecialCondition.POISONED);m.endTurn(A);}
                else if(mode==8||mode==7){int missing=470-m.activePokemonView(B).damage();EventTargetBindingTest.field(m).placeDamageCounters(B,target,missing/10);m.attack(A,attack.id());}
                else m.knockOutPokemonByEffect(A,B,target);
            }
            int expected=Set.of(0,3,8).contains(mode)?3:1,claimed=0;
            while(m.phase()==MatchPhase.PRIZE_SELECTION&&claimed<7){check(m.pendingChoicePlayer().equals(A),"Wrong prize owner");m.takePrizeAt(A,0);claimed++;}
            check(claimed==expected,"Pollen wrong prizes mode="+mode+": "+claimed+" expected "+expected);
            if(m.phase()==MatchPhase.CHOOSE_REPLACEMENT)m.chooseReplacement(B,m.benchPokemonView(B).getFirst().pokemonId());
            check(!m.hasPendingEffectChoice(),"Pollen left unresolved choice");
        }
        System.out.println("ModernPollenRepairTest passed: 27 scenarios; next-own-turn attack/effect KOs, source leaves play, defending switch/evolution clears, expiry, opponent turn, Checkup and protection");
    }
}
