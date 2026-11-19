import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch15Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){ghetsis();morty();System.out.println("NonPokemonBatch15Test passed: forced all-Item shuffle, actual count draw including zero, typed effect-KO history and previous-turn expiry");}
    static void ghetsis(){
        for(boolean empty:List.of(false,true)){
            var m=GeneratedRuleBatch23Test.fixture(NonPokemonBatch2Test.DATA.trainer("bw9_en_101").orElseThrow(),0,EnergyType.GRASS);
            var items=m.privateView(B).hand().stream().filter(c->c.definitionId().equals("item")).map(CardInstance::instanceId).toList();
            if(empty)m.discardHandCardsByEffect(A,B,items);
            var before=List.copyOf(m.privateView(B).hand());int own=m.privateView(A).hand().size(),deck=m.privateView(A).deck().size();
            m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);
            check(m.privateView(B).hand().equals(before),"Ghetsis changed hand before acknowledgement");NonPokemonBatch14Test.reveal(m);
            if(!empty){
                var q=m.effectCardPrompt(A);check(q.minimum()==items.size()&&q.maximum()==items.size(),"Ghetsis did not force all Items");
                check(new HashSet<>(q.candidates().stream().map(CardInstance::instanceId).toList()).equals(new HashSet<>(items)),"Ghetsis accepted non-Items");
                GeneratedRuleBatch8Test.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),items.subList(1,items.size())));
                m.submitEffectCards(A,q.resolutionId(),q.step(),items);
            }
            NonPokemonBatch2Test.settle(m);int count=empty?0:items.size();
            check(m.privateView(A).hand().size()==own-1+Math.min(count,deck),"Ghetsis draw did not use actual shuffled count");
            check(m.privateView(B).hand().size()==before.size()-count,"Ghetsis wrong opponent hand size");
            if(!empty)check(m.privateView(B).deck().stream().map(CardInstance::instanceId).toList().containsAll(items),"Ghetsis lost selected physical cards");
        }
    }
    static void morty(){
        for(EnergyType type:List.of(EnergyType.PSYCHIC,EnergyType.GRASS)){
            var m=NonPokemonBatch2Test.game("sm8_en_186",type,1);var card=NonPokemonBatch2Test.trainer(m);
            check(!m.canPlayTrainer(A,card.instanceId(),null),"Morty played with no knockout");
            m.endTurn(A);m.knockOutPokemonByEffect(B,A,m.benchPokemonView(A).getFirst().pokemonId());UnrestrictedAttackPrizeTest.take(m,B);m.endTurn(B);
            check(m.canPlayTrainer(A,card.instanceId(),null)==(type==EnergyType.PSYCHIC),"Morty ignored knocked-out Pokemon type or effect KO");
            if(type!=EnergyType.PSYCHIC)continue;
            int hand=m.privateView(B).hand().size();m.playTrainer(A,card.instanceId(),null);NonPokemonBatch14Test.reveal(m);
            var q=m.effectCardPrompt(A);check(q.minimum()==2&&q.maximum()==2,"Morty does not require two available cards");
            m.submitEffectCards(A,q.resolutionId(),q.step(),q.candidates().subList(0,2).stream().map(CardInstance::instanceId).toList());NonPokemonBatch2Test.settle(m);
            check(m.privateView(B).hand().size()==hand-2,"Morty did not shuffle two cards");
            m.endTurn(A);m.endTurn(B);check(!m.canPlayTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null),"Morty retained stale knockout history");
        }
    }
}
