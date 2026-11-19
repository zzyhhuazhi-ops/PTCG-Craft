import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch31Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static GeneratedRuleBatch28Test.Config config(String id){var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();c.names=Map.of("attacker","Alpha","source","Alpha","evolved","Alpha Evolution","new","Beta");return c;}
    static ServerMatch namedGame(String id){return GeneratedRuleBatch28Test.fixture(config(id));}
    static void play(ServerMatch m){NonPokemonBatch25Test.play(m);}
    static void settle(ServerMatch m){NonPokemonBatch2Test.settle(m);}
    public static void main(String[] args){green();names();friend();oak();System.out.println("NonPokemonBatch31Test passed: current own abilities including Bench and effect locks, same names across printings, discard-only pool, opponent live types, distinct Basic types and Bench placement");}
    static void green(){
        var ability=GeneratedRuleBatch8Test.ability("sm2_en_059");
        var m=GeneratedRuleBatch8Test.fixture(List.of(ability),EnergyType.METAL,EnergyType.METAL,NonPokemonBatch2Test.DATA.trainer("sm10_en_175").orElseThrow(),GeneratedRuleBatch8Test.hit(10,false));var trainer=NonPokemonBatch2Test.trainer(m);
        check(!m.canPlayTrainer(A,trainer.instanceId(),null),"Green accepted own Ability");m.disableAbilityEffectsByEffect(A,A,m.activePokemonView(A).pokemonId());check(!m.canPlayTrainer(A,trainer.instanceId(),null),"Green ignored Bench Ability");m.disableAbilityEffectsByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId());check(!m.canPlayTrainer(A,trainer.instanceId(),null),"Ability effect lock incorrectly erased possession");var old=List.of(m.activePokemonView(A).pokemonId(),m.benchPokemonView(A).getFirst().pokemonId());var plain=DynamicPassiveModifierTest.hand(m,A,"rulebox");m.playBasicPokemonToBench(A,plain.instanceId());m.switchActiveByEffect(A,A,plain.instanceId());for(UUID id:old)m.movePokemonByEffect(A,A,id,LeavePlayDestination.HAND);check(m.canPlayTrainer(A,trainer.instanceId(),null),"Green counted removed Pokemon or opponent Abilities");play(m);var q=m.effectCardPrompt(A);check(q.maximum()==2&&q.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).category()==CardCategory.TRAINER),"Green searched non-Trainers");settle(m);
    }
    static void names(){
        for(boolean discard:List.of(false,true)){
            var m=namedGame(discard?"swsh3_en_161":"xy5_en_136");var trainer=NonPokemonBatch2Test.trainer(m);
            if(discard){check(!m.canPlayTrainer(A,trainer.instanceId(),null),"Familiar Bell used field names instead of empty discard");m.discardHandCardsByEffect(A,A,List.of(DynamicPassiveModifierTest.hand(m,A,"attacker").instanceId()));}
            play(m);var q=m.effectCardPrompt(A);check(!q.candidates().isEmpty()&&q.candidates().stream().allMatch(c->Set.of("attacker","source").contains(c.definitionId())),"Name search matched evolution/substring/wrong zone");settle(m);
        }
    }
    static void friend(){var c=config("sm7_en_131");c.ownTypes=Set.of(EnergyType.FIRE,EnergyType.GRASS);c.defenderTypes=Set.of(EnergyType.GRASS);c.evolvedTypes=Set.of(EnergyType.WATER);var m=GeneratedRuleBatch28Test.fixture(c);play(m);var q=m.effectCardPrompt(A);check(!q.candidates().isEmpty()&&q.candidates().stream().allMatch(x->Set.of("attacker","source").contains(x.definitionId())),"Friend Ball ignored dual type or matched own types");settle(m);}
    static void oak(){var c=config("sm12_en_201");c.ownTypes=Set.of(EnergyType.GRASS);var m=GeneratedRuleBatch28Test.fixture(c);play(m);var q=m.effectCardPrompt(A);check(q.candidates().stream().noneMatch(x->x.definitionId().equals("evolved")),"Oak directly placed evolution");var same=q.candidates().stream().filter(x->Set.of("attacker","source").contains(x.definitionId())).limit(2).toList();check(same.size()==2,"Oak fixture lacks same-type pair");GeneratedRuleBatch8Test.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),same.stream().map(CardInstance::instanceId).toList()));var mixed=new ArrayList<UUID>();mixed.add(same.getFirst().instanceId());q.candidates().stream().filter(x->x.definitionId().equals("new")).findFirst().ifPresent(x->mixed.add(x.instanceId()));int bench=m.benchPokemonView(A).size();m.submitEffectCards(A,q.resolutionId(),q.step(),mixed);settle(m);check(m.benchPokemonView(A).size()==bench+mixed.size(),"Oak failed direct Basic placement");}
}
