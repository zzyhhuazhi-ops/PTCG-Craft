import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.view.MatchPresentation;
import dev.hua.ptcglive.client.EnergyStackLayout;
import java.util.*;

public final class MatchPresentationTest {
    static final UUID MATCH = new UUID(0,1), A = new UUID(0,2), B = new UUID(0,3), MON = new UUID(0,4);
    static MatchSession.Snapshot snapshot(long revision, UUID seat, int damage, int prizes,
            List<CardInstance> hand, List<String> cues, List<SpecialCondition> conditions) {
        var mon = new BattlefieldState.PokemonView(MON,"basic",100,damage,List.of(),conditions,List.of("basic"),List.of());
        var players = List.of(new MatchClientView.Player(A,hand.size(),40,prizes,mon,List.of(),List.of(),List.of()),
            new MatchClientView.Player(B,7,40,6,null,List.of(),List.of(),List.of()));
        var view = new MatchClientView(MATCH,seat,MatchPhase.TURN,2,A,A,null,players,hand,List.of());
        return new MatchSession.Snapshot(revision,false,List.of(A),view,List.of(),Map.of(),cues,"");
    }
    public static void main(String[] args) {
        serverGuidance();
        publicCoins();
        confirmedAttackDamage();
        serverAttackDamage();
        journalPrizePrivacy();
        journalCatchup();
        toolPublicSequence();
        abilityPublicMarkers();
        abilityJournalRepeats();
        var before=snapshot(4,A,0,6,List.of(),List.of(),List.of());
        var after=snapshot(5,A,30,6,List.of(),List.of("攻击结算完成"),List.of(SpecialCondition.POISONED));
        var events=MatchPresentation.changes(before,after);
        check(events.stream().anyMatch(e->e.kind()==MatchPresentation.Kind.DAMAGE && e.amount()==30),"伤害数字");
        check(events.stream().anyMatch(e->e.kind()==MatchPresentation.Kind.STATUS),"异常状态");
        check(events.stream().anyMatch(e->e.kind()==MatchPresentation.Kind.ATTACK),"服务端确认攻击");
        String confusion="混乱判定反面：攻击失败，自身放置3个伤害指示物（30点）";
        check(MatchPresentation.changes(before,snapshot(5,A,30,6,List.of(),List.of(confusion),List.of()))
            .stream().anyMatch(e->e.kind()==MatchPresentation.Kind.ATTACK&&e.text().equals(confusion)),"混乱失败原因必须进入界面反馈");
        check(MatchPresentation.changes(after,snapshot(6,A,10,6,List.of(),List.of(),List.of()))
            .stream().anyMatch(e->e.kind()==MatchPresentation.Kind.HEAL&&e.amount()==20),"伤害移除反馈");
        check(MatchPresentation.changes(after,after).isEmpty(),"重复快照不重播");
        check(MatchPresentation.changes(after,before).isEmpty(),"旧快照不重播");
        check(MatchPresentation.changes(before,snapshot(5,B,30,6,List.of(),List.of(),List.of())).isEmpty(),"切换甲乙不伪造动画");
        check(MatchPresentation.changes(before,snapshot(8,A,30,6,List.of(),List.of(),List.of())).isEmpty(),"跳跃快照不误认净变化为单次伤害");
        var card=new CardInstance(new UUID(0,10),A,"prize");
        var prize=snapshot(5,A,0,5,List.of(card),List.of(),List.of());
        check(MatchPresentation.changes(before,prize).stream().anyMatch(e->"prize".equals(e.cardId())),"仅本人已入手奖赏翻面");
        var observerBefore=snapshot(4,null,0,6,List.of(),List.of(),List.of());
        var observerAfter=snapshot(5,null,0,5,List.of(),List.of(),List.of());
        check(MatchPresentation.changes(observerBefore,observerAfter).stream().allMatch(e->e.cardId()==null),"旁观者不推测奖赏卡面");
        check(MatchPresentation.changes(before,snapshot(5,A,0,6,List.of(),List.of("任意内部文本"),List.of())).isEmpty(),"公开提示白名单");
        for(int w=16;w<=120;w++) for(int n:new int[]{0,1,2,10,60}) {
            var fan=EnergyStackLayout.of(10,20,w,w*7/5,n);
            check(fan.front().y()+fan.front().height()<=20+w*7/5,"正面不越界");
            check(fan.layers().size()<=2,"大量能量限量可视叠放");
            for(var r:fan.layers()) check(r.x()>=10&&r.right()<=10+w&&r.bottom()<=20+w*7/5,"能量不越卡位");
        }
        System.out.println("MatchPresentationTest passed: confirmed deltas, duplicate/stale/seat/gap suppression, prize privacy and bounded energy stacks");
    }
    static void abilityJournalRepeats(){
        var gson=new com.google.gson.Gson();var game=snapshot(4,A,0,6,List.of(),List.of(),List.of()).game();
        var journal=new BattleEventJournal();journal.observe(game,A,"",List.of(),id->id);
        var json=gson.toJsonTree(game).getAsJsonObject();json.add("resolvingAbility",gson.toJsonTree(new MatchClientView.AbilityResolution(MON,"same","同一个特性")));
        var resolving=gson.fromJson(json,MatchClientView.class);
        journal.observe(resolving,A,"结算触发特性：same",List.of(),id->id);
        journal.observe(resolving,A,"结算触发特性：same",List.of(),id->id);
        check(journal.events().stream().filter(e->e.cue().kind()==MatchPresentation.Kind.ABILITY).count()==2,"连续同名同来源触发丢失或重复");
        json.getAsJsonObject("resolvingAbility").add("pokemonId",com.google.gson.JsonNull.INSTANCE);
        journal.observe(gson.fromJson(json,MatchClientView.class),A,"来源离场后继续效果",List.of(),id->id);
        check(journal.events().stream().filter(e->e.cue().kind()==MatchPresentation.Kind.ABILITY).count()==2,"公开来源变为隐藏后错误重播特性");
        json.add("resolvingAbility",gson.toJsonTree(new MatchClientView.AbilityResolution(MON,"tool-trigger","工具效果",dev.hua.ptcglive.engine.effect.EffectOrigin.Kind.TRAINER)));
        journal.observe(gson.fromJson(json,MatchClientView.class),A,"结算触发效果：tool-trigger",List.of(),id->id);
        check(journal.events().stream().filter(e->e.cue().kind()==MatchPresentation.Kind.ABILITY).count()==2,"工具触发被误标成宝可梦特性");
    }
    static void abilityPublicMarkers(){
        var before=snapshot(4,A,0,6,List.of(),List.of(),List.of());
        for(String suffix:List.of("","·名称·保留分隔符")){
            var after=snapshot(5,A,0,6,List.of(),List.of("特性·"+A+"·"+MON+suffix),List.of());
            var cue=MatchPresentation.changes(before,after).stream().filter(c->c.kind()==MatchPresentation.Kind.ABILITY).findFirst().orElseThrow();
            check(MON.equals(cue.pokemon())&&cue.text().equals(suffix.isEmpty()?"特性发动":"名称·保留分隔符"),"新旧特性公开标记兼容");
        }
        for(UUID actor:List.of(A,B)){
            var after=snapshot(5,A,0,6,List.of(),List.of("特性·"+actor+"·"+new UUID(0,999)+"·公开名称"),List.of());
            check(MatchPresentation.changes(before,after).stream().filter(c->c.kind()==MatchPresentation.Kind.ABILITY).allMatch(c->c.pokemon()==null),"隐藏或未知特性来源不能定位到牌桌");
        }
    }
    static void toolPublicSequence(){
        var match=PersistentTrainerTest.game();
        var actor=PersistentTrainerTest.A;
        var host=match.activePokemonView(actor).pokemonId();
        var card=PersistentTrainerTest.hand(match,actor,"balloon");
        var legacyBefore=new MatchSession.Snapshot(1,false,List.of(actor),match.clientView(actor),List.of(),Map.of(),List.of(),"");
        match.enablePublicEvents();
        match.playTrainer(actor,card.instanceId(),host);
        var legacyAfter=new MatchSession.Snapshot(2,false,List.of(actor),match.clientView(actor),List.of(),Map.of(),
            List.of("出牌·"+actor+"·"+card.definitionId(),"出牌·"+actor+"·"+card.definitionId()),"");
        var legacy=MatchPresentation.changes(legacyBefore,legacyAfter);
        check(legacy.stream().filter(c->c.kind()==MatchPresentation.Kind.PLAY).count()==1,"旧快照也必须展示一次道具，重复公开标签不得重播");
        check(legacy.stream().filter(c->c.kind()==MatchPresentation.Kind.TOOL&&c.amount()==1).count()==1,"旧快照保留附着事件");
        var multipleItems=snapshot(5,A,0,6,List.of(),List.of("出牌·"+A+"·quad-stone","出牌·"+A+"·quad-stone"),List.of());
        check(MatchPresentation.changes(snapshot(4,A,0,6,List.of(),List.of(),List.of()),multipleItems).stream()
            .filter(c->c.kind()==MatchPresentation.Kind.PLAY).count()==2,"道具去重不能改变同名物品同时使用的旧反馈");
        var events=match.publicEvents().stream().map(BattleEventJournal.Event::cue).toList();
        var reveals=events.stream().filter(c->c.kind()==MatchPresentation.Kind.PLAY&&card.definitionId().equals(c.cardId())).toList();
        var attachments=events.stream().filter(c->c.kind()==MatchPresentation.Kind.TOOL&&c.amount()==1).toList();
        check(reveals.size()==1,"正常手牌道具必须公开展示一次");
        check(attachments.size()==1&&host.equals(attachments.getFirst().pokemon()),"道具附着必须保留准确公开目标");
        check(events.indexOf(reveals.getFirst())<events.indexOf(attachments.getFirst()),"展示应在附着之前");
        int seen=match.publicEvents().size();
        TrainerEffectsTest.rejects(()->match.playTrainer(actor,card.instanceId(),host));
        check(match.publicEvents().size()==seen,"已离手道具的无效重放不得新增演出");
        match.discardToolByEffect(actor,actor,host,card.instanceId());
        var removal=match.publicEvents().subList(seen,match.publicEvents().size()).stream().map(BattleEventJournal.Event::cue).toList();
        check(removal.stream().filter(c->c.kind()==MatchPresentation.Kind.TOOL&&c.amount()==-1).count()==1,"移除只生成一次道具移除反馈");
        check(removal.stream().noneMatch(c->c.kind()==MatchPresentation.Kind.PLAY),"移除不能再次触发出牌展示");
    }
    static MatchSession.Snapshot journal(MatchSession.Snapshot s,MatchPresentation.Cue... cues){
        var events=new ArrayList<BattleEventJournal.Event>();
        for(var cue:cues)events.add(new BattleEventJournal.Event(events.size()+1,2,cue,cue.text()));
        return new MatchSession.Snapshot(s.revision(),s.practice(),s.controllableSeats(),s.game(),s.options(),s.cardNames(),s.publicCues(),s.attackBlockZh(),s.guidance(),events,s.computer());
    }
    static void journalCatchup(){
        var hit=new MatchPresentation.Cue(MatchPresentation.Kind.DAMAGE,A,MON,20,"招式伤害·DRAGON",null);
        var before=journal(snapshot(4,A,20,6,List.of(),List.of(),List.of()),hit);
        var after=journal(snapshot(8,A,40,6,List.of(),List.of(),List.of()),hit,hit);
        check(MatchPresentation.changes(before,after).equals(List.of(hit)),"跳版本时只补播未读的连续事件");
        var truncated=new MatchSession.Snapshot(after.revision(),false,List.of(A),after.game(),List.of(),Map.of(),
            List.of(),"",after.guidance(),List.of(new BattleEventJournal.Event(3,2,hit,hit.text())));
        check(MatchPresentation.journalGap(before,truncated)&&MatchPresentation.changes(before,truncated).isEmpty(),"日志窗口缺失时恢复场面，不能从半段招式开始播");
    }
    static void journalPrizePrivacy(){
        var card=new CardInstance(new UUID(0,10),A,"known-prize");
        var other=new CardInstance(new UUID(0,11),A,"other-card");
        var cue=new MatchPresentation.Cue(MatchPresentation.Kind.PRIZE,A,null,1,"拿取奖赏 1 张",null);
        var before=journal(snapshot(4,A,0,6,List.of(),List.of(),List.of()));
        var after=journal(snapshot(5,A,0,5,List.of(card),List.of(),List.of()),cue);
        var shown=MatchPresentation.changes(before,after);
        check(shown.size()==1&&"known-prize".equals(shown.getFirst().cardId()),"真实公开事件需结合本人已入手卡身份翻面");
        check(after.events().getFirst().cue().cardId()==null,"补全本地卡面不能修改公开事件");
        check(MatchPresentation.changes(after,after).isEmpty(),"已揭晓奖赏的重复快照不得重播");
        var draw=new MatchPresentation.Cue(MatchPresentation.Kind.DRAW,A,null,1,"抽牌",null);
        var ambiguous=journal(snapshot(5,A,0,5,List.of(card),List.of(),List.of()),cue,draw);
        check(MatchPresentation.changes(before,ambiguous).stream().allMatch(c->c.cardId()==null),"同时抽牌时不得猜测新卡来源");
        for(var unknown:List.of(
            journal(snapshot(7,A,0,5,List.of(card),List.of(),List.of()),cue),
            journal(snapshot(5,A,0,5,List.of(card,other),List.of(),List.of()),cue),
            journal(snapshot(5,A,0,4,List.of(card,other),List.of(),List.of()),cue, cue))){
            check(MatchPresentation.changes(before,unknown).stream().allMatch(c->c.cardId()==null),"跳帧或多张新增手牌不能关联到单张奖赏");
        }
        var removedBefore=journal(snapshot(4,A,0,6,List.of(other),List.of(),List.of()));
        var replacement=journal(snapshot(5,A,0,5,List.of(card,new CardInstance(new UUID(0,12),A,"replacement")),List.of(),List.of()),cue);
        check(MatchPresentation.changes(removedBefore,replacement).stream().allMatch(c->c.cardId()==null),"手牌发生替换时不得误认奖赏身份");
        for(UUID seat:Arrays.asList(B,null)){
            var hiddenBefore=journal(snapshot(4,seat,0,6,List.of(),List.of(),List.of()));
            var hiddenAfter=journal(snapshot(5,seat,0,5,List.of(),List.of(),List.of()),cue);
            check(MatchPresentation.changes(hiddenBefore,hiddenAfter).stream().allMatch(c->c.cardId()==null),"对手和旁观者不应看到奖赏正面");
        }
    }
    static void serverGuidance() {
        var game=MatchRulesRegressionTest.game(100,List.of());MatchRulesRegressionTest.setup(game,true);
        UUID a=MatchRulesRegressionTest.A,b=MatchRulesRegressionTest.B,observer=new UUID(0,300);
        var session=MatchSession.multiplayer(game,a,b);session.addSpectator(observer);
        check(session.view(a,a).attackBlockZh().contains("首回合"),"服务端规则包首回合限制");
        check(session.view(b,b).attackBlockZh().contains("对手"),"非当前玩家等待原因");
        check(session.view(observer,null).attackBlockZh().contains("观战"),"旁观者不可攻击");
        var view=session.view(a,a);
        var end=view.options().stream().filter(o->o.labelZh().equals("结束回合")).findFirst().orElseThrow();
        session.submit(a,a,view.revision(),end.index());
        check(session.view(b,b).attackBlockZh().isBlank(),"后手允许攻击不误报首回合");
        var beforeAttack=session.view(b,b);
        var attack=beforeAttack.options().stream().filter(o->o.labelZh().startsWith("攻击：")).findFirst().orElseThrow();
        session.submit(b,b,beforeAttack.revision(),attack.index());
        var afterAttack=session.view(b,b);
        check(afterAttack.publicCues().stream().anyMatch(c->c.startsWith("招式演出·"+b+"·")),"服务器下发公开招式名称和实际伤害");
        check(afterAttack.publicCues().stream().noneMatch(c->c.startsWith("招式伤害·")),"零伤害状态招式不得伪造命中特效");
        var attackCue=MatchPresentation.changes(beforeAttack,afterAttack).stream().filter(c->c.kind()==MatchPresentation.Kind.ATTACK).findFirst().orElseThrow();
        check(!attackCue.text().equals("攻击结算完成")&&attack.labelZh().contains(attackCue.text()),"演出使用已宣告的印刷名称");
        String json=new com.google.gson.Gson().toJson(session.view(observer,null));
        for(var card:game.privateView(b).hand())check(!json.contains(card.instanceId().toString()),"引导字段不得泄漏手牌");
        for(var card:game.privateView(b).prizes())check(!json.contains(card.instanceId().toString()),"反馈字段不得泄漏奖赏");
    }
    static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
    static void confirmedAttackDamage() {
        var before=snapshot(4,A,0,6,List.of(),List.of(),List.of());
        String hit="招式伤害·"+A+"·"+MON+"·30·FIRE";
        var after=snapshot(5,A,30,6,List.of(),List.of(hit),List.of());
        var damage=MatchPresentation.changes(before,after).stream().filter(c->c.kind()==MatchPresentation.Kind.DAMAGE).toList();
        check(damage.size()==1&&damage.getFirst().text().equals("招式伤害·FIRE"),"服务端命中只播放一次，不与净伤害重复");
        var poison=MatchPresentation.changes(before,snapshot(5,A,10,6,List.of(),List.of(),List.of(SpecialCondition.POISONED)));
        check(poison.stream().noneMatch(c->c.text().startsWith("招式伤害·")),"中毒数值变化不得推测为属性攻击");
        var invalid=snapshot(5,A,0,6,List.of(),List.of("招式伤害·"+A+"·"+UUID.randomUUID()+"·30·FIRE",
            "招式伤害·"+A+"·"+MON+"·-1·FIRE","招式伤害·"+A+"·"+MON+"·30·UNKNOWN"),List.of());
        check(MatchPresentation.changes(before,invalid).isEmpty(),"拒绝非公开目标、负数和未知属性的命中标记");
        var old=after.game();
        var players=List.of(new MatchClientView.Player(A,0,40,6,null,List.of(),List.of(),List.of()),old.players().get(1));
        var gone=new MatchClientView(MATCH,A,MatchPhase.TURN,2,A,A,null,players,List.of(),List.of());
        var knockout=new MatchSession.Snapshot(5,false,List.of(A),gone,List.of(),Map.of(),List.of(hit),"");
        check(MatchPresentation.changes(before,knockout).stream().anyMatch(c->c.kind()==MatchPresentation.Kind.DAMAGE&&c.amount()==30),
            "立即击倒离场的目标仍保留服务器命中演出");
    }
    static void serverAttackDamage() {
        var game=AttackCoinMatchTest.game(AttackCoinMatchTest.nativeAttack("\"selfDamage\":0"),EnergyType.WATER,false);
        UUID a=AttackCoinMatchTest.A,b=AttackCoinMatchTest.B;
        var session=MatchSession.multiplayer(game,a,b);
        var before=session.view(a,a);
        var attack=before.options().stream().filter(o->o.labelZh().startsWith("攻击：")).findFirst().orElseThrow();
        session.submit(a,a,before.revision(),attack.index());
        var after=session.view(a,a);
        check(after.publicCues().stream().anyMatch(c->c.startsWith("招式伤害·"+b+"·")&&c.endsWith("·WATER")),
            "真实服务器下发命中目标以及攻击宝可梦的公开属性");
        var hits=MatchPresentation.changes(before,after).stream().filter(c->c.kind()==MatchPresentation.Kind.DAMAGE).toList();
        check(hits.size()==1&&hits.getFirst().amount()==game.lastAttack().finalDamage(),"真实命中和净变化不重复计数");
    }
    static void publicCoins() {
        var events=List.of(new RandomAuditEvent(1,"attack-confusion:private-owner",2,1),
            new RandomAuditEvent(2,"effect-coin:private-owner:0",2,0),
            new RandomAuditEvent(3,"effect-coin:private-owner:1",2,1),
            new RandomAuditEvent(4,"shuffle:private-card",2,0),
            new RandomAuditEvent(5,"effect-random-hand-discard:private-owner",2,1),
            new RandomAuditEvent(6,"unknown-coin:private-owner",2,0),
            new RandomAuditEvent(7,"trainer-attempt-coin:private-owner:private-card",2,0));
        var publicResults=PublicCoinResults.describe(events);
        check(publicResults.equals(List.of("硬币·混乱：反面","硬币·卡牌效果：正面","硬币·卡牌效果：反面","硬币·训练家使用前判定：正面")),"只公开确认为投币的结果，不得泄露洗牌、随机弃牌、未知随机操作或内部标识");
        var before=snapshot(4,A,0,6,List.of(),List.of(),List.of());
        var after=snapshot(5,A,0,6,List.of(),publicResults,List.of());
        check(MatchPresentation.changes(before,after).stream().filter(c->c.kind()==MatchPresentation.Kind.COIN).count()==4,"每枚硬币结果应进入独立显示反馈");
    }
}
