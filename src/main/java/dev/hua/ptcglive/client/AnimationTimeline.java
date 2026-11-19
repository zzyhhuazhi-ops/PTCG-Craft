package dev.hua.ptcglive.client;

import com.google.gson.*;
import java.util.*;

/** Immutable millisecond tracks; has no world, network, sound or rule-engine dependencies. */
final class AnimationTimeline {
    record Key(double ms,double value,String easing,List<Double> bezier) { }
    record Event(double ms,String name) { }
    private final Map<String,List<Key>> tracks;
    private final List<Event> events;
    final double durationMs,impactMs;
    final String id,provenance;

    AnimationTimeline(JsonObject root) {
        if(root.get("schema").getAsInt()!=1)throw new IllegalArgumentException("Unknown animation schema");
        id=root.get("id").getAsString();provenance=root.get("provenance").getAsString();
        durationMs=number(root,"durationMs",1,30000);impactMs=number(root,"impactMs",1,durationMs);
        if(root.has("reviewRequired")&&root.get("reviewRequired").getAsBoolean())
            throw new IllegalArgumentException("Review draft animation before installing it");
        var parsed=new LinkedHashMap<String,List<Key>>();
        var source=root.getAsJsonObject("tracks");
        if(source.size()>64)throw new IllegalArgumentException("Too many animation tracks");
        for(var entry:source.entrySet()) {
            var list=new ArrayList<Key>();double last=-1;
            if(entry.getValue().getAsJsonArray().size()>4096)throw new IllegalArgumentException("Too many keyframes");
            for(var raw:entry.getValue().getAsJsonArray()) {
                var k=raw.getAsJsonObject();double ms=number(k,"ms",0,durationMs),value=number(k,"value",-10000,10000);
                if(ms<=last)throw new IllegalArgumentException("Keyframes must be strictly ordered");
                String easing=k.has("easing")?k.get("easing").getAsString():"LINEAR";
                if(!Set.of("LINEAR","EASE_IN","EASE_OUT","EASE_IN_OUT","BEZIER").contains(easing))
                    throw new IllegalArgumentException("Unknown easing "+easing);
                var control=new ArrayList<Double>();
                if(easing.equals("BEZIER")) {
                    var b=k.getAsJsonArray("bezier");
                    if(b==null||b.size()!=4)throw new IllegalArgumentException("Bezier requires x1,y1,x2,y2");
                    for(var v:b){double d=v.getAsDouble();if(!Double.isFinite(d)||d<0||d>1)throw new IllegalArgumentException("Bezier control outside [0,1]");control.add(d);}
                }
                list.add(new Key(ms,value,easing,List.copyOf(control)));last=ms;
            }
            if(list.isEmpty())throw new IllegalArgumentException("Empty track");
            parsed.put(entry.getKey(),List.copyOf(list));
        }
        tracks=Map.copyOf(parsed);
        var cues=new ArrayList<Event>();
        if(root.has("events"))for(var raw:root.getAsJsonArray("events")) {
            var e=raw.getAsJsonObject();cues.add(new Event(number(e,"ms",0,durationMs),e.get("name").getAsString()));
        }
        if(cues.size()>128)throw new IllegalArgumentException("Too many timeline events");
        cues.sort(Comparator.comparingDouble(Event::ms));events=List.copyOf(cues);
    }
    private static double number(JsonObject o,String key,double min,double max) {
        double n=o.get(key).getAsDouble();
        if(!Double.isFinite(n)||n<min||n>max)throw new IllegalArgumentException("Invalid "+key);
        return n;
    }
    double sample(String track,double ms,double fallback) {
        var keys=tracks.get(track);if(keys==null||!Double.isFinite(ms))return fallback;
        if(ms<=keys.getFirst().ms())return keys.getFirst().value();
        int low=1,high=keys.size();
        while(low<high){int mid=(low+high)>>>1;if(keys.get(mid).ms()<ms)low=mid+1;else high=mid;}
        if(low==keys.size())return keys.getLast().value();
        Key a=keys.get(low-1),b=keys.get(low);
        double p=(ms-a.ms())/(b.ms()-a.ms());
        p=switch(b.easing()) {
            case "EASE_IN" -> p*p*p;
            case "EASE_OUT" -> 1-Math.pow(1-p,3);
            case "EASE_IN_OUT" -> p*p*(3-2*p);
            case "BEZIER" -> bezier(p,b.bezier());
            default -> p;
        };
        return a.value()+(b.value()-a.value())*p;
    }
    private static double cubic(double t,double a,double b){double u=1-t;return 3*u*u*t*a+3*u*t*t*b+t*t*t;}
    private static double bezier(double p,List<Double> b) {
        double lo=0,hi=1;
        for(int i=0;i<28;i++){double t=(lo+hi)/2;if(cubic(t,b.get(0),b.get(2))<p)lo=t;else hi=t;}
        return cubic((lo+hi)/2,b.get(1),b.get(3));
    }
    List<Event> events(){return events;}
    boolean hasTrack(String name){return tracks.containsKey(name);}
    /** Cursor processes all crossed cues once, including low-FPS jumps. Skip emits none. */
    final class Cursor {
        private double previous=-1;
        List<Event> advance(double ms) {
            if(!Double.isFinite(ms)||ms<=previous)return List.of();
            double from=previous;previous=ms;
            return events.stream().filter(e->e.ms()>from&&e.ms()<=ms).toList();
        }
        void skip(){previous=Double.POSITIVE_INFINITY;}
    }
}
