import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Assert the effect actually happens, rather than merely avoiding exceptions. */
public final class AiOperationRegressionTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] args) {
        for(var level:AiDifficulty.values()) {
            for(boolean bench:List.of(false,true)) {
                var jet=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),DATA.energyProfile("sv2_en_190").orElseThrow(),GeneratedRuleBatch8Test.hit(10,false));
                var host=bench?jet.benchPokemonView(A).getFirst().pokemonId():jet.activePokemonView(A).pokemonId();
                jet.attachEnergy(A,DynamicPassiveModifierTest.hand(jet,A,"special").instanceId(),host);
                AiCardOperationTest.settle(MatchSession.multiplayer(jet,A,B),level);
                check(jet.activePokemonView(A).pokemonId().equals(host),"Jet Energy switched the wrong host: "+bench+" "+level);
            }
            AiCardOperationTest.distinct(level);
            check(!AiCardOperationTest.rows.getLast().get("status").equals("FAILED"),"Distinct-energy selection failed: "+level);
            var attack=DATA.pokemon("sv2_en_099").orElseThrow().attacks().getFirst();
            var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.GRASS,EnergyType.GRASS,DynamicPassiveModifierTest.plainTool(),attack);
            var evolution=DynamicPassiveModifierTest.hand(m,A,"evolved");
            m.discardHandCardsByEffect(A,A,List.of(evolution.instanceId()));
            var energy=DynamicPassiveModifierTest.hand(m,A,"energy");m.attachEnergy(A,energy.instanceId(),m.activePokemonView(A).pokemonId());
            m.attack(A,attack.id());AiCardOperationTest.settle(MatchSession.multiplayer(m,A,B),level);
            check(m.benchPokemonView(A).stream().anyMatch(p->p.topCardId().equals("evolved")),"Revival Blessing failed to revive an evolution: "+level);
            check(!m.privateView(A).discard().contains(evolution)&&m.currentPlayer().equals(B),"Revival did not move exactly once or finish the turn");

            var zero=DATA.pokemon("zsv10-5_en_028").orElseThrow().attacks().stream().filter(a->a.id().equals("blizzard-burst")).findFirst().orElseThrow();
            var z=AiCardOperationTest.attackGame(zero,List.of());z.attack(A,zero.id());AiCardOperationTest.settle(MatchSession.multiplayer(z,A,B),level);
            check(z.benchPokemonView(B).stream().allMatch(p->p.damage()==0),"Zero-prize multiplier dealt Bench damage");

            var filtered=DATA.pokemon("sv10_en_078").orElseThrow().attacks().stream().filter(a->a.id().equals("thunder-raid")).findFirst().orElseThrow();
            var f=AiCardOperationTest.attackGame(filtered,List.of());var s=MatchSession.multiplayer(f,A,B);var snap=s.view(A,A);
            var option=snap.options().stream().filter(o->o.enabled()&&o.type()==ActionType.ATTACK).findFirst().orElseThrow();
            check(f.benchPokemonView(B).stream().noneMatch(p->option.cardKeys().contains("field:"+p.pokemonId())),"Session offered an ineligible Bench target");
            s.submit(A,A,snap.revision(),option.index());AiCardOperationTest.settle(s,level);
            check(f.currentPlayer().equals(B),"No eligible target should still complete the printed attack");
        }
        System.out.println("AiOperationRegressionTest passed: actual evolution revival, zero Bench damage and filtered targets on all four difficulties");
    }
}
