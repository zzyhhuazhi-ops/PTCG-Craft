import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.EnergyType;
import java.util.*;

/**
 * energy_type on search_deck / search_discard_pile: “Search your deck for up to N {G} Energy cards, reveal them, and
 * put them into your hand.” Only Basic Energy of that type (from audited energy data) is offered.
 */
public final class TypedEnergySearchTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    static String search(String type){
        return "{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":2,\"minimum\":0,\"card_category\":\"ENERGY\",\"basic_energy_only\":true,\"energy_type\":\""+type+"\"}";
    }
    public static void main(String[] args){
        matchingType(); otherType(); publicRecoveryPotential(); invalidData();
        System.out.println("TypedEnergySearchTest passed: 按属性检索基本能量，候选由服务器按已审核的能量数据筛选");
    }
    static void matchingType(){
        var a=BeforeDamageAttackTest.attack("","",search("GRASS"));
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"按属性检索能量被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=BeforeDamageAttackTest.fixture(a);
        int hand=m.privateView(A).hand().size();
        m.attack(A,a.id());
        var prompt=m.effectCardPrompt(A);
        check(prompt!=null&&!prompt.candidates().isEmpty(),"牌库里有草能量却没有候选");
        check(prompt.candidates().stream().allMatch(c->c.definitionId().equals("energy")),"候选里混进了非草属性基本能量的卡");
        m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),prompt.candidates().stream().limit(2).map(c->c.instanceId()).toList());
        check(m.privateView(A).hand().size()==hand+Math.min(2,prompt.candidates().size())&&m.currentPlayer().equals(B),"选中的能量没有加入手牌，或攻击没有结束：手牌 "+hand+"→"+m.privateView(A).hand().size()+" 候选 "+prompt.candidates().size()+" 当前 "+m.currentPlayer()+" 阶段 "+m.phase()+" 揭示 "+m.effectCardReveal());
    }
    static void otherType(){
        var a=BeforeDamageAttackTest.attack("","",search("FIRE")+",{\"type\":\"draw\",\"amount\":1}");
        var m=BeforeDamageAttackTest.fixture(a);
        int hand=m.privateView(A).hand().size();
        m.attack(A,a.id());
        check(m.privateView(A).hand().size()==hand+1&&m.currentPlayer().equals(B),"牌库没有火能量时检索应什么都找不到，后续文字照常");
    }
    static void publicRecoveryPotential(){
        // Activation must use the same combat-profile filters as the subsequent choice.
        for(int hp:List.of(100,500)){
            var trainer=ComposedTrainerIntegrationTest.item("{\"type\":\"search_discard_pile\",\"target\":\"DISCARD\",\"amount\":1,\"minimum\":1,\"card_category\":\"POKEMON\",\"hp_max\":"+hp+",\"destination\":\"HAND\"}");
            var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.GRASS,EnergyType.GRASS,trainer,GeneratedRuleBatch8Test.hit(10,false));
            var pokemon=DynamicPassiveModifierTest.hand(m,A,"basic");var item=DynamicPassiveModifierTest.hand(m,A,"trainer");m.discardHandCardsByEffect(A,A,List.of(pokemon.instanceId()));
            if(hp==100){var before=m.clientView(A);BeforeDamageAttackTest.reject(()->m.playTrainer(A,item.instanceId(),null));check(before.equals(m.clientView(A)),"ineligible public Pokemon consumed recovery Item");}
            else {m.playTrainer(A,item.instanceId(),null);var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(pokemon.instanceId()));check(m.privateView(A).hand().contains(pokemon),"eligible public Pokemon recovery rejected");}
        }
    }
    static void invalidData(){
        for(String bad:List.of(
            "{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":2,\"minimum\":0,\"card_category\":\"ENERGY\",\"energy_type\":\"GRASS\"}",
            "{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":2,\"minimum\":0,\"card_category\":\"ENERGY\",\"basic_energy_only\":true,\"energy_type\":\"SHADOW\"}"))
            BeforeDamageAttackTest.reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack("","",bad)));
    }
}
