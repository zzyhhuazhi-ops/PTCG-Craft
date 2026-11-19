import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Reads generated card programs; free costs and neutral type isolate rule semantics. */
public final class GeneratedRuleBatch7Test {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static AttackDefinition attack(String id,int slot){return GeneratedRuleBatch6Test.attack(id,slot);}
    public static void main(String[] args){round();supporter();guard();prizes();System.out.println("GeneratedRuleBatch7Test passed: actual Round, Driving Buddy, Crystal Ray, Red Banquet and Critical Bite programs");}
    static void round(){
        for(String id:List.of("zsv10-5_en_105","xy10_en_080"))for(int ownCount:List.of(1,2,3)){
            var attack=attack(id,id.equals("xy10_en_080")?1:0);var m=BeforeDamageAttackTest.fixture(attack);
            if(ownCount==1)m.movePokemonByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId(),LeavePlayDestination.HAND);
            if(ownCount==3)m.playBasicPokemonToBench(A,DynamicPassiveModifierTest.hand(m,A,"basic").instanceId());
            m.attack(A,attack.id());check(m.lastAttack().finalDamage()==ownCount*(id.equals("xy10_en_080")?10:70),"Round must count own Active and Bench, excluding opponent: "+id+" own="+ownCount+" actual="+m.lastAttack().finalDamage());
        }
    }
    static void supporter(){
        var attack=attack("sv4_en_157",1);
        for(boolean played:List.of(false,true)){
            var m=SupporterTurnConditionTest.fixture(attack);if(played)SupporterTurnConditionTest.play(m,A,attack);
            m.attack(A,attack.id());check(m.lastAttack().finalDamage()==(played?140:70),"Driving Buddy completed hand-play condition");
            m.endTurn(B);m.attack(A,attack.id());check(m.lastAttack().finalDamage()==70,"Driving Buddy history survived next turn");
        }
    }
    static void guard(){
        var attack=attack("xy10_en_116",1);
        for(boolean evolved:List.of(false,true)){
            var m=BeforeDamageAttackTest.fixture(attack);m.attack(A,attack.id());
            if(evolved)m.evolveByEffect(B,m.activePokemonView(B).pokemonId(),DynamicPassiveModifierTest.hand(m,B,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
            m.attack(B,attack.id());check(m.lastAttack().finalDamage()==(evolved?0:70),"Crystal Ray wrong printed attacker stage");
        }
    }
    static void prizes(){
        for(boolean bench:List.of(false,true))for(boolean evolved:List.of(false,true)){
            var attack=attack(bench?"swsh12_en_105":"sm12_en_136",1);var m=BeforeDamageAttackTest.fixture(attack);
            UUID host=bench?m.benchPokemonView(B).getFirst().pokemonId():m.activePokemonView(B).pokemonId();
            if(evolved){m.endTurn(A);m.evolveByEffect(B,host,DynamicPassiveModifierTest.hand(m,B,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);m.endTurn(B);}
            int hp=evolved?350:300;int damage=bench?30:120;m.placeDamageCountersByEffect(A,B,host,(hp-damage)/10);
            if(bench)m.attack(A,attack.id(),host);else m.attack(A,attack.id());
            check(UnrestrictedAttackPrizeTest.take(m,A)==(bench?3:2),"Generated bonus ignored Evolution or Bench KO");
            if(bench)check(m.activePokemonView(B).damage()==0,"Critical Bite damaged an unselected Active");
        }
    }
}
