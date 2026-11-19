import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Live costs are distinct from printed costs and from permission to perform the retreat action. */
public final class RetreatCostConditionTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static final String CONDITION="{\"type\":\"RETREAT_COST\",\"target\":\"OPPONENT_ACTIVE\",\"value\":0,\"comparison\":\"==\"}";
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID owner){return m.activePokemonView(owner).pokemonId();}
    static AttackDefinition attack(String condition){return BundledCombatData.attack(JsonParser.parseString(
        "{\"id\":\"hit\",\"nameZh\":\"Cost condition\",\"energyCost\":{},\"baseDamage\":20,\"damageType\":\"GRASS\",\"effectSupport\":\"SUPPORTED\","
        +"\"effects\":[{\"type\":\"conditional\",\"conditions\":["+condition+"],\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}]}]}").getAsJsonObject(),EffectRegistry.standard());}
    static ServerMatch game(TrainerProfile tool,TrainerProfile stadium,List<AbilityDefinition> abilities){
        return DynamicPassiveModifierTest.fixture(tool,stadium,EnergyProfile.fixed(EnergyType.COLORLESS,2),attack(CONDITION),abilities,30);
    }
    static ServerMatch game(){return game(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),List.of());}
    static TrainerProfile trainer(String category,String text){return DynamicPassiveModifierTest.trainer(category,text);}
    static void damage(ServerMatch m,boolean zero){check((m.activeRetreatCost(B)==0)==zero,"Incorrect pre-damage live cost: "+m.activeRetreatCost(B));m.attack(A,"hit");check(m.lastAttack().finalDamage()==(zero?70:10),"Cost bonus must precede weakness and resistance");}
    public static void main(String[] args){temporary();sources();restrictions();schema();System.out.println("RetreatCostConditionTest passed: current reductions/increases, Tool/Stadium/Ability lifecycle, action restrictions, damage layers and strict attack-only schema");}
    static void temporary(){
        damage(game(),false);
        var m=game();m.modifyRetreatCostByEffect(A,B,active(m,B),-3);damage(m,true);
        var increased=game();increased.modifyRetreatCostByEffect(A,B,active(increased,B),-3);increased.modifyRetreatCostByEffect(A,B,active(increased,B),3);damage(increased,false);
        var own=game();own.modifyRetreatCostByEffect(A,A,active(own,A),-3);damage(own,false);
    }
    static void sources(){
        for(String mode:List.of("LIVE","SUPPRESSED","REMOVED")){
            var m=game(trainer("TOOL","{\"type\":\"free_retreat\"}"),DynamicPassiveModifierTest.plainStadium(),List.of());
            m.endTurn(A);var t=DynamicPassiveModifierTest.hand(m,B,"tool");m.playTrainer(B,t.instanceId(),active(m,B));m.endTurn(B);
            if(mode.equals("SUPPRESSED"))m.disableToolEffectsByEffect(A,B,active(m,B));
            if(mode.equals("REMOVED"))m.discardToolByEffect(A,B,active(m,B),t.instanceId());
            damage(m,mode.equals("LIVE"));
        }
        for(boolean removed:List.of(false,true)){
            var m=game(DynamicPassiveModifierTest.plainTool(),trainer("STADIUM","{\"type\":\"retreat_cost_modifier\",\"target\":\"ALL_OPPONENT_POKEMON\",\"amount\":-3}"),List.of());
            m.playTrainer(A,DynamicPassiveModifierTest.hand(m,A,"stadium").instanceId(),null);if(removed)m.discardStadiumByEffect(A);damage(m,!removed);
        }
        var ability=BundledCombatData.ability(JsonParser.parseString("{\"id\":\"free\",\"nameZh\":\"Free\",\"kind\":\"PASSIVE\",\"effectSupport\":\"SUPPORTED\",\"effects\":[{\"type\":\"free_retreat\",\"target\":\"SELF\"}]}").getAsJsonObject(),EffectRegistry.standard());
        for(boolean disabled:List.of(false,true)){
            var m=game(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),List.of(ability));
            if(disabled)m.disableAbilityEffectsByEffect(A,B,active(m,B));else m.modifyRetreatCostByEffect(A,B,active(m,B),10);
            damage(m,!disabled);
        }
    }
    static void restrictions(){for(String mode:List.of("LOCK","ASLEEP","PARALYZED")){
        var m=game();m.modifyRetreatCostByEffect(A,B,active(m,B),-3);
        if(mode.equals("LOCK"))m.preventRetreatByEffect(A,B,active(m,B));else m.applySpecialConditionByEffect(A,B,SpecialCondition.valueOf(mode));
        damage(m,true);
    }}
    static void schema(){
        AttackProgramRules.validate(attack(CONDITION));
        for(String target:List.of("SELF","OWN_ACTIVE"))AttackProgramRules.validate(attack(CONDITION.replace("OPPONENT_ACTIVE",target)));
        for(String bad:List.of(CONDITION.replace("OPPONENT_ACTIVE","OPPONENT_BENCH"),CONDITION.replace("OPPONENT_ACTIVE","HAND"),
            CONDITION.replace("\"value\":0","\"value\":-1"),CONDITION.replace("\"value\":0","\"value\":21"),
            CONDITION.replace("\"value\":0","\"compare_target\":\"OWN_ACTIVE\""),CONDITION.replace("\"value\":0","\"value\":0,\"printed\":true")))
            reject(()->AttackProgramRules.validate(attack(bad)));
        reject(()->trainer("TOOL","{\"type\":\"free_retreat\",\"conditions\":["+CONDITION+"]}"));
        var after=new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.AFTER_ATTACK,
            List.of(new ConditionDefinition(ConditionType.RETREAT_COST,Map.of("target","OPPONENT_ACTIVE","value","0","comparison","=="),List.of())),
            List.of(new EffectDefinition("draw",EffectTarget.SELF,Map.of("amount","1"),List.of(),List.of())))));
        reject(()->ResumableProgramRules.validate(after,true,EffectTrigger.AFTER_ATTACK));
    }
}
