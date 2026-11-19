import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/** Automatic effects share the same authoritative cursor, never a second client-controlled rules path. */
public final class ResumableTriggeredAbilityTest {
    static final String CHOICE="{\"type\":\"choice\",\"effects\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"draw\",\"amount\":2}]}";
    public static void main(String[] args) {
        for(String event:List.of("energy","damage","knockout","checkup")) choice(event);
        privateZones(); revealDuringCheckup(); energyAndNestedEvents(); allocationBeforeKnockout(); coinsAndRepeat(); pausedSourceIdentity();
        System.out.println("ResumableTriggeredAbilityTest passed: owner-bound automatic choices across turn/checkup and KO checkpoints");
    }
    static void privateZones() {
        UUID a=DamageTriggeredAbilityTest.A,b=DamageTriggeredAbilityTest.B,watch=UUID.randomUUID();
        String effects="""
            {"type":"draw","amount":1},
            {"type":"search_deck","target":"DECK","amount":2,"minimum":2},
            {"type":"discard","target":"HAND","amount":1,"minimum":1},
            {"type":"search_discard_pile","target":"DISCARD","destination":"HAND","amount":1,"minimum":1},
            {"type":"draw","amount":1}
            """;
        var m=DamageTriggeredAbilityTest.fixture(DamageTriggeredAbilityTest.attack(10,""),DamageTriggeredAbilityTest.ability("search",true,false,false,effects));
        int before=m.privateView(b).hand().size();m.attack(a,"hit");var session=MatchSession.multiplayer(m,a,b);session.addSpectator(watch);
        check(m.hasPendingCardSelection()&&m.pendingChoicePlayer().equals(b)&&m.turnNumber()==3,"受伤检索未在攻击完成前等待");
        var pick=m.effectCardPrompt(b);
        for(UUID recipient:List.of(a,watch)) {
            String body=new com.google.gson.Gson().toJson(session.view(recipient,recipient.equals(watch)?null:recipient));
            for(var card:pick.candidates())check(!body.contains(card.instanceId().toString()),"自动特性泄露私有检索身份");
            EventTargetBindingTest.reject(()->m.submitEffectCards(recipient,pick.resolutionId(),pick.step(),List.of(pick.candidates().getFirst().instanceId())));
        }
        check(session.view(b,b).options().stream().anyMatch(o->o.labelZh().startsWith("选择卡牌")),"非当前玩家缺少私有选牌操作");
        var selected=pick.candidates().subList(0,2).stream().map(dev.hua.ptcglive.rules.CardInstance::instanceId).toList();
        m.submitEffectCards(b,pick.resolutionId(),pick.step(),selected);var discard=m.effectCardPrompt(b);
        EventTargetBindingTest.reject(()->m.submitEffectCards(b,pick.resolutionId(),pick.step(),selected));
        m.submitEffectCards(b,discard.resolutionId(),discard.step(),List.of(selected.getFirst()));var recover=m.effectCardPrompt(b);
        m.submitEffectCards(b,recover.resolutionId(),recover.step(),List.of(selected.getFirst()));
        check(!m.hasPendingEffectChoice()&&m.privateView(b).hand().size()==before+5&&m.privateView(b).discard().isEmpty(),"检索弃置回收或回合抽牌计数错误");
        check(m.turnNumber()==4&&m.currentPlayer().equals(b),"私有选择结束没有恰好推进一次");
    }
    static void revealDuringCheckup() {
        UUID a=EnergyTriggeredAbilityTest.A,b=EnergyTriggeredAbilityTest.B,controller=UUID.randomUUID();
        for(boolean concede:List.of(false,true)) {
            var ability=CheckupTriggeredAbilityTest.ability("search",false,"{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":1,\"reveal\":true},{\"type\":\"draw\",\"amount\":1}");
            var m=CheckupTriggeredAbilityTest.fixture(List.of(ability),List.of());int before=m.privateView(a).hand().size();
            m.endTurn(a);CheckupTriggeredAbilityTest.choose(m,"search");var pick=m.effectCardPrompt(a);
            m.submitEffectCards(a,pick.resolutionId(),pick.step(),List.of(pick.candidates().getFirst().instanceId()));
            var reveal=m.effectCardReveal();check(reveal!=null&&m.pendingChoicePlayer().equals(b)&&m.phase()==MatchPhase.POKEMON_CHECKUP,"回合间检索展示没有交给对方确认");
            EventTargetBindingTest.reject(()->m.acknowledgeEffectReveal(a,reveal.id()));
            var session=MatchSession.practice(m,controller,a,b);var view=session.view(controller,b);
            var ack=view.options().stream().filter(o->o.labelZh().startsWith("确认已查看")).findFirst().orElseThrow();
            if(concede) {m.concede(a);check(!m.hasPendingEffectChoice()&&m.effectCardReveal()==null,"认输未清理自动特性私有续接");}
            else {
                session.submit(controller,b,view.revision(),ack.index());
                check(m.turnNumber()==2&&m.privateView(a).hand().size()==before+2,"展示后的整段特性或回合间恢复错误");
            }
        }
    }
    static void energyAndNestedEvents() {
        UUID a=EnergyTriggeredAbilityTest.A,b=EnergyTriggeredAbilityTest.B;
        var ability=CheckupTriggeredAbilityTest.ability("attach",false,EnergyTriggeredAbilityTest.ATTACH+","+CHOICE);
        var after=EnergyTriggeredAbilityTest.ability("after","SELF",false,"NONE","{\"type\":\"draw\",\"amount\":2}");
        var m=EnergyTriggeredAbilityTest.fixture(List.of(ability,after),List.of(),null);int before=m.privateView(a).hand().size();
        m.endTurn(a);CheckupTriggeredAbilityTest.choose(m,"attach");var pick=m.effectCardPrompt(a);
        m.submitEffectCards(a,pick.resolutionId(),pick.step(),List.of(pick.candidates().getFirst().instanceId()));var target=m.effectAttachmentPrompt(a);
        EventTargetBindingTest.reject(()->m.chooseEffectAttachmentTarget(b,target.resolutionId(),target.step(),target.targets().getFirst()));
        m.chooseEffectAttachmentTarget(a,target.resolutionId(),target.step(),target.targets().getFirst());var choice=m.effectChoicePrompt(a);
        check(m.privateView(a).hand().size()==before-1&&m.triggerOrderPrompt()==null,"嵌套附能触发打断了原特性后续选择");
        m.chooseEffectOption(a,choice.resolutionId(),choice.step(),0);
        if(m.triggerOrderPrompt()!=null)CheckupTriggeredAbilityTest.choose(m,"after");
        CheckupTriggeredAbilityTest.finishCheckup(m);
        check(m.turnNumber()==2&&m.privateView(a).hand().size()==before+2,"原特性和后续附能触发未各执行一次");
    }
    static void allocationBeforeKnockout() {
        UUID a=DamageTriggeredAbilityTest.A,b=DamageTriggeredAbilityTest.B;
        String effects="""
            {"type":"damage_counter","amount":2,"distribution":"EXACT","target":"SELECT_OPPONENT_POKEMON","target_scope":"OPPONENT_FIELD","min_targets":1,"max_targets":2},
            {"type":"heal","mode":"MINIMUM_HP","target":"SELF","amount":10}
            """;
        var m=DamageTriggeredAbilityTest.fixture(DamageTriggeredAbilityTest.attack(500,""),DamageTriggeredAbilityTest.ability("allocate",true,false,false,effects));m.attack(a,"hit");
        var prompt=m.damageAllocationPrompt(b);UUID target=m.benchPokemonView(a).getFirst().pokemonId();
        check(m.activePokemonView(b).damage()==500&&m.phase()==MatchPhase.TURN,"分配中提前移除致命受伤来源");
        m.adjustDamageAllocation(b,prompt.resolutionId(),prompt.step(),target,1);m.adjustDamageAllocation(b,prompt.resolutionId(),prompt.step(),target,1);
        m.confirmDamageAllocation(b,prompt.resolutionId(),prompt.step());
        check(m.benchPokemonView(a).getFirst().damage()==20&&m.activePokemonView(b).damage()==290&&m.turnNumber()==4,"分配／最低HP回复／击倒检查顺序错误");
    }
    static void coinsAndRepeat() {
        UUID a=EnergyTriggeredAbilityTest.A;
        var ability=EnergyTriggeredAbilityTest.ability("coin","SELF",false,"ONCE_PER_TURN","""
            {"type":"coin_flip","flips":1,"effects":[{"type":"repeat","count":2,"effects":[
                {"type":"choice","effects":[
                    {"type":"conditional","conditions":[{"type":"COIN_RESULT","value":"HEADS"}],"effects":[{"type":"draw","amount":1}]},
                    {"type":"draw","amount":1}]}]}]}
            """);
        var m=EnergyTriggeredAbilityTest.fixture(List.of(ability),List.of(),null);UUID source=m.activePokemonView(a).pokemonId();
        var energy=m.privateView(a).hand().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();m.attachEnergy(a,energy.instanceId(),source);
        var random=m.randomTranscript();int hand=m.privateView(a).hand().size();var first=m.effectChoicePrompt(a);
        m.chooseEffectOption(a,first.resolutionId(),first.step(),0);int once=m.privateView(a).hand().size()-hand;
        var second=m.effectChoicePrompt(a);check(second.step()>first.step(),"嵌套Repeat复用了旧选择步骤");
        EventTargetBindingTest.reject(()->m.chooseEffectOption(a,first.resolutionId(),first.step(),0));
        m.chooseEffectOption(a,second.resolutionId(),second.step(),0);
        check(random.equals(m.randomTranscript())&&m.privateView(a).hand().size()==hand+2*once,"暂停丢失硬币上下文或重复掷币");
        energy=m.privateView(a).hand().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();
        m.attachEnergyByEffect(a,a,EffectSourceZone.HAND,energy.instanceId(),a,source);
        check(!m.hasPendingEffectChoice()&&random.equals(m.randomTranscript()),"无效果分支重置触发次数或重掷");
    }
    static void pausedSourceIdentity() {
        UUID a=DamageTriggeredAbilityTest.A,b=DamageTriggeredAbilityTest.B;
        var reaction=DamageTriggeredAbilityTest.ability("pause",true,false,false,"{\"type\":\"choice\",\"effects\":[{\"type\":\"heal\",\"target\":\"SELF\",\"amount\":10},{\"type\":\"damage_counter\",\"target\":\"ATTACKING_POKEMON\",\"amount\":1}]}");
        var m=DamageTriggeredAbilityTest.fixture(DamageTriggeredAbilityTest.attack(10,""),reaction);UUID old=m.activePokemonView(b).pokemonId();m.attack(a,"hit");var choice=m.effectChoicePrompt(b);
        // Test-only mutation isolates continuation identity; ordinary player actions are forbidden while suspended.
        var f=EventTargetBindingTest.field(m);f.switchActiveByEffect(b,f.benchView(b).getFirst().pokemonId());var removed=f.removePokemonByEffect(b,old);
        f.placeOnBench(b,removed.discardedCards().getFirst());UUID returned=f.printedPokemonView(b,removed.discardedCards().getFirst().instanceId()).pokemonId();check(!returned.equals(old),"Paused source reentry reused logical ID");f.placeDamageCounters(b,returned,2);
        check(choice.optionsZh().get(1).contains("实际攻击者"),"选择文案将实际攻击者误称己方");
        m.chooseEffectOption(b,choice.resolutionId(),choice.step(),0);check(f.pokemonView(b,returned).damage()==20,"暂停后SELF重新绑定同一实体卡的新登场");
    }
    static void choice(String event) {
        String effects="{\"type\":\"draw\",\"amount\":1},"+CHOICE;
        ServerMatch m; UUID owner,other;
        switch(event) {
            case "energy" -> {
                owner=EnergyTriggeredAbilityTest.A;other=EnergyTriggeredAbilityTest.B;
                m=EnergyTriggeredAbilityTest.fixture(List.of(EnergyTriggeredAbilityTest.ability("choice","SELF",false,"ONCE_PER_TURN",effects)),List.of(),null);
                var energy=m.privateView(owner).hand().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();m.attachEnergy(owner,energy.instanceId(),m.activePokemonView(owner).pokemonId());
            }
            case "checkup" -> {
                owner=EnergyTriggeredAbilityTest.A;other=EnergyTriggeredAbilityTest.B;
                m=CheckupTriggeredAbilityTest.fixture(List.of(CheckupTriggeredAbilityTest.ability("choice",false,effects)),List.of());m.endTurn(owner);CheckupTriggeredAbilityTest.choose(m,"choice");
            }
            case "damage" -> {
                owner=DamageTriggeredAbilityTest.B;other=DamageTriggeredAbilityTest.A;
                m=DamageTriggeredAbilityTest.fixture(DamageTriggeredAbilityTest.attack(10,""),DamageTriggeredAbilityTest.ability("choice",true,false,false,effects));m.attack(other,"hit");
            }
            case "knockout" -> {
                owner=DamageTriggeredAbilityTest.B;other=DamageTriggeredAbilityTest.A;
                m=DamageTriggeredAbilityTest.fixture(DamageTriggeredAbilityTest.attack(300,""),KnockoutTriggeredAbilityTest.ability("choice","OPPONENT_ATTACK_DAMAGE",true,false,effects));m.attack(other,"hit");
            }
            default -> throw new AssertionError(event);
        }
        check(m.hasPendingEffectChoice()&&m.pendingChoicePlayer().equals(owner),"触发选择未暂停或未交给拥有者："+event);
        int turn=m.turnNumber(),hand=m.privateView(owner).hand().size();var random=m.randomTranscript();var prompt=m.effectChoicePrompt(owner);
        var session=MatchSession.multiplayer(m,owner,other);UUID watcher=UUID.randomUUID();session.addSpectator(watcher);
        for(UUID player:List.of(owner,other)) {
            EventTargetBindingTest.reject(()->m.endTurn(player));EventTargetBindingTest.reject(()->m.attack(player,"hit"));
        }
        EventTargetBindingTest.reject(()->m.chooseEffectOption(other,prompt.resolutionId(),prompt.step(),0));
        EventTargetBindingTest.reject(()->m.chooseEffectOption(owner,prompt.resolutionId(),prompt.step()+1,0));
        EventTargetBindingTest.reject(()->m.chooseEffectOption(owner,prompt.resolutionId(),prompt.step(),99));
        var snapshot=session.view(owner,owner);var option=snapshot.options().stream().filter(o->o.labelZh().startsWith("选择效果 1")).findFirst().orElseThrow();
        check(session.view(other,other).options().stream().noneMatch(o->o.labelZh().startsWith("选择效果")),"当前回合玩家盗用了特性选择");
        for(int i=0;i<3;i++)session.view(owner,owner);
        check(m.turnNumber()==turn&&random.equals(m.randomTranscript())&&m.privateView(owner).hand().size()==hand,"刷新重复执行前缀或随机");
        session.submit(owner,owner,snapshot.revision(),option.index());
        EventTargetBindingTest.reject(()->session.submit(owner,owner,snapshot.revision(),option.index()));
        EventTargetBindingTest.reject(()->m.chooseEffectOption(owner,prompt.resolutionId(),prompt.step(),0));
        check(m.privateView(owner).hand().size()==hand+1+(event.equals("damage")?1:0),"分支未恰好一次结算："+event);
        if(event.equals("knockout"))check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.privateView(owner).discard().size()==1,"选择结束后未继续KO");
        else if(event.equals("damage")||event.equals("checkup"))check(m.turnNumber()==turn+1,"选择结束后未继续推进回合");
        check(!m.hasPendingEffectChoice(),"触发续接未释放");
    }
    static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
