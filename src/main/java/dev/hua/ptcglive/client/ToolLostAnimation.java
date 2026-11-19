package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import static dev.hua.ptcglive.client.BattleEffects.alpha;

/** Live Lost Vacuum zone transport, adapted to our visible Lost Zone pile. Millisecond sampling only. */
final class ToolLostAnimation {
    static final double START_MS=300,LAND_MS=700,END_MS=1100;
    private ToolLostAnimation() { }
    static EvolutionAnimation.Pose pose(Rect from,Rect to,double ms){
        double p=Math.clamp((ms-START_MS)/(LAND_MS-START_MS),0,1),ease=1-Math.pow(1-p,2);
        double size=1-Math.pow(p,3)*.96;
        double x=from.x()+from.width()*.5+(to.x()+to.width()*.5-from.x()-from.width()*.5)*ease;
        double dy=to.y()+to.height()*.5-from.y()-from.height()*.5;
        // Bend toward the destination row, away from the near-side avatar HUD.
        double y=from.y()+from.height()*.5+dy*ease+Math.signum(dy)*Math.sin(p*Math.PI)*to.height()*.5;
        return new EvolutionAnimation.Pose(x,y,to.width()*size,to.height()*size,1+2*p,110*p,0,25*Math.sin(p*Math.PI));
    }
    static void mesh(BattleEffectMesh.Sink out,Rect pile,double ms,BattleAnimationEngine.Quality quality){
        if(quality==BattleAnimationEngine.Quality.OFF||ms<START_MS||ms>=END_MS)return;
        double age=ms-START_MS,t=age/(END_MS-START_MS),a=Math.min(1,age/90)*(1-Math.pow(t,3));
        double x=pile.x()+pile.width()*.5,y=pile.y()+pile.height()*.5,r=pile.width()*.8;
        // A dark opening with broad, broken violet clouds, not a solid ring or screen flash.
        BattleEffectMesh.glow(out,x,y,r*.9,r*.8,alpha(0xFF17154C,a));
        BattleEffectMesh.ellipse(out,x,y,r*.32,r*.23,alpha(0xFF20174D,a*.65));
        int clouds=quality==BattleAnimationEngine.Quality.LOW?5:9;
        for(int i=0;i<clouds;i++){
            double angle=i*Math.PI*2/clouds-age*.006,scale=.62+.17*Math.sin(i*2.4+age*.004);
            BattleEffectMesh.glow(out,x+Math.cos(angle)*r*scale,y+Math.sin(angle)*r*scale*.83,r*.65,r*.48,
                alpha(i%2==0?0xFFE23CEF:0xFF763ADD,a*(.55+.24*Math.sin(i+age*.007))));
        }
        // Independently rotating, irregular white loops collapse into the opening.
        int loops=quality==BattleAnimationEngine.Quality.LOW?2:3;
        for(int i=0;i<loops;i++){
            double phase=age*.011+i*2.1,rad=r*(.48+.09*Math.sin(phase*.7))*(1-t*.65);
            for(int j=0;j<20;j++){
                if((j+i*3)%11==0)continue;
                double u=j*Math.PI*2/20,v=(j+1)*Math.PI*2/20;
                double r0=rad*(1+.36*Math.sin(u*2+phase)+.2*Math.cos(u*5-phase*1.4));
                double r1=rad*(1+.36*Math.sin(v*2+phase)+.2*Math.cos(v*5-phase*1.4));
                double x0=x+Math.cos(u+phase)*r0,y0=y+Math.sin(u+phase)*r0*.7;
                double x1=x+Math.cos(v+phase)*r1,y1=y+Math.sin(v+phase)*r1*.7;
                BattleEffectMesh.line(out,x0,y0,x1,y1,Math.max(.7,pile.width()*.07),alpha(0xFFCDAFFF,a*.24));
                BattleEffectMesh.line(out,x0,y0,x1,y1,Math.max(.4,pile.width()*.016),alpha(0xFFF4F0FF,a*.9));
            }
        }
    }
}
