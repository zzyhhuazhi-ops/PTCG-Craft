import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernJointDepartureRepairTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static List<CardInstance> dress(ServerMatch m,UUID owner,UUID host){
        var f=EventTargetBindingTest.field(m);var result=new ArrayList<CardInstance>();result.add(f.topCard(owner,host));
        var evolution=ModernRuleRepair2Test.hand(m,owner,"evolved");m.evolveByEffect(owner,host,evolution.instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);result.add(evolution);
        var energy=ModernRuleRepair2Test.hand(m,owner,"energy");m.attachEnergyByEffect(owner,owner,EffectSourceZone.HAND,energy.instanceId(),owner,host);result.add(energy);
        var tool=ModernRuleRepair2Test.hand(m,owner,"tool");m.attachToolByEffect(owner,owner,EffectSourceZone.HAND,tool.instanceId(),owner,host);result.add(tool);return result;
    }
    public static void main(String[] args){butterfree();spidops();System.out.println("ModernJointDepartureRepairTest passed: Butterfree exact opposing Bench and own whole stack, empty Bench, protection and no-replacement loss; Spidops both Active whole stacks, acting seat replaces first, prevention, no-Prize outcome and double-empty draw");}
    static void butterfree(){
        var attack=ModernWindowDamageRepairTest.attack("sv3-5_en_012",1);
        for(boolean opposingBench:List.of(false,true))for(boolean ownBench:List.of(false,true))for(boolean guard:List.of(false,true)){
            var m=ModernShuffleScopeRepairTest.game(attack,0);UUID source=m.activePokemonView(A).pokemonId(),target=m.benchPokemonView(B).getFirst().pokemonId(),opponentActive=m.activePokemonView(B).pokemonId();
            var own=dress(m,A,source);m.endTurn(A);var other=dress(m,B,target);m.endTurn(B);
            if(!opposingBench)m.movePokemonByEffect(A,B,target,LeavePlayDestination.HAND);if(!ownBench)m.movePokemonByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId(),LeavePlayDestination.HAND);
            if(opposingBench&&guard)EventTargetBindingTest.field(m).addProtection(B,B,target,new ProtectionEffect(new EffectProtection(EffectProtection.Source.ATTACK,EffectProtection.Relation.OPPONENT,EffectProtection.Scope.EFFECTS_ONLY),ContinuousEffect.Expiry.LEAVES_PLAY));
            m.attack(A,attack.id());if(opposingBench){var p=m.effectPokemonPrompt(A);check(p.targets().size()==1&&p.targets().getFirst().pokemonId().equals(target),"Butterfree offered wrong side/Active");CopyAttackTest.reject(()->m.chooseEffectPokemon(B,p.resolutionId(),p.step(),target));m.chooseEffectPokemon(A,p.resolutionId(),p.step(),target);CopyAttackTest.reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),target));}
            check(!m.hasPendingEffectChoice()&&m.activePokemonView(B).pokemonId().equals(opponentActive),"Butterfree stalled or removed opponent Active");
            if(!opposingBench){check(m.activePokemonView(A).pokemonId().equals(source)&&m.currentPlayer().equals(B),"Butterfree removed itself with no opponent Bench");continue;}
            check(m.privateView(A).deck().containsAll(own),"Butterfree did not return complete own stack");
            if(!guard)check(m.privateView(B).deck().containsAll(other)&&m.benchPokemonView(B).isEmpty(),"Butterfree lost attachments or failed target departure");else check(m.benchPokemonView(B).size()==1,"Butterfree ignored opposing immunity");
            if(ownBench){check(m.phase()==MatchPhase.CHOOSE_REPLACEMENT,"Butterfree missing own replacement");m.chooseReplacement(A,m.benchPokemonView(A).getFirst().pokemonId());check(m.currentPlayer().equals(B),"Butterfree did not end turn after replacement");}
            else check(m.phase()==MatchPhase.FINISHED,"Butterfree with no own remaining Pokemon did not end match");
            check(m.privateView(A).prizes().size()==6&&m.privateView(B).prizes().size()==6,"Departure incorrectly took prizes");
        }
    }
    static void spidops(){
        var attack=ModernWindowDamageRepairTest.attack("sv2_en_018",0);
        for(boolean ownBench:List.of(false,true))for(boolean opposingBench:List.of(false,true))for(boolean guard:List.of(false,true)){
            var m=ModernShuffleScopeRepairTest.game(attack,0);var own=dress(m,A,m.activePokemonView(A).pokemonId());m.endTurn(A);var other=dress(m,B,m.activePokemonView(B).pokemonId());m.endTurn(B);
            if(!ownBench)m.movePokemonByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId(),LeavePlayDestination.HAND);if(!opposingBench)m.movePokemonByEffect(A,B,m.benchPokemonView(B).getFirst().pokemonId(),LeavePlayDestination.HAND);
            if(guard)EventTargetBindingTest.field(m).addProtection(B,B,m.activePokemonView(B).pokemonId(),new ProtectionEffect(new EffectProtection(EffectProtection.Source.ATTACK,EffectProtection.Relation.OPPONENT,EffectProtection.Scope.EFFECTS_ONLY),ContinuousEffect.Expiry.LEAVES_PLAY));
            m.attack(A,attack.id());check(!m.hasPendingEffectChoice()&&m.privateView(A).deck().containsAll(own),"Spidops missing source stack or stalled");
            if(!guard)check(m.privateView(B).deck().containsAll(other),"Spidops missing opponent stack");
            if(ownBench&&(opposingBench||guard)){
                check(m.phase()==MatchPhase.CHOOSE_REPLACEMENT&&m.clientView(A).choicePlayer().equals(A),"Spidops must choose own replacement first");
                if(!guard)CopyAttackTest.reject(()->m.chooseReplacement(B,m.benchPokemonView(B).getFirst().pokemonId()));
                m.chooseReplacement(A,m.benchPokemonView(A).getFirst().pokemonId());if(!guard)m.chooseReplacement(B,m.benchPokemonView(B).getFirst().pokemonId());
                check(m.currentPlayer().equals(B)&&m.phase()==MatchPhase.TURN,"Spidops failed to resume after replacements");
            }else check(m.phase()==MatchPhase.FINISHED,"Spidops empty remaining side should end match");
            check(m.privateView(A).prizes().size()==6&&m.privateView(B).prizes().size()==6,"Spidops incorrectly took prizes");
        }
    }
}
