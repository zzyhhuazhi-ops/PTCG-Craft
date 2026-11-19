package dev.hua.ptcglive.client;

import dev.hua.ptcglive.view.MatchPresentation.*;
import java.util.UUID;

public final class RetreatAnimationTest {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        UUID seat=new UUID(0,1),a=new UUID(0,2),b=new UUID(0,3);
        var out=new Cue(Kind.MOVE,seat,a,0,"战斗位→备战区·撤退","a");
        var in=new Cue(Kind.MOVE,seat,b,0,"备战区→战斗位·撤退","b");
        check(RetreatAnimation.pair(out,in)&&RetreatAnimation.pair(in,out),"both journal orders pair");
        check(!RetreatAnimation.pair(out,out),"no self pairing");
        check(!RetreatAnimation.pair(out,new Cue(Kind.MOVE,seat,b,0,"备战区→战斗位·效果","b")),"effect switch is distinct");
        check(!RetreatAnimation.pair(out,new Cue(Kind.MOVE,a,b,0,in.text(),"b")),"different seats cannot pair");
        var from=new PocketTableCamera.Pose(4,.025,4,1,1.4,0,0);
        var to=new PocketTableCamera.Pose(0,.025,1,1.3,1.8,0,0);
        for(boolean incoming:new boolean[]{false,true})for(boolean opponent:new boolean[]{false,true}){
            check(RetreatAnimation.pose(from,to,incoming,opponent,-50).equals(from),"queued card stays at original position");
            check(RetreatAnimation.pose(from,to,incoming,opponent,700).equals(to),"exact landing, no handoff jump");
            for(int fps:new int[]{30,60,120})for(int i=0;i<=fps;i++){
                var p=RetreatAnimation.pose(from,to,incoming,opponent,i*1000./fps);
                check(Double.isFinite(p.x()+p.y()+p.z()+p.width()+p.height()),"finite pose");
                check(p.width()>0&&p.height()>0&&p.y()>=0&&p.y()<2,"bounded lift");
            }
        }
        check(RetreatAnimation.DURATION_MS>RetreatAnimation.LAND_MS,"settle tail stays gated");
        System.out.println("RetreatAnimationTest passed: paired identities, independent seats, queued origin, exact endpoints, 30/60/120 sampling");
    }
}
