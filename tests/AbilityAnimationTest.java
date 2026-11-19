package dev.hua.ptcglive.client;
import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import dev.hua.ptcglive.view.MatchPresentation.*;
import java.util.List;
public final class AbilityAnimationTest {
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        var source=new Rect(300,250,80,112);
        for(int fps:new int[]{30,60,120})for(int frame=0;frame<=fps*2;frame++){
            double ms=frame*1000./fps,p=AbilityAnimation.lift(ms),a=AbilityAnimation.opacity(ms);
            var r=AbilityAnimation.focus(source,ms);
            check(p>=0&&p<=1&&a>=0&&a<=1&&r.width()>=80&&r.width()<=90,"bounded reveal geometry");
            if(ms>=AbilityAnimation.LAND_MS)check(p==0&&a==0&&r.equals(source),"ability does not return exactly to source");
        }
        check(AbilityAnimation.lift(120)==1&&AbilityAnimation.lift(1400)==1,"reading hold too short");
        var cue=new Cue(Kind.ABILITY,null,null,0,"特性",null);
        for(Kind kind:List.of(Kind.DAMAGE,Kind.HEAL,Kind.ENERGY,Kind.DRAW)){
            var result=new Cue(kind,null,null,1,"结果",null);
            check(BattleAnimationTiming.offset(result,List.of(cue,result))>=AbilityAnimation.DURATION_NS,"ability result overlaps reveal");
        }
        check(BattleAnimationTiming.duration(cue)==AbilityAnimation.DURATION_NS,"ability duration mismatch");
        System.out.println("Ability reveal:30/60/120 timestamp geometry, exact return, reading hold and result staging passed");
    }
}
