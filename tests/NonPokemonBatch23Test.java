import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.*;

public final class NonPokemonBatch23Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){catcher();lass();oak();vip();pair();shopping();System.out.println("NonPokemonBatch23Test passed: remaining HP boundary, Basic Bench count, opponent hand reset and first-turn restriction");}
    static void catcher(){
        var m=NonPokemonBatch2Test.game("swsh7_en_163",EnergyType.GRASS,1);var card=NonPokemonBatch2Test.trainer(m);var bench=m.benchPokemonView(B).getFirst();
        m.placeDamageCountersByEffect(A,B,bench.pokemonId(),(bench.hitPoints()-60)/10);
        check(!m.canPlayTrainer(A,card.instanceId(),null),"Toy Catcher accepted 60 HP");
        m.placeDamageCountersByEffect(A,B,bench.pokemonId(),1);
        check(m.canPlayTrainer(A,card.instanceId(),null),"Toy Catcher rejected 50 HP");
        m.playTrainer(A,card.instanceId(),null);var q=m.effectPokemonPrompt(A);
        check(q.targets().size()==1&&q.targets().getFirst().pokemonId().equals(bench.pokemonId()),"Toy Catcher offered wrong target");
        m.chooseEffectPokemon(A,q.resolutionId(),q.step(),bench.pokemonId());NonPokemonBatch2Test.settle(m);
        check(m.activePokemonView(B).pokemonId().equals(bench.pokemonId()),"Toy Catcher failed to switch");
    }
    static void lass(){
        for(boolean evolved:List.of(false,true)){
            var m=NonPokemonBatch2Test.game("xy10_en_103",EnergyType.GRASS,1);var card=NonPokemonBatch2Test.trainer(m);
            if(evolved){m.endTurn(A);m.evolveByEffect(B,m.benchPokemonView(B).getFirst().pokemonId(),DynamicPassiveModifierTest.hand(m,B,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);m.endTurn(B);}
            if(evolved){check(!m.canPlayTrainer(A,card.instanceId(),null),"Lass counted Evolution or Active");continue;}
            int before=m.privateView(A).hand().size();m.playTrainer(A,card.instanceId(),null);NonPokemonBatch2Test.settle(m);
            check(m.privateView(A).hand().size()==before,"Lass did not draw exactly one for Basic Bench");
        }
    }
    static void oak(){
        var m=NonPokemonBatch2Test.game("swsh7-5r_en_004",EnergyType.GRASS,1);int before=m.privateView(A).hand().size();m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);NonPokemonBatch2Test.settle(m);
        check(m.privateView(B).hand().size()==7&&m.privateView(A).hand().size()==before-1,"Imposter Oak reset wrong hand");
    }
    static void vip(){
        var m=NonPokemonBatch2Test.game("swsh8_en_225",EnergyType.GRASS,1);
        check(!m.canPlayTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null),"VIP Pass playable after own first turn");
    }
    static void shopping(){
        var m=NonPokemonBatch2Test.game("swsh7_en_157",EnergyType.GRASS,1);var tool=DynamicPassiveModifierTest.hand(m,A,"tool");var bench=m.benchPokemonView(A).getFirst().pokemonId();
        m.playTrainer(A,tool.instanceId(),bench);m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);
        m.activateStadium(A,m.clientView(A).stadium().instanceId(),m.stadiumEntrySequence());var q=m.effectCardPrompt(A);
        check(q.candidates().equals(List.of(tool)),"Shopping Center offered wrong attachment");m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(tool.instanceId()));NonPokemonBatch2Test.settle(m);
        check(m.privateView(A).hand().contains(tool)&&m.benchPokemonView(A).getFirst().attachedTools().isEmpty()&&!m.canActivateStadium(A),"Shopping Center transfer or use limit wrong");
    }
    static void pair(){
        var p=NonPokemonBatch2Test.DATA.trainer("swsh8_en_231").orElseThrow();
        check(p.pairedPlay().count()==2&&p.pairedPlay().definitionIds().contains("swsh8_en_231"),"Crossceiver metadata missing");
        var profile=new dev.hua.ptcglive.rules.TrainerProfile(p.category(),p.effect(),p.effectProgram(),p.automaticEffects(),p.endsTurn(),p.mayPlayFirstTurn(),p.inPlayPokemon(),p.activation(),p.toolActivation(),new dev.hua.ptcglive.rules.PairedTrainerPlay(2,Set.of("trainer")));
        var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.GRASS,EnergyType.GRASS,profile,GeneratedRuleBatch8Test.hit(10,false));
        var pokemon=DynamicPassiveModifierTest.hand(m,A,"basic");var tool=DynamicPassiveModifierTest.hand(m,A,"tool");m.discardHandCardsByEffect(A,A,List.of(pokemon.instanceId(),tool.instanceId()));
        var cards=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("trainer")).limit(2).map(CardInstance::instanceId).toList();
        check(cards.size()==2&&!m.canPlayTrainer(A,cards.getFirst(),null)&&!m.canPlayTrainerPair(A,List.of(cards.getFirst(),cards.getFirst())),"Crossceiver allowed single or duplicate card");
        m.playTrainerPair(A,cards);var q=m.effectCardPrompt(A);check(q.candidates().equals(List.of(pokemon)),"Crossceiver recovered Item or Tool");
        m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(pokemon.instanceId()));NonPokemonBatch2Test.settle(m);
        check(m.privateView(A).hand().contains(pokemon)&&cards.stream().allMatch(id->m.privateView(A).discard().stream().anyMatch(c->c.instanceId().equals(id))),"Crossceiver did not spend both cards and recover once");
    }
}
