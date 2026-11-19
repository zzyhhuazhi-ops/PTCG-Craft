import dev.hua.ptcglive.client.BattleCardPanelLayout;
import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.view.*;
import java.util.*;

public final class BattleCardPanelTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void inside(Rect r,int w,int h){check(r.x()>=0&&r.y()>=0&&r.right()<=w&&r.bottom()<=h,"clipped: "+r+" at "+w+"x"+h);}
    public static void main(String[] args){
        int configurations=0;
        for(int w:new int[]{320,426,550,854,960,1920}) for(int h:new int[]{240,270,380,540,640,1080})
            for(int n:new int[]{1,2,4,8,32,100}){
                var first=BattleCardPanelLayout.of(w,h,n,0);var covered=new HashSet<Integer>();
                for(int page=0;page<first.pages();page++){
                    var l=BattleCardPanelLayout.of(w,h,n,page);inside(l.card(),w,h);inside(l.panel(),w,h);inside(l.close(),w,h);
                    check(!l.card().overlaps(l.panel()),"card obscures actions");
                    for(int i=l.first();i<l.end();i++){
                        inside(l.row(i),w,h);check(!l.row(i).overlaps(l.close()),"close obscures row");
                        check(covered.add(i),"duplicate option");
                    }
                }
                check(covered.size()==n,"lost actions");check(BattleCardPanelLayout.of(w,h,n,999).page()==first.pages()-1,"resize page clamp");configurations++;
            }
        var a=SlowkingOperationTest.A;var b=SlowkingOperationTest.B;var match=SlowkingOperationTest.fixture(SlowkingOperationTest.DONOR);
        var session=MatchSession.multiplayer(match,a,b);var view=session.view(a,a);String key="field:"+match.activePokemonView(a).pokemonId();
        var allowed=MatchActionMatcher.cardFaceActions(view.options(),key,true);
        var rows=BattleCardActions.rows(view,key,allowed,List.of());
        check(rows.size()==2,"disabled attack disappeared");
        check(rows.get(0).enabled()&&rows.get(0).damage().isBlank(),"uncertain copy is presented as exact damage");
        check(!rows.get(1).enabled()&&rows.get(1).detail().equals("能量不足"),"unpayable attack is clickable");
        check(rows.get(0).cost().contains("超×1")&&!rows.get(0).cost().contains("PSYCHIC"),"raw energy enum");
        check(rows.get(0).option()==allowed.getFirst(),"server option replaced");
        var locked=BattleCardActions.rows(view,key,List.of(),List.of("无法攻击"));
        check(locked.stream().noneMatch(BattleCardActions.Row::enabled),"preview enables a forbidden action");
        var own=view;
        var options=List.of(new MatchSession.Option(23,"使用特性：回复",List.of(key)),new MatchSession.Option(41,"撤退 → 呆呆兽",List.of(key,"field:other")),
            new MatchSession.Option(57,"使用特性：回复 → 呆呆王",List.of("field:other",key)));
        var extra=new MatchSession.Snapshot(view.revision(),view.practice(),view.controllableSeats(),view.game(),options,view.cardNames(),view.publicCues(),view.attackBlockZh(),view.guidance(),view.events());
        var utility=BattleCardActions.rows(extra,key,MatchActionMatcher.cardFaceActions(options,key,false),List.of());
        check(utility.stream().map(r->r.option().index()).toList().equals(List.of(23,41)),"source anchors/ability/retreat changed");
        session.submit(a,a,own.revision(),rows.getFirst().option().index());
        check(match.effectCardReveal()!=null,"panel's issued attack did not enter the Slowking reveal");
        System.out.println("BattleCardPanelTest passed: "+configurations+" layout configurations; disabled/blocked attacks, public previews, ability/retreat anchors and real Slowking submission");
    }
}
