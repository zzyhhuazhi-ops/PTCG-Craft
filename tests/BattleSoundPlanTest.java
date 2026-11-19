package dev.hua.ptcglive.client;

import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.view.MatchPresentation.Cue;
import dev.hua.ptcglive.view.MatchPresentation.Kind;
import java.nio.file.*;
import java.util.*;
import com.google.gson.JsonParser;

public final class BattleSoundPlanTest {
    static Cue cue(Kind kind,String text) {return new Cue(kind,null,null,20,text,null);}
    static void require(boolean value,String why) {if(!value)throw new AssertionError(why);}
    public static void main(String[] args) throws Exception {
        var manifest=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/ptcglive_mc/sounds.json"))).getAsJsonObject();
        var names=new HashSet<String>();
        for(var type:EnergyType.values()) {
            var name=BattleSoundPlan.event(cue(Kind.DAMAGE,"招式伤害·"+type),type,false);
            require(names.add(name),"each type has a distinct original sound: "+type);
        }
        require(names.size()==11,"all eleven types");
        require(BattleSoundPlan.event(cue(Kind.DAMAGE,"伤害 +20"),EnergyType.FIRE,false).equals("counter"),"poison/counters must not use attacker sound");
        require(BattleSoundPlan.event(cue(Kind.ATTACK,"攻击失败"),null,false).equals("fail"),"failed attack must not play hit");
        require(BattleSoundPlan.event(cue(Kind.PLAY,"能量附着"),null,false).equals("energy"),"manual attachment uses energy sound");
        require(BattleSoundPlan.event(cue(Kind.KNOCKOUT,""),null,false)==null,"do not double-play leaving-stack KO");
        require(BattleSoundPlan.event(cue(Kind.STATUS,"中毒·解除"),null,false).equals("heal"),"cleared condition must not sound applied");
        for(var kind:Kind.values()) {
            String name=BattleSoundPlan.event(cue(kind,""),null,false);
            if(name!=null)names.add(name);
        }
        names.addAll(List.of("coin_land","win","lose","poison","burn","sleep","paralysis","confusion","hit_colorless"));
        names.addAll(TrainerPlayAnimation.sounds(false).stream().map(BattleAnimationEngine.Sound::name).toList());
        for(String name:List.of("draw","evolve","play","trainer_item"))require(manifest.getAsJsonObject("battle."+name).getAsJsonArray("sounds")
            .get(0).getAsJsonObject().get("preload").getAsBoolean(),"evolution cue must not decode on its first visible frame: "+name);
        for(String name:names)require(manifest.has("battle."+name),"unregistered sound "+name);
        for(var entry:manifest.entrySet())if(entry.getKey().startsWith("battle.")) {
            var path=entry.getValue().getAsJsonObject().getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString();
            byte[] bytes=Files.readAllBytes(Path.of("src/main/resources/assets/ptcglive_mc/sounds/"+path.split(":",2)[1]+".ogg"));
            require(bytes.length>100&&new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("OggS"),"invalid Ogg "+path);
        }
        require(BattleSoundPlan.compensatedOffsetNanos(267,61_000_000L)==206_000_000L,"device-reported latency must advance audio dispatch");
        require(BattleSoundPlan.compensatedOffsetNanos(83,120_000_000L)==0,"high latency must not schedule before confirmation");
        require(BattleSoundPlan.compensatedOffsetNanos(267,0)==267_000_000L,"unsupported device must preserve timeline");
        require(BattleSoundPlan.compensatedOffsetNanos(267,-1)==267_000_000L,"negative device latency must be ignored");
        require(BattleSoundPlan.compensatedOffsetNanos(267,Long.MAX_VALUE)==267_000_000L,"invalid device latency must not overflow or advance cue");
        System.out.println("BattleSoundPlanTest passed: eleven types, non-attack isolation, failed attacks, condition clearing, KO deduplication, asset manifest and bounded latency compensation");
    }
}
