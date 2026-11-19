import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernTrainerEndRepairTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    public static void main(String[] args){caretaker();amarys();System.out.println("ModernTrainerEndRepairTest passed: Caretaker two prints, actual draw and exact Stadium source return; Amarys three prints current-turn hand check, switch/evolution persistence, next-turn expiry and attack/explicit endings");}
    static void caretaker(){
        var data=BundledCombatData.load();var stadium=data.trainer("sv6_en_146").orElseThrow();
        for(String id:List.of("sv6_en_144","sv6_en_203"))for(String arena:List.of("none","sv6_en_146","other-stadium"))for(int deck:List.of(0,1,2,5)){
            var m=ModernEnergyRepairTest.game(data.trainer(id).orElseThrow(),List.of(),GeneratedRuleBatch8Test.hit(10,false),EnergyType.PSYCHIC,0,Map.of(),Map.of(),Map.of("sv6_en_146",stadium,"other-stadium",stadium));
            if(!arena.equals("none"))m.playTrainer(A,ModernRuleRepair2Test.hand(m,A,arena).instanceId(),null);
            int draw=m.privateView(A).deck().size()-deck;if(draw>0)m.drawCardsByEffect(A,A,draw);
            var trainer=ModernRuleRepair2Test.hand(m,A,"trainer");var before=m.privateView(A);int rng=m.randomTranscript().size();
            if(!m.canPlayTrainer(A,trainer.instanceId(),null)){check(deck==0,"Caretaker denied nonempty deck");continue;}
            m.playTrainer(A,trainer.instanceId(),null);boolean shuffled=deck>0&&arena.equals("sv6_en_146");var after=m.privateView(A);
            check(after.hand().size()==before.hand().size()-1+Math.min(2,deck)&&after.deck().size()==deck-Math.min(2,deck)+(shuffled?1:0),"Caretaker wrong actual draw/disposition "+id+" stadium="+arena+" deck="+deck+" beforeHand="+before.hand().size()+" afterHand="+after.hand().size()+" afterDeck="+after.deck().size()+" pending="+m.hasPendingEffectChoice());
            check(after.deck().contains(trainer)==shuffled&&after.discard().contains(trainer)!=shuffled&&!after.hand().contains(trainer),"Caretaker source duplicated or wrong destination");
            check((m.randomTranscript().size()>rng)==(shuffled&&after.deck().size()>1)&&!m.hasPendingEffectChoice(),"Caretaker shuffle or continuation");
        }
    }
    static void amarys(){
        for(String id:List.of("sv8-5_en_093","sv8-5_en_132","sv8-5_en_170"))for(int size:List.of(0,4,5,8))for(boolean attackEnd:List.of(false,true)){
            var m=ModernEnergyRepairTest.game(BundledCombatData.load().trainer(id).orElseThrow(),List.of(),GeneratedRuleBatch8Test.hit(10,false),EnergyType.PSYCHIC,0);var trainer=ModernRuleRepair2Test.hand(m,A,"trainer");int hand=m.privateView(A).hand().size();
            m.playTrainer(A,trainer.instanceId(),null);check(m.privateView(A).hand().size()==hand+3&&m.privateView(A).discard().contains(trainer),"Amarys draw/source disposal");
            UUID source=m.activePokemonView(A).pokemonId();m.evolveByEffect(A,source,ModernRuleRepair2Test.hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);m.switchActiveByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId());
            var excess=m.privateView(A).hand().stream().skip(size).map(CardInstance::instanceId).toList();if(!excess.isEmpty())m.discardHandCardsByEffect(A,A,excess);
            var held=m.privateView(A).hand();check(held.size()==size,"Amarys fixture hand");
            if(attackEnd)m.attack(A,"hit");else m.endTurn(A);
            check(m.currentPlayer().equals(B)&&m.privateView(A).hand().size()==(size>=5?0:size)&&!m.hasPendingEffectChoice(),"Amarys wrong turn, threshold or lost effect after switch/evolution");
            if(size>=5)check(m.privateView(A).discard().containsAll(held),"Amarys missing discard");
            m.endTurn(B);m.drawCardsByEffect(A,A,5);int kept=m.privateView(A).hand().size();m.endTurn(A);
            check(m.privateView(A).hand().size()==kept,"Amarys incorrectly repeated next turn");
        }
    }
}
