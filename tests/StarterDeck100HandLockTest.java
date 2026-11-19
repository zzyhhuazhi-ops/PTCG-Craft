import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

public final class StarterDeck100HandLockTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static String id(int n){return StarterDeck100PokemonTest.id(n);}
    static void check(boolean ok,String why){StarterDeck100PokemonTest.check(ok,why);}
    public static void main(String[] args){for(String clear:List.of("SWITCH","TURN","IMMUNE"))beak(clear);mountain();shadow();barrage(false);barrage(true);System.out.println("StarterDeck100HandLockTest passed: hand-only attachment restriction, effect/manual paths, source zones, switch/expiry/immunity, player lock survives switch, Stadium/Special distinction and distinct counter recipients");}
    static void beak(String clear){
        var m=StarterDeck100PokemonTest.game(clear.equals("IMMUNE")?173:1,160);var target=m.activePokemonView(A).pokemonId();
        if(clear.equals("IMMUNE"))StarterDeck100EnergyTest.attach(m,A,A,"sd100_cn_grass_energy",target);
        m.endTurn(A);StarterDeck100GuardTest.attackOpponent(m,160,0);var attempted=StarterDeck100PokemonTest.hand(m,"sd100_cn_grass_energy");boolean immune=clear.equals("IMMUNE");
        check(m.canAttachEnergy(A,attempted.instanceId(),target)==immune,"Beak ignored hand lock/protection");
        if(!immune){
            TopDeckLookTest.rejects(()->m.attachEnergy(A,attempted.instanceId(),target));
            check(m.privateView(A).hand().contains(attempted),"rejected manual attach lost Energy");
            m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,attempted.instanceId(),A,target);
            check(m.privateView(A).discard().contains(attempted)&&!m.activePokemonView(A).attachedEnergyIds().contains(attempted.instanceId())&&!m.energyAttached(A),"Pokemon hand lock must discard effect attempt without attachment/quota");
        }
        var e=StarterDeck100PokemonTest.hand(m,"sd100_cn_grass_energy");
        var deckEnergy=m.privateView(A).deck().stream().filter(c->c.definitionId().equals("sd100_cn_grass_energy")).findFirst().orElseThrow();
        m.attachEnergyByEffect(A,A,EffectSourceZone.DECK,deckEnergy.instanceId(),A,target);
        check(m.canAttachEnergy(A,e.instanceId(),m.benchPokemonView(A).getFirst().pokemonId()),"Beak locked other Pokemon");
        if(clear.equals("SWITCH")){m.switchActiveByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId());m.switchActiveByEffect(A,A,target);}
        if(clear.equals("TURN")){m.endTurn(A);m.endTurn(B);}
        m.attachEnergy(A,e.instanceId(),target);check(m.activePokemonView(A).attachedEnergyIds().contains(e.instanceId()),"Beak did not clear");
    }
    static void mountain(){var m=StarterDeck100PokemonTest.game(160,310);StarterDeck100PokemonTest.charge(m,1);var deck=m.privateView(A).deck();var top=deck.subList(deck.size()-3,deck.size());int rng=m.randomTranscript().size();m.attack(A,"mountain-swing");check(m.lastAttack().finalDamage()==180&&m.privateView(A).discard().containsAll(top)&&m.privateView(A).deck().size()==deck.size()-3&&m.randomTranscript().size()==rng,"Mountain Swing mill/damage");}
    static void shadow(){var m=StarterDeck100PokemonTest.game(1,203,"sm1_en_136","sv1_en_167");var special=StarterDeck100PokemonTest.hand(m,"sm1_en_136");var stadium=StarterDeck100PokemonTest.hand(m,"sv1_en_167");m.endTurn(A);StarterDeck100GuardTest.attackOpponent(m,203,0);var active=m.activePokemonView(A).pokemonId();var basic=StarterDeck100PokemonTest.hand(m,"sd100_cn_grass_energy");check(m.trainerPlayLocked(A,stadium.definitionId()),"Shadow Mist missed Stadium");TopDeckLookTest.rejects(()->m.playTrainer(A,stadium.instanceId(),null));check(!m.canAttachEnergy(A,special.instanceId(),active)&&m.canAttachEnergy(A,basic.instanceId(),active),"Shadow Mist special/basic distinction");TopDeckLookTest.rejects(()->m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,special.instanceId(),A,active));m.switchActiveByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId());check(!m.canAttachEnergy(A,special.instanceId(),m.activePokemonView(A).pokemonId()),"switch removed player restriction");m.discardHandCardsByEffect(A,A,List.of(special.instanceId()));m.attachEnergyByEffect(A,A,EffectSourceZone.DISCARD,special.instanceId(),A,active);m.attachEnergy(A,basic.instanceId(),active);m.endTurn(A);check(!m.trainerPlayLocked(A,stadium.definitionId()),"player restriction survived restricted turn end");m.endTurn(B);var second=StarterDeck100PokemonTest.hand(m,"sm1_en_136");m.attachEnergy(A,second.instanceId(),active);m.playTrainer(A,stadium.instanceId(),null);}
    static void barrage(boolean one){var m=StarterDeck100PokemonTest.game(203);if(one){m.knockOutPokemonByEffect(A,B,m.benchPokemonView(B).getFirst().pokemonId());StarterDeck100CombatTest.settleKnockouts(m);}var active=m.activePokemonView(B).pokemonId();var bench=one?null:m.benchPokemonView(B).getFirst().pokemonId();StarterDeck100PokemonTest.attack(m,1);StarterDeck100PokemonTest.choosePokemon(m,active);if(!one){check(m.activePokemonView(B).damage()==0,"Astral Barrage applied before complete distinct selection");var p=m.effectPokemonPrompt(A);TopDeckLookTest.rejects(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),active));StarterDeck100PokemonTest.choosePokemon(m,bench);check(StarterDeck100PokemonTest.view(m,B,bench).damage()==50,"Astral Barrage skipped second recipient");}check(m.activePokemonView(B).damage()==50&&m.lastAttack().finalDamage()==0,"Astral Barrage wrong counters");}
}
