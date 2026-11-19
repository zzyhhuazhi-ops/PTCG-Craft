import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/**
 * lock_opponent_attack: “Choose 1 of your opponent's Active Pokémon's attacks. During your opponent's next turn, that
 * Pokémon can't use that attack.” The attack is chosen with the declaration from server-issued options.
 */
public final class OpponentAttackLockTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static final String LOCK="{\"type\":\"lock_opponent_attack\",\"target\":\"OPPONENT_ACTIVE\"}";
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    static void reject(Runnable r){BeforeDamageAttackTest.reject(r);}
    public static void main(String[] args){
        lockThroughOpponentTurn(); declarationChecks(); clearedBySwitch(); sessionOptions(); invalidData();
        System.out.println("OpponentAttackLockTest passed: 宣告时选定对方战斗宝可梦的招式，下个对手回合不能使用");
    }
    static void lockThroughOpponentTurn(){
        var a=BeforeDamageAttackTest.attack("","",LOCK);
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"锁定对方招式被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=BeforeDamageAttackTest.fixture(a);
        var options=m.opponentAttackLockOptions(A);
        check(options.size()==1&&options.getFirst().id().equals(a.id()),"可选的对方招式不对："+options);
        m.attackLockingOpponentAttack(A,a.id(),a.id(),null,List.of(),List.of());
        check(m.activePokemonView(B).damage()==80,"锁定招式不应影响本次伤害");
        check(m.currentPlayer().equals(B)&&m.activeAttacks(B).isEmpty(),"对手下个回合仍能使用被锁定的招式");
        reject(()->m.attack(B,a.id()));
        m.endTurn(B);
        m.endTurn(A);
        check(!m.activeAttacks(B).isEmpty(),"锁定应在施加方下个回合开始时失效");
    }
    static void declarationChecks(){
        var a=BeforeDamageAttackTest.attack("","",LOCK);
        var m=BeforeDamageAttackTest.fixture(a);
        var before=m.clientView(A);
        reject(()->m.attack(A,a.id()));
        reject(()->m.attackLockingOpponentAttack(A,a.id(),"no-such-attack",null,List.of(),List.of()));
        check(before.equals(m.clientView(A)),"非法的锁定选择在拒绝前改变了状态");
        var plain=BeforeDamageAttackTest.attack("","","");
        var other=BeforeDamageAttackTest.fixture(plain);
        reject(()->other.attackLockingOpponentAttack(A,plain.id(),plain.id(),null,List.of(),List.of()));
    }
    static void clearedBySwitch(){
        var a=BeforeDamageAttackTest.attack("","",LOCK);
        var m=BeforeDamageAttackTest.fixture(a);
        m.attackLockingOpponentAttack(A,a.id(),a.id(),null,List.of(),List.of());
        UUID first=m.activePokemonView(B).pokemonId();
        m.switchActiveByEffect(B,B,m.benchPokemonView(B).getFirst().pokemonId());
        m.switchActiveByEffect(B,B,first);
        check(!m.activeAttacks(B).isEmpty(),"换到备战区再换回来后，招式效果应已清除");
    }
    static void sessionOptions(){
        var a=BeforeDamageAttackTest.attack("","",LOCK);
        var m=BeforeDamageAttackTest.fixture(a);
        var session=MatchSession.multiplayer(m,A,B);
        var labels=session.view(A,A).options().stream().map(MatchSession.Option::labelZh).toList();
        check(labels.stream().anyMatch(l->l.startsWith("攻击：")&&l.contains("锁定对方招式：")),"对战桌没有给出锁定对方招式的选项："+labels);
    }
    static void invalidData(){
        check(AttackProgramRules.unsupportedReason(BeforeDamageAttackTest.attack("","","{\"type\":\"lock_opponent_attack\",\"target\":\"SELF\"}")).isPresent(),
            "锁定对方招式指定自己被放行");
        check(AttackProgramRules.unsupportedReason(BeforeDamageAttackTest.attack(LOCK,"","")).isPresent(),"伤害前锁定对方招式被放行");
    }
}
