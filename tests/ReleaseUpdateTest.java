import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.view.*;
import dev.hua.ptcglive.packs.*;
import java.util.*;

public final class ReleaseUpdateTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        opening(); reveal(); mulligan(); packs();
        System.out.println("ReleaseUpdateTest passed: real opening seven-card batches, 599/600-tick read timeout, mandatory choices preserved, recipient-only attention, mulligan timeout, release order and all-pack 64-batch payload bounds");
    }
    static void opening(){
        var game=OpeningSequenceTest.game(1,60,60);var a=OpeningSequenceTest.A;var b=OpeningSequenceTest.B;
        var session=MatchSession.multiplayer(game,a,b);
        game.callCoin(a,CoinSide.HEADS);var actor=game.pendingChoicePlayer();
        var before=session.view(actor,actor);var option=before.options().stream().filter(o->o.enabled()&&!o.labelZh().contains("认输")).findFirst().orElseThrow();
        session.submit(actor,actor,before.revision(),option.index());var after=session.view(actor,actor);
        var draws=MatchPresentation.changes(before,after).stream().filter(c->c.kind()==MatchPresentation.Kind.DRAW).toList();
        check(draws.size()==2&&draws.stream().allMatch(c->c.amount()==7),"Opening must animate two seven-card batches: "+draws);
        var state=game.clientView(actor);session.tickReadAcknowledgements(0);check(!session.tickReadAcknowledgements(6000)&&state.equals(game.clientView(actor)),"Opening placement was auto-selected");
    }
    static void reveal(){
        var game=AttackDeckWindowTest.game(0);var a=AttackDeckWindowTest.A;var b=AttackDeckWindowTest.B;
        var session=MatchSession.multiplayer(game,a,b);game.attack(a,"bug-out");
        check(!BattleAttention.key(session.view(b,b)).isEmpty()&&BattleAttention.key(session.view(a,a)).isEmpty(),"Read attention went to wrong seat");
        var before=game.clientView(b);session.tickReadAcknowledgements(20);
        check(!session.tickReadAcknowledgements(619)&&before.equals(game.clientView(b)),"Read confirmation expired before 30 seconds");
        check(session.tickReadAcknowledgements(620)&&game.effectCardReveal()==null,"Read confirmation did not expire");
        check(!session.tickReadAcknowledgements(621),"Read confirmation replayed");
        var pending=GeneratedRuleBatch44Test.fixture(GeneratedRuleBatch44Test.snack("me5-5_en_061"));
        pending.attack(GeneratedRuleBatch44Test.A,GeneratedRuleBatch44Test.snack("me5-5_en_061").id());
        var waiting=MatchSession.multiplayer(pending,GeneratedRuleBatch44Test.A,GeneratedRuleBatch44Test.B);
        var prompt=pending.effectCardPrompt(GeneratedRuleBatch44Test.A);check(prompt!=null,"Fixture must require card choice");
        waiting.tickReadAcknowledgements(0);check(!waiting.tickReadAcknowledgements(10000)&&prompt.equals(pending.effectCardPrompt(GeneratedRuleBatch44Test.A)),"Mandatory card choice was skipped");
    }
    static void mulligan(){
        for(int seed=0;seed<300;seed++){
            var game=OpeningSequenceTest.game(seed,1,40);OpeningSequenceTest.begin(game);
            if(!game.needsMulligan(OpeningSequenceTest.A)||game.needsMulligan(OpeningSequenceTest.B))continue;
            OpeningSequenceTest.setUp(game,OpeningSequenceTest.B);
            var session=MatchSession.multiplayer(game,OpeningSequenceTest.A,OpeningSequenceTest.B);
            var before=game.clientView(null);session.tickReadAcknowledgements(0);
            check(!session.tickReadAcknowledgements(599)&&before.equals(game.clientView(null)),"Early mulligan acknowledgement");
            check(session.tickReadAcknowledgements(600)&&session.audit().size()==2,"Both readers must acknowledge mulligan");return;
        }throw new AssertionError("No mulligan fixture");
    }
    static void packs(){
        check(PackBrowseOrder.CATEGORIES.contains("black_white")&&PackBrowseOrder.label("black_white").equals("黑白"),"Black & White category missing");
        var catalog=dev.hua.ptcglive.CardCatalog.load();var db=dev.hua.ptcglive.rules.BundledRules.loadCardDefinitions();
        var packs=new ArrayList<SetPackDefinition>(PackCatalog.loadBundled().values());packs.addAll(CustomPackCatalog.loadBundled(catalog,db).values());
        packs.sort(PackBrowseOrder.NEWEST);String last="9999-99-99";var json=new com.google.gson.Gson();
        for(var pack:packs){
            check(last.compareTo(pack.identity().releaseDate())>=0,"Release order reversed");last=pack.identity().releaseDate();
            check(PackBrowseOrder.CATEGORIES.contains(PackBrowseOrder.category(pack)),"Unreachable shop category");
            if(pack.identity().era().equals("black_white")&&(!pack.custom()||pack.setId().startsWith("collection_")))
                check(PackBrowseOrder.category(pack).equals("black_white"),"Black & White pack incorrectly placed in special: "+pack.setId());
            var preview=PackUiSnapshot.preview(UUID.randomUUID(),pack,catalog,db);
            check(preview.browseOnly().phase()==PackUiSnapshot.Phase.BROWSE&&preview.browseOnly().cards().equals(preview.cards()),"Browse changed pool");
            var longest=preview.cards().stream().max(Comparator.comparingInt(c->json.toJson(c).length())).orElseThrow();
            for(int count:List.of(16,32,64)){
                var ids=Collections.nCopies(pack.cardsPerPack()*count,longest.definitionId());var result=preview.withBatchResults(ids,count,catalog,db);
                check(result.results().size()==ids.size()&&result.cards().isEmpty(),"Batch lost results or repeated pool");
                check(json.toJson(result).length()<=250000,"Result exceeds packet bound: "+pack.setId());
            }
        }
    }
}
