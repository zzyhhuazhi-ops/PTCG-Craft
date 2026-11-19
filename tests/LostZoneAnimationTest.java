package dev.hua.ptcglive.client;
import dev.hua.ptcglive.view.MatchPresentation.*;
import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import java.util.List;
public final class LostZoneAnimationTest {
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[] args){
        var from=new Rect(500,600,80,110);var to=new Rect(100,400,50,70);
        var start=LostZoneAnimation.pose(from,to,0);check(start.width()==80&&start.height()==110,"origin resized before flight");
        for(int fps:new int[]{30,60,120})for(int frame=0;frame<=fps*2;frame++){
            double ms=frame*1000./fps;var p=LostZoneAnimation.pose(from,to,ms);
            check(Double.isFinite(p.x())&&Double.isFinite(p.y())&&p.width()>0&&p.height()>0,"invalid card pose");
            if(ms>=ToolLostAnimation.LAND_MS)check(Math.abs(p.x()-125)<.001&&Math.abs(p.y()-435)<.001&&p.width()<3,"card missed portal");
        }
        var lost=new Cue(Kind.LOST,null,null,1,"放逐区","card");var draw=new Cue(Kind.DRAW,null,null,1,"抽牌",null);
        check(BattleAnimationTiming.offset(draw,List.of(lost,draw))>=BattleAnimationTiming.duration(lost),"draw before portal closes");
        System.out.println("LostZoneAnimationTest passed: visible origin, shrinking portal arrival, FPS sampling, draw waits for portal");
    }
}
