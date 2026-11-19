import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Official errata plus CN v3.1 / EN v3.4 D-05 public-zone 1..N selection. */
public final class TrainerErrataTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        for(String id:List.of("sv2_en_188","sv2_en_276","xy8_en_149","bw3_en_095","bw6-5_en_020","bwalt_en_035"))
            for(int count=1;count<=3;count++)recover(id,count,3,true);
        for(String id:List.of("bw1_en_092","sv1_en_171","sd100_cn_365"))recover(id,1,2,false);
        for(String id:List.of("sm2_en_123","swsh5_en_124","sd100_cn_368"))recover(id,1,5,true);
        for(String id:List.of("sv10_en_168","xy2_en_096","me3_en_115"))recover(id,1,5,true,true);
        for(String id:List.of("sv1_en_182","sc_csv6c_163_ur"))palPad(id);
        expShare();
        System.out.println("TrainerErrataTest passed: six Super Rod printings allow 1/2/3; public recovery rejects zero; empty discard cannot activate; selected physical cards transferred once");
    }
    static void expShare(){for(String id:List.of("sd100_cn_383","sm1_en_118","sv1_en_174","swsh5_en_126","swsh5_en_180","bw4_en_087","bw6-5_en_018","xy5_en_128"))for(UUID owner:List.of(A,B))for(boolean accept:List.of(false,true)){
        var m=NonPokemonBatch108Test.game(EnergyType.DARKNESS,EnergyType.DARKNESS,BundledCombatData.load().trainer(id).orElseThrow(),DynamicPassiveModifierTest.plainStadium(),GeneratedRuleBatch8Test.hit(100,false));
        if(owner.equals(B))m.endTurn(A);UUID opponent=owner.equals(A)?B:A;UUID target=m.activePokemonView(owner).pokemonId(),receiver=m.benchPokemonView(owner).getFirst().pokemonId();
        m.playTrainer(owner,NonPokemonBatch108Test.hand(m,owner,"tool").instanceId(),receiver);
        var energy=NonPokemonBatch108Test.energy(m,owner,target,"energy");var special=NonPokemonBatch108Test.energy(m,owner,target,"special");
        NonPokemonBatch108Test.damage(m,owner,owner,target,400);m.endTurn(owner);m.attack(opponent,"hit");var trigger=m.triggerOrderPrompt();
        check(trigger!=null&&trigger.optionalDecision(),"Exp. Share lost optional activation: "+id);m.decideTriggeredEffect(owner,trigger.id(),accept);
        if(accept){var q=m.effectCardPrompt(owner);check(q!=null&&q.minimum()==1&&q.maximum()==1&&q.candidates().equals(List.of(energy)),"Exp. Share accepted empty/Special Energy selection: "+id);
            GeneratedRuleBatch8Test.reject(()->m.submitEffectCards(owner,q.resolutionId(),q.step(),List.of()));m.submitEffectCards(owner,q.resolutionId(),q.step(),List.of(energy.instanceId()));}
        NonPokemonBatch108Test.settle(m);
        check(EventTargetBindingTest.field(m).attachedEnergy(owner,receiver).contains(energy)==accept&&m.privateView(owner).discard().contains(energy)!=accept,"Exp. Share moved wrong physical Energy or ignored decline");
        check(m.privateView(owner).discard().contains(special),"Exp. Share moved Special Energy");
    }}
    static void palPad(String id){
        var supporter=BundledCombatData.trainer(com.google.gson.JsonParser.parseString("{\"trainerCategory\":\"SUPPORTER\",\"effects\":[{\"type\":\"draw\",\"target\":\"SELF\",\"amount\":1}]}").getAsJsonObject(),dev.hua.ptcglive.engine.effect.EffectRegistry.standard());
        var m=ModernEnergyRepairTest.game(BundledCombatData.load().trainer(id).orElseThrow(),List.of(),GeneratedRuleBatch8Test.hit(100,false),EnergyType.GRASS,0,Map.of(),Map.of(),Map.of("supporter",supporter));
        var card=ModernRuleRepair2Test.hand(m,A,"trainer");check(!m.canPlayTrainer(A,card.instanceId(),null),"Pal Pad usable without discarded Supporter");
        var saved=ModernRuleRepair2Test.hand(m,A,"supporter");m.discardHandCardsByEffect(A,A,List.of(saved.instanceId()));m.playTrainer(A,card.instanceId(),null);var q=m.effectCardPrompt(A);
        check(q.minimum()==1&&q.maximum()==2&&q.candidates().equals(List.of(saved)),"Pal Pad regional printing lacks public recovery restriction");
        GeneratedRuleBatch8Test.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of()));m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(saved.instanceId()));
        check(m.privateView(A).deck().contains(saved)&&!m.hasPendingEffectChoice(),"Pal Pad failed recovery");
    }
    static void recover(String id,int count,int maximum,boolean deck){
        recover(id,count,maximum,deck,false);
    }
    static void recover(String id,int count,int maximum,boolean deck,boolean pokemon){
        var m=NonPokemonBatch2Test.game(id,EnergyType.GRASS,1);var source=NonPokemonBatch2Test.trainer(m);
        check(!m.canPlayTrainer(A,source.instanceId(),null),"Empty public discard permits no-effect Trainer: "+id);
        var stock=m.privateView(A).hand().stream().filter(c->pokemon?m.cardDefinition(c.definitionId()).basicPokemon():m.cardDefinition(c.definitionId()).basicEnergy()).limit(3).toList();
        check(stock.size()==3,"Missing recovery fixture cards");
        m.discardHandCardsByEffect(A,A,stock.stream().map(CardInstance::instanceId).toList());
        int deckSize=m.privateView(A).deck().size(),handSize=m.privateView(A).hand().size();
        m.playTrainer(A,source.instanceId(),null);var q=m.effectCardPrompt(A);
        check(q!=null&&q.minimum()==1&&q.maximum()==maximum,"Wrong updated recovery range: "+id);
        GeneratedRuleBatch8Test.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of()));
        var chosen=stock.subList(0,count);m.submitEffectCards(A,q.resolutionId(),q.step(),chosen.stream().map(CardInstance::instanceId).toList());
        NonPokemonBatch2Test.settle(m);
        check(m.privateView(A).discard().contains(source)&&m.privateView(A).discard().containsAll(stock.subList(count,stock.size())),"Wrong source/unselected disposition");
        check((deck?m.privateView(A).deck():m.privateView(A).hand()).containsAll(chosen),"Selected physical cards not recovered");
        check(m.privateView(A).deck().size()==deckSize+(deck?count:0)&&m.privateView(A).hand().size()==handSize-1+(deck?0:count),"Recovery count changed twice");
    }
}
