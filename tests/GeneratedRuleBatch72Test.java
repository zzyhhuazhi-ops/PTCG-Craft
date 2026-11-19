import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Three actual Dhelmise profiles/costs; discard fixtures carry the actual printed Hide 'n' Sneak program. */
public final class GeneratedRuleBatch72Test {
    static final UUID A=DiscardAbilityCountTest.A,B=DiscardAbilityCountTest.B;
    static void check(boolean b,String why){DiscardAbilityCountTest.check(b,why);}
    static final CardDatabase DB=BundledRules.loadCardDefinitions();static final CombatCatalog DATA=BundledCombatData.load();
    static ServerMatch game(String id){var p=DATA.pokemon(id).orElseThrow();var m=DiscardAbilityCountTest.game(DB.find(id).orElseThrow(),p,DATA.pokemon("me5_en_006").orElseThrow().abilities().getFirst());m.attachEnergy(A,DiscardAbilityCountTest.get(m,A,"energy").instanceId(),m.activePokemonView(A).pokemonId());return m;}
    public static void main(String[] args){for(String id:List.of("me5_en_039","me5_en_091","mebsp_en_084")){printed(id);for(int n:List.of(0,3,4,5))battle(id,n,false,false);battle(id,4,true,false);battle(id,4,true,true);}System.out.println("GeneratedRuleBatch72Test passed: three actual140HP Dhelmise, Psychic1 cost,30+140 threshold, actual printed Hide n Sneak, mixed-print discard, cross-zone source removes bonus, field-only source restores discard Ability count and post-damage KO");}
    static void printed(String id){var c=DB.find(id).orElseThrow();var p=DATA.pokemon(id).orElseThrow();check(c.effectSupport()==EffectSupport.SUPPORTED&&c.basicPokemon()&&c.regulationMark().equals("J")&&c.copyLimit()==4&&p.hitPoints()==140&&p.retreatCost()==3&&p.pokemonType()==EnergyType.PSYCHIC&&p.weaknessType()==EnergyType.DARKNESS&&p.weaknessMultiplier()==2&&p.resistanceType()==EnergyType.FIGHTING&&p.resistanceAmount()==30&&p.prizeCards()==1&&p.abilities().isEmpty()&&p.attacks().size()==1,"Actual metadata changed");var a=p.attacks().getFirst();check(a.id().equals("vengeful-anchor")&&a.baseDamage()==30&&a.energyCost().equals(Map.of(EnergyType.PSYCHIC,1)),"Printed damage/cost changed");AttackProgramRules.validate(a);}
    static void battle(String id,int n,boolean global,boolean field){var m=game(id);DiscardAbilityCountTest.discard(m,"hide-a",Math.min(2,n));DiscardAbilityCountTest.discard(m,"hide-b",Math.max(0,n-2));DiscardAbilityCountTest.discard(m,"decoy",1);if(global)DiscardAbilityCountTest.tool(m,DiscardAbilityCountTest.source(m));if(field)m.playTrainer(A,DiscardAbilityCountTest.get(m,A,"field").instanceId(),null);m.attack(A,"vengeful-anchor");int damage=n>=4&&(!global||field)?170:30;check(m.lastAttack().finalDamage()==damage,"Actual Dhelmise threshold incorrect");if(damage==170){check(m.phase()==MatchPhase.PRIZE_SELECTION,"Actual lethal damage did not reach prize checkpoint");m.takePrizeAt(A,0);m.chooseReplacement(B,m.benchPokemonView(B).getFirst().pokemonId());}check(m.currentPlayer().equals(B)&&m.phase()==MatchPhase.TURN,"Actual attack did not end once");}
}
