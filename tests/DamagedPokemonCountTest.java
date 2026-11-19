import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Synthetic programs isolate the live damaged-host predicate; actual Gourgeist is tested separately. */
public final class DamagedPokemonCountTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final String COUNT="{\"type\":\"damage_per_pokemon\",\"target\":\"OWN_BENCH\",\"amount\":50,\"damage_counters_min\":1}";
    static void check(boolean value,String why){GeneratedRuleBatch8Test.check(value,why);}
    static void reject(Runnable action){GeneratedRuleBatch8Test.reject(action);}
    static EffectDefinition parse(String json){return EffectRegistry.standard().parse(JsonParser.parseString(json).getAsJsonObject());}
    static AttackDefinition attack(){return GeneratedRuleBatch8Test.hit(30,false).withEffectProgram(new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.ON_ATTACK,COUNT))));}
    static ServerMatch fixture(AttackDefinition attack){return GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.PSYCHIC,EnergyType.COLORLESS,GeneratedRuleBatch8Test.trainer("swsh5_en_124"),attack);}
    static UUID bench(ServerMatch m,UUID owner){return m.benchPokemonView(owner).getFirst().pokemonId();}
    public static void main(String[] args){hostCounts();liveChanges();phaseTiming();schema();facts();System.out.println("DamagedPokemonCountTest passed: live0..5 damaged Bench hosts rather than counters, own Active/opponent exclusions, heal/evolve/devolve/leave updates, before-versus-after timing, strict positive threshold, missing-facts rejection, legacy constructors and combined filters");}
    static void hostCounts(){for(int damaged=0;damaged<=5;damaged++){
        var m=fixture(attack());while(m.benchPokemonView(A).size()<5)m.playBasicPokemonToBench(A,GeneratedRuleBatch8Test.hand(m,"basic").instanceId());
        m.placeDamageCountersByEffect(A,A,m.activePokemonView(A).pokemonId(),8);m.placeDamageCountersByEffect(A,B,bench(m,B),9);
        var hosts=m.benchPokemonView(A);for(int i=0;i<damaged;i++)m.placeDamageCountersByEffect(A,A,hosts.get(i).pokemonId(),1+i*3);
        var hand=m.privateView(A).hand();var rng=m.randomTranscript();m.attack(A,"hit");
        check(m.lastAttack().finalDamage()==30+50*damaged,"Counted counters, Active, opponent or undamaged host: "+damaged);
        check(m.privateView(A).hand().equals(hand)&&m.randomTranscript().equals(rng),"Public count changed hand or RNG");
    }}
    static void liveChanges(){var m=fixture(attack());UUID host=bench(m,A);m.placeDamageCountersByEffect(A,A,host,4);m.attack(A,"hit");check(m.lastAttack().finalDamage()==80,"Initial damaged host missing");m.endTurn(B);
        m.evolveByEffect(A,host,GeneratedRuleBatch8Test.hand(m,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);m.attack(A,"hit");check(m.lastAttack().finalDamage()==80,"Evolution counted stack or lost damage");m.endTurn(B);
        m.devolvePokemonToHandByEffect(A,A,host);m.healDamageByEffect(A,A,host,30);m.attack(A,"hit");check(m.lastAttack().finalDamage()==80,"Partial healing/devolution erased remaining counter predicate");m.endTurn(B);
        m.healDamageByEffect(A,A,host,10);m.attack(A,"hit");check(m.lastAttack().finalDamage()==30,"Fully healed host left stale count");m.endTurn(B);
        m.placeDamageCountersByEffect(A,A,host,2);m.movePokemonByEffect(A,A,host,LeavePlayDestination.HAND);m.attack(A,"hit");check(m.lastAttack().finalDamage()==30,"Departed damaged host still counted");
    }
    static void phaseTiming(){for(boolean before:List.of(false,true)){
        String counters="{\"type\":\"select_pokemon\",\"target\":\"SELECT_OWN_POKEMON\",\"position\":\"BENCH\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"CHOSEN_POKEMON\",\"amount\":1}]}";
        var a=BeforeDamageAttackTest.attack(before?counters:"",COUNT,before?"":counters);var m=fixture(a);m.attack(A,a.id());
        var prompt=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,prompt.resolutionId(),prompt.step(),bench(m,A));
        check(m.lastAttack().finalDamage()==(before?90:40),"Damage count sampled in wrong attack phase");
        check(m.benchPokemonView(A).getFirst().damage()==10,"Counter placement missing");
    }}
    static void schema(){for(String value:List.of("0","-1","61","1.5","true","\"one\""))reject(()->parse(COUNT.replace("\"damage_counters_min\":1","\"damage_counters_min\":"+value)));
        reject(()->parse(COUNT.replace("\"damage_counters_min\":1","\"damage_counters_min\":1,\"count_mode\":\"DISTINCT_TYPES\"")));
        reject(()->parse(COUNT.replace("OWN_BENCH","HAND")));reject(()->parse(COUNT.replace("damage_counters_min","damaged")));
        check(PokemonCountEffect.from(parse(COUNT.replace("\"damage_counters_min\":1","\"damage_counters_min\":60"))).minimumDamageCounters()==60,"Upper threshold boundary rejected");
    }
    static void facts(){var definition=new CardDefinition("basic","basic",CardCategory.POKEMON,TrainerCategory.NONE,"J",Set.of("international"),true,false,60,Map.of(),EffectSupport.SUPPORTED,null);
        var profile=new PokemonProfile(60,0,null);var q=PokemonCountEffect.from(parse(COUNT));
        reject(()->q.matches(definition,profile));reject(()->q.matches(definition,profile,60));reject(()->q.matches(definition,profile,60,0));reject(()->q.matches(definition,profile,60,0,-1));
        check(!q.matches(definition,profile,60,0,0)&&q.matches(definition,profile,60,0,1)&&q.matches(definition,profile,60,0,80),"Live damaged-host predicate capped actual damage or counted units");
        var old=new PokemonCountEffect(PokemonFamilyFilter.ANY,null,PokemonCountEffect.Mode.POKEMON,PokemonTypeFilter.ANY,Set.of(),0,null,null);
        check(old.minimumDamageCounters()==0&&old.matches(definition,profile),"Eight-argument compatibility changed");
        var combined=PokemonCountEffect.from(parse(COUNT.replace("\"damage_counters_min\":1","\"damage_counters_min\":1,\"maximum_hp_equals\":60,\"attached_energy_type\":\"FIRE\"")));
        check(combined.matches(definition,profile,60,2,1)&&!combined.matches(definition,profile,70,2,1)&&!combined.matches(definition,profile,60,0,1)&&!combined.matches(definition,profile,60,2,0),"Existing predicates no longer AND together");
        var fossil=new CardDefinition("fossil","fossil",CardCategory.TRAINER,TrainerCategory.ITEM,"J",Set.of("international"),false,false,60,Map.of(),EffectSupport.SUPPORTED,null);
        check(q.matches(fossil,profile,60,0,1),"In-play Fossil Item form wrongly excluded by hidden-zone category");
    }
}
