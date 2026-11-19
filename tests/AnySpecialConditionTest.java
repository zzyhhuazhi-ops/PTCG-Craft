import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/** HAS_STATUS status ANY: “If your opponent's Active Pokémon is affected by a Special Condition, … more damage.” */
public final class AnySpecialConditionTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    static AttackDefinition moreIf(String condition){
        return BeforeDamageAttackTest.attack("","{\"type\":\"conditional\",\"conditions\":["+condition
            +"],\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}]}","");
    }
    static final String ANY="{\"type\":\"HAS_STATUS\",\"target\":\"OPPONENT_ACTIVE\",\"status\":\"ANY\"}";
    public static void main(String[] args){
        anyCondition(); specificStillWorks(); invalidData();
        System.out.println("AnySpecialConditionTest passed: 对方战斗宝可梦陷入任一特殊状态时追加伤害");
    }
    static void anyCondition(){
        var a=moreIf(ANY);
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"任一特殊状态条件被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var clean=BeforeDamageAttackTest.fixture(a);
        clean.attack(A,a.id());
        check(clean.activePokemonView(B).damage()==80,"没有特殊状态时不应追加伤害");
        // 攻击结束后的宝可梦检查会再放中毒／灼伤指示物：与同一夹具、同一状态下没有追加伤害的攻击比较（30 点 × 弱点 2 = 60）
        var plain=BeforeDamageAttackTest.attack("","","");
        for(var condition:SpecialCondition.values()){
            int more=damageAfter(a,condition),base=damageAfter(plain,condition);
            check(more-base==60,condition+" 时没有追加伤害，实际 "+more+"，不追加时 "+base);
        }
        // 只看对方战斗宝可梦：自己的战斗宝可梦有特殊状态不算
        var own=BeforeDamageAttackTest.fixture(a);
        own.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);
        own.attack(A,a.id());
        check(own.activePokemonView(B).damage()==80,"己方战斗宝可梦的特殊状态被当成对方的");
    }
    static int damageAfter(AttackDefinition attack,SpecialCondition condition){
        var m=BeforeDamageAttackTest.fixture(attack);
        m.applySpecialConditionByEffect(A,B,condition);
        m.attack(A,attack.id());
        return m.activePokemonView(B).damage();
    }
    static void specificStillWorks(){
        var a=moreIf("{\"type\":\"HAS_STATUS\",\"target\":\"OPPONENT_ACTIVE\",\"status\":\"BURNED\"}");
        check(damageAfter(a,SpecialCondition.POISONED)==damageAfter(BeforeDamageAttackTest.attack("","",""),SpecialCondition.POISONED),
            "指定灼伤的条件在中毒时成立");
        check(damageAfter(a,SpecialCondition.BURNED)-damageAfter(BeforeDamageAttackTest.attack("","",""),SpecialCondition.BURNED)==60,
            "指定灼伤的条件在灼伤时不成立");
    }
    static void invalidData(){
        check(AttackProgramRules.unsupportedReason(moreIf("{\"type\":\"HAS_STATUS\",\"target\":\"OPPONENT_ACTIVE\",\"status\":\"ANY_CONDITION\"}")).isPresent(),
            "未知的特殊状态名被放行");
        check(AttackProgramRules.unsupportedReason(moreIf("{\"type\":\"HAS_STATUS\",\"target\":\"OPPONENT_BENCH\",\"status\":\"ANY\"}")).isPresent(),
            "任一特殊状态条件读取群体目标被放行");
    }
}
