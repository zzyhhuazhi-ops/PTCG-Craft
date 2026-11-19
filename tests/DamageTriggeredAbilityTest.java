import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Damage event is audited damage, not counters; reactions occur after full attack text and before KOs. */
public final class DamageTriggeredAbilityTest {
    static final UUID A=ResumableAbilityTest.A,B=ResumableAbilityTest.B,WATCH=ResumableAbilityTest.WATCH;
    static final String DRAW="{\"type\":\"draw\",\"amount\":1}";
    static final String SURVIVE="{\"type\":\"heal\",\"target\":\"SELF\",\"mode\":\"MINIMUM_HP\",\"amount\":10}";
    public static void main(String[] args) {
        damagedOwnerOrder(); wholeAttackFirst(); surviveOverkill(); damageNotCounters(); locationFilter();
        immunityAndDisabled(); simultaneousAndDirectKO(); coinSurvival(); malformedAtomic(); healingMode();
        reducedDamageAndRecoil();
        System.out.println("DamageTriggeredAbilityTest passed: actual damage, defender order, whole attack first, pre-KO survival and filtering");
    }
    static AbilityDefinition ability(String id,boolean activeOnly,boolean lethalOnly,boolean optional,String effects) {
        return BundledCombatData.ability(JsonParser.parseString("""
          {"id":"%s","nameZh":"受伤夹具%s","kind":"TRIGGERED","effectSupport":"SUPPORTED",
           "eventPolicy":{"trigger":"ON_DAMAGE","from":"OPPONENT_ATTACK","activeOnly":%s,"lethalOnly":%s,"optional":%s,"usage":"NONE"},
           "effects":[%s]}
          """.formatted(id,id,activeOnly,lethalOnly,optional,effects)).getAsJsonObject(),EffectRegistry.standard());
    }
    static AttackDefinition attack(int damage,String extra) {
        return BundledCombatData.attack(JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"攻击夹具\",\"energyCost\":{},\"baseDamage\":"+damage+",\"effectSupport\":\"SUPPORTED\""+extra+"}").getAsJsonObject(),EffectRegistry.standard());
    }
    static ServerMatch fixture(AttackDefinition attack,AbilityDefinition... abilities) {
        return SharedUsageLimitTest.attackFixture(92,List.of(abilities),List.of(attack));
    }
    static void finishTriggers(ServerMatch m) {
        int budget=20;
        while(m.triggerOrderPrompt()!=null&&budget-->0) {
            var p=m.triggerOrderPrompt();
            if(p.optionalDecision())m.decideTriggeredEffect(p.choosingPlayer(),p.id(),true);
            else m.chooseTriggeredEffect(p.choosingPlayer(),p.id(),p.entries().getFirst().id());
        }
        check(budget>0,"受伤触发未结束");
    }
    private static void damagedOwnerOrder() {
        var m=fixture(attack(10,""),ability("one",true,false,false,DRAW),ability("two",true,false,false,DRAW));
        int hand=m.privateView(B).hand().size();m.attack(A,"hit");var p=m.triggerOrderPrompt();
        check(p!=null&&p.choosingPlayer().equals(B)&&p.entries().size()==2,"受伤方没有得到排序权，或备战／攻击者误触发");
        reject(()->m.chooseTriggeredEffect(A,p.id(),p.entries().getFirst().id()));reject(()->m.endTurn(A));
        finishTriggers(m);check(m.privateView(B).hand().size()==hand+3&&m.currentPlayer().equals(B),"触发抽牌或回合推进错误");
        reject(()->m.chooseTriggeredEffect(B,p.id(),p.entries().getFirst().id()));
    }
    private static void wholeAttackFirst() {
        var m=fixture(attack(300,",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"choice\",\"options\":["+DRAW+","+DRAW+"]}]}]"),
            ability("optional",true,false,true,DRAW));
        int hand=m.privateView(B).hand().size();m.attack(A,"hit");var choice=m.effectChoicePrompt(A);
        check(choice!=null&&m.triggerOrderPrompt()==null&&m.activePokemonView(B).damage()==300,"受伤特性打断原攻击或提前KO");
        m.chooseEffectOption(A,choice.resolutionId(),choice.step(),0);var p=m.triggerOrderPrompt();
        check(p!=null&&p.optionalDecision()&&p.choosingPlayer().equals(B)&&m.phase()==MatchPhase.TURN,"攻击完成未在KO之前提供可选特性");
        var session=MatchSession.multiplayer(m,A,B);session.addSpectator(WATCH);
        check(session.view(A,A).options().size()==1&&session.view(WATCH,null).options().isEmpty(),"无权玩家可确认特性");
        m.decideTriggeredEffect(B,p.id(),true);
        check(m.privateView(B).hand().size()==hand+1&&m.phase()==MatchPhase.PRIZE_SELECTION,"KO来源没有完整执行触发");
        m.takePrizeAt(A,0);m.chooseReplacement(B,m.benchPokemonView(B).getFirst().pokemonId());
        check(m.currentPlayer().equals(B)&&m.turnNumber()==4,"受伤触发后没有恰好推进一次");
    }
    private static void surviveOverkill() {
        for(int damage:List.of(300,500)) {
            var m=fixture(attack(damage,",\"ignoresActiveDefenderDamageEffects\":true"),ability("survive",true,true,false,SURVIVE));
            UUID original=m.activePokemonView(B).pokemonId();m.attack(A,"hit");finishTriggers(m);
            check(m.phase()==MatchPhase.TURN&&m.activePokemonView(B).pokemonId().equals(original)&&m.activePokemonView(B).damage()==290,
                "将残余HP设为10误实现为简单回复10，或无视伤害效果错误跳过后伤害特性");
        }
    }
    private static void damageNotCounters() {
        var reaction=ability("reaction",true,false,false,DRAW);
        for(int damage:List.of(0,10)) {
            String extra=",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}]}]";
            var m=fixture(attack(damage,extra),reaction);int hand=m.privateView(B).hand().size();m.attack(A,"hit");finishTriggers(m);
            check(m.privateView(B).hand().size()==hand+1+(damage>0?1:0),"把放置指示物误算为攻击伤害触发");
        }
        var lethal=fixture(attack(10,",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}]}]"),
            ability("survive",true,true,false,SURVIVE));lethal.attack(A,"hit");
        check(lethal.phase()==MatchPhase.PRIZE_SELECTION,"非致命攻击伤害后的指示物KO错误满足伤害致命触发");
    }
    private static void locationFilter() {
        for(boolean activeOnly:List.of(true,false)) {
            var m=fixture(attack(10,",\"damageTarget\":\"ALL_OPPONENT_BENCH\""),ability("bench",activeOnly,false,false,DRAW));
            int hand=m.privateView(B).hand().size();m.attack(A,"hit");finishTriggers(m);
            check(m.privateView(B).hand().size()==hand+1+(activeOnly?0:1),"伤害地点过滤错误");
        }
    }
    private static void immunityAndDisabled() {
        var react=ability("react",true,false,false,DRAW);
        var shield=ResumableAbilityTest.ability("shield",EffectOriginProtectionTest.shield("ATTACK","DAMAGE_ONLY","OPPONENT_ACTIVE","LEAVES_PLAY"));
        for(boolean bypass:List.of(false,true)) {
            var m=fixture(attack(10,",\"ignoresActiveDefenderDamageEffects\":"+bypass),react,shield);
            m.useAbility(A,m.activePokemonView(A).pokemonId(),"shield");int hand=m.privateView(B).hand().size();
            m.attack(A,"hit");finishTriggers(m);
            check(m.privateView(B).hand().size()==hand+1+(bypass?1:0),"伤害防护或无视防护错误触发受伤事件");
        }
        var disabled=fixture(attack(10,""),react);disabled.disableAbilityEffectsByEffect(A,B,disabled.activePokemonView(B).pokemonId());
        int hand=disabled.privateView(B).hand().size();disabled.attack(A,"hit");check(disabled.privateView(B).hand().size()==hand+1,"失效特性仍触发");
        var guard=ResumableAbilityTest.ability("guard",EffectOriginProtectionTest.shield("ABILITY","EFFECTS_ONLY","SELF","LEAVES_PLAY"));
        var reflect=ability("reflect",true,false,false,"{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}");
        var protectedMatch=fixture(attack(10,""),guard,reflect);protectedMatch.useAbility(A,protectedMatch.activePokemonView(A).pokemonId(),"guard");
        protectedMatch.attack(A,"hit");finishTriggers(protectedMatch);
        check(protectedMatch.activePokemonView(A).damage()==0,"受伤反击丢失来源身份，绕过特性防护");
    }
    private static void simultaneousAndDirectKO() {
        var reflect=ability("reflect",true,false,false,"{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}");
        var m=fixture(attack(300,""),reflect);m.placeDamageCountersByEffect(A,A,m.activePokemonView(A).pokemonId(),29);
        m.attack(A,"hit");check(m.phase()==MatchPhase.PRIZE_SELECTION,"致命反击未同时击倒双方");
        check(m.privateView(A).discard().size()==1&&m.privateView(B).discard().size()==1,"未在一个检查点同时移场");
        int cap=6;while(m.phase()==MatchPhase.PRIZE_SELECTION&&cap-->0)m.takePrizeAt(m.pendingChoicePlayer(),0);
        while(m.phase()==MatchPhase.CHOOSE_REPLACEMENT&&cap-->0) {
            UUID owner=m.pendingChoicePlayer();m.chooseReplacement(owner,m.benchPokemonView(owner).getFirst().pokemonId());
        }
        check(cap>0&&m.turnNumber()==4&&m.currentPlayer().equals(B),"双方替补后攻击没有正常结束");
        var direct=fixture(attack(300,",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"knock_out\",\"target\":\"OPPONENT_ACTIVE\"}]}]"),
            ability("save",true,true,false,SURVIVE));direct.attack(A,"hit");
        check(direct.phase()==MatchPhase.PRIZE_SELECTION,"伤害自救错误取消直接击倒效果");
    }
    private static void coinSurvival() {
        var coin=ability("coin",true,true,false,"{\"type\":\"coin_flip\",\"flips\":1,\"heads\":["+SURVIVE+"],\"tails\":["+DRAW+"]}");
        boolean heads=false,tails=false;
        for(int seed=0;seed<30&&!(heads&&tails);seed++) {
            var m=SharedUsageLimitTest.attackFixture(seed,List.of(coin),List.of(attack(500,"")));int random=m.randomTranscript().size();
            m.attack(A,"hit");check(m.randomTranscript().size()==random+1,"受伤硬币未由服务器恰好掷一次");
            boolean survived=m.phase()==MatchPhase.TURN;heads|=survived;tails|=!survived;
            check(survived ? m.activePokemonView(B).damage()==290&&!m.lastAttack().knockedOut() : m.phase()==MatchPhase.PRIZE_SELECTION,
                "硬币分支或最终击倒记录错误");
        }
        check(heads&&tails,"测试未覆盖两种硬币结果");
    }
    private static void malformedAtomic() {
        for(String text:List.of("{\"type\":\"heal\",\"target\":\"SELF\",\"amount\":7}",
            "{\"type\":\"heal\",\"target\":\"SELF\",\"mode\":\"TYPO\",\"amount\":10}",
            "{\"type\":\"choice\",\"options\":["+DRAW+",{\"type\":\"heal\",\"amount\":7}]}")) {
            var bad=ability("bad",true,false,false,DRAW+","+text);reject(()->TriggeredAbilityRules.validate(bad));
            // ServerMatch rejects unsupported automatic programs before the game can be set up at all.
            reject(()->fixture(attack(10,""),bad));
        }
        var m=fixture(attack(10,""),ability("optional",true,false,true,DRAW));m.attack(A,"hit");m.concede(A);
        check(m.phase()==MatchPhase.FINISHED&&m.triggerOrderPrompt()==null,"认输没有清理攻击检查点");
    }
    private static void healingMode() {
        var heal=ResumableAbilityTest.ability("floor","{\"type\":\"heal\",\"target\":\"SELF\",\"mode\":\"MINIMUM_HP\",\"amount\":50}");
        var m=fixture(attack(10,""),heal);UUID p=m.activePokemonView(A).pokemonId();
        m.placeDamageCountersByEffect(A,A,p,20);check(!m.canUseAbility(A,p,"floor"),"已有100HP却允许无作用的50HP底线回复");
        m.placeDamageCountersByEffect(A,A,p,8);m.useAbility(A,p,"floor");check(m.activePokemonView(A).damage()==250,"参数化回复底线没有复用通用规则");
    }
    private static void reducedDamageAndRecoil() {
        var reaction=ability("react",true,false,false,DRAW);
        var reduction=new AbilityDefinition("reduction","减伤夹具",new AbilityEffect(AbilityEffect.Opcode.PASSIVE_REDUCE_OPPONENT_ATTACK_DAMAGE,20),
            EffectSupport.SUPPORTED,null,AbilityDefinition.Kind.PASSIVE);
        for(int damage:List.of(10,30)) {
            var m=fixture(attack(damage,""),reaction,reduction);int hand=m.privateView(B).hand().size();m.attack(A,"hit");finishTriggers(m);
            check(m.lastAttack().finalDamage()==Math.max(0,damage-20)&&m.privateView(B).hand().size()==hand+1+(damage>20?1:0),"使用减伤前数值触发");
        }
        var recoil=fixture(attack(0,",\"selfDamage\":300,\"selfHealAfterAttack\":20"),reaction);
        UUID original=recoil.activePokemonView(A).pokemonId();int hand=recoil.privateView(A).hand().size();recoil.attack(A,"hit");
        check(recoil.activePokemonView(A).pokemonId().equals(original)&&recoil.activePokemonView(A).damage()==280&&!recoil.lastAttack().knockedOut(),"旧反伤后回复在最终KO检查前被移除");
        check(recoil.privateView(A).hand().size()==hand,"自身反伤误触发对手攻击受伤事件");
        var invalid=fixture(attack(10,""),reaction);invalid.applySpecialConditionByEffect(A,A,SpecialCondition.CONFUSED);
        var before=invalid.clientView(A);var random=invalid.randomTranscript();reject(()->invalid.attack(A,"hit",UUID.randomUUID()));
        check(before.equals(invalid.clientView(A))&&random.equals(invalid.randomTranscript()),"伪造目标提前建立攻击状态或掷硬币");
    }
    static void reject(Runnable op) { ResumableAbilityTest.rejects(op); }
    static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
}
