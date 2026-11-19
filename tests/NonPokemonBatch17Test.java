import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch17Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){roseanne();pokedex();quickBall();System.out.println("NonPokemonBatch17Test passed: all fifteen nonempty recovery combinations, exact category boundaries, private top-five reorder with short deck, mandatory Quick Ball cost");}
    static void roseanne(){
        for(int mask=1;mask<16;mask++){
            var m=GeneratedRuleBatch23Test.fixture(NonPokemonBatch2Test.DATA.trainer("swsh9_en_148").orElseThrow(),0,EnergyType.GRASS);
            var ids=List.of("basic","tool","stadium","special");var cards=ids.stream().map(id->DynamicPassiveModifierTest.hand(m,A,id)).toList();
            m.discardHandCardsByEffect(A,A,cards.stream().map(CardInstance::instanceId).toList());m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);
            var choice=m.effectChoicePrompt(A);check(choice.optionsZh().size()==15,"Roseanne lacks a nonempty category combination");m.chooseEffectOption(A,choice.resolutionId(),choice.step(),mask-1);
            for(int i=0;i<4;i++)if((mask&(1<<i))!=0){var q=m.effectCardPrompt(A);check(q.minimum()==1&&q.maximum()==1&&q.candidates().equals(List.of(cards.get(i))),"Roseanne mixed category or allowed multiple recovery");m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(cards.get(i).instanceId()));}
            NonPokemonBatch2Test.settle(m);for(int i=0;i<4;i++)check(((mask&(1<<i))!=0?m.privateView(A).deck():m.privateView(A).discard()).contains(cards.get(i)),"Roseanne recovered an unchosen category or lost a chosen card");
        }
        var empty=GeneratedRuleBatch23Test.fixture(NonPokemonBatch2Test.DATA.trainer("swsh9_en_148").orElseThrow(),0,EnergyType.GRASS);check(!empty.canPlayTrainer(A,NonPokemonBatch2Test.trainer(empty).instanceId(),null),"Roseanne accepted empty discard pile");
    }
    static void pokedex(){
        for(int size:List.of(2,5)){
            var m=NonPokemonBatch2Test.game("bw1_en_098",EnergyType.GRASS,1);m.drawCardsByEffect(A,A,m.privateView(A).deck().size()-size);var deck=List.copyOf(m.privateView(A).deck());m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);
            var q=m.effectCardPrompt(A);check(q.minimum()==size&&q.maximum()==size&&q.candidates().size()==size&&m.clientView(B).effectSelection()==null,"Pokedex wrong window/count or leaked private cards: "+q.minimum()+"/"+q.maximum()+"/"+q.candidates().size()+" expected "+size);
            var order=deck.stream().map(CardInstance::instanceId).toList();m.submitEffectCards(A,q.resolutionId(),q.step(),order);NonPokemonBatch2Test.settle(m);
            var actual=m.privateView(A).deck().stream().map(CardInstance::instanceId).toList();var expected=new ArrayList<>(order);Collections.reverse(expected);check(actual.equals(expected),"Pokedex did not honor top-first physical card order");
        }
    }
    static void quickBall(){
        var m=NonPokemonBatch2Test.game("swsh1_en_179",EnergyType.GRASS,1);var card=NonPokemonBatch2Test.trainer(m);NonPokemonBatch2Test.trim(m,A,1,card);check(!m.canPlayTrainer(A,card.instanceId(),null),"Quick Ball accepted no discard cost");
        var n=NonPokemonBatch2Test.game("swsh1_en_179",EnergyType.GRASS,1);n.playTrainer(A,NonPokemonBatch2Test.trainer(n).instanceId(),null);var cost=n.effectCardPrompt(A);n.submitEffectCards(A,cost.resolutionId(),cost.step(),List.of(cost.candidates().getFirst().instanceId()));var q=n.effectCardPrompt(A);check(q.minimum()==0&&q.candidates().stream().allMatch(c->n.cardDefinition(c.definitionId()).basicPokemon()),"Quick Ball found non-Basic Pokemon or forced hidden search");n.submitEffectCards(A,q.resolutionId(),q.step(),List.of());NonPokemonBatch2Test.settle(n);
    }
}
