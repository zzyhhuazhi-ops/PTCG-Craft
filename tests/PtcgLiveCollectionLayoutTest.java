import dev.hua.ptcglive.client.PtcgLiveCollectionLayout;
import static dev.hua.ptcglive.client.PtcgLiveCollectionLayout.*;

/** Card-box editor as a vanilla container window: everything stays inside the window and nothing overlaps at any GUI size. */
public final class PtcgLiveCollectionLayoutTest {
    public static void main(String[] args) {
        int[][] sizes = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {854, 480}, {960, 540}, {1100, 760}, {1920, 1080}, {3840, 2160}};
        for (int[] size : sizes) verify(size[0], size[1]);

        // Smaller than Minecraft's minimum GUI: laid out as 320×240 rather than crashing the client.
        var tiny = PtcgLiveCollectionLayout.forScreen(200, 150);
        check(tiny.screenWidth() == MIN_WIDTH && tiny.screenHeight() == MIN_HEIGHT, "过小界面按最小尺寸布局");
        try { PtcgLiveCollectionLayout.forScreen(0, 240); throw new AssertionError("零尺寸应明确拒绝"); }
        catch (IllegalArgumentException expected) { }

        // The narrowest GUI keeps both columns and drops only the title text.
        var narrow = PtcgLiveCollectionLayout.forScreen(320, 240);
        check(!narrow.showTitle(), "窄屏隐藏标题，把位置让给名称框");
        check(narrow.panelX(COLLECTION) > narrow.panelX(BOX), "窄屏仍为左右两栏");
        check(PtcgLiveCollectionLayout.forScreen(640, 360).showTitle(), "宽屏显示标题");

