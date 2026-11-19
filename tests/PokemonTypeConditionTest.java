import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/** IS_TYPE: “If your opponent's Active Pokémon is a {T} Pokémon, this attack does N more damage.” */
public final class PokemonTypeConditionTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    static AttackDefinition moreIf(String type){
        return BeforeDamageAttackTest.attack("","{\"type\":\"conditional\",\"conditions\":[{\"type\":\"IS_TYPE\",\"target\":\"OPPONENT_ACTIVE\",\"pokemon_type\":\""+type
            +"\"}],\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}]}","");
    }
    static ServerMatch fixture(AttackDefinition attack,EnergyType type){
        return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),
            EnergyProfile.basic(EnergyType.COLORLESS),attack,List.of(),0,null,Map.of("basic",type,"evolved",type));
    }
    public static void main(String[] args){
        matchingType(); otherType(); invalidData();
        System.out.println("PokemonTypeConditionTest passed: 按对方战斗宝可梦属性追加伤害（服务器读印刷属性）");
    }
    static void matchingType(){
        var a=moreIf("WATER");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"属性条件追加伤害被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=fixture(a,EnergyType.WATER);
        m.attack(A,a.id());
        // (40 + 30) × 弱点 2
        check(m.activePokemonView(B).damage()==140,"对方是水属性时没有追加伤害，实际 "+m.activePokemonView(B).damage());
    }
    static void otherType(){
        var a=moreIf("FIRE");
        var m=fixture(a,EnergyType.WATER);
        m.attack(A,a.id());
        check(m.activePokemonView(B).damage()==80,"对方不是火属性时不应追加伤害，实际 "+m.activePokemonView(B).damage());
    }
    static void invalidData(){
        for(String json:List.of("{\"type\":\"IS_TYPE\",\"target\":\"OPPONENT_ACTIVE\"}",
            "{\"type\":\"IS_TYPE\",\"target\":\"OPPONENT_ACTIVE\",\"pokemon_type\":\"SHADOW\"}"))
            BeforeDamageAttackTest.reject(()->BeforeDamageAttackTest.attack("","{\"type\":\"conditional\",\"conditions\":["+json
                +"],\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}]}",""));
        var extra=BeforeDamageAttackTest.attack("","{\"type\":\"conditional\",\"conditions\":[{\"type\":\"IS_TYPE\",\"target\":\"OPPONENT_ACTIVE\",\"pokemon_type\":\"FIRE\",\"value\":1}],"
            +"\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}]}","");
        check(AttackProgramRules.unsupportedReason(extra).isPresent(),"属性条件的多余参数被放行");
        var bench=BeforeDamageAttackTest.attack("","{\"type\":\"conditional\",\"conditions\":[{\"type\":\"IS_TYPE\",\"target\":\"OPPONENT_BENCH\",\"pokemon_type\":\"FIRE\"}],"
            +"\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}]}","");
        check(AttackProgramRules.unsupportedReason(bench).isPresent(),"属性条件读取群体目标被放行");
    }
}
