import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Synthetic programs test general turn boundaries, not unreviewed real-card bindings. */
public final class TurnEndEffectsTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static final String END="{\"trigger\":\"ON_TURN_END\",\"turn\":\"OWN\",\"optional\":false,\"usage\":\"NONE\"}";
    static final String SELF_DISCARD="{\"type\":\"discard_source\"}";
    static String trigger(String effects) {return PersistentSourceTriggerTest.trigger("end",END,effects);}
    static EnergyProfile energy(String effects) {return PersistentSourceTriggerTest.energy(trigger(effects));}
    static ServerMatch fixture(EnergyProfile energy) {return PersistentSourceTriggerTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),energy);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    public static void main(String[] args) {
        ownEndAndAttack(); suppression(); healBeforeCheckup(); pausedChoiceAndPrivacy(); knockoutResume();
        sourceIdentityAndKinds(); turnScopeAndAbility(); privateSelectionAndRulePause(); invalidData();
        System.out.println("TurnEndEffectsTest passed: end-of-turn boundary, source disposal, continuation, KO and privacy");
    }
    static void ownEndAndAttack() {
        var m=fixture(energy(SELF_DISCARD));var card=hand(m,A,"special");UUID host=m.activePokemonView(A).pokemonId();
        m.attachEnergy(A,card.instanceId(),host);m.endTurn(A);
        check(m.privateView(A).discard().contains(card)&&m.activePokemonView(A).attachedEnergyIds().isEmpty()&&m.currentPlayer().equals(B),"回合末未自弃或未恰好推进一次");
        var other=hand(m,A,"special");m.attachEnergyByEffect(B,A,EffectSourceZone.HAND,other.instanceId(),A,host);m.endTurn(B);
        check(!m.privateView(A).discard().contains(other),"自己的回合末错误监听对手回合");m.attack(A,"hit");
        check(m.privateView(A).discard().contains(other)&&m.activePokemonView(B).damage()==80&&m.currentPlayer().equals(B),"攻击结束路径未接回合末或提前弃能");
    }
    static void suppression() {
        var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicEnergyProvisionTest.stadium(),energy(SELF_DISCARD),DynamicPassiveModifierTest.attack(40,false,false));
        UUID host=m.activePokemonView(A).pokemonId();var card=hand(m,A,"special");m.attachEnergy(A,card.instanceId(),host);
        m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);m.endTurn(A);check(!m.privateView(A).discard().contains(card),"被关闭的特殊能量仍监听回合末");
        m.discardStadiumByEffect(B);m.endTurn(B);m.endTurn(A);check(m.privateView(A).discard().contains(card),"场地离场后未恢复回合末监听");
    }
    static void healBeforeCheckup() {
        var tool=PersistentSourceTriggerTest.trainer("TOOL",trigger("{\"type\":\"heal\",\"amount\":20}"));
        var m=PersistentSourceTriggerTest.fixture(tool,DynamicPassiveModifierTest.plainStadium(),EnergyProfile.basic(EnergyType.COLORLESS));UUID host=m.activePokemonView(A).pokemonId();
        m.playTrainer(A,hand(m,A,"tool").instanceId(),host);m.placeDamageCountersByEffect(A,A,host,29);
        m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);m.endTurn(A);
        check(m.activePokemonView(A).pokemonId().equals(host)&&m.activePokemonView(A).damage()==280&&m.currentPlayer().equals(B),"回合末回复必须先于中毒检查，不能先击倒");
    }
    static void pausedChoiceAndPrivacy() {
        var m=fixture(energy("{\"type\":\"draw\",\"amount\":1},{\"type\":\"choice\",\"effects\":["+SELF_DISCARD+",{\"type\":\"draw\",\"amount\":1}]}"));
        var card=hand(m,A,"special");m.attachEnergy(A,card.instanceId(),m.activePokemonView(A).pokemonId());int size=m.privateView(A).hand().size();m.endTurn(A);
        check(m.phase().name().equals("TURN_END")&&m.hasPendingEffectChoice()&&m.currentPlayer().equals(A),"回合末未暂停在独立阶段");
        var p=m.effectChoicePrompt(A);reject(()->m.endTurn(A));reject(()->m.chooseEffectOption(B,p.resolutionId(),p.step(),0));
        var s=MatchSession.multiplayer(m,A,B);UUID watcher=UUID.randomUUID();s.addSpectator(watcher);int rng=m.randomTranscript().size();
        for(UUID viewer:List.of(A,B,watcher)) {
            var v=s.view(viewer,viewer.equals(watcher)?null:viewer);String json=new com.google.gson.Gson().toJson(v);
            check(!json.contains("effectProgram")&&!json.contains("automaticEffects"),"客户端收到可执行规则");
            for(UUID owner:List.of(A,B)) {
                if(!owner.equals(viewer))for(var c:m.privateView(owner).hand())check(!json.contains(c.instanceId().toString()),"回合末泄露手牌");
                for(var c:m.privateView(owner).deck())check(!json.contains(c.instanceId().toString()),"回合末泄露牌库");
                for(var c:m.privateView(owner).prizes())check(!json.contains(c.instanceId().toString()),"回合末泄露奖赏");
            }
        }
        check(m.randomTranscript().size()==rng,"回合末只读视图改变随机");m.chooseEffectOption(A,p.resolutionId(),p.step(),0);
        check(m.privateView(A).hand().size()==size+1&&m.privateView(A).discard().contains(card)&&m.currentPlayer().equals(B),"恢复选择重做前缀或丢失来源身份");
        reject(()->m.chooseEffectOption(A,p.resolutionId(),p.step(),0));
    }
    static void knockoutResume() {
        var json=JsonParser.parseString("{\"energyUnits\":[[\"COLORLESS\"]],\"effects\":[{\"type\":\"hit_points_modifier\",\"amount\":100}],\"automaticEffects\":["+trigger(SELF_DISCARD)+"]}").getAsJsonObject();
        var m=fixture(BundledCombatData.specialEnergy(json,EffectRegistry.standard()));var card=hand(m,A,"special");UUID host=m.activePokemonView(A).pokemonId();
        m.attachEnergy(A,card.instanceId(),host);m.placeDamageCountersByEffect(A,A,host,36);m.endTurn(A);
        check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.currentPlayer().equals(A),"回合末自弃降低HP后没有等待拿奖");
        m.takePrizeAt(B,0);m.chooseReplacement(A,m.benchPokemonView(A).getFirst().pokemonId());
        check(m.currentPlayer().equals(B)&&m.phase()==MatchPhase.TURN&&m.privateView(A).discard().contains(card),"拿奖替补后回合末重入或无法推进");
    }
    static void invalidData() {
        reject(()->PersistentSourceTriggerTest.energy(PersistentSourceTriggerTest.trigger("end",END.replace("\"OWN\"","\"typo\""),SELF_DISCARD)));
        reject(()->PersistentSourceTriggerTest.energy(PersistentSourceTriggerTest.trigger("end",END.replace(",\"turn\":\"OWN\"",""),SELF_DISCARD)));
        reject(()->EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"discard_source\",\"target\":\"OPPONENT_ACTIVE\"}").getAsJsonObject()));
        reject(()->TriggeredAbilityRules.validate(ability(SELF_DISCARD)));
        reject(()->PersistentSourceTriggerTest.energy(PersistentSourceTriggerTest.trigger("end",END.replace("\"NONE\"","\"ONCE_PER_GAME\""),SELF_DISCARD)));
        var m=fixture(EnergyProfile.basic(EnergyType.COLORLESS));int before=m.privateView(A).hand().size();
        var program=new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_ATTACK,List.of(),List.of(EffectRegistry.standard().parse(JsonParser.parseString(SELF_DISCARD).getAsJsonObject())))));
        reject(()->m.executeEffectProgramByRule(A,m.activePokemonView(A).pokemonId(),program,EffectTrigger.ON_ATTACK));
        check(m.privateView(A).hand().size()==before,"无真实来源的自弃改变状态");
    }
    static void sourceIdentityAndKinds() {
        for(String kind:List.of("TOOL","STADIUM")) {
            var profile=PersistentSourceTriggerTest.trainer(kind,trigger(SELF_DISCARD));
            var m=PersistentSourceTriggerTest.fixture(kind.equals("TOOL")?profile:DynamicPassiveModifierTest.plainTool(),kind.equals("STADIUM")?profile:DynamicPassiveModifierTest.plainStadium(),EnergyProfile.basic(EnergyType.COLORLESS));
            var card=hand(m,A,kind.toLowerCase(Locale.ROOT));m.playTrainer(A,card.instanceId(),kind.equals("TOOL")?m.activePokemonView(A).pokemonId():null);m.endTurn(A);
            check(m.privateView(A).discard().contains(card)&&m.currentPlayer().equals(B),"非能量来源自弃失效："+kind);
        }
        String first=PersistentSourceTriggerTest.trigger("first",END,SELF_DISCARD);
        String second=PersistentSourceTriggerTest.trigger("second",END,SELF_DISCARD+",{\"type\":\"draw\",\"amount\":1}");
        var m=fixture(PersistentSourceTriggerTest.energy(first+","+second));var card=hand(m,A,"special");m.attachEnergy(A,card.instanceId(),m.activePokemonView(A).pokemonId());int before=m.privateView(A).hand().size();m.endTurn(A);
        var prompt=m.triggerOrderPrompt();check(prompt!=null&&prompt.entries().size()==2,"同边界没有提供排序");
        reject(()->m.chooseTriggeredEffect(B,prompt.id(),prompt.entries().getFirst().id()));m.chooseTriggeredEffect(A,prompt.id(),prompt.entries().getFirst().id());
        check(m.privateView(A).discard().stream().filter(c->c.equals(card)).count()==1&&m.privateView(A).hand().size()==before+1&&m.currentPlayer().equals(B),"来源已离场的第二次自弃重复入库或丢失后续效果");
        String any=trigger(SELF_DISCARD).replace("\"OWN\"","\"ANY\"");var n=fixture(PersistentSourceTriggerTest.energy(any));var e=hand(n,A,"special");
        UUID host=n.activePokemonView(B).pokemonId();n.attachEnergyByEffect(A,A,EffectSourceZone.HAND,e.instanceId(),B,host);n.endTurn(A);
        check(n.privateView(A).discard().contains(e)&&!n.privateView(B).discard().contains(e),"跨方附着来源自弃错误改变原所有权");
    }
    static AbilityDefinition ability(String effects) {
        return BundledCombatData.ability(JsonParser.parseString("{\"id\":\"turn-test\",\"nameZh\":\"回合末特性测试\",\"kind\":\"TRIGGERED\",\"effectSupport\":\"SUPPORTED\",\"eventPolicy\":"+END
            +",\"triggers\":[{\"trigger\":\"ON_TURN_END\",\"conditions\":[{\"type\":\"HAS_ENERGY\",\"target\":\"SELF\"}],\"effects\":["+effects+"]}]}").getAsJsonObject(),EffectRegistry.standard());
    }
    static void turnScopeAndAbility() {
        String body="{\"type\":\"conditional\",\"conditions\":[{\"type\":\"TURN_STATE\",\"value\":\"ACTOR_TURN\"}],\"effects\":[{\"type\":\"draw\",\"amount\":1}]}";
        var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.basic(EnergyType.COLORLESS),DynamicPassiveModifierTest.attack(40,false,false),List.of(ability(body)));
        m.attachEnergy(A,hand(m,A,"energy").instanceId(),m.activePokemonView(A).pokemonId());int before=m.privateView(A).hand().size();m.endTurn(A);
        check(m.privateView(A).hand().size()==before+1,"回合末条件错误地认为已经不是当前方回合");
        String opposing=trigger(SELF_DISCARD).replace("\"OWN\"","\"OPPONENT\"");var n=fixture(PersistentSourceTriggerTest.energy(opposing));var e=hand(n,A,"special");n.attachEnergy(A,e.instanceId(),n.activePokemonView(A).pokemonId());n.endTurn(A);
        check(!n.privateView(A).discard().contains(e),"对手回合末提前触发");n.endTurn(B);check(n.privateView(A).discard().contains(e),"对手回合末漏触发");
    }
    static void privateSelectionAndRulePause() {
        var m=fixture(energy("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":1},"+SELF_DISCARD));
        var card=hand(m,A,"special");m.attachEnergy(A,card.instanceId(),m.activePokemonView(A).pokemonId());int size=m.privateView(A).hand().size();m.endTurn(A);
        var prompt=m.effectCardPrompt(A);check(prompt!=null&&m.phase()==MatchPhase.TURN_END,"回合末没有暂停私有检索");
        reject(()->m.submitEffectCards(B,prompt.resolutionId(),prompt.step(),List.of(prompt.candidates().getFirst().instanceId())));
        m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),List.of(prompt.candidates().getFirst().instanceId()));
        check(m.currentPlayer().equals(B)&&m.privateView(A).hand().size()==size+1&&m.privateView(A).discard().contains(card),"回合末私有检索续接错误");
        var json=JsonParser.parseString("{\"energyUnits\":[[\"COLORLESS\"]],\"effects\":[{\"type\":\"tool_capacity\",\"amount\":2}],\"automaticEffects\":["+trigger(SELF_DISCARD+",{\"type\":\"draw\",\"amount\":1}")+"]}").getAsJsonObject();
        var n=fixture(BundledCombatData.specialEnergy(json,EffectRegistry.standard()));var source=hand(n,A,"special");UUID host=n.activePokemonView(A).pokemonId();
        n.attachEnergy(A,source.instanceId(),host);n.playTrainer(A,hand(n,A,"tool").instanceId(),host);n.playTrainer(A,hand(n,A,"tool").instanceId(),host);
        int before=n.privateView(A).hand().size();n.endTurn(A);var overflow=n.toolOverflowPrompt();
        check(overflow!=null&&n.phase()==MatchPhase.TURN_END&&n.privateView(A).hand().size()==before,"回合末规则选择未阻止后续抽牌");
        n.discardOverflowTool(A,overflow.id(),overflow.tools().getFirst().instanceId());
        check(n.currentPlayer().equals(B)&&n.activePokemonView(A).attachedTools().size()==1&&n.privateView(A).hand().size()==before+1,"清理工具后重复自弃或无法恢复回合末");
    }
    static void check(boolean result,String message){if(!result)throw new AssertionError(message);}
    static void reject(Runnable action){EventTargetBindingTest.reject(action);}
}
