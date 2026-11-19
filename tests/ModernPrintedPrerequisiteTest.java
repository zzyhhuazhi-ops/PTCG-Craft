import com.google.gson.*;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.nio.file.*;
import java.util.*;

/** Full printed clauses distinguish first-turn search/protection, revenge, Lost Mine and Star Requiem. */
public final class ModernPrintedPrerequisiteTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static final Map<String,JsonObject> RAW=load();
    static Map<String,JsonObject> load(){try{var out=new HashMap<String,JsonObject>();for(var line:Files.readAllLines(Path.of("outputs/pokemon-modern-prefix-programs.jsonl"))){var r=JsonParser.parseString(line).getAsJsonObject();out.put(r.get("id").getAsString(),r.getAsJsonObject("conversion").getAsJsonObject("combatEntry"));}return out;}catch(Exception e){throw new RuntimeException(e);}}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static JsonObject raw(String id,String attack){return RAW.get(id).getAsJsonArray("attacks").asList().stream().map(JsonElement::getAsJsonObject).filter(a->a.get("id").getAsString().equals(attack)).findFirst().orElseThrow().deepCopy();}
    static AttackDefinition attack(String id,String name){var a=raw(id,name);a.add("energyCost",new JsonObject());a.addProperty("damageType","COLORLESS");return BundledCombatData.attack(a,EffectRegistry.standard());}
    static ServerMatch game(String id,String name,UUID p){var a=attack(id,name);var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.fixed(EnergyType.COLORLESS,2),a);if(p.equals(B))m.endTurn(A);return m;}
    static void lost(ServerMatch m,UUID p,int count){for(var c:m.privateView(p).hand().stream().limit(count).toList())m.sendToLostZoneByEffect(p,EffectSourceZone.HAND,c.instanceId());}
    static void settle(ServerMatch m){AiCardOperationTest.settle(MatchSession.multiplayer(m,A,B),AiDifficulty.NORMAL);}
    static void choose(ServerMatch m,UUID p,List<CardInstance> cards){var q=m.effectCardPrompt(p);m.submitEffectCards(p,q.resolutionId(),q.step(),cards.stream().map(CardInstance::instanceId).toList());}
    static ServerMatch opening(String id,String name,UUID second){
        var a=attack(id,name);var defs=new CardDatabase(List.of(StadiumActivationTest.definition("basic",CardCategory.POKEMON,TrainerCategory.NONE,true)));
        var profile=new PokemonProfile(500,0,null,null,0,null,0,1,List.of(a,GeneratedRuleBatch8Test.hit(50,false)),List.of(),EnergyType.COLORLESS);
        var combat=new CombatCatalog(Map.of("basic",profile),Map.of(),Map.of());
        var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var d=new ArrayList<CardInstance>();for(int i=0;i<60;i++)d.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,"basic"));decks.add(d);}
        var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,defs,combat,new ServerRandom(9));m.callCoin(A,CoinSide.HEADS);UUID winner=m.publicCoinResult()==CoinSide.HEADS?A:B;m.chooseWhoGoesFirst(winner,winner.equals(other(second)));OpeningTestSupport.complete(m,A,B,1);return m;
    }
    public static void main(String[] args){firstTurn();revenge();lostGate();lostUnits();schema();ai();System.out.println("ModernPrintedPrerequisiteTest passed: both seats, second-player first-turn search/protection and expiry, attack-only prior KO bonus with Confusion, 0/9/10/11 own Lost Zone gate and atomic invalid declaration, exact twelve-counter allocation, shared VSTAR lock, multi-unit whole-card Lost Zone payment, and 48 four-tier games");}
    static void firstTurn(){for(UUID p:List.of(A,B))for(String name:List.of("encouraging-gift","submerge-silently")){
        String id=name.equals("encouraging-gift")?"swsh10_en_014":"swshbsp_en_273";
        for(int count:List.of(0,3)){var m=opening(id,name,p);AttackDiscardUnitsTest.atomic(m,()->m.attack(other(p),name));m.endTurn(other(p));int held=m.privateView(p).hand().size(),deck=m.privateView(p).deck().size(),rng=m.randomTranscript().size();m.attack(p,name);
            if(name.equals("encouraging-gift")){var q=m.effectCardPrompt(p);check(q.minimum()==0&&q.maximum()==3&&q.candidates().size()==deck,"Gift is not an any-card search");choose(m,p,q.candidates().subList(0,count));check(m.privateView(p).hand().size()==held+count&&m.randomTranscript().size()==rng+Math.max(0,deck-count-1),"Gift count/shuffle wrong");}
            else {check(!m.hasPendingCardSelection(),"Submerge opened an Energy search");m.attack(other(p),"hit");check(m.activePokemonView(p).damage()==0,"First opposing attack was not prevented");}
            if(m.currentPlayer().equals(other(p)))m.endTurn(other(p));AttackDiscardUnitsTest.atomic(m,()->m.attack(p,name));
            if(name.equals("submerge-silently")){m.endTurn(p);m.attack(other(p),"hit");check(m.activePokemonView(p).damage()==50,"Submerge protection did not expire");}
        }
    }}
    static void revenge(){for(String id:List.of("swsh10_en_044","swshbsp_en_205"))for(UUID p:List.of(A,B))for(String mode:List.of("none","attack","counters")){
        var m=game(id,"grudge-dive",p);if(!mode.equals("none")){m.endTurn(p);var foe=other(p);m.placeDamageCountersByEffect(foe,p,active(m,p),mode.equals("attack")?27:30);if(mode.equals("attack"))m.attack(foe,"grudge-dive");m.takePrizeAt(foe,0);m.chooseReplacement(p,m.benchPokemonView(p).getFirst().pokemonId());if(m.currentPlayer().equals(foe))m.endTurn(foe);}
        m.attack(p,"grudge-dive");check(m.lastAttack().finalDamage()==(mode.equals("attack")?120:30),"Grudge Dive bonus wrong "+mode);var conditions=m.activePokemonView(other(p)).conditions();check(conditions.contains(SpecialCondition.CONFUSED)==mode.equals("attack")&&!conditions.contains(SpecialCondition.PARALYZED),"Revenge copied Raging Freeze instead of printed Confusion");
    }}
    static void lostGate(){for(String id:List.of("swsh11_en_070","swsh11_en_131","swsh11_en_201","swsh11_en_212","swsh12-5a_en_069"))for(UUID p:List.of(A,B))for(int count:List.of(0,9,10,11)){
        String name=id.equals("swsh11_en_070")?"lost-mine":"star-requiem";var m=game(id,name,p);m.endTurn(p);lost(m,other(p),10);m.endTurn(other(p));lost(m,p,count);
        if(count<10){AttackDiscardUnitsTest.atomic(m,()->m.attack(p,name));check(m.activeAttacks(p).stream().noneMatch(a->a.id().equals(name)),"Unusable Lost Zone attack offered");lost(m,p,10-count);}
        check(m.activeAttacks(p).stream().anyMatch(a->a.id().equals(name)),"Own Lost Zone threshold not recognized");m.attack(p,name);
        if(name.equals("lost-mine")){var q=m.damageAllocationPrompt(p);check(q.budget()==12&&q.candidates().size()==2,"Lost Mine became a direct KO");var bench=m.benchPokemonView(other(p)).getFirst().pokemonId();AttackDiscardUnitsTest.atomic(m,()->m.submitDamageAllocation(p,q.resolutionId(),q.step(),List.of(new ServerMatch.DamageCounterPlacement(other(p),active(m,other(p)),11))));m.submitDamageAllocation(p,q.resolutionId(),q.step(),List.of(new ServerMatch.DamageCounterPlacement(other(p),active(m,other(p)),4),new ServerMatch.DamageCounterPlacement(other(p),bench,8)));check(m.activePokemonView(other(p)).damage()==40&&m.benchPokemonView(other(p)).getFirst().damage()==80,"Lost Mine allocation did not place exact counters");}
        else {settle(m);check(m.benchPokemonView(other(p)).isEmpty(),"Star Requiem did not KO the defender");if(m.currentPlayer().equals(other(p)))m.endTurn(other(p));AttackDiscardUnitsTest.atomic(m,()->m.attack(p,name));}
    }}
    static void lostUnits(){for(String id:List.of("swsh11_en_027","swsh11_en_173","swsh11_en_131","swsh11_en_201","swsh11_en_212","swsh12-5a_en_069"))for(UUID p:List.of(A,B))for(boolean attached:List.of(false,true)){
        boolean fire=id.equals("swsh11_en_027")||id.equals("swsh11_en_173");String name=fire?"magical-fire":"lost-impact";var m=game(id,name,p);var c=attached?CoinEnergyDiscardTest.attach(m,p,fire?active(m,p):m.benchPokemonView(p).getFirst().pokemonId(),"special"):null;m.attack(p,name);
        if(m.hasPendingCardSelection()){if(attached)AttackDiscardUnitsTest.atomic(m,()->choose(m,p,List.of()));choose(m,p,attached?List.of(c):List.of());}
        if(fire){var q=m.effectPokemonPrompt(p);m.chooseEffectPokemon(p,q.resolutionId(),q.step(),m.benchPokemonView(other(p)).getFirst().pokemonId());check(m.benchPokemonView(other(p)).getFirst().damage()==120,"Magical Fire lost its Bench damage");}
        check(!attached||m.privateView(p).lostZone().contains(c)&&!m.privateView(p).discard().contains(c),"Two units were treated as two cards");check(m.currentPlayer().equals(other(p)),"Partial Lost Zone payment stranded attack");
    }}
    static void schema(){var a=raw("swsh11_en_070","lost-mine");a.getAsJsonObject("useRequirement").addProperty("attackId","other");GeneratedRuleBatch8Test.reject(()->BundledCombatData.attack(a,EffectRegistry.standard()));GeneratedRuleBatch8Test.reject(()->new AttackUseRequirement(AttackUseRequirement.Type.LOST_ZONE_TEN_CARDS,"fake",null));}
    static void ai(){for(UUID p:List.of(A,B))for(var level:AiDifficulty.values())for(String name:List.of("encouraging-gift","submerge-silently","grudge-dive","lost-mine","star-requiem","magical-fire")){
        String id=switch(name){case "encouraging-gift"->"swsh10_en_014";case "submerge-silently"->"swshbsp_en_273";case "grudge-dive"->"swsh10_en_044";case "lost-mine"->"swsh11_en_070";case "star-requiem"->"swsh11_en_131";default->"swsh11_en_027";};
        var m=Set.of("encouraging-gift","submerge-silently").contains(name)?opening(id,name,p):game(id,name,p);if(m.currentPlayer().equals(other(p)))m.endTurn(other(p));if(Set.of("lost-mine","star-requiem").contains(name))lost(m,p,10);m.attack(p,name);var s=MatchSession.multiplayer(m,A,B);AiCardOperationTest.settle(s,level);AiCardOperationTest.finish(s,level);check(m.phase()==MatchPhase.FINISHED,"AI stalled: "+name+" "+level);
    }}
}
