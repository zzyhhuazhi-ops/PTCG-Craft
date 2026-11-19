import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class StarterDeck100EvolutionLockTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static void check(boolean b,String s){StarterDeck100PokemonTest.check(b,s);}
    static CardInstance handB(ServerMatch m,String id){var c=m.privateView(B).hand().stream().filter(x->x.definitionId().equals(id)).findFirst().orElse(null);if(c==null){c=m.privateView(B).deck().stream().filter(x->x.definitionId().equals(id)).findFirst().orElseThrow();m.searchOwnDeckToHandByEffect(B,List.of(c.instanceId()));}return c;}
    public static void main(String[] args){handLock();deckAllowed();System.out.println("StarterDeck100EvolutionLockTest passed: Mandibuzz hand-only lock, atomic manual/effect rejection, switch clear, expiry and deck evolution");}
    static void handLock(){for(boolean switched:List.of(false,true)){
        var m=StarterDeck100PokemonTest.game(283,4);StarterDeck100PokemonTest.attack(m,0);UUID host=m.activePokemonView(B).pokemonId();var evolution=handB(m,"sd100_cn_003");var before=m.privateView(B);
        check(!m.canEvolve(B,host,evolution.definitionId())&&!m.canEvolveByEffect(B,host,evolution.definitionId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING),"hand lock missing from options");
        TopDeckLookTest.rejects(()->m.evolve(B,host,evolution.instanceId()));TopDeckLookTest.rejects(()->m.evolveByEffect(B,host,evolution.instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING));check(before.equals(m.privateView(B)),"rejected hand evolution moved card");
        if(switched){m.switchActiveByEffect(B,B,m.benchPokemonView(B).getFirst().pokemonId());m.switchActiveByEffect(B,B,host);}else{m.endTurn(B);m.endTurn(A);}
        check(m.canEvolve(B,host,evolution.definitionId()),"hand restriction did not clear/expire");m.evolve(B,host,evolution.instanceId());StarterDeck100PokemonTest.declineEntryTriggers(m);
    }}
    static void deckAllowed(){var a=DynamicPassiveModifierTest.A;var b=DynamicPassiveModifierTest.B;var ability=ResumableAbilityTest.ability("ascend",DeckEvolutionEffectTest.EVOLVE);
        var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.basic(EnergyType.COLORLESS),DynamicPassiveModifierTest.attack(40,false,false),List.of(ability));UUID host=m.activePokemonView(a).pokemonId();
        var effect=EffectRegistry.standard().parse(com.google.gson.JsonParser.parseString("{\"type\":\"prevent_evolution\",\"target\":\"OWN_ACTIVE\",\"source\":\"HAND\"}").getAsJsonObject());
        m.executeEffectProgramByRule(a,host,new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_ACTIVATE,List.of(),List.of(effect)))),EffectTrigger.ON_ACTIVATE);
        check(!m.canEvolveByEffect(a,host,"evolved",EvolutionPermission.CARD_EFFECT_ALL_TIMING),"fixture hand restriction absent");m.useAbility(a,host,ability.id());var p=m.effectCardPrompt(a);check(p.candidates().stream().allMatch(c->c.definitionId().equals("evolved")),"deck evolution filtered by hand-only restriction");m.submitEffectCards(a,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId()));check(m.activePokemonView(a).topCardId().equals("evolved"),"deck evolution did not bypass hand-only restriction");
    }
}
