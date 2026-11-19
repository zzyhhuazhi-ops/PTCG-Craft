import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/** HEALED_THIS_TURN: “If this Pokémon was healed during this turn, this attack does N more damage.” */
public final class HealedThisTurnConditionTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    static AttackDefinition moreIf(String condition){
        return BeforeDamageAttackTest.attack("","{\"type\":\"conditional\",\"conditions\":["+condition
            +"],\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}]}","");
    }
    static final String HEALED="{\"type\":\"HEALED_THIS_TURN\",\"target\":\"SELF\"}";
    public static void main(String[] args){
        healedThisTurn(); notHealing(); invalidData();
        System.out.println("HealedThisTurnConditionTest passed: 这只宝可梦本回合被回复过时追加伤害（移动伤害指示物不算回复）");
    }
    static void healedThisTurn(){
        var a=moreIf(HEALED);
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"本回合被回复条件被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var plain=BeforeDamageAttackTest.fixture(a);
        plain.attack(A,a.id());
        check(plain.activePokemonView(B).damage()==80,"没有被回复时不应追加伤害");
        var healed=BeforeDamageAttackTest.fixture(a);
        UUID active=healed.activePokemonView(A).pokemonId();
        healed.placeDamageCountersByEffect(A,A,active,3);
        healed.healDamageByEffect(A,A,active,10);
        check(healed.activePokemonView(A).damage()==20,"回复量不对");
        healed.attack(A,a.id());
        check(healed.activePokemonView(B).damage()==140,"本回合被回复过却没有追加伤害，实际 "+healed.activePokemonView(B).damage());
        // 上个自己回合被回复过，这个回合不算
        var earlier=BeforeDamageAttackTest.fixture(a);
        UUID mine=earlier.activePokemonView(A).pokemonId();
        earlier.placeDamageCountersByEffect(A,A,mine,3);
        earlier.healDamageByEffect(A,A,mine,10);
        earlier.endTurn(A);
        earlier.endTurn(B);
        earlier.attack(A,a.id());
        check(earlier.activePokemonView(B).damage()==80,"上个回合的回复不应算作本回合");
    }
    static void notHealing(){
        var a=moreIf(HEALED);
        // 回复的是备战宝可梦：攻击者本身没有被回复
        var bench=BeforeDamageAttackTest.fixture(a);
        UUID benched=bench.benchPokemonView(A).getFirst().pokemonId();
        bench.placeDamageCountersByEffect(A,A,benched,3);
        bench.healDamageByEffect(A,A,benched,10);
        bench.attack(A,a.id());
        check(bench.activePokemonView(B).damage()==80,"备战宝可梦被回复却算到攻击者头上");
        // 把伤害指示物从攻击者身上移走不是回复
        var moved=BeforeDamageAttackTest.fixture(a);
        UUID active=moved.activePokemonView(A).pokemonId();
        moved.placeDamageCountersByEffect(A,A,active,3);
        moved.moveDamageCountersByEffect(A,A,active,A,moved.benchPokemonView(A).getFirst().pokemonId(),2);
        check(moved.activePokemonView(A).damage()==10,"伤害指示物没有移走");
        moved.attack(A,a.id());
        check(moved.activePokemonView(B).damage()==80,"移动伤害指示物被当成了回复");
    }
    static void invalidData(){
        check(AttackProgramRules.unsupportedReason(moreIf("{\"type\":\"HEALED_THIS_TURN\",\"target\":\"OWN_BENCH\"}")).isPresent(),
            "本回合被回复条件读取群体目标被放行");
        check(AttackProgramRules.unsupportedReason(moreIf("{\"type\":\"HEALED_THIS_TURN\",\"target\":\"SELF\",\"value\":1}")).isPresent(),
            "本回合被回复条件的多余参数被放行");
    }
}
