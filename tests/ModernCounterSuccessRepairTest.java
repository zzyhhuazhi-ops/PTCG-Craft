import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

public final class ModernCounterSuccessRepairTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static void hurt(ServerMatch m,UUID owner,UUID target,int counters){while(counters>0){int n=Math.min(60,counters);m.placeDamageCountersByEffect(A,owner,target,n);counters-=n;}}
    public static void main(String[] args){
        var attack=ModernWindowDamageRepairTest.attack("sv3_en_095",0);
        for(int remaining:List.of(10,20,500,2000))for(boolean guard:List.of(false,true))for(boolean lethal:List.of(false,true))for(boolean weak:List.of(false,true)){
            var m=ModernWindowDamageRepairTest.game(attack,0,weak);UUID source=m.activePokemonView(A).pokemonId(),target=m.activePokemonView(B).pokemonId();
            hurt(m,B,target,(2000-remaining)/10);if(lethal)hurt(m,A,source,189);
            if(guard)EventTargetBindingTest.field(m).addProtection(B,B,target,new ProtectionEffect(new EffectProtection(EffectProtection.Source.ATTACK,EffectProtection.Relation.OPPONENT,EffectProtection.Scope.EFFECTS_ONLY),ContinuousEffect.Expiry.LEAVES_PLAY));
            // An effects-only shield never prevents the attack's printed self DAMAGE.
            EventTargetBindingTest.field(m).addProtection(A,A,source,new ProtectionEffect(new EffectProtection(EffectProtection.Source.ATTACK,EffectProtection.Relation.OWN,EffectProtection.Scope.EFFECTS_ONLY),ContinuousEffect.Expiry.LEAVES_PLAY));
            var physical=EventTargetBindingTest.field(m).topCard(A,source);m.attack(A,attack.id());boolean applied=!guard&&remaining>10;
            check(m.activePokemonView(B).damage()==(guard?2000-remaining:1990),"Claydol counter amount or prevention");
            if(lethal&&applied)check(m.privateView(A).discard().contains(physical)&&m.phase()==MatchPhase.PRIZE_SELECTION,"Claydol self KO/prize timing");
            else check(m.activePokemonView(A).damage()==(lethal?1890:0)+(applied?120*(weak?2:1):0)&&m.currentPlayer().equals(B),"Claydol conditional self damage, weakness or shield incorrectly applied");
            check(!m.hasPendingEffectChoice(),"Claydol stuck effect");
        }
        System.out.println("ModernCounterSuccessRepairTest passed: Claydol actual placement, 10HP no-op, prevention, large counter counts, self damage and self-KO timing");
    }
}
