import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Full-HP trigger predicates use the damage event, never the later mutated battlefield. */
public final class GeneratedRuleBatch34Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static AbilityDefinition ability(String id){return GeneratedRuleBatch8Test.ability(id);}
    static TrainerProfile tool(String id){return GeneratedRuleBatch8Test.trainer(id);}
    static AttackDefinition hit(int n,String extra){return DamageTriggeredAbilityTest.attack(n,extra+",\"damageType\":\"COLORLESS\"");}
    static String after(String effect){return ",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":["+effect+"]}]";}
    static class Config {
        List<AbilityDefinition> abilities=List.of();TrainerProfile tool=DynamicPassiveModifierTest.plainTool();
        Set<PokemonFamily> families=Set.of();Set<EnergyType> types=Set.of(EnergyType.FIGHTING);AttackDefinition attack=hit(500,"");
    }
    static ServerMatch game(Config c){
        String[] ids={"basic","basic","evolved","tool","energy","stadium"};var defs=new ArrayList<CardDefinition>();
        for(String id:Arrays.stream(ids).distinct().toList()){
            boolean p=Set.of("basic","evolved").contains(id);var category=id.equals("tool")?TrainerCategory.TOOL:id.equals("stadium")?TrainerCategory.STADIUM:TrainerCategory.NONE;
            defs.add(new CardDefinition(id,id,p?CardCategory.POKEMON:id.equals("energy")?CardCategory.ENERGY:CardCategory.TRAINER,category,"H",Set.of("international"),id.equals("basic"),id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null,p?c.families:Set.of(),p?c.types:Set.of()));
        }
        var pokemon=new HashMap<String,PokemonProfile>();for(String id:List.of("basic","evolved"))pokemon.put(id,new PokemonProfile(300,3,id.equals("evolved")?"basic":null,null,0,null,0,1,List.of(c.attack),c.abilities,EnergyType.FIGHTING));
        var catalog=new CombatCatalog(pokemon,Map.of("energy",EnergyType.GRASS),Map.of("tool",c.tool,"stadium",DynamicPassiveModifierTest.plainStadium()));
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(defs),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(x->x.definitionId().equals("basic")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,28);m.drawCardsByEffect(A,B,28);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(p->Arrays.stream(ids).allMatch(id->m.privateView(p).hand().stream().anyMatch(x->x.definitionId().equals(id)))))return m;
        }throw new AssertionError("full HP fixture");
    }
    static void swing(ServerMatch m){m.attack(A,"hit");DamageTriggeredAbilityTest.finishTriggers(m);}
    static CardInstance attach(ServerMatch m,UUID owner,UUID host){var card=hand(m,owner,"tool");m.attachToolByEffect(A,owner,EffectSourceZone.HAND,card.instanceId(),owner,host);return card;}
    public static void main(String[] args){sturdy();snapshotTiming();snapshotTransitions();damageSources();tools();survivalOrder();knockoutReaction();schema();System.out.println("GeneratedRuleBatch34Test passed: pre-damage full HP, actual lethal/KO separation, Active/Bench/recoil sources, suppression, one successful survival Tool, real KO reflection, printed type/family and strict policy parsing");}
    static void sturdy(){for(String id:List.of("me2-5_en_057","sm8_en_112","swsh3_en_123","zsv10-5_en_052"))for(String mode:List.of("full","injured","shred","disabled","small")){
        var c=new Config();c.abilities=List.of(ability(id));c.attack=hit(mode.equals("small")?30:500,mode.equals("shred")?",\"ignoresActiveDefenderDamageEffects\":true":"");var m=game(c);var host=active(m,B);
        if(mode.equals("injured"))m.placeDamageCountersByEffect(A,B,host,1);if(mode.equals("disabled"))m.disableAbilityEffectsByEffect(A,B,host);swing(m);
        boolean saved=!Set.of("injured","disabled").contains(mode);check((m.phase()==MatchPhase.TURN)==saved,"Sturdy fullHP/source boundary "+id+mode);
        if(saved)check(active(m,B).equals(host)&&m.activePokemonView(B).damage()==(mode.equals("small")?30:290),"Sturdy overkill floor or non-lethal damage "+mode);
    }}
    static void snapshotTiming(){
        var c=new Config();c.abilities=List.of(ability("zsv10-5_en_052"));c.attack=hit(500,",\"triggers\":[{\"trigger\":\"BEFORE_ATTACK\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}]}]");var m=game(c);swing(m);check(m.phase()==MatchPhase.PRIZE_SELECTION,"Pre-damage counters ignored by full-HP snapshot");
        c.attack=hit(500,after("{\"type\":\"disable_abilities\",\"target\":\"OPPONENT_ACTIVE\"}"));m=game(c);swing(m);check(m.phase()==MatchPhase.PRIZE_SELECTION,"After-text Ability suppression ignored");
        c.attack=hit(500,after("{\"type\":\"knock_out\",\"target\":\"OPPONENT_ACTIVE\"}"));m=game(c);swing(m);check(m.phase()==MatchPhase.PRIZE_SELECTION,"Sturdy cancelled direct KO");
        c.attack=hit(0,after("{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}"));m=game(c);swing(m);check(m.phase()==MatchPhase.PRIZE_SELECTION,"Counters treated as attack damage");
        c.attack=hit(500,"");m=game(c);swing(m);m.healDamageByEffect(B,B,active(m,B),290);m.endTurn(B);swing(m);check(m.phase()==MatchPhase.TURN&&m.activePokemonView(B).damage()==290,"Fully healed host cannot reuse Sturdy");
        m=game(c);m.applySpecialConditionByEffect(A,B,SpecialCondition.POISONED);swing(m);check(m.phase()==MatchPhase.PRIZE_SELECTION,"Survival incorrectly blocks subsequent Checkup KO");
    }
    static void snapshotTransitions(){
        var c=new Config();c.abilities=List.of(ability("zsv10-5_en_052"));c.attack=hit(500,",\"triggers\":[{\"trigger\":\"BEFORE_ATTACK\",\"effects\":[{\"type\":\"heal\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":10}]}]");var m=game(c);m.placeDamageCountersByEffect(A,B,active(m,B),1);swing(m);check(m.phase()==MatchPhase.TURN&&m.activePokemonView(B).damage()==290,"Snapshot used stale pre-healing target");
        for(boolean survival:List.of(false,true)){
            c=new Config();c.abilities=List.of(ability(survival?"zsv10-5_en_052":"swsh12_en_064"));
            c.attack=hit(500,",\"damageTarget\":\"ALL_OPPONENT_BENCH\""+after("{\"type\":\"heal\",\"target\":\"OPPONENT_BENCH\",\"amount\":290},{\"type\":\"bench_damage\",\"target\":\"OPPONENT_BENCH\",\"amount\":500}"));
            m=game(c);swing(m);check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.activePokemonView(A).damage()==0,"A healed then injured lethal hit inherited the previous full-HP lethal event");
        }
        c=new Config();c.abilities=List.of(ability("zsv10-5_en_052"));c.attack=hit(0,",\"selfDamage\":500,\"selfHealAfterAttack\":300"+after("{\"type\":\"self_damage\",\"target\":\"SELF\",\"amount\":500}"));m=game(c);swing(m);check(m.phase()==MatchPhase.PRIZE_SELECTION,"Legacy self-heal retained the first lethal hit's full-HP cause");
    }
    static void damageSources(){for(String mode:List.of("main-bench","composed-bench","own-bench","legacy-recoil","composed-recoil")){
        var c=new Config();c.abilities=List.of(ability("zsv10-5_en_052"));UUID victim=mode.startsWith("own")||mode.endsWith("recoil")?A:B;boolean onBench=mode.contains("bench");
        c.attack=switch(mode){case "main-bench"->hit(500,",\"damageTarget\":\"ALL_OPPONENT_BENCH\"");case "composed-bench"->hit(0,after("{\"type\":\"bench_damage\",\"target\":\"OPPONENT_BENCH\",\"amount\":500}"));case "own-bench"->hit(0,after("{\"type\":\"bench_damage\",\"target\":\"OWN_BENCH\",\"amount\":500}"));case "legacy-recoil"->hit(0,",\"selfDamage\":500");default->hit(0,after("{\"type\":\"self_damage\",\"target\":\"SELF\",\"amount\":500}"));};
        var m=game(c);swing(m);check(m.phase()==MatchPhase.TURN&&(onBench?m.benchPokemonView(victim).getFirst():m.activePokemonView(victim)).damage()==290,"Full HP missing from damage path "+mode);
    }}
    static void tools(){for(String id:List.of("sv6_en_164","xy3_en_091"))for(String mode:List.of("full","injured","disabled","ability-disabled","wrong-type","dual-type","bench","own-recoil","small")){
        var c=new Config();c.tool=tool(id);if(mode.equals("wrong-type"))c.types=Set.of(EnergyType.WATER);if(mode.equals("dual-type"))c.types=Set.of(EnergyType.WATER,EnergyType.FIGHTING);
        boolean onBench=mode.equals("bench"),own=mode.equals("own-recoil");UUID victim=own?A:B;c.attack=hit(mode.equals("small")?30:own?0:500,own?",\"selfDamage\":500":onBench?",\"damageTarget\":\"ALL_OPPONENT_BENCH\"":"");
        var m=game(c);UUID host=onBench?bench(m,victim):active(m,victim);var card=attach(m,victim,host);if(mode.equals("injured"))m.placeDamageCountersByEffect(A,victim,host,1);if(mode.equals("disabled"))m.disableToolEffectsByEffect(A,victim,host);if(mode.equals("ability-disabled"))m.disableAbilityEffectsByEffect(A,victim,host);
        swing(m);boolean save=!Set.of("injured","disabled","own-recoil").contains(mode)&&(!mode.equals("wrong-type")||!id.equals("xy3_en_091"));check((m.phase()==MatchPhase.TURN)==save,"Survival Tool boundary "+id+mode);
        if(save){var view=onBench?m.benchPokemonView(victim).getFirst():m.activePokemonView(victim);check(view.damage()==(mode.equals("small")?30:290),"Tool floor");check(m.privateView(victim).discard().contains(card)!=mode.equals("small"),"Tool discarded without survival or retained after survival");}
    }}
    static void survivalOrder(){for(boolean toolFirst:List.of(false,true)){
        var c=new Config();c.tool=tool("xy3_en_091");c.abilities=List.of(ability("zsv10-5_en_052"));var m=game(c);var card=attach(m,B,active(m,B));m.attack(A,"hit");var p=m.triggerOrderPrompt();check(p!=null&&p.entries().size()==2,"Survival sources did not offer owner order");var choice=p.entries().stream().filter(e->(e.sourceKind()==EffectOrigin.Kind.TRAINER)==toolFirst).findFirst().orElseThrow();m.chooseTriggeredEffect(B,p.id(),choice.id());DamageTriggeredAbilityTest.finishTriggers(m);check(m.phase()==MatchPhase.TURN&&m.activePokemonView(B).damage()==290,"Survival order changed HP");check(m.privateView(B).discard().contains(card)==toolFirst,"Unused Focus Sash discarded after Sturdy already succeeded");
    }}
    static void knockoutReaction(){for(boolean toolSource:List.of(false,true))for(String mode:List.of("full","injured","shred","disabled","bench","counter","direct","survived","wrong-family")){
        var c=new Config();var reflection=ability("swsh12_en_064");c.abilities=toolSource?List.of():List.of(reflection);if(toolSource){c.tool=tool("swsh11_en_154");c.families=mode.equals("wrong-family")?Set.of(PokemonFamily.GX,PokemonFamily.RULE_BOX):Set.of(PokemonFamily.V,PokemonFamily.RULE_BOX);}
        if(mode.equals("survived"))c.abilities=toolSource?List.of(ability("zsv10-5_en_052")):List.of(reflection,ability("zsv10-5_en_052"));
        c.attack=hit(mode.equals("counter")||mode.equals("direct")?0:500,mode.equals("shred")?",\"ignoresActiveDefenderDamageEffects\":true":mode.equals("bench")?",\"damageTarget\":\"ALL_OPPONENT_BENCH\"":mode.equals("counter")?after("{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}"):mode.equals("direct")?after("{\"type\":\"knock_out\",\"target\":\"OPPONENT_ACTIVE\"}"):"");
        var m=game(c);UUID host=mode.equals("bench")?bench(m,B):active(m,B);if(toolSource)attach(m,B,host);if(mode.equals("injured"))m.placeDamageCountersByEffect(A,B,host,1);if(mode.equals("disabled")){if(toolSource)m.disableToolEffectsByEffect(A,B,host);else m.disableAbilityEffectsByEffect(A,B,host);}swing(m);
        boolean reflect=!Set.of("injured","disabled","counter","direct","survived").contains(mode)&&(!toolSource||!mode.equals("wrong-family"));check(m.activePokemonView(A).damage()==(reflect?80:0),"Actual KO reflection/source/fullHP "+toolSource+mode);
    }}
    static void schema(){
        String damage="{\"trigger\":\"ON_DAMAGE\",\"from\":\"ANY_ATTACK\",\"activeOnly\":false,\"lethalOnly\":true,\"optional\":false,\"usage\":\"NONE\",\"fullHpOnly\":true}";
        var p=(DamageTriggerPolicy)AbilityEventPolicy.parse(JsonParser.parseString(damage).getAsJsonObject());check(p.matches(false,500,true,true)&&!p.matches(false,500,true,false),"Damage full HP filter");reject(()->AbilityEventPolicy.parse(JsonParser.parseString(damage.replace("\"fullHpOnly\":true","\"fullHpOnly\":\"true\"")).getAsJsonObject()));
        String ko="{\"trigger\":\"ON_KNOCKOUT\",\"cause\":\"ANY\",\"activeOnly\":false,\"optional\":false,\"usage\":\"NONE\",\"fullHpOnly\":true}";reject(()->AbilityEventPolicy.parse(JsonParser.parseString(ko).getAsJsonObject()));
    }
}
