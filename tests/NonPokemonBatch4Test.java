import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch4Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    static ServerMatch game(String id){return NonPokemonBatch2Test.game(id,EnergyType.GRASS,1);}
    static CardInstance card(ServerMatch m){return NonPokemonBatch2Test.trainer(m);}
    public static void main(String[] args){temple();center();colress();computer();cape();System.out.println("NonPokemonBatch4Test passed: Special Energy suppression/restoration, evolved HP on both sides, both benches draw, mandatory search cost, Tool HP");}
    static void temple(){
        var energy=DynamicEnergyProvisionTest.energy("{\"energyUnits\":[[\"GRASS\"],[\"GRASS\"]],\"effects\":[{\"type\":\"retreat_cost_modifier\",\"amount\":-2}]}");
        var m=DynamicEnergyProvisionTest.fixture(energy,DATA.trainer("swsh10_en_155").orElseThrow(),DynamicEnergyProvisionTest.attack("{\"GRASS\":1}",""));
        UUID host=m.activePokemonView(A).pokemonId();m.attachEnergy(A,DynamicPassiveModifierTest.hand(m,A,"special").instanceId(),host);
        check(m.activeRetreatCost(A)==1&&DynamicEnergyProvisionTest.count(m,A,host,2),"Initial special energy incorrect");
        m.playTrainer(A,DynamicPassiveModifierTest.hand(m,A,"stadium").instanceId(),null);
        check(m.activeRetreatCost(A)==3&&DynamicEnergyProvisionTest.count(m,A,host,1),"Sinnoh did not suppress other energy effects");
        GeneratedRuleBatch8Test.reject(()->m.attack(A,"hit"));m.discardStadiumByEffect(A);
        check(m.activeRetreatCost(A)==1&&DynamicEnergyProvisionTest.count(m,A,host,2),"Special Energy did not recover after Sinnoh left play");
        m.attack(A,"hit");
    }
    static void center(){
        var m=game("xy3_en_102");m.playTrainer(A,card(m).instanceId(),null);
        for(UUID player:List.of(A,B)){
            if(player.equals(B))m.endTurn(A);
            UUID host=m.activePokemonView(player).pokemonId();check(m.activePokemonView(player).hitPoints()==500,"Training Center boosted Basic");
            m.evolveByEffect(player,host,DynamicPassiveModifierTest.hand(m,player,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
            check(m.activePokemonView(player).hitPoints()==530,"Training Center missed evolved Pokemon");
        }
        m.discardStadiumByEffect(B);check(m.activePokemonView(A).hitPoints()==500&&m.activePokemonView(B).hitPoints()==500,"Training Center HP persisted");
    }
    static void colress(){
        var m=game("bw8_en_118");m.playBasicPokemonToBench(A,DynamicPassiveModifierTest.hand(m,A,"basic").instanceId());
        int expected=m.benchPokemonView(A).size()+m.benchPokemonView(B).size();m.playTrainer(A,card(m).instanceId(),null);NonPokemonBatch2Test.settle(m);
        check(m.privateView(A).hand().size()==expected,"Colress did not draw for both benches");
    }
    static void computer(){
        for(int size:List.of(2,3)){
            var m=game("bw7_en_137");var c=card(m);NonPokemonBatch2Test.trim(m,A,size,c);
            check(m.canPlayTrainer(A,c.instanceId(),null)==(size==3),"Computer Search ignored two-card cost");
            if(size==3){m.playTrainer(A,c.instanceId(),null);NonPokemonBatch2Test.settle(m);check(m.privateView(A).hand().size()==1,"Computer Search cost or retrieval wrong");}
        }
    }
    static void cape(){
        var m=game("bw6_en_114");m.playTrainer(A,card(m).instanceId(),m.benchPokemonView(A).getFirst().pokemonId());
        check(m.benchPokemonView(A).getFirst().hitPoints()==520&&m.activePokemonView(A).hitPoints()==500,"Giant Cape host HP incorrect");
    }
}
