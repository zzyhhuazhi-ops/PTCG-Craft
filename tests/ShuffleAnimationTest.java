package dev.hua.ptcglive.client;
import dev.hua.ptcglive.view.MatchPresentation.*;
import java.util.List;
public final class ShuffleAnimationTest {
    private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[] args){
        var deck=new PocketTableCamera.Pose(3,.025,-2,1.3,1.8,0,0);
        for(int fps:new int[]{30,60,120})for(int frame=0;frame<=fps;frame++){
            double ms=frame*1000./fps;var s=ShuffleAnimation.sample(ms);
            check(s.lift()>=0&&s.lift()<=1&&s.edge()>=0&&s.edge()<=1,"invalid packet envelope");
            var p=ShuffleAnimation.pose(deck,ms,3);
            check(p.width()==deck.width()&&p.height()==deck.height(),"deck changed card dimensions");
            if(ms>=650)check(p.equals(deck),"deck did not settle at wall-clock deadline");
        }
        for(int i=0;i<4;i++)check(ShuffleAnimation.sample(95+i*110).lift()>.99,"missing packet lift");
        check(ShuffleAnimation.sample(-1).lift()==0,"queued shuffle rendered early");
        var shuffle=new Cue(Kind.SHUFFLE,null,null,0,"洗牌",null);
        var draw=new Cue(Kind.DRAW,null,null,1,"抽牌 1 张",null);
        check(BattleAnimationTiming.offset(draw,List.of(shuffle,draw))>=BattleAnimationTiming.duration(shuffle),"legacy draw starts before shuffle settled");
        System.out.println("ShuffleAnimationTest passed: four packet lifts, settled deck, wall-clock 30/60/120 samples and draw-after-shuffle");
    }
}
