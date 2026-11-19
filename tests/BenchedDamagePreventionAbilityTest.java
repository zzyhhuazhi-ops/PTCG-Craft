import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/**
 * PASSIVE_PREVENT_ATTACK_DAMAGE_WHILE_BENCHED: “As long as this Pokémon is on your Bench, prevent all damage done to
 * this Pokémon by attacks.” Same scope as the Tera rule: both players' attacks, only while benched.
 */
public final class BenchedDamagePreventionAbilityTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    static AbilityDefinition shell(){
        return new AbilityDefinition("shell-shield","备战区防伤",new AbilityEffect(AbilityEffect.Opcode.PASSIVE_PREVENT_ATTACK_DAMAGE_WHILE_BENCHED,0),
            EffectSupport.SUPPORTED,null,AbilityDefinition.Kind.PASSIVE);
    }
    static AttackDefinition benchHit(String group){
        return BeforeDamageAttackTest.attack("","","{\"type\":\"bench_damage\",\"target\":\""+group+"\",\"amount\":30}");
    }
    public static void main(String[] args){
        opponentBench(); ownBench(); activeStillTakesDamage(); invalidData();
        System.out.println("BenchedDamagePreventionAbilityTest passed: 备战区时不受任何招式伤害的被动特性");
    }
    static int benchDamage(ServerMatch m,UUID owner){
        return m.benchPokemonView(owner).stream().mapToInt(BattlefieldState.PokemonView::damage).sum();
    }
    static void opponentBench(){
        var a=benchHit("OPPONENT_BENCH");
        var plain=BeforeDamageAttackTest.fixture(a);
        plain.attack(A,a.id());
        check(benchDamage(plain,B)==30*plain.benchPokemonView(B).size()&&benchDamage(plain,B)>0,"没有特性时对手备战宝可梦应受到伤害");
        var shielded=BeforeDamageAttackTest.fixture(a,shell());
        shielded.attack(A,a.id());
        check(benchDamage(shielded,B)==0,"有特性的备战宝可梦仍受到对手招式伤害："+benchDamage(shielded,B));
        check(shielded.activePokemonView(B).damage()==80,"战斗宝可梦的伤害被特性挡掉了");
    }
    static void ownBench(){
        var a=benchHit("OWN_BENCH");
        var plain=BeforeDamageAttackTest.fixture(a);
        plain.attack(A,a.id());
        check(benchDamage(plain,A)>0,"没有特性时自己的招式应伤到自己的备战宝可梦");
        var shielded=BeforeDamageAttackTest.fixture(a,shell());
        shielded.attack(A,a.id());
        check(benchDamage(shielded,A)==0,"卡文是“招式”而不只是对手的招式：自己的招式也应被挡掉，实际 "+benchDamage(shielded,A));
    }
    static void activeStillTakesDamage(){
        var a=BeforeDamageAttackTest.attack("","","");
        var m=BeforeDamageAttackTest.fixture(a,shell());
        m.attack(A,a.id());
        check(m.activePokemonView(B).damage()==80,"在战斗位时特性不应生效");
        // 换到备战区后就受保护
        var hit=benchHit("OPPONENT_BENCH");
        var moved=BeforeDamageAttackTest.fixture(hit,shell());
        UUID formerActive=moved.activePokemonView(B).pokemonId();
        moved.endTurn(A);
        moved.switchActiveByEffect(B,B,moved.benchPokemonView(B).getFirst().pokemonId());
        moved.endTurn(B);
        moved.attack(A,hit.id());
        check(moved.benchPokemonView(B).stream().filter(p->p.pokemonId().equals(formerActive)).findFirst().orElseThrow().damage()==0,
            "换到备战区后的宝可梦没有受到保护");
    }
    static void invalidData(){
        boolean rejected=false;
        try{new AbilityEffect(AbilityEffect.Opcode.PASSIVE_PREVENT_ATTACK_DAMAGE_WHILE_BENCHED,10);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"备战区防伤特性接受了数值参数");
        rejected=false;
        try{new AbilityDefinition("shell-shield","备战区防伤",new AbilityEffect(AbilityEffect.Opcode.PASSIVE_PREVENT_ATTACK_DAMAGE_WHILE_BENCHED,0),
            EffectSupport.SUPPORTED,null,AbilityDefinition.Kind.ACTIVATED_ONCE_PER_TURN);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"被动特性操作码被当成主动特性");
    }
}
