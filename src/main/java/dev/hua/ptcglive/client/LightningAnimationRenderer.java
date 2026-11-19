package dev.hua.ptcglive.client;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Layered, deterministic hit renderer. Reused arrays hold arcs; render sampling allocates no particles. */
final class LightningAnimationRenderer {
    static final int ALL_LAYERS=0x7E,POINTS=11,MAX_ARCS=28;
    private static final double[] DIRECTIONS={-2.30,-.10,-1.32,2.90,1.94,-2.95,.68,-.65};
    private static final double[] REACH={2.1,1.55,1.85,2.0,1.4,1.8,1.6,2.0};
    private static final double[] WHITE_DIRECTIONS={-1.93,-1.15,-2.61,3.08,2.35,1.43,-.22,.53,-2.2,.05,2.8,1.9};
    private static final double[] WHITE_REACH={1.2,1.22,.94,1.125,1,.98,.94,.98,.7,.65,.7,.65};
    private static final int[] ARC_APPEARANCE_ORDER={0,3,2,1,5,4,6,7};
    private LightningAnimationRenderer(){}
    static final class State {
        ElectricImpactProfile profile=ElectricImpactProfile.DEFAULT;
        long seed;
        final double[] x=new double[MAX_ARCS*POINTS],y=new double[MAX_ARCS*POINTS];
        final double[] widths=new double[MAX_ARCS],brightness=new double[MAX_ARCS];
        final long[] buckets=new long[MAX_ARCS];
        final long[] parentBuckets=new long[MAX_ARCS];
        void reset(ElectricImpactProfile p,long s){profile=p;seed=s;Arrays.fill(buckets,Long.MIN_VALUE);Arrays.fill(parentBuckets,Long.MIN_VALUE);}
        void sample(double ms){
            int count=profile.mainArcCount()+profile.secondaryArcCount();
            for(int i=0;i<count;i++){
                long bucket=(long)Math.floor((ms+random(seed+i*71L)*profile.arcRefreshMs())/profile.arcRefreshMs());
                long parentBucket=i<profile.mainArcCount()?0:buckets[(i-profile.mainArcCount())%profile.mainArcCount()];
                if(buckets[i]==bucket&&parentBuckets[i]==parentBucket)continue;
                buckets[i]=bucket;
                parentBuckets[i]=parentBucket;
                long key=seed+i*8191L+bucket*65537L;
                boolean secondary=i>=profile.mainArcCount();
                int direction=i%profile.mainArcCount();
                double angle=DIRECTIONS[direction]+(random(seed+i*53L)-.5)*.25;
                double length=secondary?.15+random(key+7)*.45:REACH[direction]*(.82+random(key+7)*.25);
                double sx=0,sy=0;
                if(secondary){
                    int parent=(i-profile.mainArcCount())%profile.mainArcCount(),j=3+(i%4),at=parent*POINTS+j;
                    sx=x[at];sy=y[at];angle+=((i&1)==0?1:-1)*(.35+random(key+9)*.65);
                }
                double dx=Math.cos(angle)*length,dy=Math.sin(angle)*length;
                int start=i*POINTS;
                for(int j=0;j<POINTS;j++){
                    double t=j/(double)(POINTS-1);
                    double bend=Math.sin(t*Math.PI)*(!secondary&&direction==1
                        ?.85+.12*Math.sin(bucket*.8):Math.sin(angle*3+bucket*.8)*.28);
                    double jitter=bend+(random(key+j*113L)-.5)*.20*Math.sin(t*Math.PI);
                    x[start+j]=sx+dx*t-Math.sin(angle)*jitter;
                    y[start+j]=sy+dy*t+Math.cos(angle)*jitter;
                }
                widths[i]=(secondary?.006:.027)+random(key+13)*(secondary?.008:.023);
                brightness[i]=random(key+19)<.15?.2:.65+random(key+23)*.35;
            }
        }
    }
    static final class Pool {
        private final ArrayDeque<State> idle=new ArrayDeque<>();
        State acquire(ElectricImpactProfile profile,long seed){var state=idle.pollFirst();if(state==null)state=new State();state.reset(profile,seed);return state;}
        void release(State state){if(idle.size()<128)idle.addLast(state);}
        int retained(){return idle.size();}
        void clear(){idle.clear();}
    }
    static double random(long seed){
        long z=seed+0x9E3779B97F4A7C15L;z=(z^(z>>>30))*0xBF58476D1CE4E5B9L;z=(z^(z>>>27))*0x94D049BB133111EBL;
        return ((z^(z>>>31))>>>11)*0x1.0p-53;
    }
    private static int tint(int rgb,double a){return ((int)(Math.clamp(a,0,1)*255)<<24)|(rgb&0xFFFFFF);}
    static void charge(BattleEffectMesh.Sink sink,BattleAnimationEngine.Definition d,double x,double y,double size,double ms,BattleAnimationEngine.Quality q){
        if(q==BattleAnimationEngine.Quality.OFF)return;
        double a=d.timeline().sample("charge",ms,0);
        if(a>0)BattleEffectMesh.glow(sink,x,y,size*.45,tint(d.electric().outerColor(),a*.15));
    }
    static void impact(BattleEffectMesh.Sink sink,State state,double x,double y,double size,double ms,BattleAnimationEngine.Quality q,int layers){
        if(q==BattleAnimationEngine.Quality.OFF||ms<0||ms>=state.profile.durationMs()||state.profile.intensity()==0)return;
        state.sample(ms);
        for(int layer=1;layer<=6;layer++)if((layers&(1<<layer))!=0)layer(sink,state,x,y,size,ms,q,layer);
    }
    static void layer(BattleEffectMesh.Sink sink,State state,double x,double y,double s,double ms,BattleAnimationEngine.Quality q,int layer){
        var p=state.profile;
        int gold=p.glowColor(ms);
        if(q==BattleAnimationEngine.Quality.OFF||ms<0||ms>=p.durationMs())return;
        double grow=p.expansion(ms),core=p.core(ms),power=p.intensity();
        double flashPhase=Math.clamp((ms-p.flashStartMs())/(p.peakStartMs()-p.flashStartMs()),0,1);
        double decay=Math.clamp((ms-p.damagePopupMs())/(p.mainFlashEndMs()-p.damagePopupMs()),0,1);
        double radius=s*(.63+.22*grow)*(1-.63*decay*decay);
        if(layer==1){
            double a=ms<p.flashStartMs()?ms/Math.max(1,p.flashStartMs())*.08:Math.max(core,p.arc(ms)*.3);
            BattleEffectMesh.glow(sink,x,y,s*(.4+grow*1.15),tint(gold,a*power*.75));
        }else if(layer==2||layer==5){
            if(ms<p.flashStartMs()||ms>=p.mainFlashEndMs())return;
            int primary=Math.min(p.mainArcCount(),q==BattleAnimationEngine.Quality.LOW?3:q==BattleAnimationEngine.Quality.MEDIUM?5:8);
            primary=Math.min(primary,2+(int)((p.mainArcCount()-2)*BattleMotion.smooth((flashPhase-.8)/.2)));
            int secondary=q==BattleAnimationEngine.Quality.LOW?0:q==BattleAnimationEngine.Quality.MEDIUM?p.secondaryArcCount()/2:p.secondaryArcCount();
            secondary=(int)(secondary*Math.clamp((ms-p.peakStartMs())/(p.peakStartMs()-p.flashStartMs())+.3,0,1));
            for(int i=0;i<p.mainArcCount()+secondary;i++){
                if(i<p.mainArcCount()){
                    int rank=0;
                    for(int arc:ARC_APPEARANCE_ORDER){if(arc==i)break;if(arc<p.mainArcCount())rank++;}
                    if(rank>=primary)continue;
                }
                double opacity=state.brightness[i]*p.arc(ms)*power;
                int direction=i<p.mainArcCount()?i:(i-p.mainArcCount())%p.mainArcCount();
                if(direction==1)opacity*=Math.clamp((p.fadeStartMs()-ms)/(p.fadeStartMs()-p.damagePopupMs()),0,1);
                double breakStart=p.fadeStartMs();
                double cut=ms<breakStart?1:Math.clamp((p.mainFlashEndMs()-ms)/(p.mainFlashEndMs()-breakStart)+random(state.seed+i)*.15,0,1);
                int start=i*POINTS,segments=Math.max(0,(int)((POINTS-1)*cut));
                if(layer==2){
                    if(q==BattleAnimationEngine.Quality.HIGH)ribbon(sink,state,i,x,y,s,segments,4,tint(gold,opacity*.15));
                    ribbon(sink,state,i,x,y,s,segments,1.5,tint(gold,opacity*.6));
                }else {
                    double foreground=direction==4||direction==6?.3:1;
                    if(direction==3)foreground*=.45*BattleMotion.smooth((flashPhase-.5)/.5);
                    ribbon(sink,state,i,x,y,s,segments,1,tint(p.coreColor(),opacity*foreground*(direction==0?1:1-.6*BattleMotion.smooth((ms-p.damagePopupMs())/(p.fadeStartMs()-p.damagePopupMs())))));
                }
            }
        }else if(layer==3){
            if(core<=0)return;
            int count=q==BattleAnimationEngine.Quality.LOW?Math.min(12,p.impactSpikeCount()):p.impactSpikeCount();
            double firstFlash=BattleMotion.smooth(flashPhase/.35)*(1-BattleMotion.smooth((flashPhase-.8)/.2));
            spike(sink,x,y,2.90,s*1.85*grow,s*.24*grow,.40,tint(p.outerColor(),firstFlash*power),.7);
            spike(sink,x,y,-2.15,s*1.3*grow,s*.26*grow,.22,tint(p.coreColor(),firstFlash*power),.85);
            for(int i=0;i<count;i++){
                long key=state.seed+i*331L;
                double delay=random(key+1)*(p.peakStartMs()-p.flashStartMs())*.35;
                double speed=.55+random(key+2)*.65;
                double expand=1-Math.pow(2,-10*Math.clamp((ms-p.flashStartMs()-delay)/((p.peakStartMs()-p.flashStartMs())*speed),0,1));
                double finish=p.fadeStartMs()+(.35+.3*random(key+3))*(p.mainFlashEndMs()-p.fadeStartMs());
                double recede=p.damagePopupMs()+.75*(p.fadeStartMs()-p.damagePopupMs());
                double opacity=(1-BattleMotion.smooth((ms-recede)/(finish-recede)))*BattleMotion.smooth((flashPhase-.8)/.2);
                double remnant=BattleMotion.smooth((ms-p.damagePopupMs())/((p.fadeStartMs()-p.damagePopupMs())*.5));
                opacity*=1-.25*remnant;
                double narrowStart=p.damagePopupMs()+.875*(p.fadeStartMs()-p.damagePopupMs());
                double narrow=1-.65*BattleMotion.smooth((ms-narrowStart)/(p.fadeStartMs()-narrowStart));
                double a=i*2.399963+random(key+4)*1.1+.025*Math.sin(ms*.035+i);
                double outer=s*(.7+random(key+5)*1.05)*expand*(1-.18*decay)*(1+.3*remnant);
                double wide=(.055+random(key+6)*.16)*narrow;
                if(i%2==0){
                    int white=i/2;
                    double direction=WHITE_DIRECTIONS[white]+(random(key+7)-.5)*.15;
                    double reach=s*WHITE_REACH[white]*(.9+random(key+8)*.2)*expand*(1-.18*decay);
                    double spread=(.28+random(key+9)*.22)*narrow;
                    double cx=x+s*(random(key+10)-.5)*.26,cy=y+s*(random(key+11)-.5)*.26;
                    shard(sink,cx,cy,direction,reach*(1.02+.3*remnant),radius*.42*expand,spread*1.04,tint(gold,opacity*power),key,ms);
                    shard(sink,cx,cy,direction,reach,radius*.40*expand,spread,tint(p.coreColor(),opacity*core*power),key,ms);
                }else spike(sink,x,y,a,outer,radius*.60*expand,wide,tint(gold,opacity*power),.55);
            }
        }else if(layer==4){
            if(core<=0)return;
            // Filled irregular fan, with independently moving edges: never a scaled circle sprite.
            int count=32;double r=radius*.71*grow;
            for(int i=0;i<count;i++){
                double a=i*Math.PI*2/count,b=(i+1)*Math.PI*2/count;
                double ra=r*(.74+.28*random(state.seed+i*19L)+.13*Math.sin(i*3.4+ms*.017));
                double rb=r*(.74+.28*random(state.seed+(i+1)*19L)+.13*Math.sin((i+1)*3.4+ms*.017));
                sink.vertex(x,y,tint(p.coreColor(),core*power));
                sink.vertex(x+Math.cos(b)*rb,y+Math.sin(b)*rb,tint(p.coreColor(),core*power));
                sink.vertex(x+Math.cos(a)*ra,y+Math.sin(a)*ra,tint(p.coreColor(),core*power));
                sink.vertex(x,y,tint(p.coreColor(),core*power));
            }
            BattleEffectMesh.glow(sink,x,y,radius*.95,tint(p.coreColor(),core*power*.65));
        }else if(layer==6&&ms>=p.damagePopupMs()){
            int count=q==BattleAnimationEngine.Quality.LOW?5:q==BattleAnimationEngine.Quality.MEDIUM?10:16;
            for(int i=0;i<count;i++){
                long key=state.seed+i*109L;
                double start=p.damagePopupMs()+random(key)*100,age=(ms-start)/1000*650/p.durationMs();
                if(age<0)continue;
                double a=i*2.399963+random(key+1),r=s*(.65+age*(1.8+random(key+2)*2));
                double px=x+Math.cos(a)*r,py=y+Math.sin(a)*r+age*age*s*.4;
                double fade=p.tail(ms)*(.4+.6*random(key+3));
                BattleEffectMesh.line(sink,px,py,px-Math.cos(a)*s*.06,py-Math.sin(a)*s*.06,Math.max(.55,s*.012),tint(p.outerColor(),fade*power));
            }
        }
    }
    private static void spike(BattleEffectMesh.Sink sink,double x,double y,double a,double outer,double base,double spread,int color,double tipOpacity){
        if(outer<=0||base<=0)return;
        sink.vertex(x+Math.cos(a)*base*.48,y+Math.sin(a)*base*.48,color);
        sink.vertex(x+Math.cos(a+spread)*base,y+Math.sin(a+spread)*base,tint(color,(color>>>24)/255.0*.85));
        sink.vertex(x+Math.cos(a)*outer,y+Math.sin(a)*outer,tint(color,(color>>>24)/255.0*tipOpacity));
        sink.vertex(x+Math.cos(a-spread)*base,y+Math.sin(a-spread)*base,tint(color,(color>>>24)/255.0*.85));
    }
    /** Unequal shoulders and notches keep white impact sheets from becoming identical triangles. */
    private static void shard(BattleEffectMesh.Sink sink,double x,double y,double a,double outer,double base,double spread,int color,long seed,double ms){
        if(outer<=0||base<=0)return;
        double lastX=0,lastY=0,rootX=x+Math.cos(a)*base*.72,rootY=y+Math.sin(a)*base*.72;
        for(int i=0;i<7;i++){
            double q=1-i/3.0,angle=a+spread*q;
            double reach=i==3?outer:i==0||i==6?base:
                base+(outer-base)*(.20+.35*random(seed+i*23L)+.08*Math.sin(ms*.023+i));
            double px=x+Math.cos(angle)*reach,py=y+Math.sin(angle)*reach;
            if(i>0){
                sink.vertex(rootX,rootY,color);sink.vertex(lastX,lastY,color);sink.vertex(px,py,color);sink.vertex(rootX,rootY,color);
            }
            lastX=px;lastY=py;
        }
    }
    /** Shared miter joins and transparent edge bands eliminate disconnected rectangle strokes. */
    private static void ribbon(BattleEffectMesh.Sink sink,State state,int arc,double x,double y,double s,int count,double widthScale,int color){
        int start=arc*POINTS;
        for(int j=1;j<=count;j++){
            int a=start+j-1,b=start+j;
            int ap=Math.max(start,a-1),an=Math.min(start+POINTS-1,a+1),bp=b-1,bn=Math.min(start+POINTS-1,b+1);
            double adx=state.x[an]-state.x[ap],ady=state.y[an]-state.y[ap],alen=Math.max(.0001,Math.hypot(adx,ady));
            double bdx=state.x[bn]-state.x[bp],bdy=state.y[bn]-state.y[bp],blen=Math.max(.0001,Math.hypot(bdx,bdy));
            double ax=x+state.x[a]*s,ay=y+state.y[a]*s,bx=x+state.x[b]*s,by=y+state.y[b]*s;
            double aw=s*state.widths[arc]*widthScale*(1.45-.8*(j-1)/(POINTS-1.0))*.5;
            double bw=s*state.widths[arc]*widthScale*(1.45-.8*j/(POINTS-1.0))*.5;
            long key=state.seed+arc*4937L+state.buckets[arc]*31;
            aw*=.6+.8*random(key+(j-1)*103);bw*=.6+.8*random(key+j*103);
            double anx=-ady/alen,any=adx/alen,bnx=-bdy/blen,bny=bdx/blen;
            for(int band=0;band<3;band++){
                double lo=band==0?-1:band==1?-1.7:1,hi=band==0?1:band==1?-1:1.7;
                int low=band==1?color&0xFFFFFF:color,high=band==2?color&0xFFFFFF:color;
                double x0=ax+anx*aw*lo,y0=ay+any*aw*lo,x1=ax+anx*aw*hi,y1=ay+any*aw*hi;
                double x2=bx+bnx*bw*hi,y2=by+bny*bw*hi,x3=bx+bnx*bw*lo,y3=by+bny*bw*lo;
                // At very acute turns the soft outer band can fold over itself.
                // Bevel that segment using its own normal rather than reverse a face.
                if((x1-x0)*(y2-y0)-(y1-y0)*(x2-x0)>0||(x2-x0)*(y3-y0)-(y2-y0)*(x3-x0)>0){
                    double length=Math.max(.0001,Math.hypot(bx-ax,by-ay)),nx=-(by-ay)/length,ny=(bx-ax)/length;
                    x0=ax+nx*aw*lo;y0=ay+ny*aw*lo;x1=ax+nx*aw*hi;y1=ay+ny*aw*hi;
                    x2=bx+nx*bw*hi;y2=by+ny*bw*hi;x3=bx+nx*bw*lo;y3=by+ny*bw*lo;
                }
                sink.vertex(x0,y0,low);sink.vertex(x1,y1,high);sink.vertex(x2,y2,high);sink.vertex(x3,y3,low);
            }
        }
    }
}
