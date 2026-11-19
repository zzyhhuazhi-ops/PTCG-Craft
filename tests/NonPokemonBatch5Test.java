import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

public final class NonPokemonBatch5Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){caitlin();grant();welder();System.out.println("NonPokemonBatch5Test passed: ordered hand-to-bottom counted draw, Grant excluded costs and repeated recovery, Welder successful attachment draw");}
    static void caitlin(){
        var m=NonPokemonBatch2Test.game("swsh6_en_132",EnergyType.GRASS,1);var source=NonPokemonBatch2Test.trainer(m);
        var chosen=m.privateView(A).hand().stream().filter(c->!c.equals(source)).limit(2).toList();int size=m.privateView(A).hand().size();
        m.playTrainer(A,source.instanceId(),null);var prompt=m.effectCardPrompt(A);
        check(prompt!=null&&prompt.minimum()==0,"Caitlin must allow an optional hand selection");
        m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),chosen.stream().map(c->c.instanceId()).toList());NonPokemonBatch2Test.settle(m);
        check(m.privateView(A).hand().size()==size-1,"Caitlin did not draw the actual returned count");
        check(m.privateView(A).deck().subList(0,2).equals(chosen),"Caitlin lost chosen bottom order");
        check(!m.privateView(A).discard().containsAll(chosen),"Caitlin discarded the chosen cards");
    }
    static void grant(){
        var profile=DynamicPassiveModifierTest.trainer("SUPPORTER","{\"type\":\"turn_attack_bonus\",\"target\":\"SELF\",\"amount\":30,\"pokemon_types\":\"FIGHTING\"},{\"type\":\"discard_recover_named_supporter\",\"target\":\"DISCARD\",\"definition_ids\":\"trainer\",\"hand_discard_cost\":2}");
        var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.FIGHTING,EnergyType.FIGHTING,profile,GeneratedRuleBatch8Test.hit(10,false));
        var source=NonPokemonBatch2Test.trainer(m);m.discardHandCardsByEffect(A,A,List.of(source.instanceId()));
        var same=NonPokemonBatch2Test.trainer(m);var ordinary=DynamicPassiveModifierTest.hand(m,A,"energy");
        check(!m.canRecoverDiscardSupporter(A,source.instanceId(),List.of(same.instanceId(),ordinary.instanceId())),"Grant accepted another Grant as cost");
        for(int repeat=0;repeat<2;repeat++){
            var cost=m.privateView(A).hand().stream().filter(c->!c.definitionId().equals("trainer")).limit(2).map(c->c.instanceId()).toList();
            check(m.canRecoverDiscardSupporter(A,source.instanceId(),cost),"Grant incorrectly limited recovery to once per turn");
            m.recoverDiscardSupporter(A,source.instanceId(),cost);check(m.privateView(A).hand().contains(source),"Grant not recovered");
            m.discardHandCardsByEffect(A,A,List.of(source.instanceId()));
        }
    }
    static void welder(){
        var m=NonPokemonBatch2Test.game("sm10_en_189",EnergyType.FIRE,1);int before=m.privateView(A).hand().size();
        m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);
        var prompt=m.effectCardPrompt(A);check(prompt!=null&&prompt.candidates().stream().allMatch(c->c.definitionId().equals("energy")),"Welder accepted non-basic Fire Energy");
        m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),List.of(prompt.candidates().getFirst().instanceId()));NonPokemonBatch2Test.settle(m);
        check(m.privateView(A).hand().size()==before+1,"Welder must draw three after attaching one and spending the Supporter");
    }
}
