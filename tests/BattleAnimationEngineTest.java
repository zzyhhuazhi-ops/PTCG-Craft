package dev.hua.ptcglive.client;

import com.google.gson.*;
import dev.hua.ptcglive.engine.field.EnergyType;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public final class BattleAnimationEngineTest {
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void near(double expected,double actual){require(Math.abs(expected-actual)<.00001,expected+" != "+actual);}
    static JsonObject fixture(){return JsonParser.parseString("""
        {"schema":1,"id":"test","provenance":"synthetic-test","element":"LIGHTNING","durationMs":1000,"impactMs":500,
         "tracks":{"x":[{"ms":0,"value":0},{"ms":1000,"value":100,"easing":"LINEAR"}]},
         "events":[{"ms":0,"name":"start"},{"ms":500,"name":"hit"},{"ms":900,"name":"end"}]}
        """).getAsJsonObject();}
    static BattleAnimationEngine.Definition definition(JsonObject o){return BattleAnimationEngine.Definition.parse(new StringReader(o.toString()));}
    static void rejected(JsonObject o){try{definition(o);throw new AssertionError("invalid configuration accepted");}catch(IllegalArgumentException|NullPointerException expected){}}
    public static void main(String[] args)throws Exception {
        for(String ease:List.of("LINEAR","EASE_IN","EASE_OUT","EASE_IN_OUT","BEZIER")){
            var json=fixture();var end=json.getAsJsonObject("tracks").getAsJsonArray("x").get(1).getAsJsonObject();end.addProperty("easing",ease);
            if(ease.equals("BEZIER"))end.add("bezier",JsonParser.parseString("[0.2,0.2,0.8,0.8]"));
            var t=definition(json).timeline();near(0,t.sample("x",-20,-1));near(100,t.sample("x",2000,-1));
            near(ease.equals("EASE_IN")?12.5:ease.equals("EASE_OUT")?87.5:50,t.sample("x",500,-1));
        }
        var t=definition(fixture()).timeline();var cursor=t.new Cursor();
        require(cursor.advance(0).size()==1,"time-zero cue lost");require(cursor.advance(950).size()==2,"low-FPS jump loses cues");
        require(cursor.advance(950).isEmpty()&&cursor.advance(200).isEmpty(),"duplicate or stale render replays cues");
        cursor.skip();require(cursor.advance(2000).isEmpty(),"skip emits events");
        var ticks=new AtomicLong(100);var clock=new BattlePresentationClock(ticks::get);
        ticks.set(200);near(200,clock.now());clock.toggle();ticks.set(300);near(400,clock.now());
        clock.toggle();ticks.set(400);near(800,clock.now());clock.toggle();ticks.set(500);near(900,clock.now());
        var generic=definition(fixture());var typed=fixture();typed.addProperty("moveType","beam");
        var exact=fixture();exact.add("bindings",JsonParser.parseString("[{\"cardId\":\"sv-test\",\"moveName\":\"Test\"}]"));
        var beam=definition(typed);var special=definition(exact);
        BattleAnimationEngine.replace(List.of(generic,beam,special));
        require(BattleAnimationEngine.resolve("sv-test","Test","beam",EnergyType.LIGHTNING)==special,"dedicated binding priority");
        require(BattleAnimationEngine.resolve("other","Test","beam",EnergyType.LIGHTNING)==beam,"move template priority");
        require(BattleAnimationEngine.resolve(null,null,null,EnergyType.LIGHTNING)==generic,"element fallback");
        require(BattleAnimationEngine.resolve(null,null,null,EnergyType.FIRE)!=generic,"different type uses common introduction, not lightning override");
        BattleAnimationEngine.replace(List.of());
        var lightning=BattleAnimationEngine.resolve(null,null,null,EnergyType.LIGHTNING);
        require(lightning.timeline().provenance.equals("original-prototype"),"unmeasured sample presented as reference");
        var chargeSounds=BattleAnimationEngine.sounds(lightning,false,2.1);
        var hitSounds=BattleAnimationEngine.sounds(lightning,true,2.1);
        require(chargeSounds.size()==1&&chargeSounds.getFirst().name().equals("attack"),"declaration emits hit audio");
        require(hitSounds.size()==1&&hitSounds.getFirst().name().equals("hit_lightning"),"confirmed hit lacks configured audio");
        near(lightning.electric().flashStartMs(),hitSounds.getFirst().offsetMs());
        for(double contact:List.of(.65,1.2,2.1)){
            near(lightning.timeline().impactMs,BattleAnimationEngine.attackTime(lightning,contact,contact));
            near(0,BattleAnimationEngine.value(lightning,"card.lift",BattleAnimationEngine.attackTime(lightning,contact+.37,contact),-1));
        }
        // Same elapsed time must return identical pose regardless of the previous frame rate.
        for(int fps:List.of(15,30,60,144))for(int i=0;i<fps;i++){
            double ms=i*1000.0/fps;require(Double.isFinite(lightning.timeline().sample("card.scale",ms,1)),"nonfinite pose");
        }
        for(String key:List.of("durationMs","impactMs")){var invalid=fixture();invalid.addProperty(key,-1);rejected(invalid);}
        var invalid=fixture();invalid.addProperty("reviewRequired",true);rejected(invalid);
        invalid=fixture();invalid.getAsJsonObject("tracks").getAsJsonArray("x").get(1).getAsJsonObject().addProperty("ms",0);rejected(invalid);
        invalid=fixture();invalid.getAsJsonObject("tracks").getAsJsonArray("x").get(1).getAsJsonObject().addProperty("value",Double.NaN);rejected(invalid);
        int[] counts=new int[4];
        var effect=new LightningAnimationRenderer.State();effect.reset(lightning.electric(),42);
        for(var quality:BattleAnimationEngine.Quality.values()){
            for(double time:new double[]{-.1,0,.04,.11,.15,.3,.65,2}){
                int[] count={0};
                BattleEffectMesh.Sink sink=(x,y,color)->{
                    require(Double.isFinite(x)&&Double.isFinite(y)&&Math.abs(x)<800&&Math.abs(y)<800,"unbounded lightning geometry");count[0]++;
                };
                LightningAnimationRenderer.impact(sink,effect,0,0,160,time*1000,quality,LightningAnimationRenderer.ALL_LAYERS);
                require(count[0]<14000&&count[0]%4==0,"lightning batch budget");
                if(time==.15)counts[quality.ordinal()]=count[0];
                if(time<0||time>1)require(count[0]==0,"expired particles retained");
            }
        }
        require(counts[0]<counts[1]&&counts[1]<counts[2],"quality levels do not reduce work");
        for(var type:EnergyType.values()) {
            var d=BattleAnimationEngine.resolve(null,null,null,type);
            require(d!=null&&d.timeline().hasTrack("reveal.scale"),"missing common introduction for "+type);
            if(type!=EnergyType.LIGHTNING)require(BattleAnimationEngine.sounds(d,true,1.55).isEmpty(),"common introduction overrides typed impact sound");
        }
        var a=new java.util.UUID(0,1);var b=new java.util.UUID(0,2);
        var hit=new dev.hua.ptcglive.view.MatchPresentation.Cue(dev.hua.ptcglive.view.MatchPresentation.Kind.DAMAGE,a,a,30,"招式伤害·LIGHTNING",null);
        var hit2=new dev.hua.ptcglive.view.MatchPresentation.Cue(dev.hua.ptcglive.view.MatchPresentation.Kind.DAMAGE,a,b,30,"招式伤害·LIGHTNING",null);
        require(BattleAnimationEngine.moveType(List.of(hit)).equals("impact"),"single hit classification");
        require(BattleAnimationEngine.moveType(List.of(hit,hit)).equals("multi_hit"),"repeated hit classification");
        require(BattleAnimationEngine.moveType(List.of(hit,hit2)).equals("multi_target"),"spread classification");
        var counters=new dev.hua.ptcglive.view.MatchPresentation.Cue(dev.hua.ptcglive.view.MatchPresentation.Kind.DAMAGE,a,b,30,"伤害指示物",null);
        require(BattleAnimationEngine.moveType(List.of(counters)).isEmpty(),"damage counters inferred as attack hits");
        for(String name:List.of("fire","water")) {
            BattleAnimationEngine.Definition d;
            try(var reader=new InputStreamReader(BattleAnimationEngineTest.class.getResourceAsStream("/assets/ptcglive_mc/battle_animations/"+name+".json"),java.nio.charset.StandardCharsets.UTF_8)) {
                d=BattleAnimationEngine.Definition.parse(reader);
            }
            require(BattleMotion.hpProgress(d.timeline(),400)==0,"elemental HP changes before the hit effect clears");
            require(BattleMotion.hpProgress(d.timeline(),1400)==1,"elemental HP does not reach confirmed damage");
            double hpStart=d.timeline().sample("hp.startMs",0,0),hpEnd=d.timeline().sample("hp.endMs",0,0);
            require(Math.abs(BattleMotion.hpProgress(d.timeline(),(hpStart+hpEnd)/2)-.5)<.001,"HP transition midpoint");
            for(var quality:BattleAnimationEngine.Quality.values())for(double time:List.of(-.1,0.,.07,.15,.3,.7,2.)) {
                var vertices=new ArrayList<double[]>();
                ElementalImpactRenderer.draw((x,y,color)->{require(Double.isFinite(x)&&Double.isFinite(y)&&Math.abs(x)<480&&Math.abs(y)<480,"invalid elemental geometry");vertices.add(new double[]{x,y});},d,EnergyType.valueOf(name.toUpperCase(Locale.ROOT)),0,0,160,time,quality);
                require(vertices.size()<6000&&vertices.size()%4==0,"elemental batch budget");
                if(time<0||time>1.5)require(vertices.isEmpty(),"expired elemental effect draws");
                for(int i=0;i<vertices.size();i+=4){double area=0;for(int j=0;j<4;j++){var v=vertices.get(i+j);var w=vertices.get(i+(j+1)%4);area+=v[0]*w[1]-w[0]*v[1];}require(area<.00001,"elemental back-facing quad");}
            }
        }
        System.out.println("BattleAnimationEngineTest passed: interpolation, clock speeds, event cursor, matching, invalid resources, confirmed-hit timing");
    }
}
