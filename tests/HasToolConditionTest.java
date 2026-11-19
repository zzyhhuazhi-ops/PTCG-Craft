import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/** HAS_TOOL: “If this Pokémon (your opponent's Active Pokémon) has a Pokémon Tool attached, this attack does N more damage.” */
public final class HasToolConditionTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    static AttackDefinition moreIf(String target){
        return BeforeDamageAttackTest.attack("","{\"type\":\"conditional\",\"conditions\":[{\"type\":\"HAS_TOOL\",\"target\":\""+target
            +"\"}],\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}]}","");
    }
    static void attachTool(ServerMatch m,UUID owner,UUID pokemon){
        m.playTrainer(owner,DynamicPassiveModifierTest.hand(m,owner,"tool").instanceId(),pokemon);
    }
    public static void main(String[] args){
        selfTool(); opponentTool(); invalidData();
        System.out.println("HasToolConditionTest passed: 自己／对方战斗宝可梦附有宝可梦道具时追加伤害");
    }
    static void selfTool(){
        var a=moreIf("SELF");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"附有道具条件被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var bare=BeforeDamageAttackTest.fixture(a);
        bare.attack(A,a.id());
        check(bare.activePokemonView(B).damage()==80,"没有道具时不应追加伤害");
        var equipped=BeforeDamageAttackTest.fixture(a);
        attachTool(equipped,A,equipped.activePokemonView(A).pokemonId());
        equipped.attack(A,a.id());
        check(equipped.activePokemonView(B).damage()==140,"附有道具时没有追加伤害，实际 "+equipped.activePokemonView(B).damage());
        // 道具附在备战宝可梦上不算“这只宝可梦”
        var bench=BeforeDamageAttackTest.fixture(a);
        attachTool(bench,A,bench.benchPokemonView(A).getFirst().pokemonId());
        bench.attack(A,a.id());
        check(bench.activePokemonView(B).damage()==80,"备战宝可梦的道具被当成攻击者的");
        // 道具效果失效时道具仍然附着着，条件照样成立
        var disabled=BeforeDamageAttackTest.fixture(a);
        UUID active=disabled.activePokemonView(A).pokemonId();
        attachTool(disabled,A,active);
        disabled.disableToolEffectsByEffect(A,A,active);
        disabled.attack(A,a.id());
        check(disabled.activePokemonView(B).damage()==140,"道具效果失效后不应影响“附有道具”的判断");
    }
    static void opponentTool(){
        var a=moreIf("OPPONENT_ACTIVE");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"对方附有道具条件被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=BeforeDamageAttackTest.fixture(a);
        m.endTurn(A);
        attachTool(m,B,m.activePokemonView(B).pokemonId());
        m.endTurn(B);
        m.attack(A,a.id());
        check(m.activePokemonView(B).damage()==140,"对方战斗宝可梦附有道具时没有追加伤害，实际 "+m.activePokemonView(B).damage());
        var own=BeforeDamageAttackTest.fixture(a);
        attachTool(own,A,own.activePokemonView(A).pokemonId());
        own.attack(A,a.id());
        check(own.activePokemonView(B).damage()==80,"己方道具被当成对方的");
    }
    static void invalidData(){
        check(AttackProgramRules.unsupportedReason(moreIf("OWN_BENCH")).isPresent(),"附有道具条件读取群体目标被放行");
        check(AttackProgramRules.unsupportedReason(BeforeDamageAttackTest.attack("","{\"type\":\"conditional\",\"conditions\":[{\"type\":\"HAS_TOOL\","
            +"\"target\":\"SELF\",\"value\":1}],\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}]}","")).isPresent(),
            "附有道具条件的多余参数被放行");
    }
}
