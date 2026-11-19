import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exercises every migrated real attack, including SD100's mutually exclusive Fire/Lightning plans. */
public final class GeneratedRuleBatch56Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final Set<String> INCOMPLETE=Set.of("sm6_en_095","sm6_en_127","sm6_en_140","sm7-5_en_078","smbsp_en_126","swsh3-5_en_023","swsh4-5_en_073","xy1_en_046","xy1_en_143");
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static JsonObject read(String name){try(var in=GeneratedRuleBatch56Test.class.getResourceAsStream("/data/ptcglive/"+name)){
        return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject();
    }catch(IOException e){throw new UncheckedIOException(e);}}
    static List<JsonObject> attacks(){var result=new ArrayList<JsonObject>();for(var f:read("combat/index.json").getAsJsonArray("files"))for(var c:read("combat/"+f.getAsString()).getAsJsonArray("cards")){
        var card=c.getAsJsonObject();if(!card.has("attacks"))continue;for(var a:card.getAsJsonArray("attacks"))if(a.toString().contains("plan_energy_discard")){var copy=a.getAsJsonObject().deepCopy();copy.addProperty("testCardId",card.get("id").getAsString());result.add(copy);}
    }return result;}
    public static void main(String[] args){var attacks=attacks();var definitions=BundledRules.loadCardDefinitions();check(attacks.size()==112,"Unexpected real attack migration scope: "+attacks.size());int scenarios=0;
        for(var j:attacks){String id=j.remove("testCardId").getAsString();var actual=BundledCombatData.attack(j,EffectRegistry.standard());AttackProgramRules.validate(actual);
            check(definitions.find(id).orElseThrow().effectSupport()==(INCOMPLETE.contains(id)?EffectSupport.UNSUPPORTED:EffectSupport.SUPPORTED),"Whole-card status changed during component correction "+id);
            var root=actual.effectProgram().forTrigger(EffectTrigger.BEFORE_ATTACK).getFirst().effects().getLast();var plans=root.type().equals("choice")?root.effects():List.of(root);
            for(int option=0;option<plans.size();option++){var plan=plans.get(option);var free=j.deepCopy();free.add("energyCost",new JsonObject());free.addProperty("damageType","COLORLESS");var attack=BundledCombatData.attack(free,EffectRegistry.standard());
                EnergyType type=EnergyType.valueOf(plan.arguments().getOrDefault("energy_type","FIRE"));var m=GeneratedRuleBatch8Test.fixture(List.of(),type,EnergyType.COLORLESS,GeneratedRuleBatch8Test.trainer("swsh5_en_124"),attack);
                for(int i=0;i<3;i++){var c=GeneratedRuleBatch13Test.hand(m,A,"energy");m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,c.instanceId(),A,i==2?m.benchPokemonView(A).getFirst().pokemonId():m.activePokemonView(A).pokemonId());}
                var special=GeneratedRuleBatch13Test.hand(m,A,"special");m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,special.instanceId(),A,m.benchPokemonView(A).getFirst().pokemonId());
                var policy=PlannedEnergyDiscard.selection(plan);int expectedCards=policy.all()?(plan.target()==EffectTarget.ALL_OWN_POKEMON?3+(policy.cardKind()!=EnergyCountQuery.CardKind.BASIC?1:0):2):-1;
                m.attack(A,attack.id());if(root.type().equals("choice")){var choice=m.effectChoicePrompt(A);m.chooseEffectOption(A,choice.resolutionId(),choice.step(),option);}
                if(!policy.all()){var prompt=m.effectCardPrompt(A);check(prompt!=null,id+" missing public Energy selection");int required=policy.countMode()==EnergyCountQuery.Mode.UNITS?Math.min(policy.minimum(),prompt.candidates().size()):Math.max(1,prompt.minimum());var selected=prompt.candidates().stream().limit(required).toList();expectedCards=selected.size();m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),selected.stream().map(CardInstance::instanceId).toList());}
                int expected=attack.baseDamage();for(var binding:attack.effectProgram().forTrigger(EffectTrigger.ON_ATTACK))for(var effect:binding.effects())expected+=damage(effect,plan.arguments().get("bind"),expectedCards);
                check(m.lastAttack()!=null&&m.lastAttack().finalDamage()==expected,id+"/"+attack.id()+" wrong damage after plan: "+(m.lastAttack()==null?"pending":m.lastAttack().finalDamage())+" expected "+expected);
                check(m.privateView(A).discard().stream().filter(c->Set.of("energy","special").contains(c.definitionId())).count()==expectedCards,id+" discarded wrong physical count");scenarios++;
            }
        }fireBlaster();System.out.println("GeneratedRuleBatch56Test passed: all "+attacks.size()+" migrated actual attacks, "+scenarios+" execution scenarios including both SD100 choice branches, plus actual Fire Blaster double-unit single-card payment; printed programs validated, neutral free hosts isolate count semantics, actual physical discards checked");
    }
    static void fireBlaster(){var j=GeneratedRuleBatch8Test.entry("sm5_en_019").getAsJsonArray("attacks").get(0).getAsJsonObject();var a=BundledCombatData.attack(j,EffectRegistry.standard());
        var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.FIRE,EnergyType.COLORLESS,GeneratedRuleBatch8Test.trainer("swsh5_en_124"),a);var special=PlannedEnergyDiscardTest.attach(m,A,m.activePokemonView(A).pokemonId(),"special");PlannedEnergyDiscardTest.attach(m,A,m.activePokemonView(A).pokemonId(),"energy");
        m.attack(A,a.id());var p=m.effectChoicePrompt(A);m.chooseEffectOption(A,p.resolutionId(),p.step(),0);PlannedEnergyDiscardTest.submit(m,List.of(special));check(m.lastAttack().finalDamage()==160&&m.activePokemonView(A).attachedEnergyIds().size()==1&&m.privateView(A).discard().contains(special),"Actual Fire Blaster required two physical cards instead of two Energy units");
    }
    static int damage(EffectDefinition effect,String name,int count){
        if(effect.type().equals("conditional")){for(var c:effect.conditions()){check(c.type()==ConditionType.BOUND_COUNT,"Unexpected migrated damage condition");int n=c.arguments().get("bind").equals(name)?count:0;if(n<Integer.parseInt(c.arguments().get("value")))return 0;}return effect.effects().stream().mapToInt(e->damage(e,name,count)).sum();}
        int amount=Integer.parseInt(effect.arguments().get("amount"));if(effect.type().equals("damage_per_bound"))return effect.arguments().get("bind").equals(name)?count*amount:0;
        check(effect.type().equals("damage"),"Unexpected migrated damage component "+effect.type());return amount;
    }
}
