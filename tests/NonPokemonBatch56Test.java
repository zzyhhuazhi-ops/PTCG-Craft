import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.*;

public final class NonPokemonBatch56Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> KAMADO=List.of("swsh10_en_149","swsh10_en_187","swsh10_en_205"),SIGHTSEER=List.of("sm8_en_189","sc_csm1cc_183_sr","smalt_en_140");
    static final List<String> IDS=List.of(KAMADO.get(0),KAMADO.get(1),KAMADO.get(2),SIGHTSEER.get(0),SIGHTSEER.get(1),SIGHTSEER.get(2));
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static ServerMatch aiGame(String id){return NonPokemonBatch55Test.aiGame(id);}
    static void choose(ServerMatch m,UUID p,List<CardInstance> cards){var q=m.effectCardPrompt(p);m.submitEffectCards(p,q.resolutionId(),q.step(),cards.stream().map(CardInstance::instanceId).toList());}
    public static void main(String[] args){retention();System.out.println("NonPokemonBatch56Test passed: all EN/CN printings and both owners; exact retained card identities, no early draws, atomic invalid selection; Kamado one-card requirement and empty-deck discard; Sightseer zero-to-four kept, mandatory actual draw, empty-deck rejection and partial draw");}
    static void retention(){for(String id:IDS)for(UUID owner:List.of(A,B))for(int handSize:List.of(0,1,4,6))for(int deckSize:List.of(0,1,8))for(boolean keepMaximum:List.of(false,true)){
        boolean kamado=KAMADO.contains(id);if(kamado&&!keepMaximum)continue;var m=aiGame(id);if(owner.equals(B))m.endTurn(A);UUID other=owner.equals(A)?B:A;
        m.drawCardsByEffect(owner,owner,m.privateView(owner).deck().size()-deckSize);var source=NonPokemonBatch55Test.card(m,owner,"trainer");var hand=m.privateView(owner).hand().stream().filter(c->!c.equals(source)).limit(handSize).toList();m.discardHandCardsByEffect(owner,owner,m.privateView(owner).hand().stream().filter(c->!c.equals(source)&&!hand.contains(c)).map(CardInstance::instanceId).toList());
        var deck=List.copyOf(m.privateView(owner).deck());var otherHand=List.copyOf(m.privateView(other).hand());var discarded=new HashSet<>(m.privateView(owner).discard());boolean allowed=kamado?handSize>0&&(handSize>1||deckSize>0):deckSize>0;
        check(m.canPlayTrainer(owner,source.instanceId(),null)==allowed,"Wrong legal state "+id+" hand="+handSize+" deck="+deckSize);if(!allowed){GeneratedRuleBatch8Test.reject(()->m.playTrainer(owner,source.instanceId(),null));check(m.privateView(owner).hand().contains(source)&&m.privateView(owner).deck().equals(deck),"Rejected play changed state");continue;}
        m.playTrainer(owner,source.instanceId(),null);int retain=kamado?1:keepMaximum?Math.min(4,handSize):0;var kept=hand.subList(0,retain);
        if(handSize>0){var q=m.effectCardPrompt(owner);check(new HashSet<>(q.candidates()).equals(new HashSet<>(hand))&&q.minimum()==(kamado?1:0)&&q.maximum()==(kamado?1:Math.min(handSize,4)),"Wrong private retain window");GeneratedRuleBatch8Test.reject(()->m.effectCardPrompt(other));check(m.privateView(owner).deck().equals(deck),"Drew before retaining/discarding");GeneratedRuleBatch8Test.reject(()->choose(m,owner,List.of(source)));if(kamado)GeneratedRuleBatch8Test.reject(()->choose(m,owner,List.of()));else if(handSize>4)GeneratedRuleBatch8Test.reject(()->choose(m,owner,hand.subList(0,5)));check(m.privateView(owner).hand().equals(hand)&&m.privateView(owner).deck().equals(deck),"Invalid retention changed cards");choose(m,owner,kept);}
        NonPokemonBatch2Test.settle(m);int draw=Math.min(deckSize,kamado?4:5-retain);var expected=new HashSet<>(kept);expected.addAll(deck.subList(deck.size()-draw,deck.size()));for(var c:hand)if(!kept.contains(c))discarded.add(c);discarded.add(source);
        check(new HashSet<>(m.privateView(owner).hand()).equals(expected)&&m.privateView(owner).deck().equals(deck.subList(0,deck.size()-draw))&&new HashSet<>(m.privateView(owner).discard()).equals(discarded),"Wrong exact retention/discard/draw "+id);check(m.privateView(other).hand().equals(otherHand),"Retention affected opponent hand");
    }}
}
