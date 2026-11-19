import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch53Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("sm12_en_203","sc_csm2bc_187_sr");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static ServerMatch aiGame(String id){return NonPokemonBatch52Test.game("none",NonPokemonBatch2Test.DATA.trainer(id).orElseThrow());}
    static void choose(ServerMatch m,UUID owner,List<UUID> ids){var q=m.effectCardPrompt(owner);m.submitEffectCards(owner,q.resolutionId(),q.step(),ids);}
    public static void main(String[] args){draws();unpaid();System.out.println("NonPokemonBatch53Test passed: both printings/owners, Pokemon and Trainer draw two, basic and double Energy draw four, actual card payment before draw, invalid selection is atomic, partial deck and insufficient hand");}
    static void draws(){for(String id:IDS)for(UUID owner:List.of(A,B))for(String kind:List.of("basic","item","tool","energy","special"))for(int remaining:List.of(1,3,9)){
        var m=aiGame(id);if(owner.equals(B))m.endTurn(A);m.drawCardsByEffect(owner,owner,m.privateView(owner).deck().size()-remaining);
        var source=NonPokemonBatch52Test.card(m,owner,"trainer");var payment=NonPokemonBatch52Test.card(m,owner,kind);int hand=m.privateView(owner).hand().size();var other=List.copyOf(m.privateView((owner.equals(A)?B:A)).hand());
        check(m.canPlayTrainer(owner,source.instanceId(),null),"Roller Skater rejected payable hand");m.playTrainer(owner,source.instanceId(),null);var q=m.effectCardPrompt(owner);
        check(q.candidates().contains(payment)&&!q.candidates().contains(source),"Cost candidates missing payment or include played card");check(m.privateView(owner).deck().size()==remaining,"Drew before paying");
        GeneratedRuleBatch8Test.reject(()->choose(m,owner,List.of()));GeneratedRuleBatch8Test.reject(()->choose(m,owner,List.of(source.instanceId())));check(m.privateView(owner).hand().contains(payment)&&m.privateView(owner).deck().size()==remaining,"Invalid payment mutated state");
        choose(m,owner,List.of(payment.instanceId()));NonPokemonBatch2Test.settle(m);int draw=Math.min(remaining,Set.of("energy","special").contains(kind)?4:2);
        check(m.privateView(owner).hand().size()==hand-2+draw&&m.privateView(owner).deck().size()==remaining-draw,"Wrong actual draw for "+kind+" "+id);
        check(m.privateView(owner).discard().containsAll(List.of(source,payment))&&m.privateView((owner.equals(A)?B:A)).hand().equals(other),"Wrong source/payment destination or affected opponent hand");
    }}
    static void unpaid(){for(String id:IDS)for(UUID owner:List.of(A,B)){
        var m=aiGame(id);if(owner.equals(B))m.endTurn(A);var source=NonPokemonBatch52Test.card(m,owner,"trainer");m.discardHandCardsByEffect(owner,owner,m.privateView(owner).hand().stream().filter(c->!c.equals(source)).map(CardInstance::instanceId).toList());var deck=List.copyOf(m.privateView(owner).deck());
        check(!m.canPlayTrainer(owner,source.instanceId(),null),"Roller Skater payable with only itself in hand");GeneratedRuleBatch8Test.reject(()->m.playTrainer(owner,source.instanceId(),null));check(m.privateView(owner).hand().equals(List.of(source))&&m.privateView(owner).deck().equals(deck),"Unpayable play consumed card or drew");
    }}
}
