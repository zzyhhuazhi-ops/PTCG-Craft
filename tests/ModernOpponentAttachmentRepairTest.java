import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernOpponentAttachmentRepairTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    public static void main(String[] args){
        var profile=BundledCombatData.load().pokemon("sv8_en_121").orElseThrow();var attach=profile.attacks().getFirst();var damage=profile.attacks().get(1);AttackProgramRules.validate(attach);AttackProgramRules.validate(damage);
        for(int available:List.of(0,1,3,4))for(int request:List.of(0,1,3))for(boolean split:List.of(false,true))for(boolean guard:List.of(false,true)){
            var m=ModernWindowDamageRepairTest.game(attach,0,false,List.of(damage));m.drawCardsByEffect(A,B,20);var pool=new ArrayList<CardInstance>();pool.add(ModernRuleRepair2Test.hand(m,B,"special"));pool.addAll(m.privateView(B).hand().stream().filter(c->c.definitionId().equals("energy")).limit(3).toList());check(pool.size()==4,"Opponent attachment fixture");
            var discard=pool.subList(0,available);if(!discard.isEmpty())m.discardHandCardsByEffect(A,B,discard.stream().map(CardInstance::instanceId).toList());
            var wrong=ModernRuleRepair2Test.hand(m,B,"future-item");m.discardHandCardsByEffect(A,B,List.of(wrong.instanceId()));var own=ModernRuleRepair2Test.hand(m,A,"energy");m.discardHandCardsByEffect(A,A,List.of(own.instanceId()));
            UUID active=m.activePokemonView(B).pokemonId(),bench=m.benchPokemonView(B).getFirst().pokemonId();if(guard)EventTargetBindingTest.field(m).addProtection(B,B,bench,new ProtectionEffect(new EffectProtection(EffectProtection.Source.ATTACK,EffectProtection.Relation.OPPONENT,EffectProtection.Scope.EFFECTS_ONLY),ContinuousEffect.Expiry.LEAVES_PLAY));
            var beforeA=m.privateView(A);var beforeB=m.privateView(B);var random=m.randomTranscript();m.attack(A,attach.id());int count=Math.min(available,request);var chosen=discard.subList(0,count);var applied=new ArrayList<CardInstance>();
            if(available>0){var q=m.effectCardPrompt(A);check(new HashSet<>(q.candidates()).equals(new HashSet<>(discard))&&m.clientView(B).effectSelection()==null,"Wrong chooser or source for opponent discard");CopyAttackTest.reject(()->m.submitEffectCards(B,q.resolutionId(),q.step(),List.of()));CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(own.instanceId())));
                if(available>3)CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),discard.stream().map(CardInstance::instanceId).toList()));m.submitEffectCards(A,q.resolutionId(),q.step(),chosen.stream().map(CardInstance::instanceId).toList());
                if(count>0){var q2=m.effectAttachmentPrompt(A);check(q2.targets().containsAll(List.of(active,bench))&&!q2.targets().contains(m.activePokemonView(A).pokemonId()),"Opponent attachment target scope");
                    for(int i=0;i<count;i++){var c=chosen.get(i);boolean toBench=split&&i%2==1;m.assignEffectEnergyDistribution(A,q2.resolutionId(),q2.step(),c.instanceId(),toBench?bench:active);if(!toBench||!guard)applied.add(c);}
                    m.confirmEffectEnergyDistribution(A,q2.resolutionId(),q2.step());CopyAttackTest.reject(()->m.confirmEffectEnergyDistribution(A,q2.resolutionId(),q2.step()));}}
            check(m.privateView(A).discard().equals(beforeA.discard())&&m.privateView(A).hand().equals(beforeA.hand())&&m.privateView(B).discard().size()==beforeB.discard().size()-applied.size()&&m.randomTranscript().equals(random)&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Cross-owner attachment moved wrong assets or stalled");
            for(var c:discard)check(applied.contains(c)?!m.privateView(B).discard().contains(c):m.privateView(B).discard().contains(c),"Protected/unselected Energy left discard");
            m.endTurn(B);m.attack(A,damage.id());int units=applied.stream().mapToInt(c->c.definitionId().equals("special")?2:1).sum();check(m.lastAttack().finalDamage()==units*40,"Grafaiai damage must count live opposing Energy units across Active and Bench");
        }
        System.out.println("ModernOpponentAttachmentRepairTest passed: actual opponent discard, attacker chooses physical Basic/Special Energy, limited 0..3, distribution/protection, ownership/replay and subsequent all-opponent Energy damage");
    }
}
