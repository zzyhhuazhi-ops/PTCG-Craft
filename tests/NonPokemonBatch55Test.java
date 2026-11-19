import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch55Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> OLEANA=List.of("swsh2_en_163","sc_cs1_5c_080_sr"),EGG=List.of("swsh10-5_en_066","swsh10-5_en_087");
    static final List<String> IDS=List.of(OLEANA.get(0),OLEANA.get(1),EGG.get(0),EGG.get(1));
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static ServerMatch aiGame(String id){return NonPokemonBatch52Test.game("none",NonPokemonBatch2Test.DATA.trainer(id).orElseThrow());}
    static CardInstance card(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static void choose(ServerMatch m,UUID p,List<CardInstance> cards){var q=m.effectCardPrompt(p);m.submitEffectCards(p,q.resolutionId(),q.step(),cards.stream().map(CardInstance::instanceId).toList());}
    public static void main(String[] args){oleana();unpaid();incubator();System.out.println("NonPokemonBatch55Test passed: paid-before-reveal Trainer-only opponent bottom return, all Trainer categories/no Trainer in hand, both owners and printings; Incubator heads/tails, empty deck/full Bench, fail-to-find and exact physical source bottom return without shuffle");}
    static void oleana(){for(String id:OLEANA)for(UUID owner:List.of(A,B))for(String kind:List.of("item","tool","trainer",NonPokemonBatch52Test.ZONE,"none")){
        var m=aiGame(id);if(owner.equals(B))m.endTurn(A);UUID other=owner.equals(A)?B:A;
        if(kind.equals("none"))m.discardHandCardsByEffect(owner,other,m.privateView(other).hand().stream().filter(c->m.cardDefinition(c.definitionId()).category()==CardCategory.TRAINER).map(CardInstance::instanceId).toList());
        var source=card(m,owner,"trainer");var payment=List.of(card(m,owner,"item"),card(m,owner,"energy"));var ownDeck=List.copyOf(m.privateView(owner).deck());var otherDeck=List.copyOf(m.privateView(other).deck());var otherHand=List.copyOf(m.privateView(other).hand());int random=m.randomTranscript().size();m.playTrainer(owner,source.instanceId(),null);
        check(m.effectCardReveal()==null,"Oleana revealed before paying cost");GeneratedRuleBatch8Test.reject(()->choose(m,owner,List.of(payment.getFirst())));check(m.privateView(owner).hand().containsAll(payment),"Partial cost mutated hand");choose(m,owner,payment);check(m.effectCardReveal()!=null,"Oleana failed reveal after payment");NonPokemonBatch37Test.reveal(m);
        CardInstance picked=kind.equals("none")?null:card(m,other,kind);if(picked!=null){var q=m.effectCardPrompt(owner);check(q.candidates().contains(picked)&&q.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).category()==CardCategory.TRAINER),"Oleana missed Trainer category or offered Energy/Pokemon");choose(m,owner,List.of(picked));}NonPokemonBatch2Test.settle(m);
        var expectedDeck=new ArrayList<>(otherDeck);var expectedHand=new ArrayList<>(otherHand);if(picked!=null){expectedDeck.addFirst(picked);expectedHand.remove(picked);}check(m.privateView(other).deck().equals(expectedDeck)&&m.privateView(other).hand().equals(expectedHand)&&m.privateView(owner).deck().equals(ownDeck),"Oleana wrong owner/destination/order");check(m.privateView(owner).discard().containsAll(payment)&&m.privateView(owner).discard().contains(source)&&m.randomTranscript().size()==random,"Oleana failed payment or shuffled deck");
    }}
    static void unpaid(){for(String id:OLEANA){var m=aiGame(id);var source=card(m,A,"trainer");var keep=card(m,A,"item");m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().filter(c->!c.equals(source)&&!c.equals(keep)).map(CardInstance::instanceId).toList());check(!m.canPlayTrainer(A,source.instanceId(),null),"Oleana accepted insufficient cost");GeneratedRuleBatch8Test.reject(()->m.playTrainer(A,source.instanceId(),null));check(m.effectCardReveal()==null&&m.privateView(A).hand().contains(source),"Unpayable Oleana revealed/spent source");}}
    static void incubator(){for(String id:EGG)for(UUID owner:List.of(A,B))for(String mode:List.of("normal","decline","empty","full")){
        var outcomes=new HashSet<Integer>();for(int shift=0;shift<24&&outcomes.size()<2;shift++){
            var m=aiGame(id);if(owner.equals(B))m.endTurn(A);if(mode.equals("empty"))m.drawCardsByEffect(owner,owner,m.privateView(owner).deck().size());if(mode.equals("full"))while(m.benchPokemonView(owner).size()<5)m.playBasicPokemonToBench(owner,card(m,owner,"basic").instanceId());if(shift>0)m.flipCoinsByEffect(owner,shift);
            var source=card(m,owner,"trainer");var before=List.copyOf(m.privateView(owner).deck());int bench=m.benchPokemonView(owner).size(),random=m.randomTranscript().size();check(m.canPlayTrainer(owner,source.instanceId(),null),"Incubator rejected legal empty/full play");m.playTrainer(owner,source.instanceId(),null);var coins=NonPokemonBatch50Test.coins(m,random);check(coins.size()==1,"Incubator wrong flip count");int result=coins.getFirst().result();outcomes.add(result);boolean heads=result==0;CardInstance chosen=null;
            if(heads&&!Set.of("empty","full").contains(mode)){var q=m.effectCardPrompt(owner);check(q.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).basicPokemon()),"Incubator offered non-basic");if(!mode.equals("decline"))chosen=q.candidates().getFirst();choose(m,owner,chosen==null?List.of():List.of(chosen));}NonPokemonBatch2Test.settle(m);
            if(heads){var expected=new HashSet<>(before);if(chosen!=null)expected.remove(chosen);check(new HashSet<>(m.privateView(owner).deck()).equals(expected)&&m.privateView(owner).discard().contains(source)&&m.benchPokemonView(owner).size()==bench+(chosen==null?0:1),"Incubator heads wrong placement/disposition");if(mode.equals("full"))check(m.privateView(owner).deck().equals(before)&&m.randomTranscript().size()==random+1,"Full Bench caused forbidden inspection/shuffle");}
            else{var expected=new ArrayList<>(before);expected.addFirst(source);check(m.privateView(owner).deck().equals(expected)&&!m.privateView(owner).discard().contains(source)&&m.benchPokemonView(owner).size()==bench&&m.randomTranscript().size()==random+1,"Incubator tails returned wrong card/end or shuffled");}
        }check(outcomes.size()==2,"Missing Incubator coin branch "+mode);
    }}
}
