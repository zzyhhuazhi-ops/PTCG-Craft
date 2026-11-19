import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.*;

public final class NonPokemonBatch49Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("sm11_en_188","sm12_en_196","sc_csm2bc_180_sr","sc_csm2bc_185_sr");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static ServerMatch aiGame(String id){return NonPokemonBatch37Test.game(id);}
    static void trim(ServerMatch m,UUID owner,int n){GeneratedRuleBatch35Test.trim(m,owner,n,List.of());}
    static void end(ServerMatch m,UUID owner,boolean attack){if(attack)m.attack(owner,"hit");else m.endTurn(owner);}
    static void hidden(ServerMatch m,UUID owner){try{m.effectCardPrompt(owner.equals(A)?B:A);throw new AssertionError("Other player saw private retained hand");}catch(IllegalStateException expected){}check(m.effectCardReveal()==null,"Hand was publicly revealed");}
    public static void main(String[] args){blue();lillie();shortDeck();copied();System.out.println("NonPokemonBatch49Test passed: actual end-of-turn hand sizes, both owners, attack/pass boundaries, no immediate or repeated end effect, private mandatory retain-two selection, atomic rejection, short/empty deck, archived Chinese printings and copied Supporters");}
    static void copied(){for(String id:List.of(IDS.get(0),IDS.get(1))){
        var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();c.attack=DeckDiscardTriggeredAbilityTest.attack(10,"{\"type\":\"use_top_supporter\",\"target\":\"DECK\"}");
        var m=GeneratedRuleBatch28Test.fixture(c);var source=NonPokemonBatch37Test.card(m,A,"trainer");NonPokemonBatch37Test.top(m,A,source);trim(m,A,0);m.attack(A,"hit");NonPokemonBatch2Test.settle(m);
        check(m.privateView(A).hand().size()==(id.equals(IDS.get(0))?8:2),"Copied Supporter did not schedule its turn-end effect");check(m.privateView(A).discard().contains(source),"Copied Supporter physical source moved out of discard");
    }}
    static void blue(){for(String id:List.of(IDS.get(0),IDS.get(2)))for(UUID owner:List.of(A,B))for(boolean attack:List.of(false,true))for(int n:List.of(0,2,8,10)){
        var m=aiGame(id);if(owner.equals(B))m.endTurn(A);int hand=m.privateView(owner).hand().size();var source=NonPokemonBatch37Test.card(m,owner,"trainer");
        check(m.canPlayTrainer(owner,source.instanceId(),null),"Blue rejected at current high hand size");m.playTrainer(owner,source.instanceId(),null);
        check(m.privateView(owner).hand().size()==hand-1,"Blue drew immediately");trim(m,owner,n);int deck=m.privateView(owner).deck().size();end(m,owner,attack);NonPokemonBatch2Test.settle(m);
        check(m.privateView(owner).hand().size()==Math.max(n,8)&&m.privateView(owner).deck().size()==deck-Math.max(0,8-n),"Blue used play-time count/wrong turn");
        trim(m,owner,0);m.endTurn(m.currentPlayer());NonPokemonBatch2Test.settle(m);check(m.privateView(owner).hand().size()==1,"Blue ran at opponent's end");m.endTurn(owner);NonPokemonBatch2Test.settle(m);check(m.privateView(owner).hand().size()==1,"Blue repeated next turn");
    }}
    static void lillie(){for(String id:List.of(IDS.get(1),IDS.get(3)))for(UUID owner:List.of(A,B))for(boolean attack:List.of(false,true))for(int n:List.of(0,1,2,3,8)){
        var m=aiGame(id);if(owner.equals(B))m.endTurn(A);int hand=m.privateView(owner).hand().size();m.playTrainer(owner,NonPokemonBatch37Test.card(m,owner,"trainer").instanceId(),null);
        check(m.privateView(owner).hand().size()==hand+3,"Lillie did not draw four immediately");trim(m,owner,n);var before=List.copyOf(m.privateView(owner).hand());var deck=List.copyOf(m.privateView(owner).deck());int random=m.randomTranscript().size();end(m,owner,attack);
        if(n>2){var q=m.effectCardPrompt(owner);hidden(m,owner);check(q.minimum()==2&&q.maximum()==2&&new HashSet<>(q.candidates()).equals(new HashSet<>(before)),"Lillie offered wrong cards/count");
            var keep=List.of(before.getFirst(),before.getLast());GeneratedRuleBatch8Test.reject(()->m.submitEffectCards(owner,q.resolutionId(),q.step(),List.of(keep.getFirst().instanceId())));
            check(m.privateView(owner).hand().equals(before)&&m.privateView(owner).deck().equals(deck),"Rejected keep selection moved cards");
            m.submitEffectCards(owner,q.resolutionId(),q.step(),keep.stream().map(CardInstance::instanceId).toList());NonPokemonBatch2Test.settle(m);
            check(new HashSet<>(m.privateView(owner).hand()).equals(new HashSet<>(keep)),"Wrong retained physical cards");var expected=new HashSet<>(deck);expected.addAll(before);expected.removeAll(keep);
            check(new HashSet<>(m.privateView(owner).deck()).equals(expected),"Returned cards went to wrong zone");check(m.randomTranscript().size()>random,"Lillie did not shuffle");
        }else{NonPokemonBatch2Test.settle(m);check(m.privateView(owner).hand().equals(before)&&m.privateView(owner).deck().equals(deck)&&m.randomTranscript().size()==random,"Lillie changed hand/deck with at most two cards");}
        m.endTurn(m.currentPlayer());NonPokemonBatch2Test.settle(m);int current=m.privateView(owner).hand().size();m.endTurn(owner);NonPokemonBatch2Test.settle(m);check(m.privateView(owner).hand().size()==current,"Lillie repeated in later turn");
    }}
    static void shortDeck(){for(String id:List.of(IDS.get(0),IDS.get(1)))for(int remaining:List.of(0,1,3)){
        var m=aiGame(id);m.drawCardsByEffect(A,A,m.privateView(A).deck().size()-remaining);var source=NonPokemonBatch37Test.card(m,A,"trainer");GeneratedRuleBatch35Test.trim(m,A,1,List.of(source));
        m.playTrainer(A,source.instanceId(),null);int hand=id.equals(IDS.get(0))?0:remaining;check(m.privateView(A).hand().size()==hand,"Short draw changed cards before end");m.endTurn(A);NonPokemonBatch2Test.settle(m);
        check(m.privateView(A).hand().size()==(id.equals(IDS.get(0))?remaining:Math.min(2,remaining)),"Short deck end effect incorrect");
    }}
}
