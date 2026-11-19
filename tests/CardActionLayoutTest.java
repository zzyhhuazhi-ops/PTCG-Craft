import dev.hua.ptcglive.client.CardActionLayout;
import java.util.HashSet;

public final class CardActionLayoutTest {
    public static void main(String[] args) {
        cardOperations();
        for (int width : new int[] {320, 426, 480, 960, 1920})
            for (int height : new int[] {240, 270, 540, 1080})
                for (int count : new int[] {0, 1, 3, 6, 20, 100}) {
                    var first = CardActionLayout.forScreen(width, height, count, 0);
                    var covered = new HashSet<Integer>();
                    for (int page = 0; page < first.pages(); page++) {
                        var layout = CardActionLayout.forScreen(width, height, count, page);
                        check(layout.x() >= 72 && layout.x() + layout.width() < width, "技能列与卡面区域冲突");
                        for (int index = layout.first(); index < layout.end(); index++) {
                            check(covered.add(index), "技能被重复分页");
                            check(layout.rowY(index) >= 34 && layout.rowY(index) + 20 <= height - 50, "技能按钮越出屏幕");
                        }
                        if (layout.pages() > 1) check(layout.navigationY() + 20 <= height - 50, "翻页按钮越出屏幕");
                    }
                    check(covered.size() == count, "部分服务端技能选项不可访问");
                    check(CardActionLayout.forScreen(width, height, count, 999).page() == first.pages() - 1, "缩放后分页未校正");
                }
        System.out.println("CardActionLayoutTest passed: all skill choices accessible without clipping at every tested GUI scale");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void cardOperations() {
        var options=java.util.List.of(
            new dev.hua.ptcglive.engine.MatchSession.Option(0,"攻击：撞击",java.util.List.of("field:a")),
            new dev.hua.ptcglive.engine.MatchSession.Option(1,"使用特性：再构筑",java.util.List.of("field:a")),
            new dev.hua.ptcglive.engine.MatchSession.Option(2,"撤退 → 备战（建议弃置 2 张能量）",java.util.List.of("field:a","field:b")),
            new dev.hua.ptcglive.engine.MatchSession.Option(3,"使用特性：回复 → 目标",java.util.List.of("field:b","field:a")),
            new dev.hua.ptcglive.engine.MatchSession.Option(4,"本回合已使用特性：再构筑",java.util.List.of("field:a"),false,dev.hua.ptcglive.engine.ActionType.ABILITY,"rebuild"),
            new dev.hua.ptcglive.engine.MatchSession.Option(5,"攻击：重击（能量不足）",java.util.List.of("field:a"),false,dev.hua.ptcglive.engine.ActionType.ATTACK,"heavy"));
        check(dev.hua.ptcglive.view.MatchActionMatcher.cardFaceActions(options,"field:a",true).stream().map(o->o.index()).toList().equals(java.util.List.of(0,1,2)),"技能、特性与撤退应共同显示；目标锚点不能被当作发动者");
        check(dev.hua.ptcglive.view.MatchActionMatcher.cardFaceActions(options,"field:a",false).stream().map(o->o.index()).toList().equals(java.util.List.of(1,2)),"不能攻击时仍可显示合法特性／撤退");
        check(dev.hua.ptcglive.view.MatchActionMatcher.cardFaceActions(options,"field:b",false).stream().map(o->o.index()).toList().equals(java.util.List.of(3)),"备战宝可梦只显示自己的特性，不显示别人的撤退");
        var translated=options.stream().map(o->new dev.hua.ptcglive.engine.MatchSession.Option(o.index(),"说明已翻译 "+o.index(),o.cardKeys(),o.enabled(),o.type(),o.commandId())).toList();
        check(dev.hua.ptcglive.view.MatchActionMatcher.cardFaceActions(translated,"field:a",true).stream().map(o->o.index()).toList().equals(java.util.List.of(0,1,2)),"显示文案不能改变服务器发出的可用状态或操作类型");
    }
}
