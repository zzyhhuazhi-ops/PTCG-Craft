import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/**
 * BECAME_ACTIVE_THIS_TURN: “If this Pokémon moved from your Bench to the Active Spot this turn, … more damage.”
 * DAMAGE_COUNTER_COUNT on OWN_BENCH: “If your Benched Pokémon have any damage counters on them, … more damage.”
 */
public final class BecameActiveAndBenchCountersConditionTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    static AttackDefinition moreIf(String condition){
        return BeforeDamageAttackTest.attack("","{\"type\":\"conditional\",\"conditions\":["+condition
            +"],\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}]}","");
    }
    static final String MOVED="{\"type\":\"BECAME_ACTIVE_THIS_TURN\",\"target\":\"SELF\"}";
    static final String BENCH_DAMAGED="{\"type\":\"DAMAGE_COUNTER_COUNT\",\"target\":\"OWN_BENCH\",\"value\":1}";
    public static void main(String[] args){
        movedThisTurn(); benchCounters(); invalidData();
        System.out.println("BecameActiveAndBenchCountersConditionTest passed: 本回合换上场、己方备战区有伤害指示物两种追加伤害条件");
    }
    static void movedThisTurn(){
        var a=moreIf(MOVED);
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"本回合换上场条件被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var stayed=BeforeDamageAttackTest.fixture(a);
        stayed.attack(A,a.id());
        check(stayed.activePokemonView(B).damage()==80,"开局就在战斗位的宝可梦不应追加伤害");
        var moved=BeforeDamageAttackTest.fixture(a);
        moved.switchActiveByEffect(A,A,moved.benchPokemonView(A).getFirst().pokemonId());
        moved.attack(A,a.id());
        check(moved.activePokemonView(B).damage()==140,"本回合从备战区换上来的宝可梦没有追加伤害，实际 "+moved.activePokemonView(B).damage());
        // B 的回合：B 的战斗宝可梦不是本回合换上来的；A 上回合换上来的宝可梦对 B 的回合也不算“本回合”
        moved.attack(B,a.id());
        check(moved.activePokemonView(A).damage()==80,"对手的宝可梦没有换上场却追加了伤害");
        moved.attack(A,a.id());
        check(moved.activePokemonView(B).damage()==140+80,"上个自己回合换上来的宝可梦这回合不应再追加伤害");
    }
    static void benchCounters(){
        var a=moreIf(BENCH_DAMAGED);
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"备战区伤害指示物条件被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var clean=BeforeDamageAttackTest.fixture(a);
        clean.attack(A,a.id());
        check(clean.activePokemonView(B).damage()==80,"备战区没有伤害指示物时不应追加伤害");
        var hurt=BeforeDamageAttackTest.fixture(a);
        hurt.placeDamageCountersByEffect(A,A,hurt.benchPokemonView(A).getLast().pokemonId(),1);
        hurt.placeDamageCountersByEffect(A,A,hurt.activePokemonView(A).pokemonId(),5);
        hurt.attack(A,a.id());
        check(hurt.activePokemonView(B).damage()==140,"备战区有伤害指示物时没有追加伤害，实际 "+hurt.activePokemonView(B).damage());
        var activeOnly=BeforeDamageAttackTest.fixture(a);
        activeOnly.placeDamageCountersByEffect(A,A,activeOnly.activePokemonView(A).pokemonId(),5);
        activeOnly.attack(A,a.id());
        check(activeOnly.activePokemonView(B).damage()==80,"只有战斗宝可梦有伤害指示物时不应算作备战区");
    }
    static void invalidData(){
        check(AttackProgramRules.unsupportedReason(moreIf("{\"type\":\"BECAME_ACTIVE_THIS_TURN\",\"target\":\"OWN_BENCH\"}")).isPresent(),
            "本回合换上场条件读取群体目标被放行");
        check(AttackProgramRules.unsupportedReason(moreIf("{\"type\":\"BECAME_ACTIVE_THIS_TURN\",\"target\":\"SELF\",\"value\":1}")).isPresent(),
            "本回合换上场条件的多余参数被放行");
    }
}
