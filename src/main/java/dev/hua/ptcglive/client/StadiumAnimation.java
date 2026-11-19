package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.engine.MatchSession;
import dev.hua.ptcglive.view.MatchPresentation.Cue;

/** Public stadium replacement only; no rules or authoritative pile mutations. */
final class StadiumAnimation {
    static final double APPEAR_MS=1700, LAND_MS=2200, EXIT_START_MS=2600, EXIT_END_MS=3200;
    static final long DURATION_NS=3_400_000_000L;
    static long duration(Cue cue,boolean replacing){return cue.cardId()==null?800_000_000L:replacing?DURATION_NS:2_500_000_000L;}
    record Departure(CardInstance card,boolean lost){}
    static final double REJECTED_LAND_MS=2300;
    static Rect slot(PtcgLiveTableLayout l){return new Rect(l.stadiumX(),l.stadiumY(),l.benchWidth(),l.benchHeight());}
    /** Two public halves share the existing Stadium area and retain individual hit targets. */
    static Rect partSlot(PtcgLiveTableLayout l,int count,int index){
        var slot=slot(l);if(count<2)return slot;
        int w=Math.max(1,Math.round(slot.width()*.8f)),h=Math.max(1,Math.round(w*88/63f));
        return new Rect(slot.x()+(slot.width()-2*w)/2+index*w,slot.y()+(slot.height()-h)/2,w,h);
    }
    static Departure departure(MatchSession.Snapshot before,MatchSession.Snapshot after){
        var old=before.game().stadium();var next=after.game().stadium();
        if(old==null||next!=null&&old.instanceId().equals(next.instanceId()))return null;
        boolean lost=after.game().players().stream().flatMap(p->p.lostZone().stream()).anyMatch(c->c.instanceId().equals(old.instanceId()));
        return new Departure(old,lost);
    }
    /** A played stadium may be discarded by Chaotic Swell without ever entering the field. */
    static Departure rejected(MatchSession.Snapshot before,MatchSession.Snapshot after,Cue cue){
        if(cue.cardId()==null||after.game().stadium()!=null)return null;
        var known=new java.util.HashSet<java.util.UUID>();
        for(var p:before.game().players()){
            for(var c:p.discard())known.add(c.instanceId());
            for(var c:p.lostZone())known.add(c.instanceId());
        }
        if(before.game().stadium()!=null)known.add(before.game().stadium().instanceId());
        for(var p:after.game().players())for(boolean lost:new boolean[]{false,true})
            for(var c:lost?p.lostZone():p.discard())
                if(c.definitionId().equals(cue.cardId())&&!known.contains(c.instanceId()))return new Departure(c,lost);
        return null;
    }
    static double exitStart(Cue cue){return cue.cardId()==null?0:EXIT_START_MS;}
    static double exitEnd(Cue cue){return exitStart(cue)+EXIT_END_MS-EXIT_START_MS;}
    static double exit(Cue cue,double ms){return smooth((ms-exitStart(cue))/(EXIT_END_MS-EXIT_START_MS));}
    static double exitStart(Cue cue,boolean rejected){return rejected?1100:exitStart(cue);}
    static double exitEnd(Cue cue,boolean rejected){return exitStart(cue,rejected)+600;}
    static double exit(Cue cue,double ms,boolean rejected){return smooth((ms-exitStart(cue,rejected))/600);}
    static double rejectedFlight(double ms){return smooth((ms-APPEAR_MS)/(REJECTED_LAND_MS-APPEAR_MS));}
    static double appear(double ms){return smooth((ms-APPEAR_MS)/(LAND_MS-APPEAR_MS));}
    static Rect parked(Rect slot,double ms){
        double p=appear(ms);return new Rect(slot.x()-(int)(slot.width()*.8*p),slot.y(),slot.width(),slot.height());
    }
    static EvolutionAnimation.Pose flight(Rect from,Rect to,double p){
        return new EvolutionAnimation.Pose(mix(from.x()+from.width()/2.,to.x()+to.width()/2.,p),
            mix(from.y()+from.height()/2.,to.y()+to.height()/2.,p)-Math.sin(Math.PI*p)*from.height()*.35,
            mix(from.width(),to.width(),p),mix(from.height(),to.height(),p),1,-8*Math.sin(Math.PI*p),0,0);
    }
    private static double mix(double a,double b,double p){return a+(b-a)*p;}
    private static double smooth(double p){p=Math.clamp(p,0,1);return p*p*(3-2*p);}
}
