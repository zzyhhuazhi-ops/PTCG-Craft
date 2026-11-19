import dev.hua.ptcglive.spectator.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.table.*;
import net.minecraft.util.math.*;
import java.util.*;
import java.time.Clock;

public final class SpectatorScreenTest {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static void rejects(Runnable r){try{r.run();}catch(IllegalArgumentException|IllegalStateException expected){return;}throw new AssertionError("Expected rejection");}
    public static void main(String[] args){geometry();privacy();tables();System.out.println("SpectatorScreenTest passed: 216 rectangles, holes/limits/chunks/facing, shared hit layout, private-zone isolation, live public tables and cleanup.");}
    static void geometry(){
        var origin=new BlockPos(100,70,-100);
        for(var face:Direction.Type.HORIZONTAL)for(int w=3;w<=11;w++)for(int h=2;h<=7;h++){
            var cells=new HashSet<BlockPos>();for(int y=0;y<h;y++)for(int x=0;x<w;x++)cells.add(ScreenStructure.cell(origin,face,x,y));
            var r=ScreenStructure.detect(ScreenStructure.cell(origin,face,w-1,h-1),face,cells::contains,p->true);
            check(r.valid()&&r.width()==w&&r.height()==h&&r.origin().equals(origin),"Wrong rectangle "+face+" "+w+"x"+h);
            cells.remove(ScreenStructure.cell(origin,face,1,0));check(!ScreenStructure.detect(origin,face,cells::contains,p->true).valid(),"Hole merged");
            cells.add(ScreenStructure.cell(origin,face,1,0));
            var unloaded=ScreenStructure.cell(origin,face,w-1,h-1);
            check(!ScreenStructure.detect(origin,face,cells::contains,p->!p.equals(unloaded)).valid(),"Incomplete chunk merged");
            var fit=ScreenLayout.fit(w,h);check(Math.abs(fit.x(fit.left()+500*fit.scale())-500)<1e-6,"Hit x drift");
            check(Math.abs(fit.y(fit.top()+300*fit.scale())-300)<1e-6,"Hit y drift");
        }
        for(int[] size:new int[][]{{2,2},{3,1},{12,2},{3,8},{1000,1000}}){
            int[] calls={0};var r=ScreenStructure.detect(BlockPos.ORIGIN,Direction.SOUTH,p->{calls[0]++;return p.getX()>=0&&p.getX()<size[0]&&p.getY()>=0&&p.getY()<size[1]&&p.getZ()==0;},p->true);
            check(!r.valid()&&r.cells().size()<=128&&calls[0]<600,"Unbounded or invalid merge");
        }
        var plane=new HashSet<BlockPos>();for(int x=0;x<5;x++)for(int y=0;y<3;y++)plane.add(new BlockPos(x,y,0));
        plane.add(new BlockPos(2,1,1));check(ScreenStructure.detect(BlockPos.ORIGIN,Direction.SOUTH,plane::contains,p->true).cells().size()==15,"Depth neighbor merged");
    }
    static void privacy(){
        var game=MatchRulesRegressionTest.game(100,List.of());var session=MatchSession.multiplayer(game,MatchRulesRegressionTest.A,MatchRulesRegressionTest.B);
        var opening=session.screenView();check(opening.players().stream().allMatch(p->p.active()==null&&p.bench().isEmpty()),"Opening face-down field leaked");
        MatchRulesRegressionTest.setup(game,true);
        var json=new com.google.gson.Gson();String before=json.toJson(session.screenView());var random=game.randomTranscript();
        for(UUID seat:List.of(MatchRulesRegressionTest.A,MatchRulesRegressionTest.B)){
            var privateView=game.privateView(seat);
            var hidden=new ArrayList<CardInstance>(privateView.hand());hidden.addAll(privateView.deck());hidden.addAll(privateView.prizes());
            for(var card:hidden)check(!before.contains(card.instanceId().toString()),"Hidden instance on screen");
            rejects(()->ScreenMatch.capture(0,game.clientView(seat),List.of()));
            var deck=GeneratedRuleBatch49Test.zone(game,seat,"deck");Collections.swap(deck,0,deck.size()-1);
        }
        check(before.equals(json.toJson(session.screenView())),"Hidden deck order changed screen output");
        check(random.equals(game.randomTranscript()),"Viewing consumed battle RNG");
        var view=session.screenView();check(view.players().getFirst().active()!=null,"Public active missing");
        for(var card:ScreenLayout.cards(view)){
            check(card.x()>=0&&card.y()>=0&&card.x()+card.width()<=1000&&card.y()+card.height()<=600,"Card outside canvas");
            check(ScreenLayout.hit(view,card.x()+card.width()/2,card.y()+card.height()/2)!=null,"Card hit missed");
        }
        check(ScreenLayout.hit(view,990,300)==null,"Blank opened a card");
        game.concede(MatchRulesRegressionTest.A);check(session.screenView().result()!=null,"Public terminal result missing");
    }
    static DeckBox box(CardBoxService boxes,UUID owner){
        var box=boxes.create(owner,"Screen test","standard-international-2026");
        for(int i=0;i<60;i++)boxes.put(owner,box.id(),boxes.mint(owner,i<4?"fixture-basic-h":"fixture-energy-h").instanceId());return box;
    }
    static void tables(){
        var boxes=new CardBoxService(Clock.systemUTC());var tables=new CardTableManager(BundledRules.loadRulesets(),boxes,BundledRules.loadCardDefinitions(),BundledCombatData.load(),()->new ServerRandom(7));
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),solo=UUID.randomUUID();var aa=box(boxes,a);var bb=box(boxes,b);
        var key=new CardTableKey("minecraft:overworld",9812);tables.open(key,a,"standard-international-2026");tables.join(key,b);
        tables.selectBox(key,a,aa.id());tables.selectBox(key,b,bb.id());tables.setReady(key,a,true);tables.setReady(key,b,true);
        var match=tables.start(key,a);var id=match.id();check(tables.publicScreenMatches().equals(List.of(id)),"Live table not discoverable");
        var before=boxes.snapshot();var screen=tables.screenView(id);check(screen.id().equals(id)&&screen.revision()==0,"Wrong live instance");
        for(int i=0;i<100;i++)tables.screenView(id);check(before.equals(boxes.snapshot()),"Screen modified player collection");
        var state=tables.matchView(id,a,a);var concede=state.options().stream().filter(o->o.labelZh().startsWith("认输")).findFirst().orElseThrow();
        tables.submit(id,a,a,state.revision(),concede.index());check(tables.publicScreenMatches().isEmpty()&&tables.screenView(id).result()!=null,"Finish lost result or remained discoverable");
        tables.removePhysicalTable(key);check(tables.screenRevision(id)<0,"Removed table retained screen access");rejects(()->tables.screenView(id));
        var p=box(boxes,solo);var q=box(boxes,solo);var practice=tables.startPractice(solo,p.id(),q.id(),"standard-international-2026");
        check(!tables.publicScreenMatches().contains(practice.matchId()),"Private practice exposed");rejects(()->tables.screenView(practice.matchId()));
        tables.endMatch(practice.matchId());
    }
}
