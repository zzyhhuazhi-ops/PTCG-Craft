import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** An Ability can pay its discard with no drawable cards; a Trainer cannot be played just to pay a cost. */
public final class AbilityDiscardCostTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final String COST="{\"type\":\"discard\",\"target\":\"HAND\",\"amount\":1,\"minimum\":1,\"as_cost\":true}";
    static final String DRAW="{\"type\":\"draw\",\"amount\":2}";
    static void check(boolean value,String why){GeneratedRuleBatch8Test.check(value,why);}
    static void reject(Runnable action){GeneratedRuleBatch8Test.reject(action);}
    static AbilityDefinition ability(boolean gated){return BundledCombatData.ability(JsonParser.parseString("{\"id\":\"trade\",\"nameZh\":\"Trade\",\"kind\":\"ACTIVATED_ONCE_PER_TURN\",\"effectSupport\":\"SUPPORTED\",\"triggers\":[{\"trigger\":\"ONCE_PER_TURN\",\"conditions\":["+(gated?"{\"type\":\"IS_ACTIVE\",\"target\":\"SELF\"}":"")+"],\"effects\":["+COST+","+DRAW+"]}]}").getAsJsonObject(),EffectRegistry.standard());}
    public static void main(String[] args){
        for(boolean emptyHand:List.of(false,true)){
            var a=ability(false);var m=GeneratedRuleBatch8Test.fixture(List.of(a),EnergyType.GRASS,EnergyType.GRASS);UUID host=m.activePokemonView(A).pokemonId();m.drawCardsByEffect(A,A,m.privateView(A).deck().size());
            if(emptyHand)m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().map(CardInstance::instanceId).toList());
            int size=m.privateView(A).hand().size();var before=m.privateView(A);check(m.canUseAbility(A,host,a.id())!=emptyHand,"Ability availability ignored mandatory discard");
            if(emptyHand){reject(()->m.useAbility(A,host,a.id()));check(before.equals(m.privateView(A))&&m.abilityOptions(A).stream().noneMatch(ServerMatch.AbilityOption::usedThisTurn),"failed cost mutated state or usage");}
            else {m.useAbility(A,host,a.id());var p=m.effectCardPrompt(A);reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId())));m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId()));check(m.privateView(A).hand().size()==size-1&&m.currentPlayer().equals(A),"empty-deck Ability did not discard and finish");reject(()->m.useAbility(A,host,a.id()));}
        }
        var gated=ability(true);var m=GeneratedRuleBatch8Test.fixture(List.of(gated),EnergyType.GRASS,EnergyType.GRASS);UUID bench=m.benchPokemonView(A).getFirst().pokemonId();check(!m.canUseAbility(A,bench,gated.id()),"cost bypassed binding condition");var gatedMatch=m;reject(()->gatedMatch.useAbility(A,bench,gated.id()));
        var trainer=ComposedTrainerIntegrationTest.item(COST+","+DRAW);m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.GRASS,EnergyType.GRASS,trainer,GeneratedRuleBatch8Test.hit(10,false));m.drawCardsByEffect(A,A,m.privateView(A).deck().size());var card=DynamicPassiveModifierTest.hand(m,A,"trainer");var before=m.privateView(A);var match=m;reject(()->match.playTrainer(A,card.instanceId(),null));check(before.equals(m.privateView(A)),"Trainer was played only to pay a discard cost");
        System.out.println("AbilityDiscardCostTest passed: empty-deck Ability discard, empty-hand affordability, gates, usage, privacy and Trainer exclusion");
    }
}
