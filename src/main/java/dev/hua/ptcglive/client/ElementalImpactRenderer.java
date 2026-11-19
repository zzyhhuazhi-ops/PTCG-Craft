package dev.hua.ptcglive.client;

import dev.hua.ptcglive.engine.field.EnergyType;

/** Rebuilt water shell and fire vortex, authored from observed silhouettes rather than extracted assets. */
final class ElementalImpactRenderer {
    private static int tint(int rgb,double a){return ((int)(Math.clamp(a,0,1)*255)<<24)|(rgb&0xFFFFFF);}
    static void draw(net.minecraft.client.gui.DrawContext c,BattleAnimationEngine.Definition d,EnergyType type,
                     double x,double y,double size,double seconds,BattleAnimationEngine.Quality quality){
        if(quality==BattleAnimationEngine.Quality.OFF||seconds<0||seconds*1000>d.timeline().durationMs-d.timeline().impactMs)return;
        double ms=d.timeline().impactMs+seconds*1000;
        if(quality==BattleAnimationEngine.Quality.HIGH&&ElementalImpactShader.draw(c,type,x,y,size,seconds,
            Math.clamp(d.timeline().sample("burst.core",ms,0),0,1),Math.clamp(d.timeline().sample("burst.shell",ms,0),0,1),
            Math.clamp(d.timeline().sample("fragment.spread",ms,0),0,2))){
            if(type==EnergyType.WATER)waterFragments(BattleEffects.mesh(c),x,y,size,seconds,
                Math.clamp(d.timeline().sample("burst.shell",ms,0),0,1),Math.clamp(d.timeline().sample("fragment.spread",ms,0),0,2),quality);
            return;
        }
        draw(BattleEffects.mesh(c),d,type,x,y,size,seconds,quality);
    }
    static void draw(BattleEffectMesh.Sink sink,BattleAnimationEngine.Definition d,EnergyType type,
                     double x,double y,double size,double seconds,BattleAnimationEngine.Quality quality) {
        if(quality==BattleAnimationEngine.Quality.OFF||seconds<0||seconds*1000>d.timeline().durationMs-d.timeline().impactMs)return;
        double ms=d.timeline().impactMs+seconds*1000;
        double core=Math.clamp(d.timeline().sample("burst.core",ms,0),0,1);
        double shell=Math.clamp(d.timeline().sample("burst.shell",ms,0),0,1);
        double spread=Math.clamp(d.timeline().sample("fragment.spread",ms,0),0,2);
        if(core<=0&&shell<=0)return;
        if(type==EnergyType.WATER)water(sink,x,y,size,seconds,core,shell,spread,quality);
        else if(type==EnergyType.FIRE)fire(sink,x,y,size,seconds,core,shell,spread,quality);
    }
    private static void water(BattleEffectMesh.Sink sink,double x,double y,double s,double t,double core,double shell,double spread,BattleAnimationEngine.Quality q) {
        BattleEffectMesh.glow(sink,x,y,s*1.15,tint(0xFF46D6FF,Math.max(core,shell)*.85));
        BattleEffectMesh.glow(sink,x,y,s*.8,tint(0xFFB8FFFF,shell*.9));
        BattleEffectMesh.ellipse(sink,x,y,s*.92,s*.92,tint(0xFFEFFFFF,core));
        int segments=q.branches*4;
        double radius=s*(.65+.12*spread);
        for(int i=0;i<segments;i++) {
            double a=i*Math.PI*2/segments,b=(i+1)*Math.PI*2/segments;
            double ra=radius*(1+.065*Math.sin(a*5+t*10)),rb=radius*(1+.065*Math.sin(b*5+t*10));
            double px=x+Math.cos(a)*ra,py=y+Math.sin(a)*ra,tx=x+Math.cos(b)*rb,ty=y+Math.sin(b)*rb;
            BattleEffectMesh.line(sink,px,py,tx,ty,s*.14,tint(0xFF59E7FF,shell*.7));
            BattleEffectMesh.line(sink,px,py,tx,ty,Math.max(1,s*.035),tint(0xFFE5FFFF,shell*.9));
        }
        waterFragments(sink,x,y,s,t,shell,spread,q);
    }
    private static void waterFragments(BattleEffectMesh.Sink sink,double x,double y,double s,double t,double shell,double spread,BattleAnimationEngine.Quality q){
        if(shell<=0)return;
        double radius=s*(.75+.12*spread);
        for(int i=0;i<6+q.branches/2;i++) {
            double a=i*2.399+.12*t,r=radius*(.95+.17*Math.sin(i*5))+spread*s*.20;
            double px=x+Math.cos(a)*r,py=y+Math.sin(a)*r,rotation=a+Math.PI/2;
            double cs=Math.cos(rotation),sn=Math.sin(rotation),length=s*(.10+.09*(.5+.5*Math.sin(i*7)))*shell;
            BattleEffectMesh.crystal((vx,vy,c)->sink.vertex(px+vx*cs-vy*sn,py+vx*sn+vy*cs,c),s*.04*shell,length*.65,tint(0xFF42E0F3,shell*.85));
        }
    }
    private static void fire(BattleEffectMesh.Sink sink,double x,double y,double s,double t,double core,double shell,double spread,BattleAnimationEngine.Quality q) {
        double late=BattleMotion.smooth((t-.32)/.38);
        BattleEffectMesh.glow(sink,x,y,s*1.35,tint(0xFFFFAC24,Math.max(core,shell)*.8));
        BattleEffectMesh.glow(sink,x,y,s*.9,tint(0xFFFFE259,shell*.6));
        // Reduced-quality path retains filled fire lobes instead of a coarse polygon outline.
        int lobes=Math.min(8,q.branches+3);
        for(int i=0;i<lobes;i++) {
            double a=i*2.399+t*3.2,r=s*(.7+.08*Math.sin(i*3.8-t*5));
            double width=s*(.20+.06*Math.sin(i*2.7+t*4));
            BattleEffectMesh.ellipse(sink,x+Math.cos(a)*r,y+Math.sin(a)*r,width,width*1.35,
                tint(late>.5?0xFFFF9618:0xFFFFD53C,shell*(.60+.16*Math.sin(i+t*4))));
        }
        int segments=Math.max(32,q.branches*8);
        for(int i=0;i<segments;i++) {
            double a=i*Math.PI*2/segments,b=(i+1)*Math.PI*2/segments;
            double r=s*(.70+.065*Math.sin(a*3-t*8)),rr=s*(.70+.065*Math.sin(b*3-t*8));
            BattleEffectMesh.line(sink,x+Math.cos(a)*r,y+Math.sin(a)*r,x+Math.cos(b)*rr,y+Math.sin(b)*rr,
                Math.max(.7,s*.035),tint(0xFFFFFFB2,shell*(1-late)*.8));
        }
        for(int i=0;i<15;i++) {
            double a=i*2.399,reach=s*(.7+.55*(.5+.5*Math.sin(i*7.3))),w=s*(.025+.04*(.5+.5*Math.sin(i*4.1)));
            double dx=Math.cos(a),dy=Math.sin(a);int color=tint(0xFFFFFFC2,core);
            sink.vertex(x-dy*w,y+dx*w,color);sink.vertex(x+dx*reach,y+dy*reach,color);
            sink.vertex(x+dy*w,y-dx*w,color);sink.vertex(x,y,color);
        }
        BattleEffectMesh.ellipse(sink,x,y,s*.49,s*.52,tint(0xFFFFFFF6,Math.min(1,core*1.55)));
    }
}
