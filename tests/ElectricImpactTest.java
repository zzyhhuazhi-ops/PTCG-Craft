package dev.hua.ptcglive.client;
import java.util.*;
import com.google.gson.*;
public final class ElectricImpactTest {
    static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
    static void near(double a,double b){require(Math.abs(a-b)<1e-8,a+" != "+b);}
    static long mesh(LightningAnimationRenderer.State s,double ms,BattleAnimationEngine.Quality q,int mask){
        final long[] hash={1};final int[] count={0};final double[] quad=new double[8];
        LightningAnimationRenderer.impact((x,y,c)->{require(Double.isFinite(x)&&Double.isFinite(y)&&Math.abs(x)<1000&&Math.abs(y)<1000,"unbounded vertex");hash[0]=hash[0]*31+Double.doubleToLongBits(x);hash[0]=hash[0]*31+Double.doubleToLongBits(y)+c;
            int at=(count[0]%4)*2;quad[at]=x;quad[at+1]=y;count[0]++;
            if(count[0]%4==0){double area=0;for(int i=0;i<4;i++){int j=(i+1)%4;area+=quad[i*2]*quad[j*2+1]-quad[i*2+1]*quad[j*2];}require(area<.00001,"back-facing electric quad");}
        },s,0,0,160,ms,q,mask);
        require(count[0]<16000&&count[0]%4==0,"unbounded mesh budget");return hash[0];
    }
    public static void main(String[] args)throws Exception{
        var p=ElectricImpactProfile.DEFAULT;
        near(0,p.expansion(50));near(0,p.expansion(66));require(p.expansion(83)>.95,"flash too slow");require(p.core(283)>p.core(350)&&p.core(350)>p.core(383),"core does not recede through fade phases");near(0,p.core(450));near(0,p.core(650));
        near(0,p.numberOpacity(215.9));near(1,p.numberOpacity(216));require(p.numberScale(216)>1,"no numeric punch");near(1,p.numberScale(450));
        near(0,p.hpProgress(449.9));near(1,p.hpProgress(450));near(1,p.hpProgress(650));near(0,p.shake(49));near(0,p.shake(151));
        var retimed=p.edited(7,p.outerColor(),1,1300);
        near(0,retimed.hpProgress(899.9));near(1,retimed.hpProgress(900));
        for(double time:List.of(0.,50.,83.,116.,150.,216.,350.,450.))near(time,retimed.materialTime(time*2));
        try(var stream=ElectricImpactTest.class.getResourceAsStream("/assets/ptcglive_mc/textures/gui/battle/electric_core_v1.png")){
            require(stream!=null,"core material missing");var image=javax.imageio.ImageIO.read(stream);
            require(image.getWidth()==2048&&image.getHeight()==2048&&image.getColorModel().hasAlpha(),"invalid core atlas format");
            int soft=0,different=0,lateOpaque=0;
            for(int y=0;y<512;y++)for(int x1=0;x1<512;x1++){
                require((image.getRGB(x1,y)>>>24)==0&&(image.getRGB(1536+x1,1536+y)>>>24)==0,"nontransparent start/end frames");
                if((image.getRGB(512+x1,1536+y)>>>24)>=128)lateOpaque++;
                int a=image.getRGB(1536+x1,512+y),b=image.getRGB(x1,1024+y),alpha=a>>>24;
                if(alpha>0&&alpha<250)soft++;if(a!=b)different++;
            }
            require(soft>10000&&different>10000,"material is a hard cutout or repeated static sprite");
            require(lateOpaque<512*512/10,"350ms material obscures the card instead of breaking into sparse fragments");
        }
        var pool=new LightningAnimationRenderer.Pool();var state=pool.acquire(p,99);var x=state.x;pool.release(state);var reused=pool.acquire(p,100);
        require(reused==state&&reused.x==x,"pool did not reuse arrays");require(Arrays.stream(reused.buckets).allMatch(b->b==Long.MIN_VALUE),"stale arc cache");
        var simultaneous=pool.acquire(p,101);require(simultaneous!=state&&simultaneous.x!=state.x,"simultaneous effects share geometry");
        long isolated=mesh(state,216,BattleAnimationEngine.Quality.HIGH,126);mesh(simultaneous,350,BattleAnimationEngine.Quality.HIGH,126);
        require(isolated==mesh(state,216,BattleAnimationEngine.Quality.HIGH,126),"another effect corrupts geometry");
        for(int fps:List.of(30,60,120)){
            state.reset(p,173);
            for(int frame=0;frame<=fps;frame++)mesh(state,frame*1000.0/fps,BattleAnimationEngine.Quality.HIGH,LightningAnimationRenderer.ALL_LAYERS);
            for(double time:List.of(50.,83.,116.,150.,216.,283.,350.,450.,583.,650.)){
                long fromHistory=mesh(state,time,BattleAnimationEngine.Quality.HIGH,LightningAnimationRenderer.ALL_LAYERS);
                var direct=new LightningAnimationRenderer.State();direct.reset(p,173);
                require(fromHistory==mesh(direct,time,BattleAnimationEngine.Quality.HIGH,LightningAnimationRenderer.ALL_LAYERS),"frame-rate/seek dependent geometry");
            }
        }
        state.reset(p,9);for(int layer=1;layer<=6;layer++)require(mesh(state,250,BattleAnimationEngine.Quality.HIGH,1<<layer)!=1,"empty layer "+layer);
        require(mesh(state,150,BattleAnimationEngine.Quality.OFF,126)==1,"off still draws");require(mesh(state,650,BattleAnimationEngine.Quality.HIGH,126)==1,"expired effect still draws");
        state.sample(150);var buckets=state.buckets.clone();state.sample(170);int changed=0;for(int i=0;i<p.mainArcCount();i++)if(buckets[i]!=state.buckets[i])changed++;
        require(changed>0&&changed<p.mainArcCount(),"arcs refresh in lockstep");
        for(String key:List.of("durationMs","flashStartMs","peakStartMs","damagePopupMs","fadeStartMs","mainFlashEndMs","arcRefreshMs","intensity")){
            var invalid=new JsonObject();invalid.addProperty(key,Double.NaN);try{ElectricImpactProfile.parse(invalid);throw new AssertionError("accepted NaN "+key);}catch(IllegalArgumentException expected){}
        }
        System.out.println("ElectricImpactTest passed: 650ms phases, real-time seek determinism at 30/60/120 Hz, eight layers, asynchronous branches, pool reuse, bounds, off, expiry, parameter validation");
    }
}
