import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** One declared attack, across before-damage and after-damage selections; no real-card bindings. */
public final class BeforeDamageAttackTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B,WATCH=new UUID(0,8310);
    static void check(boolean b,String reason){if(!b)throw new AssertionError(reason);}
    static void reject(Runnable r){boolean denied=false;try{r.run();}catch(IllegalArgumentException|IllegalStateException expected){denied=true;}check(denied,"非法操作未拒绝");}
    static TriggeredEffects binding(EffectTrigger trigger,String json) {
        var effects=new ArrayList<EffectDefinition>();for(var e:JsonParser.parseString("["+json+"]").getAsJsonArray())effects.add(EffectRegistry.standard().parse(e.getAsJsonObject()));
        return new TriggeredEffects(trigger,List.of(),effects);
    }
    static AttackDefinition attack(String before,String damage,String after) {
        var triggers=new ArrayList<TriggeredEffects>();
        if(!before.isBlank())triggers.add(binding(EffectTrigger.BEFORE_ATTACK,before));
        if(!damage.isBlank())triggers.add(binding(EffectTrigger.ON_ATTACK,damage));
        if(!after.isBlank())triggers.add(binding(EffectTrigger.AFTER_ATTACK,after));
        return DynamicPassiveModifierTest.attack(40,false,false).withEffectProgram(new EffectProgram(triggers));
    }
    static ServerMatch fixture(AttackDefinition attack,AbilityDefinition... abilities) {
        return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.basic(EnergyType.COLORLESS),attack,List.of(abilities));
    }
    static void selectFirst(ServerMatch m,UUID owner){var p=m.effectCardPrompt(owner);m.submitEffectCards(owner,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId()));}
    public static void main(String[] args) {
        stagedSelection(); damageUsesNewFacts(); knockoutWaitsForText(); malformedAtomic(); triggeredAfterWholeAttack();
        declaredPreDamageTarget(); afterDamagePublicSelection(); afterDamageSequentialSelections(); genericSelfDamageAndRecoil(); genericAttackDamageReduction(); confusionAndConcede(); toolCleanupBeforeDamage(); energyGateAndSharedUsage(); jsonAndUnsupportedBoundaries(); protectionAndCoinPause();
        System.out.println("BeforeDamageAttackTest passed: declared attack spans damage phases, choices and final checkpoint");
    }
    static void stagedSelection() {
        var a=attack("{\"type\":\"draw\",\"amount\":1},{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":1}","","{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":1},{\"type\":\"draw\",\"amount\":1}");
        var m=fixture(a);var session=MatchSession.multiplayer(m,A,B);session.addSpectator(WATCH);int hand=m.privateView(A).hand().size(),turn=m.turnNumber();
        m.attack(A,a.id());check(m.activePokemonView(B).damage()==0&&m.privateView(A).hand().size()==hand+1,"伤害提前于伤害前选择");
        check(m.clientView(B).resolvingAttack().phase()==EffectTrigger.BEFORE_ATTACK,"客户端未标注伤害前阶段");
        var before=m.effectCardPrompt(A);var rng=m.randomTranscript();reject(()->m.endTurn(A));reject(()->m.attack(A,a.id()));
        for(UUID viewer:List.of(B,WATCH)) {
            String json=new Gson().toJson(session.view(viewer,viewer.equals(B)?B:null));
            for(var c:before.candidates())check(!json.contains(c.instanceId().toString()),"伤害前选牌泄露牌库");
            reject(()->m.submitEffectCards(viewer,before.resolutionId(),before.step(),List.of(before.candidates().getFirst().instanceId())));
        }
        session.view(A,A);check(rng.equals(m.randomTranscript()),"伤害前刷新消耗随机");
        selectFirst(m,A);check(m.activePokemonView(B).damage()==80&&m.currentPlayer().equals(A)&&m.turnNumber()==turn,"没有在两次选牌之间恰好造成一次伤害");
        check(m.clientView(B).resolvingAttack().phase()==EffectTrigger.AFTER_ATTACK,"客户端未切换伤害后阶段");
        check(!m.effectCardPrompt(A).resolutionId().equals(before.resolutionId()),"跨阶段复用选牌事务");
        reject(()->m.submitEffectCards(A,before.resolutionId(),before.step(),List.of(before.candidates().getFirst().instanceId())));
        selectFirst(m,A);check(m.activePokemonView(B).damage()==80&&m.currentPlayer().equals(B)&&m.turnNumber()==turn+1&&m.privateView(A).hand().size()==hand+4,"伤害、前缀或回合被重复执行");
        check(m.replayTranscript().stream().filter(e->e.operation().equals("宣告攻击："+a.id())).count()==1
            &&m.replayTranscript().stream().filter(e->e.operation().equals("使用攻击")).count()==1,"回放没有恰好一次宣告及伤害动作");
    }
    static void damageUsesNewFacts() {
        var a=attack("{\"type\":\"damage_counter\",\"target\":\"SELF\",\"amount\":2}","{\"type\":\"damage_per_damage_counter\",\"target\":\"SELF\",\"amount\":10}","");
        var m=fixture(a);m.attack(A,a.id());check(m.activePokemonView(A).damage()==20&&m.activePokemonView(B).damage()==120,"伤害计算没有读到伤害前效果新状态，或未应用弱点");
        var prizes=attack("","{\"type\":\"damage_per_prize\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":10}","");
        var count=fixture(prizes);count.attack(A,prizes.id());check(count.activePokemonView(B).damage()==200,"每张对方剩余奖赏卡伤害未接入攻击弱点和统一伤害层");
        var taken=DynamicPassiveModifierTest.attack(100,false,false).withEffectProgram(
            attack("","{\"type\":\"damage_per_prize\",\"target\":\"SELF\",\"amount\":10,\"prize_count\":\"TAKEN\"}","").effectProgram());
        var afterPrize=fixture(taken);afterPrize.placeDamageCountersByEffect(A,B,afterPrize.activePokemonView(B).pokemonId(),20);
        afterPrize.attack(A,taken.id());afterPrize.takePrizeAt(A,0);
        afterPrize.chooseReplacement(B,afterPrize.benchPokemonView(B).getFirst().pokemonId());afterPrize.endTurn(B);afterPrize.attack(A,taken.id());
        check(afterPrize.activePokemonView(B).damage()==220,"每张已拿取奖赏卡伤害没有从服务器开局奖赏快照计算");
        reject(()->AttackProgramRules.validate(attack("","{\"type\":\"damage_per_prize\",\"target\":\"HAND\",\"amount\":10}","")));
        reject(()->AttackProgramRules.validate(attack("","{\"type\":\"damage_per_prize\",\"target\":\"SELF\",\"amount\":10,\"prize_count\":\"UNKNOWN\"}","")));
    }
    /** A legacy SELECT target is declared separately from any attack damage target and remains server-validated. */
    static void declaredPreDamageTarget() {
        var a=attack("{\"type\":\"heal\",\"target\":\"SELECT_OWN_POKEMON\",\"amount\":10}","","");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"伤害前公开预选目标仍被规则门禁拒绝");
        var direct=fixture(a);UUID bench=direct.benchPokemonView(A).getFirst().pokemonId();
        direct.placeDamageCountersByEffect(A,A,bench,2);
        var view=direct.clientView(A);var rng=direct.randomTranscript();
        reject(()->direct.attack(A,a.id()));
        reject(()->direct.attackWithSelectedEffectTargetAndEnergyDiscards(A,a.id(),direct.activePokemonView(B).pokemonId(),List.of(),List.of()));
        check(view.equals(direct.clientView(A))&&rng.equals(direct.randomTranscript()),"非法伤害前预选目标在拒绝前改变状态或随机数");
        direct.attackWithSelectedEffectTargetAndEnergyDiscards(A,a.id(),bench,List.of(),List.of());
        check(direct.benchPokemonView(A).getFirst().damage()==10&&direct.activePokemonView(B).damage()==80,
            "伤害前预选目标没有独立结算回复或攻击伤害");

        var table=fixture(a);UUID tableBench=table.benchPokemonView(A).getFirst().pokemonId();
        table.placeDamageCountersByEffect(A,A,tableBench,2);
        var session=MatchSession.multiplayer(table,A,B);var snapshot=session.view(A,A);
        var option=snapshot.options().stream().filter(value->value.labelZh().contains("伤害前选择")
            &&value.cardKeys().contains("field:"+tableBench)).findFirst().orElseThrow();
        check(session.view(B,B).options().stream().noneMatch(value->value.labelZh().contains("伤害前选择")),"对手获得了己方攻击的预选操作");
        session.submit(A,A,snapshot.revision(),option.index());
        check(table.benchPokemonView(A).getFirst().damage()==10&&table.activePokemonView(B).damage()==80,
            "卡桌没有把服务器签发的伤害前预选目标提交给攻击结算");
    }
    /** A legacy SELECT target after damage is a fresh server-issued prompt, never a declaration parameter. */
    static void afterDamagePublicSelection() {
        var a=attack("","","{\"type\":\"heal\",\"target\":\"SELECT_OWN_POKEMON\",\"amount\":10}");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"伤害后公开目标选择仍被规则门禁拒绝");
        var m=fixture(a);UUID bench=m.benchPokemonView(A).getFirst().pokemonId();
        m.placeDamageCountersByEffect(A,A,bench,2);m.attack(A,a.id());
        check(m.activePokemonView(B).damage()==80&&m.hasPendingPokemonSelection()&&m.currentPlayer().equals(A),
            "攻击伤害后没有暂停到攻击方的服务器目标选择");
        var prompt=m.effectPokemonPrompt(A);var rng=m.randomTranscript();
        reject(()->m.effectPokemonPrompt(B));
        reject(()->m.chooseEffectPokemon(A,prompt.resolutionId(),prompt.step(),m.activePokemonView(B).pokemonId()));
        check(rng.equals(m.randomTranscript())&&m.benchPokemonView(A).getFirst().damage()==20,
            "伤害后非法目标选择改变随机数或提前执行效果");
        m.chooseEffectPokemon(A,prompt.resolutionId(),prompt.step(),bench);
        check(m.benchPokemonView(A).getFirst().damage()==10&&m.activePokemonView(B).damage()==80&&m.currentPlayer().equals(B),
            "伤害后选择没有恰好结算一次并结束攻击回合");
    }
    /** Each legacy post-damage SELECT step issues a fresh prompt after the preceding step commits. */
    static void afterDamageSequentialSelections() {
        var a=attack("","","""
            {"type":"heal","target":"SELECT_OWN_POKEMON","amount":10},
            {"type":"damage_counter","target":"SELECT_OPPONENT_POKEMON","amount":1}
            """);
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"多个伤害后公开目标选择仍被静态折叠拒绝");
        var m=fixture(a);UUID own=m.benchPokemonView(A).getFirst().pokemonId(), foe=m.benchPokemonView(B).getFirst().pokemonId();
        m.placeDamageCountersByEffect(A,A,own,2);m.attack(A,a.id());
        var first=m.effectPokemonPrompt(A);check(first.targets().stream().anyMatch(value->value.pokemonId().equals(own)),"首个伤害后选择未发行己方目标");
        m.chooseEffectPokemon(A,first.resolutionId(),first.step(),own);
        var second=m.effectPokemonPrompt(A);check(second.step()>first.step()&&second.targets().stream().anyMatch(value->value.pokemonId().equals(foe)),
            "第二个伤害后选择没有在首步提交后重新发行对方候选");
        m.chooseEffectPokemon(A,second.resolutionId(),second.step(),foe);
        check(m.benchPokemonView(A).getFirst().damage()==10&&m.benchPokemonView(B).getFirst().damage()==10&&m.currentPlayer().equals(B),
            "连续伤害后选择没有按顺序各自结算一次");
        var invalid=attack("","","{\"type\":\"conditional\",\"conditions\":[{\"type\":\"HP_BELOW\",\"target\":\"SELECT_OWN_POKEMON\",\"value\":100}],\"effects\":[{\"type\":\"draw\",\"amount\":1}]}");
        check(AttackProgramRules.unsupportedReason(invalid).isPresent(),"未选择的伤害后旧式目标条件被静默放行");
    }
    /** self_damage/recoil are reusable AFTER_ATTACK components, not legacy per-card fields. */
    static void genericSelfDamageAndRecoil() {
        var a=attack("","","""
            {"type":"self_damage","target":"SELF","amount":30},
            {"type":"recoil","target":"SELF","amount":20},
            {"type":"heal","target":"SELF","amount":10}
            """);
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"数据驱动自伤／反冲被攻击规则门禁拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=fixture(a);m.attack(A,a.id());
        check(m.activePokemonView(B).damage()==80&&m.activePokemonView(A).damage()==90&&m.lastAttack().selfDamage()==100,
            "自伤、反冲、后续回复或攻击审计没有按统一攻击检查点结算");
        check(m.currentPlayer().equals(B),"数据驱动自伤未在完整攻击文本后结束回合");

        var lethal=attack("","","{\"type\":\"recoil\",\"target\":\"SELF\",\"amount\":300}");
        var knockout=fixture(lethal);knockout.attack(A,lethal.id());
        check(knockout.lastAttack().selfDamage()==600&&knockout.lastAttack().knockedOut()&&knockout.phase()==MatchPhase.PRIZE_SELECTION,
            "数据驱动反冲没有进入统一击倒／奖赏检查点");

        var guard=ResumableAbilityTest.ability("guard",EffectOriginProtectionTest.shield("ATTACK","DAMAGE_ONLY","SELF","SOURCE_TURN_END")
            .replace("\"from\":\"OPPONENT\"","\"from\":\"OWN\""));
        var protectedMatch=fixture(a,guard);UUID active=protectedMatch.activePokemonView(A).pokemonId();
        protectedMatch.useAbility(A,active,guard.id());protectedMatch.attack(A,a.id());
        check(protectedMatch.activePokemonView(A).damage()==0&&protectedMatch.lastAttack().selfDamage()==0,
            "攻击来源伤害防护没有阻止数据驱动自伤／反冲");

        for(var invalid:List.of(
            attack("{\"type\":\"self_damage\",\"target\":\"SELF\",\"amount\":10}","",""),
            attack("","","{\"type\":\"recoil\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":10}"),
            attack("","","{\"type\":\"self_damage\",\"target\":\"SELF\",\"amount\":15}")))
            check(AttackProgramRules.unsupportedReason(invalid).isPresent(),"非法自伤／反冲阶段、目标或数值被静默放行");
    }
    /** The common post-attack next-opponent-turn reduction uses the same field lifetime as legacy data. */
    static void genericAttackDamageReduction() {
        var a=attack("","","{\"type\":\"reduce_damage\",\"target\":\"SELF\",\"amount\":30}");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"数据驱动攻击减伤被攻击规则门禁拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=fixture(a);UUID active=m.activePokemonView(A).pokemonId();m.attack(A,a.id());
        check(m.activePokemonView(A).continuousEffectZh().contains("减少 30"),"攻击后减伤未由服务器保存为公开持续效果");
        m.attack(B,a.id());
        check(m.lastAttack().finalDamage()==50&&m.activePokemonView(A).damage()==50,
            "攻击减伤没有在弱点／抗性后的实际攻击伤害上正确应用");
        check(!m.activePokemonView(A).continuousEffectZh().contains("减少 30"),"攻击减伤没有在受保护方下回合开始时到期");

        var guard=ResumableAbilityTest.ability("guard",EffectOriginProtectionTest.shield("ATTACK","EFFECTS_ONLY","SELF","SOURCE_TURN_END")
            .replace("\"from\":\"OPPONENT\"","\"from\":\"OWN\""));
        var protectedMatch=fixture(a,guard);protectedMatch.useAbility(A,protectedMatch.activePokemonView(A).pokemonId(),guard.id());
        protectedMatch.attack(A,a.id());
        check(!protectedMatch.activePokemonView(A).continuousEffectZh().contains("减少 30"),"攻击效果防护没有阻止数据驱动减伤的施加");

        for(var invalid:List.of(
            attack("{\"type\":\"reduce_damage\",\"target\":\"SELF\",\"amount\":30}","",""),
            attack("","","{\"type\":\"reduce_damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}"),
            attack("","","{\"type\":\"reduce_damage\",\"target\":\"SELF\",\"amount\":25}")))
            check(AttackProgramRules.unsupportedReason(invalid).isPresent(),"非法攻击减伤阶段、目标或数值被静默放行");
    }
    static void knockoutWaitsForText() {
        var a=attack("{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":31},{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":1}","","{\"type\":\"heal\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":100}");
        var m=fixture(a);m.attack(A,a.id());check(m.phase()==MatchPhase.TURN&&m.activePokemonView(B).damage()==310,"伤害前效果提前检查击倒");
        selectFirst(m,A);check(m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(B)&&m.activePokemonView(B).damage()==290&&m.privateView(A).prizes().size()==6,"攻击文本结束前提前移走目标或错误给奖");
    }
    static void malformedAtomic() {
        for(var a:List.of(attack("{\"type\":\"draw\",\"amount\":1},{\"type\":\"heal\",\"target\":\"SELF\",\"amount\":11}","",""),
            attack("{\"type\":\"draw\",\"amount\":1}","","{\"type\":\"copy_attack\",\"target\":\"OPPONENT_ACTIVE\",\"cost_policy\":\"DECLARED_ATTACK\"}"))) {
            var m=fixture(a);var before=m.clientView(A);var rng=m.randomTranscript();reject(()->m.attack(A,a.id()));check(before.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"后阶段非法仍执行伤害前前缀");
        }
    }
    static void triggeredAfterWholeAttack() {
        var a=attack("{\"type\":\"attach_energy\",\"source\":\"HAND\",\"target\":\"SELECT_OWN_POKEMON\",\"target_scope\":\"OWN_FIELD\",\"amount\":1,\"minimum\":1,\"basic_energy_only\":true}","","{\"type\":\"choice\",\"effects\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"draw\",\"amount\":2}]}");
        var m=fixture(a,EnergyTriggeredAbilityTest.ability("listener","SELF",true,"NONE","{\"type\":\"draw\",\"amount\":2}"));int turn=m.turnNumber();m.attack(A,a.id());
        selectFirst(m,A);var attachment=m.effectAttachmentPrompt(A);m.chooseEffectAttachmentTarget(A,attachment.resolutionId(),attachment.step(),m.activePokemonView(A).pokemonId());
        check(m.triggerOrderPrompt()==null&&m.activePokemonView(B).damage()==80&&!m.energyAttached(A),"伤害前附能触发插队或消耗手动附能次数");
        var choice=m.effectChoicePrompt(A);m.chooseEffectOption(A,choice.resolutionId(),choice.step(),0);
        check(m.triggerOrderPrompt()!=null&&m.currentPlayer().equals(A),"完整攻击后没有等附能触发");m.decideTriggeredEffect(A,m.triggerOrderPrompt().id(),true);
        check(m.currentPlayer().equals(B)&&m.turnNumber()==turn+1,"附能触发后重复或丢失回合推进");
    }
    static void confusionAndConcede() {
        var a=attack("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":1}","","");boolean success=false,failure=false;
        UUID x=SharedUsageLimitTest.A,y=SharedUsageLimitTest.B;
        for(int seed=0;seed<30&&!(success&&failure);seed++) {
            var m=SharedUsageLimitTest.attackFixture(seed,List.of(),List.of(a));m.applySpecialConditionByEffect(x,x,SpecialCondition.CONFUSED);int hand=m.privateView(x).hand().size();m.attack(x,a.id());
            if(m.hasPendingCardSelection()){success=true;m.concede(x);check(!m.clientView(y).resolvingCardEffect()&&!m.hasPendingCardSelection(),"认输后伤害前续接仍存在");}
            else {failure=true;check(m.lastAttack().selfDamage()==30&&m.privateView(x).hand().size()==hand,"混乱失败仍执行伤害前效果");}
        }
        check(success&&failure,"没有覆盖混乱正反结果");
    }
    static void toolCleanupBeforeDamage() {
        var tool=DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"tool_capacity\",\"amount\":2,\"conditions\":[{\"type\":\"HP_ABOVE\",\"value\":290}]}");
        var a=attack("{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}","","");
        var m=DynamicPassiveModifierTest.fixture(tool,DynamicPassiveModifierTest.plainStadium(),EnergyProfile.basic(EnergyType.COLORLESS),a);
        UUID host=m.activePokemonView(B).pokemonId();
        for(int i=0;i<2;i++)m.attachToolByEffect(A,B,EffectSourceZone.HAND,DynamicPassiveModifierTest.hand(m,B,"tool").instanceId(),B,host);
        int turn=m.turnNumber();m.attack(A,a.id());var p=m.toolOverflowPrompt();
        check(p!=null&&p.owner().equals(B)&&m.activePokemonView(B).damage()==10,"工具清理前提前造成攻击伤害");
        reject(()->m.endTurn(A));m.discardOverflowTool(B,p.id(),p.tools().getFirst().instanceId());
        check(m.activePokemonView(B).damage()==90&&m.currentPlayer().equals(B)&&m.turnNumber()==turn+1,"工具清理后伤害步骤或回合丢失／重复");
    }
    static void energyGateAndSharedUsage() {
        var a=BundledCombatData.attack(JsonParser.parseString("""
            {"id":"cost","nameZh":"先检查费用","energyCost":{"GRASS":1},"baseDamage":40,"damageType":"GRASS","effectSupport":"SUPPORTED"}
            """).getAsJsonObject(),EffectRegistry.standard()).withEffectProgram(attack("{\"type\":\"attach_energy\",\"source\":\"HAND\",\"target\":\"OWN_ACTIVE\",\"target_scope\":\"OWN_ACTIVE\",\"amount\":1,\"minimum\":1,\"basic_energy_only\":true}","","").effectProgram());
        var m=fixture(a);var snapshot=m.clientView(A);reject(()->m.attack(A,a.id()));check(snapshot.equals(m.clientView(A)),"用伤害前附能弥补宣告时不足的攻击费用");
        m.attachEnergy(A,DynamicPassiveModifierTest.hand(m,A,"energy").instanceId(),m.activePokemonView(A).pokemonId());
        m.attack(A,a.id());selectFirst(m,A);var attachment=m.effectAttachmentPrompt(A);
        m.chooseEffectAttachmentTarget(A,attachment.resolutionId(),attachment.step(),m.activePokemonView(A).pokemonId());
        check(m.activePokemonView(A).attachedEnergyIds().size()==2&&m.activePokemonView(B).damage()==80,"手动附能后伤害前效果附能被错误禁止");
        var limited=SharedUsageLimitTest.attack("gx",SharedUsageLimit.GX).withEffectProgram(attack("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":1}","","").effectProgram());
        var power=fixture(limited);power.attack(A,limited.id());check(power.sharedUsageExhausted(A,limited.usageLimit()),"伤害前等待期间未保留力量资格");selectFirst(power,A);
        power.endTurn(B);reject(()->power.attack(A,limited.id()));
    }
    static void jsonAndUnsupportedBoundaries() {
        var a=BundledCombatData.attack(JsonParser.parseString("""
            {"id":"json","nameZh":"三阶段数据","energyCost":{},"baseDamage":40,"effectSupport":"SUPPORTED","triggers":[
              {"trigger":"BEFORE_ATTACK","effects":[{"type":"heal","target":"OPPONENT_ACTIVE","amount":10}]},
              {"trigger":"ON_ATTACK","effects":[{"type":"damage","target":"OPPONENT_ACTIVE","amount":10}]},
              {"trigger":"AFTER_ATTACK","effects":[{"type":"draw","amount":1}]}]}
            """).getAsJsonObject(),EffectRegistry.standard());
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"三阶段JSON仍被卡组检查器拒绝");
        for(var invalid:List.of(attack("{\"type\":\"return_to_hand\",\"target\":\"SELF\"}","",""),
            attack("{\"type\":\"knock_out\",\"target\":\"OPPONENT_ACTIVE\"}","","{\"type\":\"return_to_hand\",\"target\":\"OPPONENT_ACTIVE\"}")))
            check(AttackProgramRules.unsupportedReason(invalid).isPresent(),"未核验的伤害前离场／跨阶段直接击倒被放行");
    }
    static void protectionAndCoinPause() {
        var guard=ResumableAbilityTest.ability("guard",EffectOriginProtectionTest.shield("ATTACK","EFFECTS_ONLY","SELF","SOURCE_NEXT_TURN_START"));
        var a=attack("{\"type\":\"coin_flip\",\"flips\":1,\"effects\":[{\"type\":\"choice\",\"effects\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"draw\",\"amount\":2}]}]},{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":3}","","");
        var m=fixture(a,guard);m.endTurn(A);m.useAbility(B,m.activePokemonView(B).pokemonId(),guard.id());m.endTurn(B);
        m.attack(A,a.id());var rng=m.randomTranscript();var p=m.effectChoicePrompt(A);
        m.clientView(A);m.clientView(B);check(rng.equals(m.randomTranscript()),"伤害前暂停期间重掷硬币");
        m.chooseEffectOption(A,p.resolutionId(),p.step(),0);
        check(m.activePokemonView(B).damage()==80,"伤害前指示物遗失攻击来源，穿透效果防护");
        check(rng.equals(m.randomTranscript()),"恢复伤害前选择后重复硬币前缀");
    }
}
