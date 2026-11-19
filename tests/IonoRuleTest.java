import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class IonoRuleTest {
    static final UUID A=new UUID(0,7561),B=new UUID(0,7562),WATCH=new UUID(0,7563);
    static final List<String> PRINTS=List.of("sv2_en_185","sv2_en_254","sv2_en_269","sv4-5_en_080","sv4-5_en_237","svbsp_en_124");
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static ServerMatch fixture(String id){var profile=DATA.trainer(id).orElseThrow();TrainerProgramRules.validate(profile.effectProgram());
        var m=ComposedTrainerIntegrationTest.fixture(profile);m.endTurn(A);m.endTurn(B);return m;}
    static CardInstance iono(ServerMatch m){return m.privateView(A).hand().stream().filter(c->c.definitionId().equals("component")).findFirst().orElseThrow();}
    public static void main(String[] args) {
        bundledDecks();
        for(String id:PRINTS)for(int empty=0;empty<3;empty++)resolves(id,empty);
        unequalPrizes();emptyHands();emptyDeck();schema();marnie();
        System.out.println("IonoRuleTest passed: 6 bundled printings, 18 both/one-hand scenarios; original deck order, prize-based draw, zero-effect rejection, empty deck, quotas and stale requests");
    }
    static void marnie() {
        for(String id:List.of("swsh1_en_169","swsh3-5_en_056","swshalt_en_021","swshbsp_en_120")) {
            var m=fixture(id);
            var trainer=iono(m);
            for(UUID owner:List.of(A,B)) {
                var prizes=GeneratedRuleBatch49Test.zone(m,owner,"prizes");
                var discard=GeneratedRuleBatch49Test.zone(m,owner,"discard");
                while(prizes.size()>1)discard.add(prizes.removeLast());
            }
            var topA=List.copyOf(m.privateView(A).deck());
            var topB=List.copyOf(m.privateView(B).deck());
            m.playTrainer(A,trainer.instanceId(),null);
            check(m.privateView(A).hand().equals(topA.subList(topA.size()-5,topA.size()).reversed()),"Marnie must draw 5, independent of prizes: "+id);
            check(m.privateView(B).hand().equals(topB.subList(topB.size()-4,topB.size()).reversed()),"Marnie must draw 4 for opponent: "+id);
            check(!m.mayPlaySupporter(A),"Marnie failed to consume supporter allowance");
        }
    }
    static void bundledDecks() {
        var db=BundledRules.loadCardDefinitions();var rules=BundledRules.loadCurrentRulesets().get(CurrentFormats.CHINESE);
        for(String id:PRINTS) {
            var deck=new ArrayList<CardInstance>();
            for(int i=0;i<60;i++)deck.add(new CardInstance(new UUID(1,i+1),A,i<4?"sv1_en_084":i<6?id:"sve_en_005"));
            var report=new DeckValidator(db,DATA).validate(rules,deck);
            check(report.issues().stream().noneMatch(i->i.severity()==DeckIssue.Severity.ERROR),id+": "+report.issues());
        }
    }
    static void unequalPrizes() {
        var m=fixture(PRINTS.getFirst());
        // Fixture-only setup: distinguish each player's remaining Prize count, rather than always testing 6/6.
        for(UUID owner:List.of(A,B)) {
            var prizes=GeneratedRuleBatch49Test.zone(m,owner,"prizes");
            var discard=GeneratedRuleBatch49Test.zone(m,owner,"discard");
            while(prizes.size()>(owner.equals(A)?2:5))discard.add(prizes.removeLast());
        }
        m.playTrainer(A,iono(m).instanceId(),null);
        check(m.privateView(A).hand().size()==2&&m.privateView(B).hand().size()==5,"used the same Prize count for both players");
    }
    static void clear(ServerMatch m,UUID owner,UUID keep){var cards=m.privateView(owner).hand().stream().map(CardInstance::instanceId).filter(id->!id.equals(keep)).toList();
        if(!cards.isEmpty())m.discardHandCardsByEffect(A,owner,cards);}
    static void resolves(String id,int empty) {
        var m=fixture(id);var trainer=iono(m);
        if(empty==1)clear(m,A,trainer.instanceId());if(empty==2)clear(m,B,null);
        // Use immutable lists rather than introspecting the server's mutable player state.
        var decks=Map.of(A,List.copyOf(m.privateView(A).deck()),B,List.copyOf(m.privateView(B).deck()));
        var hands=Map.of(A,m.privateView(A).hand().stream().filter(c->!c.equals(trainer)).toList(),B,List.copyOf(m.privateView(B).hand()));
        var session=MatchSession.multiplayer(m,A,B);session.addSpectator(WATCH);
        var before=m.clientView(A);var rng=m.randomTranscript();
        for(int i=0;i<3;i++)check(m.canPlayTrainer(A,trainer.instanceId(),null),"legal Iono rejected");
        check(before.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"preflight changed hand or RNG");
        var v=session.view(A,A);var action=v.options().stream().filter(o->o.cardKeys().contains("hand:"+trainer.instanceId())&&o.labelZh().startsWith("使用")).findFirst().orElseThrow();
        CopyAttackTest.reject(()->session.submit(B,A,v.revision(),action.index()));
        session.submit(A,A,v.revision(),action.index());CopyAttackTest.reject(()->session.submit(A,A,v.revision(),action.index()));
        for(UUID owner:List.of(A,B)) {
            int n=m.privateView(owner).prizes().size();var old=decks.get(owner);var now=m.privateView(owner);
            check(now.hand().equals(old.subList(old.size()-n,old.size()).reversed()),"did not draw own prize count from original top: "+id+" empty="+empty+" owner="+owner+" prizes="+n+" events="+m.replayTranscript().stream().skip(Math.max(0,m.replayTranscript().size()-20)).toList());
            check(now.deck().subList(hands.get(owner).size(),now.deck().size()).equals(old.subList(0,old.size()-n)),"shuffled original deck");
            check(new HashSet<>(now.deck().subList(0,hands.get(owner).size())).equals(new HashSet<>(hands.get(owner))),"hand not moved intact to bottom");
        }
        check(m.privateView(A).discard().contains(trainer)&&!m.privateView(A).deck().contains(trainer),"Iono returned itself or disappeared");
        check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A)&&!m.mayPlaySupporter(A),"turn/supporter quota/continuation incorrect");
        var publicJson=new com.google.gson.Gson().toJson(session.view(WATCH,null));
        for(var card:m.privateView(A).hand())check(!publicJson.contains(card.instanceId().toString()),"drawn card identity leaked to spectator");
    }
    static void emptyHands() {
        var m=fixture(PRINTS.getFirst());var trainer=iono(m);clear(m,A,trainer.instanceId());clear(m,B,null);
        var before=m.clientView(A);var rng=m.randomTranscript();
        check(!m.canPlayTrainer(A,trainer.instanceId(),null),"Iono alone counted as a hand to move");
        CopyAttackTest.reject(()->m.playTrainer(A,trainer.instanceId(),null));
        check(before.equals(m.clientView(A))&&rng.equals(m.randomTranscript())&&m.mayPlaySupporter(A),"failed play consumed source/quota/RNG");
    }
    static void emptyDeck() {
        var m=fixture(PRINTS.getFirst());m.drawCardsByEffect(A,A,60);var trainer=iono(m);clear(m,A,trainer.instanceId());
        m.playTrainer(A,trainer.instanceId(),null);
        check(m.privateView(A).hand().isEmpty()&&m.result()==null&&m.privateView(B).hand().size()==6,"empty deck effect draw failed or caused premature deck-out");
    }
    static void schema() {
        for(String extra:List.of(",\"amount\":6",",\"minimum\":0",",\"effects\":[{\"type\":\"draw\",\"amount\":1}]"))
            CopyAttackTest.reject(()->EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"shuffle_both_hands_bottom_draw_prizes\",\"target\":\"SELF\""+extra+"}").getAsJsonObject()));
    }
}
