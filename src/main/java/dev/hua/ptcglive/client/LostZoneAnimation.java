package dev.hua.ptcglive.client;
import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;

/** Public hand/selection transport using the existing Live-style zone portal. */
final class LostZoneAnimation {
    static final long DURATION_NS=(long)(ToolLostAnimation.END_MS*1e6);
    static EvolutionAnimation.Pose pose(Rect from,Rect to,double ms){
        var path=ToolLostAnimation.pose(from,to,ms);
        double p=Math.clamp((ms-ToolLostAnimation.START_MS)/(ToolLostAnimation.LAND_MS-ToolLostAnimation.START_MS),0,1);
        double ease=1-Math.pow(1-p,2),shrink=1-Math.pow(p,3)*.96;
        return new EvolutionAnimation.Pose(path.x(),path.y(),(from.width()+(to.width()-from.width())*ease)*shrink,
            (from.height()+(to.height()-from.height())*ease)*shrink,path.flip(),path.tilt(),path.yaw(),path.pitch());
    }
}
