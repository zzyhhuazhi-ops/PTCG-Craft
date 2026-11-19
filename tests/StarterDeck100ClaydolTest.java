import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class StarterDeck100ClaydolTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static final String PSYCHIC="sd100_cn_psychic_energy";
    static void check(boolean ok,String why){StarterDeck100PokemonTest.check(ok,why);}
    public static void main(String[] args){for(int supply:List.of(0,2,4))split(supply);for(int other:List.of(0,1))noDeficit(other);shot();System.out.println("StarterDeck100ClaydolTest passed: whole-field actual Energy units, mandatory deficit, scarce/empty discard, any-host distribution, private/replay rejection and Psyshot");}
    static void split(int supply){
        var m=StarterDeck100PokemonTest.game(181,1,"sm1_en_136");
        // An opponent's Pokemon can hold an Energy card owned by A; count its actual field and units.
        StarterDeck100EnergyTest.attach(m,A,A,"sm1_en_136",m.activePokemonView(A).pokemonId());
        var doubleCard=m.activePokemonView(A).attachedEnergyIds().getFirst();
        m.moveAttachedEnergyByEffect(A,A,m.activePokemonView(A).pokemonId(),B,m.benchPokemonView(B).getFirst().pokemonId(),List.of(doubleCard));
        for(int i=0;i<2;i++)StarterDeck100EnergyTest.attach(m,A,B,"sd100_cn_grass_energy",m.activePokemonView(B).pokemonId());
        StarterDeck100PokemonTest.charge(m,0);
        var discarded=new ArrayList<CardInstance>();for(int i=0;i<supply;i++){var c=StarterDeck100PokemonTest.hand(m,PSYCHIC);m.discardHandCardsByEffect(A,A,List.of(c.instanceId()));discarded.add(c);}
        var wrong=StarterDeck100PokemonTest.hand(m,"sd100_cn_grass_energy");m.discardHandCardsByEffect(A,A,List.of(wrong.instanceId()));
        m.attack(A,"power-split");
        if(supply==0){check(!m.hasPendingCardSelection()&&m.currentPlayer().equals(B),"empty discard stalled");return;}
        var p=m.effectCardPrompt(A);check(new HashSet<>(p.candidates()).equals(new HashSet<>(discarded)),"wrong energy filter");
        var before=m.clientView(A);var rng=m.randomTranscript();
        TopDeckLookTest.rejects(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));
        check(before.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"invalid partial choice mutated state");
        TopDeckLookTest.rejects(()->m.submitEffectCards(B,p.resolutionId(),p.step(),discarded.stream().limit(2).map(CardInstance::instanceId).toList()));
        int expected=Math.min(3,supply);var selected=discarded.subList(0,expected);
        m.submitEffectCards(A,p.resolutionId(),p.step(),selected.stream().map(CardInstance::instanceId).toList());
        var d=m.effectAttachmentPrompt(A);TopDeckLookTest.rejects(()->m.confirmEffectEnergyDistribution(A,d.resolutionId(),d.step()));
        for(int i=0;i<selected.size();i++)m.assignEffectEnergyDistribution(A,d.resolutionId(),d.step(),selected.get(i).instanceId(),i%2==0?m.activePokemonView(A).pokemonId():m.benchPokemonView(A).getFirst().pokemonId());
        m.confirmEffectEnergyDistribution(A,d.resolutionId(),d.step());
        check(m.activePokemonView(A).attachedEnergyIds().size()+m.benchPokemonView(A).getFirst().attachedEnergyIds().size()==1+expected,"incorrect deficit or distribution");
        check(m.privateView(A).discard().size()==supply-expected+1,"wrong number moved from discard");
        TopDeckLookTest.rejects(()->m.submitEffectCards(A,p.resolutionId(),p.step(),selected.stream().map(CardInstance::instanceId).toList()));
    }
    static void noDeficit(int other){var m=StarterDeck100PokemonTest.game(181);var c=StarterDeck100PokemonTest.hand(m,PSYCHIC);m.discardHandCardsByEffect(A,A,List.of(c.instanceId()));if(other==1)StarterDeck100EnergyTest.attach(m,A,B,"sd100_cn_grass_energy",m.activePokemonView(B).pokemonId());StarterDeck100PokemonTest.attack(m,0);check(!m.hasPendingCardSelection()&&m.privateView(A).discard().contains(c),"no deficit still offered Energy");}
    static void shot(){var m=StarterDeck100PokemonTest.game(181);StarterDeck100PokemonTest.attack(m,1);check(m.lastAttack().finalDamage()==100,"Psyshot");}
}
