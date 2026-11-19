package dev.hua.ptcglive.client;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.view.MatchPresentation.*;
import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import java.util.*;
public final class DiscardAnimationTest {
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[] args){
        UUID owner=new UUID(0,1);var old=new CardInstance(new UUID(0,2),owner,"same");
        var a=new CardInstance(new UUID(0,3),owner,"same");var b=new CardInstance(new UUID(0,4),owner,"same");
        var from=new Rect(10,300,60,84);var to=new Rect(400,200,40,56);var claimed=new HashSet<UUID>();
        var before=List.of(old);var after=List.of(old,a,b);var origins=Map.of(a.instanceId(),from);
        var first=DiscardAnimation.claim(before,after,"same",origins,claimed);
        check(first.card().equals(a)&&first.origin().equals(from),"wrong physical card or visible origin");
        var second=DiscardAnimation.claim(before,after,"same",origins,claimed);
        check(second.card().equals(b)&&second.origin()==null,"hidden origin must remain unknown");
        check(DiscardAnimation.claim(before,after,"same",origins,claimed)==null,"duplicate instance claimed");
        check(DiscardAnimation.claim(after,after,"same",origins,new HashSet<>())==null,"old pile replayed");
        check(DiscardAnimation.flat(from,to,0).equals(from),"wrong departure");
        for(int fps:new int[]{30,60,120})for(int frame=0;frame<=fps;frame++){
            double ms=frame*1000./fps,p=DiscardAnimation.progress(ms);
            check(p>=0&&p<=1,"overshot landing");
            if(ms>=DiscardAnimation.LAND_MS)check(DiscardAnimation.flat(from,to,ms).equals(to),"late settling");
        }
        var discard=new Cue(Kind.DISCARD,owner,null,0,"弃牌区","same");var draw=new Cue(Kind.DRAW,owner,null,1,"抽牌",null);
        check(BattleAnimationTiming.offset(draw,List.of(discard,draw))>=BattleAnimationTiming.duration(discard),"legacy draw overlaps discard");
        System.out.println("DiscardAnimationTest passed: exact instances, hidden origins, duplicate suppression, landing, frame-rate samples, draw ordering");
    }
}
