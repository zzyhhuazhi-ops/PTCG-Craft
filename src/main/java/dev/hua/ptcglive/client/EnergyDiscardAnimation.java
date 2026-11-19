package dev.hua.ptcglive.client;

import java.util.*;
import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import static dev.hua.ptcglive.client.BattleEffects.alpha;

/** An already-confirmed discard dissolves the actual former energy slot. */
final class EnergyDiscardAnimation {
    private EnergyDiscardAnimation(){}
    static final double REMOVE_MS=233.333333;
    record Slot(int index,int sourceCount,String cardId){}
    static Slot removed(List<UUID> before,List<String> cards,List<UUID> after,String cardId,Set<UUID> claimed){
        for(int i=0;i<Math.min(before.size(),cards.size());i++)
            if(!after.contains(before.get(i))&&Objects.equals(cardId,cards.get(i))&&claimed.add(before.get(i)))
                return new Slot(i,before.size(),cardId);
        return null;
    }
    static void mesh(BattleEffectMesh.Sink out,Rect icon,double ms,int seed,BattleAnimationEngine.Quality quality){
        var t=BattleAnimationEngine.energyDiscard();
        if(ms<0||ms>=t.durationMs||quality==BattleAnimationEngine.Quality.OFF)return;
        double x=icon.x()+icon.width()/2.,y=icon.y()+icon.height()/2.,d=Math.max(3,icon.width());
        double core=t.sample("core.alpha",ms,0),scale=t.sample("core.scale",ms,0),spark=t.sample("spark.alpha",ms,0);
        int white=0xFFFFFFFF,cyan=0xFFDFFFFC;
        BattleEffectMesh.glow(out,x,y,d*2.7,alpha(cyan,core*.4));
        // A compact asymmetric pointed silhouette, never a scaled explosion texture.
        int spikes=quality==BattleAnimationEngine.Quality.LOW?7:11;
        for(int i=0;i<spikes;i++){
            double angle=i*Math.PI*2/spikes+noise(seed,i)*.4;
            double radius=d*(1.05+noise(seed,i+31)*1.05)*scale;
            double ax=x+Math.cos(angle-.2)*d*.62*scale,ay=y+Math.sin(angle-.2)*d*.62*scale;
            double bx=x+Math.cos(angle)*radius,by=y+Math.sin(angle)*radius*1.1;
            double cx=x+Math.cos(angle+.2)*d*.62*scale,cy=y+Math.sin(angle+.2)*d*.62*scale;
            int color=alpha(white,core);out.vertex(ax,ay,color);out.vertex(cx,cy,color);out.vertex(bx,by,color);out.vertex(ax,ay,color);
        }
        BattleEffectMesh.ellipse(out,x,y,d*.86*scale,d*.9*scale,alpha(white,core));
        int count=quality==BattleAnimationEngine.Quality.HIGH?13:quality==BattleAnimationEngine.Quality.MEDIUM?9:5;
        for(int i=0;i<count;i++){
            double p=Math.clamp((ms-100-noise(seed,i+151)*80)/500,0,1);
            double a=noise(seed,i+71)*Math.PI*2,travel=d*(.35+noise(seed,i+171)*.5+p*(.8+noise(seed,i+101)*1.4));
            double px=x+Math.cos(a)*travel,py=y+Math.sin(a)*travel*.8+p*d*.2;
            double r=d*(.07+noise(seed,i+131)*.08)*(1-.4*p);
            double opacity=spark*(.6+.4*Math.sin(ms*(.013+noise(seed,i+181)*.02)+noise(seed,i+191)*6.28));
            if(i%2==0)BattleEffectMesh.glow(out,px,py,r*3.5,alpha(cyan,opacity*.6));
            BattleEffectMesh.ellipse(out,px,py,r,r,alpha(white,opacity));
            if(i%3==0){
                BattleEffectMesh.line(out,px-r*2,py,px+r*2,py,Math.max(.25,r*.5),alpha(white,opacity*.7));
                BattleEffectMesh.line(out,px,py-r*2,px,py+r*2,Math.max(.25,r*.5),alpha(white,opacity*.7));
            }
        }
    }
    private static double noise(int seed,int i){
        int n=seed+i*0x9E3779B9;n^=n>>>16;n*=0x7feb352d;n^=n>>>15;
        return (n&0xffff)/65535.;
    }
}
