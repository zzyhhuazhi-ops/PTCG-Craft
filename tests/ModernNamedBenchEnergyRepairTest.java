import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernNamedBenchEnergyRepairTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    public static void main(String[] args){
        for(String id:List.of("sv3_en_131","sv3_en_204"))for(int hosts:List.of(0,1,2,3))for(boolean take:List.of(false,true)){
            var attack=ModernWindowDamageRepairTest.attack(id,0);
            var m=ModernEnergyRepairTest.game(TrainerPlayLockTest.item(),List.of(),attack,EnergyType.DARKNESS,0,Map.of("basic","Houndour","wrongmon","Houndour ex"));
            if(hosts==0)m.movePokemonByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId(),LeavePlayDestination.HAND);
            for(int i=1;i<hosts;i++)m.playBasicPokemonToBench(A,ModernRuleRepair2Test.hand(m,A,"basic").instanceId());
            m.playBasicPokemonToBench(A,ModernRuleRepair2Test.hand(m,A,"wrongmon").instanceId());
            UUID source=m.activePokemonView(A).pokemonId();ModernRuleRepair2Test.pay(m,A,source,"energy",1);
            var targets=m.benchPokemonView(A).stream().filter(p->p.topCardId().equals("basic")).map(p->p.pokemonId()).toList();
            m.attack(A,attack.id());int count=0;
            if(m.hasPendingEffectChoice()){
                var p=m.effectCardPrompt(A);check(p.minimum()==0&&p.maximum()<=Math.max(1,hosts)&&p.candidates().stream().allMatch(c->c.definitionId().equals("energy")),"Houndour wrong source filter/host capacity");
                var selected=take?p.candidates().stream().limit(hosts).toList():List.<CardInstance>of();count=selected.size();m.submitEffectCards(A,p.resolutionId(),p.step(),selected.stream().map(CardInstance::instanceId).toList());
                if(count>0){var q=m.effectAttachmentPrompt(A);check(new HashSet<>(q.targets()).equals(new HashSet<>(targets)),"Houndour included Active or suffix name");
                    for(int i=0;i<count;i++)m.assignEffectEnergyDistribution(A,q.resolutionId(),q.step(),selected.get(i).instanceId(),targets.get(i));
                    if(count>1){m.assignEffectEnergyDistribution(A,q.resolutionId(),q.step(),selected.get(1).instanceId(),targets.get(0));CopyAttackTest.reject(()->m.confirmEffectEnergyDistribution(A,q.resolutionId(),q.step()));m.assignEffectEnergyDistribution(A,q.resolutionId(),q.step(),selected.get(1).instanceId(),targets.get(1));}
                    m.confirmEffectEnergyDistribution(A,q.resolutionId(),q.step());
                }
            }
            int attached=m.benchPokemonView(A).stream().mapToInt(p->p.attachedEnergyIds().size()).sum();
            check(attached==count&&m.activePokemonView(A).attachedEnergyIds().size()==1&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Houndour duplicate energy, active attachment or stuck turn");
        }
        System.out.println("ModernNamedBenchEnergyRepairTest passed: both Houndour prints, 0-3 exact-name Bench targets, skip/take, Basic Darkness filter and unique host enforcement");
    }
}
