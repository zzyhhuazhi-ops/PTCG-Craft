import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import java.util.*;

/**
 * gust: “Switch 1 of your opponent's Benched Pokémon with their Active Pokémon.” The effect's controller (not the
 * opponent) chooses which of the opponent's Benched Pokémon comes in; only that player's Bench is offered.
 */
public final class GustEffectTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    public static void main(String[] args){
        attackGust(); gustBeforeDamage(); trainerCoinGust(); invalidData();
        System.out.println("GustEffectTest passed: 由使用者选择对方备战宝可梦换到战斗位");
    }
    static void attackGust(){
        var a=BeforeDamageAttackTest.attack("","","{\"type\":\"gust\",\"target\":\"SELECT_OPPONENT_POKEMON\"}");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"招式换上对方备战宝可梦被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=BeforeDamageAttackTest.fixture(a);
        UUID oldActive=m.activePokemonView(B).pokemonId();
        UUID chosen=m.benchPokemonView(B).getLast().pokemonId();
        m.attack(A,a.id());
        var prompt=m.effectPokemonPrompt(A);
        check(prompt!=null,"攻击方没有得到选择提示");
        BeforeDamageAttackTest.reject(()->m.effectPokemonPrompt(B).resolutionId());
        BeforeDamageAttackTest.reject(()->m.chooseEffectPokemon(A,prompt.resolutionId(),prompt.step(),m.benchPokemonView(A).getFirst().pokemonId()));
        BeforeDamageAttackTest.reject(()->m.chooseEffectPokemon(A,prompt.resolutionId(),prompt.step(),oldActive));
        m.chooseEffectPokemon(A,prompt.resolutionId(),prompt.step(),chosen);
        check(m.activePokemonView(B).pokemonId().equals(chosen),"所选的对方备战宝可梦没有换到战斗位");
        check(m.benchPokemonView(B).stream().anyMatch(p->p.pokemonId().equals(oldActive)&&p.damage()==80),"原战斗宝可梦应带着本次伤害回到备战区");
        check(m.currentPlayer().equals(B),"换位后攻击没有结束");
    }
    /** “Switch 1 of your opponent's Benched Pokémon with their Active Pokémon. This attack does 30 damage to the new Active Pokémon.” */
    static void gustBeforeDamage(){
        var a=BeforeDamageAttackTest.attack("{\"type\":\"gust\",\"target\":\"SELECT_OPPONENT_POKEMON\"}","","");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"伤害前换上对方备战宝可梦被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=BeforeDamageAttackTest.fixture(a);
        UUID oldActive=m.activePokemonView(B).pokemonId();
        UUID chosen=m.benchPokemonView(B).getLast().pokemonId();
        m.attack(A,a.id());
        var prompt=m.effectPokemonPrompt(A);
        m.chooseEffectPokemon(A,prompt.resolutionId(),prompt.step(),chosen);
        check(m.activePokemonView(B).pokemonId().equals(chosen)&&m.activePokemonView(B).damage()==80,"伤害没有打到新换上的战斗宝可梦");
        check(m.benchPokemonView(B).stream().anyMatch(p->p.pokemonId().equals(oldActive)&&p.damage()==0),"原战斗宝可梦不应受伤");
    }
    static void trainerCoinGust(){
        var program=new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.ON_CARD_PLAYED,
            "{\"type\":\"coin_flip\",\"flips\":1,\"heads\":[{\"type\":\"gust\",\"target\":\"SELECT_OPPONENT_POKEMON\"}]}")));
        TrainerProgramRules.validate(program);
    }
    static void invalidData(){
        var registry=EffectRegistry.standard();
        for(String bad:List.of("{\"type\":\"gust\",\"target\":\"SELECT_OWN_POKEMON\"}","{\"type\":\"gust\",\"target\":\"OPPONENT_ACTIVE\"}",
            "{\"type\":\"gust\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"amount\":1}")) {
            var attack=BeforeDamageAttackTest.attack("","",bad);
            check(AttackProgramRules.unsupportedReason(attack).isPresent(),"非法换位数据被放行："+bad);
        }
        check(registry.supports("gust"),"标准注册表缺少 gust");
    }
}
