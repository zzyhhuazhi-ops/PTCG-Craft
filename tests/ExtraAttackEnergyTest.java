import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Extra units must be disjoint from current attack payment, including copying and continuation. */
public final class ExtraAttackEnergyTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void reject(Runnable r){BeforeDamageAttackTest.reject(r);}
    static String condition(int count,String type){return "{\"type\":\"HAS_EXTRA_ATTACK_ENERGY\",\"target\":\"SELF\",\"value\":"+count+(type==null?"":",\"energy_type\":\""+type+"\"")+"}";}
    static String branch(int count,String type,String effect){return "{\"type\":\"conditional\",\"conditions\":["+condition(count,type)+"],\"effects\":["+effect+"]}";}
    static AttackDefinition attack(String cost,String triggers){
        var a=BundledCombatData.attack(JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"额外能量\",\"energyCost\":"+cost+",\"baseDamage\":20,\"damageType\":\"COLORLESS\",\"effectSupport\":\"SUPPORTED\",\"triggers\":"+triggers+"}").getAsJsonObject(),EffectRegistry.standard());
        AttackProgramRules.validate(a);return a;
    }
    static String damage(){return "{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}";}
    static AttackDefinition bonus(String cost,int count,String type){return attack(cost,"[{\"trigger\":\"ON_ATTACK\",\"effects\":["+branch(count,type,damage())+"]}]");}
    static ServerMatch fixture(AttackDefinition attack,EnergyProfile energy,int costChange){
        var tool=costChange==0?DynamicPassiveModifierTest.plainTool():DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"attack_cost_modifier\",\"amount\":"+costChange+"}");
        return DynamicPassiveModifierTest.fixture(tool,DynamicPassiveModifierTest.plainStadium(),energy,attack);
    }
    static void attach(ServerMatch m,String id,int n){for(int i=0;i<n;i++)TypedEnergyCountTest.attach(m,A,m.activePokemonView(A).pokemonId(),id);}
    static void hit(ServerMatch m,int expected){m.attack(A,"hit");check(m.activePokemonView(B).damage()==expected,"wrong extra Energy damage: expected "+expected+" got "+m.activePokemonView(B).damage());check(m.currentPlayer().equals(B),"attack did not end once");}
    public static void main(String[] args){
        allocation();currentCosts();typedAndDoubleUnits();liveProvision();continuedQueries();copyCost();schemaAndEntrypoints();
        System.out.println("ExtraAttackEnergyTest passed: disjoint units, typed/generic costs, live modifiers/provision, copied cost, continuation and strict entrypoints");
    }
    static void allocation(){
        var any=new EnergyProfile(List.of(new EnergyProfile.Unit(Set.of(EnergyType.GRASS,EnergyType.FIRE))));
        var fire=new ExtraAttackEnergyRequirement(EnergyType.FIRE,1);
        check(!fire.matches(List.of(any),Map.of(EnergyType.GRASS,1)),"flexible unit counted twice");
        check(fire.matches(List.of(any,EnergyProfile.basic(EnergyType.GRASS)),Map.of(EnergyType.GRASS,1)),"flexible allocation not reserved correctly");
        check(!fire.matches(List.of(EnergyProfile.fixed(EnergyType.COLORLESS,2)),Map.of(EnergyType.COLORLESS,1)),"generic unit became typed extra");
        check(new ExtraAttackEnergyRequirement(EnergyType.COLORLESS,1).matches(List.of(EnergyProfile.fixed(EnergyType.GRASS,2)),Map.of(EnergyType.GRASS,1)),"second unit of one card not counted");
        check(!fire.matches(List.of(EnergyProfile.basic(EnergyType.FIRE)),Map.of(EnergyType.COLORLESS,1)),"generic cost reused extra typed unit");
    }
    static void currentCosts(){
        for(int change:List.of(-1,0,1)){
            var m=fixture(bonus("{\"GRASS\":1,\"COLORLESS\":1}",1,null),EnergyProfile.fixed(EnergyType.COLORLESS,2),change);
            attach(m,"energy",3);if(change!=0)m.playTrainer(A,DynamicPassiveModifierTest.hand(m,A,"tool").instanceId(),m.activePokemonView(A).pokemonId());
            hit(m,change==1?20:50);
        }
        var reduced=fixture(bonus("{\"GRASS\":1,\"COLORLESS\":1}",1,null),EnergyProfile.fixed(EnergyType.COLORLESS,2),-1);
        attach(reduced,"energy",2);reduced.playTrainer(A,DynamicPassiveModifierTest.hand(reduced,A,"tool").instanceId(),reduced.activePokemonView(A).pokemonId());hit(reduced,50);
        var disabled=fixture(bonus("{\"GRASS\":1,\"COLORLESS\":1}",1,null),EnergyProfile.fixed(EnergyType.COLORLESS,2),-1);
        attach(disabled,"energy",2);disabled.playTrainer(A,DynamicPassiveModifierTest.hand(disabled,A,"tool").instanceId(),disabled.activePokemonView(A).pokemonId());
        disabled.disableToolEffectsByEffect(A,A,disabled.activePokemonView(A).pokemonId());hit(disabled,20);
        var typed=fixture(bonus("{\"GRASS\":2}",1,null),EnergyProfile.fixed(EnergyType.COLORLESS,2),-3);
        attach(typed,"energy",2);typed.playTrainer(A,DynamicPassiveModifierTest.hand(typed,A,"tool").instanceId(),typed.activePokemonView(A).pokemonId());hit(typed,20);
    }
    static void typedAndDoubleUnits(){
        for(String required:List.of("GRASS","COLORLESS"))for(EnergyType supplied:List.of(EnergyType.GRASS,EnergyType.COLORLESS)){
            var m=fixture(bonus("{\"GRASS\":1}",2,required),EnergyProfile.fixed(supplied,2),0);attach(m,"energy",1);attach(m,"special",1);
            hit(m,required.equals("COLORLESS")||supplied==EnergyType.GRASS?50:20);
        }
        var insufficient=fixture(bonus("{\"GRASS\":2}",1,"GRASS"),EnergyProfile.fixed(EnergyType.GRASS,2),0);attach(insufficient,"special",1);hit(insufficient,20);
    }
    static void liveProvision(){
        var profile=BundledCombatData.specialEnergy(JsonParser.parseString("""
            {"energyUnits":[["COLORLESS"]],"provisionRules":[
              {"conditions":[{"type":"DAMAGE_COUNTER_COUNT","target":"SELF","value":1}],"energyUnits":[["GRASS"],["GRASS"]]}]}
            """).getAsJsonObject(),EffectRegistry.standard());
        for(boolean damage:List.of(false,true)){
            var m=fixture(bonus("{\"GRASS\":1}",1,"GRASS"),profile,0);attach(m,"energy",1);attach(m,"special",1);
            if(damage)m.placeDamageCountersByEffect(A,A,m.activePokemonView(A).pokemonId(),1);hit(m,damage?50:20);
        }
    }
    static void continuedQueries(){
        for(boolean discard:List.of(false,true)){
            String before=discard?"{\"type\":\"discard_energy\",\"target\":\"SELF\",\"all\":true}":"{\"type\":\"clear_status\",\"target\":\"SELF\",\"statuses\":\"ALL\"}";
            var attack=attack("{\"GRASS\":1}","[{\"trigger\":\"AFTER_ATTACK\",\"effects\":["+before+","+branch(1,"GRASS","{\"type\":\"draw\",\"target\":\"SELF\",\"amount\":2}")+"]}]");
            var m=fixture(attack,EnergyProfile.fixed(EnergyType.GRASS,2),0);attach(m,"special",1);int hand=m.privateView(A).hand().size();hit(m,20);
            check(m.privateView(A).hand().size()==hand+(discard?0:2),"after-damage condition used stale Energy");
        }
        String choice="{\"type\":\"choice\",\"effects\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"draw\",\"amount\":2}]}";
        var a=attack("{\"GRASS\":1}","[{\"trigger\":\"BEFORE_ATTACK\",\"effects\":["+choice+"]},{\"trigger\":\"ON_ATTACK\",\"effects\":["+branch(1,"GRASS",damage())+"]}]");
        var m=fixture(a,EnergyProfile.fixed(EnergyType.GRASS,2),0);attach(m,"special",1);m.attack(A,"hit");var p=m.effectChoicePrompt(A);check(p!=null,"before damage did not pause");m.chooseEffectOption(A,p.resolutionId(),p.step(),0);check(m.activePokemonView(B).damage()==50,"cost context lost across pause");
    }
    static void copyCost(){
        var selected=bonus("{\"FIRE\":4}",1,"GRASS");
        for(int n:List.of(1,2)){
            var m=CopyAttackTest.fixture(selected);for(int i=0;i<n;i++)m.attachEnergyByEffect(CopyAttackTest.A,CopyAttackTest.A,EffectSourceZone.HAND,DynamicPassiveModifierTest.hand(m,CopyAttackTest.A,"energy").instanceId(),CopyAttackTest.A,m.activePokemonView(CopyAttackTest.A).pokemonId());
            CopyAttackTest.copy(m,CopyAttackTest.choice(m),List.of(),List.of());
            check(m.activePokemonView(CopyAttackTest.B).damage()==(n==1?40:100),"copy used selected cost or reused declared payment");
        }
    }
    static void schemaAndEntrypoints(){
        for(String bad:List.of(condition(0,null),condition(61,null),condition(1,"BOGUS"),condition(1,null).replace("SELF","OPPONENT_ACTIVE"),condition(1,null).replace("\"value\":1","\"value\":1,\"count_mode\":\"CARDS\""))){
            reject(()->attack("{}","[{\"trigger\":\"ON_ATTACK\",\"conditions\":["+bad+"],\"effects\":["+damage()+"]}]"));
        }
        var program=attack("{}","[{\"trigger\":\"AFTER_ATTACK\",\"effects\":["+branch(1,null,"{\"type\":\"draw\",\"amount\":1}")+"]}]").effectProgram();
        for(EffectTrigger trigger:List.of(EffectTrigger.ONCE_PER_TURN,EffectTrigger.ON_CARD_PLAYED)){
            var p=new EffectProgram(List.of(new TriggeredEffects(trigger,List.of(),program.triggers().getFirst().effects())));
            reject(()->ResumableProgramRules.validate(p,trigger==EffectTrigger.ONCE_PER_TURN,trigger));
        }
        for(boolean negated:List.of(false,true)){
            String c=condition(1,null);if(negated)c="{\"type\":\"NOT\",\"conditions\":["+c+"]}";
            var a=attack("{}","[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"shuffle_into_deck\",\"target\":\"SELF\"},{\"type\":\"conditional\",\"conditions\":["+c+"],\"effects\":[{\"type\":\"draw\",\"amount\":1}]}]}]");
            var m=fixture(a,EnergyProfile.fixed(EnergyType.GRASS,2),0);attach(m,"special",1);
            int hand=m.privateView(A).hand().size();UUID replacement=m.benchPokemonView(A).getFirst().pokemonId();m.attack(A,"hit");
            m.chooseReplacement(A,replacement);check(m.privateView(A).hand().size()==hand,"departed source became a known false fact under NOT");
        }
    }
}
