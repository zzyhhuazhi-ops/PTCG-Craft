package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import dev.hua.ptcglive.view.MatchPresentation.Cue;
import dev.hua.ptcglive.view.MatchPresentation.Kind;
import java.util.Objects;

/** Shared front-facing reveal for effect exchange and a single KO replacement.
 * References: wQEQlaFtchA129.3..130.9s and idmdbTxFvFQ181.45..183.05s. */
final class SwitchAnimation {
    static final double RETURN_MS=1000, OUT_START_MS=1100, OUT_END_MS=1500, LAND_MS=1600;
    static final long DURATION_NS=1_800_000_000L;
    static boolean replacement(Cue c){return c.kind()==Kind.MOVE&&c.text().equals("备战区→战斗位·替补");}
    static boolean applies(Cue c){return c.kind()==Kind.MOVE&&(c.text().endsWith("·效果")||replacement(c));}
    static boolean incoming(Cue c){return c.text().startsWith("备战区→战斗位");}
    static boolean pair(Cue a,Cue b){
        return applies(a)&&applies(b)&&!replacement(a)&&!replacement(b)&&a.seat()!=null&&a.pokemon()!=null&&b.pokemon()!=null
            &&Objects.equals(a.seat(),b.seat())&&!a.pokemon().equals(b.pokemon())&&incoming(a)!=incoming(b);
    }
    static double landing(double ms){return smooth((ms-RETURN_MS)/(LAND_MS-RETURN_MS));}
    static double outgoingTime(double ms){return Math.clamp((ms-OUT_START_MS)/(OUT_END_MS-OUT_START_MS),0,1)*700;}
    static EvolutionAnimation.Pose reveal(Rect from,Rect stage,double ms){
        double p=smooth(ms/350),flip=ms<100?1-smooth(ms/100):smooth((ms-100)/150);
        return new EvolutionAnimation.Pose(mix(cx(from),cx(stage),p),mix(cy(from),cy(stage),p),
            mix(from.width(),stage.width(),p),mix(from.height(),stage.height(),p),flip,
            -8*Math.sin(Math.PI*p),0,0);
    }
    static EvolutionAnimation.Pose flatLanding(Rect stage,Rect target,double ms){
        double p=landing(ms);
        return new EvolutionAnimation.Pose(mix(cx(stage),cx(target),p),mix(cy(stage),cy(target),p),
            mix(stage.width(),target.width(),p),mix(stage.height(),target.height(),p),1,0,0,0);
    }
    static EvolutionAnimation.Pose replacementReveal(Rect from,Rect stage,double ms){
        double p=smooth(ms/350),flip=ms<220?1-smooth(ms/220):smooth((ms-220)/130);
        return new EvolutionAnimation.Pose(mix(cx(from),cx(stage),p),mix(cy(from),cy(stage),p),
            mix(from.width(),stage.width(),p),mix(from.height(),stage.height(),p),flip,
            -8*Math.sin(Math.PI*p),0,0);
    }
    static double replacementLift(double ms){return Math.sin(Math.PI*landing(ms))*1.6;}
    static void replacementGlow(BattleEffectMesh.Sink out,Rect stage,double ms,boolean low){
        glow(out,stage,300+(ms-150)*2,low);
        double pulse=Math.max(0,1-Math.abs(ms-250)/110);
        if(pulse>0){
            BattleEffectMesh.glow(out,cx(stage),cy(stage),stage.height()*.75,alpha(0xFF8FEAFF,pulse*.65));
            BattleEffectMesh.glow(out,cx(stage),cy(stage),stage.height()*.48,alpha(0xFFF6FFFF,pulse));
        }
    }
    static void glow(BattleEffectMesh.Sink out,Rect stage,double ms,boolean low){
        if(ms<300||ms>=900)return;
        double p=(ms-300)/600,a=Math.sin(Math.PI*p),r=stage.height()*(.48+.16*smooth(p));
        BattleEffectMesh.ring(out,cx(stage),cy(stage),r,1,stage.height()*.07,alpha(0xFF57BFFF,a*.32));
        BattleEffectMesh.ring(out,cx(stage),cy(stage),r,1,stage.height()*.018,alpha(0xFFF0FFFF,a));
        if(!low)for(int i=0;i<12;i++){
            double angle=i*Math.PI/6+ms*.0005,x=cx(stage)+Math.cos(angle)*r,y=cy(stage)+Math.sin(angle)*r;
            BattleEffectMesh.glow(out,x,y,stage.height()*.08,alpha(0xFFA3E8FF,a*.65));
        }
    }
    private static int alpha(int c,double a){return ((int)(255*Math.clamp(a,0,1))<<24)|(c&0xFFFFFF);}
    private static double cx(Rect r){return r.x()+r.width()/2.;}
    private static double cy(Rect r){return r.y()+r.height()/2.;}
    private static double mix(double a,double b,double p){return a+(b-a)*p;}
    private static double smooth(double p){p=Math.clamp(p,0,1);return p*p*(3-2*p);}
}
