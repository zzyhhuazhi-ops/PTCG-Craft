import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class StarterDeck100OpponentDiscardTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static String id(int n){return StarterDeck100PokemonTest.id(n);}
    static void check(boolean ok,String why){StarterDeck100PokemonTest.check(ok,why);}
    public static void main(String[] args){for(boolean lethal:List.of(false,true))mask(lethal);empty();disabled();System.out.println("StarterDeck100OpponentDiscardTest passed: opponent-owned private choice, spectator/controller privacy, lethal reaction before KO, empty hand and disabled Tool");}
    static ServerMatch game(){var m=StarterDeck100PokemonTest.game(1,id(386));m.playTrainer(A,StarterDeck100PokemonTest.hand(m,id(386)).instanceId(),m.activePokemonView(A).pokemonId());for(int i=0;i<2;i++)StarterDeck100EnergyTest.attach(m,A,B,"sd100_cn_grass_energy",m.activePokemonView(B).pokemonId());return m;}
    static void hit(ServerMatch m){m.attack(B,StarterDeck100CombatTest.COMBAT.pokemon(id(1)).orElseThrow().attacks().getFirst().id());}
    static void mask(boolean lethal){var m=game();UUID host=m.activePokemonView(A).pokemonId();if(lethal)m.placeDamageCountersByEffect(A,A,host,(m.activePokemonView(A).hitPoints()-10)/10);m.endTurn(A);var original=m.privateView(B).hand();hit(m);check(m.pendingChoicePlayer().equals(B),"effect controller chose opponent's hand");var p=m.effectCardPrompt(B);check(p.minimum()==1&&p.maximum()==1&&p.candidates().equals(original),"Mask hand candidates/count wrong");TopDeckLookTest.rejects(()->m.effectCardPrompt(A));
        var session=MatchSession.multiplayer(m,A,B);UUID spectator=UUID.randomUUID();session.addSpectator(spectator);var gson=new com.google.gson.Gson();for(UUID viewer:List.of(A,spectator)){String json=gson.toJson(session.view(viewer,viewer.equals(A)?A:null));for(var c:original)check(!json.contains(c.instanceId().toString()),"opponent hand leaked to controller/spectator");}check(!session.view(B,B).options().isEmpty(),"choosing opponent got no table actions");
        var before=m.clientView(B);var chosen=original.getFirst();TopDeckLookTest.rejects(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(chosen.instanceId())));TopDeckLookTest.rejects(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of()));TopDeckLookTest.rejects(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(m.privateView(B).deck().getFirst().instanceId())));check(before.equals(m.clientView(B)),"forged opponent discard changed state");
        m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(chosen.instanceId()));check(m.privateView(B).discard().contains(chosen)&&m.privateView(B).hand().size()==original.size()-1,"Mask discarded wrong count/zone");TopDeckLookTest.rejects(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(chosen.instanceId())));if(lethal)StarterDeck100CombatTest.settleKnockouts(m);check(m.currentPlayer().equals(A),"Mask did not resume KO/turn");
    }
    static void empty(){var m=game();m.endTurn(A);m.discardHandCardsByEffect(B,B,m.privateView(B).hand().stream().map(CardInstance::instanceId).toList());hit(m);check(!m.hasPendingCardSelection()&&m.currentPlayer().equals(A),"empty hand stalled Mask");}
    static void disabled(){var m=game();m.disableToolEffectsByEffect(A,A,m.activePokemonView(A).pokemonId());m.endTurn(A);int hand=m.privateView(B).hand().size();hit(m);check(!m.hasPendingCardSelection()&&m.privateView(B).hand().size()==hand,"disabled Mask discarded a card");}
}
