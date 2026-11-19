import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernRuleRepair3Test {
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){CopyAttackTest.check(ok,why);}
    static void reject(Runnable op){CopyAttackTest.reject(op);}
    public static void main(String[] args){banette();druddigon();ribombee();System.out.println("ModernRuleRepair3Test passed: Banette private opponent choices, Druddigon typed discard attachment, two Ribombee prints' optional hand-evolution and multi-card Bench placement");}
    static void banette(){
        UUID a=ModernRuleRepairTest.A,b=ModernRuleRepairTest.B;
        var attack=DATA.pokemon("sv9_en_060").orElseThrow().attacks().getFirst();AttackProgramRules.validate(attack);
        for(int size:List.of(0,2,7)){
            var m=ModernRuleRepairTest.game(List.of(attack),List.of(),EnergyType.PSYCHIC,null);ModernRuleRepairTest.pay(m,1);
            if(m.privateView(b).hand().size()<size)m.drawCardsByEffect(a,b,size-m.privateView(b).hand().size());
            var discard=m.privateView(b).hand().stream().skip(size).map(CardInstance::instanceId).toList();if(!discard.isEmpty())m.discardHandCardsByEffect(a,b,discard);
            var before=m.privateView(b);var session=MatchSession.multiplayer(m,a,b);m.attack(a,attack.id());
            var p=m.clientView(b).effectSelection();check((p!=null)==(size>0),"empty hand selection");
            if(p!=null){
                check(m.pendingChoicePlayer().equals(b)&&p.minimum()==Math.min(3,size)&&p.maximum()==Math.min(3,size),"opponent must choose exactly up to available 3");
                check(session.view(a,a).game().effectSelection()==null&&m.effectCardReveal()==null,"opponent hand disclosed to attacker");
                var chosen=p.candidates().stream().limit(Math.min(3,size)).map(CardInstance::instanceId).toList();
                reject(()->m.submitEffectCards(a,p.resolutionId(),p.step(),chosen));
                reject(()->m.submitEffectCards(b,p.resolutionId(),p.step(),List.of()));
                m.submitEffectCards(b,p.resolutionId(),p.step(),chosen);reject(()->m.submitEffectCards(b,p.resolutionId(),p.step(),chosen));
            }
            check(m.privateView(b).hand().size()==size-Math.min(3,size)+1&&m.privateView(b).deck().size()==before.deck().size()+Math.min(3,size)-1,"shuffle then opponent turn draw");
            check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(b),"Banette stalled");
        }
    }
    static void druddigon(){
        UUID a=GeneratedRuleBatch8Test.A,b=GeneratedRuleBatch8Test.B;
        var attack=DATA.pokemon("sv9_en_115").orElseThrow().attacks().getFirst();AttackProgramRules.validate(attack);
        for(boolean present:List.of(false,true)){
            var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.FIRE,EnergyType.DRAGON,DATA.trainer("swsh5_en_124").orElseThrow(),attack);
            UUID active=m.activePokemonView(a).pokemonId(),bench=m.benchPokemonView(a).getFirst().pokemonId();
            m.evolveByEffect(a,bench,ModernRuleRepair2Test.hand(m,a,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
            ModernRuleRepair2Test.pay(m,a,active,"energy",1);
            var energy=ModernRuleRepair2Test.hand(m,a,"energy");var wrong=ModernRuleRepair2Test.hand(m,a,"wrong");var special=ModernRuleRepair2Test.hand(m,a,"special");
            m.discardHandCardsByEffect(a,a,(present?List.of(energy,wrong,special):List.of(wrong,special)).stream().map(CardInstance::instanceId).toList());
            m.attack(a,attack.id());var p=m.clientView(a).effectSelection();check((p!=null)==present,"discard typed availability");
            if(p!=null){check(p.candidates().size()==1&&p.candidates().getFirst().equals(energy),"Fire Basic filter");m.submitEffectCards(a,p.resolutionId(),p.step(),List.of(energy.instanceId()));
                var q=m.effectAttachmentPrompt(a);check(!q.targets().contains(bench),"non-Dragon target allowed");
                check(q.targets().contains(active),"Dragon target missing");
                reject(()->m.assignEffectEnergyDistribution(a,q.resolutionId(),q.step(),energy.instanceId(),bench));
                m.assignEffectEnergyDistribution(a,q.resolutionId(),q.step(),energy.instanceId(),active);
                m.confirmEffectEnergyDistribution(a,q.resolutionId(),q.step());
                check(m.activePokemonView(a).attachedEnergyIds().contains(energy.instanceId()),"discard Energy not attached");}
            check(m.activePokemonView(b).damage()==20&&m.currentPlayer().equals(b)&&!m.hasPendingEffectChoice(),"Dragon's Fury stalled");
        }
    }
    static void ribombee(){
        UUID a=ModernRuleRepair2Test.A,b=ModernRuleRepair2Test.B;
        for(String id:List.of("sv9_en_067","sv9_en_164"))for(int mode:List.of(0,1,2)){
            var ability=DATA.pokemon(id).orElseThrow().abilities().getFirst();TriggeredAbilityRules.validate(ability);
            var m=ModernRuleRepair2Test.selectionGame("sv10_en_175",List.of(ability),0);
            while(m.privateView(a).hand().stream().noneMatch(c->c.definitionId().equals("evolved")))m.drawCardsByEffect(a,a,1);
            while(m.privateView(b).hand().stream().filter(c->m.cardDefinition(c.definitionId()).basicPokemon()).count()<5)m.drawCardsByEffect(a,b,1);
            m.evolveByEffect(a,m.activePokemonView(a).pokemonId(),ModernRuleRepair2Test.hand(m,a,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
            var decision=m.triggerOrderPrompt();check(decision!=null&&decision.optionalDecision()&&decision.choosingPlayer().equals(a),"optional evolution trigger");
            m.decideTriggeredEffect(a,decision.id(),mode!=0);
            if(mode==0){check(m.effectCardReveal()==null&&!m.hasPendingEffectChoice(),"declining revealed hand");continue;}
            var reveal=m.effectCardReveal();check(reveal!=null&&reveal.cardIds().size()==m.privateView(b).hand().size(),"actual opposing hand not revealed");
            m.acknowledgeEffectReveal(reveal.confirmingPlayer(),reveal.id());var p=m.clientView(a).effectSelection();
            check(p!=null&&p.minimum()==0&&p.maximum()<=4&&p.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).basicPokemon()),"Bench capacity/basic selection");
            var chosen=mode==1?List.<UUID>of():p.candidates().stream().limit(p.maximum()).map(CardInstance::instanceId).toList();int before=m.benchPokemonView(b).size();
            reject(()->m.submitEffectCards(b,p.resolutionId(),p.step(),chosen));m.submitEffectCards(a,p.resolutionId(),p.step(),chosen);
            reject(()->m.submitEffectCards(a,p.resolutionId(),p.step(),chosen));
            check(m.benchPokemonView(b).size()==before+chosen.size()&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(a),"hand-to-Bench placement/continuation");
        }
    }
}
