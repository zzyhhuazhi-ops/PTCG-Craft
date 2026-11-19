import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Removing a cost overrides additions, but never removes restrictions on the retreat action. */
public final class FreeRetreatRuleTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static ServerMatch game(){var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"free_retreat\"}"),DynamicPassiveModifierTest.plainStadium());m.playTrainer(A,DynamicPassiveModifierTest.hand(m,A,"tool").instanceId(),active(m,A));return m;}
    public static void main(String[] args){priority();actionLimits();malformed();System.out.println("FreeRetreatRuleTest passed: cost removal dominates increases, stays live, offers empty payment and preserves sleep/paralysis/lock/once-per-turn constraints");}
    static void priority(){
        var m=game();UUID host=active(m,A);m.modifyRetreatCostByEffect(A,A,host,10);
        check(m.activeRetreatCost(A)==0&&m.suggestedRetreatPayment(A).orElseThrow().isEmpty(),"Free retreat implemented as a finite reduction");
        m.disableToolEffectsByEffect(A,A,host);check(m.activeRetreatCost(A)==13,"Suppressed source left free retreat active or lost the temporary increase");
        var x=game();var tool=x.activePokemonView(A).attachedTools().getFirst();x.discardToolByEffect(A,A,active(x,A),tool.instanceId());check(x.activeRetreatCost(A)==3,"Detached Tool retained free retreat");
        var y=game();UUID old=active(y,A);y.retreat(A,bench(y,A),List.of());check(!active(y,A).equals(old)&&y.activeRetreatCost(A)==3&&y.retreated(),"Zero payment did not perform a normal retreat");
        GeneratedRuleBatch8Test.reject(()->y.retreat(A,old,List.of()));
    }
    static void actionLimits(){for(String mode:List.of("ASLEEP","PARALYZED","LOCK","POISONED","CONFUSED")){
        var m=game();UUID old=active(m,A);if(mode.equals("LOCK"))m.preventRetreatByEffect(A,A,old);else m.applySpecialConditionByEffect(A,A,SpecialCondition.valueOf(mode));
        check(m.activeRetreatCost(A)==0,"Restriction changed cost rather than action availability");
        if(Set.of("ASLEEP","PARALYZED","LOCK").contains(mode)){GeneratedRuleBatch8Test.reject(()->m.retreat(A,bench(m,A),List.of()));check(active(m,A).equals(old),"Rejected retreat mutated Active");}
        else{m.retreat(A,bench(m,A),List.of());check(!active(m,A).equals(old),"Free retreat blocked legal status");}
    }}
    static void malformed(){for(String json:List.of("{\"type\":\"free_retreat\",\"amount\":0}","{\"type\":\"free_retreat\",\"target\":\"SELECT_OWN_POKEMON\"}","{\"type\":\"free_retreat\",\"effects\":[{\"type\":\"draw\",\"amount\":1}]}"))GeneratedRuleBatch8Test.reject(()->EffectRegistry.standard().parse(JsonParser.parseString(json).getAsJsonObject()));}
}
