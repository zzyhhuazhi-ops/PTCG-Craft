import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.table.*;
import dev.hua.ptcglive.view.*;
import java.util.*;
import java.time.*;

/** Real core, recipient snapshots and fake clock; this is NOT a two-Minecraft-client test. */
public final class BattleExperienceTest {
    static final UUID A=new UUID(0,81001),B=new UUID(0,81002),C=new UUID(0,81003);
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static void rejects(Runnable r){try{r.run();}catch(IllegalStateException|IllegalArgumentException ok){return;}throw new AssertionError("非法请求被接受");}
    public static void main(String[] args){previewAndPrivacy();journalBound();invitationsAndRematch();legacySearch();System.out.println("BattleExperienceTest passed: preview purity/damage, event privacy/order/bounds, invite races/expiry/ownership, mutual rematch, committed search");}
    static CardDefinition pokemon(String id){return new CardDefinition(id,id,CardCategory.POKEMON,TrainerCategory.NONE,"H",Set.of("international"),true,false,60,Map.of(),EffectSupport.SUPPORTED,null);}
    static List<CardInstance> deck(UUID owner,String id){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(UUID.randomUUID(),owner,id));return cards;}
    static ServerMatch fixture(EnergyType weakness,EnergyType resistance,AttackDefinition attack){
        var defs=new CardDatabase(List.of(pokemon("attacker"),pokemon("defender")));
        var combat=new CombatCatalog(Map.of("attacker",new PokemonProfile(500,0,null,null,0,null,0,1,List.of(attack)),
            "defender",new PokemonProfile(500,0,null,weakness,weakness==null?0:2,resistance,resistance==null?0:20,1,List.of())),Map.of());
        var match=new ServerMatch(A,B,deck(A,"attacker"),deck(B,"defender"),MatchRulesRegressionTest.RULE,defs,combat,new ServerRandom(40));
        OpeningTestSupport.complete(match,A,B,0);
        while(!A.equals(match.currentPlayer())||!match.mayAttack(A))match.endTurn(match.currentPlayer());
        return match;
    }
    static void previewAndPrivacy(){
        var attack=new AttackDefinition("hit","测试攻击",Map.of(),60,EnergyType.FIRE,EffectSupport.SUPPORTED,null);
        for(var mode:List.of("normal","weak","resist")) {
            var game=fixture(mode.equals("weak")?EnergyType.FIRE:null,mode.equals("resist")?EnergyType.FIRE:null,attack);
            var session=MatchSession.multiplayer(game,A,B);session.addSpectator(C);
            var random=game.randomTranscript();var before=game.clientView(A);var audit=game.replayTranscript();
            var snap=session.view(A,A);var preview=snap.guidance().attacks().getFirst();
            int expected=mode.equals("weak")?120:mode.equals("resist")?40:60;
            check(Objects.equals(preview.damage(),expected),"确定预览错误："+preview);
            for(int i=0;i<12;i++)session.view(A,A);
            check(before.equals(game.clientView(A))&&random.equals(game.randomTranscript())&&audit.equals(game.replayTranscript()),"预览改变真实状态或随机序列");
            var observer=session.view(C,null);check(observer.guidance().attacks().isEmpty()&&observer.guidance().blockedCards().isEmpty()&&observer.guidance().turnUsage().isEmpty(),"旁观者获得私有提示");
            var option=snap.options().stream().filter(MatchActionMatcher::isAvailableAttackOption).findFirst().orElseThrow();
            session.submit(A,A,snap.revision(),option.index());
            check(game.lastAttack().finalDamage()==expected,"预览与实际结算不同");
            var after=session.view(A,A);var cues=MatchPresentation.changes(snap,after);
            check(cues.stream().anyMatch(c->c.kind()==MatchPresentation.Kind.DAMAGE),"真实伤害缺少反馈");
            int declared=-1,damaged=-1;for(int i=0;i<cues.size();i++){if(cues.get(i).kind()==MatchPresentation.Kind.ATTACK)declared=i;if(cues.get(i).kind()==MatchPresentation.Kind.DAMAGE)damaged=i;}
            check(declared>=0&&damaged>declared,"攻击宣告与伤害检查点顺序错误");
            check(MatchPresentation.changes(after,after).isEmpty(),"重复消息重播");
            rejects(()->session.submit(A,A,snap.revision(),option.index()));
            String pub=new com.google.gson.Gson().toJson(session.view(C,null));
            for(var p:List.of(A,B))for(var card:game.privateView(p).hand())check(!pub.contains(card.instanceId().toString()),"公共快照泄露手牌实例");
            for(var e:after.events())check(!e.text().matches(".*[0-9a-f]{8}-[0-9a-f-]{27,}.*"),"人类记录含内部标识");
        }
        var coin=new AttackDefinition("coin","随机攻击",Map.of(),10,EnergyType.FIRE,EffectSupport.SUPPORTED,null,List.of(),new AttackDefinition.CoinDamage(1,20),null,List.of());
        var uncertain=MatchSession.multiplayer(fixture(null,null,coin),A,B).view(A,A).guidance().attacks().getFirst();
        check(uncertain.damage()==null,"随机攻击伪装为精确预览");
        var costed=new AttackDefinition("costed","缺能量攻击",Map.of(EnergyType.FIRE,1),60,EnergyType.FIRE,EffectSupport.SUPPORTED,null);
        var conditional=MatchSession.multiplayer(fixture(null,null,costed),A,B).view(A,A).guidance().attacks().getFirst();
        check(!conditional.payable()&&conditional.certainty().equals("条件伤害")&&Objects.equals(conditional.damage(),60),"不能支付的招式未标记为条件伤害");
        var zeroGame=fixture(null,null,new AttackDefinition("zero","无伤害招式",Map.of(),0,EnergyType.FIRE,EffectSupport.SUPPORTED,null));
        var zeroSession=MatchSession.multiplayer(zeroGame,A,B);var zeroView=zeroSession.view(A,A);
        var zeroOption=zeroView.options().stream().filter(MatchActionMatcher::isAvailableAttackOption).findFirst().orElseThrow();
        zeroSession.submit(A,A,zeroView.revision(),zeroOption.index());
        check(zeroSession.view(A,A).events().stream().anyMatch(e->e.text().contains("攻击伤害为 0")),"零伤害攻击没有真实结果记录");
    }
    static void journalBound(){
        var journal=new BattleEventJournal();var sample=MatchPresentationTest.snapshot(1,null,0,6,List.of(),List.of(),List.of()).game();
        UUID actor=sample.players().getFirst().id();journal.observe(sample,actor,"",List.of(),id->id);
        for(int i=0;i<300;i++)journal.observe(sample,actor,"公开洗牌",List.of(),id->id);
        check(journal.events().size()==BattleEventJournal.LIMIT,"公共记录未限制内存");
        int n=journal.events().size();journal.observe(sample,A,"隐藏种子 secret-card-id",List.of(),id->id);
        check(journal.events().size()==n&&journal.events().stream().noneMatch(e->e.text().contains("secret")),"内部记录泄露");
    }
    static final class Time extends Clock {long now=1_000_000;public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return Instant.ofEpochMilli(now);}public long millis(){return now;}}
    static void invitationsAndRematch(){
        var time=new Time();var boxes=new CardBoxService(time);var rules=BundledRules.loadRulesets();var cards=BundledRules.loadCardDefinitions();
        var tables=CardTableManager.restore(rules,boxes,cards,BundledCombatData.load(),()->new ServerRandom(19),time,new CardTableManager.Snapshot(List.of(),List.of(),List.of()));
        var a=legalBox(boxes,A);var b=legalBox(boxes,B);var key=new CardTableKey("minecraft:overworld",100);
        var online=new HashSet<>(List.of(A,B,C));var near=new HashSet<>(List.of(A,B));var physical=new HashSet<>(List.of(key));
        tables.socialPresence(online::contains,physical::contains,(id,k)->near.contains(id));
        tables.open(key,A,"standard-international-2026");tables.selectBox(key,A,a.id());
        rejects(()->tables.invite(key,A,A));var first=tables.invite(key,A,B);rejects(()->tables.invite(key,A,C));
        near.remove(B);tables.answerInvitation(B,first.id(),true);rejects(()->tables.answerInvitation(B,first.id(),true));
        tables.selectBox(key,B,b.id());tables.setReady(key,A,true);tables.setReady(key,B,true);
        rejects(()->tables.start(key,A));near.add(B);var game=tables.start(key,A);
        finish(tables,game.id());var old=game.id();
        check(tables.requestRematch(old,A).isEmpty(),"一人强行再战");
        var fresh=tables.requestRematch(old,B).orElseThrow();check(!fresh.id().equals(old)&&fresh.turnNumber()==0&&fresh.publicEvents().isEmpty(),"再战沿用旧局状态");
        rejects(()->boxes.take(A,a.id(),a.contents().getFirst()));finish(tables,fresh.id());
        tables.requestRematch(fresh.id(),A);tables.cancelRematch(fresh.id(),B);rejects(()->tables.requestRematch(fresh.id(),A));
        tables.open(key,A,"standard-international-2026");tables.selectBox(key,A,a.id());time.now+=10_001;
        var exp=tables.invite(key,A,B);time.now+=60_001;rejects(()->tables.answerInvitation(B,exp.id(),true));
        var cancelled=tables.invite(key,A,B);tables.cancelInvitation(A,cancelled.id());rejects(()->tables.invite(key,A,B));time.now+=10_001;
        var raced=tables.invite(key,A,B);tables.join(key,C);rejects(()->tables.answerInvitation(B,raced.id(),true));tables.leave(key,C);time.now+=10_001;
        var offline=tables.invite(key,A,B);online.remove(B);rejects(()->tables.answerInvitation(B,offline.id(),true));online.add(B);time.now+=10_001;
        var removed=tables.invite(key,A,B);physical.remove(key);check(tables.expireInvitations().size()==1,"拆桌未清理邀请");rejects(()->tables.answerInvitation(B,removed.id(),true));physical.add(key);time.now+=10_001;
        var changed=tables.invite(key,A,B);tables.requireBoxPossession((owner,box)->!owner.equals(A));rejects(()->tables.answerInvitation(B,changed.id(),true));
        check(tables.invitationsFor(A).isEmpty(),"失效邀请残留");
    }
    static DeckBox legalBox(CardBoxService boxes,UUID owner){var box=boxes.create(owner,"测试卡盒","standard-international-2026");for(int i=0;i<60;i++){var c=boxes.mint(owner,i<4?"fixture-basic-h":"fixture-energy-h");boxes.put(owner,box.id(),c.instanceId());}return box;}
    static void finish(CardTableManager tables,UUID id){var s=tables.session(id);var snap=s.view(A,A);var concede=snap.options().stream().filter(o->o.labelZh().equals("认输并结束本局")).findFirst().orElseThrow();tables.submit(id,A,A,snap.revision(),concede.index());}
    static void legacySearch(){
        var search=new CardDefinition("search","检索测试",CardCategory.TRAINER,TrainerCategory.ITEM,"H",Set.of("international"),false,false,60,Map.of(),EffectSupport.SUPPORTED,null);
        var definitions=new CardDatabase(List.of(pokemon("basic"),search));
        for(int minimum:new int[]{0,1,2}) {
            var combat=new CombatCatalog(Map.of("basic",new PokemonProfile(100,0,null)),Map.of(),Map.of("search",new TrainerProfile(TrainerCategory.ITEM,
                new TrainerEffect(TrainerEffect.Opcode.SEARCH_BASIC_POKEMON_TO_HAND,2,0,0,0,minimum))));
            var cards=deck(A,"basic");for(int i=0;i<30;i++)cards.set(i,new CardInstance(UUID.randomUUID(),A,"search"));
            var game=new ServerMatch(A,B,cards,deck(B,"basic"),MatchRulesRegressionTest.RULE,definitions,combat,new ServerRandom(4));
            OpeningTestSupport.complete(game,A,B,0);if(!game.currentPlayer().equals(A))game.endTurn(B);
            var session=MatchSession.multiplayer(game,A,B);session.addSpectator(C);
            var before=session.view(A,A);var play=before.options().stream().filter(o->o.labelZh().contains("开始检索")).findFirst().orElseThrow();
            check(before.options().stream().noneMatch(o->o.labelZh().contains("：检索 basic")),"使用前泄露牌库候选");
            session.submit(A,A,before.revision(),play.index());var selecting=session.view(A,A);var prompt=selecting.game().effectSelection();
            check(prompt!=null&&prompt.minimum()==0&&prompt.maximum()==2,"检索未复用数量规则");
            check(session.view(B,B).game().effectSelection()==null&&session.view(C,null).game().effectSelection()==null,"私人候选泄露");
            check(selecting.options().stream().noneMatch(o->o.labelZh().equals("结束回合")),"已公开卡牌后绕过选择");
            for(int i=0;i<minimum;i++){var pick=session.view(A,A);String key="effect:"+prompt.candidates().get(i).instanceId();var o=pick.options().stream().filter(v->v.cardKeys().contains(key)).findFirst().orElseThrow();session.submit(A,A,pick.revision(),o.index());}
            var selected=session.view(A,A);check(selected.game().effectSelection().selected().size()==minimum,"重开未恢复服务器选择");
            var confirm=selected.options().stream().filter(o->o.labelZh().startsWith("确认选牌")).findFirst().orElseThrow();session.submit(A,A,selected.revision(),confirm.index());
            if(game.effectCardReveal()!=null){var reveal=game.effectCardReveal();game.acknowledgeEffectReveal(reveal.confirmingPlayer(),reveal.id());}
            check(!game.hasPendingCardSelection(),"确认后卡死");check(game.privateView(A).discard().stream().anyMatch(c->c.definitionId().equals("search")),"训练家未进入弃牌区");
        }
    }
}