        // A larger screen never shows fewer cards per page.
        int previous = 0;
        for (int[] size : new int[][]{{320, 240}, {480, 270}, {640, 360}, {960, 540}, {1920, 1080}}) {
            int cards = PtcgLiveCollectionLayout.forScreen(size[0], size[1]).cardsPerPage();
            check(cards >= previous, "屏幕变大后每页张数不应减少：" + size[0] + "x" + size[1]);
            previous = cards;
        }
        System.out.println("PtcgLiveCollectionLayoutTest passed: vanilla-window card box keeps top bar, reserved slot, slot grids, pagers and status inside the window at every GUI size");
    }

    private static void verify(int width, int height) {
        var l = PtcgLiveCollectionLayout.forScreen(width, height);
        String at = " @" + width + "x" + height;
        // The window sits inside the screen; its contents stay inside its padding.
        int left = l.windowX() + PADDING, right = l.windowX() + l.windowWidth() - PADDING;
        int top = l.windowY() + PADDING, bottom = l.windowY() + l.windowHeight() - PADDING;
        check(l.windowX() >= 0 && l.windowY() >= 0 && l.windowX() + l.windowWidth() <= width && l.windowY() + l.windowHeight() <= height,
            "窗口出屏" + at);

        // Top bar: [title] name · save · reserved · rules · close, in order, inside the window.
        int bar = l.barY();
        check(bar >= top && bar + BUTTON_HEIGHT < l.columnTop(), "顶栏压住两栏" + at);
        check(l.nameX() >= left + (l.showTitle() ? TITLE_WIDTH + GAP : 0), "名称框压住标题" + at);
        check(l.nameWidth() >= (l.showTitle() ? MIN_NAME_WIDTH : 60), "名称框过窄：" + l.nameWidth() + at);
        check(l.saveX() == l.nameX() + l.nameWidth() + GAP, "保存名称紧跟名称框" + at);
        check(l.reservedX() == l.saveX() + SAVE_WIDTH + GAP && RESERVED_WIDTH == SAVE_WIDTH, "预留位在保存名称右侧且同宽" + at);
        check(l.rulesX() == l.reservedX() + RESERVED_WIDTH + GAP, "规则按钮紧跟预留位" + at);
        check(l.closeX() == l.rulesX() + RULES_WIDTH + GAP && l.closeX() + CLOSE_WIDTH == right, "关闭按钮贴窗口右内边" + at);

        // Two equal columns with a divider between them, all inside the window.
        int box = l.panelX(BOX), collection = l.panelX(COLLECTION), cw = l.columnWidth();
        check(box == left && collection + cw == right && box + cw < l.dividerX() && l.dividerX() + 2 <= collection, "两栏或分隔线越界、重叠" + at);
        check(l.columnBottom() < l.statusY() && l.statusY() + 9 <= bottom, "状态行与两栏重叠或出窗口" + at);

        // Inside each column: label, toolbar, slot grid, pager, top to bottom.
        check(l.toolbarY() == l.columnTop() + LABEL_HEIGHT && l.toolbarY() + BUTTON_HEIGHT <= l.gridTop(), "标签或工具栏压住格子" + at);
        check(l.gridTop() + l.gridHeight() <= l.pagerY(), "格子压住翻页" + at);
        check(3 * l.toolButtonWidth() + 2 * GAP <= cw && l.toolButtonWidth() >= 28, "卡盒工具按钮放不下" + at);
        check(l.searchWidth() >= 40 && l.searchButtonX() == l.searchX() + l.searchWidth() + GAP && l.searchButtonX() + SEARCH_BUTTON_WIDTH == collection + cw,
            "搜索框与按钮" + at);
        check(l.pagerRightX(BOX) - (l.pagerLeftX(BOX) + PAGER_BUTTON_WIDTH) >= 30, "翻页按钮之间没有页码空间" + at);
        for (int side = BOX; side <= COLLECTION; side++)
            check(l.gridLeft(side) >= l.panelX(side) && l.gridLeft(side) + l.gridWidth() <= l.panelX(side) + cw, "格子出栏" + at);
        check(l.rulesAreaX() == box && l.rulesAreaX() + l.rulesAreaWidth() == right, "规则文字区横跨两栏" + at);

        // Slot and page size.
        int cards = l.cardsPerPage();
        check(cards >= TARGET_CARDS && cards <= MAX_CARDS, "每页张数超出范围：" + cards + at);
        check(l.cardHeight() >= MIN_CARD_HEIGHT, "卡图过小：" + l.cardHeight() + at);
        check(l.cardHeight() <= MAX_CARD_HEIGHT || l.columns() * l.rows() <= MAX_CARDS, "卡图只在超出每页上限时才放大" + at);
        check(Math.abs(l.cardWidth() - Math.round(l.cardHeight() * dev.hua.ptcglive.client.PtcgLiveTheme.CARD_ASPECT)) <= 1, "卡图比例" + at);
        check(l.slotWidth() == l.cardWidth() + 2 * SLOT_BORDER && l.slotHeight() == l.cardHeight() + 2 * SLOT_BORDER, "格子比卡多一圈凹边" + at);

        // Hit testing matches the drawn slots exactly; slots touch like a chest grid.
        for (int side = BOX; side <= COLLECTION; side++) {
            int lastIndex = cards - 1, column = lastIndex % l.columns(), row = lastIndex / l.columns();
            int x = l.cardX(side, column), y = l.cardY(row);
            check(l.cardIndexAt(side, x, y) == lastIndex && l.cardIndexAt(side, x + l.slotWidth() - 1, y + l.slotHeight() - 1) == lastIndex,
                "命中最后一格" + at);
            check(l.cardIndexAt(side, x + l.slotWidth(), y) != lastIndex, "右侧下一格不是同一格" + at);
            check(l.cardIndexAt(side, l.gridLeft(side) - 1, l.gridTop()) == -1, "格子左侧不算命中" + at);
            check(l.panelAt(x, y) == side, "格子所在栏" + at);
        }
        check(l.panelAt(l.dividerX(), l.gridTop()) == -1, "两栏中缝不属于任何一栏" + at);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
