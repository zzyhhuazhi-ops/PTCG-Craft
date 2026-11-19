import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** B-09 damage stages, using synthetic cards rather than enabling unaudited prints. */
public final class AttackSelfDamageCalculationTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    static String modifier(int amount,String direction,String extra){return "{\"type\":\"attack_damage_modifier\",\"amount\":"+amount+",\"direction\":\""+direction+"\""+extra+"}";}
    static String self(String opcode,int amount){return "{\"type\":\""+opcode+"\",\"target\":\"SELF\",\"amount\":"+amount+"}";}
    static String trigger(String phase,String effects){return "{\"trigger\":\""+phase+"\",\"effects\":["+effects+"]}";}
    static AttackDefinition attack(String mode,int amount,EnergyType type,String extra){
        return BundledCombatData.attack(JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"self test\",\"energyCost\":{},\"baseDamage\":40,\"damageType\":\""+type+"\",\"effectSupport\":\"SUPPORTED\""
            +(mode.equals("native")?",\"selfDamage\":"+amount:",\"triggers\":["+trigger("AFTER_ATTACK",self(mode,amount))+"]")+extra+"}").getAsJsonObject(),EffectRegistry.standard());
    }
    static AttackDefinition program(String triggers,String extra){return BundledCombatData.attack(JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"staged self test\",\"energyCost\":{},\"baseDamage\":40,\"damageType\":\"COLORLESS\",\"effectSupport\":\"SUPPORTED\",\"triggers\":["+triggers+"]"+extra+"}").getAsJsonObject(),EffectRegistry.standard());}
    static ServerMatch game(AttackDefinition attack,String modifiers,int resistance,List<AbilityDefinition> abilities){
        var tool=modifiers.isEmpty()?DynamicPassiveModifierTest.plainTool():DynamicPassiveModifierTest.trainer("TOOL",modifiers);
        return DynamicPassiveModifierTest.fixture(tool,DynamicPassiveModifierTest.trainer("STADIUM","{\"type\":\"retreat_cost_modifier\",\"target\":\"ALL_OWN_POKEMON\",\"amount\":-1}"),EnergyProfile.fixed(EnergyType.COLORLESS,2),attack,abilities,resistance);
    }
    static ServerMatch game(AttackDefinition attack,String modifiers){return game(attack,modifiers,0,List.of());}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static void tool(ServerMatch m,UUID host){m.attachToolByEffect(A,A,EffectSourceZone.HAND,DynamicPassiveModifierTest.hand(m,A,"tool").instanceId(),A,host);}
    static void expect(ServerMatch m,int own,int other,String label){m.attack(A,"hit");check(m.activePokemonView(A).damage()==own&&m.lastAttack().selfDamage()==own,label+" self="+m.activePokemonView(A).damage()+" audited="+m.lastAttack().selfDamage());check(m.activePokemonView(B).damage()==other,label+" opposing="+m.activePokemonView(B).damage());}
    public static void main(String[] args){
        if(args.length>0&&args[0].equals("resistance")){resistanceStopsPerTarget();return;}
        sourceAndReceivingLayers();scope();weaknessResistance();snapshot();benchAttacker();copies();opponentNativeGuards();countersAndConfusion();resistanceStopsPerTarget();
        System.out.println("AttackSelfDamageCalculationTest passed: native/self_damage/recoil ordered signed layers, W/R and zero stops, opponent scopes, actual copied type, Bench source, frozen outgoing facts, native guards and untouched counters/Confusion");
    }
    static void sourceAndReceivingLayers(){
        for(String mode:List.of("native","self_damage","recoil")){
            var m=game(attack(mode,30,EnergyType.COLORLESS,""),modifier(-20,"DEALT",""));tool(m,active(m,A));expect(m,10,20,mode+" outgoing once");
            m=game(attack(mode,30,EnergyType.COLORLESS,""),modifier(-40,"DEALT","")+","+modifier(20,"DEALT",""));tool(m,active(m,A));expect(m,10,20,mode+" source same layer");
            m=game(attack(mode,30,EnergyType.COLORLESS,""),modifier(-40,"RECEIVED","")+","+modifier(30,"RECEIVED",""));tool(m,active(m,A));expect(m,20,40,mode+" receiver same layer");
            m=game(attack(mode,30,EnergyType.COLORLESS,""),modifier(-40,"DEALT","")+","+modifier(100,"RECEIVED",""));tool(m,active(m,A));expect(m,0,0,mode+" source stops before received");
        }
        var zero=game(attack("native",0,EnergyType.COLORLESS,""),modifier(100,"DEALT",""));tool(zero,active(zero,A));expect(zero,0,140,"No printed self damage remains zero");
    }
    static void scope(){
        String mods=modifier(100,"DEALT",",\"opponent_only\":true")+","+modifier(100,"DEALT",",\"opponent_active_only\":true")+","+modifier(-100,"RECEIVED",",\"opponent_only\":true");
        for(String mode:List.of("native","self_damage","recoil")){var m=game(attack(mode,30,EnergyType.COLORLESS,""),mods);tool(m,active(m,A));expect(m,30,240,"Opponent scopes "+mode);}
    }
    static void weaknessResistance(){
        for(String mode:List.of("native","self_damage","recoil")){
            var m=game(attack(mode,30,EnergyType.GRASS,""),modifier(-20,"DEALT",""));tool(m,active(m,A));expect(m,20,40,mode+" source before weakness");
            m=game(attack(mode,30,EnergyType.GRASS,""),modifier(-20,"DEALT","")+","+modifier(100,"RECEIVED",""),30,List.of());tool(m,active(m,A));expect(m,0,10,mode+" resistance negative stops before receiver");
            m=game(attack(mode,30,EnergyType.GRASS,",\"ignoresWeakness\":true"),"",30,List.of());expect(m,0,10,mode+" resistance exact zero stops");
            m=game(attack(mode,30,EnergyType.GRASS,",\"ignoresWeaknessAndResistance\":true"),"",30,List.of());expect(m,30,40,mode+" ignore WR");
            m=game(attack(mode,30,EnergyType.GRASS,",\"ignoresResistance\":true"),"",30,List.of());expect(m,60,80,mode+" ignore resistance only");
            m=game(attack(mode,30,EnergyType.COLORLESS,",\"ignoresActiveDefenderDamageEffects\":true"),modifier(-20,"RECEIVED",""));tool(m,active(m,A));expect(m,10,40,mode+" opponent defender bypass is not self bypass");
        }
    }
    static void snapshot(){
        String disable="{\"type\":\"disable_tools\",\"target\":\"SELF\"}";
        for(boolean before:List.of(false,true)){
            String triggers=before?trigger("BEFORE_ATTACK",disable)+","+trigger("AFTER_ATTACK",self("self_damage",30)):trigger("AFTER_ATTACK",disable+","+self("self_damage",30));
            var m=game(program(triggers,""),modifier(-20,"DEALT",""));tool(m,active(m,A));expect(m,before?30:10,before?40:20,"Snapshot timing "+before);
        }
    }
    static void benchAttacker(){
        for(String mode:List.of("native","self_damage","recoil")){
            var m=game(attack(mode,30,EnergyType.GRASS,",\"mayUseFromBench\":true"),modifier(-20,"DEALT",""),30,List.of());UUID host=m.benchPokemonView(A).getFirst().pokemonId();tool(m,host);
            var o=m.benchAttackOptions(A).stream().filter(v->v.pokemonId().equals(host)).findFirst().orElseThrow();m.attackFromBench(A,host,o.entry(),o.topCardId(),o.attack().id());
            check(m.activePokemonView(A).damage()==0&&m.benchPokemonView(A).getFirst().damage()==10&&m.lastAttack().selfDamage()==10,"Bench self must address actual source without W/R: "+mode);
        }
        var prevent=new AbilityDefinition("bench-shield","Bench shield",new AbilityEffect(AbilityEffect.Opcode.PASSIVE_PREVENT_ATTACK_DAMAGE_WHILE_BENCHED,0),EffectSupport.SUPPORTED,null,AbilityDefinition.Kind.PASSIVE);
        var m=game(attack("self_damage",30,EnergyType.COLORLESS,",\"mayUseFromBench\":true"),"",0,List.of(prevent));var o=m.benchAttackOptions(A).getFirst();m.attackFromBench(A,o.pokemonId(),o.entry(),o.topCardId(),o.attack().id());check(m.benchPokemonView(A).getFirst().damage()==0,"Both-player Bench shield must prevent self damage");
    }
    static void copies(){for(String mode:List.of("native","self_damage","recoil"))for(EnergyType actual:List.of(EnergyType.PSYCHIC,EnergyType.FIRE)){
        var borrowed=attack(mode,30,EnergyType.FIRE,"");var m=CopyAttackTest.fixture(CopyAttackTest.copier(),borrowed,actual,0);CopyAttackTest.pay(m);var choice=CopyAttackTest.choice(m);CopyAttackTest.copy(m,choice,List.of(),List.of());
        check(m.activePokemonView(CopyAttackTest.A).damage()==(actual==EnergyType.FIRE?60:30),"Self W/R used donor type instead of effective actual copied type "+mode+"/"+actual);
    }}
    static void opponentNativeGuards(){for(var opcode:List.of(AbilityEffect.Opcode.PASSIVE_PREVENT_OPPONENT_ATTACK_DAMAGE,AbilityEffect.Opcode.PASSIVE_REDUCE_OPPONENT_ATTACK_DAMAGE)){
        var ability=new AbilityDefinition("guard","Opponent guard",new AbilityEffect(opcode,opcode==AbilityEffect.Opcode.PASSIVE_REDUCE_OPPONENT_ATTACK_DAMAGE?20:0),EffectSupport.SUPPORTED,null,AbilityDefinition.Kind.PASSIVE);
        var m=game(attack("self_damage",30,EnergyType.COLORLESS,""),"",0,List.of(ability));expect(m,30,opcode==AbilityEffect.Opcode.PASSIVE_PREVENT_OPPONENT_ATTACK_DAMAGE?0:20,"Native opponent guard "+opcode);
    }}
    static void countersAndConfusion(){
        var m=game(program(trigger("AFTER_ATTACK",self("damage_counter",3)),""),modifier(-100,"DEALT","")+","+modifier(-100,"RECEIVED",""));tool(m,active(m,A));m.attack(A,"hit");check(m.activePokemonView(A).damage()==30,"Self counters were calculated as attack damage");
        boolean tails=false;for(int seed=0;seed<30&&!tails;seed++){
            var g=CopyAttackTest.fixture(CopyAttackTest.copier(),attack("native",30,EnergyType.FIRE,""),EnergyType.FIRE,seed);CopyAttackTest.pay(g);g.applySpecialConditionByEffect(CopyAttackTest.A,CopyAttackTest.A,SpecialCondition.CONFUSED);CopyAttackTest.copy(g,CopyAttackTest.choice(g),List.of(),List.of());
            if(g.lastAttack().finalDamage()==0){tails=true;check(g.activePokemonView(CopyAttackTest.A).damage()==30,"Confusion counters incorrectly doubled by own Fire weakness");}
        }check(tails,"No confusion tails fixture");
    }
    static void resistanceStopsPerTarget(){
        var stadium=DynamicPassiveModifierTest.trainer("STADIUM",modifier(20,"RECEIVED",",\"target\":\"ALL_OPPONENT_POKEMON\""));
        for(int base:List.of(10,20))for(boolean mixed:List.of(false,true)){
            var json=JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"resistance stage\",\"energyCost\":{},\"baseDamage\":"+base+",\"damageType\":\"GRASS\",\"effectSupport\":\"SUPPORTED\""+(mixed?",\"damageTarget\":\"ALL_OPPONENT_POKEMON\"":"")+",\"triggers\":["+trigger("AFTER_ATTACK","{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}")+ "]}").getAsJsonObject();
            var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),stadium,EnergyProfile.fixed(EnergyType.COLORLESS,2),BundledCombatData.attack(json,EffectRegistry.standard()),List.of(),30);m.playTrainer(A,DynamicPassiveModifierTest.hand(m,A,"stadium").instanceId(),null);m.attack(A,"hit");
            int expected=base==10?0:30;check(m.lastAttack().finalDamage()==expected&&m.activePokemonView(B).damage()==expected+10,"Resistance stopped damage must retain independent AFTER text "+base+"/"+mixed);
            if(mixed)check(m.benchPokemonView(B).getFirst().damage()==base+20,"Active zero stop leaked to Bench calculation");
        }
    }
}
