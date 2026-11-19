import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import java.util.*;

/**
 * “During your opponent's next turn, the Defending Pokémon's attacks do N less damage (before applying Weakness
 * and Resistance).”: reduce_attack_damage on the opponent's Active, owned by the attacking player.
 */
public final class AttackDamagePenaltyTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    static dev.hua.ptcglive.engine.field.AttackDefinition weaken(int amount){
        return BeforeDamageAttackTest.attack("","","{\"type\":\"reduce_attack_damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":"+amount+"}");
    }
    public static void main(String[] args) {
        beforeWeaknessAndExpiry(); bigPenaltyFloorsAtZero(); invalidData();
        System.out.println("AttackDamagePenaltyTest passed: 防守宝可梦下个回合攻击伤害减少（弱点前计算、到来源玩家下回合开始失效、不低于 0）");
    }
    static void beforeWeaknessAndExpiry() {
        var a=weaken(20);
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"攻击伤害减少被攻击规则拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=BeforeDamageAttackTest.fixture(a);
        m.attack(A,a.id());
        check(m.activePokemonView(B).damage()==80,"施加减伤的攻击本身伤害被改变");
        check(m.activePokemonView(B).continuousEffectZh().contains("攻击伤害减少 20"),"防守宝可梦没有得到公开的持续效果");
        check(!m.activePokemonView(A).continuousEffectZh().contains("攻击伤害减少"),"效果挂到了攻击者身上");
        m.attack(B,a.id());
        // 40 − 20 = 20，再算弱点 ×2 = 40；若在弱点之后减则是 60，没减则是 80
        check(m.activePokemonView(A).damage()==40,"减伤没有在弱点和抵抗力之前计算，实际伤害 "+m.activePokemonView(A).damage());
        check(!m.activePokemonView(B).continuousEffectZh().contains("攻击伤害减少"),"效果没有在来源玩家下个回合开始时失效");
        // B 用的是同一招式，A 的战斗宝可梦也被挂上了 B 拥有的减伤，到 B 下个回合开始前有效
        check(m.activePokemonView(A).continuousEffectZh().contains("攻击伤害减少 20"),"对方施加的减伤没有挂到 A 的战斗宝可梦");
        m.attack(A,a.id());
        check(m.activePokemonView(B).damage()==80+40,"A 的攻击没有按对方施加的减伤计算，实际 "+m.activePokemonView(B).damage());
    }
    static void bigPenaltyFloorsAtZero() {
        var a=weaken(60);
        var m=BeforeDamageAttackTest.fixture(a);
        m.attack(A,a.id());
        m.attack(B,a.id());
        check(m.activePokemonView(A).damage()==0,"攻击伤害减到负数后没有按 0 处理");
    }
    static void invalidData() {
        for(var invalid:List.of(
            BeforeDamageAttackTest.attack("","","{\"type\":\"reduce_attack_damage\",\"target\":\"SELF\",\"amount\":20}"),
            BeforeDamageAttackTest.attack("","","{\"type\":\"reduce_attack_damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":25}"),
            BeforeDamageAttackTest.attack("{\"type\":\"reduce_attack_damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":20}","","")))
            check(AttackProgramRules.unsupportedReason(invalid).isPresent(),"非法攻击伤害减少（目标、数值或时机）被放行");
        var trainer=new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.ON_CARD_PLAYED,
            "{\"type\":\"reduce_attack_damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":20}")));
        check(ResumableProgramRules.unsupportedReason(trainer,false).isPresent(),"训练家不能施加“防守宝可梦”效果");
    }
}
