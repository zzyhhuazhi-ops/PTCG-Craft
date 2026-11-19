import dev.hua.ptcglive.client.PackGalleryLayout;
import static dev.hua.ptcglive.client.PackGalleryLayout.*;

/** Pack-opening window: header, info column, pool grid, pager, status line and result row never overlap at any GUI size. */
public final class PackGalleryLayoutTest {
    public static void main(String[] args) {
        int[][] sizes = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {854, 480}, {960, 540}, {1100, 760}, {1920, 1080}};
        for (int[] size : sizes) for (int cards : new int[]{0, 1, 5, 207, 600}) verify(size[0], size[1], cards);
        var rarityCodes = dev.hua.ptcglive.CardCatalog.load().stream()
            .map(c -> dev.hua.ptcglive.CardLabel.rarityCode(c.rarityZh())).distinct().sorted().toList();
        var filterWidths = new java.util.ArrayList<Integer>(java.util.List.of(34, 34));
        // Conservative font widths: wider than the default Latin rarity labels.
        rarityCodes.forEach(code -> filterWidths.add(28 + code.length() * 7));
        for (int[] size : sizes) for (int controls : new int[]{2, 6, filterWidths.size()})
            verifyFilters(size[0], size[1], filterWidths.subList(0, controls));
        for (int count = 1; count <= 36; count++) {
            int displayed = 0;
            for (int page = 0; page < PackGalleryLayout.resultPages(count); page++) displayed += Math.min(RESULTS_PER_PAGE, count - page * RESULTS_PER_PAGE);
            check(displayed == count, "可配置开包张数的结果分页遗漏卡牌");
        }

