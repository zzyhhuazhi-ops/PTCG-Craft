import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Active abilities must use the same authoritative continuation as Trainer programs. */
public final class ResumableAbilityTest {
    static final UUID A = new UUID(0,7571), B = new UUID(0,7572), WATCH = new UUID(0,7573);
    public static void main(String[] args) {
        privateSearchAndUsage(); choiceAndSource(); allocationCheckpoint(); revealAndConcede(); invalidIsAtomic();
        selectedTargetAndConditions(); attachmentsAndDeferredTriggers(); coinsRemainLatched();
        System.out.println("ResumableAbilityTest passed: active abilities share selection, choice, allocation, privacy and rate limits");
    }
    static void privateSearchAndUsage() {
        var ability = ability("search", """
            {"type":"draw","amount":1},
            {"type":"search_deck","target":"DECK","amount":2,"minimum":2},
            {"type":"discard","target":"HAND","amount":1,"minimum":1},
            {"type":"draw","amount":1}
            """);
        var m=fixture(ability); var s=MatchSession.multiplayer(m,A,B); s.addSpectator(WATCH);
        UUID source=m.activePokemonView(A).pokemonId(); int before=m.privateView(A).hand().size();
        var opening=s.view(A,A);
        var start=opening.options().stream().filter(o->o.labelZh().startsWith("使用特性") && o.cardKeys().contains("field:"+source)).findFirst().orElseThrow();
        s.submit(A,A,opening.revision(),start.index());
        check(m.hasPendingCardSelection() && m.privateView(A).hand().size()==before+1,"特性前缀没有暂停在检索");
        check(m.abilityOptions(A).stream().anyMatch(o->o.pokemonId().equals(source)&&o.usedThisTurn()),"等待期间未保留特性使用次数");
        var first=m.effectCardPrompt(A); var random=m.randomTranscript();
        check(m.clientView(A).resolvingTrainer()==null&&m.clientView(A).resolvingAbility().pokemonId().equals(source)
            &&m.clientView(B).resolvingCardEffect(),"特性公共提示丢失或冒充训练家");
        rejects(()->m.useAbility(A,source,ability.id())); rejects(()->m.endTurn(A));
        rejects(()->s.submit(A,A,opening.revision(),start.index()));
        for(UUID recipient:List.of(B,WATCH)) {
            var view=s.view(recipient,recipient.equals(B)?B:null);
            check(view.game().effectSelection()==null,"他人收到了特性私有选牌");
            String json=new Gson().toJson(view);
            for(var card:first.candidates()) check(!json.contains(card.instanceId().toString()),"包泄露牌库身份");
            rejects(()->m.submitEffectCards(recipient,first.resolutionId(),first.step(),List.of(first.candidates().getFirst().instanceId())));
        }
        for(int i=0;i<3;i++) check(s.view(A,A).game().effectSelection().equals(first),"刷新更换了选择");
        check(random.equals(m.randomTranscript()),"刷新重掷或重洗牌");
        var picked=first.candidates().subList(0,2).stream().map(CardInstance::instanceId).toList();
        m.submitEffectCards(A,first.resolutionId(),first.step(),picked);
        var second=m.effectCardPrompt(A);
        check(second.candidates().size()==before+3,"特性错误排除了原手牌或未加入检索牌");
        rejects(()->m.submitEffectCards(A,first.resolutionId(),first.step(),picked));
        m.submitEffectCards(A,second.resolutionId(),second.step(),List.of(picked.getFirst()));
        check(!m.hasPendingEffectChoice()&&m.privateView(A).hand().size()==before+3,"特性整段结算错误");
        check(m.privateView(A).discard().size()==1&&m.activePokemonView(A).pokemonId().equals(source),"将特性来源误弃置");
        rejects(()->m.useAbility(A,source,ability.id()));
        m.endTurn(A); m.endTurn(B); check(m.canUseAbility(A,source,ability.id()),"下回合特性未恢复");
    }
    static void choiceAndSource() {
        var ability=ability("self", """
            {"type":"choice","effects":[{"type":"heal","target":"SELF","amount":10},{"type":"draw","amount":1}]},
            {"type":"draw","amount":1}
            """);
        var m=fixture(ability); UUID source=m.benchPokemonView(A).getFirst().pokemonId();
        m.placeDamageCountersByEffect(A,A,source,2); int hand=m.privateView(A).hand().size();
        m.useAbility(A,source,ability.id()); var choice=m.effectChoicePrompt(A);
        rejects(()->m.chooseEffectOption(B,choice.resolutionId(),choice.step(),0));
        m.chooseEffectOption(A,choice.resolutionId(),choice.step(),0);
        check(m.benchPokemonView(A).getFirst().damage()==10,"SELF没有绑定发动特性的备战宝可梦");
        check(m.privateView(A).hand().size()==hand+1,"选择分支执行错误");
        check(m.canUseAbility(A,m.activePokemonView(A).pokemonId(),ability.id()),"不同来源错误共享次数");
    }
    static void allocationCheckpoint() {
        var ability=ability("allocate", """
            {"type":"damage_counter","amount":12,"target":"OPPONENT_ACTIVE"},
            {"type":"damage_counter","amount":2,"distribution":"EXACT","target":"SELECT_OPPONENT_POKEMON","target_scope":"OPPONENT_FIELD","min_targets":1,"max_targets":2},
            {"type":"draw","amount":1}
            """);
        var m=fixture(ability); UUID source=m.activePokemonView(A).pokemonId();
        int hand=m.privateView(A).hand().size(); UUID bench=m.benchPokemonView(B).getFirst().pokemonId();
        m.useAbility(A,source,ability.id()); var prompt=m.damageAllocationPrompt(A);
        check(m.phase()==MatchPhase.TURN&&m.activePokemonView(B).damage()==120,"特性中途提前结算击倒");
        m.adjustDamageAllocation(A,prompt.resolutionId(),prompt.step(),bench,1);
        m.adjustDamageAllocation(A,prompt.resolutionId(),prompt.step(),bench,1);
        check(m.benchPokemonView(B).getFirst().damage()==0,"暂选提前改变伤害");
        m.confirmDamageAllocation(A,prompt.resolutionId(),prompt.step());
        check(m.privateView(A).hand().size()==hand+1&&m.phase()==MatchPhase.PRIZE_SELECTION,"未在整段完成后处理击倒");
        check(m.benchPokemonView(B).getFirst().damage()==20,"分配未执行");
    }
    static void revealAndConcede() {
        var ability=ability("reveal", """
            {"type":"search_deck","target":"DECK","amount":1,"minimum":1,"reveal":true},
            {"type":"draw","amount":1}
            """);
        var m=fixture(ability); UUID source=m.activePokemonView(A).pokemonId();
        m.useAbility(A,source,ability.id()); var prompt=m.effectCardPrompt(A);
        m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),List.of(prompt.candidates().getFirst().instanceId()));
        check(m.effectCardReveal()!=null,"特性检索没有公开展示");
        var s=MatchSession.practice(m,WATCH,A,B); var response=s.view(WATCH,B);
        var ack=response.options().stream().filter(o->o.labelZh().contains("确认")&&!o.labelZh().contains("认输")).findFirst().orElseThrow();
        s.submit(WATCH,B,response.revision(),ack.index());
        check(!m.hasPendingEffectChoice()&&m.effectCardReveal()==null,"练习切席无法确认特性展示");
        m.endTurn(A);m.endTurn(B);m.useAbility(A,source,ability.id());
        int discard=m.privateView(A).discard().size();m.concede(A);
        check(!m.hasPendingEffectChoice()&&m.privateView(A).discard().size()==discard,"认输把来源宝可梦复制到弃牌区");
    }
    static void invalidIsAtomic() {
        var bad=ability("bad", """
            {"type":"draw","amount":1},
            {"type":"search_deck","target":"DECK","amount":1,"minimum":1},
            {"type":"heal","target":"SELF","amount":11}
            """);
        var m=fixture(bad);var before=m.privateView(A);var random=m.randomTranscript();
        rejects(()->m.useAbility(A,m.activePokemonView(A).pokemonId(),bad.id()));
        check(before.equals(m.privateView(A))&&random.equals(m.randomTranscript())&&!m.hasPendingEffectChoice(),"错误程序部分执行");
        check(m.abilityOptions(A).stream().noneMatch(ServerMatch.AbilityOption::usedThisTurn),"错误程序消耗特性次数");
        check(ResumableProgramRules.unsupportedReason(bad.effectProgram(),true).isPresent(),"静态检查误放行错误特性");
        check(MatchSession.multiplayer(m,A,B).view(A,A).options().stream().anyMatch(o->o.labelZh().startsWith("特性组合尚未支持")),"GUI没有说明未支持原因");
    }
    static void selectedTargetAndConditions() {
        var ability=ability("selected", """
            {"type":"heal","target":"SELECT_OWN_POKEMON","amount":10},
            {"type":"search_deck","target":"DECK","amount":1,"minimum":1}
            """);
        var m=fixture(ability); UUID source=m.activePokemonView(A).pokemonId(), target=m.benchPokemonView(A).getFirst().pokemonId();
        m.placeDamageCountersByEffect(A,A,target,1);
        rejects(()->m.useAbility(A,source,ability.id()));
        rejects(()->m.useAbility(A,source,ability.id(),m.activePokemonView(B).pokemonId()));
        var session=MatchSession.multiplayer(m,A,B); var view=session.view(A,A);
        var option=view.options().stream().filter(o->o.cardKeys().contains("field:"+source)&&o.cardKeys().contains("field:"+target)).findFirst().orElseThrow();
        session.submit(A,A,view.revision(),option.index());
        check(m.benchPokemonView(A).getFirst().damage()==0&&m.hasPendingCardSelection(),"公开目标特性未接入续接");
        var ability2=ability("hand-count", """
            {"type":"conditional","conditions":[{"type":"HAND_SIZE","target":"SELF","comparison":"=","value":6}],
             "effects":[{"type":"search_deck","target":"DECK","amount":1,"minimum":1}]}
            """);
        var another=fixture(ability2);
        // Opening 7, active + bench remove 2, first-turn draw adds 1 = 6; no Trainer projection subtraction.
        check(another.privateView(A).hand().size()==6,"手牌条件夹具错误");
        another.useAbility(A,another.activePokemonView(A).pokemonId(),ability2.id());
        check(another.hasPendingCardSelection(),"特性误套用训练家的手牌减一投影");
    }
    static void attachmentsAndDeferredTriggers() {
        UUID a=EnergyTriggeredAbilityTest.A;
        var active=ability("attach", EnergyTriggeredAbilityTest.ATTACH + "," + """
            {"type":"choice","effects":[{"type":"draw","amount":1},{"type":"draw","amount":2}]}
            """);
        var triggered=EnergyTriggeredAbilityTest.ability("after", "SELF",false,"NONE","{\"type\":\"draw\",\"amount\":2}");
        var m=EnergyTriggeredAbilityTest.fixture(List.of(active,triggered),List.of(),null);
        int before=m.privateView(a).hand().size(); UUID source=m.activePokemonView(a).pokemonId();
        m.useAbility(a,source,active.id()); var pick=m.effectCardPrompt(a);
        m.submitEffectCards(a,pick.resolutionId(),pick.step(),List.of(pick.candidates().getFirst().instanceId()));
        var target=m.effectAttachmentPrompt(a);m.chooseEffectAttachmentTarget(a,target.resolutionId(),target.step(),source);
        check(m.privateView(a).hand().size()==before-1&&m.triggerOrderPrompt()==null&&!m.energyAttached(a),"特性附能插入了触发或占用手动次数");
        var choice=m.effectChoicePrompt(a); m.chooseEffectOption(a,choice.resolutionId(),choice.step(),0);
        check(m.privateView(a).hand().size()==before+2&&m.activePokemonView(a).attachedEnergyIds().size()==1,"整段特性完成后未继续触发");
        rejects(()->m.useAbility(a,source,active.id()));
    }
    static void coinsRemainLatched() {
        var ability=ability("coin", """
            {"type":"coin_flip","flips":1,"effects":[
              {"type":"choice","effects":[
                {"type":"conditional","conditions":[{"type":"COIN_RESULT","value":"HEADS"}],"effects":[{"type":"draw","amount":1}]},
                {"type":"draw","amount":1}]}]}
            """);
        var m=fixture(ability); UUID source=m.activePokemonView(A).pokemonId();
        m.useAbility(A,source,ability.id()); var choice=m.effectChoicePrompt(A);var random=m.randomTranscript();
        for(int i=0;i<3;i++)m.clientView(A);
        rejects(()->m.chooseEffectOption(A,choice.resolutionId(),choice.step(),999));
        check(random.equals(m.randomTranscript()),"错误选择或刷新重掷硬币");
        m.chooseEffectOption(A,choice.resolutionId(),choice.step(),0);
        check(random.equals(m.randomTranscript())&&!m.canUseAbility(A,source,ability.id()),"分支无效果重置限次或重复掷币");
    }
    static AbilityDefinition ability(String id,String effects) {
        return BundledCombatData.ability(JsonParser.parseString("{\"id\":\""+id+"\",\"nameZh\":\"测试特性\",\"effectSupport\":\"SUPPORTED\",\"effects\":["+effects+"]}").getAsJsonObject(),EffectRegistry.standard());
    }
    static ServerMatch fixture(AbilityDefinition... abilities) {
        var card=new CardDefinition("basic","测试基础",CardCategory.POKEMON,TrainerCategory.NONE,"H",Set.of("international"),true,false,60,Map.of(),EffectSupport.SUPPORTED,null);
        var combat=new CombatCatalog(Map.of("basic",new PokemonProfile(120,0,null,null,0,null,0,1,List.of(),List.of(abilities))),Map.of());
        var m=new ServerMatch(A,B,deck(A),deck(B),BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(List.of(card)),combat,new ServerRandom(92));
        OpeningTestSupport.complete(m,A,B,1);if(m.currentPlayer().equals(B))m.endTurn(B);return m;
    }
    static List<CardInstance> deck(UUID owner) {var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(UUID.randomUUID(),owner,"basic"));return cards;}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void rejects(Runnable action){try{action.run();}catch(IllegalArgumentException|IllegalStateException expected){return;}throw new AssertionError("非法特性动作被接受");}
}
