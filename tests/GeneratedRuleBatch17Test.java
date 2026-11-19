import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.BundledCombatData;
import java.util.*;

/** Real generated free-retreat abilities/tools/Stadiums, with live recipient and source boundaries. */
public final class GeneratedRuleBatch17Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static ServerMatch ability(String id,EnergyType energy){return GeneratedRuleBatch8Test.fixture(List.of(GeneratedRuleBatch8Test.ability(id)),energy,EnergyType.COLORLESS);}
    static void attach(ServerMatch m,UUID owner,UUID host,String id){GeneratedRuleBatch13Test.attach(m,owner,host,id);}
    static void suppress(ServerMatch m,UUID owner){m.disableAbilityEffectsByEffect(A,owner,active(m,owner));m.disableAbilityEffectsByEffect(A,owner,bench(m,owner));}
    public static void main(String[] args){basicAura();energyPresence();typedEnergy();twoUnits();stadium();damage();tools();stadiumScopes();opponentConditions();modernEx();System.out.println("GeneratedRuleBatch17Test passed: real Basic/whole-field auras, typed/physical Energy, unit threshold, Stadium and HP conditions, Float Stone/Rescue Board, bilateral Stadium scopes and live opponent eligibility");}
    static void basicAura(){
        var m=ability("sm4_en_090",EnergyType.COLORLESS);suppress(m,B);check(m.activeRetreatCost(A)==0&&m.activeRetreatCost(B)==1,"Gyro Unit owner boundary");
        m.evolveByEffect(A,active(m,A),GeneratedRuleBatch13Test.hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);check(m.activeRetreatCost(A)==1,"Gyro Unit applied to evolved recipient");
        var n=ability("sv3-5_en_149",EnergyType.COLORLESS);n.evolveByEffect(A,active(n,A),GeneratedRuleBatch13Test.hand(n,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);check(n.activeRetreatCost(A)==0,"Jet Cruise excluded Evolution recipient");suppress(n,A);check(n.activeRetreatCost(A)==1,"Suppressed aura remained");
    }
    static void energyPresence(){
        for(String id:List.of("me2_en_011","swsh4_en_057")){
            boolean absent=id.startsWith("me2");var m=ability(id,EnergyType.LIGHTNING);check(m.activeRetreatCost(A)==(absent?0:1),"Initial physical Energy condition");attach(m,A,active(m,A),"special");check(m.activeRetreatCost(A)==(absent?1:0),"Physical Special Energy presence ignored");
        }
    }
    static void typedEnergy(){
        for(String id:List.of("sm6_en_021","xy9_en_032")){
            var m=ability(id,EnergyType.WATER);check(m.activeRetreatCost(A)==1,"Typed free retreat without Energy");attach(m,A,active(m,A),"wrong");check(m.activeRetreatCost(A)==1,"Wrong Energy type qualified");attach(m,A,active(m,A),"special");check(m.activeRetreatCost(A)==0,"Typed Special Energy supply did not qualify");
            m.modifyRetreatCostByEffect(A,A,active(m,A),10);check(m.activeRetreatCost(A)==0,"Typed free retreat did not override cost increase");
            m.disableAbilityEffectsByEffect(A,A,active(m,A));check(m.activeRetreatCost(A)==(id.startsWith("xy")?0:11),"Self versus Bench-source aura scope");suppress(m,A);check(m.activeRetreatCost(A)==11,"Typed aura did not stop after suppression");
        }
    }
    static void twoUnits(){var m=ability("sm11_en_051",EnergyType.WATER);check(m.activeRetreatCost(A)==0,"Zero Energy within two-unit threshold");attach(m,A,active(m,A),"special");check(m.activeRetreatCost(A)==0,"Exactly two units excluded");attach(m,A,active(m,A),"energy");check(m.activeRetreatCost(A)==1,"Threshold counted two cards rather than three supplied units");}
    static void stadium(){
        var aura=GeneratedRuleBatch8Test.ability("sm4_en_031");var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.fixed(EnergyType.COLORLESS,2),DynamicPassiveModifierTest.attack(40,false,false),List.of(aura));
        check(m.activeRetreatCost(A)==3,"Absent Stadium qualified");m.playTrainer(A,DynamicPassiveModifierTest.hand(m,A,"stadium").instanceId(),null);check(m.activeRetreatCost(A)==0&&m.activeRetreatCost(B)==0,"Any Stadium did not qualify both owners");m.discardStadiumByEffect(A);check(m.activeRetreatCost(A)==3,"Removed Stadium retained free retreat");
    }
    static void damage(){var m=ability("swsh10_en_074",EnergyType.COLORLESS);check(m.activeRetreatCost(A)==1,"Undamaged Sudowoodo qualified");m.placeDamageCountersByEffect(A,A,active(m,A),1);check(m.activeRetreatCost(A)==0,"Damage condition absent");m.healDamageByEffect(A,A,active(m,A),10);check(m.activeRetreatCost(A)==1,"Healed Pokemon retained free retreat");}
    static void tools(){
        for(String id:List.of("xy8_en_137","sv5_en_159","me1_en_166")){
            var m=DynamicPassiveModifierTest.fixture(GeneratedRuleBatch8Test.trainer(id),DynamicPassiveModifierTest.plainStadium());m.playTrainer(A,DynamicPassiveModifierTest.hand(m,A,"tool").instanceId(),active(m,A));int expected=id.equals("xy8_en_137")?0:id.equals("sv5_en_159")?2:1;check(m.activeRetreatCost(A)==expected,"Tool retreat modifier "+id);
            if(id.equals("sv5_en_159")){m.modifyRetreatCostByEffect(A,A,active(m,A),10);m.placeDamageCountersByEffect(A,A,active(m,A),27);check(m.activeRetreatCost(A)==0,"Rescue Board at 30 HP did not override increases");m.healDamageByEffect(A,A,active(m,A),10);check(m.activeRetreatCost(A)==12,"Rescue Board above 30 HP retained override");}
        }
    }
    static void stadiumScopes(){
        for(String id:List.of("sv1_en_167","sm3_en_118")){
            var stadium=BundledCombatData.load().trainer(id).orElseThrow();
            var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),stadium);m.playTrainer(A,DynamicPassiveModifierTest.hand(m,A,"stadium").instanceId(),null);int expected=id.startsWith("sv")?2:4;check(m.activeRetreatCost(A)==expected&&m.activeRetreatCost(B)==expected,"Bilateral Basic Stadium scope");m.evolveByEffect(A,active(m,A),DynamicPassiveModifierTest.hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);check(m.activeRetreatCost(A)==3,"Basic Stadium continued after Evolution");
        }
        var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),GeneratedRuleBatch8Test.trainer("xy1_en_117"),EnergyProfile.fixed(EnergyType.FAIRY,2),DynamicPassiveModifierTest.attack(40,false,false));m.playTrainer(A,DynamicPassiveModifierTest.hand(m,A,"stadium").instanceId(),null);check(m.activeRetreatCost(A)==3,"Fairy Garden without Energy");var e=DynamicPassiveModifierTest.hand(m,A,"special");m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,e.instanceId(),B,active(m,B));check(m.activeRetreatCost(A)==3&&m.activeRetreatCost(B)==0,"Fairy Garden failed opponent typed Special Energy");
        for(EnergyType type:List.of(EnergyType.DARKNESS,EnergyType.PSYCHIC)){
            var x=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),GeneratedRuleBatch8Test.trainer("sm11_en_193"),EnergyProfile.fixed(EnergyType.COLORLESS,2),DynamicPassiveModifierTest.attack(40,false,false),List.of(),0,null,Map.of("basic",type,"evolved",type));
            x.playTrainer(A,DynamicPassiveModifierTest.hand(x,A,"stadium").instanceId(),null);check(x.activeRetreatCost(A)==(type==EnergyType.DARKNESS?0:3)&&x.activeRetreatCost(B)==(type==EnergyType.DARKNESS?0:3),"Dark City exact type and both owners");x.evolveByEffect(A,active(x,A),DynamicPassiveModifierTest.hand(x,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);check(x.activeRetreatCost(A)==3,"Dark City allowed evolved recipient");
        }
    }
    static void opponentConditions(){for(String id:List.of("sm9_en_088","swsh9_en_085")){
        var m=ability(id,EnergyType.COLORLESS);suppress(m,B);m.disableAbilityEffectsByEffect(A,A,bench(m,A));boolean poison=id.startsWith("swsh");
        check(m.activeRetreatCost(B)==(poison?1:2)&&m.activeRetreatCost(A)==1,"Opponent increase source/condition");
        if(poison){m.applySpecialConditionByEffect(A,B,SpecialCondition.POISONED);check(m.activeRetreatCost(B)==2,"Poison did not update cost");}
        m.endTurn(A);m.evolveByEffect(B,active(m,B),GeneratedRuleBatch13Test.hand(m,B,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);check(m.activeRetreatCost(B)==1,"Evolution failed to clear recipient qualification");
    }}
    static void modernEx(){var m=ability("sm7-5_en_044",EnergyType.COLORLESS);var ex=GeneratedRuleBatch13Test.hand(m,A,"rulebox");m.playBasicPokemonToBench(A,ex.instanceId());m.switchActiveByEffect(A,A,ex.instanceId());check(m.activeRetreatCost(A)==0,"Legacy GX/EX exclusion incorrectly includes modern ex");}
}
