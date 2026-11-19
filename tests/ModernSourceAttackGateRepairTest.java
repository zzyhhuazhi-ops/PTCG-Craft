import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernSourceAttackGateRepairTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static void copy(ServerMatch m,String id){var o=m.copyAttackOptions(A,"copy").stream().filter(c->c.attackId().equals(id)).findFirst().orElseThrow();check(o.effectiveAttack()!=null,"Copy unexpectedly unavailable: "+o.unsupportedReasonZh());m.copyAttackWithEnergyDiscards(A,"copy",o.pokemonId(),o.entrySequence(),id,List.of(),List.of());}
    public static void main(String[] args){
        var profile=BundledCombatData.load().pokemon("sv2_en_073").orElseThrow();var stun=profile.attacks().getFirst();var follow=profile.attacks().get(1);AttackProgramRules.validate(follow);
        for(boolean copiedFirst:List.of(false,true))for(boolean copiedFollow:List.of(false,true))for(String mode:List.of("SAME","OTHER_HOST","ROUND_TRIP","REENTER","SKIPPED")){
            var m=ModernWindowDamageRepairTest.game(follow,0,false,List.of(stun));UUID source=m.activePokemonView(A).pokemonId(),backup=m.benchPokemonView(A).getFirst().pokemonId();
            var before=m.privateView(A);var rng=m.randomTranscript();check(m.activeAttacks(A).stream().noneMatch(a->a.id().equals(follow.id())),"Follow-up offered without prior attack");CopyAttackTest.reject(()->m.attack(A,follow.id()));
            check(m.privateView(A).equals(before)&&m.randomTranscript().equals(rng)&&m.currentPlayer().equals(A),"Rejected follow-up consumed state or RNG");
            if(copiedFirst)copy(m,stun.id());else m.attack(A,stun.id());m.endTurn(B);
            if(mode.equals("OTHER_HOST"))m.switchActiveByEffect(A,A,backup);
            if(mode.equals("ROUND_TRIP")){m.switchActiveByEffect(A,A,backup);m.switchActiveByEffect(A,A,source);}
            if(mode.equals("SKIPPED")){m.endTurn(A);m.endTurn(B);}
            if(mode.equals("REENTER")){
                var card=EventTargetBindingTest.field(m).topCard(A,source);m.switchActiveByEffect(A,A,backup);m.movePokemonByEffect(A,A,source,LeavePlayDestination.HAND);m.playBasicPokemonToBench(A,card.instanceId());m.switchActiveByEffect(A,A,m.benchPokemonView(A).getLast().pokemonId());}
            boolean allowed=Set.of("SAME","ROUND_TRIP").contains(mode);check(m.activeAttacks(A).stream().anyMatch(a->a.id().equals(follow.id()))==allowed,"Follow-up used player-wide or stale source history: "+mode);
            var option=m.copyAttackOptions(A,"copy").stream().filter(o->o.attackId().equals(follow.id())).findFirst().orElseThrow();check((option.effectiveAttack()!=null)==allowed,"Copy gate read provider history instead of actual attacker: "+mode);
            if(allowed){if(copiedFollow)copy(m,follow.id());else m.attack(A,follow.id());check(m.lastAttack().finalDamage()==100&&m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Follow-up failed actual damage or turn completion");}
            else {var state=m.privateView(A);var random=m.randomTranscript();CopyAttackTest.reject(()->m.attack(A,follow.id()));check(m.privateView(A).equals(state)&&m.randomTranscript().equals(random),"Blocked follow-up modified state");}
        }
        // Opponent's attack never supplies the current player's source history.
        var m=ModernWindowDamageRepairTest.game(follow,0,false,List.of(stun));m.drawCardsByEffect(A,B,16);ModernRuleRepair2Test.pay(m,B,m.activePokemonView(B).pokemonId(),"energy",1);m.endTurn(A);m.attack(B,stun.id());check(m.activeAttacks(A).stream().noneMatch(a->a.id().equals(follow.id())),"Opponent history unlocked follow-up");
        System.out.println("ModernSourceAttackGateRepairTest passed: Pincurchin native/copy source and entry identity, prior own turn, no stale permission after reentry/skipped turn, no mutation on illegal declaration");
    }
}
