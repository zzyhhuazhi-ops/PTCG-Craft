import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch47Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("xy2_en_098","swsh6_en_137","sm3_en_122");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static ServerMatch aiGame(String id){return NonPokemonBatch37Test.game(id);}
    static void play(ServerMatch m){NonPokemonBatch37Test.play(m);}
    static void choose(ServerMatch m,List<UUID> ids){NonPokemonBatch37Test.choose(m,ids);}
    static void hidden(ServerMatch m){NonPokemonBatch37Test.hidden(m);check(m.effectCardReveal()==null,"Private deck inspection was publicly revealed");}
    static void settle(ServerMatch m){NonPokemonBatch2Test.settle(m);}
    public static void main(String[] args){shovel();orders();empty();System.out.println("NonPokemonBatch47Test passed: either owner's optional top discard, private window, untouched other deck, bottom-to-top order, inspect-before-shuffle, all-or-zero reorder and short/empty deck boundaries");}
    static void shovel(){for(UUID owner:List.of(A,B))for(boolean discard:List.of(false,true))for(boolean shortDeck:List.of(false,true)){
        var m=aiGame(IDS.get(0));if(shortDeck)m.drawCardsByEffect(A,owner,m.privateView(owner).deck().size()-1);
        var before=List.copyOf(m.privateView(owner).deck());var other=List.copyOf(m.privateView(owner.equals(A)?B:A).deck());var top=before.getLast();
        int random=m.randomTranscript().size();play(m);NonPokemonBatch37Test.option(m,owner.equals(A)?0:1);var q=m.effectCardPrompt(A);hidden(m);
        check(q.candidates().equals(List.of(top)),"Shovel inspected wrong owner's deck");choose(m,discard?List.of(top.instanceId()):List.of());settle(m);
        check(m.privateView(owner).deck().equals(before.subList(0,before.size()-(discard?1:0)))&&m.privateView(owner).discard().contains(top)==discard,"Shovel discarded from wrong player or failed to preserve top");
        check(m.privateView(owner.equals(A)?B:A).deck().equals(other)&&m.randomTranscript().size()==random,"Shovel changed other deck or shuffled");
    }}
    static void orders(){for(String id:IDS.subList(1,3))for(boolean shortDeck:List.of(false,true))for(boolean shuffle:List.of(false,true)){
        if(shuffle&&id.equals(IDS.get(1)))continue;var m=aiGame(id);if(shortDeck)m.drawCardsByEffect(A,A,m.privateView(A).deck().size()-2);
        var before=List.copyOf(m.privateView(A).deck());var other=List.copyOf(m.privateView(B).deck());boolean bottom=id.equals(IDS.get(1));int n=Math.min(before.size(),bottom?3:4);
        var window=new ArrayList<>(bottom?before.subList(0,n):before.subList(before.size()-n,before.size()));if(!bottom)Collections.reverse(window);
        int random=m.randomTranscript().size();play(m);hidden(m);var q=m.effectCardPrompt(A);
        check(q.inspectedCards().equals(window)&&new HashSet<>(q.candidates()).equals(new HashSet<>(window)),"Wrong private bottom/top window order");
        if(!bottom){check(q.minimum()==0&&q.labelZh().contains("不选"),"Shuffle alternative is not disclosed after inspection");GeneratedRuleBatch8Test.reject(()->choose(m,List.of(q.candidates().getFirst().instanceId())));check(m.privateView(A).deck().equals(before),"Rejected partial reorder mutated deck");var first=q.candidates().getFirst().instanceId();m.toggleEffectCard(A,q.resolutionId(),q.step(),first);check(!m.canConfirmEffectCards(A),"UI/AI offered invalid partial reorder confirmation");m.toggleEffectCard(A,q.resolutionId(),q.step(),first);check(m.canConfirmEffectCards(A),"UI/AI omitted zero-card shuffle confirmation");}
        var ordered=new ArrayList<>(window);Collections.reverse(ordered);choose(m,shuffle?List.of():ordered.stream().map(CardInstance::instanceId).toList());settle(m);
        if(shuffle){check(new HashSet<>(m.privateView(A).deck()).equals(new HashSet<>(before))&&m.randomTranscript().size()>random,"Rotom lost cards or did not shuffle whole deck");}
        else {var expected=new ArrayList<>(before);expected.removeAll(window);var reversed=new ArrayList<>(ordered);Collections.reverse(reversed);expected.addAll(reversed);check(m.privateView(A).deck().equals(expected)&&m.randomTranscript().size()==random,"Wrong order/destination or unintended shuffle");}
        check(m.privateView(B).deck().equals(other),"Private reorder changed opponent's deck");
    }}
    static void empty(){for(String id:IDS){var m=aiGame(id);m.drawCardsByEffect(A,A,m.privateView(A).deck().size());m.drawCardsByEffect(A,B,m.privateView(B).deck().size());check(!m.canPlayTrainer(A,NonPokemonBatch37Test.card(m,A,"trainer").instanceId(),null),"Empty inspection played");}
        var m=aiGame(IDS.get(0));m.drawCardsByEffect(A,A,m.privateView(A).deck().size());play(m);GeneratedRuleBatch8Test.reject(()->NonPokemonBatch37Test.option(m,0));NonPokemonBatch37Test.option(m,1);choose(m,List.of());settle(m);
    }
}
