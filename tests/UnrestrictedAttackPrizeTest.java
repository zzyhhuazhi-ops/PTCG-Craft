import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class UnrestrictedAttackPrizeTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static AttackDefinition attack(boolean basicOnly,boolean bench,String extra){
        return BundledCombatData.attack(JsonParser.parseString("""
            {"id":"reward","nameZh":"额外奖赏","energyCost":{},"baseDamage":40,"damageType":"COLORLESS","effectSupport":"SUPPORTED",
             "damageTarget":"%s","triggers":[{"trigger":"AFTER_ATTACK","effects":[
              {"type":"extra_prize_on_attack_ko","target":"SELF","amount":1,"basic_only":%s}%s]}]}
            """.formatted(bench?"ALL_OPPONENT_BENCH":"OPPONENT_ACTIVE",basicOnly,extra)).getAsJsonObject(),EffectRegistry.standard());
    }
    static int take(ServerMatch m,UUID owner){int before=m.privateView(owner).prizes().size();while(m.phase()==MatchPhase.PRIZE_SELECTION)m.takePrizeAt(owner,0);return before-m.privateView(owner).prizes().size();}
    public static void main(String[] args){
        for(boolean basicOnly:List.of(false,true))for(String mode:List.of("basic","evolution","poison","effect","later")){
            var attack=attack(basicOnly,false,mode.equals("effect")?",{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}":"");
            var m=BeforeDamageAttackTest.fixture(attack);
            if(mode.equals("evolution")){m.endTurn(A);m.evolveByEffect(B,m.activePokemonView(B).pokemonId(),DynamicPassiveModifierTest.hand(m,B,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);m.endTurn(B);}
            var target=m.activePokemonView(B);boolean counter=mode.equals("poison")||mode.equals("effect");
            if(!mode.equals("later"))m.placeDamageCountersByEffect(A,B,target.pokemonId(),(target.hitPoints()-(counter?50:40))/10);
            if(mode.equals("poison"))m.applySpecialConditionByEffect(A,B,SpecialCondition.POISONED);
            m.attack(A,attack.id());if(mode.equals("later"))m.knockOutPokemonByEffect(B,B,target.pokemonId());
            int expected=mode.equals("basic")||mode.equals("evolution")&&!basicOnly?2:1;
            check(take(m,A)==expected,"Extra prizes from wrong KO: "+basicOnly+" "+mode);
        }
        var attack=attack(false,true,"");var m=BeforeDamageAttackTest.fixture(attack);m.endTurn(A);
        m.playBasicPokemonToBench(B,DynamicPassiveModifierTest.hand(m,B,"basic").instanceId());m.endTurn(B);
        for(var target:m.benchPokemonView(B))m.placeDamageCountersByEffect(A,B,target.pokemonId(),26);
        m.attack(A,attack.id());check(take(m,A)==4&&m.activePokemonView(B).damage()==0,"Each attack-damage Bench KO needs its own extra prize");
        for(String flag:List.of("\"yes\"","0","null"))BeforeDamageAttackTest.reject(()->EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"extra_prize_on_attack_ko\",\"amount\":1,\"basic_only\":"+flag+"}").getAsJsonObject()));
        BeforeDamageAttackTest.reject(()->EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"extra_prize_on_attack_ko\",\"amount\":1}").getAsJsonObject()));
        System.out.println("UnrestrictedAttackPrizeTest passed: Basic/Evolution, per-KO Bench prizes, poison/effect/later exclusions and strict schema");
    }
}
