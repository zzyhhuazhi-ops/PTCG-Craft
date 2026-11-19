import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Synthetic programs: tests reusable persistent sources, not unreviewed real-card bindings. */
public final class DynamicPassiveModifierTest {
    static final UUID A=new UUID(0,8201),B=new UUID(0,8202);
    public static void main(String[] args) {
        dynamicRetreat(); damageLayers(); energyLifecycle(); capacity(); invalidPrograms(); sourceImmunity(); resistanceIntermediate(); prizeModifier();
        System.out.println("DynamicPassiveModifierTest passed: live persistent modifier sources and conditions");
    }
    static TrainerProfile trainer(String category,String effects) {
        return BundledCombatData.trainer(JsonParser.parseString("{\"trainerCategory\":\""+category+"\",\"triggers\":[{\"trigger\":\"PASSIVE\",\"effects\":["+effects+"]}]}").getAsJsonObject(),EffectRegistry.standard());
    }
    static void dynamicRetreat() {
        var tool=trainer("TOOL","""
            {"type":"retreat_cost_modifier","amount":-1},
            {"type":"conditional","conditions":[{"type":"DAMAGE_COUNTER_COUNT","value":2}],"effects":[{"type":"retreat_cost_modifier","amount":-1}]}
            """);
        var stadium=trainer("STADIUM","""
            {"type":"retreat_cost_modifier","target":"ALL_OWN_POKEMON","amount":-1,"conditions":[{"type":"IS_BASIC"}]},
            {"type":"retreat_cost_modifier","target":"ALL_OPPONENT_POKEMON","amount":-1,"conditions":[{"type":"IS_BASIC"}]}
            """);
        var m=fixture(tool,stadium);UUID active=m.activePokemonView(A).pokemonId();
        check(m.activeRetreatCost(A)==3,"初始撤退费用错误");
        var card=hand(m,A,"tool");m.playTrainer(A,card.instanceId(),active);
        check(m.activeRetreatCost(A)==2,"动态工具没有作用");
        m.placeDamageCountersByEffect(A,A,active,2);check(m.activeRetreatCost(A)==1,"伤害条件变化没有立即重算");
        m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);
        check(m.activeRetreatCost(A)==0&&m.activeRetreatCost(B)==2,"场地没有按双方当前宝可梦计算");
        m.evolve(A,active,hand(m,A,"evolved").instanceId());check(m.activeRetreatCost(A)==1,"进化后基础限定场地残留");
        m.disableToolEffectsByEffect(A,A,active);check(m.activeRetreatCost(A)==3,"工具失效未取消动态修正");
        m.discardToolByEffect(A,A,active,card.instanceId());check(m.privateView(A).discard().contains(card)&&m.activeRetreatCost(A)==3,"弃置工具留下缓存");
        m.discardStadiumByEffect(A);check(m.activeRetreatCost(B)==3,"场地离场后效果残留");
    }
    static ServerMatch fixture(TrainerProfile tool,TrainerProfile stadium) {
        return fixture(tool,stadium,EnergyProfile.fixed(EnergyType.COLORLESS,2),attack(40,false,false));
    }
    static AttackDefinition attack(int damage,boolean bypass,boolean bench) {
        return BundledCombatData.attack(JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"测试攻击\",\"energyCost\":{},\"baseDamage\":"+damage+
            ",\"damageType\":\"GRASS\",\"effectSupport\":\"SUPPORTED\",\"ignoresActiveDefenderDamageEffects\":"+bypass+
            (bench?",\"damageTarget\":\"ALL_OPPONENT_BENCH\"":"")+"}").getAsJsonObject(),EffectRegistry.standard());
    }
    static ServerMatch fixture(TrainerProfile tool,TrainerProfile stadium,EnergyProfile special,AttackDefinition attack) {
        return fixture(tool,stadium,special,attack,List.of());
    }
    static ServerMatch fixture(TrainerProfile tool,TrainerProfile stadium,EnergyProfile special,AttackDefinition attack,List<AbilityDefinition> abilities) {
        return fixture(tool,stadium,special,attack,abilities,0);
    }
    static ServerMatch fixture(TrainerProfile tool,TrainerProfile stadium,EnergyProfile special,AttackDefinition attack,List<AbilityDefinition> abilities,int resistance) {
        return fixture(tool,stadium,special,attack,abilities,resistance,null);
    }
    static ServerMatch fixture(TrainerProfile tool,TrainerProfile stadium,EnergyProfile special,AttackDefinition attack,List<AbilityDefinition> abilities,int resistance,TrainerProfile item) {
        return fixture(tool,stadium,special,attack,abilities,resistance,item,Map.of());
    }
    static ServerMatch fixture(TrainerProfile tool,TrainerProfile stadium,EnergyProfile special,AttackDefinition attack,List<AbilityDefinition> abilities,int resistance,TrainerProfile item,Map<String,EnergyType> pokemonTypes) {
        return fixture(tool,stadium,special,attack,abilities,resistance,item,pokemonTypes,null);
    }
    static ServerMatch fixture(TrainerProfile tool,TrainerProfile stadium,EnergyProfile special,AttackDefinition attack,List<AbilityDefinition> abilities,int resistance,TrainerProfile item,Map<String,EnergyType> pokemonTypes,String pokemonName) {
        String[] ids=item==null?new String[]{"basic","basic","evolved","tool","stadium","energy","special"}:new String[]{"basic","basic","evolved","tool","stadium","energy","special","item"};
        var defs=new ArrayList<CardDefinition>();
        for(String id:Arrays.stream(ids).distinct().toList()) {
            boolean p=id.equals("basic")||id.equals("evolved"),e=id.equals("energy")||id.equals("special");
            var category=p||e?TrainerCategory.NONE:id.equals("tool")?TrainerCategory.TOOL:id.equals("item")?item.category():TrainerCategory.STADIUM;
            defs.add(new CardDefinition(id,id,p?CardCategory.POKEMON:e?CardCategory.ENERGY:CardCategory.TRAINER,category,"H",Set.of("international"),id.equals("basic"),id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null));
        }
        var trainers=new HashMap<>(Map.of("tool",tool,"stadium",stadium));if(item!=null)trainers.put("item",item);
        var combat=new CombatCatalog(Map.of("basic",new PokemonProfile(300,3,null,EnergyType.GRASS,2,resistance>0?EnergyType.GRASS:null,resistance,1,List.of(attack),abilities,pokemonTypes.get("basic"),pokemonName,null),
            "evolved",new PokemonProfile(350,3,"basic",EnergyType.GRASS,2,resistance>0?EnergyType.GRASS:null,resistance,1,List.of(attack),abilities,pokemonTypes.get("evolved"))),Map.of("energy",EnergyType.GRASS),trainers,Map.of("special",special));
        for(int seed=0;seed<1000;seed++) {
            var decks=new ArrayList<List<CardInstance>>();
            for(UUID owner:List.of(A,B)) {var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(defs),combat,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("basic")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,30);m.drawCardsByEffect(A,B,30);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(p->Arrays.stream(ids).allMatch(id->m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals(id)))))return m;
        }
        throw new AssertionError("动态持续效果夹具未生成");
    }
    static CardInstance hand(ServerMatch m,UUID owner,String id) {return m.privateView(owner).hand().stream().filter(c->c.definitionId().equals(id)).findFirst().orElseThrow();}
    static EnergyProfile energy(String effects) {
        return BundledCombatData.specialEnergy(JsonParser.parseString("{\"energyUnits\":[[\"COLORLESS\"],[\"COLORLESS\"]],\"effects\":["+effects+"]}").getAsJsonObject(),EffectRegistry.standard());
    }
    static TrainerProfile plainTool() {return trainer("TOOL","{\"type\":\"retreat_cost_modifier\",\"amount\":-1}");}
    static TrainerProfile plainStadium() {return trainer("STADIUM","{\"type\":\"retreat_cost_modifier\",\"target\":\"ALL_OWN_POKEMON\",\"amount\":-1}");}
    static void damageLayers() {
        var tool=trainer("TOOL","{\"type\":\"attack_damage_modifier\",\"direction\":\"DEALT\",\"amount\":20}");
        var stadium=trainer("STADIUM","{\"type\":\"attack_damage_modifier\",\"target\":\"ALL_OPPONENT_POKEMON\",\"direction\":\"RECEIVED\",\"amount\":-10}");
        var energy=energy("{\"type\":\"attack_damage_modifier\",\"direction\":\"DEALT\",\"amount\":-20}");
        for(boolean bypass:List.of(false,true))for(boolean bench:List.of(false,true)) {
            if(bypass&&bench)continue;
            var m=fixture(tool,stadium,energy,attack(40,bypass,bench));UUID source=m.activePokemonView(A).pokemonId();
            m.playTrainer(A,hand(m,A,"tool").instanceId(),source);m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);m.attachEnergy(A,hand(m,A,"special").instanceId(),source);
            m.attack(A,"hit");int damage=bench?m.benchPokemonView(B).getFirst().damage():m.activePokemonView(B).damage();
            check(damage==(bench?30:bypass?80:70),"攻击方修正在弱点前、受伤方修正在弱点后或备战/穿透处理错误");
        }
        var zero=fixture(tool,stadium,energy,attack(0,false,false));zero.playTrainer(A,hand(zero,A,"tool").instanceId(),zero.activePokemonView(A).pokemonId());
        zero.attack(A,"hit");check(zero.activePokemonView(B).damage()==0,"伤害修正把零伤害攻击变成了伤害");
        var counters=fixture(tool,stadium);counters.playTrainer(A,hand(counters,A,"stadium").instanceId(),null);
        counters.placeDamageCountersByEffect(A,B,counters.activePokemonView(B).pokemonId(),2);check(counters.activePokemonView(B).damage()==20,"持续伤害修正误减伤害指示物");
    }
    static void energyLifecycle() {
        var energy=energy("""
            {"type":"retreat_cost_modifier","amount":-1},
            {"type":"retreat_cost_modifier","amount":-1,"conditions":[{"type":"ENERGY_COUNT","value":2}]}
            """);
        var m=fixture(plainTool(),plainStadium(),energy,attack(40,false,false));UUID active=m.activePokemonView(A).pokemonId(),bench=m.benchPokemonView(A).getFirst().pokemonId();
        var card=hand(m,A,"special");m.attachEnergy(A,card.instanceId(),active);check(m.activeRetreatCost(A)==1,"特殊能量持续修正未生效或双能量被当成一单位");
        m.moveAttachedEnergyByEffect(A,A,active,A,bench,List.of(card.instanceId()));check(m.activeRetreatCost(A)==3,"能量移动后旧宿主仍有修正");
        m.switchActiveByEffect(A,A,bench);check(m.activeRetreatCost(A)==1,"能量移动后新宿主没有修正");
        m.returnAttachedEnergyToHandByEffect(A,A,bench,List.of(card.instanceId()));check(m.activeRetreatCost(A)==3&&m.privateView(A).hand().contains(card),"能量回手后仍有修正或卡牌丢失");
        check(EnergyProfile.canPay(List.of(energy),Map.of(EnergyType.COLORLESS,2)),"持续程序破坏了固定供能算法");
    }
    static void capacity() {
        var tool=trainer("TOOL","{\"type\":\"tool_capacity\",\"amount\":2},{\"type\":\"retreat_cost_modifier\",\"amount\":-1}");
        var m=fixture(tool,plainStadium());UUID target=m.activePokemonView(A).pokemonId();
        m.playTrainer(A,hand(m,A,"tool").instanceId(),target);m.playTrainer(A,hand(m,A,"tool").instanceId(),target);
        check(m.activePokemonView(A).attachedTools().size()==2&&m.activeRetreatCost(A)==1,"容量取最大值或修正逐来源叠加错误");
        var before=m.clientView(A);var third=hand(m,A,"tool");reject(()->m.playTrainer(A,third.instanceId(),target));check(before.equals(m.clientView(A)),"超容量附着破坏状态");
        m.disableToolEffectsByEffect(A,A,target);check(!m.canPlayTrainer(A,third.instanceId(),target)&&m.activeRetreatCost(A)==3,"失效工具仍提供容量或费用修正");
    }
    static void invalidPrograms() {
        for(String effects:List.of("{\"type\":\"draw\",\"amount\":1}","{\"type\":\"retreat_cost_modifier\",\"amount\":-1,\"ignored\":true}",
            "{\"type\":\"retreat_cost_modifier\",\"amount\":-1,\"conditions\":[{\"type\":\"COIN_RESULT\",\"value\":\"HEADS\"}]}",
            "{\"type\":\"retreat_cost_modifier\",\"target\":\"SELECT_OWN_POKEMON\",\"amount\":-1}"))reject(()->trainer("TOOL",effects));
        reject(()->trainer("STADIUM","{\"type\":\"retreat_cost_modifier\",\"amount\":-1}"));
        reject(()->energy("{\"type\":\"draw\",\"amount\":1}"));
        var modifier=EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"retreat_cost_modifier\",\"amount\":-1}").getAsJsonObject());
        check(EffectExecutionCapabilityCatalog.assess(modifier).capability()==EffectExecutionCapability.REQUIRES_PERSISTENT_SOURCE,"持续修正误分类为一次性动作");
        var ability=ResumableAbilityTest.ability("bad","{\"type\":\"draw\",\"amount\":1},{\"type\":\"retreat_cost_modifier\",\"amount\":-1}");
        var m=ResumableAbilityTest.fixture(ability);var before=m.clientView(ResumableAbilityTest.A);
        reject(()->m.useAbility(ResumableAbilityTest.A,m.activePokemonView(ResumableAbilityTest.A).pokemonId(),"bad"));check(before.equals(m.clientView(ResumableAbilityTest.A)),"持续修正从主动入口执行了前缀");
    }
    static void sourceImmunity() {
        for(String source:List.of("TRAINER","ENERGY","ANY_CARD","ABILITY")) {
            var guard=ResumableAbilityTest.ability("guard",EffectOriginProtectionTest.shield(source,"EFFECTS_ONLY","SELF","LEAVES_PLAY"));
            String aura="{\"type\":\"retreat_cost_modifier\",\"target\":\"ALL_OPPONENT_POKEMON\",\"amount\":1}";
            var m=fixture(plainTool(),trainer("STADIUM",aura),energy(aura),attack(40,false,false),List.of(guard));
            m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);m.attachEnergy(A,hand(m,A,"special").instanceId(),m.activePokemonView(A).pokemonId());
            check(m.activeRetreatCost(B)==5,"对手来源光环未生效");m.endTurn(A);m.useAbility(B,m.activePokemonView(B).pokemonId(),"guard");
            check(m.activeRetreatCost(B)==(source.equals("ANY_CARD")?3:source.equals("ABILITY")?5:4),"持续来源绕过或混淆效果免疫："+source);
        }
    }
    static void resistanceIntermediate() {
        var stadium=trainer("STADIUM","{\"type\":\"attack_damage_modifier\",\"target\":\"ALL_OPPONENT_POKEMON\",\"direction\":\"RECEIVED\",\"amount\":20}");
        for(int base:List.of(10,20)) {
            var m=fixture(plainTool(),stadium,EnergyProfile.fixed(EnergyType.COLORLESS,2),attack(base,false,false),List.of(),30);
            m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);m.attack(A,"hit");
            check(m.activePokemonView(B).damage()==(base*2-30<=0?0:base*2-30+20),"抵抗力降至零应终止计算；正数分支继续接收修正");
        }
        var tool=trainer("TOOL","{\"type\":\"attack_damage_modifier\",\"direction\":\"DEALT\",\"amount\":-20}");
        var m=fixture(tool,stadium);m.playTrainer(A,hand(m,A,"tool").instanceId(),m.activePokemonView(A).pokemonId());
        m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);m.attack(A,"hit");check(m.activePokemonView(B).damage()==60,"攻击来源修正时点错误");
        var stopped=fixture(tool,stadium,EnergyProfile.fixed(EnergyType.COLORLESS,2),attack(10,false,false));
        stopped.playTrainer(A,hand(stopped,A,"tool").instanceId(),stopped.activePokemonView(A).pokemonId());stopped.playTrainer(A,hand(stopped,A,"stadium").instanceId(),null);
        stopped.attack(A,"hit");check(stopped.activePokemonView(B).damage()==0,"基础计算已停止的攻击被受伤方增伤复活");
    }
    static void prizeModifier() {
        var ability=BundledCombatData.ability(JsonParser.parseString("""
            {"id":"prize-aura","nameZh":"奖赏夹具","kind":"PASSIVE","effectSupport":"SUPPORTED",
             "effects":[{"type":"prize_card_modifier","amount":1}]}
            """).getAsJsonObject(),EffectRegistry.standard());
        var m=fixture(plainTool(),plainStadium(),EnergyProfile.fixed(EnergyType.COLORLESS,2),attack(40,false,false),List.of(ability));
        UUID target=m.activePokemonView(B).pokemonId();m.placeDamageCountersByEffect(A,B,target,30);
        check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.pendingChoicePlayer().equals(A),"数据驱动奖赏修正没有进入拿奖流程");
        m.takePrizeAt(A,0);check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.pendingChoicePlayer().equals(A),"奖赏修正没有冻结为第二张奖赏");
        m.takePrizeAt(A,0);check(m.privateView(A).prizes().size()==4,"数据驱动奖赏修正张数错误");
        reject(()->trainer("TOOL","{\"type\":\"prize_card_modifier\",\"amount\":0}"));
    }
    static void reject(Runnable action) {EventTargetBindingTest.reject(action);}
    static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
