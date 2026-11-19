import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch12Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){for(int prefix:List.of(0,2))receiver(prefix);absent();System.out.println("NonPokemonBatch12Test passed: reveal stops at first physical Supporter, no hidden deeper cards, acknowledgement before movement, no-Supporter full reveal/shuffle");}
    static void receiver(int prefix){
        var m=GeneratedRuleBatch23Test.fixture(NonPokemonBatch2Test.DATA.trainer("bw5_en_099").orElseThrow(),0,EnergyType.GRASS);
        m.drawCardsByEffect(A,A,m.privateView(A).deck().size());
        var supporters=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("supporter")).limit(2).toList();check(supporters.size()==2,"Fixture lacks two Supporters");
        var stack=new ArrayList<CardInstance>(supporters);for(int i=0;i<prefix;i++){String id=i==0?"energy":"basic";stack.add(m.privateView(A).hand().stream().filter(c->c.definitionId().equals(id)).findFirst().orElseThrow());}
        m.discardHandCardsByEffect(A,A,stack.stream().map(CardInstance::instanceId).toList());for(var card:stack)m.recoverDiscardCardByEffect(A,A,card.instanceId(),CardEffectDestination.DECK_TOP);
        var before=List.copyOf(m.privateView(A).deck());int hand=m.privateView(A).hand().size();
        m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);var reveal=m.effectCardReveal();
        var expected=new ArrayList<>(stack.subList(1,stack.size()));Collections.reverse(expected);
        check(reveal!=null&&reveal.cardIds().equals(expected.stream().map(CardInstance::definitionId).toList())&&m.privateView(A).deck().equals(before),"Receiver revealed beyond first Supporter or moved before acknowledgement");
        GeneratedRuleBatch8Test.reject(()->m.acknowledgeEffectReveal(A,reveal.id()));m.acknowledgeEffectReveal(B,reveal.id());NonPokemonBatch2Test.settle(m);
        check(m.privateView(A).hand().size()==hand&&m.privateView(A).hand().contains(supporters.get(1))&&!m.privateView(A).hand().contains(supporters.getFirst()),"Receiver chose wrong Supporter or incorrect count");
        check(m.privateView(A).deck().size()==before.size()-1&&m.privateView(A).deck().contains(supporters.getFirst()),"Receiver discarded remaining revealed cards");
        GeneratedRuleBatch8Test.reject(()->m.acknowledgeEffectReveal(B,reveal.id()));
    }
    static void absent(){
        var m=NonPokemonBatch2Test.game("bw5_en_099",EnergyType.GRASS,1);var before=List.copyOf(m.privateView(A).deck());int hand=m.privateView(A).hand().size();
        m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);var reveal=m.effectCardReveal();
        var reversed=new ArrayList<>(before);Collections.reverse(reversed);check(reveal.cardIds().equals(reversed.stream().map(CardInstance::definitionId).toList()),"No-Supporter deck not fully revealed");
        NonPokemonBatch2Test.settle(m);check(m.privateView(A).hand().size()==hand-1&&new HashSet<>(m.privateView(A).deck()).equals(new HashSet<>(before)),"Receiver without Supporter lost cards or drew one");
    }
}
