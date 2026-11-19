package dev.hua.ptcglive.client;

import dev.hua.ptcglive.view.MatchPresentation.*;
import java.util.List;

public final class CoinAnimationTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        for(int[] size:new int[][]{{640,360},{320,480},{320,640},{640,240}}){
            double step=CoinAnimation.spacing(size[0],size[1]);
            check(step*1.4<size[0]/2.,"outer coin crosses narrow viewport edge");
            check(step<=size[1]*.20,"rows exceed vertical layout");
        }
        check(CoinAnimation.spacing(640,360)==72,"landscape layout changed");
        var coin=new Cue(Kind.COIN,null,null,0,"硬币·测试：正面",null);
        var effect=new Cue(Kind.DAMAGE,null,null,40,"特性伤害",null);
        var cues=List.of(coin,coin,effect,coin);
        check(CoinAnimation.groupSize(cues,0)==2&&CoinAnimation.groupSize(cues,3)==1,"separate effects merged coin groups");
        for(int count:new int[]{1,2,8,10,64}){
            double end=CoinAnimation.duration(count)/1e6;
            check(end>CoinAnimation.landing(count-1)+1500,"last result has no reading hold");
            check(CoinAnimation.opacity(end,count)==0,"coin overlay retained");
            for(int i=0;i<count;i++){
                check(!CoinAnimation.landed(CoinAnimation.landing(i)-.01,i),"future result disclosed");
                check(CoinAnimation.landed(CoinAnimation.landing(i),i),"confirmed result did not settle");
                check(CoinAnimation.visiblePage(CoinAnimation.start(i),count)==i/9,"page does not follow active coin");
            }
            for(int fps:new int[]{30,60,120})for(int i=0;i<Math.ceil(end*fps/1000);i++){
                double ms=i*1000./fps,a=CoinAnimation.opacity(ms,count);
                check(Double.isFinite(a)&&a>=0&&a<=1,"invalid real-time opacity");
            }
        }
        check(BattleAnimationTiming.offset(effect,List.of(coin,coin,effect))>=CoinAnimation.duration(2),"legacy result before toss sequence completes");
        System.out.println("CoinAnimationTest passed: distinct groups, no future results, last-result hold, pages,30/60/120 timeline and legacy staging");
    }
}
