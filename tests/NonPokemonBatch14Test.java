import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.*;
import com.google.gson.JsonParser;
import java.util.*;

public final class NonPokemonBatch14Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){sidney();oldFilters();System.out.println("NonPokemonBatch14Test passed: revealed-hand selection admits only Tool/Special Energy/Stadium, basic Energy rejected, older Item/Pokemon filters no longer admit unrelated cards");}
    static void reveal(ServerMatch m){var r=m.effectCardReveal();check(r!=null,"Opponent hand was not revealed before selection");m.acknowledgeEffectReveal(r.confirmingPlayer(),r.id());}
    static void sidney(){
        var m=GeneratedRuleBatch23Test.fixture(NonPokemonBatch2Test.DATA.trainer("swsh8_en_241").orElseThrow(),0,EnergyType.GRASS);
        int hand=m.privateView(B).hand().size();m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);check(m.privateView(B).hand().size()==hand,"Sidney moved cards before reveal acknowledgement");reveal(m);
        var q=m.effectCardPrompt(A);check(q.minimum()==0&&q.maximum()==2&&!q.candidates().isEmpty()&&q.candidates().stream().allMatch(c->Set.of("tool","special","stadium").contains(c.definitionId())),"Sidney category union contains wrong cards");
        var basic=DynamicPassiveModifierTest.hand(m,B,"energy");GeneratedRuleBatch8Test.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(basic.instanceId())));
        var selected=new ArrayList<UUID>();for(String id:List.of("tool","special"))selected.add(q.candidates().stream().filter(c->c.definitionId().equals(id)).findFirst().orElseThrow().instanceId());
        m.submitEffectCards(A,q.resolutionId(),q.step(),selected);NonPokemonBatch2Test.settle(m);
        check(m.privateView(B).hand().size()==hand-2&&m.privateView(B).discard().stream().map(CardInstance::instanceId).toList().containsAll(selected),"Sidney did not discard the selected physical cards");
    }
    static void oldFilters(){
        for(boolean items:List.of(false,true)){
            String args=items?"\"amount\":2,\"minimum\":0,\"card_category\":\"TRAINER\",\"trainer_category\":\"ITEM\",\"destination\":\"DISCARD\"":"\"amount\":1,\"card_category\":\"POKEMON\"";
            var profile=BundledCombatData.trainer(JsonParser.parseString("{\"trainerCategory\":\"SUPPORTER\",\"effects\":[{\"type\":\"reveal_hand\",\"target\":\"OPPONENT_ACTIVE\"},{\"type\":\"opponent_hand_to_bottom\",\"target\":\"OPPONENT_ACTIVE\","+args+"}]}").getAsJsonObject(),EffectRegistry.standard());
            var m=GeneratedRuleBatch23Test.fixture(profile,0,EnergyType.GRASS);m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);reveal(m);var q=m.effectCardPrompt(A);
            check(!q.candidates().isEmpty()&&q.candidates().stream().allMatch(c->items?Set.of("item","tool").contains(c.definitionId()):m.cardDefinition(c.definitionId()).category()==CardCategory.POKEMON),"Old opponent-hand selector leaked an unrelated category");
            NonPokemonBatch2Test.settle(m);
        }
    }
}
