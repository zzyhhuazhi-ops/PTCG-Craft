import com.google.gson.*;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.nio.file.*;
import java.util.*;

/** Printed discard precedes status; a switched attacker remains the source of its attachment. */
public final class DiscardStatusOrderTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    record Case(String id,JsonObject profile,JsonObject attack) { }
    static final List<Case> CASES=load();
    static List<Case> load(){try{var out=new ArrayList<Case>();for(var line:Files.readAllLines(Path.of("outputs/pokemon-discard-status-programs.jsonl"))){var r=JsonParser.parseString(line).getAsJsonObject();var profile=r.getAsJsonObject("conversion").getAsJsonObject("combatEntry");for(var value:profile.getAsJsonArray("attacks")){var attack=value.getAsJsonObject();if(!attack.has("triggers"))continue;var effects=attack.getAsJsonArray("triggers").get(0).getAsJsonObject().getAsJsonArray("effects");if(effects.size()>=2&&effects.get(0).getAsJsonObject().get("type").getAsString().equals("discard_energy")&&Set.of("burn","paralyze","poison").contains(effects.get(1).getAsJsonObject().get("type").getAsString()))out.add(new Case(r.get("id").getAsString(),profile,attack));}}return out;}catch(Exception e){throw new RuntimeException(e);}}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static JsonArray effects(JsonObject attack){return attack.getAsJsonArray("triggers").get(0).getAsJsonObject().getAsJsonArray("effects");}
    static AttackDefinition attack(Case c,boolean pause){var a=c.attack.deepCopy();a.add("energyCost",new JsonObject());if(pause){var original=effects(a).deepCopy();var sequence=new JsonArray();sequence.add(original.get(0));sequence.add(JsonParser.parseString("{\"type\":\"choice\",\"options\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"draw\",\"amount\":2}]}"));for(int i=1;i<original.size();i++)sequence.add(original.get(i));a.getAsJsonArray("triggers").get(0).getAsJsonObject().add("effects",sequence);}return BundledCombatData.attack(a,EffectRegistry.standard());}
    static ServerMatch game(Case c,UUID p,boolean pause){var discard=effects(c.attack).get(0).getAsJsonObject();var type=discard.has("energy_type")?EnergyType.valueOf(discard.get("energy_type").getAsString()):EnergyType.COLORLESS;var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.fixed(type,3),attack(c,pause));if(p.equals(B))m.endTurn(A);return m;}
    static CardInstance attach(ServerMatch m,UUID p,String id){return AttackDiscardUnitsTest.attach(m,p,id);}
    static void choose(ServerMatch m,UUID p,List<CardInstance> cards){var q=m.effectCardPrompt(p);m.submitEffectCards(p,q.resolutionId(),q.step(),cards.stream().map(CardInstance::instanceId).toList());}
    public static void main(String[] args){timing();sourceAttachment();ai();System.out.println("DiscardStatusOrderTest passed: 15 exact printed attack programs, both seats, discard-before-status checkpoints, partial/empty payment, typed/all-unit discard, dual statuses, original source after switching, and 24 four-tier matches");}
    static void timing(){for(var c:CASES)for(UUID p:List.of(A,B))for(int count:List.of(0,3)){
        var m=game(c,p,true);var own=new ArrayList<CardInstance>();for(int i=0;i<count;i++)own.add(attach(m,p,"special"));CardInstance basic=count>0?attach(m,p,"energy"):null;var enemy=attach(m,other(p),"special");
        m.attack(p,attack(c,true).id());check(m.activePokemonView(other(p)).conditions().isEmpty(),"Native status preceded printed discard "+c.id);
        if(m.hasPendingCardSelection()){AttackDiscardUnitsTest.atomic(m,()->choose(m,p,List.of(enemy)));choose(m,p,List.of(own.getFirst()));}
        check(m.hasPendingEffectChoice()&&m.activePokemonView(other(p)).conditions().isEmpty(),"Status occurred before discard checkpoint "+c.id);
        var operation=effects(c.attack).get(0).getAsJsonObject();boolean all=operation.has("all"),typed=operation.has("energy_type");int removed=count==0?0:all?count+(typed?0:1):1;
        check(m.activePokemonView(p).attachedEnergyIds().size()==(count==0?0:count+1-removed),"Discard count/type mismatch "+c.id);
        if(typed&&basic!=null)check(m.activePokemonView(p).attachedEnergyIds().contains(basic.instanceId()),"Typed discard removed unrelated Basic Energy");
        var q=m.effectChoicePrompt(p);m.chooseEffectOption(p,q.resolutionId(),q.step(),0);var status=m.activePokemonView(other(p)).conditions();var expected=effects(c.attack).asList().stream().skip(1).map(e->e.getAsJsonObject().get("type").getAsString()).toList();
        check(!expected.contains("paralyze")||status.contains(SpecialCondition.PARALYZED),"Discard prevented later Paralysis");check(!expected.contains("poison")||status.contains(SpecialCondition.POISONED),"Discard prevented later Poison");
        int checkup=(expected.contains("burn")?20:0)+(expected.contains("poison")?10:0);check(m.activePokemonView(other(p)).damage()==m.lastAttack().finalDamage()+checkup&&m.currentPlayer().equals(other(p)),"Status checkup or end of turn mismatch "+c.id);
    }}
    static void sourceAttachment(){var c=CASES.stream().filter(x->x.id.equals("bw11_en_101")).findFirst().orElseThrow();for(UUID p:List.of(A,B)){boolean reached=false;for(int skip=0;skip<50&&!reached;skip++){
        var a=c.profile.getAsJsonArray("attacks").get(0).getAsJsonObject().deepCopy();a.add("energyCost",new JsonObject());var sequence=new JsonArray();sequence.add(JsonParser.parseString("{\"type\":\"switch\",\"target\":\"SELECT_OWN_POKEMON\"}"));for(var e:effects(a))sequence.add(e);a.getAsJsonArray("triggers").get(0).getAsJsonObject().add("effects",sequence);
        var attack=BundledCombatData.attack(a,EffectRegistry.standard());var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.fixed(EnergyType.COLORLESS,2),attack);if(p.equals(B))m.endTurn(A);UUID source=active(m,p),replacement=m.benchPokemonView(p).getFirst().pokemonId();var energy=DynamicPassiveModifierTest.hand(m,p,"energy");m.discardHandCardsByEffect(p,p,List.of(energy.instanceId()));for(int i=0;i<skip;i++)m.flipCoinsByEffect(p,1);
        m.attack(p,attack.id());var target=m.effectPokemonPrompt(p);m.chooseEffectPokemon(p,target.resolutionId(),target.step(),replacement);
        if(m.hasPendingCardSelection()){choose(m,p,List.of(energy));reached=true;check(m.benchPokemonView(p).stream().anyMatch(v->v.pokemonId().equals(source)&&v.attachedEnergyIds().contains(energy.instanceId()))&&m.activePokemonView(p).attachedEnergyIds().isEmpty(),"This-Pokemon attachment rebound to the new Active");check(m.currentPlayer().equals(other(p)),"Switched-source attachment stranded turn");}
    }check(reached,"No heads branch for source attachment");}}
    static void ai(){for(String id:List.of("bw11_en_101","me2-5_en_045","swsh4_en_106"))for(UUID p:List.of(A,B))for(var level:AiDifficulty.values()){
        var c=CASES.stream().filter(x->x.id.equals(id)).findFirst().orElseThrow();var m=game(c,p,false);attach(m,p,"special");var session=MatchSession.multiplayer(m,A,B);var v=session.view(p,p);var option=v.options().stream().filter(o->o.type()==ActionType.ATTACK&&o.enabled()).findFirst().orElseThrow();session.submit(p,p,v.revision(),option.index());AiCardOperationTest.settle(session,level);AiCardOperationTest.finish(session,level);check(m.phase()==MatchPhase.FINISHED,"Discard/status AI stalled "+id+" "+level);
    }}
}
