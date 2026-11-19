import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.table.*;
import java.time.Clock;
import java.util.*;

/** A real transport disconnect must release only that player's matches and waiting seats. */
public final class DisconnectCleanupTest {
    private static final String RULE = "standard-international-2026";
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    private static DeckBox box(CardBoxService boxes, UUID owner) {
        var box = boxes.create(owner, "断线测试", RULE);
        for (int i=0; i<60; i++) boxes.put(owner, box.id(), boxes.mint(owner,
            i<4 ? "fixture-basic-h" : "fixture-energy-h").instanceId());
        return box;
    }
    public static void main(String[] args) {
        var boxes = new CardBoxService(Clock.systemUTC());
        var tables = new CardTableManager(BundledRules.loadRulesets(), boxes, BundledRules.loadCardDefinitions(), BundledCombatData.load(), () -> new ServerRandom(9));
        UUID a=UUID.randomUUID(), b=UUID.randomUUID(), c=UUID.randomUUID(), d=UUID.randomUUID(), watcher=UUID.randomUUID();
        var ab=box(boxes,a); var bb=box(boxes,b); var cb=box(boxes,c); var db=box(boxes,d);
        var one=new CardTableKey("minecraft:overworld", 101); var two=new CardTableKey("minecraft:overworld", 202);
        var first=start(tables,one,a,b,ab,bb); var second=start(tables,two,c,d,cb,db);
        tables.spectate(one,watcher);
        check(tables.disconnectPlayer(watcher).isEmpty(), "旁观者断线中止了对局");
        check(tables.publicTableView(one).spectatorCount()==0, "旁观者席位未清理");
        check(boxes.isLocked(a,ab.id()) && tables.session(first)!=null, "旁观断线解锁了参赛卡盒");
        check(tables.disconnectPlayer(b).equals(List.of(first)), "对手断线未返回中止对局");
        check(!boxes.isLocked(a,ab.id()) && !boxes.isLocked(b,bb.id()), "任一方断线后两盒未解锁");
        check(boxes.isLocked(c,cb.id()) && tables.session(second)!=null, "误伤独立第二桌");
        check(tables.publicTableView(one).status()==TableLobbyStatus.ENDED, "已中止大厅未结束");
        check(tables.recoveryHistoryFor(a).size()==1 && tables.completedHistoryFor(a).isEmpty(), "断线错误计为胜负");
        check(tables.disconnectPlayer(b).isEmpty() && tables.recoveryHistoryFor(a).size()==1, "重复断线产生副作用");
        try { tables.session(first); throw new AssertionError("旧对局仍可访问"); } catch (IllegalArgumentException expected) { }
        var waiting=new CardTableKey("minecraft:overworld",303);
        tables.open(waiting,a,RULE); tables.join(waiting,b); tables.selectBox(waiting,b,bb.id()); tables.setReady(waiting,b,true);
        tables.disconnectPlayer(b);
        check(tables.publicTableView(waiting).guest()==null && !tables.publicTableView(waiting).guestReady(), "离线对手仍占据已准备席位");
        tables.disconnectPlayer(a);
        check(tables.publicTableView(waiting).status()==TableLobbyStatus.ENDED,"离线房主留下无法加入的大厅");
        var solo1=box(boxes,a); var solo2=box(boxes,a); var practice=tables.startPractice(a,solo1.id(),solo2.id(),RULE);
        check(tables.disconnectPlayer(a).equals(List.of(practice.matchId())),"练习控制者断线未中止");
        check(!boxes.isLocked(a,solo1.id()) && !boxes.isLocked(a,solo2.id()),"练习卡盒未解锁");
        check(boxes.contentsForValidation(a,solo1.id()).size()==60 && boxes.contentsForValidation(b,bb.id()).size()==60,"断线改变原卡组");
        tables.disconnectPlayer(c);
        check(!boxes.isLocked(d,db.id()), "房主断线未解除对手锁");
        System.out.println("DisconnectCleanupTest passed: participants, spectators, waiting seats, practice, independent tables, idempotency and card conservation");
    }
    private static UUID start(CardTableManager t, CardTableKey key, UUID a, UUID b, DeckBox ab, DeckBox bb) {
        t.open(key,a,RULE); t.join(key,b); t.selectBox(key,a,ab.id()); t.selectBox(key,b,bb.id()); t.setReady(key,a,true); t.setReady(key,b,true);
        return t.start(key,a).id();
    }
}
