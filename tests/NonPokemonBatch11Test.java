import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch11Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){retreat();charge();helmet();System.out.println("NonPokemonBatch11Test passed: zero/exact printed retreat costs, Special Energy-only recovery, GX/legacy EX damage reduction");}
    static void retreat(){
        for(int retreat:List.of(0,1)){
            var m=NonPokemonBatch2Test.game("swsh10_en_141",EnergyType.GRASS,retreat);m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);var q=m.hasPendingCardSelection()?m.effectCardPrompt(A):null;
            check(retreat==0?q!=null&&!q.candidates().isEmpty():q==null||q.candidates().isEmpty(),"Feather Ball confused zero printed retreat with other costs");NonPokemonBatch2Test.settle(m);
        }
        for(int retreat:List.of(3,4)){
            var m=NonPokemonBatch2Test.game("sm11_en_204",EnergyType.GRASS,retreat);m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);var q=m.hasPendingCardSelection()?m.effectCardPrompt(A):null;
            check(retreat==3?q==null||q.candidates().isEmpty():q!=null&&!q.candidates().isEmpty()&&q.candidates().stream().allMatch(c->Set.of("basic","rulebox").contains(c.definitionId())),"Poke Maniac treated exactly four as four or more");NonPokemonBatch2Test.settle(m);
        }
    }
    static void charge(){
        var m=NonPokemonBatch2Test.game("xy11_en_105",EnergyType.GRASS,1);var special=DynamicPassiveModifierTest.hand(m,A,"special");var basic=DynamicPassiveModifierTest.hand(m,A,"energy");
        m.discardHandCardsByEffect(A,A,List.of(special.instanceId(),basic.instanceId()));m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);var q=m.hasPendingCardSelection()?m.effectCardPrompt(A):null;
        check(q!=null&&q.candidates().size()==1&&q.candidates().getFirst().equals(special),"Special Charge admitted basic Energy");
        m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(special.instanceId()));NonPokemonBatch2Test.settle(m);
        check(m.privateView(A).deck().contains(special)&&m.privateView(A).discard().contains(basic),"Special Charge moved wrong energy or failed with one available");
    }
    static void helmet(){
        for(var family:List.of(PokemonFamily.GX,PokemonFamily.EX_LEGACY,PokemonFamily.EX,PokemonFamily.V)){
            var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer("sm8_en_169").orElseThrow();c.defenderFamilies=Set.of(family);
            var m=GeneratedRuleBatch28Test.fixture(c);GeneratedRuleBatch28Test.play(m);m.endTurn(A);m.attack(B,"hit");
            check(m.activePokemonView(A).damage()==(family==PokemonFamily.GX||family==PokemonFamily.EX_LEGACY?70:100),"Choice Helmet incorrectly filtered attacking family");
        }
    }
}
