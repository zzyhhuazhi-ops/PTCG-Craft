import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class StarterDeck100StyleSearchTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static String id(int n){return StarterDeck100PokemonTest.id(n);}
    static void check(boolean b,String s){StarterDeck100PokemonTest.check(b,s);}
    public static void main(String[] args){octillery();altaria();invalid();System.out.println("StarterDeck100StyleSearchTest passed: cross-category Rapid Strike search, player-wide usage, private candidates and reveal/shuffle/top-deck ordering");}
    static void octillery(){for(int pick:List.of(0,89,394,414,380,402)){
        var m=StarterDeck100PokemonTest.game(90,id(394),id(414),id(380),id(402),id(373));UUID source=m.activePokemonView(A).pokemonId(),other=m.benchPokemonView(A).getFirst().pokemonId();StarterDeck100PokemonTest.evolve(m,A,other,90);StarterDeck100PokemonTest.ability(m,source);var p=m.effectCardPrompt(A);
        check(p.minimum()==0&&p.maximum()==1&&p.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).battleStyles().contains(BattleStyle.RAPID_STRIKE)),"Rapid Strike Search category/style filter wrong");
        for(int n:List.of(89,394,414,380,402))check(p.candidates().stream().anyMatch(c->c.definitionId().equals(id(n))),"Rapid Strike Search omitted printed category: "+n);
        check(m.clientView(B).effectSelection()==null,"Rapid Strike private candidates leaked");
        var picked=pick==0?List.<CardInstance>of():List.of(p.candidates().stream().filter(c->c.definitionId().equals(id(pick))).findFirst().orElseThrow());int rng=m.randomTranscript().size();m.submitEffectCards(A,p.resolutionId(),p.step(),picked.stream().map(CardInstance::instanceId).toList());
        if(pick!=0){var reveal=m.effectCardReveal();check(reveal!=null&&!m.privateView(A).hand().contains(picked.getFirst()),"Rapid Strike card moved before reveal acknowledgement");m.acknowledgeEffectReveal(B,reveal.id());check(m.privateView(A).hand().contains(picked.getFirst()),"Rapid Strike card never reached hand");}
        check(m.randomTranscript().size()>rng&&!m.canUseAbility(A,other,"rapid-strike-search"),"Rapid Strike failed search did not shuffle or second Octillery bypassed shared use");m.endTurn(A);m.endTurn(B);check(m.canUseAbility(A,other,"rapid-strike-search"),"Rapid Strike shared use did not reset");
    }}
    static void altaria(){for(boolean take:List.of(false,true)){
        var m=StarterDeck100PokemonTest.game(314,id(398),id(373));UUID source=m.activePokemonView(A).pokemonId();var deck=List.copyOf(m.privateView(A).deck());var hand=List.copyOf(m.privateView(A).hand());int rng=m.randomTranscript().size();StarterDeck100PokemonTest.ability(m,source);var p=m.effectCardPrompt(A);check(p.candidates().stream().allMatch(c->c.definitionId().equals(id(398))),"Tempting Tune offered non-Supporter");var card=p.candidates().getFirst();
        var item=deck.stream().filter(c->c.definitionId().equals(id(373))).findFirst().orElseThrow();TopDeckLookTest.rejects(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(item.instanceId())));
        m.submitEffectCards(A,p.resolutionId(),p.step(),take?List.of(card.instanceId()):List.of());if(take){var reveal=m.effectCardReveal();check(reveal!=null&&m.privateView(A).deck().equals(deck)&&m.randomTranscript().size()==rng,"Tempting Tune shuffled before reveal");TopDeckLookTest.rejects(()->m.acknowledgeEffectReveal(A,reveal.id()));m.acknowledgeEffectReveal(B,reveal.id());}
        check(m.privateView(A).hand().equals(hand)&&m.privateView(A).deck().size()==deck.size()&&m.randomTranscript().size()>rng,"Tempting Tune moved to hand, lost a card or skipped failed-search shuffle");
        if(take){check(m.privateView(A).deck().getLast().equals(card),"Tempting Tune shuffled chosen card instead of placing it on top afterward");m.drawCardsByEffect(A,A,1);check(m.privateView(A).hand().contains(card),"Tempting Tune placed at wrong deck end");}
        check(!m.canUseAbility(A,source,"tempting-tune"),"Tempting Tune did not consume source use");
    }}
    static void invalid(){for(String effect:List.of("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":0,\"battle_style\":\"UNKNOWN\"}","{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":2,\"minimum\":0,\"destination\":\"DECK_TOP_AFTER_SHUFFLE\",\"reveal\":true}","{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":0,\"destination\":\"DECK_TOP_AFTER_SHUFFLE\"}"))TopDeckLookTest.rejects(()->ZoneSelectionEffect.from(EffectRegistry.standard().parse(JsonParser.parseString(effect).getAsJsonObject())));}
}
