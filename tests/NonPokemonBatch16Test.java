import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import com.google.gson.JsonParser;
import java.util.*;

public final class NonPokemonBatch16Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){communication();maintenance();spoon();System.out.println("NonPokemonBatch16Test passed: mandatory revealed Pokemon exchange, complete two-card shuffle cost including empty deck, Tool blocks new attack effects but retains damage and existing status");}
    static void communication(){
        for(String id:List.of("bw1_en_099","sm9_en_152")){
            var without=NonPokemonBatch2Test.game(id,EnergyType.GRASS,1);
            var pokemon=without.privateView(A).hand().stream().filter(c->without.cardDefinition(c.definitionId()).category()==CardCategory.POKEMON).toList();
            without.discardHandCardsByEffect(A,A,pokemon.stream().map(CardInstance::instanceId).toList());
            check(!without.canPlayTrainer(A,NonPokemonBatch2Test.trainer(without).instanceId(),null),"Communication accepted no Pokemon in hand");
            var m=NonPokemonBatch2Test.game(id,EnergyType.GRASS,1);var trainer=NonPokemonBatch2Test.trainer(m);m.playTrainer(A,trainer.instanceId(),null);
            var q=m.effectCardPrompt(A);check(q.minimum()==1&&q.maximum()==1&&q.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).category()==CardCategory.POKEMON),"Communication first selection not one Pokemon");
            var returned=q.candidates().getFirst();m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(returned.instanceId()));
            check(m.privateView(A).hand().contains(returned),"Communication moved card before reveal acknowledgement");
            NonPokemonBatch14Test.reveal(m);q=m.effectCardPrompt(A);check(q.minimum()==0&&q.maximum()==1&&q.candidates().contains(returned),"Communication cannot retrieve returned Pokemon");
            m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(returned.instanceId()));NonPokemonBatch2Test.settle(m);
            check(m.privateView(A).hand().contains(returned)&&!m.privateView(A).deck().contains(returned),"Communication lost exchanged physical card");
        }
    }
    static void maintenance(){
        for(boolean emptyDeck:List.of(false,true)){
            var m=NonPokemonBatch2Test.game("xy12_en_079",EnergyType.GRASS,1);var trainer=NonPokemonBatch2Test.trainer(m);
            if(emptyDeck)m.drawCardsByEffect(A,A,m.privateView(A).deck().size());
            NonPokemonBatch2Test.trim(m,A,2,trainer);check(!m.canPlayTrainer(A,trainer.instanceId(),null),"Maintenance accepted only one other hand card");
        }
        var m=NonPokemonBatch2Test.game("xy12_en_079",EnergyType.GRASS,1);m.drawCardsByEffect(A,A,m.privateView(A).deck().size());var trainer=NonPokemonBatch2Test.trainer(m);NonPokemonBatch2Test.trim(m,A,3,trainer);
        check(m.canPlayTrainer(A,trainer.instanceId(),null),"Maintenance rejected empty deck despite returning cards first");
        var selected=m.privateView(A).hand().stream().filter(c->!c.equals(trainer)).map(CardInstance::instanceId).toList();
        m.playTrainer(A,trainer.instanceId(),null);var q=m.effectCardPrompt(A);check(q.minimum()==2&&q.maximum()==2,"Maintenance cost was not exactly two");
        m.submitEffectCards(A,q.resolutionId(),q.step(),selected);NonPokemonBatch2Test.settle(m);
        check(m.privateView(A).hand().size()==1&&m.privateView(A).deck().size()==1&&selected.contains(m.privateView(A).hand().getFirst().instanceId()),"Maintenance did not shuffle before draw");
        check(m.privateView(A).discard().contains(trainer),"Maintenance shuffled its own source card");
    }
    static void spoon(){
        var attack=BundledCombatData.attack(JsonParser.parseString("""
            {"id":"hit","nameZh":"test","energyCost":{},"baseDamage":100,"damageType":"COLORLESS","effectSupport":"SUPPORTED","triggers":[{"trigger":"AFTER_ATTACK","effects":[{"type":"damage_counter","target":"OPPONENT_ACTIVE","amount":2}]}]}
            """).getAsJsonObject(),EffectRegistry.standard());
        for(boolean disabled:List.of(false,true)){
            var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.GRASS,EnergyType.GRASS,NonPokemonBatch2Test.DATA.trainer("xy10_en_093").orElseThrow(),attack);
            var host=m.activePokemonView(A).pokemonId();m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);
            m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),host);check(m.activePokemonView(A).conditions().contains(SpecialCondition.POISONED),"Bent Spoon removed existing poison");
            m.endTurn(A);if(disabled)m.disableToolEffectsByEffect(B,A,host);int before=m.activePokemonView(A).damage();m.attack(B,"hit");
            check(m.lastAttack().finalDamage()==100,"Bent Spoon prevented attack damage");
            check(m.activePokemonView(A).damage()-before==(disabled?130:110),"Bent Spoon effect guard or disabled-tool behavior wrong");
        }
    }
}
