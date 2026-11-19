import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Real JSON + server intents: Checkup is neither player's turn and checks KOs only at its end. */
public final class CheckupTriggeredAbilityTest {
    static final UUID A=EnergyTriggeredAbilityTest.A, B=EnergyTriggeredAbilityTest.B, WATCH=EnergyTriggeredAbilityTest.WATCH;
    public static void main(String[] args) {
        orderAndDeferredKnockout(); statusBlockAndHealing(); optionalAndPrivacy(); noOrdinaryTurn();
        groupEffectsAndKnockouts(); deckOutAndPractice(); malformedAndFilters(); statusSequence(); protection(); sharedGameAndShorthand();
        System.out.println("CheckupTriggeredAbilityTest passed: next-player order, indivisible conditions, delayed KOs, optional ownership and privacy");
    }
    static AbilityDefinition ability(String id, boolean optional, String effects) {
        return BundledCombatData.ability(JsonParser.parseString("""
            {"id":"%s","nameZh":"回合间夹具%s","kind":"TRIGGERED","effectSupport":"SUPPORTED",
             "eventPolicy":{"trigger":"ON_POKEMON_CHECKUP","optional":%s,"usage":"NONE"},
             "triggers":[{"trigger":"ON_POKEMON_CHECKUP","conditions":[{"type":"IS_ACTIVE","target":"SELF"}],
                "effects":[%s]}]}
            """.formatted(id,id,optional,effects)).getAsJsonObject(),EffectRegistry.standard());
    }
    static ServerMatch fixture(List<AbilityDefinition> a,List<AbilityDefinition> b) {
        return EnergyTriggeredAbilityTest.fixture(a,b,null);
    }
    static void choose(ServerMatch m,String text) {
        var p=m.triggerOrderPrompt(); check(p!=null&&!p.optionalDecision(),"缺少排序");
        var e=p.entries().stream().filter(x->x.nameZh().contains(text)).findFirst().orElseThrow();
        m.chooseTriggeredEffect(p.choosingPlayer(),p.id(),e.id());
    }
    static void finishCheckup(ServerMatch m) {
        int limit=20;
        while(m.triggerOrderPrompt()!=null&&limit-->0) {
            var p=m.triggerOrderPrompt();
            if(p.optionalDecision())m.decideTriggeredEffect(p.choosingPlayer(),p.id(),false);
            else m.chooseTriggeredEffect(p.choosingPlayer(),p.id(),p.entries().getFirst().id());
        }
        check(limit>0,"回合间未结束");
    }
    private static void orderAndDeferredKnockout() {
        var m=fixture(List.of(ability("hurt",false,"{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}")),
            List.of(ability("heal",false,"{\"type\":\"heal\",\"target\":\"SELF\",\"amount\":20}")));
        UUID b=m.activePokemonView(B).pokemonId(); m.placeDamageCountersByEffect(A,B,b,11);
        int before=m.privateView(B).deck().size(); m.endTurn(A);
        check(m.phase().name().equals("POKEMON_CHECKUP"),"检查仍伪装成玩家回合");
        var p=m.triggerOrderPrompt(); check(p.choosingPlayer().equals(B),"没有由下一回合玩家决定顺序");
        rejects(()->m.chooseTriggeredEffect(A,p.id(),p.entries().getFirst().id()));
        choose(m,"hurt"); check(m.activePokemonView(B).damage()==120&&m.phase().name().equals("POKEMON_CHECKUP"),"提前击倒，没有等待回复");
        choose(m,"heal"); finishCheckup(m);
        check(m.activePokemonView(B).pokemonId().equals(b)&&m.activePokemonView(B).damage()==100,"回复不能救回暂时达到HP的宝可梦");
        check(m.currentPlayer().equals(B)&&m.turnNumber()==2&&m.privateView(B).deck().size()==before-1,"未恰好推进一次并抽一张");
        rejects(()->m.chooseTriggeredEffect(B,p.id(),p.entries().getFirst().id()));
    }
    private static void statusBlockAndHealing() {
        for(boolean conditionsFirst:List.of(true,false)) {
            var m=fixture(List.of(ability("heal",false,"{\"type\":\"heal\",\"target\":\"SELF\",\"amount\":20}")),List.of());
            UUID a=m.activePokemonView(A).pokemonId();
            m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED); m.endTurn(A);
            if(conditionsFirst)choose(m,"异常状态"); else choose(m,"heal");
            finishCheckup(m);
            check(m.activePokemonView(A).damage()==(conditionsFirst?0:10),"没有允许完整异常状态步骤在特性之前或之后");
            check(m.activePokemonView(A).pokemonId().equals(a),"错误移除宝可梦");
        }
    }
    private static void optionalAndPrivacy() {
        var m=fixture(List.of(ability("optional",true,"{\"type\":\"draw\",\"amount\":1}")),List.of());
        var session=MatchSession.multiplayer(m,A,B); session.addSpectator(WATCH);
        int hand=m.privateView(A).hand().size(); m.endTurn(A); choose(m,"optional");
        var p=m.triggerOrderPrompt(); check(p.optionalDecision()&&p.choosingPlayer().equals(A),"下一玩家错误替特性拥有者决定用不用");
        rejects(()->m.decideTriggeredEffect(B,p.id(),true)); rejects(()->m.endTurn(A));
        var view=session.view(A,A); var use=view.options().stream().filter(o->o.labelZh().startsWith("使用触发特性")).findFirst().orElseThrow();
        session.submit(A,A,view.revision(),use.index()); finishCheckup(m);
        check(m.privateView(A).hand().size()==hand+1,"抽牌没有归属特性拥有者");
        rejects(()->session.submit(A,A,view.revision(),use.index()));
        for(UUID receiver:List.of(B,WATCH)) {
            String json=new com.google.gson.Gson().toJson(session.view(receiver,receiver.equals(B)?B:null));
            for(var hidden:m.privateView(A).hand())check(!json.contains(hidden.instanceId().toString()),"回合间泄露手牌");
        }
        m.endTurn(B); choose(m,"optional"); m.concede(A);
        check(m.triggerOrderPrompt()==null&&m.phase()==MatchPhase.FINISHED,"终局没有清理检查中队列");
    }
    private static void noOrdinaryTurn() {
        var m=fixture(List.of(ability("plain",false,"{\"type\":\"draw\",\"amount\":1}")),List.of());
        m.endTurn(A);
        check(!m.mayAttack(A)&&!m.mayPlaySupporter(A),"检查允许普通行动");
        for(UUID owner:List.of(A,B))for(String value:List.of("ACTOR_TURN","OPPONENT_TURN","FIRST_TURN"))
            check(!m.matchesEffectCondition(owner,m.activePokemonView(owner).pokemonId(),
                new ConditionDefinition(ConditionType.TURN_STATE,Map.of("value",value),List.of())),"回合间被判为任一玩家回合");
        rejects(()->m.endTurn(A)); rejects(()->m.drawCardsByEffect(A,A,1)); rejects(()->m.drawCardsByEffect(B,B,1)); finishCheckup(m);
    }
    private static void groupEffectsAndKnockouts() {
        var hit=ability("field",false,"{\"type\":\"damage_counter\",\"target\":\"ALL_OPPONENT_POKEMON\",\"amount\":1}");
        var m=fixture(List.of(hit),List.of(hit));
        UUID a=m.activePokemonView(A).pokemonId(),b=m.activePokemonView(B).pokemonId();
        UUID bench=m.benchPokemonView(B).getFirst().pokemonId();
        for(var target:List.of(a,b,bench))m.placeDamageCountersByEffect(A,target.equals(a)?A:B,target,11);
        int aPrizes=m.privateView(A).prizes().size(),bPrizes=m.privateView(B).prizes().size();
        m.endTurn(A); choose(m,"异常状态");
        var p=m.triggerOrderPrompt(); var owned=p.entries().stream().filter(e->e.owner().equals(A)).findFirst().orElseThrow();
        m.chooseTriggeredEffect(B,p.id(),owned.id());
        // The selected full-field effect removes both of B's Pokémon.  The
        // Active has no legal replacement, so the current rule ends the game
        // immediately; pending Checkup effects do not get a chance to revive
        // or otherwise act after that loss condition.
        check(m.phase()==MatchPhase.FINISHED&&m.result().winner().equals(A)
            &&m.result().reason()==MatchResult.Reason.NO_POKEMON,
            "全场效果没有在无备战位时立即判负");
        check(m.privateView(A).discard().isEmpty()&&m.privateView(B).discard().size()==2,
            "立即判负前没有同时移除对方战斗和备战致命目标：甲弃牌="+m.privateView(A).discard().size()+"，乙弃牌="+m.privateView(B).discard().size());
        check(m.privateView(A).prizes().size()==aPrizes&&m.privateView(B).prizes().size()==bPrizes,
            "无宝可梦自动败北错误开放了奖赏选择");
        // A single KO with remaining Bench must promote and begin the next turn, not repeat Checkup.
        var r=fixture(List.of(ability("one",false,"{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}")),List.of());
        r.placeDamageCountersByEffect(A,B,r.activePokemonView(B).pokemonId(),11);r.endTurn(A);finishCheckup(r);
        r.takePrizeAt(A,0);check(r.phase()==MatchPhase.CHOOSE_REPLACEMENT,"没有等待替补");
        var replacement=r.benchPokemonView(B).getFirst().pokemonId();r.chooseReplacement(B,replacement);
        check(r.turnNumber()==2&&r.currentPlayer().equals(B)&&r.activePokemonView(B).damage()==0,"替补后重复检查或抽牌");
    }
    private static void deckOutAndPractice() {
        var m=fixture(List.of(),List.of(ability("last",false,"{\"type\":\"draw\",\"amount\":1}")));
        m.drawCardsByEffect(A,B,m.privateView(B).deck().size()-1);m.endTurn(A);choose(m,"last");finishCheckup(m);
        check(m.phase()==MatchPhase.FINISHED&&m.result().winner().equals(A),"回合间抽空后下一回合未因抽牌失败判负");
        var practice=fixture(List.of(ability("optional",true,"{\"type\":\"draw\",\"amount\":1}")),List.of());
        var s=MatchSession.practice(practice,WATCH,A,B);practice.endTurn(A);
        check(s.view(WATCH,A).options().stream().noneMatch(o->o.labelZh().startsWith("优先结算触发")),"练习可从错误席位排序");
        var v=s.view(WATCH,B);var action=v.options().stream().filter(o->o.labelZh().contains("optional")).findFirst().orElseThrow();s.submit(WATCH,B,v.revision(),action.index());
        var own=s.view(WATCH,A);var decline=own.options().stream().filter(o->o.labelZh().equals("放弃触发特性")).findFirst().orElseThrow();
        s.submit(WATCH,A,own.revision(),decline.index());finishCheckup(practice);
        check(practice.turnNumber()==2,"练习未复用同一检查流程");
    }
    private static void malformedAndFilters() {
        var valid=ability("valid",false,"{\"type\":\"heal\",\"target\":\"ALL_OWN_POKEMON\",\"amount\":20}");
        TriggeredAbilityRules.validate(valid);
        rejects(()->AbilityEventPolicy.parse(JsonParser.parseString("{\"trigger\":\"ON_POKEMON_CHECKUP\",\"optional\":false,\"usage\":\"ONCE_PER_TURN\"}").getAsJsonObject()));
        rejects(()->AbilityEventPolicy.parse(JsonParser.parseString("{\"trigger\":\"ON_POKEMON_CHECKUP\",\"optional\":false,\"usage\":\"NONE\",\"source\":\"HAND\"}").getAsJsonObject()));
        var wrong=new AbilityDefinition(valid.id(),valid.nameZh(),valid.effect(),valid.effectSupport(),null,valid.kind(),valid.effectProgram(),
            new EnergyTriggerPolicy(EnergyTriggerPolicy.Target.SELF,EnergyTriggerPolicy.Source.ANY,EnergyTriggerPolicy.Cause.ANY,false,EnergyTriggerPolicy.Usage.NONE,false));
        rejects(()->TriggeredAbilityRules.validate(wrong));
        var game=new AbilityDefinition(valid.id(),valid.nameZh(),valid.effect(),valid.effectSupport(),null,valid.kind(),valid.effectProgram(),
            new CheckupTriggerPolicy(false,EnergyTriggerPolicy.Usage.ONCE_PER_GAME));
        rejects(()->TriggeredAbilityRules.validate(game));
        var choices=ability("choices",false,"{\"type\":\"choice\",\"options\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"heal\",\"amount\":7}]}");
        rejects(()->TriggeredAbilityRules.validate(choices));
        // Energy events must not accidentally enqueue a Checkup binding.
        var m=fixture(List.of(valid),List.of());var energy=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();
        m.attachEnergy(A,energy.instanceId(),m.activePokemonView(A).pokemonId());check(m.triggerOrderPrompt()==null,"附能误触发回合间特性");
    }
    private static void statusSequence() {
        var f=new BattlefieldState(A,B,Map.of("basic",new PokemonProfile(120,0,null)),new ServerRandom(17));
        f.placeInitialActive(A,new CardInstance(UUID.randomUUID(),A,"basic"));f.placeInitialActive(B,new CardInstance(UUID.randomUUID(),B,"basic"));f.beginTurn(A);
        for(UUID p:List.of(A,B))for(var status:List.of(SpecialCondition.POISONED,SpecialCondition.BURNED,SpecialCondition.ASLEEP))f.applySpecialCondition(p,status);
        var result=f.pokemonCheckup();
        check(result.events().stream().map(BattlefieldState.CheckupEvent::condition).toList().equals(List.of(
            SpecialCondition.POISONED,SpecialCondition.POISONED,SpecialCondition.BURNED,SpecialCondition.BURNED,SpecialCondition.ASLEEP,SpecialCondition.ASLEEP)),"双方异常状态没有按同一顺序整组处理");
        check(result.randomEvents().size()==4&&f.activeView(A).damage()==30&&f.activeView(B).damage()==30,"状态硬币或伤害重复／丢失");
    }
    private static void protection() {
        var guard=ResumableAbilityTest.ability("guard",EffectOriginProtectionTest.shield("ABILITY","EFFECTS_ONLY","SELF","SOURCE_NEXT_TURN_START"));
        var hit=ability("hit",false,"{\"type\":\"damage_counter\",\"target\":\"ALL_OPPONENT_POKEMON\",\"amount\":1}");
        var m=fixture(List.of(guard),List.of(hit));UUID source=m.activePokemonView(A).pokemonId();m.useAbility(A,source,guard.id());
        m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);m.endTurn(A);finishCheckup(m);
        check(m.activePokemonView(A).damage()==10&&m.benchPokemonView(A).getFirst().damage()==10,"特性防护未逐目标应用，或错误免疫规则中毒");
    }
    private static void sharedGameAndShorthand() {
        var once=BundledCombatData.ability(JsonParser.parseString("""
            {"id":"once","nameZh":"共享回合间夹具","kind":"TRIGGERED","effectSupport":"SUPPORTED",
             "eventPolicy":{"trigger":"ON_POKEMON_CHECKUP","optional":false,"usage":"ONCE_PER_GAME"},
             "usageLimit":{"group":"checkup-once","period":"GAME"},"effects":[{"type":"draw","amount":1}]}
            """).getAsJsonObject(),EffectRegistry.standard());
        TriggeredAbilityRules.validate(once);check(once.effectProgram().triggers().getFirst().trigger()==EffectTrigger.ON_POKEMON_CHECKUP,"简写程序入口错误");
        var m=fixture(List.of(once),List.of());int before=m.privateView(A).hand().size();m.endTurn(A);finishCheckup(m);
        check(m.privateView(A).hand().size()==before+1,"同组多来源绕过共享整局资格");
        m.endTurn(B);check(m.triggerOrderPrompt()==null&&m.privateView(A).hand().size()==before+2,"下次检查重复消耗整局特性，或漏抽正常回合牌");
    }
    static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    static void rejects(Runnable op) { try{op.run();}catch(IllegalArgumentException|IllegalStateException expected){return;}throw new AssertionError("非法操作未拒绝"); }
}
