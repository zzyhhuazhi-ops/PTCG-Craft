package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import dev.hua.ptcglive.view.MatchPresentation.*;
import java.util.UUID;

public final class SwitchAnimationTest {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        UUID seat=new UUID(0,1),a=new UUID(0,2),b=new UUID(0,3);
        var out=new Cue(Kind.MOVE,seat,a,0,"战斗位→备战区·效果","a");
        var in=new Cue(Kind.MOVE,seat,b,0,"备战区→战斗位·效果","b");
        check(SwitchAnimation.pair(out,in)&&SwitchAnimation.pair(in,out),"both orders pair");
        check(!SwitchAnimation.pair(out,out),"no self pairing");
        check(!SwitchAnimation.pair(out,new Cue(Kind.MOVE,seat,b,0,"备战区→战斗位·撤退","b")),"retreat remains distinct");
        check(!SwitchAnimation.pair(out,new Cue(Kind.MOVE,a,b,0,in.text(),"b")),"seats remain independent");
        var replacement=new Cue(Kind.MOVE,seat,b,0,"备战区→战斗位·替补","b");
        check(SwitchAnimation.applies(replacement)&&SwitchAnimation.incoming(replacement),"replacement uses reveal");
        check(!SwitchAnimation.pair(out,replacement)&&!SwitchAnimation.pair(replacement,out),"replacement never paired with outgoing");
        check(!SwitchAnimation.applies(new Cue(Kind.MOVE,seat,b,0,"战斗位→备战区·替补","b")),"only confirmed incoming replacement");
        var from=new Rect(450,430,75,100);var stage=new Rect(400,180,210,300);var to=new Rect(470,320,90,125);
        check(SwitchAnimation.outgoingTime(1000)==0,"outgoing card holds during reveal");
        check(SwitchAnimation.landing(1000)==0&&SwitchAnimation.landing(1600)==1,"exact landing boundaries");
        check(SwitchAnimation.reveal(from,stage,0).bounds().equals(from),"origin preserved");
        check(SwitchAnimation.flatLanding(stage,to,1600).bounds().equals(to),"final bounds preserved");
        check(SwitchAnimation.replacementReveal(from,stage,0).bounds().equals(from),"replacement origin");
        check(SwitchAnimation.replacementReveal(from,stage,350).bounds().equals(stage),"replacement face at stage");
        check(SwitchAnimation.replacementLift(1000)==0&&Math.abs(SwitchAnimation.replacementLift(1600))<1e-8,"replacement lift endpoints");
        check(SwitchAnimation.replacementLift(1300)>1,"replacement rises before landing");
        for(boolean low:new boolean[]{false,true})for(int fps:new int[]{30,60,120})for(int i=0;i<=fps*2;i++){
            double ms=i*1000./fps;int[] count={0};
            SwitchAnimation.glow((x,y,c)->{check(Double.isFinite(x+y),"finite mesh");count[0]++;},stage,ms,low);
            check(count[0]<=12000,"bounded mesh");if(ms>=900)check(count[0]==0,"ring fully removed before landing");
            count[0]=0;SwitchAnimation.replacementGlow((x,y,c)->{check(Double.isFinite(x+y),"finite replacement mesh");count[0]++;},stage,ms,low);
            check(count[0]<=14000,"bounded replacement mesh");if(ms>=450)check(count[0]==0,"replacement flash removed");
            var p=SwitchAnimation.reveal(from,stage,ms);check(p.width()>0&&p.height()>0,"valid card");
        }
        System.out.println("SwitchAnimationTest passed: pair isolation, held outgoing, reveal/landing endpoints, bounded glow, 30/60/120 sampling");
    }
}
