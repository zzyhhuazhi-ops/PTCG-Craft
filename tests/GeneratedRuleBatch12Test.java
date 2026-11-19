import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Printed costs and generated programs; controlled hosts neutralize weakness and isolate ability effects. */
public final class GeneratedRuleBatch12Test {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static AttackDefinition attack(String id,int slot){
        JsonObject card;
        if(Set.of("sm11_en_071","sm12_en_001").contains(id)){
            try(var in=GeneratedRuleBatch12Test.class.getResourceAsStream("/rule-data-batch12-draft-attacks.json")){
                card=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("cards").asList().stream().map(JsonElement::getAsJsonObject).filter(c->c.get("id").getAsString().equals(id)).findFirst().orElseThrow();
            }catch(IOException e){throw new UncheckedIOException(e);}
        }else card=GeneratedRuleBatch8Test.entry(id);
        var json=card.getAsJsonArray("attacks").get(slot).getAsJsonObject().deepCopy();
        check(json.get("effectSupport").getAsString().equals("SUPPORTED"),id+" attack unsupported");
        json.addProperty("damageType","COLORLESS");
        var a=BundledCombatData.attack(json,EffectRegistry.standard());AttackProgramRules.validate(a);return a;
    }
    static ServerMatch fixture(AttackDefinition attack,EnergyType energy){return GeneratedRuleBatch8Test.fixture(List.of(),energy,EnergyType.GRASS,GeneratedRuleBatch8Test.trainer("swsh5_en_124"),attack);}
    static UUID active(ServerMatch m,UUID owner){return m.activePokemonView(owner).pokemonId();}
    static UUID bench(ServerMatch m,UUID owner){return m.benchPokemonView(owner).getFirst().pokemonId();}
    static void units(ServerMatch m,int count){
        while(count>0){boolean found=false;
            for(String id:count>=2?List.of("special","energy"):List.of("energy")){
                var card=m.privateView(A).hand().stream().filter(c->c.definitionId().equals(id)).findFirst();var zone=EffectSourceZone.HAND;
                if(card.isEmpty()){card=m.privateView(A).deck().stream().filter(c->c.definitionId().equals(id)).findFirst();zone=EffectSourceZone.DECK;}
                if(card.isEmpty())continue;
                var c=card.get();m.attachEnergyByEffect(A,A,zone,c.instanceId(),A,active(m,A));count-=id.equals("special")?2:1;found=true;break;
            }
            check(found,"fixture exhausted Energy cards");
        }
    }
    static void done(ServerMatch m){check(m.currentPlayer().equals(B),"generated attack did not end turn");}
    public static void main(String[] args){thresholds();caps();benchDamage();drawAndMill();fullHealing();switchAndStatus();System.out.println("GeneratedRuleBatch12Test passed: printed-cost extra Energy thresholds/caps, typed multi-unit supply, conditional Bench damage/draw/mill/heal, switch, status and GX limits");}
    static void thresholds(){
        for(String id:List.of("sv10_en_069","swsh1_en_110"))for(int extra:List.of(0,1,2)){
            boolean sand=id.startsWith("swsh");var a=attack(id,sand?0:1);var m=fixture(a,sand?EnergyType.FIGHTING:EnergyType.LIGHTNING);units(m,(sand?2:3)+extra);m.attack(A,a.id());
            check(m.activePokemonView(B).damage()==(sand?60+(extra>=1?70:0):180+(extra>=2?100:0)),"printed threshold wrong: "+id);done(m);
        }
    }
    static void caps(){
        for(String id:List.of("swsh3_en_115","swsh10-5_en_048"))for(int n:List.of(3,4,5,6)){
            boolean dark=id.equals("swsh3_en_115");var a=attack(id,0);var m=fixture(a,dark?EnergyType.DARKNESS:EnergyType.METAL);units(m,n);m.attack(A,a.id());
            check(m.activePokemonView(B).damage()==(dark?170:160)+Math.min(2,n-3)*(dark?50:60),"cap or printed typed payment wrong");done(m);
        }
    }
    static void benchDamage(){
        for(String id:List.of("swsh8_en_041","sm9_en_033","sm9_en_160"))for(boolean extra:List.of(false,true)){
            boolean water=id.equals("sm9_en_160"),fire=id.startsWith("swsh");var a=attack(id,1);var m=fixture(a,water?EnergyType.WATER:fire?EnergyType.FIRE:EnergyType.LIGHTNING);units(m,(water?7:5)+(extra?1:0));
            m.attack(A,a.id());if(extra&&!water){check(m.effectPokemonPrompt(A)!=null,"conditional target prompt missing");GeneratedRuleBatch10Test.target(m,bench(m,B));}
            else done(m);
            check(m.activePokemonView(B).damage()==(water?10:fire?90:200),"base damage changed");check(m.benchPokemonView(B).getFirst().damage()==(extra?(water?100:fire?180:170):0),"conditional Bench damage wrong");done(m);
            if(!fire){m.endTurn(B);BeforeDamageAttackTest.reject(()->m.attack(A,a.id()));}
        }
    }
    static void drawAndMill(){
        for(boolean extra:List.of(false,true)){
            var a=attack("sm9_en_120",2);var m=fixture(a,EnergyType.GRASS);units(m,extra?5:4);
            m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().skip(2).map(CardInstance::instanceId).toList());m.attack(A,a.id());check(m.privateView(A).hand().size()==(extra?10:2),"Megaton Friends draw condition");done(m);
            a=attack("sm11_en_126",1);m=fixture(a,EnergyType.DARKNESS);units(m,extra?10:9);int deck=m.privateView(B).deck().size(),discard=m.privateView(B).discard().size();m.attack(A,a.id());
            check(m.privateView(B).discard().size()==discard+(extra?Math.min(15,deck):0),"Gigafall mill count/condition");done(m);
        }
    }
    static void fullHealing(){
        for(String id:List.of("sm1_en_042","sm3_en_130","sm11_en_071","sm12_en_001"))for(boolean extra:List.of(false,true)){
            int slot=id.equals("sm12_en_001")?1:id.equals("sm11_en_071")?0:2;var a=attack(id,slot);var m=fixture(a,id.equals("sm11_en_071")?EnergyType.PSYCHIC:EnergyType.GRASS);
            int cost=a.energyCost().values().stream().mapToInt(Integer::intValue).sum();boolean conditional=id.startsWith("sm11")||id.startsWith("sm12");units(m,cost+(extra?(id.startsWith("sm12")?2:1):0));
            m.placeDamageCountersByEffect(A,A,active(m,A),8);m.placeDamageCountersByEffect(A,A,bench(m,A),3);m.placeDamageCountersByEffect(A,B,active(m,B),2);
            m.attack(A,a.id());boolean heal=!conditional||extra;check(m.activePokemonView(A).damage()==(heal?0:80),"self all-heal wrong");check(m.benchPokemonView(A).getFirst().damage()==(heal&&!id.equals("sm3_en_130")?0:30),"whole-field or self-only healing wrong");
            check(m.activePokemonView(B).damage()>0,"healed opponent");done(m);
        }
    }
    static void switchAndStatus(){
        for(boolean extra:List.of(false,true)){
            var a=attack("sm11_en_054",1);var m=fixture(a,EnergyType.LIGHTNING);units(m,extra?5:4);UUID old=active(m,A),replacement=bench(m,A);m.attack(A,a.id());check(m.activePokemonView(B).damage()==(extra?250:150),"Lightning Ride bonus used switched-in Pokemon");
            var p=m.effectPokemonPrompt(A);check(p!=null,"mandatory switch prompt missing");m.chooseEffectPokemon(A,p.resolutionId(),p.step(),replacement);check(!active(m,A).equals(old),"Lightning Ride did not switch");done(m);
            a=attack("sm12_en_038",1);m=fixture(a,EnergyType.WATER);units(m,extra?6:5);m.attack(A,a.id());check(m.activePokemonView(B).damage()==(extra?250:100)&&m.activePokemonView(B).conditions().contains(SpecialCondition.PARALYZED),"Bubble Launcher incorrectly conditioned base paralysis");done(m);
        }
    }
}
