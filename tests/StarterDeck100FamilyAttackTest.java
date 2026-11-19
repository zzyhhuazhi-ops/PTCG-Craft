import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class StarterDeck100FamilyAttackTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static String id(int n){return StarterDeck100PokemonTest.id(n);}
    static void check(boolean ok,String why){StarterDeck100PokemonTest.check(ok,why);}
    public static void main(String[] args){for(boolean bench:List.of(false,true))espeon(bench);noV();mixedField();preservedFilter();System.out.println("StarterDeck100FamilyAttackTest passed: V family targets, Active-only weakness, empty target set, authority and UI filter");}
    static void espeon(boolean bench){var m=StarterDeck100PokemonTest.game(172,225);StarterDeck100PokemonTest.charge(m,0);UUID target=bench?m.benchPokemonView(B).getFirst().pokemonId():m.activePokemonView(B).pokemonId();m.attack(A,"zen-shot",target);check(StarterDeck100PokemonTest.view(m,B,target).damage()==(bench?60:120),"Zen Shot missing base60 or incorrect Active/Bench Weakness");}
    static void noV(){var m=StarterDeck100PokemonTest.game(172,7);StarterDeck100PokemonTest.charge(m,0);var before=m.clientView(A);TopDeckLookTest.rejects(()->m.attack(A,"zen-shot",m.activePokemonView(B).pokemonId()));check(before.equals(m.clientView(A)),"non-V declaration changed state");m.attack(A,"zen-shot");check(m.activePokemonView(B).damage()==0&&m.benchPokemonView(B).getFirst().damage()==0&&m.lastAttack().finalDamage()==0&&m.currentPlayer().equals(B),"no V field should allow a no-damage attack");}
    static void mixedField(){var m=StarterDeck100ZoneCountTest.game(172,id(7));m.endTurn(A);var nonV=StarterDeck100ZoneCountTest.hand(m,B,id(7));m.playBasicPokemonToBench(B,nonV.instanceId());m.endTurn(B);StarterDeck100PokemonTest.charge(m,0);var options=MatchSession.practice(m,A,A,B).view(A,A).options().stream().filter(o->o.labelZh().contains("意念枪弹")).toList();check(options.size()==2,"table offered non-V Zen Shot targets");var before=m.clientView(A);TopDeckLookTest.rejects(()->m.attack(A,"zen-shot",PokemonIdentityTestSupport.logical(m,B,nonV.instanceId())));check(before.equals(m.clientView(A)),"forged non-V target mutated match");m.attack(A,"zen-shot",m.activePokemonView(B).pokemonId());check(m.activePokemonView(B).damage()==60&&StarterDeck100PokemonTest.view(m,B,nonV.instanceId()).damage()==0,"family attack hit excluded Pokemon");}
    static void preservedFilter(){var attack=StarterDeck100CombatTest.COMBAT.pokemon(id(172)).orElseThrow().attacks().getFirst();check(attack.withEffectProgram(attack.effectProgram()).targetFamilies().equals(attack.targetFamilies()),"program replacement lost target family");check(attack.targetFamilies().matches(StarterDeck100CombatTest.CARDS.find(id(235)).orElseThrow()),"V filter omitted VMAX");}
}
