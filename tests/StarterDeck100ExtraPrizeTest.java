import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

public final class StarterDeck100ExtraPrizeTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static String id(int n){return StarterDeck100PokemonTest.id(n);}
    static void check(boolean ok,String why){StarterDeck100PokemonTest.check(ok,why);}
    public static void main(String[] args){for(int n:List.of(349,350)){for(int opp:List.of(1,139,16))fangs(n,opp);poison(n);later(n);recoil(n);}System.out.println("StarterDeck100ExtraPrizeTest passed: both printings, Basic single/multi-Prize KO, Evolution exclusion, poison/later KO exclusion, attack-local reward and recoil");}
    static void fangs(int number,int opponent){var m=StarterDeck100PokemonTest.game(number,opponent);if(opponent==16){m.endTurn(A);StarterDeck100GuardTest.prepareOpponent(m,opponent,0);m.endTurn(B);}var target=m.activePokemonView(B);m.placeDamageCountersByEffect(A,B,target.pokemonId(),(target.hitPoints()-40)/10);StarterDeck100PokemonTest.attack(m,0);StarterDeck100CombatTest.settleKnockouts(m);int expected=opponent==1?3:opponent==16?1:2;check(m.privateView(A).prizes().size()==6-expected,"Double Dip Fangs wrong prize count for "+opponent);}
    static void poison(int number){var m=StarterDeck100PokemonTest.game(number);var target=m.activePokemonView(B);m.placeDamageCountersByEffect(A,B,target.pokemonId(),17);m.applySpecialConditionByEffect(A,B,SpecialCondition.POISONED);StarterDeck100PokemonTest.attack(m,0);StarterDeck100CombatTest.settleKnockouts(m);check(m.privateView(A).prizes().size()==4,"poison KO gained extra prize");}
    static void later(int number){var m=StarterDeck100PokemonTest.game(number);StarterDeck100PokemonTest.attack(m,0);m.knockOutPokemonByEffect(B,B,m.activePokemonView(B).pokemonId());StarterDeck100CombatTest.settleKnockouts(m);check(m.privateView(A).prizes().size()==4,"bonus persisted to a later rule KO");}
    static void recoil(int number){var m=StarterDeck100PokemonTest.game(number);StarterDeck100PokemonTest.attack(m,1);check(m.lastAttack().finalDamage()==200&&m.activePokemonView(A).damage()==30,"Wild Tackle self damage");}
}
