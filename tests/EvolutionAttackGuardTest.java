import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class EvolutionAttackGuardTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static AttackDefinition attack(boolean bypass){
        return BundledCombatData.attack(JsonParser.parseString("""
            {"id":"guard","nameZh":"进化攻击防护","energyCost":{},"baseDamage":40,"damageType":"COLORLESS","effectSupport":"SUPPORTED",
             "ignoresActiveDefenderDamageEffects":%s,"triggers":[{"trigger":"AFTER_ATTACK","effects":[
              {"type":"prevent_damage","target":"SELF","source":"ATTACK","from":"OPPONENT","scope":"DAMAGE_ONLY",
               "duration":"OPPONENT_TURN_END","attacker_filter":"EVOLUTION"}]}]}
            """.formatted(bypass)).getAsJsonObject(),EffectRegistry.standard());
    }
    public static void main(String[] args){
        for(boolean evolved:List.of(false,true))for(boolean bypass:List.of(false,true)){
            var attack=attack(bypass);var m=BeforeDamageAttackTest.fixture(attack);m.attack(A,attack.id());
            if(evolved)m.evolveByEffect(B,m.activePokemonView(B).pokemonId(),DynamicPassiveModifierTest.hand(m,B,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
            m.placeDamageCountersByEffect(B,A,m.activePokemonView(A).pokemonId(),1);m.attack(B,attack.id());
            int damage=evolved&&!bypass?0:40;
            check(m.lastAttack().finalDamage()==damage&&m.activePokemonView(A).damage()==damage+10,"Evolution guard stage, bypass or effect-counter boundary");
            m.endTurn(A);m.attack(B,attack.id());check(m.lastAttack().finalDamage()==40,"Evolution guard did not expire");
        }
        var filter=AttackSourceFilter.from(Map.of("attacker_filter","EVOLUTION"));
        for(var category:CardCategory.values()){
            var card=new CardDefinition("test","测试",category,category==CardCategory.TRAINER?TrainerCategory.ITEM:TrainerCategory.NONE,
                "H",Set.of("international"),false,false,4,Map.of(),EffectSupport.SUPPORTED,null);
            check(filter.matches(card,"测试",false)==(category==CardCategory.POKEMON),"non-Pokemon classified as Evolution");
        }
        var m=BeforeDamageAttackTest.fixture(attack(false));m.attack(A,"guard");
        var active=m.activePokemonView(A).pokemonId();m.switchActiveByEffect(B,A,m.benchPokemonView(A).getFirst().pokemonId());m.switchActiveByEffect(B,A,active);
        m.evolveByEffect(B,m.activePokemonView(B).pokemonId(),DynamicPassiveModifierTest.hand(m,B,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        m.attack(B,"guard");check(m.lastAttack().finalDamage()==40,"Leaving Active retained guard");
        BeforeDamageAttackTest.reject(()->AttackSourceFilter.from(Map.of("attacker_filter","EVOLVED")));
        System.out.println("EvolutionAttackGuardTest passed: printed stage, damage only, bypass, expiry and leaving Active");
    }
}
