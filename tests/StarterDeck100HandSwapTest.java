import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class StarterDeck100HandSwapTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static String id(int n){return StarterDeck100PokemonTest.id(n);}
    static void check(boolean ok,String why){StarterDeck100PokemonTest.check(ok,why);}
    public static void main(String[] args){swap();cannotPlay();invalid();System.out.println("StarterDeck100HandSwapTest passed: blind hand-first swap, exact deck order, no RNG, private UI, impossible/forged/replayed choices");}
    static void swap(){var m=StarterDeck100PokemonTest.game(1,id(374));var cup=StarterDeck100PokemonTest.hand(m,id(374));var deck=m.privateView(A).deck();var top=deck.getLast();var oldHand=m.privateView(A).hand();var chosen=oldHand.stream().filter(c->!c.equals(cup)).findFirst().orElseThrow();int rng=m.randomTranscript().size();m.playTrainer(A,cup.instanceId(),null);var p=m.effectCardPrompt(A);
        check(p.minimum()==1&&p.maximum()==1&&!p.candidates().contains(cup)&&p.candidates().contains(chosen),"wrong Cups candidates");String own=new com.google.gson.Gson().toJson(MatchSession.practice(m,A,A,B).view(A,A));for(var card:deck)check(!own.contains(card.instanceId().toString()),"top/deck identity leaked before choosing hand card");TopDeckLookTest.rejects(()->m.effectCardPrompt(B));var before=m.clientView(A);TopDeckLookTest.rejects(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));TopDeckLookTest.rejects(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(top.instanceId())));TopDeckLookTest.rejects(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(chosen.instanceId())));check(before.equals(m.clientView(A)),"forged Cups request changed state");
        m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(chosen.instanceId()));var expected=new ArrayList<>(deck);expected.set(expected.size()-1,chosen);check(m.privateView(A).deck().equals(expected)&&m.privateView(A).hand().contains(top)&&!m.privateView(A).hand().contains(chosen)&&m.privateView(A).discard().contains(cup),"Cups swapped wrong cards/order/zone");check(m.randomTranscript().size()==rng&&m.currentPlayer().equals(A),"Cups shuffled or ended turn");TopDeckLookTest.rejects(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(chosen.instanceId())));
    }
    static void cannotPlay(){for(boolean emptyDeck:List.of(false,true)){var m=StarterDeck100PokemonTest.game(1,id(374));var cup=StarterDeck100PokemonTest.hand(m,id(374));if(emptyDeck)m.discardTopDeckCardsByEffect(A,A,m.privateView(A).deck().size());else m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().filter(c->!c.equals(cup)).map(CardInstance::instanceId).toList());var before=m.clientView(A);TopDeckLookTest.rejects(()->m.playTrainer(A,cup.instanceId(),null));check(before.equals(m.clientView(A))&&m.privateView(A).hand().contains(cup),"unplayable Cups consumed card");}}
    static void invalid(){for(String field:List.of("\"minimum\":0","\"top_count\":1,\"minimum\":1")){String json="{\"type\":\"swap_hand_top_deck\",\"target\":\"HAND\",\"amount\":1,"+field+"}";TopDeckLookTest.rejects(()->EffectRegistry.standard().parse(com.google.gson.JsonParser.parseString(json).getAsJsonObject()));}}
}
