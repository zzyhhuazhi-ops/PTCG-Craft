import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.engine.effect.*;
import com.google.gson.JsonParser;
import java.util.*;

public final class NonPokemonBatch8Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){maxPotion();superPotion();catcher();nita();hala();belt();System.out.println("NonPokemonBatch8Test passed: healing and exact energy removal, Catcher errata coin branches, opponent deck-top return, GX draw branches, remaining HP damage threshold");}
    static void superPotion(){
        for(String id:List.of("sv9_en_158","xy12_en_087"))for(int count:List.of(0,1,2)){
            var m=NonPokemonBatch2Test.game(id,EnergyType.GRASS,1);var host=m.activePokemonView(A).pokemonId();
            for(int i=0;i<count;i++){
                m.attachEnergy(A,DynamicPassiveModifierTest.hand(m,A,"energy").instanceId(),host);
                if(i+1<count){m.endTurn(A);m.endTurn(B);}
            }
            m.placeDamageCountersByEffect(A,A,host,12);m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);NonPokemonBatch2Test.settle(m);
            check(m.activePokemonView(A).damage()==60,"Super Potion did not heal exactly 60");
            check(m.activePokemonView(A).attachedEnergyIds().size()==Math.max(0,count-1),"Super Potion discarded more than one energy");
        }
    }
    static void catcher(){
        var outcomes=new HashSet<Boolean>();
        for(int seed=0;seed<40&&outcomes.size()<2;seed++){
            var m=GeneratedRuleBatch23Test.fixture(NonPokemonBatch2Test.DATA.trainer("bw2_en_095").orElseThrow(),seed,EnergyType.GRASS);
            var old=m.activePokemonView(B).pokemonId();int before=m.randomTranscript().size();
            m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);NonPokemonBatch2Test.settle(m);
            var flips=m.randomTranscript().subList(before,m.randomTranscript().size()).stream().filter(x->x.operation().startsWith("effect-coin:")).toList();
            check(flips.size()==1,"Old Catcher did not flip exactly one coin");boolean heads=flips.getFirst().result()==0;outcomes.add(heads);
            check(!old.equals(m.activePokemonView(B).pokemonId())==heads,"Old Catcher ignored coin result");
        }
        check(outcomes.size()==2,"Catcher fixture missed a coin outcome");
    }
    static void maxPotion(){
        for(boolean attached:List.of(false,true)){
            var m=NonPokemonBatch2Test.game("bw2_en_094",EnergyType.GRASS,1);var host=m.activePokemonView(A).pokemonId();
            if(attached)m.attachEnergy(A,DynamicPassiveModifierTest.hand(m,A,"energy").instanceId(),host);
            m.placeDamageCountersByEffect(A,A,host,12);m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),host);NonPokemonBatch2Test.settle(m);
            check(m.activePokemonView(A).damage()==0&&m.activePokemonView(A).attachedEnergyIds().isEmpty(),"Max Potion requires energy or leaves damage/energy");
        }
    }
    static void nita(){
        var m=NonPokemonBatch2Test.game("sm9_en_151",EnergyType.GRASS,1);var host=m.activePokemonView(B).pokemonId();
        m.endTurn(A);var energy=DynamicPassiveModifierTest.hand(m,B,"energy");m.attachEnergy(B,energy.instanceId(),host);m.endTurn(B);
        m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);NonPokemonBatch2Test.settle(m);
        check(m.privateView(B).deck().getLast().equals(energy)&&m.activePokemonView(B).attachedEnergyIds().isEmpty(),"Nita did not return energy to its owner's deck top");
    }
    static void hala(){
        var attack=BundledCombatData.attack(JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"GX\",\"energyCost\":{},\"baseDamage\":10,\"effectSupport\":\"SUPPORTED\",\"usageLimit\":{\"group\":\"gx-attack\",\"period\":\"GAME\"}}").getAsJsonObject(),EffectRegistry.standard());
        for(boolean used:List.of(false,true)){
            var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.GRASS,EnergyType.GRASS,NonPokemonBatch2Test.DATA.trainer("sm2_en_126").orElseThrow(),attack);
            if(used){m.attack(A,"hit");m.endTurn(B);}
            m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);NonPokemonBatch2Test.settle(m);
            check(m.privateView(A).hand().size()==(used?7:4),"Hala ignored actual GX usage");
        }
    }
    static void belt(){
        for(int damage:List.of(0,460,470,480)){
            var m=NonPokemonBatch2Test.game("sm7_en_134",EnergyType.GRASS,1);var host=m.activePokemonView(A).pokemonId();
            m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),host);
            if(damage>0)m.placeDamageCountersByEffect(A,A,host,damage/10);
            m.attack(A,"hit");check(m.activePokemonView(B).damage()==(damage>=470?70:10),"Hustle Belt remaining HP threshold incorrect");
        }
    }
}
