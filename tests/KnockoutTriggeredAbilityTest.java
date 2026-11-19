import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** KO reactions run before simultaneous discard, prizes and replacement; never on mere damage. */
public final class KnockoutTriggeredAbilityTest {
    static final UUID A=DamageTriggeredAbilityTest.A,B=DamageTriggeredAbilityTest.B,WATCH=DamageTriggeredAbilityTest.WATCH;
    static final String DRAW=DamageTriggeredAbilityTest.DRAW;
    static AbilityDefinition ability(String id,String cause,boolean active,boolean optional,String effects) {
        return BundledCombatData.ability(JsonParser.parseString("""
          {"id":"%s","nameZh":"击倒夹具%s","kind":"TRIGGERED","effectSupport":"SUPPORTED",
           "eventPolicy":{"trigger":"ON_KNOCKOUT","cause":"%s","activeOnly":%s,"optional":%s,"usage":"NONE"},
           "effects":[%s]}
          """.formatted(id,id,cause,active,optional,effects)).getAsJsonObject(),EffectRegistry.standard());
    }
    static ServerMatch match(int damage,String extra,AbilityDefinition... abilities) {
        return DamageTriggeredAbilityTest.fixture(DamageTriggeredAbilityTest.attack(damage,extra),abilities);
    }
    public static void main(String[] args) {
        beforeDiscardAndOrder(); damageCause(); simultaneousChain(); checkup(); fullAttackAndSurvival();
        locationAndDisabled(); healThenDifferentCause(); checkupOrder(); directReactionAndProtection();
        sharedUsageAndInvalid(); privacyPracticeAndConcession(); allFieldDraw(); simultaneousOwnerOrder();
        System.out.println("KnockoutTriggeredAbilityTest passed: pre-discard events, cause, simultaneous chain, owner confirmation and checkup");
    }
    static void simultaneousOwnerOrder() {
        var m=match(300,",\"selfDamage\":300",ability("one","ANY",true,false,DRAW),ability("two","ANY",true,false,DRAW));
        m.attack(A,"hit");var p=m.triggerOrderPrompt();
        check(p!=null&&!p.optionalDecision()&&p.choosingPlayer().equals(A)&&p.entries().size()==4,"Simultaneous KOs lost active-player ordering");
        reject(()->m.chooseTriggeredEffect(B,p.id(),p.entries().getFirst().id()));DamageTriggeredAbilityTest.finishTriggers(m);
        check(m.phase()==MatchPhase.PRIZE_SELECTION,"Simultaneous KO ordering failed to finish");
    }
    static void beforeDiscardAndOrder() {
        var m=match(300,"",ability("one","OPPONENT_ATTACK_DAMAGE",true,true,DRAW),ability("two","ANY",true,false,DRAW));
        UUID original=m.activePokemonView(B).pokemonId();int hand=m.privateView(B).hand().size();
        m.attack(A,"hit");var p=m.triggerOrderPrompt();
        check(p!=null&&p.trigger()==EffectTrigger.ON_KNOCKOUT&&p.choosingPlayer().equals(B)&&p.entries().size()==2,"单只击倒的持有者未获得效果排序权");
        check(m.activePokemonView(B).pokemonId().equals(original)&&m.phase()==MatchPhase.TURN,"触发前提前移场或拿奖");
        reject(()->m.chooseTriggeredEffect(A,p.id(),p.entries().getFirst().id()));reject(()->m.endTurn(A));
        m.chooseTriggeredEffect(B,p.id(),p.entries().stream().filter(e->e.nameZh().endsWith("one")).findFirst().orElseThrow().id());
        var optional=m.triggerOrderPrompt();check(optional.optionalDecision()&&optional.choosingPlayer().equals(B),"可选确认未归拥有者");
        reject(()->m.decideTriggeredEffect(A,optional.id(),true));
        m.decideTriggeredEffect(B,optional.id(),true);
        check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.publicView().get(B).activeCardId()==null&&m.privateView(B).hand().size()==hand+2,"触发、移场、拿奖顺序错误");
        reject(()->m.decideTriggeredEffect(B,optional.id(),true));
        m.takePrizeAt(A,0);m.chooseReplacement(B,m.benchPokemonView(B).getFirst().pokemonId());
        check(m.turnNumber()==4&&m.currentPlayer().equals(B),"击倒反应后重复推进");
    }
    static void damageCause() {
        for(String cause:List.of("ANY","OPPONENT_ATTACK_DAMAGE")) {
            var reaction=ability("cause",cause,true,false,DRAW);
            var m=match(0,",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}]}]",reaction);
            int hand=m.privateView(B).hand().size();m.attack(A,"hit");
            check(m.privateView(B).hand().size()==hand+(cause.equals("ANY")?1:0),"指示物错误归因为攻击伤害");
            var direct=match(300,",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"knock_out\",\"target\":\"OPPONENT_ACTIVE\"}]}]",reaction);
            hand=direct.privateView(B).hand().size();direct.attack(A,"hit");
            check(direct.privateView(B).hand().size()==hand+(cause.equals("ANY")?1:0),"直接击倒错误归因为攻击伤害");
        }
    }
    static void simultaneousChain() {
        String reflect="{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}";
        var m=match(300,"",ability("chain","ANY",true,true,reflect));
        UUID a=m.activePokemonView(A).pokemonId(),b=m.activePokemonView(B).pokemonId();m.attack(A,"hit");
        var first=m.triggerOrderPrompt();check(first!=null&&first.choosingPlayer().equals(B),"首个击倒触发丢失");
        m.decideTriggeredEffect(B,first.id(),true);var second=m.triggerOrderPrompt();
        check(second!=null&&second.choosingPlayer().equals(A)&&m.activePokemonView(A).pokemonId().equals(a)&&m.activePokemonView(B).pokemonId().equals(b),"连锁击倒中提前移场");
        m.decideTriggeredEffect(A,second.id(),true);
        check(m.triggerOrderPrompt()==null&&m.publicView().get(A).activeCardId()==null&&m.publicView().get(B).activeCardId()==null,"重复捕获或未同时移除");
        check(m.phase()==MatchPhase.PRIZE_SELECTION,"连锁击倒未进入统一拿奖");
    }
    static void checkup() {
        var m=match(0,"",ability("checkup","ANY",true,true,DRAW));
        UUID target=m.activePokemonView(A).pokemonId();m.placeDamageCountersByEffect(A,A,target,29);
        m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);m.endTurn(A);
        var p=m.triggerOrderPrompt();check(p!=null&&p.trigger()==EffectTrigger.ON_KNOCKOUT&&m.phase()==MatchPhase.POKEMON_CHECKUP,"回合间击倒触发丢失");
        reject(()->m.endTurn(A));m.decideTriggeredEffect(A,p.id(),true);
        check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.publicView().get(A).activeCardId()==null,"回合间反应未继续击倒");
        m.takePrizeAt(B,0);m.chooseReplacement(A,m.benchPokemonView(A).getFirst().pokemonId());
        check(m.turnNumber()==4&&m.currentPlayer().equals(B),"回合间KO错误重进Checkup");
    }
    static void fullAttackAndSurvival() {
        var m=match(300,"",DamageTriggeredAbilityTest.ability("save",true,true,false,DamageTriggeredAbilityTest.SURVIVE),ability("ko","ANY",true,false,DRAW));
        int hand=m.privateView(B).hand().size();m.attack(A,"hit");
        check(m.activePokemonView(B).damage()==290&&m.privateView(B).hand().size()==hand+1,"受伤后存活仍触发KO");
        m=match(300,",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"choice\",\"options\":["+DRAW+","+DRAW+"]}]}]",ability("ko","ANY",true,true,DRAW));
        m.attack(A,"hit");check(m.effectChoicePrompt(A)!=null&&m.triggerOrderPrompt()==null,"KO触发打断原卡文字");
        var choice=m.effectChoicePrompt(A);m.chooseEffectOption(A,choice.resolutionId(),choice.step(),0);
        check(m.triggerOrderPrompt()!=null&&m.triggerOrderPrompt().trigger()==EffectTrigger.ON_KNOCKOUT,"全文后未进入KO反应");
    }
    static void locationAndDisabled() {
        for(boolean activeOnly:List.of(false,true)) {
            var m=match(300,",\"damageTarget\":\"ALL_OPPONENT_BENCH\"",ability("bench","OPPONENT_ATTACK_DAMAGE",activeOnly,false,DRAW));
            int hand=m.privateView(B).hand().size();m.attack(A,"hit");
            check(m.privateView(B).hand().size()==hand+(activeOnly?0:1)&&m.benchPokemonView(B).isEmpty(),"备战击倒筛选错误");
        }
        var m=match(300,"",ability("off","ANY",true,false,DRAW));
        m.disableAbilityEffectsByEffect(A,B,m.activePokemonView(B).pokemonId());int hand=m.privateView(B).hand().size();m.attack(A,"hit");
        check(m.privateView(B).hand().size()==hand,"失效来源仍产生击倒触发");
        var safe=match(10,"",ability("alive","ANY",true,false,DRAW));hand=safe.privateView(B).hand().size();safe.attack(A,"hit");
        check(safe.privateView(B).hand().size()==hand+1,"普通受伤被当作击倒");
    }
    static void healThenDifferentCause() {
        String text=",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"heal\",\"target\":\"OPPONENT_ACTIVE\",\"mode\":\"MINIMUM_HP\",\"amount\":10},{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}]}]";
        for(String cause:List.of("ANY","OPPONENT_ATTACK_DAMAGE")) {
            var m=match(500,text,ability("cause",cause,true,false,DRAW));int hand=m.privateView(B).hand().size();m.attack(A,"hit");
            check(m.privateView(B).hand().size()==hand+(cause.equals("ANY")?1:0),"回复救活后新的指示物KO继承旧攻击伤害原因");
        }
    }
    static void checkupOrder() {
        var m=match(0,"",ability("first","ANY",true,false,DRAW),ability("second","ANY",true,true,DRAW));
        m.placeDamageCountersByEffect(A,A,m.activePokemonView(A).pokemonId(),29);m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);
        m.endTurn(A);var p=m.triggerOrderPrompt();
        check(p!=null&&!p.optionalDecision()&&p.choosingPlayer().equals(A),"单只Checkup击倒排序权不在持有者");
        reject(()->m.chooseTriggeredEffect(B,p.id(),p.entries().getFirst().id()));
        DamageTriggeredAbilityTest.finishTriggers(m);check(m.phase()==MatchPhase.PRIZE_SELECTION,"Checkup击倒队列未结束");
    }
    static void directReactionAndProtection() {
        String ko="{\"type\":\"knock_out\",\"target\":\"OPPONENT_ACTIVE\"}";
        var reaction=ability("direct","OPPONENT_ATTACK_DAMAGE",true,false,ko);
        var m=match(300,"",reaction);m.attack(A,"hit");
        check(m.privateView(A).discard().size()==1&&m.privateView(B).discard().size()==1,"对手回合中的直接击倒反应未正确执行");
        var guard=ResumableAbilityTest.ability("guard",EffectOriginProtectionTest.shield("ABILITY","EFFECTS_ONLY","SELF","LEAVES_PLAY"));
        var protectedMatch=match(300,"",reaction,guard);protectedMatch.useAbility(A,protectedMatch.activePokemonView(A).pokemonId(),"guard");protectedMatch.attack(A,"hit");
        check(protectedMatch.privateView(A).discard().isEmpty()&&protectedMatch.activePokemonView(A).damage()==0,"击倒反应绕过特性来源防护");
        var ordinary=match(0,"",ability("draw","ANY",true,false,DRAW));
        ordinary.placeDamageCountersByEffect(A,B,ordinary.activePokemonView(B).pokemonId(),30);
        ordinary.takePrizeAt(A,0);ordinary.chooseReplacement(B,ordinary.benchPokemonView(B).getFirst().pokemonId());
        check(ordinary.currentPlayer().equals(A)&&ordinary.turnNumber()==3,"非攻击击倒错误结束回合");
    }
    static AbilityDefinition withUsage(AbilityDefinition base,EnergyTriggerPolicy.Usage usage,SharedUsageLimit limit) {
        var policy=(KnockoutTriggerPolicy)base.eventPolicy();
        return new AbilityDefinition(base.id(),base.nameZh(),base.effect(),base.effectSupport(),null,base.kind(),base.effectProgram(),
            new KnockoutTriggerPolicy(policy.cause(),policy.activeOnly(),policy.optional(),usage),limit);
    }
    static void sharedUsageAndInvalid() {
        var base=ability("share","ANY",false,true,DRAW);
        reject(()->TriggeredAbilityRules.validate(withUsage(base,EnergyTriggerPolicy.Usage.ONCE_PER_TURN,null)));
        reject(()->TriggeredAbilityRules.validate(withUsage(base,EnergyTriggerPolicy.Usage.NONE,new SharedUsageLimit("ko-turn",SharedUsageLimit.Period.TURN))));
        reject(()->TriggeredAbilityRules.validate(withUsage(base,EnergyTriggerPolicy.Usage.ONCE_PER_GAME,null)));
        var shared=withUsage(base,EnergyTriggerPolicy.Usage.ONCE_PER_GAME,new SharedUsageLimit("ko-shared",SharedUsageLimit.Period.GAME));
        var m=match(0,"",shared);int hand=m.privateView(B).hand().size();
        m.placeDamageCountersAcrossTargetsByEffect(A,List.of(new ServerMatch.DamageCounterPlacement(B,m.activePokemonView(B).pokemonId(),30),new ServerMatch.DamageCounterPlacement(B,m.benchPokemonView(B).getFirst().pokemonId(),30)));
        DamageTriggeredAbilityTest.finishTriggers(m);check(m.privateView(B).hand().size()==hand+1,"同一玩家共享GAME次数被多来源击倒绕过");
        for(String effect:List.of("{\"type\":\"heal\",\"amount\":7}","{\"type\":\"choice\",\"options\":["+DRAW+",{\"type\":\"heal\",\"amount\":7}]}")) {
            var bad=ability("bad","ANY",true,false,effect);reject(()->TriggeredAbilityRules.validate(bad));reject(()->match(0,"",bad));
        }
        reject(()->ability("bad","GUESS",true,false,DRAW));
    }
    static void privacyPracticeAndConcession() {
        var m=match(300,"",ability("optional","ANY",true,true,DRAW));var s=MatchSession.multiplayer(m,A,B);s.addSpectator(WATCH);m.attack(A,"hit");
        var gson=new com.google.gson.Gson();var p=m.triggerOrderPrompt();var random=m.randomTranscript();
        for(UUID recipient:List.of(A,B,WATCH)) {
            String json=gson.toJson(s.view(recipient,recipient.equals(WATCH)?null:recipient));
            var view=gson.fromJson(json,MatchSession.Snapshot.class);check(p.equals(view.game().triggerOrder()),"序列化丢失击倒事件提示");
            if(!recipient.equals(B))for(var card:m.privateView(B).hand())check(!json.contains(card.instanceId().toString()),"KO等待泄露手牌");
            for(var card:m.privateView(B).prizes())check(!json.contains(card.instanceId().toString()),"KO等待泄露奖赏");
            for(var card:m.privateView(B).deck())check(!json.contains(card.instanceId().toString()),"KO等待泄露牌库");
        }
        check(random.equals(m.randomTranscript()),"KO刷新重复随机");var view=s.view(B,B);var use=view.options().stream().filter(o->o.labelZh().startsWith("使用触发特性")).findFirst().orElseThrow();
        reject(()->s.submit(A,B,view.revision(),use.index()));s.submit(B,B,view.revision(),use.index());reject(()->s.submit(B,B,view.revision(),use.index()));
        var practice=match(300,"",ability("practice","ANY",true,true,DRAW));var ps=MatchSession.practice(practice,WATCH,A,B);practice.attack(A,"hit");
        var pv=ps.view(WATCH,B);var decline=pv.options().stream().filter(o->o.labelZh().equals("放弃触发特性")).findFirst().orElseThrow();ps.submit(WATCH,B,pv.revision(),decline.index());
        check(practice.phase()==MatchPhase.PRIZE_SELECTION,"练习换席未使用同一服务器KO核心");
        var concede=match(300,"",ability("concede","ANY",true,true,DRAW));concede.attack(A,"hit");var pending=concede.triggerOrderPrompt();concede.concede(B);
        check(concede.phase()==MatchPhase.FINISHED&&concede.triggerOrderPrompt()==null,"认输未清理KO事务");reject(()->concede.decideTriggeredEffect(B,pending.id(),true));
    }
    static void allFieldDraw() {
        var m=match(300,"",ability("all","ANY",true,true,"{\"type\":\"damage_counter\",\"target\":\"ALL_OPPONENT_POKEMON\",\"amount\":30}"));
        m.attack(A,"hit");DamageTriggeredAbilityTest.finishTriggers(m);
        int cap=5;while(m.phase()==MatchPhase.PRIZE_SELECTION&&cap-->0)m.takePrizeAt(m.pendingChoicePlayer(),0);
        check(cap>0&&m.phase()==MatchPhase.FINISHED&&m.result().reason()==MatchResult.Reason.DRAW,"反击全场同时无宝可梦没有统一平局判定");
    }
    static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    static void reject(Runnable action) { try { action.run(); } catch(IllegalArgumentException|IllegalStateException expected) { return; } throw new AssertionError("非法操作未被拒绝"); }
}
