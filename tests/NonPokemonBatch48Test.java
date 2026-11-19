import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch48Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("sm7_en_140","sm7_en_150");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static ServerMatch aiGame(String id){return GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.GRASS,EnergyType.COLORLESS,NonPokemonBatch2Test.DATA.trainer(id).orElseThrow(),GeneratedRuleBatch8Test.hit(100,false));}
    static void play(ServerMatch m){NonPokemonBatch37Test.play(m);}
    static void choose(ServerMatch m,List<UUID> ids){NonPokemonBatch37Test.choose(m,ids);}
    static void settle(ServerMatch m){NonPokemonBatch2Test.settle(m);}
    static void hidden(ServerMatch m){NonPokemonBatch37Test.hidden(m);check(m.effectCardReveal()==null,"Revealed private remainder");}
    public static void main(String[] args){nav();underground();empty();System.out.println("NonPokemonBatch48Test passed: Pokemon/basic/Special Energy eligibility, optional and empty-match search, only selected card revealed, exact remaining top/bottom order, no shuffle, short/empty decks and mandatory bottom selection");}
    static void nav(){for(String kind:List.of("basic","evolved","rulebox","energy","special","skip","miss"))for(boolean shortDeck:List.of(false,true)){
        var m=aiGame(IDS.get(0));if(shortDeck)m.drawCardsByEffect(A,A,m.privateView(A).deck().size());
        boolean take=!Set.of("skip","miss").contains(kind);
        if(!shortDeck){NonPokemonBatch45Test.top(m,A,"tool");NonPokemonBatch45Test.top(m,A,"tool");}
        var intended=NonPokemonBatch45Test.top(m,A,take?kind:kind.equals("skip")?"basic":"tool");
        var before=List.copyOf(m.privateView(A).deck());var opponent=List.copyOf(m.privateView(B).deck());var window=before.subList(Math.max(0,before.size()-3),before.size());int random=m.randomTranscript().size();var hand=List.copyOf(m.privateView(A).hand());
        play(m);var q=m.effectCardPrompt(A);hidden(m);
        if(!q.labelZh().contains("按选择顺序")){
            check(new HashSet<>(q.inspectedCards()).equals(new HashSet<>(window)),"Nav concealed ineligible inspected cards or widened window");
            check(q.candidates().stream().noneMatch(c->c.definitionId().equals("tool")),"Nav offered a Trainer");
            choose(m,take?List.of(intended.instanceId()):List.of());
        } else check(kind.equals("miss"),"Nav skipped usable choice");
        if(take){var reveal=m.effectCardReveal();check(reveal!=null&&reveal.cardIds().equals(List.of(intended.definitionId()))&&m.privateView(A).deck().equals(before),"Nav revealed unchosen window or transferred before reveal");m.acknowledgeEffectReveal(reveal.confirmingPlayer(),reveal.id());}
        var rest=new ArrayList<>(window);if(take)rest.remove(intended);var order=new ArrayList<>(rest);Collections.reverse(order);
        if(!rest.isEmpty()){var reorder=m.effectCardPrompt(A);hidden(m);check(new HashSet<>(reorder.inspectedCards()).equals(new HashSet<>(rest)),"Nav reorder included an uninspected fourth card");choose(m,order.stream().map(CardInstance::instanceId).toList());}
        settle(m);var expected=new ArrayList<>(before);expected.removeAll(window);var reversed=new ArrayList<>(order);Collections.reverse(reversed);expected.addAll(reversed);
        check(m.privateView(A).deck().equals(expected)&&m.privateView(B).deck().equals(opponent)&&m.randomTranscript().size()==random,"Nav wrong remainder or unwanted shuffle");
        check(m.privateView(A).hand().stream().filter(c->!hand.contains(c)).count()==(take?1:0)&&m.privateView(A).hand().contains(intended)==take,"Nav took wrong cards");
    }}
    static void underground(){for(int size:List.of(1,2,3,4,10)){
        var m=aiGame(IDS.get(1));m.drawCardsByEffect(A,A,m.privateView(A).deck().size()-size);var before=List.copyOf(m.privateView(A).deck());var opponent=List.copyOf(m.privateView(B).deck());var window=before.subList(0,Math.min(4,size));var taken=window.subList(0,Math.min(2,size));int random=m.randomTranscript().size();
        play(m);var q=m.effectCardPrompt(A);hidden(m);check(new HashSet<>(q.inspectedCards()).equals(new HashSet<>(window))&&q.minimum()==Math.min(2,size),"Underground wrong bottom window or narrowed count");
        GeneratedRuleBatch8Test.reject(()->choose(m,List.of()));choose(m,taken.stream().map(CardInstance::instanceId).toList());
        var rest=new ArrayList<>(window);rest.removeAll(taken);Collections.reverse(rest);
        if(!rest.isEmpty()){var reorder=m.effectCardPrompt(A);hidden(m);check(new HashSet<>(reorder.inspectedCards()).equals(new HashSet<>(rest)),"Underground reorder widened its original bottom window");choose(m,rest.stream().map(CardInstance::instanceId).toList());}
        settle(m);var expected=new ArrayList<>(before);expected.removeAll(window);expected.addAll(0,rest);
        check(m.privateView(A).deck().equals(expected)&&m.privateView(A).hand().containsAll(taken)&&m.privateView(B).deck().equals(opponent)&&m.randomTranscript().size()==random,"Underground wrong remaining bottom order, destination or shuffle");
    }}
    static void empty(){for(String id:IDS){var m=aiGame(id);m.drawCardsByEffect(A,A,m.privateView(A).deck().size());check(!m.canPlayTrainer(A,NonPokemonBatch37Test.card(m,A,"trainer").instanceId(),null),"Empty deck-only window played");}}
}
