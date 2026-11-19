import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernCoinEnergyReturnTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    public static void main(String[] args){
        var attack=ModernWindowDamageRepairTest.attack("sv4_en_002",0);Set<Integer> branches=new HashSet<>();
        for(int seed=0;seed<32;seed++)for(int count:List.of(0,1,3))for(boolean guard:List.of(false,true)){
            var m=ModernWindowDamageRepairTest.game(attack,seed,false);m.drawCardsByEffect(A,B,20);UUID target=m.activePokemonView(B).pokemonId();
            var energies=m.privateView(B).hand().stream().filter(c->m.cardDefinition(c.definitionId()).category()==CardCategory.ENERGY).limit(count).toList();
            check(energies.size()==count,"Coin energy fixture");for(var c:energies)m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,c.instanceId(),B,target);
            if(guard)EventTargetBindingTest.field(m).addProtection(B,B,target,new ProtectionEffect(new EffectProtection(EffectProtection.Source.ATTACK,EffectProtection.Relation.OPPONENT,EffectProtection.Scope.EFFECTS_ONLY),ContinuousEffect.Expiry.LEAVES_PLAY));
            int rng=m.randomTranscript().size();m.attack(A,attack.id());
            var coins=m.randomTranscript().subList(rng,m.randomTranscript().size());int heads=(int)coins.stream().filter(c->c.bound()==2&&c.result()==0).count();branches.add(heads);
            int selected=Math.min(count,heads);List<CardInstance> chosen=List.of();
            if(m.hasPendingEffectChoice()){
                var p=m.effectCardPrompt(A);check(selected>0&&p.minimum()==selected&&p.maximum()==selected&&new HashSet<>(p.candidates()).equals(new HashSet<>(energies)),"Coin return wrong required amount/candidates");
                chosen=p.candidates().stream().limit(selected).toList();var ids=chosen.stream().map(CardInstance::instanceId).toList();
                CopyAttackTest.reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),ids));CopyAttackTest.reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));
                m.submitEffectCards(A,p.resolutionId(),p.step(),ids);CopyAttackTest.reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),ids));
            }else check(selected==0||guard,"Nonzero coins skipped selection");
            int returned=guard?0:selected;check(m.activePokemonView(B).attachedEnergyIds().size()==count-returned&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Coin return counts/protection/continuation");
            if(!guard)for(var c:chosen)check(m.privateView(B).deck().contains(c)||m.privateView(B).hand().contains(c),"Returned energy lost");
            long shuffles=m.randomTranscript().subList(rng,m.randomTranscript().size()).stream().filter(r->r.operation().contains("energy")&&!r.operation().contains("coin")).count();
            if(returned==0)check(shuffles==0,"No returned energy must not shuffle");
        }
        check(branches.contains(0)&&branches.stream().anyMatch(n->n>=3),"Coin fixture needs zero and multiple heads");
        System.out.println("ModernCoinEnergyReturnTest passed: 192 until-tails scenarios, actual Energy cards including Special, mandatory clipped count, effect protection and no duplicate requests");
    }
}
