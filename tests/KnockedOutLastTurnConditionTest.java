import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/**
 * KNOCKED_OUT_BY_ATTACK_LAST_TURN: “If any of your Pokémon were Knocked Out by damage from an attack during your
 * opponent's last turn, this attack does N more damage.”
 */
public final class KnockedOutLastTurnConditionTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    static AttackDefinition moreIf(String target){
        return BeforeDamageAttackTest.attack("","{\"type\":\"conditional\",\"conditions\":[{\"type\":\"KNOCKED_OUT_BY_ATTACK_LAST_TURN\",\"target\":\""
            +target+"\"}],\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}]}","");
    }
    public static void main(String[] args){
        afterAttackKnockout(); notAfterCounterKnockout(); invalidData();
        System.out.println("KnockedOutLastTurnConditionTest passed: 上个对手回合己方宝可梦被招式伤害击倒时追加伤害");
    }
    /** B knocks out A's Active with attack damage; on A's next turn the condition holds, one turn later it no longer does. */
    static void afterAttackKnockout(){
        var a=moreIf("SELF");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"被击倒条件被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var plain=BeforeDamageAttackTest.fixture(a);
        plain.attack(A,a.id());
        check(plain.activePokemonView(B).damage()==80,"没有被击倒过时不应追加伤害");
        var m=BeforeDamageAttackTest.fixture(a);
        m.endTurn(A);
        m.placeDamageCountersByEffect(B,A,m.activePokemonView(A).pokemonId(),22);
        m.attack(B,a.id());
        m.takePrizeAt(B,0);
        m.chooseReplacement(A,m.benchPokemonView(A).getFirst().pokemonId());
        check(m.currentPlayer().equals(A),"击倒后没有轮到 A");
        int before=m.activePokemonView(B).damage();
        m.attack(A,a.id());
        check(m.activePokemonView(B).damage()-before==140,"上个对手回合被招式击倒后没有追加伤害，实际 "+(m.activePokemonView(B).damage()-before));
        // B 的回合：B 的宝可梦上个回合没有被击倒
        int mine=m.activePokemonView(A).damage();
        m.attack(B,a.id());
        check(m.activePokemonView(A).damage()-mine==80,"对手没有被击倒却追加了伤害");
        // 再下一个 A 回合：上个对手回合（B 的回合）没有击倒，条件不再成立
        int later=m.activePokemonView(B).damage();
        m.attack(A,a.id());
        check(m.activePokemonView(B).damage()-later==80,"两个回合之前的击倒不应再算");
    }
    /** Knocked Out by damage counters from an effect is not “Knocked Out by damage from an attack”. */
    static void notAfterCounterKnockout(){
        var a=moreIf("SELF");
        var m=BeforeDamageAttackTest.fixture(a);
        m.endTurn(A);
        m.placeDamageCountersByEffect(B,A,m.activePokemonView(A).pokemonId(),30);
        m.takePrizeAt(B,0);
        m.chooseReplacement(A,m.benchPokemonView(A).getFirst().pokemonId());
        if(m.currentPlayer().equals(B))m.endTurn(B);
        int before=m.activePokemonView(B).damage();
        m.attack(A,a.id());
        check(m.activePokemonView(B).damage()-before==80,"被效果放伤害指示物击倒不应算作“被招式伤害击倒”");
    }
    static void invalidData(){
        check(AttackProgramRules.unsupportedReason(moreIf("OWN_BENCH")).isEmpty(),"被击倒条件未能读取己方备战区群体记录");
        BeforeDamageAttackTest.reject(()->BeforeDamageAttackTest.attack("","{\"type\":\"conditional\",\"conditions\":[{\"type\":"
            +"\"KNOCKED_OUT_BY_ATTACK_LAST_TURN\",\"target\":\"SELF\",\"value\":1}],\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}]}",""));
    }
}
