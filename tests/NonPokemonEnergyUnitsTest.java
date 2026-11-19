import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/** Official C-10/D-05: supplied units, whole-card movement and nonempty public selections. */
public final class NonPokemonEnergyUnitsTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    public static void main(String[] args){
        for(String id:List.of("sv3_en_193","zsv10-5_en_083"))for(int units:List.of(0,2)) {
            var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();
            c.specialEnergy=units==0?new EnergyProfile(List.of()):EnergyProfile.fixed(EnergyType.COLORLESS,units);
            var m=GeneratedRuleBatch28Test.fixture(c);var from=m.benchPokemonView(A).getFirst().pokemonId();var to=m.activePokemonView(A).pokemonId();
            var energy=DynamicPassiveModifierTest.hand(m,A,"energy");m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,energy.instanceId(),A,from);
            var trainer=DynamicPassiveModifierTest.hand(m,A,"trainer");
            check(m.canPlayTrainer(A,trainer.instanceId(),null)==(units>0),"Zero supplied Energy became a movable unit: "+id);
            if(units==0)continue;
            m.playTrainer(A,trainer.instanceId(),null);var q=m.effectCardPrompt(A);
            check(q.minimum()==1&&q.candidates().equals(List.of(energy)),"Public up-to Energy selection allowed zero or wrong host");
            GeneratedRuleBatch8Test.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of()));
            m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(energy.instanceId()));
            if(m.hasPendingAttachmentTarget()){var p=m.effectAttachmentPrompt(A);m.chooseEffectAttachmentTarget(A,p.resolutionId(),p.step(),to);}
            check(m.activePokemonView(A).attachedEnergyIds().contains(energy.instanceId())&&m.benchPokemonView(A).getFirst().attachedEnergyIds().isEmpty(),"Double Energy was split or lost");
            check(!m.energyAttached(A)&&!m.hasPendingEffectChoice(),"Effect used manual attachment or stalled");
        }
        for(String id:List.of("sv1_en_195","sv1_en_242","sv3_en_195","sm9_en_151","sc_csm1_5c_073_sr"))for(int units:List.of(0,2)) {
            var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();
            c.specialEnergy=units==0?new EnergyProfile(List.of()):EnergyProfile.fixed(EnergyType.COLORLESS,units);
            var m=GeneratedRuleBatch28Test.fixture(c);var energy=DynamicPassiveModifierTest.hand(m,B,"energy");
            m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,energy.instanceId(),B,m.activePokemonView(B).pokemonId());
            var trainer=DynamicPassiveModifierTest.hand(m,A,"trainer");
            check(m.canPlayTrainer(A,trainer.instanceId(),null)==(units>0),"Deck-top return used card count instead of units");
            if(units==0)continue;
            m.playTrainer(A,trainer.instanceId(),null);var p=m.effectCardPrompt(A);
            m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(energy.instanceId()));
            check(m.privateView(B).deck().getLast().equals(energy)&&m.activePokemonView(B).attachedEnergyIds().isEmpty(),"Whole Energy was not put on its owner's deck top");
        }
        for(String id:List.of("sv3-5_en_161","sv3-5_en_197","sv3-5_en_204","sc_csv1c_159_sar"))for(UUID actor:List.of(A,B))for(int units:List.of(0,2)) {
            var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();
            c.specialEnergy=units==0?new EnergyProfile(List.of()):EnergyProfile.fixed(EnergyType.COLORLESS,units);
            var m=GeneratedRuleBatch28Test.fixture(c);if(actor.equals(B))m.endTurn(A);UUID opponent=actor.equals(A)?B:A;
            var returned=DynamicPassiveModifierTest.hand(m,opponent,"energy");
            m.attachEnergyByEffect(actor,opponent,EffectSourceZone.HAND,returned.instanceId(),opponent,m.activePokemonView(opponent).pokemonId());
            var trainer=DynamicPassiveModifierTest.hand(m,actor,"trainer");
            check(m.canPlayTrainer(actor,trainer.instanceId(),null)==(units>0),"Giovanni return gate ignored current supplied units: "+id);
            if(units==0)continue;
            m.playTrainer(actor,trainer.instanceId(),null);var q=m.effectCardPrompt(actor);
            m.submitEffectCards(actor,q.resolutionId(),q.step(),List.of(returned.instanceId()));
            AiCardOperationTest.settle(MatchSession.multiplayer(m,A,B),AiDifficulty.NORMAL);
            check(m.privateView(opponent).hand().contains(returned)&&m.activePokemonView(opponent).attachedEnergyIds().isEmpty(),"Giovanni split or lost the returned double Energy");
            check(m.activePokemonView(actor).attachedEnergyIds().size()==1&&!m.energyAttached(actor)&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(actor),"Giovanni failed the bound follow-up attachment");
        }
        NonPokemonBatch20Test.main(args);NonPokemonBatch40Test.main(args);
        System.out.println("NonPokemonEnergyUnitsTest passed: Poppy/N's Plan and four Giovanni printings, zero/two units, nonempty public choice, whole-card movement and bound follow-up attachments; Tool reactions and Tag Switch regressions");
    }
}
