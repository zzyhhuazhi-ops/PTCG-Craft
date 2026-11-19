import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch9Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){altar();aether();multiSwitch();milk();greatCatcher();typedHealing();sunne();System.out.println("NonPokemonBatch9Test passed: energy-dependent retreat, typed damage reduction, bench energy transfer, coin healing, GX/EX-only gust, attached-energy healing filter, live Stadium weakness removal");}
    static void sunne(){
        for(var type:List.of(EnergyType.FIRE,EnergyType.METAL,EnergyType.GRASS)){
            var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer("sm2_en_118").orElseThrow();c.defenderTypes=Set.of(type);c.weakness=true;
            var m=GeneratedRuleBatch28Test.fixture(c);GeneratedRuleBatch28Test.play(m);m.attack(A,"hit");
            check(m.activePokemonView(B).damage()==(type==EnergyType.GRASS?200:100),"Sunne removed wrong type weakness or failed to apply");
            m.discardStadiumByEffect(B);m.endTurn(B);int before=m.activePokemonView(B).damage();m.attack(A,"hit");
            check(m.activePokemonView(B).damage()-before==200,"Weakness removal persisted after Stadium left play");
        }
    }
    static void greatCatcher(){
        for(var family:List.of(PokemonFamily.GX,PokemonFamily.EX_LEGACY,PokemonFamily.EX,PokemonFamily.V)){
            var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer("sm12_en_192").orElseThrow();c.defenderFamilies=Set.of(family);
            var m=GeneratedRuleBatch28Test.fixture(c);var old=m.activePokemonView(B).pokemonId();var card=GeneratedRuleBatch28Test.hand(m,A,"trainer");
            boolean eligible=family==PokemonFamily.GX||family==PokemonFamily.EX_LEGACY;
            check(m.canPlayTrainer(A,card.instanceId(),null)==eligible,"Great Catcher confused GX/legacy EX with V/modern ex");
            if(eligible){m.playTrainer(A,card.instanceId(),null);NonPokemonBatch2Test.settle(m);check(!m.activePokemonView(B).pokemonId().equals(old),"Great Catcher failed after cost");}
        }
    }
    static void typedHealing(){
        for(String id:List.of("sm5_en_124","sm3_en_117"))for(boolean matching:List.of(false,true)){
            var energy=id.equals("sm5_en_124")?EnergyType.GRASS:EnergyType.WATER;
            var m=NonPokemonBatch2Test.game(id,energy,1);var host=m.activePokemonView(A).pokemonId();var bench=m.benchPokemonView(A).getFirst().pokemonId();
            m.attachEnergy(A,DynamicPassiveModifierTest.hand(m,A,matching?"special":"wrong").instanceId(),host);
            m.placeDamageCountersByEffect(A,A,host,12);m.placeDamageCountersByEffect(A,A,bench,12);
            var card=NonPokemonBatch2Test.trainer(m);UUID selected=id.equals("sm5_en_124")?host:null;
            check(m.canPlayTrainer(A,card.instanceId(),selected)==matching,"Typed healing ignored current energy provision");
            if(matching){m.playTrainer(A,card.instanceId(),selected);NonPokemonBatch2Test.settle(m);check(m.activePokemonView(A).damage()==(id.equals("sm5_en_124")?40:70)&&m.benchPokemonView(A).getFirst().damage()==120,"Typed healing reached unenergized host");}
        }
    }
    static void altar(){
        for(var energy:List.of(EnergyType.PSYCHIC,EnergyType.DARKNESS,EnergyType.GRASS)){
            var m=NonPokemonBatch2Test.game("sm2_en_117",energy,3);m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);
            for(var owner:List.of(A,B)){
                if(owner.equals(B))m.endTurn(A);
                check(m.activeRetreatCost(owner)==3,"Moone applied without energy");
                m.attachEnergy(owner,DynamicPassiveModifierTest.hand(m,owner,"energy").instanceId(),m.activePokemonView(owner).pokemonId());
                check(m.activeRetreatCost(owner)==(energy==EnergyType.GRASS?3:1),"Moone energy type/owner filtering failed");
            }
            m.discardStadiumByEffect(B);check(m.activeRetreatCost(A)==3&&m.activeRetreatCost(B)==3,"Moone modifier persisted");
        }
    }
    static void aether(){
        for(var type:List.of(EnergyType.GRASS,EnergyType.LIGHTNING,EnergyType.FIRE)){
            var m=GeneratedRuleBatch8Test.fixture(List.of(),type,type,NonPokemonBatch2Test.DATA.trainer("sm2_en_116").orElseThrow(),GeneratedRuleBatch8Test.hit(50,false));
            m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);m.attack(A,"hit");
            int expected=type==EnergyType.FIRE?50:20;check(m.activePokemonView(B).damage()==expected,"Aether type filter wrong");
            m.attack(B,"hit");check(m.activePokemonView(A).damage()==expected,"Aether missed owner field");
        }
    }
    static void multiSwitch(){
        var m=NonPokemonBatch2Test.game("sm2_en_129",EnergyType.GRASS,1);var bench=m.benchPokemonView(A).getFirst().pokemonId();var card=DynamicPassiveModifierTest.hand(m,A,"special");
        m.attachEnergy(A,card.instanceId(),bench);m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);NonPokemonBatch2Test.settle(m);
        check(m.activePokemonView(A).attachedEnergyIds().contains(card.instanceId())&&m.benchPokemonView(A).getFirst().attachedEnergyIds().isEmpty(),"Multi Switch failed to move a Special Energy from Bench to Active");
    }
    static void milk(){
        var outcomes=new HashSet<Long>();
        for(int seed=0;seed<40&&outcomes.size()<3;seed++){
            var m=GeneratedRuleBatch23Test.fixture(NonPokemonBatch2Test.DATA.trainer("sm8_en_185").orElseThrow(),seed,EnergyType.GRASS);var host=m.activePokemonView(A).pokemonId();
            m.placeDamageCountersByEffect(A,A,host,12);int before=m.randomTranscript().size();
            m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);NonPokemonBatch2Test.settle(m);
            long heads=m.randomTranscript().subList(before,m.randomTranscript().size()).stream().filter(x->x.operation().startsWith("effect-coin:")&&x.result()==0).count();outcomes.add(heads);
            check(m.activePokemonView(A).damage()==120-heads*30,"Moomoo Milk did not heal chosen host for actual heads");
        }
        check(outcomes.size()==3,"Milk fixture missed a coin outcome");
    }
}