        var tiny = PackGalleryLayout.forScreen(200, 150, 10);
        check(tiny.screenWidth() == MIN_WIDTH && tiny.screenHeight() == MIN_HEIGHT, "过小界面按最小尺寸布局");
        try { PackGalleryLayout.forScreen(0, 240, 1); throw new AssertionError("零尺寸应明确拒绝"); }
        catch (IllegalArgumentException expected) { }
        check(!PackGalleryLayout.forScreen(320, 240, 1).showInfo(), "窄窗口隐藏卡包信息栏");
        check(PackGalleryLayout.forScreen(480, 270, 1).showInfo(), "常见尺寸显示卡包信息栏");
        System.out.println("PackGalleryLayoutTest passed: vanilla-window pack screen keeps header, info, pool grid, pager, status and results apart; wheel pages are continuous");
    }

    private static void verify(int width, int height, int cards) {
        var l = PackGalleryLayout.forScreen(width, height, cards);
        String at = " @" + width + "x" + height + "/" + cards;
        check(l.windowX() >= 0 && l.windowY() >= 0 && l.windowX() + l.windowWidth() <= width && l.windowY() + l.windowHeight() <= height, "窗口出屏" + at);
        int left = l.innerLeft(), right = l.innerRight(), bottom = l.windowY() + l.windowHeight() - PADDING;

        // Header: title space, then 取消 · 确认抽取 (or 完成) flush right; nothing below it is touched.
        check(l.barY() + BUTTON_HEIGHT < l.contentTop(), "顶栏压住内容" + at);
        check(l.cancelX() + CANCEL_WIDTH + GAP == l.confirmX() && l.confirmX() + CONFIRM_WIDTH == right, "取消与确认抽取" + at);
        check(l.doneX() + DONE_WIDTH == right, "完成按钮贴右" + at);
        check(l.titleWidth(false) >= 80 && l.titleWidth(true) >= 80, "标题空间过窄" + at);

        // Content bands, top to bottom: label row, pool grid, pager, status line.
        check(l.gridTop() == l.contentTop() + LABEL_HEIGHT, "卡池标签行" + at);
        check(l.gridTop() + l.gridHeight() <= l.pagerY() - GAP, "卡池格子压住翻页" + at);
        check(l.columnBottom() < l.statusY() && l.statusY() + 9 <= bottom, "翻页压住服务器提示或提示出窗口" + at);
        check(l.pagerRightX() - (l.pagerLeftX() + PAGER_BUTTON_WIDTH) >= 30, "翻页按钮之间没有提示空间" + at);
        int controls=l.pagerRightX()-GAP-(l.pagerLeftX()+PAGER_BUTTON_WIDTH+GAP);
        check((controls-GAP)/2>=80,"数量与存放位置按钮在此GUI尺寸过窄"+at);

        // Info column, divider and pool sit side by side.
        if (l.showInfo()) {
            check(l.infoWidth() >= INFO_MIN_WIDTH && l.infoWidth() <= INFO_MAX_WIDTH, "信息栏宽度" + at);
            check(l.artX() >= left && l.artX() + l.artSize() <= left + l.infoWidth(), "封面出信息栏" + at);
            check(left + l.infoWidth() < l.dividerX() && l.dividerX() + 2 <= l.galleryX(), "分隔线压住信息栏或卡池" + at);
            check(l.infoTextTop() + INFO_LINE_HEIGHT <= l.columnBottom(), "封面下没有信息文字的空间" + at);
        } else check(l.galleryX() == left, "无信息栏时卡池从左边开始" + at);
        check(l.gridLeft() >= l.galleryX() && l.gridLeft() + l.gridWidth() <= right, "卡池格子出栏" + at);
        check(l.cardHeight() >= MIN_CARD_HEIGHT && l.cardHeight() <= MAX_CARD_HEIGHT && l.capacity() >= 1, "卡池卡片尺寸" + at);
        check(l.slotIndexAt(l.slotX(l.columns() - 1), l.slotY(l.rows() - 1)) == l.capacity() - 1, "命中最后一格" + at);
        check(l.slotIndexAt(l.gridLeft() - 1, l.gridTop()) == -1 && l.slotIndexAt(l.gridLeft(), l.gridTop() + l.gridHeight()) == -1, "格子外不算命中" + at);

        // Wheel pages cover every card exactly once.
        int emitted = 0;
        for (int page = 0; page < l.pages(); page++) {
            int start = l.pageStart(page), end = l.pageEndExclusive(page);
            check(start == emitted && end >= start && end - start <= l.capacity(), "滚轮分页范围不连续" + at);
            emitted = end;
        }
        check(emitted == cards, "卡墙分页遗漏或重复卡牌" + at);

        // Results: one centred row of big slots with two label lines, all above the status line.
        for (int count : new int[]{1, 5, 10}) {
            var row = l.resultRow(count);
            String r = at + " results=" + count;
            check(row.top() >= l.contentTop() && row.labelY() + 19 <= l.statusY() - GAP, "结果卡或卡名压住服务器提示" + r);
            check(row.labelY() + 19 <= l.pagerY() - GAP, "结果卡名压住结果翻页按钮" + r);
            check(row.left() >= left && row.left() + row.width() <= right, "结果行出窗口" + r);
            // Packs currently give 5 cards; a 10-card pack must still fit, just smaller.
            if (count <= 5) check(row.cardHeight() >= 40, "结果卡过小：" + row.cardHeight() + r);
            check(Math.abs(row.cardWidth() - Math.round(row.cardHeight() * dev.hua.ptcglive.client.PtcgLiveTheme.CARD_ASPECT)) <= 1, "结果卡比例" + r);
            for (int i = 0; i + 1 < count; i++) check(row.slotX(i) + row.slotWidth() < row.slotX(i + 1), "结果卡相互重叠" + r);
            check(row.indexAt(row.slotX(count - 1), row.top()) == count - 1 && row.indexAt(row.slotX(0), row.top() - 1) == -1, "结果卡命中" + r);
        }
    }
    private static void verifyFilters(int width, int height, java.util.List<Integer> widths) {
        for (int cards : new int[]{0, 1, 600}) {
            var l = PackGalleryLayout.forScreen(width, height, cards, widths);
            var slots = l.filterSlots(widths);
            String at = " @" + width + "x" + height + "/" + widths.size() + " filters";
            check(l.cardHeight() >= MIN_CARD_HEIGHT && l.capacity() > 0, "筛选栏挤掉卡池" + at);
            check(l.gridTop() + l.gridHeight() <= l.pagerY() - GAP, "筛选后卡池挡住底部按钮" + at);
            for (int i = 0; i < slots.size(); i++) {
                var s = slots.get(i);
                check(s.x() >= l.galleryX() && s.x() + s.width() <= l.innerRight(), "罕度按钮横向越界" + at);
                check(s.y() >= l.contentTop() + LABEL_HEIGHT && s.y() + FILTER_HEIGHT <= l.gridTop() - GAP, "罕度按钮覆盖标签或卡牌" + at);
                check(l.slotIndexAt(s.x(), s.y()) == -1 && l.slotIndexAt(s.x() + s.width() - 1, s.y() + FILTER_HEIGHT - 1) == -1, "右键罕度按钮穿透卡牌" + at);
                if (i > 0) {
                    var previous = slots.get(i - 1);
                    check(s.y() >= previous.y() + FILTER_HEIGHT || s.x() >= previous.x() + previous.width(), "罕度按钮重叠" + at);
                }
            }
            check(l.slotIndexAt(l.slotX(0), l.slotY(0)) == 0, "筛选后首张卡不可点击" + at);
            check(l.pages() >= 1 && l.pageEndExclusive(0) <= cards, "筛选空列表页码无效" + at);
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
