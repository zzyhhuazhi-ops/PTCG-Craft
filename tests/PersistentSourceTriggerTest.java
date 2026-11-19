import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Synthetic card data proves reusable triggers; no real-card binding is claimed. */
public final class PersistentSourceTriggerTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static final String DAMAGE="{\"trigger\":\"ON_DAMAGE\",\"from\":\"OPPONENT_ATTACK\",\"activeOnly\":true,\"lethalOnly\":false,\"optional\":false,\"usage\":\"NONE\"}";
    static final String CHECKUP="{\"trigger\":\"ON_POKEMON_CHECKUP\",\"optional\":false,\"usage\":\"NONE\"}";
    static final String ENERGY="{\"trigger\":\"ON_ENERGY_ATTACHED\",\"target\":\"SELF\",\"source\":\"HAND\",\"cause\":\"MANUAL\",\"optional\":false,\"usage\":\"NONE\"}";
    public static void main(String[] args) {
        toolReaction(); toolDisabled(); energySuppressed(); stadiumCheckup(); knockoutEnergy(); sourceKindsAndChoice(); sourceOnlyAndLimits(); sharedAndDeclinedLimits(); invalidPrograms();
        System.out.println("PersistentSourceTriggerTest passed: Tool, Stadium and Energy share authoritative event continuation");
    }
    static String trigger(String id,String policy,String effects) {return "{\"id\":\""+id+"\",\"nameZh\":\"通用来源测试\",\"eventPolicy\":"+policy+",\"effects\":["+effects+"]}";}
    static TrainerProfile trainer(String category,String automatic) {
        String scope=category.equals("STADIUM")?",\"target\":\"ALL_OWN_POKEMON\"":"";
        return BundledCombatData.trainer(JsonParser.parseString("{\"trainerCategory\":\""+category+"\",\"effects\":[{\"type\":\"retreat_cost_modifier\",\"amount\":-1"+scope+"}],\"automaticEffects\":["+automatic+"]}").getAsJsonObject(),EffectRegistry.standard());
    }
    static EnergyProfile energy(String automatic) {
        return BundledCombatData.specialEnergy(JsonParser.parseString("{\"energyUnits\":[[\"COLORLESS\"]],\"automaticEffects\":["+automatic+"]}").getAsJsonObject(),EffectRegistry.standard());
    }
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static ServerMatch fixture(TrainerProfile tool,TrainerProfile stadium,EnergyProfile energy){return DynamicPassiveModifierTest.fixture(tool,stadium,energy,DynamicPassiveModifierTest.attack(40,false,false));}
    static void toolReaction() {
        var tool=trainer("TOOL",trigger("recoil",DAMAGE,"{\"type\":\"damage_counter\",\"target\":\"ATTACKING_POKEMON\",\"amount\":2}"));
        var m=fixture(tool,DynamicPassiveModifierTest.plainStadium(),EnergyProfile.basic(EnergyType.COLORLESS));
        m.playTrainer(A,hand(m,A,"tool").instanceId(),m.activePokemonView(A).pokemonId());m.endTurn(A);m.attack(B,"hit");
        check(m.activePokemonView(B).damage()==20,"工具受伤触发未走通用事件队列或没有命中实际攻击者");
    }
    static void toolDisabled() {
        var tool=trainer("TOOL",trigger("draw",DAMAGE,"{\"type\":\"draw\",\"amount\":2}"));
        var m=fixture(tool,DynamicPassiveModifierTest.plainStadium(),EnergyProfile.basic(EnergyType.COLORLESS));UUID host=m.activePokemonView(A).pokemonId();
        m.playTrainer(A,hand(m,A,"tool").instanceId(),host);m.disableToolEffectsByEffect(A,A,host);int size=m.privateView(A).hand().size();
        m.endTurn(A);m.attack(B,"hit");check(m.privateView(A).hand().size()==size+1,"失效工具仍触发抽牌");
    }
    static void energySuppressed() {
        for(boolean suppressed:List.of(false,true)) {
            var e=energy(trigger("draw",ENERGY,"{\"type\":\"draw\",\"amount\":2}"));
            var m=fixture(DynamicPassiveModifierTest.plainTool(),DynamicEnergyProvisionTest.stadium(),e);UUID host=m.activePokemonView(A).pokemonId();
            m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,hand(m,A,"special").instanceId(),A,host);
            if(suppressed)m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);
            int before=m.privateView(A).hand().size();m.attachEnergy(A,hand(m,A,"energy").instanceId(),host);
            check(m.privateView(A).hand().size()==before-1+(suppressed?0:2),"特殊能量监听或场地关闭触发错误");
        }
    }
    static void stadiumCheckup() {
        var s=trainer("STADIUM",trigger("checkup",CHECKUP,"{\"type\":\"heal\",\"target\":\"ALL_OWN_POKEMON\",\"amount\":10}"));
        var m=fixture(DynamicPassiveModifierTest.plainTool(),s,EnergyProfile.basic(EnergyType.COLORLESS));UUID host=m.activePokemonView(A).pokemonId();
        m.placeDamageCountersByEffect(A,A,host,2);m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);m.endTurn(A);
        while(m.triggerOrderPrompt()!=null) {var p=m.triggerOrderPrompt();m.chooseTriggeredEffect(p.choosingPlayer(),p.id(),p.entries().getFirst().id());}
        check(m.activePokemonView(A).damage()==10&&m.currentPlayer().equals(B),"场地回合间效果或原异常状态步骤未结算");
    }
    static void knockoutEnergy() {
        String ko="{\"trigger\":\"ON_KNOCKOUT\",\"cause\":\"OPPONENT_ATTACK_DAMAGE\",\"activeOnly\":true,\"optional\":false,\"usage\":\"NONE\"}";
        var e=energy(trigger("ko-draw",ko,"{\"type\":\"draw\",\"amount\":2}"));
        var m=fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),e);UUID host=m.activePokemonView(A).pokemonId();
        var card=hand(m,A,"special");m.attachEnergy(A,card.instanceId(),host);m.placeDamageCountersByEffect(A,A,host,24);int before=m.privateView(A).hand().size();m.endTurn(A);m.attack(B,"hit");
        check(m.privateView(A).hand().size()==before+2&&m.privateView(A).discard().contains(card),"击倒能量没有在移场前完成触发或归还弃牌区");
    }
    static void sourceKindsAndChoice() {
        for(boolean toolSource:List.of(false,true))for(String protection:List.of("ABILITY","TRAINER","ENERGY","ANY_CARD")) {
            var guard=BundledCombatData.ability(JsonParser.parseString("{\"id\":\"guard\",\"nameZh\":\"来源防护\",\"effectSupport\":\"SUPPORTED\",\"effects\":[{\"type\":\"prevent_effects\",\"target\":\"SELF\",\"source\":\""+protection+"\",\"from\":\"OPPONENT\",\"scope\":\"EFFECTS_ONLY\",\"duration\":\"SOURCE_NEXT_TURN_START\"}]}").getAsJsonObject(),EffectRegistry.standard());
            String body="{\"type\":\"draw\",\"amount\":1},{\"type\":\"choice\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"ATTACKING_POKEMON\",\"amount\":2},{\"type\":\"draw\",\"amount\":1}]}";
            String automatic=trigger("choice",DAMAGE,body);
            var tool=toolSource?trainer("TOOL",automatic):DynamicPassiveModifierTest.plainTool();
            var energy=toolSource?EnergyProfile.basic(EnergyType.COLORLESS):energy(automatic);
            var m=DynamicPassiveModifierTest.fixture(tool,DynamicPassiveModifierTest.plainStadium(),energy,DynamicPassiveModifierTest.attack(40,false,false),List.of(guard));
            UUID host=m.activePokemonView(A).pokemonId();if(toolSource)m.playTrainer(A,hand(m,A,"tool").instanceId(),host);else m.attachEnergy(A,hand(m,A,"special").instanceId(),host);
            int before=m.privateView(A).hand().size();m.endTurn(A);m.useAbility(B,m.activePokemonView(B).pokemonId(),"guard");m.attack(B,"hit");
            check(m.hasPendingEffectChoice()&&m.pendingChoicePlayer().equals(A)&&m.privateView(A).hand().size()==before+1,"非当前玩家来源没有暂停选择或重复执行前缀");
            var s=MatchSession.multiplayer(m,A,B);UUID watch=UUID.randomUUID();s.addSpectator(watch);
            var kind=toolSource?EffectOrigin.Kind.TRAINER:EffectOrigin.Kind.ENERGY;
            for(UUID viewer:List.of(A,B,watch)) {
                var snapshot=s.view(viewer,viewer.equals(watch)?null:viewer);String json=new com.google.gson.Gson().toJson(snapshot);
                check(snapshot.game().resolvingAbility().sourceKind()==kind,"公开来源类型伪装成特性");
                check(!json.contains("effectProgram")&&!json.contains("automaticEffects"),"规则程序泄露");
                for(UUID owner:List.of(A,B)) {
                    if(!viewer.equals(owner))for(var card:m.privateView(owner).hand())check(!json.contains(card.instanceId().toString()),"手牌泄露");
                    for(var card:m.privateView(owner).deck())check(!json.contains(card.instanceId().toString()),"牌库顺序泄露");
                }
            }
            var prompt=m.effectChoicePrompt(A);reject(()->m.chooseEffectOption(B,prompt.resolutionId(),prompt.step(),0));
            m.chooseEffectOption(A,prompt.resolutionId(),prompt.step(),0);boolean blocked=protection.equals("ANY_CARD")||protection.equals(toolSource?"TRAINER":"ENERGY");
            check(m.activePokemonView(B).damage()==(blocked?0:20)&&m.currentPlayer().equals(A),"选择续接后丢失真实来源或重复推进回合");
            reject(()->m.chooseEffectOption(A,prompt.resolutionId(),prompt.step(),0));
        }
    }
    static void sourceOnlyAndLimits() {
        String any=ENERGY.replace("\"MANUAL\"","\"ANY\"");
        String self=trigger("own",any,"{\"type\":\"draw\",\"amount\":1}");
        self=self.substring(0,self.length()-1)+",\"sourceCardOnly\":true}";
        var m=fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),energy(self));UUID host=m.activePokemonView(A).pokemonId();
        var e=hand(m,A,"special");int before=m.privateView(A).hand().size();m.attachEnergy(A,e.instanceId(),host);
        check(m.privateView(A).hand().size()==before,"监听自身附着未触发，或重复触发");
        m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,hand(m,A,"energy").instanceId(),A,host);
        check(m.privateView(A).hand().size()==before-1,"自身限定错误监听其它能量卡");
        String limited=any.replace("\"NONE\"","\"ONCE_PER_TURN\"");
        var limitedMatch=fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),energy(trigger("limit",limited,"{\"type\":\"draw\",\"amount\":1}")));
        host=limitedMatch.activePokemonView(A).pokemonId();UUID bench=limitedMatch.benchPokemonView(A).getFirst().pokemonId();var card=hand(limitedMatch,A,"special");
        limitedMatch.attachEnergy(A,card.instanceId(),host);int size=limitedMatch.privateView(A).hand().size();
        limitedMatch.moveAttachedEnergyByEffect(A,A,host,A,bench,List.of(card.instanceId()));limitedMatch.attachEnergyByEffect(A,A,EffectSourceZone.HAND,hand(limitedMatch,A,"energy").instanceId(),A,bench);
        check(limitedMatch.privateView(A).hand().size()==size-1,"场内移动重置同一来源的限次");
        limitedMatch.returnAttachedEnergyToHandByEffect(A,A,bench,List.of(card.instanceId()));int returned=limitedMatch.privateView(A).hand().size();
        limitedMatch.attachEnergyByEffect(A,A,EffectSourceZone.HAND,card.instanceId(),A,bench);check(limitedMatch.privateView(A).hand().size()==returned,"离场重新附着没有获得新入场身份");
    }
    static void sharedAndDeclinedLimits() {
        String policy=ENERGY.replace("\"MANUAL\"","\"ANY\"");
        String body=trigger("shared",policy,"{\"type\":\"draw\",\"amount\":1}");
        body=body.substring(0,body.length()-1)+",\"usageLimit\":{\"group\":\"persistent-test\",\"period\":\"GAME\"}}";
        var m=fixture(trainer("TOOL",body),DynamicPassiveModifierTest.plainStadium(),energy(body));UUID host=m.activePokemonView(A).pokemonId();
        m.playTrainer(A,hand(m,A,"tool").instanceId(),host);var card=hand(m,A,"special");int before=m.privateView(A).hand().size();m.attachEnergy(A,card.instanceId(),host);
        var prompt=m.triggerOrderPrompt();check(prompt!=null&&prompt.entries().size()==2&&prompt.choosingPlayer().equals(A),"不同来源未共享同一事件排序");
        check(prompt.entries().stream().map(TriggerOrderPrompt.Entry::sourceKind).distinct().count()==2,"同批工具和能量丢失各自来源");
        m.chooseTriggeredEffect(A,prompt.id(),prompt.entries().getFirst().id());
        check(m.triggerOrderPrompt()==null&&m.privateView(A).hand().size()==before,"跨来源共享次数没有裁掉第二条已捕获效果");
        m.returnAttachedEnergyToHandByEffect(A,A,host,List.of(card.instanceId()));int size=m.privateView(A).hand().size();
        m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,card.instanceId(),A,host);
        check(m.privateView(A).hand().size()==size-1&&m.triggerOrderPrompt()==null,"离场重进重置了玩家整局共享次数");

        var optional=energy(trigger("optional",policy.replace("\"optional\":false","\"optional\":true").replace("\"NONE\"","\"ONCE_PER_TURN\""),"{\"type\":\"draw\",\"amount\":1}"));
        var n=fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),optional);UUID target=n.activePokemonView(A).pokemonId();
        n.attachEnergy(A,hand(n,A,"special").instanceId(),target);var first=n.triggerOrderPrompt();n.decideTriggeredEffect(A,first.id(),false);
        n.attachEnergyByEffect(A,A,EffectSourceZone.HAND,hand(n,A,"energy").instanceId(),A,target);var again=n.triggerOrderPrompt();
        check(again!=null&&again.optionalDecision(),"放弃使用错误消耗了卡牌触发次数");size=n.privateView(A).hand().size();n.decideTriggeredEffect(A,again.id(),true);
        check(n.privateView(A).hand().size()==size+1,"可选来源没有只抽一次");
        n.attachEnergyByEffect(A,A,EffectSourceZone.HAND,hand(n,A,"energy").instanceId(),A,target);check(n.triggerOrderPrompt()==null,"已使用一次的可选来源再次触发");
    }
    static void invalidPrograms() {
        reject(()->trainer("STADIUM",trigger("bad",DAMAGE,"{\"type\":\"draw\",\"amount\":1}")));
        reject(()->trainer("STADIUM",trigger("bad",CHECKUP,"{\"type\":\"heal\",\"amount\":10}")));
        reject(()->trainer("TOOL",trigger("bad",DAMAGE,"{\"type\":\"draw\",\"amount\":1},{\"type\":\"copy_attack\",\"target\":\"OPPONENT_ACTIVE\"}")));
        String same=trigger("duplicate",ENERGY,"{\"type\":\"draw\",\"amount\":1}");reject(()->energy(same+","+same));
        String sourceOnly=same.substring(0,same.length()-1)+",\"sourceCardOnly\":true}";reject(()->trainer("TOOL",sourceOnly));
    }
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    static void reject(Runnable action){EventTargetBindingTest.reject(action);}
}
