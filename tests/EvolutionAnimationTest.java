package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import dev.hua.ptcglive.view.MatchPresentation.*;
import java.util.*;

public final class EvolutionAnimationTest {
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static void near(double a,double b,String why){check(Math.abs(a-b)<.001,why);}
    public static void main(String[] args){
        var t=BattleAnimationEngine.evolution();var target=new Rect(280,70,86,120);var hand=new Rect(500,0,20,30);var arena=new Rect(80,20,560,420);
        var sounds=BattleAnimationEngine.sounds(t);
        check(sounds.equals(List.of(new BattleAnimationEngine.Sound("draw",83),new BattleAnimationEngine.Sound("evolve",267),new BattleAnimationEngine.Sound("play",1850))),"evolution audio must follow reveal, halo and landing flash, without attack-time remapping");
        check(t.impactMs<1850&&t.sample("landing.alpha",t.impactMs,0)==0,"card settlement must precede the landing flash");
        near(t.sample("orbit.alpha",250,0),0,"halo appears before the final front-face reveal");
        check(t.sample("orbit.alpha",267,0)>.95,"halo misses the reveal burst");
        near(t.sample("focus.alpha",150,0),0,"background dims during the initial public face");
        // Full-resolution reference: residual arcs at850/900ms, gone at917ms.
        check(t.sample("orbit.alpha",850,0)>0&&t.sample("orbit.alpha",900,0)>0,"residual arcs disappear before the reference tail");
        check(t.sample("orbit.alpha",917,0)==0&&t.sample("focus.alpha",950,0)>0,"background focus must fade independently after the shell");
        for(double ms:new double[]{1000,1250,t.durationMs,t.durationMs+1000})near(t.sample("focus.alpha",ms,0),0,"background focus remains during return or after expiry");
        // OFF and expired presentation must return before touching rendering state.
        EvolutionAnimation.behind(null,null,t,300,BattleAnimationEngine.Quality.OFF,0);
        EvolutionAnimation.behind(null,null,t,t.durationMs,BattleAnimationEngine.Quality.HIGH,0);
        var full=EvolutionAnimation.pose(t,hand,target,arena,600);
        check(full.front()&&full.height()>target.height(),"evolution must enlarge the front face");
        near(full.width()/full.height(),PtcgLiveTheme.CARD_ASPECT,"reveal must use card art aspect, not projected table bounds");
        var bench=new Rect(120,300,43,60);
        near(full.height(),EvolutionAnimation.pose(t,hand,bench,arena,600).height(),"bench evolution reveal must not shrink with the target slot");
        var benchEnd=EvolutionAnimation.pose(t,hand,bench,arena,t.durationMs);
        near(benchEnd.x(),141.5,"bench landing x changed");near(benchEnd.y(),330,"bench landing y changed");
        check(EvolutionAnimation.pose(t,hand,target,arena,100).front(),"first public face missing");
        check(!EvolutionAnimation.pose(t,hand,target,arena,183).front(),"second sleeve phase missing");
        check(EvolutionAnimation.pose(t,hand,target,arena,150).front(),"public face disappears before reference sleeve transition");
        check(!EvolutionAnimation.pose(t,hand,target,arena,216.666667).front(),"sleeve vanishes before final reveal");
        var edge=EvolutionAnimation.pose(t,hand,target,arena,225);
        check(edge.drawnWidth()<edge.width()*.04,"flip has no edge-on phase");
        // Perspective must alter the two vertical edges, not just squeeze a rectangle.
        var reveal=EvolutionAnimation.pose(t,hand,target,arena,400);
        double[][] corners=new double[4][2];int[] corner={0};
        EvolutionCardRenderer.Vertex cornerSink=(x,y,u,v)->{corners[corner[0]][0]=x;corners[corner[0]++][1]=y;};
        var projection=EvolutionCardRenderer.Projection.of(reveal);
        projection.vertex(cornerSink,0,0);projection.vertex(cornerSink,0,1);
        projection.vertex(cornerSink,1,0);projection.vertex(cornerSink,1,1);
        check(Math.abs((corners[1][1]-corners[0][1])-(corners[3][1]-corners[2][1]))>2,"reveal has no perspective foreshortening");
        var settled=EvolutionAnimation.pose(t,hand,target,arena,t.durationMs);
        EvolutionCardRenderer.Projection.of(settled).vertex((x,y,u,v)->{near(x,target.x(),"projected landing x");near(y,target.y(),"projected landing y");},0,0);
        for(double ms:new double[]{t.impactMs,t.durationMs,t.durationMs+1000}){
            var p=EvolutionAnimation.pose(t,hand,target,arena,ms);
            near(p.x(),323,"card misses target x");near(p.y(),130,"card misses target y");near(p.width(),86,"card remains enlarged after landing");near(p.height(),120,"card size discontinuity on expiry");
        }
        var evolve=new Cue(Kind.EVOLVE,null,null,0,"",null);var ability=new Cue(Kind.ABILITY,null,null,0,"",null);
        check(BattleAnimationTiming.offset(ability,List.of(evolve,ability))>=BattleAnimationTiming.duration(evolve),"ability overlaps unfinished evolution reveal");
        int[] counts=new int[4];
        for(var quality:BattleAnimationEngine.Quality.values()){
            int[] count={0};BattleEffectMesh.Sink sink=(x,y,color)->{
                check(Double.isFinite(x)&&Double.isFinite(y)&&Math.abs(x)<1000&&Math.abs(y)<1000,"unbounded landing geometry");count[0]++;
            };
            for(int fps:new int[]{30,60,120})for(int frame=0;frame<fps*3;frame++){
                double ms=frame*1000.0/fps;var a=EvolutionAnimation.pose(t,hand,target,arena,ms);
                check(a.width()>0&&a.height()>0&&a.drawnWidth()>0,"invalid pose");
                int[] projected={0};
                EvolutionCardRenderer.mesh(a,(x,y,u,v)->{
                    check(Double.isFinite(x)&&Double.isFinite(y)&&Math.abs(x)<1000&&Math.abs(y)<1000,"invalid projected vertex");
                    check(u>=0&&u<=1&&v>=0&&v<=1,"projected UV outside card");projected[0]++;
                });
                check(projected[0]==4*EvolutionCardRenderer.GRID*EvolutionCardRenderer.GRID,"projection work depends on frame or size");
                EvolutionCardRenderer.mesh(a,(x,y,u,v)->check(1-u>=0&&1-u<=1,"mirrored back UV outside card"));
                // Seeking backward must be identical; no frame-history integration.
                EvolutionAnimation.pose(t,hand,target,arena,ms+500);
                check(a.equals(EvolutionAnimation.pose(t,hand,target,arena,ms)),"frame history changes pose");
                count[0]=0;EvolutionAnimation.landing(sink,target,t,ms,quality);EvolutionAnimation.hoverGlints(sink,a,t,ms,quality);check(count[0]<1800,"particle work grows unbounded");
                if(ms>=t.durationMs||quality==BattleAnimationEngine.Quality.OFF)check(count[0]==0,"expired/disabled effect still emits");
            }
            count[0]=0;EvolutionAnimation.landing(sink,target,t,1950,quality);counts[quality.ordinal()]=count[0];
        }
        check(counts[0]<counts[1]&&counts[1]<counts[2]&&counts[3]==0,"quality settings do not reduce evolution work");
        System.out.println("EvolutionAnimationTest passed: reveal, flip, landing, sequencing, frame-rate independence, quality, expiry");
    }
}
