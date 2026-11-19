import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernKantoSupporterRepairTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    public static void main(String[] args){giovanni();erika();System.out.println("ModernKantoSupporterRepairTest passed: Giovanni three prints actual return gates own attachment; Erika three prints revealed Basic becomes exact Active; empty/blocked sources and stale/unauthorized requests");}
    static void giovanni(){
        for(String id:List.of("sv3-5_en_161","sv3-5_en_197","sv3-5_en_204"))for(boolean hasEnergy:List.of(false,true))for(boolean special:List.of(false,true))for(boolean shield:List.of(false,true))for(boolean ownEnergy:List.of(false,true)){
            var m=ModernEnergyRepairTest.game(BundledCombatData.load().trainer(id).orElseThrow(),List.of(),GeneratedRuleBatch8Test.hit(10,false),EnergyType.DARKNESS,0);m.drawCardsByEffect(A,B,20);
            UUID target=m.activePokemonView(B).pokemonId(),source=m.activePokemonView(A).pokemonId();CardInstance returned=ModernRuleRepair2Test.hand(m,B,special?"special":"energy");
            if(hasEnergy)m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,returned.instanceId(),B,target);
            if(shield)EventTargetBindingTest.field(m).addProtection(B,B,target,new ProtectionEffect(new EffectProtection(EffectProtection.Source.TRAINER,EffectProtection.Relation.OPPONENT,EffectProtection.Scope.EFFECTS_ONLY),ContinuousEffect.Expiry.LEAVES_PLAY));
            if(!ownEnergy)m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().filter(c->m.cardDefinition(c.definitionId()).category()==CardCategory.ENERGY).map(CardInstance::instanceId).toList());
            var trainer=ModernRuleRepair2Test.hand(m,A,"trainer");if(!m.canPlayTrainer(A,trainer.instanceId(),null)){check(!hasEnergy||shield,"Giovanni rejected usable return");continue;}
            m.playTrainer(A,trainer.instanceId(),null);CardInstance attached=null;
            if(m.hasPendingEffectChoice()){
                var q=m.effectCardPrompt(A);check(q.candidates().equals(List.of(returned)),"Giovanni wrong attached source");CopyAttackTest.reject(()->m.submitEffectCards(B,q.resolutionId(),q.step(),List.of(returned.instanceId())));m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(returned.instanceId()));
                if(m.hasPendingEffectChoice()){
                    var p=m.effectCardPrompt(A);check(hasEnergy&&!shield&&ownEnergy&&p.minimum()==1&&p.maximum()==1,"Giovanni attached without successful return");
                    attached=p.candidates().stream().filter(c->c.definitionId().equals(special?"special":"energy")).findFirst().orElseThrow();m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(attached.instanceId()));
                    var host=m.effectAttachmentPrompt(A);check(host.targets().equals(List.of(source)),"Giovanni offered non-Active host");m.chooseEffectAttachmentTarget(A,host.resolutionId(),host.step(),source);
                }
            }
            check(m.activePokemonView(B).attachedEnergyIds().contains(returned.instanceId())==(hasEnergy&&shield),"Giovanni return/prevention mismatch");
            check(m.activePokemonView(A).attachedEnergyIds().size()==(hasEnergy&&!shield&&ownEnergy?1:0),"Giovanni missing/duplicated own attachment "+id+" has="+hasEnergy+" special="+special+" shield="+shield+" own="+ownEnergy+" actual="+m.activePokemonView(A).attachedEnergyIds().size()+" pending="+m.hasPendingEffectChoice());
            if(attached!=null)check(!m.privateView(A).hand().contains(attached),"Giovanni duplicated hand energy");
            check(!m.hasPendingEffectChoice()&&m.privateView(A).discard().contains(trainer)&&m.currentPlayer().equals(A),"Giovanni continuation or supporter consumption");
        }
    }
    static void erika(){
        for(String id:List.of("sv3-5_en_160","sv3-5_en_196","sv3-5_en_203"))for(boolean basic:List.of(false,true))for(boolean full:List.of(false,true)){
            var m=ModernEnergyRepairTest.game(BundledCombatData.load().trainer(id).orElseThrow(),List.of(),GeneratedRuleBatch8Test.hit(10,false),EnergyType.DARKNESS,0);m.drawCardsByEffect(A,B,20);
            if(full){m.endTurn(A);while(m.benchPokemonView(B).size()<5)m.playBasicPokemonToBench(B,ModernRuleRepair2Test.hand(m,B,"basic").instanceId());m.endTurn(B);}
            if(!basic)m.discardHandCardsByEffect(A,B,m.privateView(B).hand().stream().filter(c->m.cardDefinition(c.definitionId()).basicPokemon()).map(CardInstance::instanceId).toList());
            var old=m.activePokemonView(B);int bench=m.benchPokemonView(B).size();var trainer=ModernRuleRepair2Test.hand(m,A,"trainer");
            if(!m.canPlayTrainer(A,trainer.instanceId(),null)){check(full||!basic,"Erika rejected usable hand");continue;}
            m.playTrainer(A,trainer.instanceId(),null);var reveal=m.effectCardReveal();if(reveal!=null)m.acknowledgeEffectReveal(reveal.confirmingPlayer(),reveal.id());
            if(basic&&!full){var q=m.effectCardPrompt(A);check(q.minimum()==1&&q.maximum()==1&&q.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).basicPokemon()),"Erika missing mandatory Basic-only hand choice");var selected=q.candidates().getLast();
                CopyAttackTest.reject(()->m.submitEffectCards(B,q.resolutionId(),q.step(),List.of(selected.instanceId())));m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(selected.instanceId()));
                check(m.activePokemonView(B).topCardId().equals(selected.definitionId())&&!m.activePokemonView(B).pokemonId().equals(old.pokemonId())&&m.benchPokemonView(B).stream().anyMatch(p->p.pokemonId().equals(old.pokemonId()))&&!m.privateView(B).hand().contains(selected)&&m.benchPokemonView(B).size()==bench+1,"Erika switched wrong/new host or copied card");
                CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(selected.instanceId())));
            }else check(m.activePokemonView(B).pokemonId().equals(old.pokemonId())&&m.benchPokemonView(B).size()==bench,"Erika changed field without legal placement");
            check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A),"Erika stalled");
        }
    }
}
