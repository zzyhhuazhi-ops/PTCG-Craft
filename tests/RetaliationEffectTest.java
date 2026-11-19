import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import java.util.*;

/**
 * “During your opponent's next turn, if this Pokémon is damaged by an attack (even if it is Knocked Out), put N damage
 * counters on the Attacking Pokémon.”: retaliate on the attacker itself, a temporary reaction until the effect owner's
 * next turn starts. Also “… put damage counters on the Attacking Pokémon equal to the damage done to this Pokémon.”
 */
public final class RetaliationEffectTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    static dev.hua.ptcglive.engine.field.AttackDefinition retaliate(String args){
        return BeforeDamageAttackTest.attack("","","{\"type\":\"retaliate\",\"target\":\"SELF\","+args+"}");
    }
    public static void main(String[] args){
        countersOnAttacker(); evenIfKnockedOut(); equalToDamageDone(); invalidData();
        System.out.println("RetaliationEffectTest passed: 下个对手回合受到攻击伤害时在攻击方放伤害指示物（即使被击倒；到来源玩家下回合开始失效；与伤害相同的写法）");
    }
    static void countersOnAttacker(){
        var a=retaliate("\"amount\":3");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"受击反伤被攻击规则拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=BeforeDamageAttackTest.fixture(a);
        m.attack(A,a.id());
        check(m.activePokemonView(B).damage()==80,"施加反伤的攻击本身伤害被改变");
        check(m.activePokemonView(A).continuousEffectZh().contains("攻击伤害时"),"使用招式的宝可梦没有得到公开的持续效果");
        m.attack(B,a.id());
        check(m.activePokemonView(A).damage()==80,"对手的攻击伤害被改变");
        // 80（A 的攻击）+ 3 个指示物
        check(m.activePokemonView(B).damage()==110,"攻击方身上没有被放 3 个伤害指示物，实际 "+m.activePokemonView(B).damage());
        check(m.replayTranscript().stream().anyMatch(e->e.operation().contains("受击反伤")),"回放没有记录反伤");
        check(m.currentPlayer().equals(A)&&!m.activePokemonView(A).continuousEffectZh().contains("攻击伤害时"),"效果没有在来源玩家下个回合开始时失效");
        // B 的同一招式在 B 的战斗宝可梦上也挂了反伤；A 这次攻击要被反放 3 个
        m.attack(A,a.id());
        check(m.activePokemonView(A).damage()==110,"对方施加的反伤没有作用在 A 身上，实际 "+m.activePokemonView(A).damage());
    }
    static void evenIfKnockedOut(){
        var a=retaliate("\"amount\":3");
        var m=BeforeDamageAttackTest.fixture(a);
        m.attack(A,a.id());
        UUID doomed=m.activePokemonView(A).pokemonId();
        m.placeDamageCountersByEffect(B,A,doomed,25);
        m.attack(B,a.id());
        boolean stillActive;
        try { stillActive=m.activePokemonView(A).pokemonId().equals(doomed); } catch(IllegalArgumentException|IllegalStateException noActive) { stillActive=false; }
        check(!stillActive&&m.benchPokemonView(A).stream().noneMatch(p->p.pokemonId().equals(doomed)),"受到攻击后 HP 归零的宝可梦没有被击倒");
        check(m.activePokemonView(B).damage()==110,"被击倒时仍应在攻击方身上放伤害指示物，实际 "+m.activePokemonView(B).damage());
    }
    static void equalToDamageDone(){
        var a=retaliate("\"mode\":\"DAMAGE_DONE\"");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"“与受到的伤害相同” 被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=BeforeDamageAttackTest.fixture(a);
        m.attack(A,a.id());
        m.attack(B,a.id());
        // 受到 80 → 放 8 个
        check(m.activePokemonView(B).damage()==80+80,"应放与受到的伤害相同数量的指示物，实际 "+m.activePokemonView(B).damage());
    }
    static void invalidData(){
        for(String bad:List.of(
            "{\"type\":\"retaliate\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":3}",
            "{\"type\":\"retaliate\",\"target\":\"SELF\",\"amount\":0}",
            "{\"type\":\"retaliate\",\"target\":\"SELF\",\"amount\":31}",
            "{\"type\":\"retaliate\",\"target\":\"SELF\",\"amount\":3,\"mode\":\"DAMAGE_DONE\"}",
            "{\"type\":\"retaliate\",\"target\":\"SELF\",\"mode\":\"HALF\"}",
            "{\"type\":\"retaliate\",\"target\":\"SELF\"}"))
            BeforeDamageAttackTest.reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack("","",bad)));
        BeforeDamageAttackTest.reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack("{\"type\":\"retaliate\",\"target\":\"SELF\",\"amount\":3}","","")));
        var trainer=new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.ON_CARD_PLAYED,"{\"type\":\"retaliate\",\"target\":\"SELF\",\"amount\":3}")));
        check(ResumableProgramRules.unsupportedReason(trainer,false).isPresent(),"训练家不能施加受击反伤");
    }
}
