import com.google.gson.*;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.nio.file.*;
import java.util.*;

/** Printed modern clauses, tested against observable effects and rejected illegal choices. */
public final class ModernAttackClauseTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static final Map<String,JsonObject> RAW=load();
    static Map<String,JsonObject> load(){try{var out=new HashMap<String,JsonObject>();for(var line:Files.readAllLines(Path.of("outputs/pokemon-modern-attack-clause-programs.jsonl"))){var r=JsonParser.parseString(line).getAsJsonObject();out.put(r.get("id").getAsString(),r.getAsJsonObject("conversion").getAsJsonObject("combatEntry"));}return out;}catch(Exception e){throw new RuntimeException(e);}}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static void atomic(ServerMatch m,Runnable r){AttackDiscardUnitsTest.atomic(m,r);}
    static AttackDefinition attack(String id,String name){var profile=RAW.containsKey(id)?RAW.get(id):GeneratedRuleBatch8Test.entry(id);var a=profile.getAsJsonArray("attacks").asList().stream().map(JsonElement::getAsJsonObject).filter(x->x.get("id").getAsString().equals(name)).findFirst().orElseThrow().deepCopy();a.add("energyCost",new JsonObject());a.addProperty("damageType","COLORLESS");return BundledCombatData.attack(a,EffectRegistry.standard());}
    static ServerMatch game(String id,String name,UUID p,int units,List<AbilityDefinition> abilities,String pokemonName){var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),units==0?new EnergyProfile(List.of()):EnergyProfile.fixed(EnergyType.COLORLESS,units),attack(id,name),abilities,0,null,Map.of(),pokemonName);if(p.equals(B))m.endTurn(A);return m;}
    static ServerMatch game(String id,String name,UUID p){return game(id,name,p,2,List.of(),null);}
    static void choose(ServerMatch m,UUID p,List<CardInstance> cards){var q=m.effectCardPrompt(p);m.submitEffectCards(p,q.resolutionId(),q.step(),cards.stream().map(CardInstance::instanceId).toList());}
    static void optional(ServerMatch m,UUID p,boolean yes){if(m.hasPendingEffectChoice()){var q=m.effectChoicePrompt(p);m.chooseEffectOption(p,q.resolutionId(),q.step(),yes?0:1);}}
    public static void main(String[] args){benchTargets();tools();guards();evolution();returns();coins();moves();ai();System.out.println("ModernAttackClauseTest passed: both seats; Bench-only selected counters; 0/1/2 own tools; Ability guards and explicit exceptions; hand/deck evolution boundaries; optional Energy-unit returns including zero-unit exclusion; 0..3 tails with whole multi-unit card discard; basic-only move; 48 four-tier games");}
    static void benchTargets(){for(String id:List.of("swsh4_en_042","swshbsp_en_246","sv10_en_128"))for(UUID p:List.of(A,B)){
        String name=id.equals("swshbsp_en_246")?"pursuit-claw":id.equals("sv10_en_128")?"strike-the-sleeper":"targeted-skewer";
        var m=game(id,name,p);m.placeDamageCountersByEffect(p,other(p),active(m,other(p)),7);m.placeDamageCountersByEffect(p,other(p),bench(m,other(p)),2);
        atomic(m,()->m.attack(p,name,active(m,other(p))));atomic(m,()->m.attack(p,name,bench(m,p)));m.attack(p,name,bench(m,other(p)));
        check(m.activePokemonView(other(p)).damage()==70&&m.benchPokemonView(other(p)).getFirst().damage()==60,"Selected Bench counters or target restriction wrong: "+id);
    }}
    static void tools(){for(String id:List.of("swsh9_en_052","me2-5_en_092","sv10_en_009"))for(UUID p:List.of(A,B))for(int count:List.of(0,1,2)){
        String name=id.equals("swsh9_en_052")?"windup-thunder":"gadget-show";var m=game(id,name,p);m.endTurn(p);m.playTrainer(other(p),DynamicPassiveModifierTest.hand(m,other(p),"tool").instanceId(),active(m,other(p)));m.endTurn(other(p));
        if(count>0)m.playTrainer(p,DynamicPassiveModifierTest.hand(m,p,"tool").instanceId(),active(m,p));if(count>1)m.playTrainer(p,DynamicPassiveModifierTest.hand(m,p,"tool").instanceId(),bench(m,p));m.attack(p,name);
        check(m.lastAttack().finalDamage()==30*count,"Tool multiplier gained printed multiplier as base damage or counted opponent Tools: "+id);
    }}
    static void guards(){for(String id:List.of("swsh8_en_189","sv4_en_005"))for(UUID p:List.of(A,B))for(boolean ability:List.of(false,true))for(String name:List.of("Simisage","Other")){
        var a=ResumableAbilityTest.ability("ascend",DeckEvolutionEffectTest.EVOLVE);String move=id.equals("swsh8_en_189")?"ingot-swing":"arm-thrust-needle";var m=game(id,move,p,2,ability?List.of(a):List.of(),name);m.attack(p,move);m.attack(other(p),move);
        boolean blocked=ability&&!(id.equals("sv4_en_005")&&name.equals("Simisage"));check(m.activePokemonView(p).damage()==(blocked?0:id.equals("swsh8_en_189")?80:100),"Ability damage guard exception wrong");
    }}
    static void evolution(){for(String id:List.of("swsh8_en_173","sv3_en_018"))for(UUID p:List.of(A,B))for(String mode:List.of("deck","switch","expire")){
        String move=id.equals("swsh8_en_173")?"bone-block":"queenly-heel";var ability=ResumableAbilityTest.ability("ascend",DeckEvolutionEffectTest.EVOLVE);var m=game(id,move,p,2,List.of(ability),null);m.attack(p,move);UUID q=other(p),host=active(m,q);var card=DynamicPassiveModifierTest.hand(m,q,"evolved");
        check(!m.canEvolve(q,host,"evolved"),"Hand evolution offered through Bone Block");atomic(m,()->m.evolve(q,host,card.instanceId()));atomic(m,()->m.evolveByEffect(q,host,card.instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING));
        if(mode.equals("deck")){m.useAbility(q,host,"ascend");var prompt=m.effectCardPrompt(q);choose(m,q,List.of(prompt.candidates().getFirst()));check(m.activePokemonView(q).topCardId().equals("evolved"),"Hand lock incorrectly blocked deck evolution");}
        else {if(mode.equals("switch")){m.switchActiveByEffect(q,q,bench(m,q));m.switchActiveByEffect(q,q,host);}else{m.endTurn(q);m.endTurn(p);}m.evolve(q,host,card.instanceId());check(m.activePokemonView(q).topCardId().equals("evolved"),"Hand lock did not clear or expire");}
    }}
    static void returns(){for(UUID p:List.of(A,B))for(int units:List.of(0,2))for(boolean yes:List.of(false,true)){
        var m=game("swsh3_en_016","time-out-kick",p,units,List.of(),null);UUID foe=other(p);var special=CoinEnergyDiscardTest.attach(m,foe,active(m,foe),"special");var basic=CoinEnergyDiscardTest.attach(m,foe,active(m,foe),"energy");var decoy=CoinEnergyDiscardTest.attach(m,foe,bench(m,foe),"energy");m.attack(p,"time-out-kick");optional(m,p,yes);
        var selected=units==0?basic:special;if(yes){var prompt=m.effectCardPrompt(p);check(prompt.candidates().contains(special)==(units>0),"Zero-unit Special Energy selected as Energy");atomic(m,()->choose(m,p,List.of(decoy)));choose(m,p,List.of(selected));}
        check(m.privateView(foe).hand().contains(selected)==yes&&m.benchPokemonView(foe).getFirst().attachedEnergyIds().contains(decoy.instanceId()),"Returned Energy went to wrong hand/zone");check(m.currentPlayer().equals(foe),"Optional Energy return stranded attack");
    }}
    static void coins(){for(UUID p:List.of(A,B))for(int tails=0;tails<=3;tails++){
        boolean found=false;for(int skip=0;skip<60&&!found;skip++){
            var m=game("swsh8_en_035","body-splash",p);var first=CoinEnergyDiscardTest.attach(m,p,active(m,p),"special");var second=CoinEnergyDiscardTest.attach(m,p,active(m,p),"special");var basic=CoinEnergyDiscardTest.attach(m,p,active(m,p),"energy");var decoy=CoinEnergyDiscardTest.attach(m,p,bench(m,p),"energy");for(int i=0;i<skip;i++)m.flipCoinsByEffect(p,1);int start=m.randomTranscript().size();m.attack(p,"body-splash");var flips=m.randomTranscript().subList(start,m.randomTranscript().size()).stream().filter(r->r.operation().startsWith("effect-coin:")).toList();if(flips.stream().filter(r->r.result()!=0).count()!=tails)continue;found=true;check(flips.size()==3,"Body Splash did not flip exactly three coins");
            if(tails>0){atomic(m,()->choose(m,p,List.of()));atomic(m,()->choose(m,p,List.of(decoy)));if(tails==3)atomic(m,()->choose(m,p,List.of(first)));choose(m,p,tails==3?List.of(first,second):List.of(first));}
            check(m.privateView(p).discard().contains(first)==(tails>0)&&m.privateView(p).discard().contains(second)==(tails==3)&&m.activePokemonView(p).attachedEnergyIds().contains(basic.instanceId()),"Tails counted physical cards instead of Energy units");check(m.currentPlayer().equals(other(p)),"Coin discard did not continue");
        }check(found,"Coin fixture not found");
    }}
    static void moves(){for(UUID p:List.of(A,B)){
        var m=game("swsh9_en_116","hurricane",p);var basic=CoinEnergyDiscardTest.attach(m,p,active(m,p),"energy");var special=CoinEnergyDiscardTest.attach(m,p,active(m,p),"special");m.attack(p,"hurricane");var prompt=m.effectCardPrompt(p);check(prompt.candidates().equals(List.of(basic)),"Basic Energy move accepted Special Energy");choose(m,p,List.of(basic));var target=m.effectAttachmentPrompt(p);atomic(m,()->m.chooseEffectAttachmentTarget(p,target.resolutionId(),target.step(),active(m,p)));m.chooseEffectAttachmentTarget(p,target.resolutionId(),target.step(),bench(m,p));check(m.benchPokemonView(p).getFirst().attachedEnergyIds().contains(basic.instanceId())&&m.activePokemonView(p).attachedEnergyIds().contains(special.instanceId()),"Hurricane moved wrong Energy");
    }}
    static void ai(){for(UUID p:List.of(A,B))for(var level:AiDifficulty.values())for(String name:List.of("targeted-skewer","windup-thunder","bone-block","time-out-kick","body-splash","hurricane")){
        String id=switch(name){case "targeted-skewer"->"swsh4_en_042";case "windup-thunder"->"swsh9_en_052";case "bone-block"->"swsh8_en_173";case "time-out-kick"->"swsh3_en_016";case "body-splash"->"swsh8_en_035";default->"swsh9_en_116";};var m=game(id,name,p);CoinEnergyDiscardTest.attach(m,p,active(m,p),"energy");CoinEnergyDiscardTest.attach(m,p,active(m,p),"special");CoinEnergyDiscardTest.attach(m,other(p),active(m,other(p)),"special");if(name.equals("targeted-skewer")){m.placeDamageCountersByEffect(p,other(p),bench(m,other(p)),2);m.attack(p,name,bench(m,other(p)));}else m.attack(p,name);var session=MatchSession.multiplayer(m,A,B);AiCardOperationTest.settle(session,level);AiCardOperationTest.finish(session,level);check(m.phase()==MatchPhase.FINISHED,"AI stalled on "+name+" "+level);
    }}
}
