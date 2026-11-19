import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import com.google.gson.JsonParser;
import java.util.*;

/** Typed supply is not payment, and supplied units are not physical Energy cards. */
public final class TypedEnergyCountTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B,WATCH=new UUID(0,8503);
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static void reject(Runnable r){BeforeDamageAttackTest.reject(r);}
    public static void main(String[] args) {
        unitsAndPayment(); serverCounts(); aggregateDamage(); aggregateConditions(); dynamicSources(); preDamageAttachment(); conditionsInAbilities(); invalidData(); fairyData();
        System.out.println("TypedEnergyCountTest passed: typed supply, units/cards, source kind, field sums, live queries and stage continuation");
    }
    static AttackDefinition attack(String target,String fields) {
        return DynamicEnergyProvisionTest.attack("{}","{\"type\":\"damage_per_energy\",\"target\":\""+target+"\",\"amount\":30"+fields+"}");
    }
    static EnergyProfile water(){return EnergyProfile.fixed(EnergyType.WATER,2);}
    static ServerMatch fixture(AttackDefinition attack) {return DynamicEnergyProvisionTest.fixture(water(),attack);}
    static void attach(ServerMatch m,UUID owner,UUID host,String id) {
        m.attachEnergyByEffect(A,owner,EffectSourceZone.HAND,DynamicPassiveModifierTest.hand(m,owner,id).instanceId(),owner,host);
    }
    static ConditionDefinition condition(ConditionType kind,Map<String,String> fields) {return new ConditionDefinition(kind,fields,List.of());}
    static boolean count(ServerMatch m,UUID owner,UUID host,int expected,String type,String mode,String cardKind) {
        var fields=new LinkedHashMap<String,String>();fields.put("value",""+expected);fields.put("comparison","=");
        if(type!=null)fields.put("energy_type",type);if(mode!=null)fields.put("count_mode",mode);if(cardKind!=null)fields.put("card_kind",cardKind);
        return m.matchesEffectCondition(owner,host,condition(ConditionType.ENERGY_COUNT,fields));
    }
    static void unitsAndPayment() {
        var multi=new EnergyProfile(List.of(new EnergyProfile.Unit(Set.of(EnergyType.GRASS,EnergyType.FIRE,EnergyType.WATER))));
        check(multi.unitCount()==1&&multi.unitsOfType(EnergyType.WATER)==1&&multi.unitsOfType(EnergyType.FIRE)==1,"多属性单位重复计数");
        check(multi.unitsOfType(EnergyType.COLORLESS)==0&&EnergyProfile.canPay(List.of(multi),Map.of(EnergyType.COLORLESS,1)),"无色费用与提供无色能量混淆");
        check(!EnergyProfile.canPay(List.of(multi),Map.of(EnergyType.WATER,1,EnergyType.FIRE,1)),"类型查询让一个单位支付两次");
        var entries=List.of(new EnergyCountQuery.Contribution(multi,false),new EnergyCountQuery.Contribution(water(),false),
            new EnergyCountQuery.Contribution(EnergyProfile.basic(EnergyType.GRASS),true));
        check(EnergyCountQuery.ALL_UNITS.count(entries)==4,"通用单位总数错误");
        check(EnergyCountQuery.from(Map.of("energy_type","WATER")).count(entries)==3,"指定水能量单位错误");
        check(EnergyCountQuery.from(Map.of("energy_type","WATER","count_mode","CARDS")).count(entries)==2,"能量实体张数按单位计算");
        check(EnergyCountQuery.from(Map.of("card_kind","BASIC")).count(entries)==1,"基本卡供能过滤错误");
        check(EnergyCountQuery.from(Map.of("card_kind","SPECIAL","count_mode","CARDS")).count(entries)==2,"特殊能量卡张数错误");
        reject(()->DynamicEnergyProvisionTest.conditional().unitsOfType(EnergyType.GRASS));
    }
    static void serverCounts() {
        var tool=DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"attack_damage_modifier\",\"direction\":\"DEALT\",\"amount\":30,\"conditions\":[{\"type\":\"ENERGY_COUNT\",\"energy_type\":\"WATER\",\"value\":2}]}");
        var m=DynamicPassiveModifierTest.fixture(tool,DynamicPassiveModifierTest.plainStadium(),water(),attack("SELF",",\"energy_type\":\"WATER\""));
        UUID host=m.activePokemonView(A).pokemonId();attach(m,A,host,"special");attach(m,A,host,"energy");
        check(count(m,A,host,2,"WATER",null,null)&&count(m,A,host,1,"GRASS",null,null)&&count(m,A,host,3,null,null,null),"服务器按附着张数或全部单位代替属性计数");
        check(count(m,A,host,1,"WATER","CARDS",null)&&count(m,A,host,0,"WATER",null,"BASIC"),"实体／基本能源过滤错误");
        check(!m.matchesEffectCondition(A,host,condition(ConditionType.HAS_ENERGY,Map.of("energy_type","FIRE"))),"任意能量被当成指定属性");
        m.playTrainer(A,DynamicPassiveModifierTest.hand(m,A,"tool").instanceId(),host);var rng=m.randomTranscript();
        m.clientView(A);m.clientView(B);check(rng.equals(m.randomTranscript()),"能量只读查询消耗随机数");
        m.attack(A,"hit");check(m.activePokemonView(B).damage()==200,"类型计数、动态条件增伤与弱点顺序错误");
    }
    static void aggregateDamage() {
        for(String target:List.of("ALL_OWN_POKEMON","OWN_BENCH","ALL_OPPONENT_POKEMON","OPPONENT_BENCH")) {
            var m=fixture(attack(target,",\"energy_type\":\"WATER\""));
            attach(m,A,m.activePokemonView(A).pokemonId(),"special");attach(m,A,m.benchPokemonView(A).getFirst().pokemonId(),"special");
            attach(m,B,m.activePokemonView(B).pokemonId(),"special");
            int expected=switch(target){case "ALL_OWN_POKEMON"->260;case "OPPONENT_BENCH"->20;default->140;};
            m.attack(A,"hit");check(m.activePokemonView(B).damage()==expected,"全场／备战属性计数范围错误："+target);
        }
        var cards=fixture(attack("ALL_OWN_POKEMON",",\"energy_type\":\"WATER\",\"count_mode\":\"CARDS\""));
        attach(cards,A,cards.activePokemonView(A).pokemonId(),"special");attach(cards,A,cards.benchPokemonView(A).getFirst().pokemonId(),"special");
        cards.attack(A,"hit");check(cards.activePokemonView(B).damage()==140,"全场实体能量卡数被当成四单位");
        var empty=fixture(attack("OWN_BENCH",",\"energy_type\":\"WATER\""));
        empty.movePokemonByEffect(A,A,empty.benchPokemonView(A).getFirst().pokemonId(),LeavePlayDestination.HAND);empty.attack(A,"hit");
        check(empty.activePokemonView(B).damage()==20,"空备战范围未按零计数");
    }
    static void aggregateConditions() {
        var ability=BundledCombatData.ability(JsonParser.parseString("""
            {"id":"empty-bench-energy","nameZh":"空备战能量条件","effectSupport":"SUPPORTED","effects":[
              {"type":"draw","amount":1,"conditions":[{"type":"ENERGY_COUNT","target":"OWN_BENCH","energy_type":"WATER","value":0,"comparison":"="}]}]}
            """).getAsJsonObject(),EffectRegistry.standard());
        var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),water(),attack("SELF",""),List.of(ability));
        var host=m.activePokemonView(A).pokemonId();attach(m,A,host,"special");
        var count=new ConditionDefinition(ConditionType.ENERGY_COUNT,Map.of("target","ALL_OWN_POKEMON","energy_type","WATER","value","2","comparison","="),List.of());
        check(m.matchesEffectCondition(A,host,count),"全场能量条件未汇总");
        m.movePokemonByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId(),LeavePlayDestination.HAND);
        check(m.canUseAbility(A,host,ability.id()),"空备战能量为零被当成不存在条件目标");
        int hand=m.privateView(A).hand().size();m.useAbility(A,host,ability.id());check(m.privateView(A).hand().size()==hand+1,"空范围计数条件未结算");
        check(!m.matchesEffectCondition(A,host,condition(ConditionType.HAS_ENERGY,Map.of("target","OWN_BENCH","energy_type","WATER"))),"空备战仍有能量");
        var groups=fixture(attack("SELF",""));var own=groups.activePokemonView(A).pokemonId();
        attach(groups,A,own,"special");attach(groups,A,groups.benchPokemonView(A).getFirst().pokemonId(),"special");
        attach(groups,B,groups.activePokemonView(B).pokemonId(),"special");
        var rng=groups.randomTranscript();
        for(var entry:Map.of("ALL_OWN_POKEMON",4,"OWN_BENCH",2,"ALL_OPPONENT_POKEMON",2,"OPPONENT_BENCH",0).entrySet()) {
            var fields=Map.of("target",entry.getKey(),"energy_type","WATER","value",""+entry.getValue(),"comparison","=");
            check(groups.matchesEffectCondition(A,own,condition(ConditionType.ENERGY_COUNT,fields)),"范围计数错误："+entry.getKey());
            check(groups.matchesEffectCondition(A,own,condition(ConditionType.HAS_ENERGY,Map.of("target",entry.getKey(),"energy_type","WATER")))==(entry.getValue()>0),"范围存在条件错误");
        }
        check(rng.equals(groups.randomTranscript()),"全场条件查询消耗随机数");
    }
    static void dynamicSources() {
        var dynamic=DynamicEnergyProvisionTest.energy("""
            {"energyUnits":[["COLORLESS"]],"provisionRules":[{"conditions":[{"type":"IS_BASIC"}],"energyUnits":[["WATER"],["WATER"],["WATER"]]}]}
            """);
        var stadium=DynamicPassiveModifierTest.trainer("STADIUM","{"+DynamicEnergyProvisionTest.OVERRIDE+",\"target\":\"ALL_OWN_POKEMON\"}");
        var m=DynamicEnergyProvisionTest.fixture(dynamic,stadium,attack("SELF",",\"energy_type\":\"WATER\""));var host=m.activePokemonView(A).pokemonId();attach(m,A,host,"special");attach(m,A,host,"energy");
        check(count(m,A,host,3,"WATER",null,null),"条件供能未进入属性计数");
        m.playTrainer(A,DynamicPassiveModifierTest.hand(m,A,"stadium").instanceId(),null);
        check(count(m,A,host,0,"WATER",null,null)&&count(m,A,host,1,"COLORLESS",null,null)&&count(m,A,host,1,"GRASS",null,"BASIC"),"场地覆盖未重算类型或影响了基本能量");
        m.discardStadiumByEffect(A);check(count(m,A,host,3,"WATER",null,null),"场地离场后类型未恢复");
        m.evolve(A,host,DynamicPassiveModifierTest.hand(m,A,"evolved").instanceId());
        check(count(m,A,host,0,"WATER",null,null)&&count(m,A,host,1,"COLORLESS",null,null),"进化后沿用旧宿主供能类型");
    }
    static void preDamageAttachment() {
        var a=attack("SELF",",\"energy_type\":\"GRASS\"");
        a=a.withEffectProgram(new EffectProgram(List.of(
            BeforeDamageAttackTest.binding(EffectTrigger.BEFORE_ATTACK,"{\"type\":\"attach_energy\",\"source\":\"HAND\",\"target\":\"OWN_ACTIVE\",\"target_scope\":\"OWN_ACTIVE\",\"amount\":2,\"minimum\":2,\"basic_energy_only\":true,\"energy_type\":\"GRASS\"}"),
            a.effectProgram().triggers().getFirst())));
        var m=fixture(a);var host=m.activePokemonView(A).pokemonId();attach(m,A,host,"special");int turn=m.turnNumber();m.attack(A,"hit");
        var p=m.effectCardPrompt(A);check(m.activePokemonView(B).damage()==0,"附能选择前已经伤害");
        m.submitEffectCards(A,p.resolutionId(),p.step(),p.candidates().stream().limit(2).map(CardInstance::instanceId).toList());
        var target=m.effectAttachmentPrompt(A);m.chooseEffectAttachmentTarget(A,target.resolutionId(),target.step(),host);
        check(m.activePokemonView(B).damage()==140&&m.turnNumber()==turn+1,"伤害前新增属性能量没有计数或重复推进");
    }
    static void conditionsInAbilities() {
        var ability=BundledCombatData.ability(JsonParser.parseString("""
            {"id":"typed","nameZh":"属性条件测试","effectSupport":"SUPPORTED","effects":[{"type":"draw","amount":1,"conditions":[{"type":"HAS_ENERGY","energy_type":"WATER"}]}]}
            """).getAsJsonObject(),EffectRegistry.standard());
        var multi=new EnergyProfile(List.of(new EnergyProfile.Unit(Set.of(EnergyType.WATER,EnergyType.FIRE))));
        var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),multi,attack("SELF",",\"energy_type\":\"WATER\""),List.of(ability));
        UUID host=m.activePokemonView(A).pokemonId();check(!m.canUseAbility(A,host,"typed"),"没有水能量仍允许特性");attach(m,A,host,"special");
        var both=new ConditionDefinition(ConditionType.AND,Map.of(),List.of(condition(ConditionType.HAS_ENERGY,Map.of("energy_type","WATER")),condition(ConditionType.HAS_ENERGY,Map.of("energy_type","FIRE"))));
        check(m.matchesEffectCondition(A,host,both),"多属性同时存在条件错误套用支付匹配");
        int hand=m.privateView(A).hand().size();m.useAbility(A,host,"typed");check(m.privateView(A).hand().size()==hand+1,"类型条件没有进入主动特性程序");
    }
    static void invalidData() {
        for(String fields:List.of(",\"energy_type\":\"BAD\"",",\"count_mode\":\"ENERGIES\"",",\"card_kind\":\"TRAINER\"",",\"energy_typo\":\"WATER\""))
            reject(()->AttackProgramRules.validate(attack("SELF",fields)));
        for(String target:List.of("HAND","DECK","DISCARD","ENERGY","SELECT_OWN_POKEMON","CHOSEN_POKEMON"))reject(()->AttackProgramRules.validate(attack(target,",\"energy_type\":\"WATER\"")));
        reject(()->DynamicEnergyProvisionTest.energy("{\"energyUnits\":[[\"WATER\"]],\"provisionRules\":[{\"conditions\":[{\"type\":\"ENERGY_COUNT\",\"energy_type\":\"WATER\",\"value\":1}],\"energyUnits\":[[\"WATER\"],[\"WATER\"]]}]}"));
        DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"hit_points_modifier\",\"amount\":100,\"conditions\":[{\"type\":\"HAS_ENERGY\",\"energy_type\":\"WATER\"}]}");
        reject(()->BundledCombatData.ability(JsonParser.parseString("{\"id\":\"bad\",\"nameZh\":\"坏条件\",\"effectSupport\":\"SUPPORTED\",\"effects\":[{\"type\":\"draw\",\"amount\":1,\"conditions\":[{\"type\":\"HAS_ENERGY\",\"energy_type\":\"INVALID\"}]}]}").getAsJsonObject(),EffectRegistry.standard()));
        reject(()->BundledCombatData.ability(JsonParser.parseString("{\"id\":\"misplaced\",\"nameZh\":\"错位条件\",\"effectSupport\":\"SUPPORTED\",\"conditions\":[{\"type\":\"HAS_ENERGY\",\"energy_type\":\"WATER\"}],\"effects\":[{\"type\":\"draw\",\"amount\":1}]}").getAsJsonObject(),EffectRegistry.standard()));
    }
    static void fairyData() {
        var energy=DynamicEnergyProvisionTest.energy("{\"energyUnits\":[[\"FAIRY\"]]}");
        check(energy.unitsOfType(EnergyType.FAIRY)==1&&EnergyProfile.canPay(List.of(energy),Map.of(EnergyType.FAIRY,1)),"历史妖精属性无法载入／支付");
        var m=DynamicEnergyProvisionTest.fixture(energy,attack("SELF",",\"energy_type\":\"FAIRY\""));attach(m,A,m.activePokemonView(A).pokemonId(),"special");
        m.attack(A,"hit");check(m.activePokemonView(B).damage()==80,"历史妖精供能计数错误");
    }
}
