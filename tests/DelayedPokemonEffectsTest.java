import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** All cards here are synthetic programs; no unreviewed card is enabled. */
public final class DelayedPokemonEffectsTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static String delayed(String body){return "{\"type\":\"delayed_effect\",\"target\":\"OPPONENT_ACTIVE\",\"when\":\"OPPONENT_NEXT_TURN_END\",\"clear\":\"BENCH_EVOLVE_LEAVE\",\"effects\":["+body+"]}";}
    static final String KO="{\"type\":\"knock_out\",\"target\":\"TRIGGER_POKEMON\"}";
    static final String COUNTERS="{\"type\":\"damage_counter\",\"target\":\"TRIGGER_POKEMON\",\"amount\":3}";
    static ServerMatch fixture(String body){return BeforeDamageAttackTest.fixture(BeforeDamageAttackTest.attack("","",delayed(body)));}
    static void check(boolean b,String m){BeforeDamageAttackTest.check(b,m);}
    static void reject(Runnable r){BeforeDamageAttackTest.reject(r);}
    public static void main(String[] args) {
        knockoutAtBoundary(); clearAndReentry(); sourceDeparture(); preventionTiming(); delayedChoice(); invalidPrograms();
        ownNextBoundary(); dueConditionsAndCoin(); simultaneousKnockouts(); pendingPrivacyAndConcede(); orderingPrivacy();
        knockoutCauseAndFinality(); statusBeforeCheckup(); devolutionClears();
        System.out.println("DelayedPokemonEffectsTest passed: delayed target identity, cleanup, original source and end-turn continuation");
    }
    static void knockoutAtBoundary() {
        var m=fixture(KO);UUID target=m.activePokemonView(B).pokemonId(),physical=PokemonIdentityTestSupport.physical(m,B,target);m.attack(A,"hit");
        check(m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(B)&&m.activePokemonView(B).pokemonId().equals(target),"延迟击倒被立即执行");
        check(m.activePokemonView(B).continuousEffectZh().contains("延迟"),"场上没有公开延迟提示");m.endTurn(B);
        check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.pendingChoicePlayer().equals(A),"延迟到期没有经过统一击倒拿奖");
        m.takePrizeAt(A,0);m.chooseReplacement(B,m.benchPokemonView(B).getFirst().pokemonId());
        check(m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(A),"延迟击倒替补后未恰好推进一次");
    }
    static void clearAndReentry() {
        for(String operation:List.of("switch","evolve","leave")) {
            var m=fixture(COUNTERS);UUID target=m.activePokemonView(B).pokemonId(),physical=PokemonIdentityTestSupport.physical(m,B,target);m.attack(A,"hit");
            if(operation.equals("switch")) {
                m.switchActiveByEffect(B,B,m.benchPokemonView(B).getFirst().pokemonId());m.switchActiveByEffect(B,B,target);
            } else if(operation.equals("evolve"))m.evolve(B,target,DynamicPassiveModifierTest.hand(m,B,"evolved").instanceId());
            else {
                m.movePokemonByEffect(B,B,target,LeavePlayDestination.HAND);m.chooseReplacement(B,m.benchPokemonView(B).getFirst().pokemonId());
                m.playBasicPokemonToBench(B,physical);UUID returned=PokemonIdentityTestSupport.logical(m,B,physical);check(!returned.equals(target),"Reentry reused logical identity");m.switchActiveByEffect(B,B,returned);
            }
            check(!m.activePokemonView(B).continuousEffectZh().contains("延迟"),"清除动作遗留延迟标记："+operation);
            int damage=m.activePokemonView(B).damage();m.endTurn(B);
            check(m.activePokemonView(B).damage()==damage&&m.currentPlayer().equals(A),"旧延迟追踪了换位／进化／重新入场的目标："+operation);
        }
    }
    static void sourceDeparture() {
        var m=fixture(COUNTERS);UUID source=m.activePokemonView(A).pokemonId();m.attack(A,"hit");
        m.movePokemonByEffect(B,A,source,LeavePlayDestination.HAND);m.chooseReplacement(A,m.benchPokemonView(A).getFirst().pokemonId());m.endTurn(B);
        check(m.activePokemonView(B).damage()==110&&m.currentPlayer().equals(A),"已施加的延迟依赖原攻击者仍在场");
        int before=m.activePokemonView(B).damage();m.endTurn(A);m.endTurn(B);check(m.activePokemonView(B).damage()==before,"同一延迟重复到期");
    }
    static AbilityDefinition guard() {
        return BundledCombatData.ability(JsonParser.parseString("{\"id\":\"guard\",\"nameZh\":\"防护测试\",\"effectSupport\":\"SUPPORTED\",\"effects\":[{\"type\":\"prevent_effects\",\"target\":\"SELF\",\"source\":\"ATTACK\",\"from\":\"OPPONENT\",\"scope\":\"EFFECTS_ONLY\",\"duration\":\"LEAVES_PLAY\"}]}").getAsJsonObject(),EffectRegistry.standard());
    }
    static void preventionTiming() {
        for(boolean before:List.of(false,true)) {
            var m=BeforeDamageAttackTest.fixture(BeforeDamageAttackTest.attack("","",delayed(COUNTERS)),guard());
            if(before){m.endTurn(A);m.useAbility(B,m.activePokemonView(B).pokemonId(),"guard");m.endTurn(B);}
            m.attack(A,"hit");
            if(!before)m.useAbility(B,m.activePokemonView(B).pokemonId(),"guard");m.endTurn(B);
            check(m.activePokemonView(B).damage()==(before?80:110),"防护应阻止新效果，但不能移除已经施加的延迟");
        }
    }
    static void delayedChoice() {
        var m=fixture("{\"type\":\"draw\",\"amount\":1},{\"type\":\"choice\",\"effects\":["+COUNTERS+",{\"type\":\"draw\",\"amount\":1}]}" );
        m.attack(A,"hit");int size=m.privateView(A).hand().size();m.endTurn(B);var p=m.effectChoicePrompt(A);
        check(m.phase()==MatchPhase.TURN_END&&p!=null&&m.privateView(A).hand().size()==size+1,"延迟效果未保留原控制者或暂停前缀");
        reject(()->m.chooseEffectOption(B,p.resolutionId(),p.step(),0));m.chooseEffectOption(A,p.resolutionId(),p.step(),0);
        check(m.privateView(A).hand().size()==size+2&&m.activePokemonView(B).damage()==110&&m.currentPlayer().equals(A),"延迟选择重复前缀或漏掉下回合抽牌");
    }
    static void invalidPrograms() {
        reject(()->fixture(delayed(KO))); // no recursive schedules
        reject(()->fixture("{\"type\":\"knock_out\",\"target\":\"OPPONENT_ACTIVE\"}")); // original target only
        reject(()->fixture("{\"type\":\"conditional\",\"conditions\":[{\"type\":\"COIN_RESULT\",\"value\":\"HEADS\"}],\"effects\":["+KO+"]}"));
        reject(()->BeforeDamageAttackTest.attack("","",delayed(COUNTERS).replace("BENCH_EVOLVE_LEAVE","NEVER")));
        reject(()->BeforeDamageAttackTest.attack("","",delayed(COUNTERS).replace("OPPONENT_NEXT_TURN_END","NOW")));
        reject(()->fixture("{\"type\":\"damage_counter\",\"target\":\"TRIGGER_POKEMON\",\"amount\":3,\"typo\":1}"));
        var delayedEffect=EffectRegistry.standard().parse(JsonParser.parseString(delayed(COUNTERS)).getAsJsonObject());
        reject(()->ResumableProgramRules.validate(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ONCE_PER_TURN,List.of(),List.of(delayedEffect)))),true));
        reject(()->ResumableProgramRules.validate(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_CARD_PLAYED,List.of(),List.of(delayedEffect)))),false));
    }
    static void ownNextBoundary() {
        var m=BeforeDamageAttackTest.fixture(BeforeDamageAttackTest.attack("","",delayed(COUNTERS).replace("OPPONENT_NEXT_TURN_END","OWN_NEXT_TURN_END")));
        m.attack(A,"hit");check(m.activePokemonView(B).damage()==80,"己方下回合末误算成本回合末");
        m.endTurn(B);check(m.activePokemonView(B).damage()==80,"己方到期误算为对方回合末");m.endTurn(A);
        check(m.activePokemonView(B).damage()==110&&m.currentPlayer().equals(B),"己方下回合末未执行");
    }
    static void dueConditionsAndCoin() {
        var m=fixture("{\"type\":\"conditional\",\"conditions\":[{\"type\":\"HP_BELOW\",\"target\":\"TRIGGER_POKEMON\",\"value\":200}],\"effects\":["+COUNTERS+"]}");
        m.attack(A,"hit");m.placeDamageCountersByEffect(B,B,m.activePokemonView(B).pokemonId(),3);m.endTurn(B);
        check(m.activePokemonView(B).damage()==140,"到期条件没有读取原目标的新状态");
        m=fixture("{\"type\":\"coin_flip\",\"flips\":1,\"heads\":["+COUNTERS+"],\"tails\":[{\"type\":\"repeat\",\"count\":2,\"effects\":["+COUNTERS+"]}]}" );
        var rng=m.randomTranscript();m.attack(A,"hit");check(rng.equals(m.randomTranscript()),"延迟硬币在攻击时提前投掷");m.endTurn(B);
        check(m.randomTranscript().size()==rng.size()+1&&Set.of(110,140).contains(m.activePokemonView(B).damage()),"延迟硬币或嵌套分支没有复用服务器随机");
    }
    static void simultaneousKnockouts() {
        var m=BeforeDamageAttackTest.fixture(BeforeDamageAttackTest.attack("","",delayed(KO)+","+delayed(KO)));
        m.attack(A,"hit");m.endTurn(B);var p=m.triggerOrderPrompt();check(p!=null&&p.entries().size()==2,"同一边界丢失独立延迟效果");
        m.chooseTriggeredEffect(B,p.id(),p.entries().getFirst().id());check(m.phase()==MatchPhase.PRIZE_SELECTION,"延迟击倒未进入统一拿奖");
        m.takePrizeAt(A,0);m.chooseReplacement(B,m.benchPokemonView(B).getFirst().pokemonId());
        check(m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(A)&&m.privateView(A).prizes().size()==5,"已离场目标的第二个延迟追到替补或卡住回合");
    }
    static void pendingPrivacyAndConcede() {
        var m=fixture("{\"type\":\"choice\",\"effects\":["+COUNTERS+",{\"type\":\"draw\",\"amount\":1}]}" );
        UUID source=m.activePokemonView(A).pokemonId(),watcher=BeforeDamageAttackTest.WATCH;m.attack(A,"hit");
        m.movePokemonByEffect(B,A,source,LeavePlayDestination.HAND);m.chooseReplacement(A,m.benchPokemonView(A).getFirst().pokemonId());
        int replay=m.replayTranscript().size();m.endTurn(B);var session=MatchSession.multiplayer(m,A,B);session.addSpectator(watcher);
        var rng=m.randomTranscript();
        for(UUID viewer:List.of(A,B,watcher)) {
            var view=session.view(viewer,viewer.equals(watcher)?null:viewer);String json=new com.google.gson.Gson().toJson(view);
            check(!json.contains("effectProgram"),"延迟规则程序被外发");
            for(UUID owner:List.of(A,B)) {
                var hidden=new ArrayList<CardInstance>(m.privateView(owner).deck());hidden.addAll(m.privateView(owner).prizes());
                if(!owner.equals(viewer))hidden.addAll(m.privateView(owner).hand());
                for(var card:hidden)check(!json.contains(card.instanceId().toString()),"延迟续接泄露原攻击者或隐藏卡");
            }
        }
        check(rng.equals(m.randomTranscript()),"只读延迟视图消耗随机");
        check(m.replayTranscript().subList(replay,m.replayTranscript().size()).stream().noneMatch(e->e.operation().contains(source.toString())),"到期回放重新公开已回手来源的身份");
        m.concede(B);check(m.phase()==MatchPhase.FINISHED&&!m.activePokemonView(B).continuousEffectZh().contains("延迟"),"结束对局未清理延迟标记");
    }
    static void orderingPrivacy() {
        var m=BeforeDamageAttackTest.fixture(BeforeDamageAttackTest.attack("","",delayed(COUNTERS)+","+delayed(COUNTERS)));
        UUID source=m.activePokemonView(A).pokemonId();m.attack(A,"hit");m.movePokemonByEffect(B,A,source,LeavePlayDestination.HAND);
        m.chooseReplacement(A,m.benchPokemonView(A).getFirst().pokemonId());m.endTurn(B);
        var session=MatchSession.multiplayer(m,A,B);session.addSpectator(BeforeDamageAttackTest.WATCH);
        for(UUID viewer:List.of(B,BeforeDamageAttackTest.WATCH)) {
            String json=new com.google.gson.Gson().toJson(session.view(viewer,viewer.equals(B)?B:null));
            check(!json.contains(source.toString()),"延迟排序提示泄露已回手的原攻击者");
        }
        var prompt=m.triggerOrderPrompt();m.chooseTriggeredEffect(B,prompt.id(),prompt.entries().getFirst().id());
        check(m.currentPlayer().equals(A)&&m.activePokemonView(B).damage()==140,"多个非击倒延迟没有各执行一次");
    }
    static void knockoutCauseAndFinality() {
        for(String cause:List.of("ANY","OPPONENT_ATTACK_DAMAGE")) {
            var reaction=KnockoutTriggeredAbilityTest.ability("delayed-cause",cause,true,false,"{\"type\":\"draw\",\"amount\":1}");
            var m=BeforeDamageAttackTest.fixture(BeforeDamageAttackTest.attack("","",delayed(KO+",{\"type\":\"heal\",\"target\":\"TRIGGER_POKEMON\",\"amount\":100}")),reaction);
            m.attack(A,"hit");int hand=m.privateView(B).hand().size();m.endTurn(B);
            check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.privateView(B).hand().size()==hand+(cause.equals("ANY")?1:0),"延迟直接击倒被回复取消或被错误计为攻击伤害击倒");
        }
    }
    static void statusBeforeCheckup() {
        var m=fixture("{\"type\":\"poison\",\"target\":\"TRIGGER_POKEMON\"}");m.attack(A,"hit");m.endTurn(B);
        check(m.activePokemonView(B).conditions().contains(SpecialCondition.POISONED)&&m.activePokemonView(B).damage()==90,"延迟中毒未先于随后的回合间检查");
    }
    static void devolutionClears() {
        var m=fixture(COUNTERS);UUID target=m.activePokemonView(B).pokemonId();m.endTurn(A);
        m.evolve(B,target,DynamicPassiveModifierTest.hand(m,B,"evolved").instanceId());m.endTurn(B);m.attack(A,"hit");
        m.devolvePokemonToHandByEffect(B,B,target);int damage=m.activePokemonView(B).damage();m.endTurn(B);
        check(m.activePokemonView(B).damage()==damage&&!m.activePokemonView(B).continuousEffectZh().contains("延迟"),"退化未复用清除攻击效果的流程");
    }
}
