import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernInvitingKissRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static void check(boolean ok,String why){ModernRuleRepair2Test.check(ok,why);}
    public static void main(String[] args){
        var attack=ModernWindowDamageRepairTest.attack("sv6_en_046",0);
        for(int seed:List.of(0,30,80))for(int mode:List.of(0,1,2,3)){
            var m=ModernEnergyRepairTest.game(ModernEnergyRepairTest.DATA.trainer("swsh5_en_124").orElseThrow(),List.of(),attack,EnergyType.WATER,seed);
            UUID source=m.activePokemonView(A).pokemonId();var energy=ModernRuleRepair2Test.hand(m,A,"energy");var special=ModernRuleRepair2Test.hand(m,A,"special");
            m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,energy.instanceId(),A,source);m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,special.instanceId(),A,source);
            if(mode==3)while(m.benchPokemonView(A).size()<5)m.playBasicPokemonToBench(A,ModernRuleRepair2Test.hand(m,A,"basic").instanceId());
            var existing=m.benchPokemonView(A).stream().map(v->v.pokemonId()).toList();m.attack(A,attack.id());
            if(mode!=3){var q=m.effectCardPrompt(A);check(q.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).basicPokemon()),"Search includes evolution/energy");
                var chosen=mode==0?List.<UUID>of():List.of(q.candidates().getFirst().instanceId());
                CopyAttackTest.reject(()->m.submitEffectCards(B,q.resolutionId(),q.step(),chosen));m.submitEffectCards(A,q.resolutionId(),q.step(),chosen);
                if(mode>0){var p=m.effectCardPrompt(A);var moved=mode==1?energy:special;
                    check(p.minimum()==1&&p.maximum()==1&&new HashSet<>(p.candidates()).equals(Set.of(energy,special)),"Must select one actual source Energy card");
                    CopyAttackTest.reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));
                    m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(moved.instanceId()));
                    CopyAttackTest.reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(moved.instanceId())));
                    UUID added=m.benchPokemonView(A).stream().filter(v->!existing.contains(v.pokemonId())).findFirst().orElseThrow().pokemonId();
                    check(EventTargetBindingTest.field(m).attachedEnergy(A,added).equals(List.of(moved)),"Energy moved to wrong target/not new entry");
                    check(EventTargetBindingTest.field(m).attachedEnergy(A,source).size()==1,"Energy copied or count changed");
                }
            }
            check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Inviting Kiss stalled");
            if(mode==0||mode==3)check(EventTargetBindingTest.field(m).attachedEnergy(A,source).size()==2&&m.benchPokemonView(A).size()==existing.size(),"Failed search/full Bench still moved Energy");
        }
        System.out.println("ModernInvitingKissRepairTest passed: 12 cases; Basic-only search, optional failed search, full Bench, exact new entry, mandatory one physical Basic/Special Energy, authority/stale checks");
    }
}
