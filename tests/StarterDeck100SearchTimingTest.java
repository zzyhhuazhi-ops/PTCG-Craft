import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Real printed programs: turn-sensitive search, one union selection and delayed original-defender KO. */
public final class StarterDeck100SearchTimingTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static void check(boolean b,String s){StarterDeck100PokemonTest.check(b,s);}
    static String id(int n){return StarterDeck100PokemonTest.id(n);}
    public static void main(String[] args){searchCounts();photonLaser();muddyMaker();concoction();wordOfRuin();inspectTop();System.out.println("StarterDeck100SearchTimingTest passed: first-turn search, same-host attachments, Energy union, conditional draw, delayed KO and private top-card inspection");}
    static void inspectTop(){for(boolean discard:List.of(false,true)){
        var m=StarterDeck100PokemonTest.game(205);StarterDeck100PokemonTest.charge(m,0);var deck=m.privateView(A).deck();var top=deck.getLast();var random=m.randomTranscript();m.attack(A,"dig-it-up");var p=m.effectCardPrompt(A);
        check(p.inspectedCards().equals(List.of(top))&&p.candidates().equals(List.of(top))&&m.privateView(A).deck().equals(deck),"inspection must not move cards before choice");
        var session=MatchSession.multiplayer(m,A,B);UUID watcher=new UUID(0,937521);session.addSpectator(watcher);
        for(UUID viewer:List.of(B,watcher)){String json=new com.google.gson.Gson().toJson(session.view(viewer,viewer.equals(B)?B:null));check(!json.contains(top.instanceId().toString()),"private top card leaked");}
        TopDeckLookTest.rejects(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(top.instanceId())));
        TopDeckLookTest.rejects(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(deck.getFirst().instanceId())));
        m.submitEffectCards(A,p.resolutionId(),p.step(),discard?List.of(top.instanceId()):List.of());
        check(m.privateView(A).deck().equals(discard?deck.subList(0,deck.size()-1):deck)&&m.privateView(A).discard().contains(top)==discard,"top inspection shuffled/reordered or discarded wrong card");
        check(m.randomTranscript().equals(random),"inspection consumed shuffle randomness");
        TopDeckLookTest.rejects(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));
    }
        var m=StarterDeck100PokemonTest.game(205);StarterDeck100PokemonTest.charge(m,0);m.discardTopDeckCardsByEffect(A,A,m.privateView(A).deck().size());m.attack(A,"dig-it-up");check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B)&&m.phase()==MatchPhase.TURN,"empty top inspection got stuck or caused deck-out");
    }
    static void searchCounts(){for(int n:List.of(44,182)){
        var m=new ServerMatch(A,B,StarterDeck100PokemonTest.deck(A,1),StarterDeck100PokemonTest.deck(B,n),StarterDeck100CombatTest.RULE,StarterDeck100CombatTest.CARDS,StarterDeck100CombatTest.COMBAT,new ServerRandom(17));OpeningTestSupport.complete(m,A,B,0);m.endTurn(A);
        StarterDeck100EnergyTest.attach(m,B,B,n==44?"sd100_cn_grass_energy":"sd100_cn_psychic_energy",m.activePokemonView(B).pokemonId());
        for(int expected:List.of(3,1)){
            m.attack(B,n==44?"pack-call":"crescent-glow");
            if(m.hasPendingPokemonSelection()){var p=m.effectPokemonPrompt(B);m.chooseEffectPokemon(B,p.resolutionId(),p.step(),m.activePokemonView(B).pokemonId());}
            var p=m.effectCardPrompt(B);check(p.maximum()==expected,"wrong turn-sensitive maximum");
            check(p.candidates().stream().allMatch(c->n==44?m.cardDefinition(c.definitionId()).pokemonTypes().contains(EnergyType.GRASS):c.definitionId().equals("sd100_cn_psychic_energy")),"search filter mismatch");
            int before=m.activePokemonView(B).attachedEnergyIds().size();
            m.submitEffectCards(B,p.resolutionId(),p.step(),p.candidates().stream().limit(expected).map(CardInstance::instanceId).toList());if(m.effectCardReveal()!=null)m.acknowledgeEffectReveal(A,m.effectCardReveal().id());
            if(n==182){var dest=m.effectAttachmentPrompt(B);m.chooseEffectAttachmentTarget(B,dest.resolutionId(),dest.step(),m.activePokemonView(B).pokemonId());}
            check(n!=182||m.activePokemonView(B).attachedEnergyIds().size()==before+expected,"Crescent Glow split or lost attachments");m.endTurn(A);
        }
    }}
    static void photonLaser(){for(int units:List.of(4,5)){var m=StarterDeck100PokemonTest.game(182,"sm1_en_136");StarterDeck100PokemonTest.charge(m,1);UUID bench=m.benchPokemonView(A).getFirst().pokemonId();StarterDeck100EnergyTest.attach(m,A,A,"sm1_en_136",bench);if(units==5)StarterDeck100EnergyTest.attach(m,A,A,"sd100_cn_grass_energy",bench);m.attack(A,"photon-laser");check(m.lastAttack().finalDamage()==(units==5?120:30),"Photon Laser did not count all field units");}}
    static void muddyMaker(){var m=StarterDeck100PokemonTest.game(95,"sd100_cn_fighting_energy","sm1_en_136");for(String energy:List.of("sd100_cn_water_energy","sd100_cn_fighting_energy","sd100_cn_grass_energy","sm1_en_136"))StarterDeck100PokemonTest.hand(m,energy);UUID source=m.activePokemonView(A).pokemonId(),target=m.benchPokemonView(A).getFirst().pokemonId();StarterDeck100PokemonTest.ability(m,source);var p=m.effectCardPrompt(A);check(p.candidates().stream().map(CardInstance::definitionId).collect(java.util.stream.Collectors.toSet()).equals(Set.of("sd100_cn_water_energy","sd100_cn_fighting_energy")),"Muddy Maker union includes wrong Energy or omits an option");StarterDeck100PokemonTest.select(m,1);var dest=m.effectAttachmentPrompt(A);m.chooseEffectAttachmentTarget(A,dest.resolutionId(),dest.step(),target);TopDeckLookTest.rejects(()->StarterDeck100PokemonTest.ability(m,source));StarterDeck100PokemonTest.attack(m,0);check(StarterDeck100PokemonTest.view(m,A,target).damage()==20,"Earthquake did not damage own Bench");}
    static void concoction(){for(boolean empty:List.of(false,true)){var m=StarterDeck100PokemonTest.game(258);StarterDeck100PokemonTest.charge(m,0);if(empty)StarterDeck100PokemonTest.trim(m,0);int before=m.privateView(A).hand().size();m.attack(A,"concoction");if(!empty)StarterDeck100PokemonTest.select(m,1);check(m.privateView(A).hand().size()==(empty?0:before+2),"Concoction drew without discard or wrong count");}}
    static void wordOfRuin(){for(boolean switched:List.of(false,true)){var m=StarterDeck100PokemonTest.game(258);UUID old=m.activePokemonView(B).pokemonId();StarterDeck100PokemonTest.attack(m,1);check(m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(B),"Word of Ruin KO happened early");if(switched){m.switchActiveByEffect(B,B,m.benchPokemonView(B).getFirst().pokemonId());m.switchActiveByEffect(B,B,old);}m.endTurn(B);check(m.phase()==(switched?MatchPhase.TURN:MatchPhase.PRIZE_SELECTION),"Word of Ruin expiry or switch clear incorrect");if(!switched){StarterDeck100CombatTest.settleKnockouts(m);check(m.privateView(A).prizes().size()==4&&m.currentPlayer().equals(A),"delayed V KO prize/turn transition failed");}}}
}
