package dev.hua.ptcglive.client;

import com.google.gson.*;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.view.MatchPresentation.Cue;
import dev.hua.ptcglive.view.MatchPresentation.Kind;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Samples existing confirmed-event queues. Never emits gameplay actions or waits for visuals. */
final class BattleAnimationEngine {
    enum Quality {
        LOW(3),MEDIUM(5),HIGH(8),OFF(0);
        final int branches;
        Quality(int branches){this.branches=branches;}
        Quality next(){return values()[(ordinal()+1)%values().length];}
        String label(){return switch(this){case LOW->"低";case MEDIUM->"中";case HIGH->"高";case OFF->"关";};}
    }
    record Binding(String cardId,String moveName) { }
    record Sound(String name,double offsetMs) { }
    record Definition(AnimationTimeline timeline,EnergyType element,String moveType,List<Binding> bindings,ElectricImpactProfile electric) {
        static Definition parse(Reader reader) {
            var root=JsonParser.parseReader(reader).getAsJsonObject();
            var binds=new ArrayList<Binding>();
            if(root.has("bindings"))for(var raw:root.getAsJsonArray("bindings")){
                var b=raw.getAsJsonObject();binds.add(new Binding(b.get("cardId").getAsString(),b.get("moveName").getAsString()));
            }
            String element=root.get("element").getAsString();
            return new Definition(new AnimationTimeline(root),element.equals("ANY")?null:EnergyType.valueOf(element),
                root.has("moveType")?root.get("moveType").getAsString():"",List.copyOf(binds),ElectricImpactProfile.parse(root.getAsJsonObject("electricImpact")));
        }
    }
    private static final Definition FALLBACK=loadFallback("lightning");
    private static final Definition COMMON=loadFallback("attack_common");
    private static final Definition EVOLUTION=loadFallback("evolution");
    private static final Definition TURN=loadFallback("turn_banner");
    private static final Definition KNOCKOUT=loadFallback("knockout");
    private static final Definition TRAINER=loadFallback("trainer_play");
    private static final Definition TOOL=loadFallback("trainer_tool");
    private static final Definition ENERGY=loadFallback("energy_attach");
    private static final Definition ENERGY_TRANSFER=loadFallback("energy_transfer");
    private static final Definition ENERGY_DISCARD=loadFallback("energy_discard");
    private static volatile List<Definition> definitions=List.of(FALLBACK,COMMON);
    private BattleAnimationEngine() { }
    static AnimationTimeline evolution() {
        for(var d:definitions)if(d.moveType().equals("scene:evolution"))return d.timeline();
        return EVOLUTION.timeline();
    }
    static AnimationTimeline turn() {
        for(var d:definitions)if(d.moveType().equals("scene:turn"))return d.timeline();
        return TURN.timeline();
    }
    static AnimationTimeline knockout() {
        for(var d:definitions)if(d.moveType().equals("scene:knockout"))return d.timeline();
        return KNOCKOUT.timeline();
    }
    static AnimationTimeline trainer() {
        for(var d:definitions)if(d.moveType().equals("scene:trainer"))return d.timeline();
        return TRAINER.timeline();
    }
    static AnimationTimeline tool() {
        for(var d:definitions)if(d.moveType().equals("scene:tool"))return d.timeline();
        return TOOL.timeline();
    }
    static AnimationTimeline energy() {
        for(var d:definitions)if(d.moveType().equals("scene:energy"))return d.timeline();
        return ENERGY.timeline();
    }
    static AnimationTimeline energyTransfer() {
        for(var d:definitions)if(d.moveType().equals("scene:energy_transfer"))return d.timeline();
        return ENERGY_TRANSFER.timeline();
    }
    static AnimationTimeline energyDiscard() {
        for(var d:definitions)if(d.moveType().equals("scene:energy_discard"))return d.timeline();
        return ENERGY_DISCARD.timeline();
    }
    private static Definition loadFallback(String name) {
        try(var stream=BattleAnimationEngine.class.getResourceAsStream("/assets/ptcglive_mc/battle_animations/"+name+".json")) {
            if(stream==null)throw new IllegalStateException("Missing bundled animation "+name);
            return Definition.parse(new InputStreamReader(stream,StandardCharsets.UTF_8));
        } catch(IOException e){throw new UncheckedIOException(e);}
    }
    static void replace(List<Definition> next){definitions=next.isEmpty()?List.of(FALLBACK,COMMON):List.copyOf(next);}
    static Definition resolve(String card,String move,String moveType,EnergyType element) {
        var snapshot=definitions;
        for(var d:snapshot)if(d.bindings().stream().anyMatch(b->Objects.equals(card,b.cardId())&&Objects.equals(move,b.moveName())))return d;
        for(var d:snapshot)if(!d.moveType().isEmpty()&&d.moveType().equals(moveType)&&(d.element()==element||d.element()==null))return d;
        for(var d:snapshot)if(d.bindings().isEmpty()&&d.moveType().isEmpty()&&d.element()==element)return d;
        if(element==EnergyType.LIGHTNING)return FALLBACK;
        for(var d:snapshot)if(d.bindings().isEmpty()&&d.moveType().isEmpty()&&d.element()==null)return d;
        return COMMON;
    }
    static String moveType(List<Cue> cues) {
        UUID target=null;
        int hits=0;
        boolean heal=false,status=false;
        for(var cue:cues){
            if(cue.kind()==Kind.DAMAGE&&cue.text().startsWith("招式伤害·")){
                hits++;
                if(cue.pokemon()==null)continue;
                if(target!=null&&!target.equals(cue.pokemon()))return "multi_target";
                target=cue.pokemon();
            }
            if(cue.kind()==Kind.HEAL)heal=true;
            if(cue.kind()==Kind.STATUS)status=true;
        }
        if(hits>1)return "multi_hit";
        if(hits==1)return "impact";
        return heal?"heal":status?"status":"";
    }
    static double attackTime(Definition d,double elapsedSeconds,double confirmedContactSeconds) {
        double contact=Math.max(.001,confirmedContactSeconds*1000),ms=elapsedSeconds*1000;
        return ms<=contact?ms*d.timeline().impactMs/contact:d.timeline().impactMs+ms-contact;
    }
    static double value(Definition d,String track,double ms,double fallback){return d==null?fallback:d.timeline().sample(track,ms,fallback);}
    /** Scene events already use absolute presentation milliseconds, including AI. */
    static List<Sound> sounds(AnimationTimeline timeline) {
        return timeline.events().stream().filter(e->e.name().matches("sound\\.[a-z0-9_]+"))
            .map(e->new Sound(e.name().substring(6),e.ms())).toList();
    }
    static List<Sound> sounds(Definition d,boolean confirmedDamage,double contactSeconds) {
        if(d==null)return List.of();
        var t=d.timeline();
        return t.events().stream().filter(e->e.name().matches("sound\\.[a-z0-9_]+"))
            .filter(e->confirmedDamage?e.ms()>=t.impactMs:e.ms()<t.impactMs)
            .map(e->new Sound(e.name().substring(6),confirmedDamage?e.ms()-t.impactMs:e.ms()/t.impactMs*contactSeconds*1000)).toList();
    }
}
