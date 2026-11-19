import com.google.gson.*;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.nio.file.*;
import java.util.*;

/** Real Evil Orders/Nab 'n' Dash and Bedrock Shake, including empty and hidden zones. */
public final class BenchDependentAttackTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final Map<String,JsonObject> RAW=load();
    static Map<String,JsonObject> load(){try{var out=new HashMap<String,JsonObject>();for(var line:Files.readAllLines(Path.of("outputs/pokemon-bench-selection-programs.jsonl"))){var r=JsonParser.parseString(line).getAsJsonObject();out.put(r.get("id").getAsString(),r.getAsJsonObject("conversion").getAsJsonObject("combatEntry"));}return out;}catch(Exception e){throw new RuntimeException(e);}}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static AttackDefinition attack(String id,int slot){var a=RAW.get(id).getAsJsonArray("attacks").get(slot).getAsJsonObject().deepCopy();a.add("energyCost",new JsonObject());return BundledCombatData.attack(a,EffectRegistry.standard());}
    static void atomic(ServerMatch m,Runnable r){var before=m.clientView(null);var rng=m.randomTranscript();GeneratedRuleBatch8Test.reject(r);check(before.equals(m.clientView(null))&&rng.equals(m.randomTranscript()),"Rejected selection changed state/RNG");}
    static List<CardInstance> zone(ServerMatch m,UUID p,String name){return PairedTrainerPlayTest.zone(m,p,name);}
    static void choose(ServerMatch m,UUID p,List<UUID> ids){var q=m.effectCardPrompt(p);m.submitEffectCards(p,q.resolutionId(),q.step(),ids);}
    static ServerMatch search(String id,UUID p,int benchCount){
        var c=new GeneratedRuleBatch28Test.Config();c.attack=attack(id,0);c.defenderAttack=c.attack;
        c.trainer=DynamicPassiveModifierTest.trainer("STADIUM","{\"type\":\"bench_limit\",\"target\":\"SELF\",\"amount\":8,\"leave_limit\":5}");
        var m=GeneratedRuleBatch28Test.fixture(c);if(p.equals(B))m.endTurn(A);
        if(benchCount>5)m.playTrainer(p,GeneratedRuleBatch28Test.hand(m,p,"trainer").instanceId(),null);
        if(benchCount==0)m.movePokemonByEffect(p,p,bench(m,p),LeavePlayDestination.HAND);
        while(m.benchPokemonView(p).size()<benchCount){var card=NonPokemonBatch133Test.put(m,p,p.equals(A)?"attacker":"defender");m.playBasicPokemonToBench(p,card.instanceId());}
        return m;
    }
    public static void main(String[] args){searches();shortDecks();spread();knockouts();schema();ai();System.out.println("BenchDependentAttackTest passed: two printed searches, both seats, 0/1/3/5/8 Bench, optional/private/shuffled/short-deck choices and atomic rejection; damaged-only spread, protection/no Bench Weakness, simultaneous KO and four-tier matches");}
    static void searches(){for(String id:List.of("sm1_en_077","svbsp_en_188"))for(UUID p:List.of(A,B))for(int n:List.of(0,1,3,5,8))for(boolean take:List.of(false,true)){
        var m=search(id,p,n);var deck=List.copyOf(zone(m,p,"deck"));int hand=zone(m,p,"hand").size(),rng=m.randomTranscript().size();m.attack(p,attack(id,0).id());
        var chosen=new ArrayList<CardInstance>();
        if(m.hasPendingCardSelection()){
            var q=m.effectCardPrompt(p);check(q.minimum()==0&&q.maximum()==Math.max(1,n),"Search ignored live Bench count");
            check(n==0?q.candidates().isEmpty():new HashSet<>(q.candidates()).equals(new HashSet<>(deck)),"Search exposed wrong candidates");
            var session=MatchSession.multiplayer(m,A,B);String hidden=new Gson().toJson(session.view(other(p),other(p)));
            for(var card:deck)check(!hidden.contains(card.instanceId().toString()),"Deck identity leaked to opponent");
            atomic(m,()->m.effectCardPrompt(other(p)));atomic(m,()->choose(m,p,List.of(UUID.randomUUID())));
            if(n>0){var card=q.candidates().getFirst();atomic(m,()->choose(m,p,List.of(card.instanceId(),card.instanceId())));if(deck.size()>n)atomic(m,()->choose(m,p,deck.subList(0,n+1).stream().map(CardInstance::instanceId).toList()));}
            if(take)chosen.addAll(q.candidates().subList(0,Math.min(n,q.candidates().size())));
            choose(m,p,chosen.stream().map(CardInstance::instanceId).toList());
            atomic(m,()->m.submitEffectCards(p,q.resolutionId(),q.step(),List.of()));
        }else check(n==0,"Nonempty search silently skipped");
        var remaining=new HashSet<>(deck);remaining.removeAll(chosen);
        check(zone(m,p,"hand").size()==hand+chosen.size()&&zone(m,p,"hand").containsAll(chosen)&&new HashSet<>(zone(m,p,"deck")).equals(remaining),"Search did not conserve exact physical cards");
        check(m.currentPlayer().equals(other(p))&&!m.hasPendingEffectChoice()&&m.effectCardReveal()==null&&m.randomTranscript().size()>rng,"Search did not shuffle/end privately even when selecting zero");
    }}
    static void shortDecks(){for(UUID p:List.of(A,B))for(int count:List.of(0,1,2)){
        var m=search("sm1_en_077",p,5);while(zone(m,p,"deck").size()>count)zone(m,p,"discard").add(zone(m,p,"deck").removeFirst());
        var expected=List.copyOf(zone(m,p,"deck"));m.attack(p,"evil-orders");if(m.hasPendingCardSelection())choose(m,p,m.effectCardPrompt(p).candidates().stream().map(CardInstance::instanceId).toList());
        check(zone(m,p,"deck").isEmpty()&&zone(m,p,"hand").containsAll(expected)&&m.currentPlayer().equals(other(p)),"Short deck caused impossible mandatory selection");
    }}
    static ServerMatch spreadGame(UUID p,boolean shield){
        var c=new GeneratedRuleBatch28Test.Config();c.attack=attack("swsh1_en_099",1);c.defenderAttack=c.attack;c.weakness=true;c.defenderWeakness=EnergyType.FIGHTING;c.ownWeakness=EnergyType.FIGHTING;
        if(shield){var ability=BenchedDamagePreventionAbilityTest.shell();var profiles=new HashMap<String,PokemonProfile>();for(var id:List.of("attacker","defender"))profiles.put(id,new PokemonProfile(1000,0,null,EnergyType.FIGHTING,2,null,0,1,List.of(c.attack),List.of(ability),EnergyType.COLORLESS));c.restoredProfiles=profiles;}
        var m=GeneratedRuleBatch28Test.fixture(c);if(p.equals(B))m.endTurn(A);
        for(UUID owner:List.of(A,B)){var card=NonPokemonBatch133Test.put(m,owner,owner.equals(A)?"attacker":"defender");if(!m.currentPlayer().equals(owner))m.endTurn(m.currentPlayer());m.playBasicPokemonToBench(owner,card.instanceId());}
        if(!m.currentPlayer().equals(p))m.endTurn(m.currentPlayer());return m;
    }
    static void spread(){for(UUID p:List.of(A,B))for(boolean shield:List.of(false,true)){
        var m=spreadGame(p,shield);for(UUID owner:List.of(A,B))m.placeDamageCountersByEffect(p,owner,bench(m,owner),1);
        m.attack(p,"bedrock-shake");for(UUID owner:List.of(A,B)){var rows=m.benchPokemonView(owner);check(rows.getFirst().damage()==(shield?10:70)&&rows.getLast().damage()==0,"Spread hit undamaged Bench, used Weakness or bypassed protection");}
        check(m.activePokemonView(other(p)).damage()==240&&m.activePokemonView(p).damage()==0,"Bench predicate changed Active damage");
    }}
    static void knockouts(){for(UUID p:List.of(A,B)){
        var m=spreadGame(p,false);for(UUID owner:List.of(A,B)){for(int i=0;i<9;i++)m.placeDamageCountersByEffect(p,owner,bench(m,owner),10);m.placeDamageCountersByEffect(p,owner,bench(m,owner),4);}
        m.attack(p,"bedrock-shake");check(m.phase()==MatchPhase.PRIZE_SELECTION,"Simultaneous damaged Bench KOs not checkpointed");
        AiCardOperationTest.settle(MatchSession.multiplayer(m,A,B),AiDifficulty.NORMAL);
        for(UUID owner:List.of(A,B))check(m.privateView(owner).prizes().size()==5&&m.benchPokemonView(owner).size()==1,"One side's simultaneous Bench KO/prize was lost");
    }}
    static void schema(){for(String json:List.of("{\"type\":\"bench_damage\",\"target\":\"OPPONENT_BENCH\",\"amount\":60,\"damaged_only\":false}","{\"type\":\"bench_damage\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"amount\":60,\"damaged_only\":true}"))GeneratedRuleBatch8Test.reject(()->PokemonEffectSelection.validateBenchDamage(GeneratedRuleBatch28Test.effect(json)));
        for(String target:List.of("DISCARD","HAND"))GeneratedRuleBatch8Test.reject(()->ZoneSelectionEffect.forProgram(GeneratedRuleBatch28Test.effect("{\"type\":\"search_deck\",\"target\":\""+target+"\",\"amount\":60,\"minimum\":0,\"amount_mode\":\"OWN_BENCH_SIZE\"}"),true));
    }
    static void ai(){for(UUID p:List.of(A,B))for(var level:AiDifficulty.values())for(int n:List.of(0,3)){
        var m=search("sm1_en_077",p,n);var s=MatchSession.multiplayer(m,A,B);var v=s.view(p,p);var option=v.options().stream().filter(o->o.enabled()&&o.type()==ActionType.ATTACK).findFirst().orElseThrow();s.submit(p,p,v.revision(),option.index());AiCardOperationTest.settle(s,level);AiCardOperationTest.finish(s,level);check(m.phase()==MatchPhase.FINISHED,"AI search stalled "+level);
    }}
}
