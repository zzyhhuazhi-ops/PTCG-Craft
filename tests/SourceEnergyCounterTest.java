import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** A damage reaction counts current supplied typed units on its captured source, before KO cleanup. */
public final class SourceEnergyCounterTest {
    static final UUID A=new UUID(0,7701),B=new UUID(0,7702);
    static final String COUNTERS="{\"type\":\"damage_counter\",\"target\":\"ATTACKING_POKEMON\",\"amount\":3,\"per_source_energy_type\":\"GRASS\"}";
    static void check(boolean b,String s){GeneratedRuleBatch8Test.check(b,s);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static AbilityDefinition ability(){return DamageTriggeredAbilityTest.ability("needly-armor",true,false,false,COUNTERS);}
    static String after(String e){return ",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":["+e+"]}]";}
    static AbilityDefinition guard(String condition){return BundledCombatData.ability(JsonParser.parseString("{\"id\":\"guard\",\"nameZh\":\"Guard\",\"kind\":\"PASSIVE\",\"effectSupport\":\"SUPPORTED\",\"effects\":[{\"type\":\"passive_effect_guard\",\"target\":\"SELF\",\"source\":\"ABILITY\",\"from\":\"OPPONENT\",\"scope\":\"DAMAGE_AND_EFFECTS\""+(condition.isEmpty()?"":",\"conditions\":["+condition+"]")+"}]}").getAsJsonObject(),EffectRegistry.standard());}
    static ServerMatch game(AttackDefinition attack,List<AbilityDefinition> guards){
        return game(attack,guards,EnergyProfile.fixed(EnergyType.GRASS,4));
    }
    static ServerMatch game(AttackDefinition attack,List<AbilityDefinition> guards,EnergyProfile special){
        var defs=new ArrayList<CardDefinition>();String[] pool={"base","source","energy","energy","wrong","double-basic","special","special","special","base"};
        for(String id:Arrays.stream(pool).distinct().toList())defs.add(new CardDefinition(id,id,id.equals("base")||id.equals("source")?CardCategory.POKEMON:CardCategory.ENERGY,TrainerCategory.NONE,"J",Set.of("international"),id.equals("base"),Set.of("energy","wrong","double-basic").contains(id),60,Map.of(),EffectSupport.SUPPORTED,null));
        var profiles=Map.of("base",new PokemonProfile(1000,1,null,null,0,null,0,1,List.of(attack),guards,EnergyType.COLORLESS),
            "source",new PokemonProfile(1000,1,"base",null,0,null,0,1,List.of(attack),List.of(ability()),EnergyType.GRASS));
        // double-basic is a synthetic resolved two-unit Basic profile, not an enabled Meganium card.
        var combat=new CombatCatalog(profiles,Map.of("energy",EnergyType.GRASS,"wrong",EnergyType.FIRE),Map.of(),
            Map.of("double-basic",EnergyProfile.fixed(EnergyType.GRASS,2),"special",special));
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cs=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cs.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,pool[i%pool.length]));decks.add(cs);}
            var m=new ServerMatch(A,B,decks.getFirst(),decks.getLast(),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("base")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,32);m.drawCardsByEffect(A,B,32);m.endTurn(A);
            if(List.of(A,B).stream().anyMatch(p->Arrays.stream(pool).anyMatch(id->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals(id)))))continue;
            if(m.privateView(B).hand().stream().filter(c->c.definitionId().equals("special")).count()<6||m.privateView(B).hand().stream().filter(c->c.definitionId().equals("double-basic")).count()<2)continue;
            m.evolveByEffect(B,active(m,B),hand(m,B,"source").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);m.endTurn(B);return m;
        }throw new AssertionError("No source-energy reaction fixture");
    }
    static ServerMatch game(){return game(DamageTriggeredAbilityTest.attack(10,""),List.of());}
    static void attach(ServerMatch m,UUID owner,UUID host,String id,int count){for(int i=0;i<count;i++)m.attachEnergyByEffect(A,owner,EffectSourceZone.HAND,hand(m,owner,id).instanceId(),owner,host);}
    static void attack(ServerMatch m){m.attack(A,"hit");DamageTriggeredAbilityTest.finishTriggers(m);}
    public static void main(String[] args){units();timing();identity();guards();nonDamage();schema();System.out.println("SourceEnergyCounterTest passed: current typed supplied units, zero/wrong/multi/Basic profiles, whole attack before reaction, suppression/devolution, captured source/attacker, lethal cleanup, >60 one-guard placement, and closed event/schema bounds");}
    static void units(){for(int count:List.of(0,1,3)){
        var m=game();attach(m,B,active(m,B),"energy",count);attach(m,B,active(m,B),"wrong",1);attach(m,A,active(m,A),"special",1);attack(m);check(m.activePokemonView(A).damage()==count*30,"Counted wrong type/owner or omitted zero");
    }
        var doubled=game();attach(doubled,B,active(doubled,B),"double-basic",2);attack(doubled);check(doubled.activePokemonView(A).damage()==120,"Two resolved two-unit Basic cards must yield12 counters");
        var special=game();attach(special,B,active(special,B),"special",1);attack(special);check(special.activePokemonView(A).damage()==120,"Special provided Grass units were ignored");
        var flexible=game(DamageTriggeredAbilityTest.attack(10,""),List.of(),new EnergyProfile(List.of(new EnergyProfile.Unit(Set.of(EnergyType.GRASS,EnergyType.FIRE,EnergyType.WATER)))));
        attach(flexible,B,active(flexible,B),"special",1);attack(flexible);check(flexible.activePokemonView(A).damage()==30,"Flexible single unit counted more than once");
        for(boolean damaged:List.of(false,true)){
            var profile=new EnergyProfile(List.of(),EffectProgram.EMPTY,List.of(new EnergyProvisionRule(List.of(new ConditionDefinition(ConditionType.HP_BELOW,Map.of("target","SELF","value","980"),List.of())),EnergyProfile.fixed(EnergyType.GRASS,2).units())));
            var current=game(DamageTriggeredAbilityTest.attack(damaged?30:10,""),List.of(),profile);attach(current,B,active(current,B),"special",1);attack(current);
            check(current.activePokemonView(A).damage()==(damaged?60:0),"Reaction did not resolve post-damage conditional Energy supply");
        }
    }
    static void timing(){for(String mode:List.of("DISCARD","DEVOLVE","DISABLED","LETHAL")){
        String e=mode.equals("DISCARD")?after("{\"type\":\"discard_energy\",\"target\":\"OPPONENT_ACTIVE\",\"all\":true}"):mode.equals("DEVOLVE")?after("{\"type\":\"devolve\",\"target\":\"OPPONENT_ACTIVE\"}"):"";
        var m=game(DamageTriggeredAbilityTest.attack(mode.equals("LETHAL")?1000:10,e),List.of());attach(m,B,active(m,B),"energy",2);if(mode.equals("DISABLED"))m.disableAbilityEffectsByEffect(A,B,active(m,B));attack(m);
        check(m.activePokemonView(A).damage()==(mode.equals("LETHAL")?60:0),"Reaction used stale Energy/Ability or disappeared before lethal cleanup: "+mode);
        if(mode.equals("LETHAL"))check(m.phase()==MatchPhase.PRIZE_SELECTION,"Lethal reaction skipped prize checkpoint");
    }}
    static void identity(){
        var switched=game(DamageTriggeredAbilityTest.attack(10,after("{\"type\":\"switch\",\"target\":\"SELECT_OWN_POKEMON\"}")),List.of());UUID attacker=active(switched,A);attach(switched,B,active(switched,B),"energy",1);switched.attack(A,"hit");var p=switched.effectPokemonPrompt(A);switched.chooseEffectPokemon(A,p.resolutionId(),p.step(),p.targets().getFirst().pokemonId());DamageTriggeredAbilityTest.finishTriggers(switched);
        check(switched.activePokemonView(A).damage()==0&&switched.benchPokemonView(A).stream().anyMatch(v->v.pokemonId().equals(attacker)&&v.damage()==30),"Reaction hit replacement attacker");
        var defender=game(DamageTriggeredAbilityTest.attack(10,after("{\"type\":\"force_switch\",\"target\":\"SELECT_OPPONENT_POKEMON\"}")),List.of());UUID source=active(defender,B);attach(defender,B,source,"energy",1);defender.attack(A,"hit");var q=defender.effectPokemonPrompt(B);defender.chooseEffectPokemon(B,q.resolutionId(),q.step(),q.targets().getFirst().pokemonId());DamageTriggeredAbilityTest.finishTriggers(defender);check(defender.activePokemonView(A).damage()==30,"Reaction read replacement defender Energy instead of captured source");
    }
    static void guards(){for(boolean conditional:List.of(false,true)){
        var m=game(DamageTriggeredAbilityTest.attack(10,""),List.of(guard(conditional?"{\"type\":\"HP_BELOW\",\"target\":\"SELF\",\"value\":500}":"")));
        attach(m,B,active(m,B),"special",6);attack(m);check(m.activePokemonView(A).damage()==(conditional?720:0),"Computed single placement reevaluated protection between 60-counter chunks");
    }}
    static void nonDamage(){
        var m=game(DamageTriggeredAbilityTest.attack(0,after("{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}")),List.of());attach(m,B,active(m,B),"energy",1);attack(m);check(m.activePokemonView(A).damage()==0,"Nonattack-damage counters triggered reflection");
        var bench=game(DamageTriggeredAbilityTest.attack(10,",\"damageTarget\":\"ALL_OPPONENT_BENCH\""),List.of());UUID source=active(bench,B);attach(bench,B,source,"energy",1);bench.switchActiveByEffect(A,B,bench.benchPokemonView(B).getFirst().pokemonId());attack(bench);check(bench.activePokemonView(A).damage()==0,"Benched source reflected");
    }
    static void schema(){TriggeredAbilityRules.validate(ability());
        for(String bad:List.of(COUNTERS.replace("ATTACKING_POKEMON","SELF"),COUNTERS.replace("GRASS","UNKNOWN"),COUNTERS.replace("\"amount\":3","\"amount\":0"),COUNTERS.replace("\"amount\":3","\"amount\":11"),COUNTERS.replace("\"amount\":3","\"amount\":3,\"count_mode\":\"CARDS\""),COUNTERS.replace("\"amount\":3","\"amount\":3,\"from_trigger_damage\":true")))
            reject(()->DamageCounterEffect.from(EffectRegistry.standard().parse(JsonParser.parseString(bad).getAsJsonObject())));
        var effect=EffectRegistry.standard().parse(JsonParser.parseString(COUNTERS).getAsJsonObject());reject(()->ResumableProgramRules.validate(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.AFTER_ATTACK,List.of(),List.of(effect)))),true,EffectTrigger.AFTER_ATTACK));
        var damage=new DamageTriggerPolicy(true,false,false,EnergyTriggerPolicy.Usage.NONE,true,false,EventPokemonScope.OWN_FIELD,Set.of("Source"));
        reject(()->TriggeredAbilityRules.validateProgram(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_DAMAGE,List.of(),List.of(effect)))),damage,null));
        reject(()->ResumableProgramRules.validate(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ONCE_PER_TURN,List.of(),List.of(effect)))),true,EffectTrigger.ONCE_PER_TURN));
        var m=game();reject(()->m.placeDamageCountersByEffect(A,B,active(m,B),61));
    }
}
