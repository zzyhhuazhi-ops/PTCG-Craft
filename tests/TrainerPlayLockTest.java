import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/**
 * “During your opponent's next turn, they can't play any Item cards from their hand.”: prevent_trainer_play on the
 * opponent, owned by the attacking player and lifted when that player starts their next turn.
 */
public final class TrainerPlayLockTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    static String lock(String category){
        return "{\"type\":\"prevent_trainer_play\",\"target\":\"OPPONENT_ACTIVE\",\"trainer_category\":\""+category+"\"}";
    }
    static TrainerProfile item(){
        return BundledCombatData.trainer(JsonParser.parseString("{\"trainerCategory\":\"ITEM\",\"effect\":{\"opcode\":\"DRAW\",\"amount\":1}}").getAsJsonObject(),EffectRegistry.standard());
    }
    static ServerMatch fixture(AttackDefinition attack){
        return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),
            EnergyProfile.basic(EnergyType.COLORLESS),attack,List.of(),0,item());
    }
    public static void main(String[] args){
        itemLockedForOneTurn(); invalidData();
        System.out.println("TrainerPlayLockTest passed: 对手下个回合不能从手牌使用物品卡（道具、场地照常；到来源玩家下回合开始解除）");
    }
    static void itemLockedForOneTurn(){
        var a=BeforeDamageAttackTest.attack("","",lock("ITEM"));
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"禁止使用物品卡被攻击规则拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=fixture(a);
        check(m.canPlayTrainer(A,DynamicPassiveModifierTest.hand(m,A,"item").instanceId(),null),"夹具的物品卡本来就不能用");
        m.attack(A,a.id());
        check(m.currentPlayer().equals(B),"攻击没有结束");
        var item=DynamicPassiveModifierTest.hand(m,B,"item");
        check(!m.canPlayTrainer(B,item.instanceId(),null),"被限制的回合仍可使用物品卡");
        BeforeDamageAttackTest.reject(()->m.playTrainer(B,item.instanceId(),null));
        var labels=MatchSession.multiplayer(m,A,B).view(B,B).options().stream().map(o->o.labelZh()).toList();
        check(labels.stream().anyMatch(l->l.startsWith("本回合不能使用")),"对局操作列表没有说明物品卡被限制："+labels);
        check(labels.stream().noneMatch(l->l.equals("使用：item")),"被限制的物品卡仍出现在可用操作里");
        // 现行规则下宝可梦道具不是物品卡，场地也不是：照常可用
        UUID active=m.activePokemonView(B).pokemonId();
        check(m.canPlayTrainer(B,DynamicPassiveModifierTest.hand(m,B,"tool").instanceId(),active),"宝可梦道具不应被物品卡限制挡住");
        check(m.canPlayTrainer(B,DynamicPassiveModifierTest.hand(m,B,"stadium").instanceId(),null),"场地卡不应被物品卡限制挡住");
        m.endTurn(B);
        check(m.canPlayTrainer(A,DynamicPassiveModifierTest.hand(m,A,"item").instanceId(),null),"施加限制的一方自己的物品卡不受影响");
        m.endTurn(A);
        check(m.canPlayTrainer(B,DynamicPassiveModifierTest.hand(m,B,"item").instanceId(),null),"限制没有在来源玩家下个回合开始时解除");
    }
    static void invalidData(){
        for(String bad:List.of(
            "{\"type\":\"prevent_trainer_play\",\"target\":\"OWN_ACTIVE\",\"trainer_category\":\"ITEM\"}",
            "{\"type\":\"prevent_trainer_play\",\"target\":\"OPPONENT_ACTIVE\",\"trainer_category\":\"TOOL\"}",
            "{\"type\":\"prevent_trainer_play\",\"target\":\"OPPONENT_ACTIVE\"}",
            "{\"type\":\"prevent_trainer_play\",\"target\":\"OPPONENT_ACTIVE\",\"trainer_category\":\"ITEM\",\"amount\":1}"))
            BeforeDamageAttackTest.reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack("","",bad)));
        BeforeDamageAttackTest.reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack(lock("ITEM"),"","")));
        check(AttackProgramRules.unsupportedReason(BeforeDamageAttackTest.attack("","",lock("SUPPORTER"))).isEmpty(),"禁止使用支援者卡被拒绝");
        var trainer=new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.ON_CARD_PLAYED,lock("ITEM"))));
        check(ResumableProgramRules.unsupportedReason(trainer,false).isPresent(),"训练家不能施加限制使用训练家卡");
    }
}
