import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.CardInstance;
import com.google.gson.JsonParser;
import java.util.*;

public final class StarterDeck100NamedBenchTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static String id(int n){return StarterDeck100PokemonTest.id(n);}
    static void check(boolean ok,String why){StarterDeck100PokemonTest.check(ok,why);}
    public static void main(String[] args){for(int count:List.of(0,1,3))mirage(count);capacity();invalid();System.out.println("StarterDeck100NamedBenchTest passed: named Evolution cards enter Bench directly, private choice, zero/one/three, capacity, wrong-name and replay rejection");}
    static ServerMatch game(){for(int seed=0;seed<300;seed++){var m=StarterDeck100PokemonTest.gameSeed(176,1,seed,id(176));if(m.privateView(A).deck().stream().filter(c->c.definitionId().equals(id(176))).count()>=3)return m;}throw new AssertionError("no Kirlia fixture");}
    static void mirage(int count){var m=game();var wrong=m.privateView(A).deck().stream().filter(c->c.definitionId().equals(id(175))).findFirst().orElseThrow();int bench=m.benchPokemonView(A).size();StarterDeck100PokemonTest.attack(m,0);var p=m.effectCardPrompt(A);check(p.candidates().size()>=3&&p.candidates().stream().allMatch(c->c.definitionId().equals(id(176))),"Mirage offered wrong names or omitted Evolution cards");var cards=p.candidates().stream().limit(count).toList();var before=m.clientView(A);TopDeckLookTest.rejects(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(wrong.instanceId())));TopDeckLookTest.rejects(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of()));check(before.equals(m.clientView(A)),"forged Mirage selection mutated state");m.submitEffectCards(A,p.resolutionId(),p.step(),cards.stream().map(CardInstance::instanceId).toList());
        check(m.benchPokemonView(A).size()==bench+count&&m.currentPlayer().equals(B),"Mirage did not place chosen count/end turn");for(var card:cards){var view=StarterDeck100PokemonTest.view(m,A,card.instanceId());check(view.topCardId().equals(id(176))&&view.damage()==0&&!m.privateView(A).hand().contains(card)&&!m.privateView(A).deck().contains(card),"Mirage card not directly placed as its own host");}TopDeckLookTest.rejects(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));
    }
    static void capacity(){var m=game();while(m.benchPokemonView(A).size()<4)m.playBasicPokemonToBench(A,StarterDeck100PokemonTest.hand(m,id(175)).instanceId());StarterDeck100PokemonTest.attack(m,0);var p=m.effectCardPrompt(A);TopDeckLookTest.rejects(()->m.submitEffectCards(A,p.resolutionId(),p.step(),p.candidates().stream().limit(2).map(CardInstance::instanceId).toList()));m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId()));check(m.benchPokemonView(A).size()==5,"Mirage exceeded/missed last free Bench slot");m.endTurn(B);m.attack(A,"mirage-step");check(!m.hasPendingCardSelection()&&m.currentPlayer().equals(B),"full Bench stalled attack");}
    static void invalid(){for(String json:List.of("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":3,\"minimum\":0,\"card_category\":\"POKEMON\",\"destination\":\"BENCH\"}","{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":3,\"minimum\":0,\"pokemon_names\":\"Kirlia\",\"destination\":\"BENCH\"}"))TopDeckLookTest.rejects(()->ZoneSelectionEffect.from(EffectRegistry.standard().parse(JsonParser.parseString(json).getAsJsonObject())));}
}
