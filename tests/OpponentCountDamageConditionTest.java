import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/**
 * Damage-phase comparison with the opponent's count: “If you have the same number of cards in your hand as your
 * opponent, this attack does N more damage.” (HAND_SIZE, compare_to OPPONENT, comparison EQUALS).
 */
public final class OpponentCountDamageConditionTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    public static void main(String[] args){
        var a=BeforeDamageAttackTest.attack("","{\"type\":\"conditional\",\"conditions\":[{\"type\":\"HAND_SIZE\",\"target\":\"SELF\",\"compare_to\":\"OPPONENT\","
            +"\"comparison\":\"EQUALS\"}],\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}]}","");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"手牌数与对手相同的条件被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var differ=BeforeDamageAttackTest.fixture(a);
        int mine=differ.privateView(A).hand().size(),theirs=differ.privateView(B).hand().size();
        check(mine!=theirs,"夹具双方手牌数已经相同");
        differ.attack(A,a.id());
        check(differ.activePokemonView(B).damage()==80,"手牌数不同时不应追加伤害");
        var same=BeforeDamageAttackTest.fixture(a);
        int extra=same.privateView(A).hand().size()-same.privateView(B).hand().size();
        if(extra>0)same.discardRandomHandCardsByEffect(A,A,extra);else same.drawCardsByEffect(A,A,-extra);
        check(same.privateView(A).hand().size()==same.privateView(B).hand().size(),"没能把双方手牌调成相同");
        same.attack(A,a.id());
        check(same.activePokemonView(B).damage()==140,"手牌数相同时没有在伤害计算中追加（再算弱点），实际 "+same.activePokemonView(B).damage());
        System.out.println("OpponentCountDamageConditionTest passed: 伤害计算阶段可以和对手的手牌数比较");
    }
}
