import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/**
 * “Discard a Stadium in play.” in attack text: with a Stadium in play it is discarded; with none the attack is
 * still used and only this step does nothing (attack text does as much as it can).
 */
public final class AttackStadiumDiscardTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    public static void main(String[] args){
        var a=BeforeDamageAttackTest.attack("","","{\"type\":\"discard_stadium\"},{\"type\":\"draw\",\"amount\":1}");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"攻击中弃置场地被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var none=BeforeDamageAttackTest.fixture(a);
        int hand=none.privateView(A).hand().size();
        check(none.clientView(null).stadium()==null,"夹具开局就有场地");
        none.attack(A,a.id());
        check(none.activePokemonView(B).damage()==80&&none.privateView(A).hand().size()==hand+1&&none.currentPlayer().equals(B),
            "场上没有场地时招式没有照常使用，或后续文字没有结算");
        var some=BeforeDamageAttackTest.fixture(a);
        CardInstance stadium=DynamicPassiveModifierTest.hand(some,A,"stadium");
        some.playTrainer(A,stadium.instanceId(),null);
        check(some.clientView(null).stadium()!=null,"夹具没能放出场地");
        some.attack(A,a.id());
        check(some.clientView(null).stadium()==null&&some.privateView(A).discard().contains(stadium),"场上的场地没有被弃置到其主人的弃牌区");
        System.out.println("AttackStadiumDiscardTest passed: 招式弃置场地，场上没有场地时招式照常使用");
    }
}
