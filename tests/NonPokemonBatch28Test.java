import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch28Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static ServerMatch game(String id,EnergyType energy,int units,String stadium){return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),NonPokemonBatch2Test.DATA.trainer(stadium).orElseThrow(),EnergyProfile.fixed(energy,units),GeneratedRuleBatch8Test.hit(10,false),List.of(),0,NonPokemonBatch2Test.DATA.trainer(id).orElseThrow());}
    static CardInstance card(ServerMatch m,String id){return DynamicPassiveModifierTest.hand(m,A,id);}
    static void chooseHost(ServerMatch m,UUID host){var q=m.effectPokemonPrompt(A);check(q.targets().size()==1&&q.targets().getFirst().pokemonId().equals(host),"Healing offered wrong or insufficiently energized host");m.chooseEffectPokemon(A,q.resolutionId(),q.step(),host);}
    public static void main(String[] args){hyper();tin();fan();System.out.println("NonPokemonBatch28Test passed: live Energy units versus cards, Sinnoh suppression/restoration, actual-heal discard gate, typed Special Energy and exact opponent deck-bottom return");}
    static void hyper(){
        var m=game("swsh1_en_166",EnergyType.COLORLESS,2,"swsh10_en_155");var host=m.activePokemonView(A).pokemonId();m.placeDamageCountersByEffect(A,A,host,20);var energy=card(m,"special");var item=card(m,"item");
        check(!m.canPlayTrainer(A,item.instanceId(),null),"Hyper Potion accepted no Energy");m.attachEnergy(A,energy.instanceId(),host);check(m.canPlayTrainer(A,item.instanceId(),null),"One double-providing card was not two Energy");
        m.playTrainer(A,card(m,"stadium").instanceId(),null);check(!m.canPlayTrainer(A,item.instanceId(),null),"Hyper Potion ignored Sinnoh's single-unit override");m.discardStadiumByEffect(A);
        m.playTrainer(A,item.instanceId(),null);chooseHost(m,host);var q=m.effectCardPrompt(A);check(q.candidates().equals(List.of(energy)),"Hyper Potion omitted double Energy discard candidate");m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(energy.instanceId()));NonPokemonBatch2Test.settle(m);
        check(m.activePokemonView(A).damage()==80&&m.activePokemonView(A).attachedEnergyIds().isEmpty()&&m.privateView(A).discard().contains(energy),"Hyper Potion did not heal 120 and discard one double Energy card");
        var blocked=game("swsh1_en_166",EnergyType.COLORLESS,2,"swsh6_en_135");var target=blocked.activePokemonView(A).pokemonId();blocked.placeDamageCountersByEffect(A,A,target,20);var special=card(blocked,"special");blocked.attachEnergy(A,special.instanceId(),target);blocked.playTrainer(A,card(blocked,"stadium").instanceId(),null);
        check(!blocked.canPlayTrainer(A,card(blocked,"item").instanceId(),null)&&blocked.activePokemonView(A).attachedEnergyIds().contains(special.instanceId()),"Blocked healing offered a discard-only use");
    }
    static void tin(){
        for(EnergyType type:List.of(EnergyType.PSYCHIC,EnergyType.GRASS)){
            var m=game("swsh3-5_en_066",type,1,"swsh10_en_155");var host=m.activePokemonView(A).pokemonId();m.placeDamageCountersByEffect(A,A,host,12);var energy=card(m,"special");m.attachEnergy(A,energy.instanceId(),host);var item=card(m,"item");
            if(type==EnergyType.GRASS){check(!m.canPlayTrainer(A,item.instanceId(),null),"Food Tin accepted non-Psychic Energy");continue;}
            m.playTrainer(A,item.instanceId(),null);chooseHost(m,host);var q=m.effectCardPrompt(A);m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(energy.instanceId()));NonPokemonBatch2Test.settle(m);
            check(m.activePokemonView(A).damage()==40&&m.privateView(A).discard().contains(energy),"Food Tin ignored supplied Psychic type or actual healing");
        }
    }
    static void fan(){
        var m=NonPokemonBatch2Test.game("swsh5_en_127",EnergyType.GRASS,1);var bench=m.benchPokemonView(B).getFirst().pokemonId();var energy=DynamicPassiveModifierTest.hand(m,B,"special");var basic=DynamicPassiveModifierTest.hand(m,B,"energy");m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,energy.instanceId(),B,bench);m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,basic.instanceId(),B,m.activePokemonView(B).pokemonId());var deck=List.copyOf(m.privateView(B).deck());
        m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);var q=m.effectCardPrompt(A);check(q.candidates().equals(List.of(energy)),"Fan of Waves included Basic Energy or missed Bench");m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(energy.instanceId()));NonPokemonBatch2Test.settle(m);
        check(m.privateView(B).deck().getFirst().equals(energy)&&m.privateView(B).deck().subList(1,m.privateView(B).deck().size()).equals(deck),"Fan of Waves shuffled deck or returned to wrong end/owner");check(m.activePokemonView(B).attachedEnergyIds().contains(basic.instanceId())&&m.benchPokemonView(B).getFirst().attachedEnergyIds().isEmpty(),"Fan of Waves removed wrong attachment");
    }
}
