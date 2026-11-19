import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** PASSIVE_COIN_PREVENT_ATTACK_DAMAGE: “If any damage is done to this Pokémon by attacks, flip a coin. If heads, prevent that damage.” */
public final class CoinPreventDamageAbilityTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static final String PREFIX="ability-coin-prevent-damage:";
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    static AbilityDefinition luck(){
        return new AbilityDefinition("coin-guard","掷币防伤",new AbilityEffect(AbilityEffect.Opcode.PASSIVE_COIN_PREVENT_ATTACK_DAMAGE,0),
            EffectSupport.SUPPORTED,null,AbilityDefinition.Kind.PASSIVE);
    }
    public static void main(String[] args){
        activeDamage(); benchDamage(); noFlipWithoutDamage(); bypassingAttack(); invalidData();
        System.out.println("CoinPreventDamageAbilityTest passed: 受到招式伤害时由服务器掷币，正面防止伤害");
    }
    static List<RandomAuditEvent> coins(ServerMatch m,int from){
        var all=m.randomTranscript();
        return all.subList(from,all.size()).stream().filter(e->e.operation().startsWith(PREFIX)).toList();
    }
    /** Both players' Active Pokémon have the Ability; attacks alternate until both coin results have been seen. */
    static void activeDamage(){
        var a=BeforeDamageAttackTest.attack("","","");
        var m=BeforeDamageAttackTest.fixture(a,luck());
        boolean heads=false,tails=false;
        for(int i=0;i<24&&!(heads&&tails);i++){
            UUID attacker=m.currentPlayer(),defender=attacker.equals(A)?B:A;
            UUID target=m.activePokemonView(defender).pokemonId();
            int from=m.randomTranscript().size(),before=m.activePokemonView(defender).damage();
            m.attack(attacker,a.id());
            var flips=coins(m,from);
            check(flips.size()==1&&flips.getFirst().operation().equals(PREFIX+defender+":"+target),"每次受到招式伤害应恰好掷 1 次硬币："+flips);
            int dealt=m.activePokemonView(defender).damage()-before;
            boolean head=flips.getFirst().result()==0;
            check(dealt==(head?0:80),"掷币结果与伤害不一致：正面="+head+" 伤害="+dealt);
            heads|=head; tails|=!head;
            if(m.activePokemonView(defender).damage()>0)m.healDamageByEffect(defender,defender,target,m.activePokemonView(defender).damage());
        }
        check(heads&&tails,"多次攻击没有同时出现正面和反面");
    }
    /** BenchDamageEffect targets flip separately, each for its own damage. */
    static void benchDamage(){
        var a=BeforeDamageAttackTest.attack("","","{\"type\":\"bench_damage\",\"target\":\"OPPONENT_BENCH\",\"amount\":30}");
        var m=BeforeDamageAttackTest.fixture(a,luck());
        var bench=m.benchPokemonView(B);
        int from=m.randomTranscript().size();
        m.attack(A,a.id());
        var flips=coins(m,from);
        check(flips.size()==bench.size()+1,"战斗位和每只备战宝可梦都应各掷一次："+flips.size());
        for(var before:bench){
            var flip=flips.stream().filter(e->e.operation().equals(PREFIX+B+":"+before.pokemonId())).findFirst().orElseThrow();
            int after=m.benchPokemonView(B).stream().filter(p->p.pokemonId().equals(before.pokemonId())).findFirst().orElseThrow().damage();
            check(after-before.damage()==(flip.result()==0?0:30),"备战宝可梦的掷币结果与伤害不一致");
        }
    }
    static void noFlipWithoutDamage(){
        var a=DynamicPassiveModifierTest.attack(0,false,false);
        var m=BeforeDamageAttackTest.fixture(a,luck());
        int from=m.randomTranscript().size();
        m.attack(A,a.id());
        check(coins(m,from).isEmpty(),"没有伤害时不应掷硬币");
    }
    /** “This attack's damage isn't affected by any effects on your opponent's Active Pokémon” also ignores this Ability. */
    static void bypassingAttack(){
        var a=DynamicPassiveModifierTest.attack(40,true,false);
        var m=BeforeDamageAttackTest.fixture(a,luck());
        int from=m.randomTranscript().size();
        m.attack(A,a.id());
        check(coins(m,from).isEmpty()&&m.activePokemonView(B).damage()==80,"无视对方战斗宝可梦身上效果的招式不应触发掷币");
    }
    static void invalidData(){
        boolean rejected=false;
        try{new AbilityEffect(AbilityEffect.Opcode.PASSIVE_COIN_PREVENT_ATTACK_DAMAGE,10);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"掷币防伤特性接受了数值参数");
    }
}
