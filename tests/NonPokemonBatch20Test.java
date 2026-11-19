import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch20Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){damageReactions();knockoutReactions();System.out.println("NonPokemonBatch20Test passed: correct damage reactions including lethal hits and chooser ownership, no unprinted counter retaliation, knockout draw/mill and non-attack exclusion");}
    static void damageReactions(){
        for(String id:List.of("swsh6_en_152","swsh7_en_160"))for(boolean lethal:List.of(false,true)){
            var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.GRASS,EnergyType.GRASS,NonPokemonBatch2Test.DATA.trainer(id).orElseThrow(),GeneratedRuleBatch8Test.hit(100,false));
            var host=m.activePokemonView(A).pokemonId();m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),host);if(lethal)m.placeDamageCountersByEffect(A,A,host,40);m.endTurn(A);
            var energy=DynamicPassiveModifierTest.hand(m,B,"energy");m.attachEnergy(B,energy.instanceId(),m.activePokemonView(B).pokemonId());int hand=m.privateView(B).hand().size();m.attack(B,"hit");
            UUID chooser=id.equals("swsh6_en_152")?A:B;var q=m.effectCardPrompt(chooser);UUID selected=id.equals("swsh6_en_152")?energy.instanceId():q.candidates().getFirst().instanceId();
            GeneratedRuleBatch8Test.reject(()->m.submitEffectCards(chooser.equals(A)?B:A,q.resolutionId(),q.step(),List.of(selected)));
            m.submitEffectCards(chooser,q.resolutionId(),q.step(),List.of(selected));
            check(m.activePokemonView(B).damage()==0,"Tool incorrectly used Rocky Helmet damage counters");
            check(m.privateView(B).hand().size()==hand+(id.equals("swsh6_en_152")?1:-1),"Tool returned/discarded wrong hand count");
            if(id.equals("swsh6_en_152"))check(m.privateView(B).hand().contains(energy)&&m.activePokemonView(B).attachedEnergyIds().isEmpty(),"Helmet lost captured attacker's Energy");
            else check(m.privateView(B).discard().stream().anyMatch(c->c.instanceId().equals(selected)),"Mask did not discard opponent's chosen physical card");
            NonPokemonBatch2Test.settle(m);
        }
    }
    static void knockoutReactions(){
        for(String id:List.of("swsh1_en_167","swsh2_en_157"))for(boolean damage:List.of(false,true)){
            var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.GRASS,EnergyType.GRASS,NonPokemonBatch2Test.DATA.trainer(id).orElseThrow(),GeneratedRuleBatch8Test.hit(100,false));
            var trainer=NonPokemonBatch2Test.trainer(m);NonPokemonBatch2Test.trim(m,A,2,trainer);var host=m.activePokemonView(A).pokemonId();m.playTrainer(A,trainer.instanceId(),host);m.placeDamageCountersByEffect(A,A,host,40);m.endTurn(A);
            var deck=List.copyOf(m.privateView(B).deck());if(damage)m.attack(B,"hit");else m.knockOutPokemonByEffect(B,A,host);
            if(id.equals("swsh1_en_167"))check(m.privateView(A).hand().size()==(damage?7:1),"Lucky Egg ignored damage-only condition or failed after host knockout");
            else {check(m.privateView(B).deck().size()==deck.size()-(damage?2:0),"Cursed Shovel wrong discard count/cause");if(damage)check(m.privateView(B).discard().containsAll(deck.subList(deck.size()-2,deck.size())),"Shovel discarded wrong physical deck cards");}
            NonPokemonBatch2Test.settle(m);
        }
    }
}
