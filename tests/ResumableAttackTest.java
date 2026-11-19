import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Printed damage happens once, then an attack completes its own choices before KO/checkup. */
public final class ResumableAttackTest {
    static final UUID A=ResumableAbilityTest.A,B=ResumableAbilityTest.B,WATCH=ResumableAbilityTest.WATCH;
    public static void main(String[] args) {
        privateSearch(); knockoutAfterText(); counterAllocation(); protectionSurvivesWait(); malformedAtomic(); concede();
        latchedCoins(); energyTriggersWait(); healingBeforeKnockout(); classification(); combinedDamageAndJson();
        System.out.println("ResumableAttackTest passed: after-damage continuation, privacy, source, KO and one turn advance");
    }
    static AttackDefinition attack(String effects) {
        var parsed=JsonParser.parseString("["+effects+"]").getAsJsonArray();
        var list=new ArrayList<EffectDefinition>(); for(var json:parsed)list.add(EffectRegistry.standard().parse(json.getAsJsonObject()));
        return SharedUsageLimitTest.attack("resumable",null).withEffectProgram(new EffectProgram(List.of(
            new TriggeredEffects(EffectTrigger.AFTER_ATTACK,List.of(),list))));
    }
    static ServerMatch fixture(AttackDefinition attack,AbilityDefinition... abilities) {
        return SharedUsageLimitTest.attackFixture(92,List.of(abilities),List.of(attack));
    }
    static void privateSearch() {
        var a=attack("""
            {"type":"draw","amount":1},
            {"type":"search_deck","target":"DECK","amount":1,"minimum":1},
            {"type":"draw","amount":1}
            """);
        var m=fixture(a);var s=MatchSession.multiplayer(m,A,B);s.addSpectator(WATCH);int hand=m.privateView(A).hand().size(),turn=m.turnNumber();
        m.attack(A,a.id());var prompt=m.effectCardPrompt(A);var random=m.randomTranscript();
        check(m.activePokemonView(B).damage()==10&&m.privateView(A).hand().size()==hand+1,"前缀伤害／抽牌未执行");
        check(m.currentPlayer().equals(A)&&m.turnNumber()==turn&&m.clientView(A).resolvingCardEffect(),"等待中提前结束攻击");
        reject(()->m.attack(A,a.id()));reject(()->m.endTurn(A));
        for(UUID viewer:List.of(B,WATCH)) {
            var view=s.view(viewer,viewer.equals(B)?B:null);check(view.game().effectSelection()==null,"泄露攻击检索");
            String json=new Gson().toJson(view);for(var card:prompt.candidates())check(!json.contains(card.instanceId().toString()),"泄露牌库身份");
            reject(()->m.submitEffectCards(viewer,prompt.resolutionId(),prompt.step(),List.of(prompt.candidates().getFirst().instanceId())));
        }
        for(int i=0;i<3;i++)check(s.view(A,A).game().effectSelection().equals(prompt),"刷新更换选择");
        check(random.equals(m.randomTranscript()),"刷新消耗随机");
        m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),List.of(prompt.candidates().getFirst().instanceId()));
        check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B)&&m.turnNumber()==turn+1,"攻击未唯一推进回合");
        check(m.activePokemonView(B).damage()==10&&m.privateView(A).hand().size()==hand+3,"续接重复执行伤害／抽牌");
        reject(()->m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),List.of(prompt.candidates().getFirst().instanceId())));
    }
    static void knockoutAfterText() {
        var a=attack("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":1},{\"type\":\"draw\",\"amount\":1}");
        var m=fixture(a);m.placeDamageCountersByEffect(A,B,m.activePokemonView(B).pokemonId(),29);int hand=m.privateView(A).hand().size(),turn=m.turnNumber();
        m.attack(A,a.id());check(m.phase()==MatchPhase.TURN&&m.activePokemonView(B).damage()==300,"伤害后未等全文就击倒");
        var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId()));
        check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.privateView(A).hand().size()==hand+2,"全文后未拿奖");
        m.takePrizeAt(A,0);check(m.phase()==MatchPhase.CHOOSE_REPLACEMENT,"未等待替补");
        m.chooseReplacement(B,m.benchPokemonView(B).getFirst().pokemonId());
        check(m.currentPlayer().equals(B)&&m.turnNumber()==turn+1,"拿奖／替补后未结束攻击");
    }
    static void counterAllocation() {
        var a=attack("""
            {"type":"damage_counter","amount":2,"distribution":"EXACT","target":"SELECT_OPPONENT_POKEMON","target_scope":"OPPONENT_FIELD","min_targets":1,"max_targets":2},
            {"type":"draw","amount":1}
            """);
        var m=fixture(a);m.attack(A,a.id());var p=m.damageAllocationPrompt(A);UUID bench=m.benchPokemonView(B).getFirst().pokemonId();
        m.adjustDamageAllocation(A,p.resolutionId(),p.step(),bench,1);reject(()->m.confirmDamageAllocation(A,p.resolutionId(),p.step()));
        check(m.benchPokemonView(B).getFirst().damage()==0,"未完成分配已伤害");
        m.adjustDamageAllocation(A,p.resolutionId(),p.step(),bench,1);m.confirmDamageAllocation(A,p.resolutionId(),p.step());
        check(m.benchPokemonView(B).getFirst().damage()==20&&m.currentPlayer().equals(B),"攻击分配未续接");
    }
    static void protectionSurvivesWait() {
        var guard=ResumableAbilityTest.ability("guard",EffectOriginProtectionTest.shield("ATTACK","EFFECTS_ONLY","SELF","SOURCE_NEXT_TURN_START"));
        var a=attack("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":1},{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":2}");
        var m=fixture(a,guard);m.useAbility(A,m.activePokemonView(A).pokemonId(),guard.id());m.endTurn(A);m.attack(B,a.id());
        var p=m.effectCardPrompt(B);m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId()));
        check(m.activePokemonView(A).damage()==10,"攻击暂停后遗失来源，效果穿透防护");
    }
    static void malformedAtomic() {
        var a=attack("{\"type\":\"draw\",\"amount\":1},{\"type\":\"heal\",\"target\":\"SELF\",\"amount\":11}");var m=fixture(a);
        var before=m.clientView(A);var random=m.randomTranscript();reject(()->m.attack(A,a.id()));
        check(before.equals(m.clientView(A))&&random.equals(m.randomTranscript()),"未支持攻击在报错前产生副作用");
    }
    static void concede() {
        var a=attack("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":1}");var m=fixture(a);
        m.attack(A,a.id());m.concede(A);check(!m.hasPendingEffectChoice()&&m.privateView(A).discard().isEmpty(),"认输误弃攻击来源");
    }
    static void latchedCoins() {
        var a=attack("""
            {"type":"coin_flip","flips":1,"effects":[
              {"type":"choice","effects":[
                {"type":"conditional","conditions":[{"type":"COIN_RESULT","value":"HEADS"}],"effects":[{"type":"draw","amount":1}]},
                {"type":"draw","amount":2}]}]}
            """);
        for(int seed=0;seed<10;seed++) {
            var m=SharedUsageLimitTest.attackFixture(seed,List.of(),List.of(a));int turn=m.turnNumber();m.attack(A,a.id());
            var p=m.effectChoicePrompt(A);var random=m.randomTranscript();var s=MatchSession.practice(m,WATCH,A,B);
            for(UUID side:List.of(A,B,A))s.view(WATCH,side);
            reject(()->m.chooseEffectOption(A,p.resolutionId(),p.step(),999));
            m.chooseEffectOption(A,p.resolutionId(),p.step(),0);
            check(random.equals(m.randomTranscript())&&m.turnNumber()==turn+1,"硬币分支重新掷币或无作用后未结束回合");
        }
    }
    static void healingBeforeKnockout() {
        var a=attack("{\"type\":\"heal\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":10}");var m=fixture(a);
        m.placeDamageCountersByEffect(A,B,m.activePokemonView(B).pokemonId(),29);m.attack(A,a.id());
        check(m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(B)&&m.activePokemonView(B).damage()==290,"未按全文末状态检查击倒");
    }
    static void energyTriggersWait() {
        for(boolean optional:List.of(false,true)) for(boolean zero:List.of(false,true)) {
            var a=attack("""
                {"type":"attach_energy","source":"HAND","target":"SELECT_OWN_POKEMON","target_scope":"OWN_FIELD","amount":1,"minimum":0,"basic_energy_only":true},
                {"type":"choice","effects":[{"type":"draw","amount":1},{"type":"draw","amount":2}]}
                """);
            var trigger=EnergyTriggeredAbilityTest.ability("after","SELF",optional,"NONE","{\"type\":\"draw\",\"amount\":2}");
            var m=energyFixture(a,trigger);int hand=m.privateView(A).hand().size(),turn=m.turnNumber();
            m.attack(A,a.id());var p=m.effectCardPrompt(A);
            m.submitEffectCards(A,p.resolutionId(),p.step(),zero?List.of():List.of(p.candidates().getFirst().instanceId()));
            if(!zero) {var target=m.effectAttachmentPrompt(A);m.chooseEffectAttachmentTarget(A,target.resolutionId(),target.step(),m.activePokemonView(A).pokemonId());}
            check(m.triggerOrderPrompt()==null&&!m.energyAttached(A)&&m.currentPlayer().equals(A),"攻击文字中途插入触发／结束回合");
            var choice=m.effectChoicePrompt(A);m.chooseEffectOption(A,choice.resolutionId(),choice.step(),0);
            if(optional&&!zero) {
                check(m.currentPlayer().equals(A)&&m.privateView(A).hand().size()==hand,"可选触发前提前抽牌／结束");
                reject(()->m.endTurn(A));reject(()->m.attack(A,a.id()));
                m.decideTriggeredEffect(A,m.triggerOrderPrompt().id(),true);
            }
            check(m.currentPlayer().equals(B)&&m.turnNumber()==turn+1&&m.privateView(A).hand().size()==hand+(zero?1:2),"攻击附能触发未完成后结束");
        }
    }
    static ServerMatch energyFixture(AttackDefinition attack,AbilityDefinition trigger) {
        var defs=List.of(new CardDefinition("basic","攻击夹具",CardCategory.POKEMON,TrainerCategory.NONE,"H",Set.of("international"),true,false,60,Map.of(),EffectSupport.SUPPORTED,null),
            new CardDefinition("energy","能量夹具",CardCategory.ENERGY,TrainerCategory.NONE,"H",Set.of("international"),false,true,60,Map.of(),EffectSupport.SUPPORTED,null));
        var combat=new CombatCatalog(Map.of("basic",new PokemonProfile(300,0,null,null,0,null,0,1,List.of(attack),List.of(trigger))),Map.of("energy",EnergyType.GRASS));
        for(int seed=0;seed<100;seed++) {
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var deck=new ArrayList<CardInstance>();for(int i=0;i<60;i++)deck.add(new CardInstance(UUID.randomUUID(),owner,i<40?"basic":"energy"));decks.add(deck);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(defs),combat,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(id->m.privateView(id).hand().stream().filter(c->c.definitionId().equals("basic")).count()<2)
                ||m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals("energy")))continue;
            OpeningTestSupport.complete(m,A,B,1);m.endTurn(A);m.endTurn(B);return m;
        }
        throw new AssertionError("无附能测试夹具");
    }
    static void classification() {
        for(String effect:List.of("{\"type\":\"copy_attack\",\"target\":\"OPPONENT_ACTIVE\",\"cost_policy\":\"DECLARED_ATTACK\"}","{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":20}",
            "{\"type\":\"conditional\",\"conditions\":[{\"type\":\"COIN_RESULT\",\"value\":\"HEADS\"}],\"effects\":[{\"type\":\"draw\",\"amount\":1}]}")) {
            // An attack's any-card “up to” search may take zero (official rule); see AttackAnyCardSearchZeroTest.
            var a=attack(effect);check(AttackProgramRules.unsupportedReason(a).isPresent(),"未支持时序被接受");
            var m=fixture(a);var before=m.clientView(A);reject(()->m.attack(A,a.id()));check(before.equals(m.clientView(A)),"错误攻击修改了状态");
            check(MatchSession.multiplayer(m,A,B).view(A,A).options().stream().anyMatch(o->o.labelZh().startsWith("攻击组合尚未支持")),"GUI缺少未支持原因");
        }
    }
    static void combinedDamageAndJson() {
        var a=BundledCombatData.attack(JsonParser.parseString("""
            {"id":"mixed","nameZh":"参数攻击","energyCost":{},"baseDamage":10,"damageType":"GRASS","effectSupport":"SUPPORTED",
              "usageLimit":{"group":"gx-attack","period":"GAME"},
              "effects":[{"type":"damage","target":"OPPONENT_ACTIVE","amount":20}],
              "triggers":[{"trigger":"AFTER_ATTACK","effects":[
                {"type":"search_deck","target":"DECK","amount":1,"minimum":1},
                {"type":"damage_counter","target":"OPPONENT_ACTIVE","amount":2}]}]}
            """).getAsJsonObject(),EffectRegistry.standard());
        var defs=new CardDatabase(List.of(new CardDefinition("basic","组合夹具",CardCategory.POKEMON,TrainerCategory.NONE,"H",Set.of("international"),true,false,60,Map.of(),EffectSupport.SUPPORTED,null)));
        var combat=new CombatCatalog(Map.of("basic",new PokemonProfile(300,0,null,EnergyType.GRASS,2,null,0,1,List.of(a))),Map.of());
        var rule=BundledRules.loadRulesets().get("standard-international-2026");var deckA=ResumableAbilityTest.deck(A);
        check(new DeckValidator(defs,combat).validate(rule,deckA).issues().stream().noneMatch(i->i.code().equals("攻击组合未支持")),"已接入攻击被卡组检查器误拒绝");
        var m=new ServerMatch(A,B,deckA,ResumableAbilityTest.deck(B),rule,defs,combat,new ServerRandom(92));OpeningTestSupport.complete(m,A,B,1);m.endTurn(A);m.endTurn(B);
        m.attack(A,a.id());check(m.activePokemonView(B).damage()==60,"伤害组件未经过弱点");
        var packet=new Gson().fromJson(new Gson().toJson(m.clientView(A)),MatchClientView.class);
        check(packet.resolvingAttack()!=null&&packet.resolvingAttack().attackId().equals(a.id())&&SharedUsageLimitTest.marker(packet,A).gxUsed(),"攻击来源和GX资格未保存");
        var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId()));
        check(m.activePokemonView(B).damage()==80&&m.lastAttack().finalDamage()==60,"指示物错误套弱点或攻击伤害重做");
        var invalid=attack("{\"type\":\"copy_attack\",\"target\":\"OPPONENT_ACTIVE\",\"cost_policy\":\"DECLARED_ATTACK\"}");
        var invalidCombat=new CombatCatalog(Map.of("basic",new PokemonProfile(300,0,null,null,0,null,0,1,List.of(invalid))),Map.of());
        check(new DeckValidator(defs,invalidCombat).validate(rule,deckA).issues().stream().anyMatch(i->i.code().equals("攻击组合未支持")),"检查器未拒绝未支持时序");
    }
    static void check(boolean value,String message){ResumableAbilityTest.check(value,message);}
    static void reject(Runnable action){ResumableAbilityTest.rejects(action);}
}
