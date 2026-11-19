import dev.hua.ptcglive.client.PtcgLiveTableLayout;
import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import java.util.ArrayList;
import java.util.List;

/** 对战桌（PTCG Live 横版布局）在常见 Minecraft GUI 尺寸下的几何约束。 */
public final class PtcgLiveTableLayoutTest {
    public static void main(String[] args) {
        // 原版自动界面尺寸保证 GUI 至少 320×240；其余是 720p/1080p/2K 在不同界面尺寸下的 GUI 大小。
        int[][] sizes = {{320, 240}, {426, 240}, {480, 270}, {640, 360}, {683, 384}, {854, 480}, {960, 540},
            {1100, 760}, {1280, 720}, {1920, 1080}, {400, 300}, {1024, 768}};
        for (int[] size : sizes) verify(size[0], size[1]);
        // Cover vanilla scale factors, odd window sizes and ultrawide displays.
        for (int w = 320; w <= 1920; w += 137)
            for (int h = 240; h <= 1080; h += 97) verify(w, h);
        // 1080p 自动界面尺寸（480×270）下，在完整显示手牌的前提下保持备战／战斗卡的最小尺寸。
        var auto = PtcgLiveTableLayout.forScreen(480, 270);
        check(auto.benchWidth() >= 26 && auto.benchHeight() >= 38 && auto.activeWidth() >= 33 && auto.activeHeight() >= 47,
            "1080p 自动界面尺寸的卡太小：" + auto);
        check(PtcgLiveTableLayout.forScreen(960, 540).ui() == 2 && auto.ui() == 1, "大界面应把图标放大 2 倍，小界面保持 1 倍");
        try { PtcgLiveTableLayout.forScreen(159, 240); throw new AssertionError("过小界面应明确拒绝"); }
        catch (IllegalArgumentException expected) { }
        try { PtcgLiveTableLayout.forScreen(480, 120); throw new AssertionError("过矮界面应明确拒绝"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("PtcgLiveTableLayoutTest passed: 双方托盘、牌堆、头像、提示区、右侧栏和手牌在各 GUI 尺寸均无重叠且不出屏");
    }

    private static void verify(int width, int height) {
        var layout = PtcgLiveTableLayout.forScreen(width, height);
        String where = width + "x" + height + " -> " + layout;
        var result=layout.resultSheet();
        check(result.x()>=0&&result.y()>=0&&result.right()<=width&&result.bottom()<=height,"结算面板越界");
        int firstButton=result.bottom()-12*layout.ui()-3*(layout.buttonHeight()+4*layout.ui());
        check(firstButton>result.y()+111*layout.ui(),"结算操作遮住说明");
        check(layout.activeHeight() > layout.benchHeight() && layout.benchWidth() > 0, "战斗卡应比备战卡大：" + where);
        check(layout.handPeek() >= 14, "手牌至少要露出 14 像素：" + where);
        check(layout.handY() + layout.activeHeight() == height - layout.margin(),
            "我方手牌必须锚在屏幕底部安全边距：" + where);
        check(layout.benchTray(true).y() == layout.margin()
            && layout.benchY(true) == layout.margin() + layout.pad(),
            "对方牌区必须锚在屏幕顶部安全边距：" + where);
        check(layout.handRaisedY() >= layout.benchTray(false).bottom() + 2,
            "悬停手牌不能盖住自己的备战区：" + where);
        check(layout.handRaisedY() >= 0 && layout.handRaisedY() <= layout.handY()
            && layout.handRaisedY() + layout.activeHeight() <= height, "悬停升起的手牌应整张可见：" + where);

        // 各区域都在屏幕内，且两两不重叠。
        var regions = new ArrayList<Rect>();
        var names = new ArrayList<String>();
        for (boolean opponent : new boolean[] {true, false}) {
            String side = opponent ? "对方" : "我方";
            add(regions, names, layout.benchTray(opponent), side + "备战区");
            add(regions, names, layout.activeSlot(opponent), side + "战斗位");
            add(regions, names, layout.prizeTray(opponent), side + "奖赏卡");
            add(regions, names, layout.lostTray(opponent), side + "放逐区");
            add(regions, names, layout.pileTray(opponent), side + "牌库与弃牌");
            add(regions, names, layout.avatar(opponent), side + "头像");
        }
        add(regions, names, layout.stadiumSlot(), "场地");
        add(regions, names, layout.prompt(), "提示区");
        add(regions, names, layout.menuButton(), "菜单");
        add(regions, names, layout.switchButton(), "切换视角");
        add(regions, names, layout.endTurn(), "结束回合");
        for (int i = 0; i < regions.size(); i++) {
            var area = regions.get(i);
            check(area.x() >= 0 && area.y() >= 0 && area.right() <= width && area.bottom() <= height,
                names.get(i) + " 越出屏幕：" + area + " @ " + where);
            for (int j = i + 1; j < regions.size(); j++)
                check(!area.overlaps(regions.get(j)), names.get(i) + " 与 " + names.get(j) + " 重叠 @ " + where);
        }
        // 托盘里五个备战格都在托盘内。
        for (int i = 0; i < 5; i++) {
            int x = layout.benchX(i);
            check(x >= layout.trayX() && x + layout.benchWidth() <= layout.trayX() + layout.trayWidth(), "第 " + (i + 1) + " 个备战格越出托盘 @ " + where);
        }
        // 卡位在托盘内。
        check(layout.prizeX() + layout.prizeWidth() <= layout.prizeTray(false).right(), "奖赏扇形越出托盘 @ " + where);
        check(layout.discardX() + layout.benchWidth() <= layout.pileTray(false).right(), "弃牌区越出托盘 @ " + where);
        // 手牌行不进右侧栏；行里每张卡都在屏幕内。
        for (int count : new int[] {1, 7, 12, 20, 40, 60}) {
            var row = layout.handRow(count);
            int left = row.x(0), right = row.x(count - 1) + layout.activeWidth();
            check(left >= 0 && right <= layout.rightColumnX(), count + " 张手牌越出可用宽度 @ " + where);
        }
        for (int x : new int[] {0, width / 2, width}) for (int y : new int[] {0, height / 2, height}) {
            var drag = layout.draggedCard(x, y, layout.activeWidth(), layout.activeHeight());
            check(Math.abs(drag.x()+drag.width()/2.0-x)<=0.5 && Math.abs(drag.y()+drag.height()/2.0-y)<=0.5,
                "拖拽卡牌中心偏离鼠标 @ " + where);
            check(drag.width()==layout.activeWidth()&&drag.height()==layout.activeHeight(),"拖拽改变卡牌大小 @ "+where);
        }
        // 手牌行从我方备战区下方开始；中间弹层盖住中间带但不盖备战区和右侧栏。
        check(layout.handY() >= layout.benchTray(false).bottom(), "手牌挡住我方备战区 @ " + where);
        check(layout.handHintY() >= layout.benchTray(false).bottom()
            && layout.handHintY() + 9 * layout.ui() <= layout.handY(),
            "手牌说明必须位于我方备战区与底部手牌之间 @ " + where);
        var overlay = layout.overlay();
        check(!overlay.overlaps(layout.benchTray(true)) && !overlay.overlaps(layout.benchTray(false)) && overlay.right() <= layout.rightColumnX()
            && !overlay.overlaps(layout.avatar(true)) && !overlay.overlaps(layout.avatar(false)), "中间弹层盖住了备战区、头像或右侧栏 @ " + where);
        check(overlay.width() >= 150 && overlay.height() >= layout.activeHeight(), "中间弹层太小 @ " + where);
        // 伤害指示物在提示区里，提示区至少能写 8 个汉字。
        var tray = layout.counterTray();
        check(layout.prompt().contains(tray.x(), tray.y()) && tray.x() + tray.size() <= layout.prompt().right()
            && tray.y() + tray.size() <= layout.prompt().bottom(), "伤害指示物不在提示区里 @ " + where);
        check(layout.prompt().width() >= 8 * 9 * layout.ui(), "提示区太窄 @ " + where);
    }

    private static void add(List<Rect> regions, List<String> names, Rect area, String name) { regions.add(area); names.add(name); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
