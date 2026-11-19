package dev.hua.ptcglive.client;

import net.minecraft.client.gui.DrawContext;
import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import static dev.hua.ptcglive.client.BattleEffects.alpha;

/** A confirmed attachment only; null source means no inferred flight from a hidden zone. */
final class EnergyAttachmentAnimation {
    private EnergyAttachmentAnimation(){}
    static double contactMs(boolean knownSource){return knownSource?BattleAnimationEngine.energy().impactMs:0;}
    static int diameter(int width,int ui){return Math.max(5*ui,Math.min(9*ui,(int)Math.round(width*.16)));}
    static int step(int width,int ui){return Math.max(1,diameter(width,ui)-ui);}
    static int room(int width,int ui){return Math.max(1,(width-4-diameter(width,ui))/step(width,ui)+1);}
    static Rect icon(Rect target,int ui,int index){
        int d=diameter(target.width(),ui),slot=Math.clamp(index,0,room(target.width(),ui)-1);
        return new Rect(target.x()+2+slot*step(target.width(),ui),target.y()+target.height()-d-2*ui,d,d);
    }
    static void draw(DrawContext c,Rect source,Rect target,int ui,int index,double ms,int color,BattleAnimationEngine.Quality quality){
        mesh(BattleEffects.mesh(c),source,target,ui,index,ms,color,quality);
    }
    static void mesh(BattleEffectMesh.Sink out,Rect source,Rect target,int ui,int index,double ms,int color,BattleAnimationEngine.Quality quality){
        if(quality==BattleAnimationEngine.Quality.OFF||ms<0)return;
        var t=BattleAnimationEngine.energy();
        if(source==null)ms+=t.impactMs;
        if(ms>=t.durationMs)return;
        color=0xFF000000|(((color>>16&255)+510)/3<<16)|(((color>>8&255)+510)/3<<8)|((color&255)+510)/3;
        var icon=icon(target,ui,index);
        double tx=icon.x()+icon.width()/2.,ty=icon.y()+icon.height()/2.,size=Math.max(8,target.width());
        int detail=quality==BattleAnimationEngine.Quality.HIGH?11:quality==BattleAnimationEngine.Quality.MEDIUM?8:5;
        if(source!=null&&ms<t.impactMs){
            double fx=source.x()+source.width()/2.,fy=source.y()+source.height()/2.;
            double p=t.sample("flight",ms,0),a=t.sample("orb.alpha",ms,0),trail=t.sample("trail.alpha",ms,0);
            double lift=Math.min(size*.35,Math.max(0,fy-2));
            double length=Math.max(1,Math.hypot(tx-fx,ty-fy)),nx=-(ty-fy)/length,ny=(tx-fx)/length;
            int streams=quality==BattleAnimationEngine.Quality.HIGH?3:quality==BattleAnimationEngine.Quality.MEDIUM?2:1;
            for(int stream=0;stream<streams;stream++)for(int i=0;i<detail;i++){
                double t0=Math.max(0,ms-i*20-stream*12),t1=Math.max(0,ms-(i+1)*20-stream*12);
                double p0=t.sample("flight",t0,0),p1=t.sample("flight",t1,0);
                double side=stream==0?0:stream==1?-size*.07:size*.07;
                double x0=fx+(tx-fx)*p0+nx*side*Math.sin(Math.PI*p0),y0=flightY(fy,ty,p0,lift,t0)+ny*side*Math.sin(Math.PI*p0);
                double x1=fx+(tx-fx)*p1+nx*side*Math.sin(Math.PI*p1),y1=flightY(fy,ty,p1,lift,t1)+ny*side*Math.sin(Math.PI*p1);
                double fade=trail*(1-i/(double)detail)/(1+stream*.2);
                BattleEffectMesh.line(out,x0,y0,x1,y1,size*.025,alpha(color,fade*.18));
                BattleEffectMesh.line(out,x0,y0,x1,y1,Math.max(.45,size*.009),alpha(0xFFFFFFFF,fade*.95));
            }
            double x=fx+(tx-fx)*p,y=flightY(fy,ty,p,lift,ms);
            BattleEffectMesh.glow(out,x,y,size*.12,alpha(color,a*.5));
            BattleEffectMesh.ellipse(out,x,y,size*.045,size*.045,alpha(0xFFF6FFFF,a));
        }
        double flash=t.sample("contact.alpha",ms,0);
        // The incoming light resolves into the actual persistent energy icon, not a central explosion.
        BattleEffectMesh.glow(out,tx,ty,icon.width()*.9,alpha(color,flash*.3));
        BattleEffectMesh.ellipse(out,tx,ty,icon.width()*.5,icon.height()*.5,alpha(0xFFF5FAFF,flash));
    }
    private static double flightY(double from,double to,double p,double lift,double ms){return from+(to-from)*p-lift*(1-p)*BattleMotion.smooth(ms/100);}
}
