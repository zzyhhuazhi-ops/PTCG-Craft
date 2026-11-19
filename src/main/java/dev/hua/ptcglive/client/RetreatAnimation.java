package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import dev.hua.ptcglive.view.MatchPresentation.Cue;
import dev.hua.ptcglive.view.MatchPresentation.Kind;
import java.util.Objects;

/** Confirmed retreat: outgoing slide and incoming lift share one real-time clock.
 * Pocket reference wQEQlaFtchA 243.9..244.6s; selection and status recovery are separate. */
final class RetreatAnimation {
    static final double LAND_MS=700, DURATION_MS=900;
    static final long DURATION_NS=900_000_000L;
    static boolean applies(Cue cue){return cue.kind()==Kind.MOVE&&cue.text().endsWith("·撤退");}
    static boolean incoming(Cue cue){return cue.text().startsWith("备战区→战斗位");}
    static boolean pair(Cue a,Cue b){
        return applies(a)&&applies(b)&&a.seat()!=null&&a.pokemon()!=null&&b.pokemon()!=null
            &&Objects.equals(a.seat(),b.seat())&&!a.pokemon().equals(b.pokemon())&&incoming(a)!=incoming(b);
    }
    static PocketTableCamera.Pose pose(PocketTableCamera.Pose from,PocketTableCamera.Pose to,boolean incoming,boolean opponent,double ms){
        if(ms<=0)return from;if(ms>=LAND_MS)return to;
        if(!incoming){double p=smooth(ms/450);return mix(from,to,p).move(0,Math.sin(Math.PI*p)*.14,0);}
        var peak=to.move(0,1.05,(opponent?1:-1)*to.height()*.48).scale(1.12).angles(18,0);
        return ms<220?mix(from,peak,smooth(ms/220)):mix(peak,to,smooth((ms-220)/(LAND_MS-220)));
    }
    static EvolutionAnimation.Pose flat(Rect from,Rect to,boolean incoming,boolean opponent,double ms){
        double p=incoming?smooth(ms/220):smooth(ms/450),q=smooth((ms-220)/(LAND_MS-220));
        double x=mix(cx(from),cx(to),p),y=mix(cy(from),cy(to),p),w=mix(from.width(),to.width(),p),h=mix(from.height(),to.height(),p);
        if(incoming){
            double peakY=cy(to)+(opponent?1:-1)*to.height()*.85;
            y=ms<220?mix(cy(from),peakY,p):mix(peakY,cy(to),q);
            w=ms<220?mix(from.width(),to.width()*1.12,p):mix(to.width()*1.12,to.width(),q);
            h=ms<220?mix(from.height(),to.height()*1.12,p):mix(to.height()*1.12,to.height(),q);
        }
        return new EvolutionAnimation.Pose(x,y,w,h,1,0,0,0);
    }
    private static PocketTableCamera.Pose mix(PocketTableCamera.Pose a,PocketTableCamera.Pose b,double p){
        return new PocketTableCamera.Pose(mix(a.x(),b.x(),p),mix(a.y(),b.y(),p),mix(a.z(),b.z(),p),
            mix(a.width(),b.width(),p),mix(a.height(),b.height(),p),mix(a.tilt(),b.tilt(),p),mix(a.yaw(),b.yaw(),p));
    }
    private static double cx(Rect r){return r.x()+r.width()/2.;}
    private static double cy(Rect r){return r.y()+r.height()/2.;}
    private static double mix(double a,double b,double p){return a+(b-a)*p;}
    private static double smooth(double p){p=Math.clamp(p,0,1);return p*p*(3-2*p);}
}
