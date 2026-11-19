import dev.hua.ptcglive.client.PtcgLiveLobbyLayout;
import static dev.hua.ptcglive.client.PtcgLiveLobbyLayout.*;

/** Card-table lobby window, settings page and both dialogs: everything stays inside and nothing overlaps at any GUI size. */
public final class PtcgLiveLobbyLayoutTest {
    public static void main(String[] args) {
        int[][] sizes = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {854, 480}, {960, 540}, {1100, 760}, {1920, 1080}};
        for (int[] size : sizes) verify(size[0], size[1]);
        var tiny = PtcgLiveLobbyLayout.forScreen(200, 150);
        check(tiny.screenWidth() == MIN_WIDTH && tiny.screenHeight() == MIN_HEIGHT, "过小界面按最小尺寸布局");
        try { PtcgLiveLobbyLayout.forScreen(0, 240); throw new AssertionError("零尺寸应明确拒绝"); }
        catch (IllegalArgumentException expected) { }
        check(!PtcgLiveLobbyLayout.forScreen(320, 240).wide(), "窄窗口收起规则栏和观众栏");
        check(PtcgLiveLobbyLayout.forScreen(480, 270).wide(), "常见尺寸显示规则栏和观众栏");
        System.out.println("PtcgLiveLobbyLayoutTest passed: lobby seats, side columns, footer, settings and both dialogs stay apart at every GUI size");
    }

    private static void verify(int width, int height) {
        var l = PtcgLiveLobbyLayout.forScreen(width, height);
        String at = " @" + width + "x" + height;
        int left = l.innerLeft(), right = l.innerRight(), bottom = l.windowY() + l.windowHeight() - PADDING;
        check(l.windowX() >= 0 && l.windowY() >= 0 && l.windowX() + l.windowWidth() <= width && l.windowY() + l.windowHeight() <= height, "窗口出屏" + at);

        // Header: title space, then 设置 · × flush right.
        check(l.settingsX() + SETTINGS_WIDTH + GAP == l.closeX() && l.closeX() + CLOSE_WIDTH == right, "设置与关闭按钮" + at);
        check(l.settingsX() - 8 - left >= 80, "标题空间过窄" + at);
        check(l.barY() + BUTTON_HEIGHT < l.contentTop(), "顶栏压住内容" + at);

        // Body: [rules] | seats | [spectators], above the footer.
        check(l.bodyBottom() + 6 == l.footerY() && l.footerY() + BUTTON_HEIGHT == bottom, "底栏位置" + at);
        if (l.wide()) {
            check(l.sideWidth() >= SIDE_MIN && l.sideWidth() <= SIDE_MAX, "侧栏宽度" + at);
            check(l.rulesX() + l.sideWidth() + DIVIDER <= l.seatsX(), "规则栏压住座位" + at);
            check(l.seatsX() + l.seatsWidth() + DIVIDER <= l.spectatorsX() && l.spectatorsX() + l.sideWidth() == right, "观众栏压住座位或出窗口" + at);
            check(l.spectatorListBottom(true) + GAP == l.spectateButtonY() && l.spectateButtonY() + BUTTON_HEIGHT == l.bodyBottom(), "观战按钮" + at);
            check(l.sideListTop() == l.contentTop() + LABEL_HEIGHT && l.seatTop() == l.contentTop(), "侧栏标题行" + at);
        } else {
            check(l.seatsX() == left && l.seatsX() + l.seatsWidth() == right, "窄屏座位占满宽度" + at);
            check(l.seatTop() >= l.contentTop() + LABEL_HEIGHT, "窄屏信息行压住座位" + at);
        }

        // Seats: upper, VS gap, lower; avatar, name and deck button inside each seat.
        check(l.seatHeight() >= 40, "座位过矮：" + l.seatHeight() + at);
        check(l.seatY(0) + l.seatHeight() < l.vsLineY() - 4 && l.vsLineY() + 4 < l.seatY(1), "VS 分隔压住座位" + at);
        check(l.seatY(1) + l.seatHeight() <= l.bodyBottom(), "下方座位压住底栏" + at);
        for (int index = 0; index < 2; index++) {
            int seatTop = l.seatY(index), seatBottom = seatTop + l.seatHeight();
            check(l.avatarY(index) >= seatTop + 3 && l.avatarY(index) + l.avatarSize() <= seatBottom - 3, "头像出座位" + at);
            check(l.avatarX() + l.avatarSize() < l.seatTextX() && l.seatTextX() < l.seatTextRight(), "头像压住名字" + at);
            check(l.deckButtonY(index) >= seatTop + 28 && l.deckButtonY(index) + BUTTON_HEIGHT <= seatBottom - 4, "卡组按钮压住名字或出座位" + at);
            check(l.seatTextX() + l.deckButtonWidth() <= l.seatTextRight() && l.deckButtonWidth() >= 80, "卡组按钮宽度" + at);
            check(l.leaveX() > l.seatTextX() + 40 && l.leaveX() + LEAVE_WIDTH == l.seatTextRight(), "离开按钮" + at);
            check(l.joinButtonWidth() <= l.seatsWidth() - 10, "加入按钮放不下" + at);
        }

        // Footer: 对局记录 · 单人练习 on the left, room for the hint before up to two action buttons.
        check(l.practiceX() == l.historyX() + l.footerButtonWidth() + GAP, "底栏左侧按钮" + at);
        int actions = READY_WIDTH + GAP + START_WIDTH;
        check(right - actions - 4 - l.footerHintLeft() >= 30, "底栏提示没有空间" + at);

        // Settings page: list | details above two option rows, then the footer.
        check(l.listTop() == l.contentTop() + LABEL_HEIGHT && l.listBottom() + 6 == l.optionsTop(), "设置页列表位置" + at);
        check(l.visibleListRows() >= 4, "规则包列表至少显示 4 行" + at);
        check(left + l.listWidth() + DIVIDER == l.detailsX() && l.detailsX() + l.detailsWidth() == right && l.detailsWidth() >= 80, "列表与说明" + at);
        check(l.optionsTop() + OPTION_ROWS_HEIGHT + 6 == l.footerY(), "选项行与底栏" + at);
        check(left + OPTION_LABEL_WIDTH + l.formatWidth() <= right && left + OPTION_LABEL_WIDTH + END_WIDTH <= right, "选项按钮出窗口" + at);
        check(l.cancelX() + CANCEL_WIDTH + GAP == l.applyX() && l.applyX() + APPLY_WIDTH == right && l.cancelX() - 6 - left >= 60, "取消与应用" + at);

        // Dialogs: inside the lobby window, with their own bands.
        for (var dialog : new Dialog[]{l.practiceDialog(), l.historyDialog()}) {
            check(dialog.x() >= l.windowX() && dialog.y() >= l.windowY() && dialog.x() + dialog.width() <= l.windowX() + l.windowWidth()
                && dialog.y() + dialog.height() <= l.windowY() + l.windowHeight(), "弹窗出大厅窗口" + at);
            check(dialog.contentTop() < dialog.footerY(), "弹窗没有内容区" + at);
        }
        var practice = l.practiceDialog();
        int columnWidth = practiceColumnWidth(practice);
        check(practiceColumnX(practice, 0) + columnWidth + DIVIDER == practiceColumnX(practice, 1)
            && practiceColumnX(practice, 1) + columnWidth <= practice.innerRight() && columnWidth >= 100, "练习弹窗两列" + at);
        check(practiceListBottom(practice) - practiceListTop(practice) >= 2 * PRACTICE_ROW + 4, "练习弹窗至少显示两副卡组" + at);
        check(practiceHintY(practice) + 9 < practice.footerY() && practiceListBottom(practice) < practiceHintY(practice), "练习弹窗提示行" + at);
        check(RESUME_WIDTH + GAP + PRACTICE_CANCEL_WIDTH + GAP + START_PRACTICE_WIDTH <= practice.innerWidth(), "练习弹窗按钮放不下" + at);
        var history = l.historyDialog();
        check(historyDetailsX(history) - history.innerLeft() - historyListWidth(history) == DIVIDER && history.innerRight() - historyDetailsX(history) >= 120, "记录弹窗两栏" + at);
        check(historyLogTop(history) + 3 * LOG_ROW <= history.innerBottom(), "记录弹窗动作列表至少 3 行" + at);
        check(history.contentTop() + 3 * HISTORY_ROW <= history.innerBottom(), "记录弹窗至少显示 3 场" + at);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
