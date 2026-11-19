import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

public final class NonPokemonBatch22Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){town();cemetery();scarf();crown();System.out.println("NonPokemonBatch22Test passed: both players' hand evolution, typed hand-source attachment with ownership, own-hand healing only, opponent-turn Basic-only healing");}
    static void town(){
        var m=NonPokemonBatch2Test.game("sm3_en_121",EnergyType.GRASS,1);m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);
        for(UUID p:List.of(A,B)){if(p.equals(B))m.endTurn(A);m.evolveByEffect(p,m.activePokemonView(p).pokemonId(),DynamicPassiveModifierTest.hand(m,p,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);NonPokemonBatch2Test.settle(m);check(m.activePokemonView(p).damage()==30&&m.benchPokemonView(p).getFirst().damage()==0,"Po Town affected wrong Pokemon or owner");}
    }
    static void cemetery(){
        for(EnergyType type:List.of(EnergyType.GRASS,EnergyType.PSYCHIC)){
            var m=NonPokemonBatch2Test.game("swsh6_en_147",type,1);m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);var host=m.benchPokemonView(A).getFirst().pokemonId();
            m.attachEnergy(A,DynamicPassiveModifierTest.hand(m,A,"energy").instanceId(),host);NonPokemonBatch2Test.settle(m);int expected=type==EnergyType.PSYCHIC?0:20;check(m.benchPokemonView(A).getFirst().damage()==expected,"Cemetery ignored target Psychic type");
            m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,DynamicPassiveModifierTest.hand(m,B,"energy").instanceId(),A,host);NonPokemonBatch2Test.settle(m);check(m.benchPokemonView(A).getFirst().damage()==expected,"Cemetery treated opponent hand as target owner's hand");
            var energy=DynamicPassiveModifierTest.hand(m,A,"energy");m.discardHandCardsByEffect(A,A,List.of(energy.instanceId()));m.attachEnergyByEffect(A,A,EffectSourceZone.DISCARD,energy.instanceId(),A,host);NonPokemonBatch2Test.settle(m);check(m.benchPokemonView(A).getFirst().damage()==expected,"Cemetery triggered from discard");
        }
    }
    static void scarf(){
        var m=NonPokemonBatch2Test.game("xy6_en_084",EnergyType.GRASS,1);var host=m.activePokemonView(A).pokemonId();m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),host);m.placeDamageCountersByEffect(A,A,host,6);
        m.attachEnergy(A,DynamicPassiveModifierTest.hand(m,A,"energy").instanceId(),host);NonPokemonBatch2Test.settle(m);check(m.activePokemonView(A).damage()==40,"Scarf did not heal after manual hand attachment");
        m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,DynamicPassiveModifierTest.hand(m,A,"energy").instanceId(),A,host);NonPokemonBatch2Test.settle(m);check(m.activePokemonView(A).damage()==20,"Scarf ignored effect attachment from own hand");
        m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,DynamicPassiveModifierTest.hand(m,B,"energy").instanceId(),A,host);NonPokemonBatch2Test.settle(m);check(m.activePokemonView(A).damage()==20,"Scarf healed from opponent hand");
        var energy=DynamicPassiveModifierTest.hand(m,A,"energy");m.discardHandCardsByEffect(A,A,List.of(energy.instanceId()));m.attachEnergyByEffect(A,A,EffectSourceZone.DISCARD,energy.instanceId(),A,host);NonPokemonBatch2Test.settle(m);check(m.activePokemonView(A).damage()==20,"Scarf healed from discard");
    }
    static void crown(){
        var m=NonPokemonBatch2Test.game("xy9-5r_en_026",EnergyType.GRASS,1);var host=m.activePokemonView(A).pokemonId();m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),host);m.placeDamageCountersByEffect(A,A,host,6);m.endTurn(A);NonPokemonBatch2Test.settle(m);check(m.activePokemonView(A).damage()==60,"Crown healed at own turn end");m.endTurn(B);NonPokemonBatch2Test.settle(m);check(m.activePokemonView(A).damage()==40,"Crown failed at opponent turn end");
        m.evolveByEffect(A,host,DynamicPassiveModifierTest.hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);m.endTurn(A);m.endTurn(B);NonPokemonBatch2Test.settle(m);check(m.activePokemonView(A).damage()==40,"Crown healed an Evolution");
    }
}
