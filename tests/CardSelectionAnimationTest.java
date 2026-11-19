package dev.hua.ptcglive.client;
import dev.hua.ptcglive.engine.EffectCardPrompt;
import java.util.*;
public final class CardSelectionAnimationTest {
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    static EffectCardPrompt prompt(long step,List<UUID> selected){return new EffectCardPrompt(new UUID(0,73),step,"选择",0,8,List.of(),selected);}
    public static void main(String[] args){
        for(int fps:new int[]{30,60,120}){
            var a=new CardSelectionAnimation();var p=prompt(1,List.of());a.sync(p,0);
            for(int f=0;f<fps;f++){long now=Math.round(f*1e9/fps);a.sync(p,now);check(a.locked(p,now)==(now<CardSelectionAnimation.ENTRY_NS),"duplicate snapshot extended entry");}
            var id=new UUID(0,1);p=prompt(1,List.of(id));check(a.pending(p),"new selection not detected");a.sync(p,1_000_000_000L);
            check(a.flying(id,1_000_000_000L)&&a.locked(p,1_100_000_000L),"selection flight not gated");
            check(!a.locked(p,1_180_000_000L),"flight never released");
            p=prompt(2,List.of());check(a.pending(p),"new step ignored");a.sync(p,2_000_000_000L);check(a.locked(p,2_100_000_000L),"new step bypassed entry");
            a.sync(null,3_000_000_000L);check(!a.locked(null,3_000_000_000L),"closed prompt retained lock");
        }
        for(int[] s:new int[][]{{640,360,1},{320,480,1},{320,240,1},{1280,720,2}})for(int count=1;count<=5;count++)for(int i=0;i<count;i++){
            var r=CardSelectionAnimation.slot(s[0],s[1],s[2],count,i);var tray=CardSelectionAnimation.tray(s[0],s[1]);
            check(r.x()>=0&&r.right()<=s[0]&&r.y()>=tray.y()&&r.bottom()<=tray.bottom(),"slot outside tray");
            check(r.bottom()<CardSelectionAnimation.candidates(s[0],s[1],s[2]).y(),"selected cards overlap candidates");
        }
        for(int[] s:new int[][]{{640,360,1},{360,480,1},{320,240,1},{1280,720,2}}){
            var a=CopyAttackPanel.row(s[0],s[1],s[2],0);var b=CopyAttackPanel.row(s[0],s[1],s[2],1);
            var heading=CopyAttackPanel.heading(s[0],s[1],s[2]);var card=CopyAttackPanel.card(s[0],s[1]);
            check(a.x()>=0&&a.right()<=s[0]&&a.bottom()<b.y(),"copy rows overlap or leave viewport");
            check(b.bottom()<=s[1]-34*s[2],"copy rows cover footer controls");
            check(heading.bottom()<card.y()&&card.bottom()<=s[1],"copy card/header clipping");
        }
        System.out.println("CardSelectionAnimationTest passed: duplicate identity, step boundaries, flight gate, 30/60/120 timing, viewport slots and copy panel footer clearance");
    }
}
