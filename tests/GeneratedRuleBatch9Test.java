import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Reads actual generated attacks, with owned synthetic hosts to isolate damage semantics. */
public final class GeneratedRuleBatch9Test {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static AttackDefinition attack(String id,int slot){return GeneratedRuleBatch6Test.attack(id,slot);}
    static ServerMatch fixture(AttackDefinition attack){return GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.GRASS,EnergyType.GRASS,GeneratedRuleBatch8Test.trainer("swsh5_en_124"),attack);}
    static void turn(ServerMatch m,UUID owner){if(!m.currentPlayer().equals(owner))m.endTurn(m.currentPlayer());}
    static void take(ServerMatch m,UUID owner,int count){
        UUID victim=owner.equals(A)?B:A;
        for(int i=0;i<count;i++){
            turn(m,victim);
            if(m.benchPokemonView(victim).isEmpty())m.playBasicPokemonToBench(victim,DynamicPassiveModifierTest.hand(m,victim,"basic").instanceId());
            turn(m,owner);m.knockOutPokemonByEffect(owner,victim,m.benchPokemonView(victim).getFirst().pokemonId());
            check(UnrestrictedAttackPrizeTest.take(m,owner)==1,"Prize fixture did not take exactly one card");
        }
        turn(m,A);
    }
    public static void main(String[] args){bothBenches();prizes();opponentHand();stadium();basic();counters();tripleStatus();System.out.println("GeneratedRuleBatch9Test passed: actual both-Bench counts, exact/relative/taken prizes, opponent hand, Stadium, Basic, counter threshold and three statuses");}
    static void bothBenches(){
        var attack=attack("sm5_en_034",0);
        for(int own:List.of(0,1,2))for(int opp:List.of(0,1,2)){
            var m=fixture(attack);
            for(UUID p:List.of(A,B)){
                turn(m,p);int wanted=p.equals(A)?own:opp;
                if(wanted==0)m.movePokemonByEffect(p,p,m.benchPokemonView(p).getFirst().pokemonId(),LeavePlayDestination.HAND);
                if(wanted==2)m.playBasicPokemonToBench(p,DynamicPassiveModifierTest.hand(m,p,"basic").instanceId());
            }
            turn(m,A);m.attack(A,attack.id());check(m.lastAttack().finalDamage()==20*(own+opp),"Total Command included Active or omitted a Bench");
        }
    }
    static void prizes(){
        for(String id:List.of("swsh2_en_009","swsh4_en_127"))for(int taken:List.of(0,1,2)){
            var attack=attack(id,0);var m=fixture(attack);take(m,A,taken);take(m,B,1);m.attack(A,attack.id());
            check(m.lastAttack().finalDamage()==(id.equals("swsh2_en_009")?60*taken:160+30*taken),"Prize damage confused owner, remaining or taken: "+id);
        }
        for(boolean opponent:List.of(false,true))for(int delta:List.of(-1,0,1)){
            var attack=attack(opponent?"sm6_en_077":"sm8_en_108",0);var m=fixture(attack);int exact=opponent?4:3;take(m,opponent?B:A,6-exact+delta);m.attack(A,attack.id());
            check(m.lastAttack().finalDamage()==(opponent?30:80)+(delta==0?(opponent?90:80):0),"Exact Prize count accepted adjacent value");
        }
        for(int[] counts:List.of(new int[]{0,0},new int[]{0,1},new int[]{1,0},new int[]{1,1},new int[]{1,2})){
            var attack=attack("sm10_en_104",1);var m=fixture(attack);take(m,A,counts[0]);take(m,B,counts[1]);m.attack(A,attack.id());
            check(m.lastAttack().finalDamage()==(counts[0]<counts[1]?90:30),"Knuckle Blast reversed or weakened strict comparison");
        }
    }
    static void opponentHand(){
        var attack=attack("swsh4_en_172",1);
        for(int count:List.of(0,1,4)){
            var m=fixture(attack);var discard=m.privateView(B).hand().stream().skip(count).map(CardInstance::instanceId).toList();m.discardHandCardsByEffect(A,B,discard);
            check(m.privateView(A).hand().size()!=count,"Hand fixture cannot distinguish owner");m.attack(A,attack.id());check(m.lastAttack().finalDamage()==30*count,"Mind Ruler did not read defender hand");
        }
    }
    static void stadium(){
        var attack=attack("sm3_en_069",1);
        for(String state:List.of("absent","own","opponent","removed")){
            var m=BeforeDamageAttackTest.fixture(attack);
            if(!state.equals("absent")){
                UUID owner=state.equals("opponent")?B:A;turn(m,owner);m.playTrainer(owner,DynamicPassiveModifierTest.hand(m,owner,"stadium").instanceId(),null);turn(m,A);
                if(state.equals("removed"))m.discardStadiumByEffect(A);
            }
            m.attack(A,attack.id());check(m.lastAttack().finalDamage()==(Set.of("own","opponent").contains(state)?40:20),"Solar Heat used wrong Stadium ownership/lifecycle");
        }
    }
    static void basic(){
        var attack=attack("sm3-5_en_051",0);
        for(boolean evolved:List.of(false,true)){
            var m=fixture(attack);if(evolved){turn(m,B);m.evolveByEffect(B,m.activePokemonView(B).pokemonId(),DynamicPassiveModifierTest.hand(m,B,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);turn(m,A);}
            m.attack(A,attack.id());check(m.lastAttack().finalDamage()==(evolved?20:70),"Dangerous Head used wrong current stage");
        }
    }
    static void counters(){
        var attack=attack("swsh6_en_025",1);
        for(int count:List.of(0,9,10,11)){
            var m=fixture(attack);if(count>0)m.placeDamageCountersByEffect(A,A,m.activePokemonView(A).pokemonId(),count);
            m.attack(A,attack.id());check(m.lastAttack().finalDamage()==(count>=10?250:100),"Dynamite Tackle counted damage points or wrong threshold");
        }
    }
    static void tripleStatus(){
        var attack=attack("sm2_en_003",0);var m=fixture(attack);m.attack(A,attack.id());
        var defender=m.activePokemonView(B);
        check(m.lastAttack().finalDamage()==20&&defender.damage()==50,"Pollen Hazard omitted poison/burn Checkup damage");
        check(defender.conditions().containsAll(Set.of(SpecialCondition.POISONED,SpecialCondition.CONFUSED)),"Pollen Hazard did not retain simultaneous statuses");
    }
}
