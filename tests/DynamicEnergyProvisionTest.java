import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Synthetic fixtures exercise generic provision rules; no unreviewed real card is enabled. */
public final class DynamicEnergyProvisionTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static final String OVERRIDE="\"type\":\"special_energy_override\",\"amount\":1,\"energy_type\":\"COLORLESS\",\"suppress_other_effects\":true";
    public static void main(String[] args) {
        conditionalProvision(); retreatAndMovement(); stadiumOverride(); energyDamageCount(); invalidData(); branchesAndMatching(); suppressionAndProtection(); sessionRetreat();
        System.out.println("DynamicEnergyProvisionTest passed: shared live energy provision, costs, counters and suppression");
    }
    static EnergyProfile energy(String json) {return BundledCombatData.specialEnergy(JsonParser.parseString(json).getAsJsonObject(),EffectRegistry.standard());}
    static EnergyProfile conditional() {
        return energy("""
            {"energyUnits":[["COLORLESS"]],"provisionRules":[
              {"conditions":[{"type":"IS_BASIC"}],"energyUnits":[["GRASS"],["GRASS"],["GRASS"]]}
            ]}
            """);
    }
    static AttackDefinition attack(String cost,String effects) {
        return BundledCombatData.attack(JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"动态供能测试\",\"energyCost\":"+cost+
            ",\"baseDamage\":10,\"damageType\":\"GRASS\",\"effectSupport\":\"SUPPORTED\",\"effects\":["+effects+"]}").getAsJsonObject(),EffectRegistry.standard());
    }
    static ServerMatch fixture(EnergyProfile e,TrainerProfile stadium,AttackDefinition attack) {
        return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),stadium,e,attack);
    }
    static ServerMatch fixture(EnergyProfile e,AttackDefinition attack) {return fixture(e,DynamicPassiveModifierTest.plainStadium(),attack);}
    static CardInstance hand(ServerMatch m,UUID p,String id) {return DynamicPassiveModifierTest.hand(m,p,id);}
    static boolean count(ServerMatch m,UUID p,UUID host,int count) {
        return m.matchesEffectCondition(p,host,new ConditionDefinition(ConditionType.ENERGY_COUNT,Map.of("value",""+count,"comparison","="),List.of()));
    }
    static void conditionalProvision() {
        var m=fixture(conditional(),attack("{\"GRASS\":1,\"COLORLESS\":2}",""));UUID host=m.activePokemonView(A).pokemonId();
        m.attachEnergy(A,hand(m,A,"special").instanceId(),host);
        check(count(m,A,host,3),"条件供能没有用于统一ENERGY_COUNT，三单位被当成一张卡");
        m.attack(A,"hit");check(m.activePokemonView(B).damage()==20,"类型与三个单位未参与攻击费用");
        var evolved=fixture(conditional(),attack("{\"GRASS\":1,\"COLORLESS\":2}",""));host=evolved.activePokemonView(A).pokemonId();
        evolved.attachEnergy(A,hand(evolved,A,"special").instanceId(),host);evolved.evolve(A,host,hand(evolved,A,"evolved").instanceId());
        check(count(evolved,A,host,1),"进化后供能仍沿用旧条件");reject(()->evolved.attack(A,"hit"));
        check(evolved.activePokemonView(B).damage()==0&&evolved.currentPlayer().equals(A),"无效攻击改变了伤害或回合");
    }
    static void retreatAndMovement() {
        var m=fixture(conditional(),attack("{}",""));UUID host=m.activePokemonView(A).pokemonId(),bench=m.benchPokemonView(A).getFirst().pokemonId();
        var card=hand(m,A,"special");m.attachEnergy(A,card.instanceId(),host);m.retreat(A,bench,List.of(card.instanceId()));
        check(m.privateView(A).discard().contains(card)&&m.activePokemonView(A).pokemonId().equals(bench),"撤退未按当前三单位供能支付一个实体卡");
        var moved=fixture(conditional(),attack("{}",""));host=moved.activePokemonView(A).pokemonId();bench=moved.benchPokemonView(A).getFirst().pokemonId();
        moved.evolve(A,bench,hand(moved,A,"evolved").instanceId());card=hand(moved,A,"special");moved.attachEnergy(A,card.instanceId(),host);
        moved.moveAttachedEnergyByEffect(A,A,host,A,bench,List.of(card.instanceId()));
        check(count(moved,A,host,0)&&count(moved,A,bench,1),"能量移动后没有按新宿主重新计算");
        moved.switchActiveByEffect(A,A,bench);final UUID replacement=host,paid=card.instanceId();
        reject(()->moved.retreat(A,replacement,List.of(paid)));
        check(moved.activePokemonView(A).attachedEnergyIds().contains(paid),"被拒绝的撤退仍弃置了能量");
    }
    static TrainerProfile stadium() {return DynamicPassiveModifierTest.trainer("STADIUM","{"+OVERRIDE+",\"target\":\"ALL_OWN_POKEMON\"},{"+OVERRIDE+",\"target\":\"ALL_OPPONENT_POKEMON\"}");}
    static void stadiumOverride() {
        var energy=energy("{\"energyUnits\":[[\"GRASS\"],[\"GRASS\"]],\"effects\":[{\"type\":\"retreat_cost_modifier\",\"amount\":-2}]}");
        var m=fixture(energy,stadium(),attack("{\"GRASS\":1}",""));UUID host=m.activePokemonView(A).pokemonId();
        var card=hand(m,A,"special");m.attachEnergy(A,card.instanceId(),host);
        check(m.activeRetreatCost(A)==1&&count(m,A,host,2),"初始能量持续修正或固定单位错误");
        m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);
        for(int i=0;i<10;i++)check(m.activeRetreatCost(A)==3&&count(m,A,host,1),"场地没有替换供能并关闭特殊能量其它效果");
        reject(()->m.attack(A,"hit"));
        m.discardStadiumByEffect(A);check(m.activeRetreatCost(A)==1&&count(m,A,host,2),"场地离场后能量效果未恢复");
        m.attack(A,"hit");check(m.activePokemonView(B).damage()==20,"场地离场后类型没有恢复");
        var basic=fixture(energy,stadium(),attack("{\"GRASS\":1}",""));basic.attachEnergy(A,hand(basic,A,"energy").instanceId(),basic.activePokemonView(A).pokemonId());
        basic.playTrainer(A,hand(basic,A,"stadium").instanceId(),null);basic.attack(A,"hit");check(basic.activePokemonView(B).damage()==20,"特殊能量场地改写了基本能量");
    }
    static void energyDamageCount() {
        var large=new DamageContext(0,0,0,0,6,6,0,0,61,61,0,0,0,0,0,0,0,0,0,0);
        check(new DamageModifier(DamageModifier.Basis.ATTACKER_ATTACHED_ENERGY_COUNT,10,0).resolve(large)==610,"旧伤害上下文仍将供能上限当作卡牌张数");
        for(boolean field:List.of(false,true)) {
            var m=fixture(conditional(),stadium(),attack("{}","{\"type\":\"damage_per_energy\",\"amount\":10}"));UUID host=m.activePokemonView(A).pokemonId();
            m.attachEnergy(A,hand(m,A,"special").instanceId(),host);if(field)m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);
            m.attack(A,"hit");check(m.activePokemonView(B).damage()==(field?40:80),"每个能量伤害没有使用当前供能单位");
            var oldAttack=BundledCombatData.attack(JsonParser.parseString("""
                {"id":"hit","nameZh":"旧计数适配测试","energyCost":{},"baseDamage":10,"damageType":"GRASS","effectSupport":"SUPPORTED",
                 "damageModifiers":[{"basis":"ATTACKER_ATTACHED_ENERGY_COUNT","amountPerUnit":10}]}
                """).getAsJsonObject(),EffectRegistry.standard());
            var legacy=fixture(conditional(),stadium(),oldAttack);legacy.attachEnergy(A,hand(legacy,A,"special").instanceId(),legacy.activePokemonView(A).pokemonId());
            if(field)legacy.playTrainer(A,hand(legacy,A,"stadium").instanceId(),null);
            legacy.attack(A,"hit");check(legacy.activePokemonView(B).damage()==(field?40:80),"旧计数攻击未复用动态供能结果");
        }
    }
    static void invalidData() {
        for(String condition:List.of("{\"type\":\"ENERGY_COUNT\",\"value\":2}","{\"type\":\"HAS_ENERGY\"}","{\"type\":\"COIN_RESULT\",\"value\":\"HEADS\"}","{\"type\":\"IS_BASIC\",\"target\":\"OPPONENT_ACTIVE\"}"))
            reject(()->energy("{\"energyUnits\":[[\"COLORLESS\"]],\"provisionRules\":[{\"conditions\":["+condition+"],\"energyUnits\":[[\"GRASS\"]]}]}"));
        reject(()->energy("{\"energyUnits\":[[\"COLORLESS\"]],\"provisionRules\":[{\"conditions\":[],\"energyUnits\":[[\"GRASS\"]]}]}"));
        reject(()->energy("{\"energyUnits\":[[\"COLORLESS\"]],\"provisionRules\":[{\"conditions\":[{\"type\":\"IS_BASIC\"}],\"energyUnits\":[[\"GRASS\"]],\"typo\":true}]}"));
        reject(()->DynamicPassiveModifierTest.trainer("TOOL","{"+OVERRIDE+",\"target\":\"ALL_OWN_POKEMON\"}"));
        reject(()->DynamicPassiveModifierTest.trainer("STADIUM","{"+OVERRIDE+",\"target\":\"ALL_OWN_POKEMON\",\"conditions\":[{\"type\":\"ENERGY_COUNT\",\"value\":2}]}"));
        reject(()->DynamicPassiveModifierTest.trainer("STADIUM","{"+OVERRIDE+",\"target\":\"ALL_OWN_POKEMON\"},{"+OVERRIDE+",\"target\":\"ALL_OWN_POKEMON\"}"));
        reject(()->EnergyProfile.canPay(List.of(conditional()),Map.of()));
        reject(()->conditional().unitCount());
        reject(()->DynamicPassiveModifierTest.trainer("STADIUM","{\"type\":\"conditional\",\"conditions\":[{\"type\":\"IS_BASIC\"}],\"effects\":[{"+OVERRIDE+",\"target\":\"ALL_OWN_POKEMON\"}]}"));
        reject(()->energy("{\"energyUnits\":[[\"COLORLESS\"]],\"effects\":[{"+OVERRIDE+",\"target\":\"ALL_OWN_POKEMON\"}]}"));
        var program=BundledCombatData.ability(JsonParser.parseString("{\"id\":\"bad\",\"nameZh\":\"非法一次性替换\",\"effectSupport\":\"SUPPORTED\",\"effects\":[{\"type\":\"draw\",\"amount\":1},{"+OVERRIDE+",\"target\":\"ALL_OWN_POKEMON\"}]}").getAsJsonObject(),EffectRegistry.standard());
        var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),stadium(),conditional(),attack("{}",""),List.of(program));
        var before=m.privateView(A).hand();reject(()->m.useAbility(A,m.activePokemonView(A).pokemonId(),"bad"));check(before.equals(m.privateView(A).hand()),"非法特性部分执行了抽牌前缀");
    }
    static void branchesAndMatching() {
        var profile=energy("""
            {"energyUnits":[["COLORLESS"],["COLORLESS"]],"provisionRules":[
              {"conditions":[{"type":"OR","conditions":[{"type":"IS_V"},{"type":"IS_GX"}]}],"energyUnits":[["COLORLESS"]]}
            ]}
            """);
        for(var type:List.of(ConditionType.IS_BASIC,ConditionType.IS_V,ConditionType.IS_GX)) {
            var state=facts(type);var resolved=profile.resolve(state);
            check(resolved.unitCount()==(type==ConditionType.IS_BASIC?2:1),"V/GX族条件未统一解析");
        }
        var selectable=energy("""
            {"energyUnits":[["COLORLESS"]],"provisionRules":[
              {"conditions":[{"type":"AND","conditions":[{"type":"IS_BASIC"},{"type":"NOT","conditions":[{"type":"IS_V"}]}]}],"energyUnits":[["FIRE","WATER"],["FIRE","WATER"]]},
              {"conditions":[{"type":"IS_BASIC"}],"energyUnits":[["GRASS"],["GRASS"],["GRASS"]]}
            ]}
            """).resolve(facts(ConditionType.IS_BASIC));
        check(EnergyProfile.canPay(List.of(selectable),Map.of(EnergyType.FIRE,1,EnergyType.WATER,1)),"可选类型单位未正确分配");
        check(!EnergyProfile.canPay(List.of(selectable),Map.of(EnergyType.FIRE,2,EnergyType.WATER,1)),"一个供能单位被重复用于两个类型");
        check(selectable.unitCount()==2,"多个满足条件的分支被错误叠加，未使用首个匹配分支");
    }
    static EffectConditionContext facts(ConditionType family) {
        return new EffectConditionContext() {
            public int number(ConditionType t,EffectTarget target,Map<String,String> args){return 0;}
            public boolean hasStatus(EffectTarget target,String status){return false;}
            public boolean flag(ConditionType t,EffectTarget target,Map<String,String> args){return t==family;}
            public boolean coinResult(String expected){throw new AssertionError("供能读取随机数");}
            public boolean customCondition(ConditionDefinition c){throw new AssertionError("供能读取未审核条件");}
        };
    }
    static void suppressionAndProtection() {
        var energy=energy("""
            {"energyUnits":[["GRASS"],["GRASS"]],"effects":[
              {"type":"attack_damage_modifier","direction":"DEALT","amount":-20},
              {"type":"retreat_cost_modifier","target":"ALL_OPPONENT_POKEMON","amount":1}]}
            """);
        var guard=BundledCombatData.ability(JsonParser.parseString("""
            {"id":"guard","nameZh":"宝可梦保护测试","effectSupport":"SUPPORTED","effects":[
              {"type":"prevent_effects","target":"SELF","source":"ANY_CARD","from":"OPPONENT","scope":"EFFECTS_ONLY","duration":"SOURCE_NEXT_TURN_START"}]}
            """).getAsJsonObject(),EffectRegistry.standard());
        var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),stadium(),energy,DynamicPassiveModifierTest.attack(40,false,false),List.of(guard));
        UUID host=m.activePokemonView(A).pokemonId();m.attachEnergy(A,hand(m,A,"special").instanceId(),host);m.useAbility(A,host,"guard");
        check(m.activeRetreatCost(B)==4,"能量光环未生效");m.endTurn(A);m.playTrainer(B,hand(m,B,"stadium").instanceId(),null);
        check(count(m,A,host,1)&&m.activeRetreatCost(B)==3,"宝可梦保护错误挡住作用于附着能量的场地，或能量光环仍生效");
        m.attachEnergy(B,hand(m,B,"special").instanceId(),m.activePokemonView(B).pokemonId());
        check(count(m,B,m.activePokemonView(B).pokemonId(),1),"场地未覆盖控制者一方");m.endTurn(B);m.attack(A,"hit");
        check(m.activePokemonView(B).damage()==80,"特殊能量的减伤效果没有被场地关闭");
    }
    static void sessionRetreat() {
        var m=fixture(conditional(),attack("{}",""));UUID host=m.activePokemonView(A).pokemonId(),bench=m.benchPokemonView(A).getFirst().pokemonId();
        var card=hand(m,A,"special");m.attachEnergy(A,card.instanceId(),host);var session=MatchSession.multiplayer(m,A,B);
        var view=session.view(A,A);var option=view.options().stream().filter(o->o.labelZh().startsWith("撤退 →")).findFirst();
        check(option.isPresent(),"会话仍按卡片张数过滤撤退：一个三单位能量没有撤退操作");
        session.submit(A,A,view.revision(),option.orElseThrow().index());RetreatEnergyChoiceTest.confirmSuggested(session,A);check(m.privateView(A).discard().contains(card)&&m.activePokemonView(A).pokemonId().equals(bench),"会话撤退没有使用当前供能结果");
        var mixed=fixture(conditional(),attack("{}",""));host=mixed.activePokemonView(A).pokemonId();
        var basic=hand(mixed,A,"energy");var triple=hand(mixed,A,"special");
        mixed.attachEnergy(A,basic.instanceId(),host);mixed.attachEnergyByEffect(A,A,EffectSourceZone.HAND,triple.instanceId(),A,host);
        check(mixed.suggestedRetreatPayment(A).orElseThrow().equals(List.of(triple.instanceId())),"建议支付包含多余的基本能量");
        mixed.evolve(A,host,hand(mixed,A,"evolved").instanceId());check(mixed.suggestedRetreatPayment(A).isEmpty(),"进化后费用不足仍有撤退建议");
        mixed.modifyRetreatCostByEffect(A,A,host,-3);check(mixed.suggestedRetreatPayment(A).orElseThrow().isEmpty(),"零费用撤退仍选择弃置能量");
    }
    static void reject(Runnable r) {EventTargetBindingTest.reject(r);}
    static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
