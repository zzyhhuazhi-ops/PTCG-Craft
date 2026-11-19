import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import java.util.*;

/** lock_this_attack: “This Pokémon can't use [this attack] again until it leaves the Active Spot.” */
public final class AttackLockUntilLeavesActiveTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    public static void main(String[] args){
        var a=BeforeDamageAttackTest.attack("","","{\"type\":\"lock_this_attack\",\"target\":\"SELF\"}");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"锁定本招式被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=BeforeDamageAttackTest.fixture(a);
        UUID first=m.activePokemonView(A).pokemonId();
        m.attack(A,a.id());
        check(m.activePokemonView(A).continuousEffectZh().contains("离开战斗位前不能再使用攻击"),"攻击者没有得到公开的锁定效果");
        m.endTurn(B);
        check(m.activeAttacks(A).stream().noneMatch(x->x.id().equals(a.id())),"下个回合仍然可以使用被锁定的招式");
        BeforeDamageAttackTest.reject(()->m.attack(A,a.id()));
        m.endTurn(A);m.endTurn(B);
        check(m.activeAttacks(A).stream().noneMatch(x->x.id().equals(a.id())),"锁定不应随回合结束失效");
        UUID bench=m.benchPokemonView(A).getFirst().pokemonId();
        m.switchActiveByEffect(A,A,bench);
        check(m.activeAttacks(A).stream().anyMatch(x->x.id().equals(a.id())),"换上来的另一只宝可梦不应被锁定");
        m.switchActiveByEffect(A,A,first);
        check(m.activeAttacks(A).stream().anyMatch(x->x.id().equals(a.id())),"离开过战斗位后锁定应已解除");
        for(String bad:List.of("{\"type\":\"lock_this_attack\",\"target\":\"OPPONENT_ACTIVE\"}","{\"type\":\"lock_this_attack\",\"target\":\"SELF\",\"amount\":1}"))
            BeforeDamageAttackTest.reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack("","",bad)));
        check(AttackProgramRules.unsupportedReason(BeforeDamageAttackTest.attack("{\"type\":\"lock_this_attack\",\"target\":\"SELF\"}","","")).isPresent(),
            "伤害前锁定本招式被放行");
        System.out.println("AttackLockUntilLeavesActiveTest passed: 离开战斗位前不能再使用同名招式");
    }
}
