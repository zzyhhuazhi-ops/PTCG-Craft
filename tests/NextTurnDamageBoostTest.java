import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/**
 * boost_attack_damage: “During your next turn, this Pokémon's attacks do N more damage to your opponent's Active Pokémon
 * (before applying Weakness and Resistance).”  increase_damage_taken: “During your next turn, the Defending Pokémon takes
 * N more damage from attacks (after applying Weakness and Resistance).”
 */
public final class NextTurnDamageBoostTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    static AttackDefinition after(String effect){return BeforeDamageAttackTest.attack("","",effect);}
    static final String BOOST="{\"type\":\"boost_attack_damage\",\"target\":\"SELF\",\"amount\":30}";
    static final String VULNERABLE="{\"type\":\"increase_damage_taken\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}";
    public static void main(String[] args){
        boostNextTurn(); boostClearedBySwitch(); vulnerableNextTurn(); invalidData();
        System.out.println("NextTurnDamageBoostTest passed: 下个自己回合招式追加伤害、防守宝可梦多受伤害");
    }
    static int hit(ServerMatch m,UUID attacker,AttackDefinition a){
        UUID defender=attacker.equals(A)?B:A;
        int before=m.activePokemonView(defender).damage();
        m.attack(attacker,a.id());
        return m.activePokemonView(defender).damage()-before;
    }
    static void boostNextTurn(){
        var a=after(BOOST);
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"下回合追加伤害被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=BeforeDamageAttackTest.fixture(a);
        check(hit(m,A,a)==80,"施加的当回合不应追加伤害");
        check(hit(m,B,a)==80,"对手回合不应读到 A 的追加伤害");
        check(hit(m,A,a)==140,"下个自己回合没有在弱点前追加伤害");
        // 只持续一个自己回合：这个回合不攻击，再下一个自己回合就没有了
        var once=BeforeDamageAttackTest.fixture(a);
        hit(once,A,a);
        once.endTurn(B);
        once.endTurn(A);
        once.endTurn(B);
        check(hit(once,A,a)==80,"追加伤害应在下个自己回合结束时失效");
    }
    static void boostClearedBySwitch(){
        var a=after(BOOST);
        var m=BeforeDamageAttackTest.fixture(a);
        UUID first=m.activePokemonView(A).pokemonId();
        hit(m,A,a);
        m.endTurn(B);
        m.switchActiveByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId());
        m.switchActiveByEffect(A,A,first);
        check(hit(m,A,a)==80,"换到备战区再换回来后，招式效果应已清除");
    }
    static void vulnerableNextTurn(){
        var a=after(VULNERABLE);
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"多受伤害被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=BeforeDamageAttackTest.fixture(a);
        check(hit(m,A,a)==80,"施加的当回合不应多受伤害");
        m.endTurn(B);
        check(hit(m,A,a)==110,"下个攻击方回合没有在弱点后多受伤害，实际 "+m.activePokemonView(B).damage());
        var once=BeforeDamageAttackTest.fixture(a);
        hit(once,A,a);
        once.endTurn(B);
        once.endTurn(A);
        once.endTurn(B);
        check(hit(once,A,a)==80,"多受伤害应在攻击方下个回合结束时失效");
    }
    static void invalidData(){
        check(AttackProgramRules.unsupportedReason(after("{\"type\":\"boost_attack_damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}")).isPresent(),
            "追加伤害指定对方宝可梦被放行");
        check(AttackProgramRules.unsupportedReason(after("{\"type\":\"increase_damage_taken\",\"target\":\"SELF\",\"amount\":30}")).isPresent(),
            "多受伤害指定自己被放行");
        check(AttackProgramRules.unsupportedReason(after("{\"type\":\"boost_attack_damage\",\"target\":\"SELF\",\"amount\":25}")).isPresent(),
            "非 10 倍数的追加伤害被放行");
        check(AttackProgramRules.unsupportedReason(BeforeDamageAttackTest.attack(BOOST,"","")).isPresent(),"伤害前施加追加伤害被放行");
    }
}
