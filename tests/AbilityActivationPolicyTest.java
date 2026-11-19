import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Declared activation frequency is enforced in all server paths, including paused selections. */
public final class AbilityActivationPolicyTest {
    static final UUID A=ResumableAbilityTest.A,B=ResumableAbilityTest.B,WATCH=ResumableAbilityTest.WATCH;
    public static void main(String[] args) {
        unlimitedLegacyAndDefault(); unlimitedComposed(); sharedTurnStillLimits(); gameLimitSurvivesSource();
        unlimitedAttachment(); malformedAndNoEffect(); legacySearchAndDisable(); cursorLease(); gameLeaveAndPractice();
        System.out.println("AbilityActivationPolicyTest passed: unlimited/turn/game declarations, source, pause, ownership and shared quotas");
    }
    static AbilityDefinition ability(String id,String kind,String body,String extra) {
        return BundledCombatData.ability(JsonParser.parseString("{\"id\":\""+id+"\",\"nameZh\":\"发动政策夹具\",\"effectSupport\":\"SUPPORTED\",\"kind\":\""+kind+"\","+body+extra+"}").getAsJsonObject(),EffectRegistry.standard());
    }
    static AbilityDefinition composed(String id,String kind,String effects,String extra) {
        return ability(id,kind,"\"effects\":["+effects+"]",extra);
    }
    static String group(String group,String period) {return ",\"usageLimit\":{\"group\":\""+group+"\",\"period\":\""+period+"\"}";}
    static final String DRAW="{\"type\":\"draw\",\"amount\":1}";
    static final String SEARCH="{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":1}";
    static void unlimitedLegacyAndDefault() {
        var unlimited=ability("many","ACTIVATED_UNLIMITED","\"effect\":{\"opcode\":\"DRAW\",\"amount\":1}","");
        var once=ResumableAbilityTest.ability("once",DRAW);var m=ResumableAbilityTest.fixture(unlimited,once);
        UUID source=m.activePokemonView(A).pokemonId();int hand=m.privateView(A).hand().size();
        for(int i=0;i<3;i++) {check(m.canUseAbility(A,source,unlimited.id()),"任意次旧效果被限次");m.useAbility(A,source,unlimited.id());}
        check(m.privateView(A).hand().size()==hand+3,"任意次抽牌错误");
        m.useAbility(A,source,once.id());reject(()->m.useAbility(A,source,once.id()));
        UUID bench=m.benchPokemonView(A).getFirst().pokemonId();m.useAbility(A,bench,once.id());
        m.switchActiveByEffect(A,A,bench);reject(()->m.useAbility(A,source,once.id()));
        m.useAbility(A,source,unlimited.id());m.endTurn(A);reject(()->m.useAbility(A,source,unlimited.id()));
        m.endTurn(B);check(m.canUseAbility(A,source,once.id()),"默认每回合资格未恢复");
    }
    static void unlimitedComposed() {
        var ability=composed("search","ACTIVATED_UNLIMITED",DRAW+","+SEARCH+","+DRAW,"");var m=ResumableAbilityTest.fixture(ability);
        UUID source=m.activePokemonView(A).pokemonId();var s=MatchSession.multiplayer(m,A,B);s.addSpectator(WATCH);int hand=m.privateView(A).hand().size();
        for(int use=0;use<3;use++) {
            var view=s.view(A,A);var action=view.options().stream().filter(o->o.labelZh().startsWith("使用特性")&&o.cardKeys().contains("field:"+source)).findFirst().orElseThrow();
            s.submit(A,A,view.revision(),action.index());var prompt=m.effectCardPrompt(A);var random=m.randomTranscript();
            reject(()->m.useAbility(A,source,ability.id()));reject(()->m.endTurn(A));
            reject(()->s.submit(A,A,view.revision(),action.index()));
            for(UUID recipient:List.of(B,WATCH)) {
                var packet=s.view(recipient,recipient.equals(B)?B:null);check(packet.game().effectSelection()==null,"检索候选泄露");
                String json=new Gson().toJson(packet);for(var card:prompt.candidates())check(!json.contains(card.instanceId().toString()),"牌库身份泄露");
            }
            check(random.equals(m.randomTranscript()),"刷新重洗牌");
            m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),List.of(prompt.candidates().getFirst().instanceId()));
            check(m.canUseAbility(A,source,ability.id()),"上次选择完成仍不能使用任意次特性");
        }
        check(m.privateView(A).hand().size()==hand+9,"多次发动重复／丢失前后缀");
    }
    static void sharedTurnStillLimits() {
        var ability=composed("same","ACTIVATED_UNLIMITED",DRAW,group("same-pool","TURN"));var m=ResumableAbilityTest.fixture(ability);
        UUID source=m.activePokemonView(A).pokemonId(),bench=m.benchPokemonView(A).getFirst().pokemonId();
        m.useAbility(A,source,ability.id());reject(()->m.useAbility(A,bench,ability.id()));
        m.endTurn(A);m.endTurn(B);m.useAbility(A,bench,ability.id());
    }
    static void gameLimitSurvivesSource() {
        for(boolean legacy:List.of(false,true)) {
            var ability=legacy?ability("game","ACTIVATED_ONCE_PER_GAME","\"effect\":{\"opcode\":\"DRAW\",\"amount\":1}",group("test-game","GAME"))
                :composed("game","ACTIVATED_ONCE_PER_GAME",SEARCH,group("test-game","GAME"));
            var m=ResumableAbilityTest.fixture(ability);UUID source=m.activePokemonView(A).pokemonId(),bench=m.benchPokemonView(A).getFirst().pokemonId();
            m.useAbility(A,source,ability.id());
            if(!legacy){var p=m.effectCardPrompt(A);reject(()->m.useAbility(A,bench,ability.id()));m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId()));}
            m.switchActiveByEffect(A,A,bench);reject(()->m.useAbility(A,source,ability.id()));reject(()->m.useAbility(A,bench,ability.id()));
            m.endTurn(A);m.endTurn(B);reject(()->m.useAbility(A,source,ability.id()));
            var fresh=ResumableAbilityTest.fixture(ability);check(fresh.canUseAbility(A,fresh.activePokemonView(A).pokemonId(),ability.id()),"新对局未恢复整局资格");
        }
    }
    static void unlimitedAttachment() {
        var many=composed("attach","ACTIVATED_UNLIMITED",EnergyTriggeredAbilityTest.ATTACH,"");
        var triggered=EnergyTriggeredAbilityTest.ability("after","SELF",false,"NONE","{\"type\":\"draw\",\"amount\":1}");
        var m=EnergyTriggeredAbilityTest.fixture(List.of(many,triggered),List.of(),null);UUID owner=EnergyTriggeredAbilityTest.A,source=m.activePokemonView(owner).pokemonId();
        int hand=m.privateView(owner).hand().size();
        for(int i=0;i<3;i++) {
            m.useAbility(owner,source,many.id());var p=m.effectCardPrompt(owner);m.submitEffectCards(owner,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId()));
            var target=m.effectAttachmentPrompt(owner);m.chooseEffectAttachmentTarget(owner,target.resolutionId(),target.step(),source);
        }
        check(m.activePokemonView(owner).attachedEnergyIds().size()==3&&!m.energyAttached(owner)&&m.privateView(owner).hand().size()==hand,"任意次附能错误限一次／占用手动次数／触发丢失");
    }
    static void malformedAndNoEffect() {
        reject(()->composed("bad","ACTIVATED_ONCE_PER_GAME",DRAW,""));
        reject(()->composed("bad","ACTIVATED_ONCE_PER_GAME",DRAW,group("bad","TURN")));
        var a=composed("empty","ACTIVATED_UNLIMITED","{\"type\":\"heal\",\"target\":\"SELF\",\"amount\":10}","");
        var m=ResumableAbilityTest.fixture(a);var before=m.clientView(A);reject(()->m.useAbility(A,m.activePokemonView(A).pokemonId(),a.id()));
        check(before.equals(m.clientView(A)),"无作用任意次特性仍执行");
        m.placeDamageCountersByEffect(A,A,m.activePokemonView(A).pokemonId(),1);m.useAbility(A,m.activePokemonView(A).pokemonId(),a.id());
        reject(()->m.useAbility(A,m.activePokemonView(A).pokemonId(),a.id()));
        for(String kind:List.of("ACTIVATED_UNLIMITED","ACTIVATED_ONCE_PER_GAME")) {
            var bad=ability("wrong-timing",kind,"\"triggers\":[{\"trigger\":\"ONCE_PER_TURN\",\"effects\":["+DRAW+"]}]",kind.endsWith("GAME")?group("bad","GAME"):"");
            check(ActivatedAbilityRules.unsupportedReason(bad).isPresent(),"错误默认时机被静态检查忽略");
            var match=ResumableAbilityTest.fixture(bad);var initial=match.clientView(A);reject(()->match.useAbility(A,match.activePokemonView(A).pokemonId(),bad.id()));
            check(initial.equals(match.clientView(A)),"错误时机先执行后拒绝");
            check(MatchSession.multiplayer(match,A,B).view(A,A).options().stream().anyMatch(o->o.labelZh().startsWith("特性组合尚未支持")),"GUI未解释错误时机");
        }
    }
    static void legacySearchAndDisable() {
        var search=ability("search-old","ACTIVATED_UNLIMITED","\"effect\":{\"opcode\":\"SEARCH_CARD_TO_HAND\",\"amount\":1}","");var m=ResumableAbilityTest.fixture(search);
        UUID source=m.activePokemonView(A).pokemonId();int hand=m.privateView(A).hand().size();
        for(int i=0;i<3;i++) {var picked=m.privateView(A).deck().getFirst();var before=m.privateView(A);
            reject(()->m.useAbilityWithDeckSearch(A,source,search.id(),UUID.randomUUID()));check(before.equals(m.privateView(A)),"伪造旧检索修改隐藏区");
            m.useAbilityWithDeckSearch(A,source,search.id(),picked.instanceId());
        }
        check(m.privateView(A).hand().size()==hand+3,"任意次旧检索被限次");
        m.disableAbilityEffectsByEffect(A,A,source);
        check(!m.canUseAbilityDeckSearch(A,source,search.id(),List.of(m.privateView(A).deck().getFirst().instanceId())),"任意次特性绕过特性失效");
        m.endTurn(A);m.endTurn(B);
        check(m.canUseAbilityDeckSearch(A,source,search.id(),List.of(m.privateView(A).deck().getFirst().instanceId())),"失效到期后未恢复");
        var draw=ability("empty-old","ACTIVATED_UNLIMITED","\"effect\":{\"opcode\":\"DRAW\",\"amount\":1}","");
        var empty=ResumableAbilityTest.fixture(draw);empty.drawCardsByEffect(A,A,60);var before=empty.clientView(A);
        check(!empty.canUseAbility(A,empty.activePokemonView(A).pokemonId(),draw.id()),"牌库为空仍提供无作用任意次抽牌");
        reject(()->empty.useAbility(A,empty.activePokemonView(A).pokemonId(),draw.id()));
        check(before.equals(empty.clientView(A)),"无作用抽牌修改状态");
    }
    static void cursorLease() {
        var a=composed("cursor","ACTIVATED_UNLIMITED",DRAW+",{\"type\":\"choice\",\"effects\":["+DRAW+","+DRAW+"]}","");
        var source=new EffectLifecycleDispatcher.Source(UUID.randomUUID(),"unlimited",a.effectProgram());var dispatcher=new EffectLifecycleDispatcher();
        var context=new LeaseContext();var cursor=dispatcher.beginActivation(source,1,context);cursor.advance();
        check(context.count==1&&cursor.pendingChoice()!=null,"任意次游标未等待");
        reject(()->dispatcher.beginActivation(source,1,context));reject(()->dispatcher.dispatch(source,EffectTrigger.ON_ACTIVATE,1,context));
        var choice=cursor.pendingChoice();cursor.select(choice.step(),0);cursor.advance();
        check(!dispatcher.usageLimited(source,EffectTrigger.ON_ACTIVATE,1),"已完成游标未释放任意次资格");
        var second=dispatcher.beginActivation(source,1,context);second.advance();check(context.count==3,"再次发动未执行唯一前缀");
        choice=second.pendingChoice();second.select(choice.step(),0);context.fail=true;reject(second::advance);
        context.fail=false;reject(()->dispatcher.beginActivation(source,2,context));check(context.count==3,"失败后重放前缀");
        dispatcher.clearCompletedMatch();dispatcher.beginActivation(source,2,context);
    }
    static final class LeaseContext implements EffectExecutionContext {
        int count;boolean fail;
        public CoinResult flipCoins(EffectDefinition e,int flips){return new CoinResult(flips,0);}
        public int choose(EffectDefinition e,int options){return 0;}
        public void executeAtomic(EffectDefinition e){if(fail)throw new IllegalStateException("测试中断");count++;}
        public int number(ConditionType t,EffectTarget target,Map<String,String> args){return 1;}
        public boolean hasStatus(EffectTarget target,String status){return false;}
        public boolean flag(ConditionType t,EffectTarget target,Map<String,String> args){return false;}
        public boolean coinResult(String expected){return false;}
        public boolean customCondition(ConditionDefinition c){return false;}
    }
    static void gameLeaveAndPractice() {
        var a=composed("vstar","ACTIVATED_ONCE_PER_GAME",SEARCH,group("vstar-power","GAME"));var m=ResumableAbilityTest.fixture(a);
        UUID source=m.activePokemonView(A).pokemonId(),bench=m.benchPokemonView(A).getFirst().pokemonId();var practice=MatchSession.practice(m,WATCH,A,B);
        m.useAbility(A,bench,a.id());var prompt=m.effectCardPrompt(A);
        check(practice.view(WATCH,B).game().effectSelection()==null&&practice.view(WATCH,A).game().effectSelection().equals(prompt),"切席泄露或重置任意次选择");
        m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),List.of(prompt.candidates().getFirst().instanceId()));
        UUID physical=PokemonIdentityTestSupport.physical(m,A,bench);m.movePokemonByEffect(A,A,bench,LeavePlayDestination.HAND);m.playBasicPokemonToBench(A,physical);UUID returned=PokemonIdentityTestSupport.logical(m,A,physical);
        check(!returned.equals(bench),"A new stay must have a new logical identity");
        reject(()->m.useAbility(A,returned,a.id()));reject(()->m.useAbility(A,source,a.id()));
        check(SharedUsageLimitTest.marker(practice.view(WATCH,B).game(),A).vstarUsed(),"重新入场／切席重置整局标记");
    }
    static void check(boolean value,String message){ResumableAbilityTest.check(value,message);}
    static void reject(Runnable action){ResumableAbilityTest.rejects(action);}
}
