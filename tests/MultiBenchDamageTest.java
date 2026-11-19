import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import java.util.*;

/**
 * “This attack also does N damage to 2 of your opponent's Benched Pokémon.”: a selected bench_damage step with
 * target_count. The attacker chooses that many different Benched Pokémon one prompt at a time (fewer when the Bench
 * has fewer), then each takes the attack damage without Weakness/Resistance.
 */
public final class MultiBenchDamageTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    static String two(int amount){return "{\"type\":\"bench_damage\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"amount\":"+amount+",\"target_count\":2}";}
    public static void main(String[] args){
        twoDifferent(); fewerThanCount(); invalidData();
        System.out.println("MultiBenchDamageTest passed: 对对手 2 只不同的备战宝可梦造成伤害（逐只选择、不能重复、不足时有几只选几只、不计弱点）");
    }
    /** B's own turn: play Basic Pokémon from hand until the Bench has {@code size}, then back to A. */
    static void benchUp(ServerMatch m,int size){
        m.endTurn(A);
        while(m.benchPokemonView(B).size()<size)m.playBasicPokemonToBench(B,DynamicPassiveModifierTest.hand(m,B,"basic").instanceId());
        m.endTurn(B);
    }
    static void twoDifferent(){
        var a=BeforeDamageAttackTest.attack("","",two(20));
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"两只备战伤害被攻击规则拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=BeforeDamageAttackTest.fixture(a);
        benchUp(m,3);
        var bench=m.benchPokemonView(B).stream().map(p->p.pokemonId()).toList();
        m.attack(A,a.id());
        check(m.activePokemonView(B).damage()==80,"战斗宝可梦的攻击伤害（含弱点）被改变");
        var first=m.effectPokemonPrompt(A);
        check(new HashSet<>(first.targets().stream().map(t->t.pokemonId()).toList()).equals(new HashSet<>(bench)),"第一次的候选应是对方全部备战宝可梦，不含战斗宝可梦");
        check(first.labelZh().contains("已选 0 / 2"),"提示没有显示选择进度："+first.labelZh());
        UUID one=bench.get(0),two=bench.get(1);
        m.chooseEffectPokemon(A,first.resolutionId(),first.step(),one);
        check(m.benchPokemonView(B).stream().allMatch(p->p.damage()==0),"选完第一只就提前造成了伤害");
        var second=m.effectPokemonPrompt(A);
        check(second.step()==first.step()&&second.targets().stream().noneMatch(t->t.pokemonId().equals(one))&&second.targets().size()==bench.size()-1,
            "第二次的候选应排除已选的那只");
        check(second.labelZh().contains("已选 1 / 2"),"第二次提示没有显示进度："+second.labelZh());
        BeforeDamageAttackTest.reject(()->m.chooseEffectPokemon(A,second.resolutionId(),second.step(),one));
        BeforeDamageAttackTest.reject(()->m.chooseEffectPokemon(B,second.resolutionId(),second.step(),two));
        m.chooseEffectPokemon(A,second.resolutionId(),second.step(),two);
        for(var p:m.benchPokemonView(B))
            check(p.damage()==(p.pokemonId().equals(one)||p.pokemonId().equals(two)?20:0),"只有选中的两只各受 20 点（不计弱点），其余不受伤");
        check(m.currentPlayer().equals(B),"选完两只后攻击没有结束");
    }
    static void fewerThanCount(){
        var a=BeforeDamageAttackTest.attack("","",two(30));
        var m=BeforeDamageAttackTest.fixture(a);
        check(m.benchPokemonView(B).size()==1,"夹具对方应正好有 1 只备战宝可梦，实际 "+m.benchPokemonView(B).size());
        UUID only=m.benchPokemonView(B).getFirst().pokemonId();
        m.attack(A,a.id());
        var prompt=m.effectPokemonPrompt(A);
        m.chooseEffectPokemon(A,prompt.resolutionId(),prompt.step(),only);
        check(m.benchPokemonView(B).getFirst().damage()==30&&m.currentPlayer().equals(B),"对方只有 1 只备战宝可梦时选这 1 只就结束");
    }
    static void invalidData(){
        for(String bad:List.of(
            two(20).replace("\"target_count\":2","\"target_count\":1"),
            two(20).replace("\"target_count\":2","\"target_count\":6"),
            two(20).replace("\"target_count\":2","\"target_count\":\"two\""),
            two(25),
            "{\"type\":\"bench_damage\",\"target\":\"OPPONENT_BENCH\",\"amount\":20,\"target_count\":2}",
            "{\"type\":\"heal\",\"target\":\"SELECT_OWN_POKEMON\",\"amount\":20,\"target_count\":2}",
            two(20).replace("\"target_count\"","\"count\"")))
            check(AttackProgramRules.unsupportedReason(BeforeDamageAttackTest.attack("","",bad)).isPresent(),"非法多目标备战伤害被放行："+bad);
        check(AttackProgramRules.unsupportedReason(BeforeDamageAttackTest.attack(two(20),"","")).isPresent(),"备战伤害不能写在伤害前");
        var trainer=new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.ON_CARD_PLAYED,two(20))));
        check(ResumableProgramRules.unsupportedReason(trainer,false).isPresent(),"训练家不能造成攻击伤害");
    }
}
