import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import java.util.*;

/**
 * “This attack (also) does N damage to each of your (opponent's) Benched Pokémon.”: bench_damage with the
 * fixed group OWN_BENCH / OPPONENT_BENCH. Every current Benched Pokémon takes attack damage without
 * Weakness/Resistance, nobody chooses a target, and Knock Outs use the attack's normal checkpoint.
 */
public final class GroupBenchDamageTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    static dev.hua.ptcglive.engine.field.AttackDefinition spread(String target,int amount){
        return BeforeDamageAttackTest.attack("","","{\"type\":\"bench_damage\",\"target\":\""+target+"\",\"amount\":"+amount+"}");
    }
    public static void main(String[] args) {
        opponentBench(); ownBench(); knockOutAwardsPrize(); invalidData();
        System.out.println("GroupBenchDamageTest passed: 己方／对方全部备战宝可梦各受攻击伤害、不计弱点、不需选择、击倒照常给奖");
    }
    static void opponentBench() {
        var a=spread("OPPONENT_BENCH",20);
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"对方全体备战伤害被攻击规则拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=BeforeDamageAttackTest.fixture(a);
        check(!m.benchPokemonView(B).isEmpty(),"夹具对方没有备战宝可梦");
        m.attack(A,a.id());
        check(m.activePokemonView(B).damage()==80,"战斗宝可梦的攻击伤害（含弱点）被改变");
        check(m.benchPokemonView(B).stream().allMatch(p->p.damage()==20),"对方每只备战宝可梦都应受到 20 点且不计弱点");
        check(m.benchPokemonView(A).stream().allMatch(p->p.damage()==0)&&m.activePokemonView(A).damage()==0,"己方宝可梦不应受伤");
        check(m.currentPlayer().equals(B),"全体备战伤害不应停下来要求选择目标");
    }
    static void ownBench() {
        var a=spread("OWN_BENCH",30);
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"己方全体备战伤害被攻击规则拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=BeforeDamageAttackTest.fixture(a);
        check(!m.benchPokemonView(A).isEmpty(),"夹具己方没有备战宝可梦");
        m.attack(A,a.id());
        check(m.activePokemonView(B).damage()==80,"对方战斗宝可梦伤害被改变");
        check(m.benchPokemonView(A).stream().allMatch(p->p.damage()==30),"己方每只备战宝可梦都应受到 30 点");
        check(m.activePokemonView(A).damage()==0,"攻击者自己不在备战区，不应受伤");
        check(m.benchPokemonView(B).stream().allMatch(p->p.damage()==0),"对方备战不应受伤");
    }
    static void knockOutAwardsPrize() {
        var a=spread("OPPONENT_BENCH",20);
        var m=BeforeDamageAttackTest.fixture(a);
        var bench=m.benchPokemonView(B);
        UUID doomed=bench.getFirst().pokemonId();
        m.placeDamageCountersByEffect(A,B,doomed,29);
        int before=bench.size();
        m.attack(A,a.id());
        check(m.benchPokemonView(B).stream().noneMatch(p->p.pokemonId().equals(doomed)),"受到全体备战伤害后 HP 归零的宝可梦没有被击倒");
        check(m.benchPokemonView(B).size()==before-1,"其他备战宝可梦不应被移除");
        check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.pendingChoicePlayer().equals(A),"击倒备战宝可梦后应由攻击方拿奖赏");
        m.takePrizeAt(A,0);
        check(m.benchPokemonView(B).stream().allMatch(p->p.damage()==20),"其余备战宝可梦应各受 20 点");
    }
    static void invalidData() {
        for(var invalid:List.of(
            BeforeDamageAttackTest.attack("","","{\"type\":\"bench_damage\",\"target\":\"OWN_ACTIVE\",\"amount\":20}"),
            BeforeDamageAttackTest.attack("","","{\"type\":\"bench_damage\",\"target\":\"OPPONENT_BENCH\",\"amount\":25}"),
            BeforeDamageAttackTest.attack("","","{\"type\":\"bench_damage\",\"target\":\"OPPONENT_BENCH\",\"amount\":20,\"count\":2}"),
            BeforeDamageAttackTest.attack("{\"type\":\"bench_damage\",\"target\":\"OPPONENT_BENCH\",\"amount\":20}","","")))
            check(AttackProgramRules.unsupportedReason(invalid).isPresent(),"非法全体备战伤害（目标、数值、参数或时机）被放行");
        var trainer=new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.ON_CARD_PLAYED,
            "{\"type\":\"bench_damage\",\"target\":\"OPPONENT_BENCH\",\"amount\":20}")));
        check(ResumableProgramRules.unsupportedReason(trainer,false).isPresent(),"训练家不能造成攻击伤害");
    }
}
