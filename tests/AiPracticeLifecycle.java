import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.table.*;
import java.time.Clock;
import java.util.*;

/** Exercises the production server-tick controller, including independent tables and cleanup. */
public final class AiPracticeLifecycle {
    public static void main(String[] args){AiPracticeTest.cards=BundledRules.loadCardDefinitions();AiPracticeTest.combat=BundledCombatData.load();AiPracticeTest.rules=BundledRules.loadRulesets();run();}
    static void check(boolean b,String m){AiPracticeTest.check(b,m);}
    static void rejects(Runnable r){AiPracticeTest.rejects(r);}
    static DeckBox box(CardBoxService boxes,UUID owner,AiPresets.Preset p){var box=boxes.create(owner,"练习验收",p.rule());p.cards().forEach((id,n)->{for(int i=0;i<n;i++)boxes.put(owner,box.id(),boxes.mint(owner,id).instanceId());});return box;}
    static UUID start(CardTableManager tables,CardTableKey key,UUID owner,UUID player,UUID computer,String rule){var report=tables.checkComputer(key,owner,player,computer,rule);check(report.allowed(),"配置预检失败："+report);return tables.startComputer(key,owner,player,computer,rule,report.token());}
    static CardTableManager manager(CardBoxService boxes){return new CardTableManager(AiPracticeTest.rules,boxes,AiPracticeTest.cards,AiPracticeTest.combat,()->new ServerRandom(19));}
    static void run(){multiTable();interruptionAndRematch();forcedFailure();System.out.println("AI LIFECYCLE passed: two concurrent automatic opponents, result cleanup, table removal, offline, changed/missing boxes, stale intents, forced-choice failure abort, no assets minted, restart snapshot excludes live games");}
    static void multiTable(){
        var boxes=new CardBoxService(Clock.systemUTC());var tables=manager(boxes);var owners=List.of(new UUID(14,1),new UUID(14,2));var games=new LinkedHashMap<UUID,UUID>();
        var used=new HashMap<UUID,Set<String>>();var turns=new HashMap<UUID,Integer>();var attacks=new HashMap<UUID,Integer>();
        for(int i=0;i<2;i++){var owner=owners.get(i);var p=AiCustomDeckTest.configurations().get(i);var b=box(boxes,owner,p);var key=new CardTableKey("minecraft:overworld",1510+i);tables.open(key,owner,p.rule());games.put(owner,start(tables,key,owner,b.id(),b.id(),p.rule()));used.put(owner,new HashSet<>());}
        var assets=boxes.snapshot();var notBefore=new HashMap<UUID,Long>();var publicViews=new HashMap<UUID,MatchSession.Snapshot>();
        games.forEach((owner,id)->publicViews.put(id,tables.matchView(id,owner,null)));
        long maxTick=0;var durations=new ArrayList<Double>();int tick;
        for(tick=0;tick<60000&&tables.activeComputerCount()>0;tick++) {
            long begin=System.nanoTime();var changes=tables.tickComputers(tick);long elapsed=System.nanoTime()-begin;maxTick=Math.max(maxTick,elapsed);if(!changes.isEmpty())durations.add(elapsed/1_000_000.0);
            for(var change:changes) {
                check(!change.aborted(),"有效预设发生异常中止："+change.notice());
                check(tick>=notBefore.getOrDefault(change.match(),0L),"AI 在上一段公开特效显示结束前又行动");
                var owner=games.entrySet().stream().filter(e->e.getValue().equals(change.match())).findFirst().orElseThrow().getKey();
                var after=tables.matchView(change.match(),owner,null);
                var cues=dev.hua.ptcglive.view.MatchPresentation.changes(publicViews.put(change.match(),after),after);
                notBefore.put(change.match(),tick+dev.hua.ptcglive.view.AiPresentationTiming.ticks(cues));
            }
            for(var entry:games.entrySet()){
                UUID owner=entry.getKey(),id=entry.getValue();if(!tables.computerRunning(id))continue;
                var session=tables.session(id);if(!humanCanAct(session.match(),owner))continue;int current=session.match().turnNumber();if(!Objects.equals(turns.put(owner,current),current))used.get(owner).clear();
                var v=AiPracticeScenarios.view(session,owner);var decision=AiPolicy.choose(v,141400L+tick,used.get(owner));if(decision.isEmpty())continue;
                var d=decision.get();var o=v.snapshot().options().get(d.option());if(d.category().equals("攻击"))attacks.merge(owner,1,Integer::sum);
                tables.submit(id,owner,owner,d.revision(),d.option());String key=AiPolicy.optionalKey(o);if(!key.isEmpty())used.get(owner).add(key);
                rejects(()->tables.submit(id,owner,owner,d.revision(),d.option()));
                publicViews.put(id,tables.matchView(id,owner,null));
            }
        }
        check(tables.activeComputerCount()==0,"多桌调度没有结束");var after=boxes.snapshot();check(assets.cards().equals(after.cards())&&assets.boxes().equals(after.boxes())&&assets.discoveries().equals(after.discoveries()),"电脑练习改变收藏或生成临时卡盒");
        for(var entry:games.entrySet()){var end=tables.matchView(entry.getValue(),entry.getKey(),entry.getKey());check(end.game().result()!=null,"正常结算缺失");check(attacks.getOrDefault(entry.getKey(),0)>0,"自动人类席位只结束回合");rejects(()->tables.session(entry.getValue()));}
        System.out.println("AI SCHEDULER tables=2 ticks="+tick+" maxTickMs="+(maxTick/1_000_000.0));
        Collections.sort(durations);try{java.nio.file.Files.writeString(java.nio.file.Path.of("outputs/ai-scheduler-0.7.15-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("tables",2,"ticks",tick,"activeTicks",durations.size(),"medianMs",durations.get(durations.size()/2),"p95Ms",durations.get((int)((durations.size()-1)*.95)),"maxMs",maxTick/1_000_000.0,"durationsMs",durations)));}catch(java.io.IOException e){throw new AssertionError(e);}
    }
    static boolean humanCanAct(ServerMatch game,UUID seat){return switch(game.phase()){
        case OPENING_POKEMON,MULLIGAN_REVEAL,MULLIGAN_DRAW,MULLIGAN_BENCH -> true;
        default -> seat.equals(game.pendingChoicePlayer());
    };}
    static void interruptionAndRematch(){
        var boxes=new CardBoxService(Clock.systemUTC());var tables=manager(boxes);var p=AiPresets.all().getFirst();UUID a=AiPracticeTest.A;var b=box(boxes,a,p);var key=new CardTableKey("minecraft:overworld",1616);tables.open(key,a,p.rule());
        UUID id=start(tables,key,a,b.id(),b.id(),p.rule());var session=tables.session(id);UUID bot=tables.matchView(id,a,a).computer().seat();
        var saved=tables.snapshot();check(saved.waitingTables().isEmpty()&&!saved.interruptedMatches().isEmpty(),"重启保存了可恢复的活局");
        var botCards=session.match().privateView(bot).deck().stream().map(CardInstance::instanceId).collect(java.util.stream.Collectors.toSet());
        var v=tables.matchView(id,a,a);tables.submit(id,a,a,v.revision(),v.options().stream().filter(o->o.labelZh().startsWith("认输")).findFirst().orElseThrow().index());
        check(!boxes.isLocked(a,b.id()),"正常终局未解锁");
        UUID removed=b.contents().getFirst();boxes.take(a,b.id(),removed);rejects(()->tables.rematchComputer(id,a));boxes.put(a,b.id(),removed);
        tables.requireBoxPossession((owner,box)->false);rejects(()->tables.rematchComputer(id,a));tables.requireBoxPossession((owner,box)->true);
        UUID next=tables.rematchComputer(id,a);var nextView=tables.matchView(next,a,a);check(nextView.revision()==0&&nextView.game().phase()==MatchPhase.COIN_CALL,"再战没执行完整开局");
        UUID nextBot=nextView.computer().seat();check(!nextBot.equals(bot)&&tables.session(next).match().privateView(nextBot).deck().stream().noneMatch(c->botCards.contains(c.instanceId())),"再战保留电脑旧实例");
        tables.removePhysicalTable(key);check(tables.activeComputerCount()==0&&!tables.hasMatch(next)&&!boxes.isLocked(a,b.id()),"拆桌残留任务、临时数据或卡盒锁");check(tables.tickComputers(200).isEmpty(),"拆桌后还行动");
        tables.open(key,a,p.rule());UUID offline=start(tables,key,a,b.id(),b.id(),p.rule());tables.socialPresence(owner->false,k->true,(owner,k)->true);
        var stopped=tables.tickComputers(500);check(stopped.stream().anyMatch(u->u.match().equals(offline)&&u.aborted()),"未明确提示离线异常终止");check(!boxes.isLocked(a,b.id())&&!tables.hasMatch(offline),"离线未清理");
    }
    static void forcedFailure(){
        var boxes=new CardBoxService(Clock.systemUTC());var tables=manager(boxes);var p=AiPresets.all().getFirst();UUID a=AiPracticeTest.A;var b=box(boxes,a,p);var key=new CardTableKey("minecraft:overworld",1717);tables.open(key,a,p.rule());
        UUID id=start(tables,key,a,b.id(),b.id(),p.rule());var session=tables.session(id);UUID bot=tables.matchView(id,a,a).computer().seat();var assets=boxes.snapshot();boolean injected=false,aborted=false;var used=new HashSet<String>();int turn=-1;
        for(int tick=0;tick<10000&&tables.computerRunning(id);tick++) {
            for(var update:tables.tickComputers(tick))if(update.match().equals(id))aborted|=update.aborted();
            if(!tables.computerRunning(id))break;
            var sv=session.view(bot,bot);var prompt=sv.game().effectSelection();
            if(!injected&&prompt!=null&&!prompt.selected().isEmpty()){
                var deck=AiPracticeScenarios.zone(session.match(),bot,"deck");var selected=deck.stream().filter(c->prompt.selected().contains(c.instanceId())).findFirst();
                if(selected.isPresent()){deck.remove(selected.get());AiPracticeScenarios.zone(session.match(),bot,"discard").add(selected.get());injected=true;}
            }
            if(injected)continue;
            var v=AiPracticeScenarios.view(session,a);if(turn!=v.snapshot().game().turnNumber()){turn=v.snapshot().game().turnNumber();used.clear();}
            var d=AiPolicy.choose(v,9914L+tick,used);if(d.isPresent()){var o=v.snapshot().options().get(d.get().option());tables.submit(id,a,a,d.get().revision(),d.get().option());String remembered=AiPolicy.optionalKey(o);if(!remembered.isEmpty())used.add(remembered);}
        }
        check(injected&&aborted,"强制选择失效未明确异常终止");check(!tables.hasMatch(id)&&!boxes.isLocked(a,b.id())&&assets.equals(boxes.snapshot()),"异常终止未释放临时数据与卡盒");check(session.match().result()==null,"异常终止伪造了正常胜负");
    }
}
