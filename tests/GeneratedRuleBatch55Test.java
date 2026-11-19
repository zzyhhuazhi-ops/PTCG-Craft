import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Actual ME5 programs; free, neutral synthetic hosts isolate live field-count predicates. */
public final class GeneratedRuleBatch55Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static void attach(ServerMatch m,UUID p,UUID host,String id){var c=hand(m,p,id);m.attachEnergyByEffect(A,p,EffectSourceZone.HAND,c.instanceId(),p,host);}
    static AttackDefinition attack(String id){
        if(id.startsWith("me5_"))return GeneratedRuleBatch6Test.attack(id,0);
        // These three old cards still lack their Ability: exercise the exact
        // converter component evidence, without publishing a partial combat profile.
        try(var in=GeneratedRuleBatch55Test.class.getResourceAsStream("/data/ptcglive/references/generated-rule-batch55-sources-2026-10-14.json")){
            var data=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject();
            for(var entry:data.getAsJsonArray("evidence")){var row=entry.getAsJsonObject();if(!row.get("cardId").getAsString().equals(id))continue;
                var mapping=row.getAsJsonObject("mapping");var j=mapping.getAsJsonObject("fields").deepCopy();j.addProperty("id","colony-rush");j.addProperty("nameZh","未发布同文组件回归");j.addProperty("damageType","COLORLESS");j.addProperty("effectSupport","SUPPORTED");j.add("energyCost",new JsonObject());j.add("effects",mapping.getAsJsonArray("program").deepCopy());var attack=BundledCombatData.attack(j,EffectRegistry.standard());AttackProgramRules.validate(attack);return attack;
            }throw new AssertionError("Missing unpromoted component evidence "+id);
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    static ServerMatch energyGame(AttackDefinition a,EnergyType type){return GeneratedRuleBatch8Test.fixture(List.of(),type,EnergyType.COLORLESS,GeneratedRuleBatch8Test.trainer("swsh5_en_124"),a);}
    static ServerMatch fireGame(AttackDefinition a){return energyGame(a,EnergyType.FIRE);}
    public static void main(String[] args){printed();fireHosts();specialProvision();liveChanges();names();printedFossilPredicate();copyOwnership();schema();
        System.out.println("GeneratedRuleBatch55Test passed: both actual Armarouge and Relicanth prints, printed costs/base damage, zero to five own energized Bench hosts counted once per host, Active/opponent/other type exclusions, Basic and multi-unit/selectable/conditional Special Energy and live override/evolution/discard changes, exact printed name substring/top-card facts and Trainer Fossil predicate, copied actor ownership, strict schema and compatibility constructors");}
    static void printed(){var db=BundledRules.loadCardDefinitions();for(String id:List.of("me5_en_012","me5_en_017","me5_en_086")){
        check(db.find(id).orElseThrow().effectSupport()==EffectSupport.SUPPORTED,"Missing audited full support: "+id);
        var j=GeneratedRuleBatch8Test.entry(id).getAsJsonArray("attacks").get(0).getAsJsonObject();var a=BundledCombatData.attack(j,EffectRegistry.standard());
        check(a.baseDamage()==(id.equals("me5_en_017")?10:40)&&a.energyCost().equals(Map.of(id.equals("me5_en_017")?EnergyType.COLORLESS:EnergyType.FIRE,1)),"Printed cost/base damage changed: "+id);AttackProgramRules.validate(a);
    }for(String id:List.of("sv3_en_022","sv4-5_en_005","sv4-5_en_213"))check(db.find(id).orElseThrow().effectSupport()==EffectSupport.SUPPORTED,"Completed Toedscruel ex was not promoted: "+id);}
    static void fireHosts(){for(String id:List.of("me5_en_012","me5_en_086","sv3_en_022","sv4-5_en_005","sv4-5_en_213"))for(int n=0;n<=5;n++){
        var a=attack(id);var type=id.startsWith("me5_")?EnergyType.FIRE:EnergyType.GRASS;var m=energyGame(a,type);while(m.benchPokemonView(A).size()<5)m.playBasicPokemonToBench(A,hand(m,A,"basic").instanceId());
        attach(m,A,active(m,A),"energy");attach(m,B,bench(m,B),"energy");var hosts=m.benchPokemonView(A);
        for(int i=0;i<n;i++)attach(m,A,hosts.get(i).pokemonId(),"energy");
        if(n>0){attach(m,A,hosts.getFirst().pokemonId(),"special");attach(m,A,hosts.getFirst().pokemonId(),"energy");}
        if(n<5)attach(m,A,hosts.getLast().pokemonId(),"wrong");
        int rng=m.randomTranscript().size();var cards=m.privateView(A);m.attack(A,a.id());check(m.lastAttack().finalDamage()==a.baseDamage()+40*n&&m.activePokemonView(B).damage()==a.baseDamage()+40*n,"Energized count used cards/units, Active, opponent or wrong type: "+id+" / "+n);
        check(m.randomTranscript().size()==rng&&m.currentPlayer().equals(B)&&m.privateView(A).hand().equals(cards.hand()),"Pure field count spent/reserved Energy or RNG");
    }}
    static void specialProvision(){var a=attack("me5_en_012");for(String json:List.of("{\"energyUnits\":[[\"FIRE\"],[\"FIRE\"],[\"FIRE\"]]}","{\"energyUnits\":[[\"FIRE\",\"WATER\"]]}","{\"energyUnits\":[[\"COLORLESS\"]],\"provisionRules\":[{\"conditions\":[{\"type\":\"IS_BASIC\"}],\"energyUnits\":[[\"FIRE\"],[\"FIRE\"]]}]}")){
        var energy=DynamicEnergyProvisionTest.energy(json);var m=DynamicEnergyProvisionTest.fixture(energy,DynamicPassiveModifierTest.plainStadium(),a);attach(m,A,bench(m,A),"special");m.attack(A,a.id());check(m.lastAttack().finalDamage()==80,"Selectable/conditional multi-unit Fire host not counted once");
    }
        var conditional=DynamicEnergyProvisionTest.energy("{\"energyUnits\":[[\"COLORLESS\"]],\"provisionRules\":[{\"conditions\":[{\"type\":\"IS_BASIC\"}],\"energyUnits\":[[\"FIRE\"],[\"FIRE\"]]}]}");
        var evolved=DynamicEnergyProvisionTest.fixture(conditional,DynamicPassiveModifierTest.plainStadium(),a);UUID host=bench(evolved,A);attach(evolved,A,host,"special");evolved.evolve(A,host,hand(evolved,A,"evolved").instanceId());evolved.attack(A,a.id());check(evolved.lastAttack().finalDamage()==40,"Fire count used printed Special Energy instead of live evolved provision");
        var stadium=DynamicPassiveModifierTest.trainer("STADIUM","{"+DynamicEnergyProvisionTest.OVERRIDE+",\"target\":\"ALL_OWN_POKEMON\"}");var m=DynamicEnergyProvisionTest.fixture(EnergyProfile.fixed(EnergyType.FIRE,2),stadium,a);attach(m,A,bench(m,A),"special");m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);m.attack(A,a.id());check(m.lastAttack().finalDamage()==40,"Special Energy override left stale Fire count");m.endTurn(B);m.discardStadiumByEffect(A);m.attack(A,a.id());check(m.lastAttack().finalDamage()==80,"Removing override did not restore Fire host count");
    }
    static void liveChanges(){var a=attack("me5_en_012");var m=fireGame(a);var energy=hand(m,A,"energy");UUID host=bench(m,A);m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,energy.instanceId(),A,host);m.attack(A,a.id());check(m.lastAttack().finalDamage()==80,"Initial Fire host not counted");m.endTurn(B);m.discardAttachedEnergyByEffect(A,A,host,List.of(energy.instanceId()));m.attack(A,a.id());check(m.lastAttack().finalDamage()==40,"Discarded Energy left stale count");
        var empty=fireGame(a);empty.movePokemonByEffect(A,A,bench(empty,A),LeavePlayDestination.HAND);attach(empty,A,active(empty,A),"energy");empty.attack(A,a.id());check(empty.lastAttack().finalDamage()==40,"Empty Bench counted energized Active");
    }
    static void names(){var a=attack("me5_en_017");for(String name:List.of("Antique Armor Fossil","Rebuilt Antique Relic","Ancient Artifact","antique armor fossil")){
        var m=GeneratedRuleBatch21Test.fixture(a,Map.of("basic",name,"evolved","Modern Armor","rulebox","Modern Relic"),Map.of());m.attack(A,a.id());check(m.lastAttack().finalDamage()==(name.contains("Antique")?40:10),"Relicanth substring became equality/case folding/Active count: "+name);
    }
        var m=GeneratedRuleBatch21Test.fixture(a,Map.of("basic","Antique Armor Fossil","evolved","Modern Armor","rulebox","Antique Skull Fossil"),Map.of());UUID root=bench(m,A);m.playBasicPokemonToBench(A,hand(m,A,"rulebox").instanceId());m.evolve(A,root,hand(m,A,"evolved").instanceId());m.attack(A,a.id());check(m.lastAttack().finalDamage()==40,"Relicanth counted covered root or excluded Rule-Box top by an unprinted filter");
        var unknown=GeneratedRuleBatch21Test.fixture(a,Map.of(),Map.of());unknown.attack(A,a.id());check(unknown.lastAttack().finalDamage()==10,"Missing printed name inferred from internal ID");
    }
    static void printedFossilPredicate(){var e=EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"damage_per_pokemon\",\"target\":\"OWN_BENCH\",\"amount\":30,\"name_contains\":\"Antique\"}").getAsJsonObject());var q=PokemonCountEffect.from(e);
        var fossil=new CardDefinition("fossil","fossil",CardCategory.TRAINER,TrainerCategory.ITEM,"J",Set.of("international"),false,false,60,Map.of(),EffectSupport.SUPPORTED,null);var p=new PokemonProfile(60,0,null,null,0,null,0,1,List.of(),List.of(),EnergyType.COLORLESS,"Antique Armor Fossil",null);
        check(q.matches(fossil,p,60),"In-play Item Fossil printed name excluded by card category");
    }
    static void copyOwnership(){var original=GeneratedRuleBatch8Test.entry("me5_en_012").getAsJsonArray("attacks").get(0).getAsJsonObject().deepCopy();original.addProperty("id","hit");original.getAsJsonArray("effects").get(0).getAsJsonObject().addProperty("attached_energy_type","GRASS");var selected=BundledCombatData.attack(original,EffectRegistry.standard());
        // The old copy fixture has only Grass cards; this synthetic type substitution isolates actor ownership.
        for(boolean own:List.of(false,true)){var m=CopyAttackTest.fixture(CopyAttackTest.copier(),selected,EnergyType.COLORLESS,0);CopyAttackTest.pay(m);UUID p=own?CopyAttackTest.A:CopyAttackTest.B;var c=DynamicPassiveModifierTest.hand(m,p,"energy");m.attachEnergyByEffect(CopyAttackTest.A,p,EffectSourceZone.HAND,c.instanceId(),p,m.benchPokemonView(p).getFirst().pokemonId());var choice=CopyAttackTest.choice(m);CopyAttackTest.copy(m,choice,List.of(),List.of());check(m.lastAttack().finalDamage()==(own?80:40),"Copied typed host count read provider instead of actual actor");}
    }
    static void schema(){for(String extra:List.of("\"attached_energy_type\":\"FIER\"","\"attached_energy_type\":\"\"","\"name_contains\":\"\"","\"name_contains\":\" Antique\"","\"name_contains\":\"Antique \"","\"energy_type\":\"FIRE\""))reject(()->EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"damage_per_pokemon\",\"target\":\"OWN_BENCH\",\"amount\":40,"+extra+"}").getAsJsonObject()));
        var e=EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"damage_per_pokemon\",\"target\":\"OWN_BENCH\",\"amount\":40,\"attached_energy_type\":\"FIRE\"}").getAsJsonObject());var q=PokemonCountEffect.from(e);var db=new CardDefinition("basic","basic",CardCategory.POKEMON,TrainerCategory.NONE,"J",Set.of("international"),true,false,60,Map.of(),EffectSupport.SUPPORTED,null);var p=new PokemonProfile(60,0,null);reject(()->q.matches(db,p,60));reject(()->q.matches(db,p,60,-1));check(!q.matches(db,p,60,0)&&q.matches(db,p,60,4),"Matching Energy became unit count instead of Boolean host predicate");
        var old=new PokemonCountEffect(PokemonFamilyFilter.ANY,null,PokemonCountEffect.Mode.POKEMON,PokemonTypeFilter.ANY,Set.of(),0);check(old.attachedEnergyType()==null&&old.nameContains()==null&&old.matches(db,p,60),"Compatibility constructor changed old filters");
    }
}
