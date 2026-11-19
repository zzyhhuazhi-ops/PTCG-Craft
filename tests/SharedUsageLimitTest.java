import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Public JSON -> authoritative activation; no real cards are implicitly enabled. */
public final class SharedUsageLimitTest {
    static final UUID A=ResumableAbilityTest.A,B=ResumableAbilityTest.B,WATCH=ResumableAbilityTest.WATCH;
    static AbilityDefinition ability(String id,String group,String period,String effects) {
        return BundledCombatData.ability(JsonParser.parseString("{\"id\":\""+id+"\",\"nameZh\":\"共享资格夹具\",\"effectSupport\":\"SUPPORTED\",\"usageLimit\":{\"group\":\""+group+"\",\"period\":\""+period+"\"},\"effects\":["+effects+"]}").getAsJsonObject(),EffectRegistry.standard());
    }
    public static void main(String[]args) {
        perTurnAndPlayer(); aliasesAndLeavePlay(); pendingAndPractice(); failedActivation(); attackAndAbilityPool();
        confusionAndValidation(); legacyAndTriggered(); malformedAndLedger(); evolutionKeepsSharedUsage(); zeroDamageAndInvalidCost();
        System.out.println("SharedUsageLimitTest passed: shared turn/game pools, GX/VSTAR attack and Ability, pause, entry, authority and markers");
    }
    static void perTurnAndPlayer() {
        var shared=ability("shared","quick-search","TURN","{\"type\":\"draw\",\"amount\":1}");
        var m=ResumableAbilityTest.fixture(shared);UUID active=m.activePokemonView(A).pokemonId(),bench=m.benchPokemonView(A).getFirst().pokemonId();
        m.useAbility(A,active,shared.id());var before=m.privateView(A);
        check(!m.canUseAbility(A,bench,shared.id()),"另一只宝可梦绕过全队共享次数");
        ResumableAbilityTest.rejects(()->m.useAbility(A,bench,shared.id()));
        check(before.equals(m.privateView(A)),"共享次数拒绝后仍抽牌");
        m.endTurn(A);m.useAbility(B,m.activePokemonView(B).pokemonId(),shared.id());m.endTurn(B);
        check(m.canUseAbility(A,bench,shared.id()),"下一回合共享次数未恢复");
    }
    static void aliasesAndLeavePlay(){
        var first=ability("one","shared-name","TURN","{\"type\":\"draw\",\"amount\":1}");
        var alias=ability("reprint","shared-name","TURN","{\"type\":\"draw\",\"amount\":2}");
        var other=ResumableAbilityTest.ability("other","{\"type\":\"draw\",\"amount\":1}");
        var m=ResumableAbilityTest.fixture(first,alias,other);UUID source=m.benchPokemonView(A).getFirst().pokemonId();
        m.useAbility(A,source,first.id());check(!m.canUseAbility(A,m.activePokemonView(A).pokemonId(),alias.id()),"不同印刷特性ID未共用组资格");
        check(m.canUseAbility(A,source,other.id()),"普通独立特性被误共享");
        UUID physical=PokemonIdentityTestSupport.physical(m,A,source);m.switchActiveByEffect(A,A,source);m.movePokemonByEffect(A,A,source,LeavePlayDestination.HAND);
        m.chooseReplacement(A,m.benchPokemonView(A).getFirst().pokemonId());m.playBasicPokemonToBench(A,physical);UUID returned=PokemonIdentityTestSupport.logical(m,A,physical);check(!returned.equals(source),"Shared-usage source reentry reused old logical identity");
        check(!m.canUseAbility(A,returned,first.id()),"离场重新登场重置玩家共享资格");
        var fresh=ResumableAbilityTest.fixture(first);check(fresh.canUseAbility(A,fresh.activePokemonView(A).pokemonId(),first.id()),"新对局继承旧资格");
    }
    static void pendingAndPractice(){
        var power=ability("power",SharedUsageLimit.VSTAR,"GAME","{\"type\":\"choice\",\"effects\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"draw\",\"amount\":2}]}");
        var m=ResumableAbilityTest.fixture(power);var session=MatchSession.practice(m,WATCH,A,B);UUID source=m.activePokemonView(A).pokemonId();
        var view=session.view(WATCH,A);var option=view.options().stream().filter(o->o.cardKeys().contains("field:"+source)&&o.labelZh().startsWith("使用特性")).findFirst().orElseThrow();
        session.submit(WATCH,A,view.revision(),option.index());var pending=m.effectChoicePrompt(A);var random=m.randomTranscript();
        for(UUID seat:Arrays.asList(A,B,null)) {
            var decoded=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(m.clientView(seat)),MatchClientView.class);
            check(marker(decoded,A).vstarUsed()&&!marker(decoded,B).vstarUsed()&&!marker(decoded,A).gxUsed(),"公开力量标记归属错误");
        }
        check(session.view(WATCH,B).game().viewingSeat().equals(B),"练习换席失败");
        ResumableAbilityTest.rejects(()->m.chooseEffectOption(B,pending.resolutionId(),pending.step(),0));
        ResumableAbilityTest.rejects(()->m.chooseEffectOption(A,pending.resolutionId(),pending.step(),99));
        check(random.equals(m.randomTranscript())&&marker(m.clientView(A),A).vstarUsed(),"刷新或非法选择重置标记／随机");
        m.chooseEffectOption(A,pending.resolutionId(),pending.step(),0);m.endTurn(A);m.endTurn(B);
        check(!m.canUseAbility(A,m.benchPokemonView(A).getFirst().pokemonId(),power.id()),"整局资格在下回合恢复");
        check(m.abilityOptions(A).stream().allMatch(ServerMatch.AbilityOption::usedThisTurn),"操作列表未报告共享资格已用");
    }
    static void failedActivation(){
        var invalid=ability("bad",SharedUsageLimit.VSTAR,"GAME","{\"type\":\"draw\",\"amount\":1},{\"type\":\"heal\",\"target\":\"SELF\",\"amount\":11}");
        var good=ability("good",SharedUsageLimit.VSTAR,"GAME","{\"type\":\"draw\",\"amount\":1}");
        var base=ability("gate",SharedUsageLimit.VSTAR,"GAME","{\"type\":\"draw\",\"amount\":1}");
        var gate=new AbilityDefinition(base.id(),base.nameZh(),base.effect(),base.effectSupport(),null,base.kind(),new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ONCE_PER_TURN,
            List.of(new ConditionDefinition(ConditionType.DAMAGE_COUNTER_COUNT,Map.of("target","SELF","value","1"),List.of())),base.effectProgram().triggers().getFirst().effects()))),null,base.usageLimit());
        var m=ResumableAbilityTest.fixture(invalid,good,gate);UUID source=m.activePokemonView(A).pokemonId();var before=m.clientView(A);var random=m.randomTranscript();
        ResumableAbilityTest.rejects(()->m.useAbility(A,source,invalid.id()));ResumableAbilityTest.rejects(()->m.useAbility(A,source,gate.id()));
        check(before.equals(m.clientView(A))&&random.equals(m.randomTranscript()),"校验失败却消耗共享资格或产生前缀");
        m.useAbility(A,source,good.id());check(marker(m.clientView(A),A).vstarUsed(),"合法特性未消耗资格");
    }
    static AttackDefinition attack(String id,String group){
        return BundledCombatData.attack(JsonParser.parseString("{\"id\":\""+id+"\",\"nameZh\":\"力量攻击夹具\",\"energyCost\":{},\"baseDamage\":10,\"effectSupport\":\"SUPPORTED\",\"effects\":[]"+(group==null?"":",\"usageLimit\":{\"group\":\""+group+"\",\"period\":\"GAME\"}")+"}").getAsJsonObject(),EffectRegistry.standard());
    }
    static ServerMatch attackFixture(long seed,List<AbilityDefinition> abilities,List<AttackDefinition> attacks){
        var card=new CardDefinition("basic","共享规则夹具",CardCategory.POKEMON,TrainerCategory.NONE,"H",Set.of("international"),true,false,60,Map.of(),EffectSupport.SUPPORTED,null);
        var combat=new CombatCatalog(Map.of("basic",new PokemonProfile(300,0,null,null,0,null,0,1,attacks,abilities)),Map.of());
        var m=new ServerMatch(A,B,ResumableAbilityTest.deck(A),ResumableAbilityTest.deck(B),BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(List.of(card)),combat,new ServerRandom(seed));
        OpeningTestSupport.complete(m,A,B,1);m.endTurn(A);m.endTurn(B);return m;
    }
    static void attackAndAbilityPool(){
        var power=ability("power",SharedUsageLimit.VSTAR,"GAME","{\"type\":\"draw\",\"amount\":1}");
        var star=attack("star",SharedUsageLimit.VSTAR);var gx=attack("gx",SharedUsageLimit.GX);var plain=attack("ordinary-gx",null);
        check(Objects.equals(star.withEffectProgram(EffectProgram.EMPTY).usageLimit(),star.usageLimit()),"替换攻击组件程序丢失力量资格");
        var m=attackFixture(5,List.of(power),List.of(star,gx,plain));m.useAbility(A,m.activePokemonView(A).pokemonId(),power.id());var before=m.clientView(A);
        ResumableAbilityTest.rejects(()->m.attack(A,star.id()));check(before.equals(m.clientView(A)),"VSTAR特性后仍能用VSTAR攻击");
        m.attack(A,gx.id());check(marker(m.clientView(B),A).gxUsed()&&marker(m.clientView(B),A).vstarUsed(),"GX与VSTAR未作为独立资格");
        m.attack(B,star.id());check(marker(m.clientView(A),B).vstarUsed()&&!marker(m.clientView(A),B).gxUsed(),"双方共享了同一个力量标记");
        m.switchActiveByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId());ResumableAbilityTest.rejects(()->m.attack(A,gx.id()));
        check(m.activeAttacks(A).size()==1&&m.activeAttacks(A).getFirst().id().equals(plain.id()),"失效攻击仍出现在动作列表或仅按名字识别GX");
        m.attack(A,plain.id());m.endTurn(B);
        var inverse=attackFixture(5,List.of(power),List.of(star));inverse.attack(A,star.id());inverse.endTurn(B);
        check(!inverse.canUseAbility(A,inverse.activePokemonView(A).pokemonId(),power.id()),"VSTAR攻击后仍能使用VSTAR特性");
    }
    static void confusionAndValidation(){
        var gx=attack("gx",SharedUsageLimit.GX);boolean failed=false,succeeded=false;
        for(int seed=0;seed<30 && !(failed&&succeeded);seed++) {
            var m=attackFixture(seed,List.of(),List.of(gx));m.applySpecialConditionByEffect(A,A,SpecialCondition.CONFUSED);
            var before=m.clientView(A);var random=m.randomTranscript();
            ResumableAbilityTest.rejects(()->m.attack(A,gx.id(),UUID.randomUUID()));
            check(before.equals(m.clientView(A))&&random.equals(m.randomTranscript()),"非法目标提前掷混乱或消耗力量资格");
            m.attack(A,gx.id());boolean confusionFailed=m.lastAttack().selfDamage()==30;
            check(marker(m.clientView(B),A).gxUsed()!=confusionFailed,"混乱取消攻击的力量消耗时点错误");
            failed|=confusionFailed;succeeded|=!confusionFailed;
        }
        check(failed&&succeeded,"夹具未覆盖混乱两种结果");
        var m=attackFixture(5,List.of(),List.of(gx));m.applySpecialConditionByEffect(A,A,SpecialCondition.ASLEEP);var before=m.clientView(A);
        ResumableAbilityTest.rejects(()->m.attack(A,gx.id()));check(before.equals(m.clientView(A)),"睡眠时非法GX消耗资格");
    }
    static void legacyAndTriggered(){
        var legacy=BundledCombatData.ability(JsonParser.parseString("{\"id\":\"legacy\",\"nameZh\":\"旧检索夹具\",\"effectSupport\":\"SUPPORTED\",\"usageLimit\":{\"group\":\"legacy-search\",\"period\":\"TURN\"},\"effect\":{\"opcode\":\"SEARCH_CARD_TO_HAND\",\"amount\":1}}").getAsJsonObject(),EffectRegistry.standard());
        var m=ResumableAbilityTest.fixture(legacy);UUID source=m.activePokemonView(A).pokemonId(),bench=m.benchPokemonView(A).getFirst().pokemonId();
        ResumableAbilityTest.rejects(()->m.useAbilityWithDeckSearch(A,source,legacy.id(),UUID.randomUUID()));
        m.useAbilityWithDeckSearch(A,source,legacy.id(),m.privateView(A).deck().getFirst().instanceId());
        check(!m.canUseAbilityDeckSearch(A,bench,legacy.id(),List.of(m.privateView(A).deck().getFirst().instanceId())),"旧检索入口绕过共享限次");
        var base=EnergyTriggeredAbilityTest.ability("trigger","OWN_FIELD",true,"NONE","{\"type\":\"draw\",\"amount\":1}");
        var limited=new AbilityDefinition(base.id(),base.nameZh(),base.effect(),base.effectSupport(),null,base.kind(),base.effectProgram(),base.eventPolicy(),new SharedUsageLimit("shared-trigger",SharedUsageLimit.Period.TURN));
        UUID actor=EnergyTriggeredAbilityTest.A;var triggered=EnergyTriggeredAbilityTest.fixture(List.of(limited),List.of(),null);
        int hand=triggered.privateView(actor).hand().size();var energy=triggered.privateView(actor).hand().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();
        triggered.attachEnergy(actor,energy.instanceId(),triggered.activePokemonView(actor).pokemonId());var order=triggered.triggerOrderPrompt();
        triggered.chooseTriggeredEffect(actor,order.id(),order.entries().getFirst().id());var optional=triggered.triggerOrderPrompt();triggered.decideTriggeredEffect(actor,optional.id(),false);
        optional=triggered.triggerOrderPrompt();check(optional!=null&&!triggered.sharedUsageExhausted(actor,limited.usageLimit()),"放弃可选特性消耗共享资格");
        triggered.decideTriggeredEffect(actor,optional.id(),true);
        check(triggered.privateView(actor).hand().size()==hand&&triggered.triggerOrderPrompt()==null,"同组同时触发执行了多次");
        check(triggered.sharedUsageExhausted(actor,limited.usageLimit()),"已接受触发未记录共享限次");
    }
    static void malformedAndLedger(){
        for(String json:List.of("{\"group\":\"vstar-power\",\"period\":\"TURN\"}","{\"group\":\"a\",\"period\":\"LIFE\"}","{\"group\":\"a\",\"period\":\"GAME\",\"reset\":true}","{\"group\":1,\"period\":\"GAME\"}"))
            ResumableAbilityTest.rejects(()->SharedUsageLimit.parse(JsonParser.parseString(json).getAsJsonObject()));
        ResumableAbilityTest.rejects(()->ability("bad",SharedUsageLimit.GX,"GAME","{\"type\":\"draw\",\"amount\":1}"));
        ResumableAbilityTest.rejects(()->ResumableAbilityTest.fixture(ability("a","same","TURN","{\"type\":\"draw\",\"amount\":1}"),ability("b","same","GAME","{\"type\":\"draw\",\"amount\":1}")));
        var ledger=new SharedUsageLedger();var limit=new SharedUsageLimit("same",SharedUsageLimit.Period.TURN);
        check(!ledger.exhausted(A,limit,4),"空资格账本不可用");ledger.consume(A,limit,4);ResumableAbilityTest.rejects(()->ledger.consume(A,limit,4));
        ResumableAbilityTest.rejects(()->ledger.exhausted(A,limit,3));ResumableAbilityTest.rejects(()->ledger.consume(A,new SharedUsageLimit("same",SharedUsageLimit.Period.GAME),5));
        check(!ledger.exhausted(B,limit,4)&&!ledger.exhausted(A,limit,5),"资格错误跨方或不随回合更新");
    }
    static void evolutionKeepsSharedUsage(){
        var power=ability("power",SharedUsageLimit.VSTAR,"GAME","{\"type\":\"draw\",\"amount\":1}");
        var defs=List.of(new CardDefinition("basic","基础夹具",CardCategory.POKEMON,TrainerCategory.NONE,"H",Set.of("international"),true,false,60,Map.of(),EffectSupport.SUPPORTED,null),new CardDefinition("evo","进化夹具",CardCategory.POKEMON,TrainerCategory.NONE,"H",Set.of("international"),false,false,60,Map.of(),EffectSupport.SUPPORTED,null));
        var combat=new CombatCatalog(Map.of("basic",new PokemonProfile(200,0,null,null,0,null,0,1,List.of(),List.of(power)),"evo",new PokemonProfile(200,0,"basic",null,0,null,0,1,List.of(),List.of(power))),Map.of());
        for(int seed=0;seed<100;seed++) {
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var deck=new ArrayList<CardInstance>();for(int i=0;i<60;i++)deck.add(new CardInstance(UUID.randomUUID(),owner,i<40?"basic":"evo"));decks.add(deck);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals("evo"))||m.privateView(A).hand().stream().filter(c->c.definitionId().equals("basic")).count()<2||m.privateView(B).hand().stream().filter(c->c.definitionId().equals("basic")).count()<2)continue;
            OpeningTestSupport.complete(m,A,B,1);UUID source=m.activePokemonView(A).pokemonId();m.useAbility(A,source,power.id());
            var evo=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("evo")).findFirst().orElseThrow();m.evolveByEffect(A,source,evo.instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
            check(!m.canUseAbility(A,source,power.id())&&marker(m.clientView(A),A).vstarUsed(),"进化重置整局资格");m.devolvePokemonToHandByEffect(A,A,source);
            check(!m.canUseAbility(A,source,power.id()),"退化重置整局资格");return;
        }
        throw new AssertionError("缺少进化测试起手");
    }
    static MatchClientView.Player marker(MatchClientView view,UUID player){return view.players().stream().filter(p->p.id().equals(player)).findFirst().orElseThrow();}
    static void zeroDamageAndInvalidCost(){
        var guard=ResumableAbilityTest.ability("guard",EffectOriginProtectionTest.shield("ATTACK","DAMAGE_ONLY","SELF","SOURCE_NEXT_TURN_START"));
        var gx=attack("gx",SharedUsageLimit.GX);var m=attackFixture(5,List.of(guard),List.of(gx));
        m.useAbility(A,m.activePokemonView(A).pokemonId(),guard.id());m.endTurn(A);m.attack(B,gx.id());
        check(m.lastAttack().finalDamage()==0&&marker(m.clientView(A),B).gxUsed(),"免伤错误退还已经使用的GX资格");
        var energy=BundledCombatData.attack(JsonParser.parseString("{\"id\":\"cost\",\"nameZh\":\"能量不足夹具\",\"energyCost\":{\"GRASS\":1},\"baseDamage\":10,\"effectSupport\":\"SUPPORTED\",\"usageLimit\":{\"group\":\"gx-attack\",\"period\":\"GAME\"}}").getAsJsonObject(),EffectRegistry.standard());
        var unpaid=attackFixture(6,List.of(),List.of(energy));var before=unpaid.clientView(A);var random=unpaid.randomTranscript();
        ResumableAbilityTest.rejects(()->unpaid.attack(A,energy.id()));
        check(before.equals(unpaid.clientView(A))&&random.equals(unpaid.randomTranscript()),"能量不足仍消耗资格或改变状态");
        var unsupported=gx.withEffectProgram(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_ATTACK,List.of(),List.of(new EffectDefinition("draw",EffectTarget.SELF,Map.of("amount","1"),List.of(),List.of()))))));
        var rejected=attackFixture(6,List.of(),List.of(unsupported));var initial=rejected.clientView(A);
        ResumableAbilityTest.rejects(()->rejected.attack(A,unsupported.id()));check(initial.equals(rejected.clientView(A)),"未支持攻击提前消耗共享资格");
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
