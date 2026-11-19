import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual new card programs with their printed Energy costs, plus preserved second attacks. */
public final class GeneratedRuleBatch58Test {
    static void check(boolean b,String s){AnyStageDeckPlacementTest.check(b,s);}
    static AttackDefinition attack(String id,String attack){return GeneratedRuleBatch57Test.attack(id,attack);}
    public static void main(String[] args){
        var cards=BundledRules.loadCardDefinitions();for(String id:List.of("me5_en_001","me5_en_008","me5_en_071"))check(cards.find(id).orElseThrow().effectSupport()==EffectSupport.SUPPORTED,"Missing completed card "+id);
        var fruit=attack("me5_en_001","fruity-aroma");check(fruit.baseDamage()==0&&fruit.energyCost().equals(Map.of(EnergyType.COLORLESS,1)),"Fruity Aroma printed cost changed");AnyStageDeckPlacementTest.fruit(fruit);AnyStageDeckPlacementTest.boundaries(fruit,false);
        var portal=attack("me5_en_008","trick-portal");check(portal.baseDamage()==0&&portal.energyCost().equals(Map.of(EnergyType.FIRE,1)),"Trick Portal printed cost changed");AnyStageDeckPlacementTest.portal(portal);AnyStageDeckPlacementTest.boundaries(portal,true);
        var delivery=attack("me5_en_071","challenging-delivery");check(delivery.baseDamage()==0&&delivery.energyCost().equals(Map.of(EnergyType.COLORLESS,2)),"Challenging Delivery printed cost changed");AnyStageDeckPlacementTest.coins(delivery);
        var glow=attack("me5_en_008","eerie-glow");check(glow.baseDamage()==200&&glow.energyCost().equals(Map.of(EnergyType.FIRE,1,EnergyType.COLORLESS,2))&&glow.conditions().size()==2,"Eerie Glow changed");
        var m=AnyStageDeckPlacementTest.game(glow);m.attack(AnyStageDeckPlacementTest.A,glow.id());check(m.lastAttack().finalDamage()==200&&m.activePokemonView(AnyStageDeckPlacementTest.B).conditions().contains(SpecialCondition.CONFUSED),"Actual second attack missing damage/Confusion");
        check(attack("me5_en_001","solar-beam").baseDamage()==60&&attack("me5_en_071","speed-wing").baseDamage()==100,"Printed second attacks changed");
        for(var row:List.of(List.of("me5_en_001",110,1,1),List.of("me5_en_008",350,2,3),List.of("me5_en_071",100,1,1))){var j=GeneratedRuleBatch8Test.entry((String)row.getFirst());check(j.get("hitPoints").getAsInt()==(int)row.get(1)&&j.get("retreatCost").getAsInt()==(int)row.get(2)&&j.get("prizeCards").getAsInt()==(int)row.get(3),"Printed HP/retreat/prizes changed");}
        System.out.println("GeneratedRuleBatch58Test passed: all three actual ME5 cards, printed costs and statistics, top-six multi-card revealed hand search, top-nine any-stage Bench placement, coin-dependent whole-deck placement, short/empty/full boundaries and preserved second attacks");
    }
}
