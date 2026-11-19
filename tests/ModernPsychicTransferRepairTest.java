import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernPsychicTransferRepairTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    public static void main(String[] args){
        var attack=ModernWindowDamageRepairTest.attack("sv3_en_082",0);
        for(int selected:List.of(0,1,4))for(int seed:List.of(0,30,100)){
            var m=ModernEnergyRepairTest.game(BundledCombatData.load().trainer("swsh5_en_124").orElseThrow(),List.of(),attack,EnergyType.PSYCHIC,seed);var f=EventTargetBindingTest.field(m);
            UUID active=m.activePokemonView(A).pokemonId(),bench=m.benchPokemonView(A).getFirst().pokemonId();
            ModernRuleRepair2Test.pay(m,A,active,"energy",1);ModernRuleRepair2Test.pay(m,A,active,"special",1);ModernRuleRepair2Test.pay(m,A,bench,"energy",2);ModernRuleRepair2Test.pay(m,A,bench,"wrong",1);
            var from=new HashMap<UUID,UUID>();for(var host:List.of(active,bench))for(var c:f.attachedEnergy(A,host))if(!c.definitionId().equals("wrong"))from.put(c.instanceId(),host);
            check(from.size()==4,"Transfer fixture energy");m.attack(A,attack.id());var p=m.effectCardPrompt(A);
            check(p.minimum()==0&&p.maximum()==4&&new HashSet<>(p.candidates().stream().map(CardInstance::instanceId).toList()).equals(from.keySet()),"Psychic transfer omitted Special or included wrong Energy");
            var cards=p.candidates().stream().limit(selected).toList();m.submitEffectCards(A,p.resolutionId(),p.step(),cards.stream().map(CardInstance::instanceId).toList());
            if(selected>0){var q=m.effectAttachmentPrompt(A);CopyAttackTest.reject(()->m.confirmEffectEnergyDistribution(A,q.resolutionId(),q.step()));
                for(var c:cards){UUID source=from.get(c.instanceId()),dest=source.equals(active)?bench:active;CopyAttackTest.reject(()->m.assignEffectEnergyDistribution(A,q.resolutionId(),q.step(),c.instanceId(),source));m.assignEffectEnergyDistribution(A,q.resolutionId(),q.step(),c.instanceId(),dest);}
                CopyAttackTest.reject(()->m.confirmEffectEnergyDistribution(B,q.resolutionId(),q.step()));m.confirmEffectEnergyDistribution(A,q.resolutionId(),q.step());CopyAttackTest.reject(()->m.confirmEffectEnergyDistribution(A,q.resolutionId(),q.step()));}
            for(var entry:from.entrySet()){boolean moved=cards.stream().anyMatch(c->c.instanceId().equals(entry.getKey()));UUID expected=moved?(entry.getValue().equals(active)?bench:active):entry.getValue();check(f.attachedEnergy(A,expected).stream().anyMatch(c->c.instanceId().equals(entry.getKey())),"Psychic transfer lost energy or failed two-way swap");}
            check(m.activePokemonView(A).attachedEnergyIds().size()+m.benchPokemonView(A).getFirst().attachedEnergyIds().size()==5&&m.activePokemonView(B).damage()==170&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Transfer duplicated cards, damage or turn");
        }
        System.out.println("ModernPsychicTransferRepairTest passed: optional zero/one/all, Basic and multi-unit Special Energy, wrong-type exclusion, simultaneous two-way swaps, incomplete/stale/other-player requests");
    }
}
