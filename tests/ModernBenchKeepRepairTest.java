import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

public final class ModernBenchKeepRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static void check(boolean ok,String why){ModernRuleRepair2Test.check(ok,why);}
    public static void main(String[] args){
        for(String id:List.of("sv5_en_005","sv5_en_163"))for(int count:List.of(0,1,3,4,5))for(boolean guard:List.of(false,true)){
            var attack=ModernWindowDamageRepairTest.attack(id,0);var m=ModernShuffleScopeRepairTest.game(attack,0);
            ModernShuffleScopeRepairTest.pay(m,A,m.activePokemonView(A).pokemonId());m.endTurn(A);
            while(m.benchPokemonView(B).size()<count)m.playBasicPokemonToBench(B,ModernRuleRepair2Test.hand(m,B,"basic").instanceId());
            var bench=m.benchPokemonView(B).stream().map(v->v.pokemonId()).toList();
            List<dev.hua.ptcglive.rules.CardInstance> stack=count>3?ModernJointDepartureRepairTest.dress(m,B,bench.getLast()):List.of();
            m.endTurn(B);if(count==0)m.movePokemonByEffect(A,B,bench.getFirst(),LeavePlayDestination.HAND);
            if(count>3&&guard)EventTargetBindingTest.field(m).addProtection(B,B,bench.getLast(),new ProtectionEffect(new EffectProtection(EffectProtection.Source.ATTACK,EffectProtection.Relation.OPPONENT,EffectProtection.Scope.EFFECTS_ONLY),ContinuousEffect.Expiry.LEAVES_PLAY));
            UUID active=m.activePokemonView(B).pokemonId();int prizes=m.privateView(A).prizes().size();m.attack(A,attack.id());
            if(count>3){
                for(int i=0;i<3;i++){
                    var p=m.effectPokemonPrompt(A);check(!p.canFinish()&&p.targets().size()==count-i,"Keep-three candidates/count incorrect");
                    UUID chosen=bench.get(i);CopyAttackTest.reject(()->m.chooseEffectPokemon(B,p.resolutionId(),p.step(),chosen));
                    CopyAttackTest.reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),active));
                    m.chooseEffectPokemon(A,p.resolutionId(),p.step(),chosen);
                    CopyAttackTest.reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),chosen));
                }
                check(m.benchPokemonView(B).size()==(guard?4:3),"Incorrect unchosen Bench departure");
                check(m.benchPokemonView(B).stream().map(v->v.pokemonId()).toList().containsAll(bench.subList(0,3)),"Kept target left play");
                if(!guard)check(m.privateView(B).deck().containsAll(stack),"Whole evolution/energy/tool stack missing");
                else check(!m.privateView(B).deck().contains(stack.getFirst()),"Ignored immunity");
            }else check(m.benchPokemonView(B).size()==count,"Small Bench changed");
            check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B)&&m.activePokemonView(B).pokemonId().equals(active),"Selection stalled/Active removed");
            check(m.privateView(A).prizes().size()==prizes,"Shuffle awarded prizes");
        }
        System.out.println("ModernBenchKeepRepairTest passed: 20 cases, both prints, 0/1/3/4/5 Bench, attacker chooses three, whole stacks, immunity, authority/replay checks");
    }
}
