package dev.hua.ptcglive.client;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import java.util.*;

/** A public discard arrival; the origin is retained only when this client actually saw it. */
final class DiscardAnimation {
    static final long DURATION_NS=270_000_000L;
    static final double LAND_MS=180;
    record Arrival(CardInstance card,Rect origin){}
    static Arrival claim(List<CardInstance> before,List<CardInstance> after,String definition,
                         Map<UUID,Rect> visibleOrigins,Set<UUID> claimed){
        for(var card:after)if(Objects.equals(definition,card.definitionId())&&!claimed.contains(card.instanceId())
            &&before.stream().noneMatch(old->old.instanceId().equals(card.instanceId()))){
            claimed.add(card.instanceId());return new Arrival(card,visibleOrigins.get(card.instanceId()));
        }
        return null;
    }
    static double progress(double ms){double p=Math.clamp(ms/LAND_MS,0,1);return p*p*(3-2*p);}
    static Rect flat(Rect from,Rect to,double ms){
        double p=progress(ms),arc=Math.sin(Math.PI*p)*from.height()*.22;
        return new Rect((int)Math.round(from.x()+(to.x()-from.x())*p),
            (int)Math.round(from.y()+(to.y()-from.y())*p-arc),
            (int)Math.round(from.width()+(to.width()-from.width())*p),
            (int)Math.round(from.height()+(to.height()-from.height())*p));
    }
}
