import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

public final class NonPokemonBatch24Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){hugh();reactions();System.out.println("NonPokemonBatch24Test passed: opponent-first five-card hands; six-counter and confusion reactions including lethal damage");}
    static void hugh(){
        for(int own:List.of(2,8))for(int opponent:List.of(2,8)){
            var m=NonPokemonBatch2Test.game("bw7_en_130",EnergyType.GRASS,1);var card=NonPokemonBatch2Test.trainer(m);NonPokemonBatch2Test.trim(m,A,own,card);NonPokemonBatch2Test.trim(m,B,opponent,null);
            m.playTrainer(A,card.instanceId(),null);
            if(opponent==8){check(m.pendingChoicePlayer().equals(B),"Hugh did not let opponent discard first");var q=m.effectCardPrompt(B);m.submitEffectCards(B,q.resolutionId(),q.step(),q.candidates().stream().limit(3).map(c->c.instanceId()).toList());}
            NonPokemonBatch2Test.settle(m);check(m.privateView(A).hand().size()==5&&m.privateView(B).hand().size()==5,"Hugh failed draw/discard combination");
        }
    }
    static void reactions(){
        for(String id:List.of("bw9_en_108","sm11_en_194"))for(boolean lethal:List.of(false,true)){
            var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.GRASS,EnergyType.GRASS,NonPokemonBatch2Test.DATA.trainer(id).orElseThrow(),GeneratedRuleBatch8Test.hit(100,false));var host=m.activePokemonView(A).pokemonId();
            m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),host);if(lethal)m.placeDamageCountersByEffect(A,A,host,40);m.endTurn(A);m.attack(B,"hit");
            if(id.equals("bw9_en_108"))check(m.activePokemonView(B).damage()==60,"Rock Guard failed to place six counters");
            else check(m.activePokemonView(B).conditions().contains(SpecialCondition.CONFUSED),"Bell failed to confuse attacker");
            NonPokemonBatch2Test.settle(m);
        }
    }
}
