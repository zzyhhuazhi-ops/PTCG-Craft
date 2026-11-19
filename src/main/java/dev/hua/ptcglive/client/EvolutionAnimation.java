package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import net.minecraft.client.gui.DrawContext;
import static dev.hua.ptcglive.client.BattleEffects.*;

/** Public confirmed evolution presentation. Sampling has no gameplay state or accumulated particles. */
final class EvolutionAnimation {
    record Pose(double x,double y,double width,double height,double flip,double tilt,double yaw,double pitch) {
        Rect bounds(){return new Rect((int)Math.round(x-width/2),(int)Math.round(y-height/2),Math.max(1,(int)Math.round(width)),Math.max(1,(int)Math.round(height)));}
        boolean front(){return Math.cos((1-flip)*Math.PI)>=0;}
        int drawnWidth(){return Math.max(1,(int)Math.round(width*Math.max(.018,Math.abs(Math.cos(flip*Math.PI)))));}
    }
    static Pose pose(AnimationTimeline t,Rect hand,Rect target,Rect arena,double ms){
        // Bench and active evolutions share a reveal size; only the final destination differs.
        double h=Math.max(target.height(),Math.min(arena.height()*t.sample("stage.height",ms,.46),arena.width()*.8/PtcgLiveTheme.CARD_ASPECT));
        double w=h*PtcgLiveTheme.CARD_ASPECT;
        double launch=t.sample("card.launch",ms,1),back=t.sample("card.return",ms,1),size=t.sample("card.size",ms,0);
        double handScale=t.sample("card.handScale",ms,1);
        double x=lerp(centerX(hand),centerX(arena),launch),y=lerp(centerY(hand)+target.height()*t.sample("card.handDrop",ms,0),centerY(arena)+arena.height()*t.sample("stage.y",ms,0),launch);
        return new Pose(lerp(x,centerX(target),back),lerp(y,centerY(target),back)-target.height()*t.sample("card.hover",ms,0),
            lerp(target.width()*handScale,w,size),lerp(target.height()*handScale,h,size),t.sample("card.flip",ms,1),t.sample("card.tilt",ms,0),
            t.sample("card.yaw",ms,0),t.sample("card.pitch",ms,0));
    }
    private static double centerX(Rect r){return r.x()+r.width()/2.0;}
    private static double centerY(Rect r){return r.y()+r.height()/2.0;}
    private static double lerp(double a,double b,double p){return a+(b-a)*p;}
    static void behind(DrawContext c,Pose pose,AnimationTimeline t,double ms,BattleAnimationEngine.Quality q,int focusBottom){
        if(q==BattleAnimationEngine.Quality.OFF||ms<0||ms>=t.durationMs)return;
        double a=t.sample("orbit.alpha",ms,0),radius=t.sample("orbit.radius",ms,1);
        double focus=t.sample("focus.alpha",ms,0);
        if(a<=0&&focus<=0)return;
        if(q!=BattleAnimationEngine.Quality.LOW&&EvolutionGlowShader.draw(c,pose.x(),pose.y(),pose.width()*1.29,pose.height()*.92,ms/1000,a,radius,focus,focusBottom))return;
        double rx=pose.width()*1.04*radius,ry=pose.height()*.69*radius;
        if(focus>0){
            c.enableScissor(0,0,c.getScaledWindowWidth(),Math.max(0,focusBottom));
            BattleEffectMesh.glow(mesh(c),pose.x(),pose.y(),pose.width()*2.06,pose.height()*1.47,alpha(0xFF1F008F,focus));
            c.disableScissor();
        }
        if(a<=0)return;
        // Scissor changes flush GUI buffers; acquire a new sink afterwards.
        var sink=mesh(c);
        BattleEffectMesh.glow(sink,pose.x(),pose.y(),rx*1.3,ry*1.3,alpha(0xFF57C5FF,a*.28));
        double thickness=Math.max(1,pose.width()*.035);
        BattleEffectMesh.ring(sink,pose.x(),pose.y(),rx,ry/rx,thickness+3,alpha(0xFF277DA5,a*.55));
        BattleEffectMesh.ring(sink,pose.x(),pose.y(),rx,ry/rx,thickness,alpha(0xFFCBFBFF,a));
    }
    static void front(DrawContext c,Pose pose,Rect target,AnimationTimeline t,double ms,BattleAnimationEngine.Quality q){
        if(q==BattleAnimationEngine.Quality.OFF||ms<0||ms>=t.durationMs)return;
        double flash=t.sample("card.flash",ms,0);
        if(flash>0){var b=pose.bounds();BattleUi.round(c,b.x(),b.y(),b.width(),b.height(),Math.max(2,b.width()/18),alpha(0xFFEEFFFF,flash));}
        landing(mesh(c),target,t,ms,q);
        hoverGlints(mesh(c),pose,t,ms,q);
        double a=t.sample("orbit.alpha",ms,0);int count=q==BattleAnimationEngine.Quality.LOW?4:q==BattleAnimationEngine.Quality.MEDIUM?8:12;
        for(int i=0;i<count&&a>0;i++){
            double angle=i*2.399+ms*.0016,r=t.sample("orbit.radius",ms,1);
            double opacity=a*Math.pow(.5+.5*Math.sin(i*3.1+ms*.009),3);
            BattleSceneEffects.glint(c,pose.x()+Math.cos(angle)*pose.width()*1.05*r,pose.y()+Math.sin(angle)*pose.height()*.69*r,
                pose.width()*.035,alpha(0xFFF1FFFF,opacity));
        }
    }
    static void hoverGlints(BattleEffectMesh.Sink sink,Pose pose,AnimationTimeline t,double ms,BattleAnimationEngine.Quality q){
        if(q==BattleAnimationEngine.Quality.OFF||ms<0||ms>=t.durationMs)return;
        double a=Math.clamp(t.sample("card.hover",ms,0)*2,0,1);
        if(a<=0)return;
        int count=q==BattleAnimationEngine.Quality.LOW?4:q==BattleAnimationEngine.Quality.MEDIUM?10:16;
        for(int i=0;i<count;i++){
            double seed=.5+.5*Math.sin(i*7.19),pulse=Math.pow(.5+.5*Math.sin(ms*.018+i*2.4),4);
            double px=pose.x()+(i%2==0?-1:1)*pose.width()*(.54+.1*seed);
            double py=pose.y()+Math.sin(i*4.13)*pose.height()*.46;
            if(i%3==0){px=pose.x()+Math.sin(i*2.71)*pose.width()*.45;py=pose.y()+(i%2==0?-1:1)*pose.height()*.51;}
            double radius=pose.width()*(.06+.1*seed)*pulse,thickness=Math.max(.35,pose.width()*.006)*pulse;
            int color=alpha(0xFFF1FFFF,a*pulse),clear=color&0xFFFFFF;
            // Thin horizontal pointed glints, independent of the earlier circular shell.
            sink.vertex(px,py-thickness,color);sink.vertex(px-radius,py,clear);
            sink.vertex(px,py+thickness,color);sink.vertex(px+radius,py,clear);
            sink.vertex(px,py-radius*.18,clear);sink.vertex(px-thickness,py,color);
            sink.vertex(px,py+radius*.18,clear);sink.vertex(px+thickness,py,color);
        }
    }
    static void landing(BattleEffectMesh.Sink sink,Rect target,AnimationTimeline t,double ms,BattleAnimationEngine.Quality q){
        if(q==BattleAnimationEngine.Quality.OFF||ms<0||ms>=t.durationMs)return;
        double a=t.sample("landing.alpha",ms,0),sparkles=t.sample("landing.sparkles",ms,0),frame=t.sample("landing.frame",ms,0);
        int count=q==BattleAnimationEngine.Quality.LOW?4:q==BattleAnimationEngine.Quality.MEDIUM?8:12;
        double w=target.width(),h=target.height(),x=centerX(target),y=centerY(target);
        if(a>0){
            BattleEffectMesh.glow(sink,x,y,w*.82,h*.85,alpha(0xFF65DFFF,a*.4));
            for(int i=0;i<count;i++){
                double seed=.5+.5*Math.sin(i*8.17),widthSeed=.5+.5*Math.sin(i*5.73+1.2);
                double jitter=i==0||i==count-1?0:Math.sin(i*3.47)*.34;
                double px=x+((i+jitter)/(count-1)-.5)*w*.98;
                // Long columns fade independently. Scaling their height by opacity
                // made the whole curtain collapse uniformly toward the card.
                double length=h*(1.35+seed),py=target.bottom()-h*(.01+.05*widthSeed);
                double intensity=Math.pow(a,.65+widthSeed*.9)*(.96+.04*Math.sin(i*4.1+ms*.014));
                beam(sink,px,py,length,w*(.035+.09*widthSeed),alpha(0xFF6EDFFF,intensity*.65));
                beam(sink,px,py,length,Math.max(.8,w*(.02+.045*widthSeed)),alpha(0xFFF3FFFF,intensity*1.15));
            }
        }
        if(frame>0){
            double left=target.x(),right=target.right(),top=target.y(),bottom=target.bottom();
            for(int layer=0;layer<2;layer++){
                double thickness=w*(layer==0?.065:.024);int color=alpha(layer==0?0xFF65DFFF:0xFFE5FFFF,frame*(layer==0?.55:1));
                BattleEffectMesh.line(sink,left,top,right,top,thickness,color);BattleEffectMesh.line(sink,right,top,right,bottom,thickness,color);
                BattleEffectMesh.line(sink,right,bottom,left,bottom,thickness,color);BattleEffectMesh.line(sink,left,bottom,left,top,thickness,color);
            }
        }
        for(int i=0;i<count&&sparkles>0;i++){
            double px=x+Math.sin(i*7.9)*w*.65,py=y-h*(.15+.7*(.5+.5*Math.sin(i*4.7)))-(ms-1850)*h*.00012;
            double radius=w*.035*sparkles*(.5+.5*Math.sin(i*2.7+ms*.01));
            BattleEffectMesh.line(sink,px-radius,py,px+radius,py,.8,alpha(0xFFE5FFFF,sparkles));
            BattleEffectMesh.line(sink,px,py-radius*2,px,py+radius*2,.8,alpha(0xFFE5FFFF,sparkles));
        }
    }
    private static void beam(BattleEffectMesh.Sink sink,double x,double bottom,double length,double width,int color){
        double shoulder=bottom-length*.78;
        sink.vertex(x-width/2,shoulder,color);sink.vertex(x-width/2,bottom,color);
        sink.vertex(x+width/2,bottom,color);sink.vertex(x+width/2,shoulder,color);
        sink.vertex(x-width/2,bottom-length,color&0xFFFFFF);sink.vertex(x-width/2,shoulder,color);
        sink.vertex(x+width/2,shoulder,color);sink.vertex(x+width/2,bottom-length,color&0xFFFFFF);
    }
}
