import dev.hua.ptcglive.collection.BoxEditorSession;
import dev.hua.ptcglive.rules.*;
import java.time.Clock;
import java.util.*;

public final class BoxEditorTest {
    private static final UUID OWNER = UUID.randomUUID(), OTHER = UUID.randomUUID();
    private static final String RULE = "standard-international-2026";
    public static void main(String[] args) {
        var boxes = new CardBoxService(Clock.systemUTC());
        var box = boxes.create(OWNER, "编辑测试", RULE);
        var a = boxes.mint(OWNER, "sv5_en_014");
        var b = boxes.mint(OWNER, "sve_en_001");
        boxes.mint(OTHER, "me1_en_119");
        var editor = session(boxes, box.id());
        var view = editor.view(OWNER);
        check(view.collection().size() == 2, "只同步本人的未入盒收藏");
        check(view.rules().getFirst().statusZh().equals("不可用"), "少于六十张必须不可开局");
        rejects(() -> editor.view(OTHER));
        final int firstRevision = view.revision();
        rejects(() -> editor.submit(OTHER, firstRevision, "ADD", "sv5_en_014", 0, ""));
        rejects(() -> editor.submit(OWNER, firstRevision, "ADD", "me1_en_119", 0, ""));
        view = editor.submit(OWNER, view.revision(), "ADD", "sv5_en_014", 0, "");
        check(view.totalCards() == 1 && boxes.unboxedForOwner(OWNER).size() == 1, "放入不能复制");
        rejects(() -> editor.submit(OWNER, firstRevision, "ADD", "sve_en_001", 0, ""));
        view = editor.submit(OWNER, view.revision(), "RENAME", "我的卡组", 0, "");
        check(view.nameZh().equals("我的卡组"), "改名");
        final int currentRevision = view.revision();
        rejects(() -> editor.submit(OWNER, currentRevision, "RENAME", "换行\n伪造", 0, ""));
        rejects(() -> editor.submit(OWNER, currentRevision, "RULESET", "unknown", 0, ""));
        rejects(() -> editor.submit(OWNER, currentRevision, "DELETE_EMPTY", "", 0, ""));
        view = editor.submit(OWNER, view.revision(), "EXPORT", "", 0, "");
        check(view.exportText().contains(" TEF 14") && view.exportText().endsWith("Total Cards: 1")
            && !view.exportText().contains(a.instanceId().toString()), "导出 Live 卡表不复制实例");
        UUID match = UUID.randomUUID(); boxes.snapshotForMatch(OWNER, box.id(), match);
        final int lockedRevision = view.revision();
        rejects(() -> editor.view(OWNER));
        for (String action : List.of("ADD", "TAKE", "RENAME", "RULESET", "SORT", "EXPORT", "DELETE_EMPTY"))
            rejects(() -> editor.submit(OWNER, lockedRevision, action, "sv5_en_014", 0, ""));
        boxes.unlock(match);
        view = editor.view(OWNER);
        view = editor.submit(OWNER, view.revision(), "TAKE", "sv5_en_014", 0, "");
        check(view.totalCards() == 0 && boxes.unboxedForOwner(OWNER).size() == 2, "取出回收藏，不删除实际卡");
        view = editor.submit(OWNER, view.revision(), "DELETE_EMPTY", "", 0, "");
        check(view.deleted() && boxes.boxesForOwner(OWNER).isEmpty(), "删除空盒");
        rejects(() -> editor.view(OWNER));
        check(boxes.unboxedForOwner(OWNER).stream().anyMatch(c -> c.instanceId().equals(b.instanceId())), "删除空盒不影响卡牌");

        var full = boxes.create(OWNER, "合法真实卡", RULE);
        for (int i = 0; i < 60; i++) boxes.put(OWNER, full.id(), boxes.mint(OWNER, i < 4 ? "sv5_en_014" : "sve_en_001").instanceId());
        var valid = session(boxes, full.id()); var v = valid.view(OWNER);
        check(v.rules().getFirst().canStart(), "复用真实卡组检查器");
        final int fullRevision = v.revision();
        rejects(() -> valid.submit(OWNER, fullRevision, "ADD", "sve_en_001", 0, ""));
        check(full.contents().size() == 60, "拒绝第六十一张");
        v = valid.submit(OWNER, v.revision(), "SORT", "", 0, "");
        var ids = boxes.contentsForValidation(OWNER, full.id()).stream().map(CardInstance::definitionId).toList();
        check(ids.equals(ids.stream().sorted().toList()), "持久化排序");
        var restored = CardBoxService.restore(Clock.systemUTC(), boxes.snapshot());
        // A rejected transaction restores authoritative objects; the earlier `full` reference is stale.
        check(restored.getOwned(OWNER, full.id()).contents().equals(boxes.getOwned(OWNER, full.id()).contents()), "编辑保存与恢复");

        var otherBox = boxes.create(OWNER, "另一个盒", RULE);
        for (int i = 0; i < 37; i++) boxes.mint(OWNER, "unknown_" + i);
        var paged = session(boxes, otherBox.id()); var p = paged.view(OWNER);
        check(p.collection().size() == BoxEditorSession.PAGE_SIZE && p.collectionPages() > 1, "大收藏分页");
        p = paged.submit(OWNER, p.revision(), "PAGE", "", 10000, "unknown_");
        check(p.collectionPage() == p.collectionPages() - 1, "页码安全收敛");
        p = paged.submit(OWNER, p.revision(), "PAGE", "", 0, "unknown_36");
        check(p.collection().size() == 1, "服务器搜索");
        p = paged.submit(OWNER, p.revision(), "ADD", "unknown_36", 0, "");
        check(p.rules().getFirst().statusZh().equals("需更新卡牌／规则数据"), "未知卡不是可对战");

        // The client reports its grid capacity as the PAGE argument; the server clamps it and keeps it.
        p = paged.submit(OWNER, p.revision(), "PAGE", "5", 0, "unknown_");
        check(p.collection().size() == 5 && p.collectionPageSize() == 5, "按客户端格子容量分页");
        int fivePages = p.collectionPages();
        check(fivePages > 1 && p.collectionCards() >= 36, "收藏总张数与页数");
        p = paged.submit(OWNER, p.revision(), "PAGE", "", 1, "unknown_");
        check(p.collectionPageSize() == 5 && p.collection().size() == 5 && p.collectionPage() == 1, "空参数沿用已有每页张数");
        p = paged.submit(OWNER, p.revision(), "REFRESH", "", p.collectionPage(), p.query());
        check(p.collectionPageSize() == 5 && p.collectionPage() == 1, "刷新不改每页张数");
        p = paged.submit(OWNER, p.revision(), "PAGE", "999", 0, "unknown_");
        check(p.collectionPageSize() == BoxEditorSession.MAX_PAGE_SIZE && p.collectionPages() < fivePages, "每页张数有上限");
        final var beforeInvalid = p;
        rejects(() -> paged.submit(OWNER, beforeInvalid.revision(), "PAGE", "abc", 3, "x"));
        rejects(() -> paged.submit(OWNER, beforeInvalid.revision(), "PAGE", "0", 3, "x"));
        p = paged.submit(OWNER, p.revision(), "REFRESH", "", p.collectionPage(), p.query());
        check(p.collectionPageSize() == BoxEditorSession.MAX_PAGE_SIZE && p.collectionPage() == 0 && p.query().equals("unknown_"),
            "无效每页张数整体拒绝，不改页码与搜索");
        retainSourcePosition();
        System.out.println("BoxEditorTest passed: ownership, replay rejection, locks, capacity, sorting, export, deletion, pagination, validation, persistence and inventory/collection position after repeated transfers");
    }
    private static void retainSourcePosition() {
        for(String source:List.of("INVENTORY","COLLECTION")) {
            var boxes=new CardBoxService(Clock.systemUTC());var box=boxes.create(OWNER,"翻页回归",RULE);
            var stock=new java.util.TreeMap<String,Integer>();
            for(int i=0;i<31;i++){
                String id=String.format(java.util.Locale.ROOT,"scroll_%03d",i);stock.put(id,2);
                if(source.equals("COLLECTION")){boxes.mint(OWNER,id);boxes.mint(OWNER,id);}
            }
            var editor=session(boxes,box.id());
            editor.connectInventory(()->Map.copyOf(stock),id->{int n=stock.getOrDefault(id,0);if(n<1)throw new IllegalStateException();
                boxes.put(OWNER,box.id(),boxes.mint(OWNER,id).instanceId());if(n==1)stock.remove(id);else stock.put(id,n-1);});
            var view=editor.view(OWNER);view=editor.submit(OWNER,view.revision(),"SOURCE",source,0,"");
            view=editor.submit(OWNER,view.revision(),"PAGE","5",4,"scroll_");
            String id=view.collection().getFirst().definitionId();
            for(int i=0;i<2;i++){
                view=editor.submit(OWNER,view.revision(),"ADD",id,view.collectionPage(),view.query());
                check(view.collectionPage()==4&&view.query().equals("scroll_")&&view.source().equals(source),"加入卡盒重置了"+source+"页码/检索");
            }
            view=editor.submit(OWNER,view.revision(),"TAKE",id,view.collectionPage(),view.query());
            check(view.collectionPage()==4,"取卡重置了来源页码");
            view=editor.submit(OWNER,view.revision(),"PAGE","5",100,"scroll_");
            int last=view.collectionPage();
            check(last>=5,"测试必须位于后面的页面");
            String lastId=view.collection().getLast().definitionId();
            for(int i=0;i<2;i++)view=editor.submit(OWNER,view.revision(),"ADD",lastId,view.collectionPage(),view.query());
            check(view.collectionPage()>=last-1&&view.collectionPage()>0,"末页减少后应收敛到临近有效页，不能跳首页");
        }
    }
    private static BoxEditorSession session(CardBoxService boxes, UUID box) {
        var editor = new BoxEditorSession(boxes, BundledRules.loadCardDefinitions(), BundledCombatData.load(), BundledRules.loadRulesets(), Map.of(), OWNER, box);
        var view = editor.view(OWNER);
        editor.submit(OWNER, view.revision(), "SOURCE", "COLLECTION", 0, "");
        return editor;
    }
    private static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("应拒绝的操作被接受");
    }
}
