import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

public final class NonPokemonBatch21Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){arm();board();System.out.println("NonPokemonBatch21Test passed: Asleep/Paralyzed attack or retreat permissions remain distinct, status retained on attachment, disabled Tools restore restrictions, UI agrees with attack authority");}
    static ServerMatch game(String id){var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.GRASS,EnergyType.GRASS,NonPokemonBatch2Test.DATA.trainer(id).orElseThrow(),GeneratedRuleBatch8Test.hit(100,false));m.attachEnergy(A,DynamicPassiveModifierTest.hand(m,A,"energy").instanceId(),m.activePokemonView(A).pokemonId());return m;}
    static void arm(){
        for(var status:List.of(SpecialCondition.ASLEEP,SpecialCondition.PARALYZED))for(boolean disabled:List.of(false,true)){
            var m=game("swsh11_en_170");var host=m.activePokemonView(A).pokemonId();m.applySpecialConditionByEffect(A,A,status);GeneratedRuleBatch8Test.reject(()->m.attack(A,"hit"));
            check(MatchSession.multiplayer(m,A,B).view(A,A).options().stream().noneMatch(o->o.enabled()&&o.type()==ActionType.ATTACK),"Status-blocked attack still offered to AI");
            m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),host);
            check(m.activePokemonView(A).conditions().contains(status),"Arm cured status on attachment");if(disabled)m.disableToolEffectsByEffect(A,A,host);
            var view=MatchSession.multiplayer(m,A,B).view(A,A);check(view.attackBlockZh().isEmpty()!=disabled,"Arm attack message disagrees with Tool status");
            check(view.options().stream().anyMatch(o->o.enabled()&&o.type()==ActionType.ATTACK)!=disabled,"Windup Arm action permission stale or missing");
            if(disabled){GeneratedRuleBatch8Test.reject(()->m.attack(A,"hit"));continue;}
            GeneratedRuleBatch8Test.reject(()->m.retreat(A,m.benchPokemonView(A).getFirst().pokemonId(),List.of()));m.attack(A,"hit");check(m.activePokemonView(B).damage()==100,"Arm failed to permit attack");
        }
    }
    static void board(){
        for(var status:List.of(SpecialCondition.ASLEEP,SpecialCondition.PARALYZED))for(boolean disabled:List.of(false,true)){
            var m=game("sm5_en_122");var host=m.activePokemonView(A).pokemonId();m.applySpecialConditionByEffect(A,A,status);m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),host);
            check(m.activePokemonView(A).conditions().contains(status)&&m.activeRetreatCost(A)==0,"Board cured status or missed retreat reduction");if(disabled)m.disableToolEffectsByEffect(A,A,host);
            GeneratedRuleBatch8Test.reject(()->m.attack(A,"hit"));var replacement=m.benchPokemonView(A).getFirst().pokemonId();
            if(disabled){GeneratedRuleBatch8Test.reject(()->m.retreat(A,replacement,List.of()));continue;}
            m.retreat(A,replacement,List.of());check(m.activePokemonView(A).pokemonId().equals(replacement)&&m.benchPokemonView(A).stream().filter(p->p.pokemonId().equals(host)).findFirst().orElseThrow().conditions().isEmpty(),"Board did not perform a normal retreat and status removal");
        }
    }
}
