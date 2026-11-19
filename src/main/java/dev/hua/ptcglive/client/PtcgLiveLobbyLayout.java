package dev.hua.ptcglive.client;

/**
 * Responsive geometry for the card-table lobby, drawn as a vanilla container
 * window over the dimmed world:
 * <pre>
 *   header   卡牌对战桌 · 状态 ........................ [设置][×]
 *   body     [当前规则] | 上方座位 / VS / 下方座位 | [观众]      (narrow: one info line, then the seats)
 *   footer   [对局记录][单人练习]   提示   [准备][开始对战]
 * </pre>
 * The same window hosts the settings page, and two dialogs (单人练习, 对局记录)
 * open over the dimmed lobby.  Pure arithmetic only; tests/PtcgLiveLobbyLayoutTest covers it.
 */
public record PtcgLiveLobbyLayout(int screenWidth, int screenHeight, int windowX, int windowY, int windowWidth, int windowHeight,
                                  int barY, int contentTop, int footerY, int bodyBottom,
                                  int sideWidth, int seatsX, int seatsWidth, int seatTop, int seatHeight) {
    /** Minecraft never scales the GUI below 320×240; smaller sizes are laid out as 320×240 instead of failing. */
    public static final int MIN_WIDTH = 320, MIN_HEIGHT = 240;
    public static final int PADDING = 7, BUTTON_HEIGHT = 20, GAP = 4, LABEL_HEIGHT = 11, DIVIDER = 10, VS_HEIGHT = 12;
    public static final int SETTINGS_WIDTH = 40, CLOSE_WIDTH = 20, BACK_WIDTH = 40;
    /** Side columns (rules, spectators) appear only when the window is at least this wide. */
    public static final int WIDE_WINDOW = 400, SIDE_MIN = 84, SIDE_MAX = 140;
    public static final int AVATAR_MAX = 34, DECK_BUTTON_MAX = 180, LEAVE_WIDTH = 36, SPECTATOR_ROW = 20;
    public static final int FOOTER_WIDE = 56, FOOTER_NARROW = 36, READY_WIDTH = 56, START_WIDTH = 64, MATCH_WIDTH = 64;
    /** Settings page: two option rows (赛制, 危险操作) above the footer, ruleset rows in the list. */
    public static final int OPTION_ROWS_HEIGHT = 48, LIST_ROW = 12, OPTION_LABEL_WIDTH = 48, FORMAT_WIDTH = 130, END_WIDTH = 72;
    public static final int APPLY_WIDTH = 48, CANCEL_WIDTH = 48;

    public static PtcgLiveLobbyLayout forScreen(int width, int height) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("卡桌大厅尺寸无效");
        width = Math.max(MIN_WIDTH, width); height = Math.max(MIN_HEIGHT, height);
        int margin = clamp(Math.min(width, height) / 30, 4, 32);
        int windowX = margin, windowY = margin, windowWidth = width - margin * 2, windowHeight = height - margin * 2;
        int innerLeft = windowX + PADDING, innerRight = windowX + windowWidth - PADDING, innerBottom = windowY + windowHeight - PADDING;
        int barY = windowY + PADDING, contentTop = barY + BUTTON_HEIGHT + 6;
        int footerY = innerBottom - BUTTON_HEIGHT, bodyBottom = footerY - 6;
        int sideWidth = windowWidth >= WIDE_WINDOW ? clamp((innerRight - innerLeft) * 22 / 100, SIDE_MIN, SIDE_MAX) : 0;
        int seatsX = innerLeft + (sideWidth > 0 ? sideWidth + DIVIDER : 0);
        int seatsRight = sideWidth > 0 ? innerRight - sideWidth - DIVIDER : innerRight;
        // Narrow windows replace the side columns with one info line above the seats.
        int seatTop = contentTop + (sideWidth > 0 ? 0 : LABEL_HEIGHT + 1);
        int seatHeight = (bodyBottom - seatTop - VS_HEIGHT) / 2;
        return new PtcgLiveLobbyLayout(width, height, windowX, windowY, windowWidth, windowHeight, barY, contentTop, footerY, bodyBottom,
            sideWidth, seatsX, seatsRight - seatsX, seatTop, seatHeight);
    }

    // ---- window and header ----
    public int innerLeft() { return windowX + PADDING; }
    public int innerRight() { return windowX + windowWidth - PADDING; }
    public int innerWidth() { return innerRight() - innerLeft(); }
    public int closeX() { return innerRight() - CLOSE_WIDTH; }
    public int settingsX() { return closeX() - GAP - SETTINGS_WIDTH; }
    public int backX() { return innerRight() - BACK_WIDTH; }

    // ---- side columns ----
    public boolean wide() { return sideWidth > 0; }
    public int rulesX() { return innerLeft(); }
    public int spectatorsX() { return innerRight() - sideWidth; }
    public int sideListTop() { return contentTop + LABEL_HEIGHT; }
    /** Bottom of the spectator list; leaves room for the 观战 button when it is shown. */
    public int spectatorListBottom(boolean withButton) { return withButton ? bodyBottom - BUTTON_HEIGHT - GAP : bodyBottom; }
    public int spectateButtonY() { return bodyBottom - BUTTON_HEIGHT; }

    // ---- seats ----
    /** @param index 0 = upper seat, 1 = lower seat (the viewer's own seat when seated). */
    public int seatY(int index) { return index == 0 ? seatTop : seatTop + seatHeight + VS_HEIGHT; }
    public int vsLineY() { return seatTop + seatHeight + VS_HEIGHT / 2; }
    public int avatarSize() { return Math.max(10, Math.min(AVATAR_MAX, seatHeight - 8)); }
    public int avatarX() { return seatsX + 5; }
    public int avatarY(int index) { return seatY(index) + (seatHeight - avatarSize()) / 2; }
    public int seatTextX() { return avatarX() + avatarSize() + 6; }
    public int seatTextRight() { return seatsX + seatsWidth - 6; }
    public int deckButtonY(int index) { return seatY(index) + seatHeight - 6 - BUTTON_HEIGHT; }
    public int deckButtonWidth() { return Math.min(DECK_BUTTON_MAX, seatTextRight() - seatTextX()); }
    public int leaveX() { return seatTextRight() - LEAVE_WIDTH; }
    public int joinButtonWidth() { return 72; }

    // ---- footer ----
    public int footerButtonWidth() { return wide() ? FOOTER_WIDE : FOOTER_NARROW; }
    public int historyX() { return innerLeft(); }
    public int practiceX() { return innerLeft() + footerButtonWidth() + GAP; }
    /** Left edge of the hint text, after the two footer buttons. */
    public int footerHintLeft() { return practiceX() + footerButtonWidth() + 8; }

    // ---- settings page ----
    public int optionsTop() { return footerY - 6 - OPTION_ROWS_HEIGHT; }
    public int listTop() { return contentTop + LABEL_HEIGHT; }
    public int listBottom() { return optionsTop() - 6; }
    public int listWidth() { return (innerWidth() - DIVIDER) * 55 / 100; }
    public int detailsX() { return innerLeft() + listWidth() + DIVIDER; }
    public int detailsWidth() { return innerRight() - detailsX(); }
    public int visibleListRows() { return Math.max(1, (listBottom() - listTop() - 4) / LIST_ROW); }
    public int formatWidth() { return Math.min(FORMAT_WIDTH, innerWidth() - OPTION_LABEL_WIDTH); }
    public int applyX() { return innerRight() - APPLY_WIDTH; }
    public int cancelX() { return applyX() - GAP - CANCEL_WIDTH; }

    // ---- dialogs over the lobby ----
    /** A centred dialog window: {@code widthShare}/{@code heightShare} percent of the screen, within the lobby window. */
    public record Dialog(int x, int y, int width, int height) {
        public int innerLeft() { return x + PADDING; }
        public int innerRight() { return x + width - PADDING; }
        public int innerWidth() { return innerRight() - innerLeft(); }
        public int innerTop() { return y + PADDING; }
        public int innerBottom() { return y + height - PADDING; }
        public int footerY() { return innerBottom() - BUTTON_HEIGHT; }
        public int contentTop() { return innerTop() + BUTTON_HEIGHT + 6; }
    }
    public Dialog dialog(int widthShare, int heightShare) {
        int width = Math.min(windowWidth - 16, Math.max(300, screenWidth * widthShare / 100));
        int height = Math.min(windowHeight - 12, Math.max(200, screenHeight * heightShare / 100));
        return new Dialog((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
    }
    /** 单人练习: rule line, then two deck lists side by side above a hint line and the footer. */
    public Dialog practiceDialog() { return dialog(70, 78); }
    public static final int PRACTICE_ROW = 21, RESUME_WIDTH = 84, START_PRACTICE_WIDTH = 64, PRACTICE_CANCEL_WIDTH = 48;
    public static int practiceColumnTop(Dialog dialog) { return dialog.innerTop() + 34; }
    public static int practiceColumnWidth(Dialog dialog) { return (dialog.innerWidth() - DIVIDER) / 2; }
    public static int practiceColumnX(Dialog dialog, int side) { return dialog.innerLeft() + side * (practiceColumnWidth(dialog) + DIVIDER); }
    public static int practiceHintY(Dialog dialog) { return dialog.footerY() - 13; }
    public static int practiceListTop(Dialog dialog) { return practiceColumnTop(dialog) + LABEL_HEIGHT; }
    public static int practiceListBottom(Dialog dialog) { return practiceHintY(dialog) - 4; }
    /** 对局记录: match list on the left, details and the public-action log on the right. */
    public Dialog historyDialog() { return dialog(78, 84); }
    public static final int HISTORY_ROW = 24, LOG_ROW = 10, CLOSE_DIALOG_WIDTH = 40;
    public static int historyListWidth(Dialog dialog) { return (dialog.innerWidth() - DIVIDER) * 45 / 100; }
    public static int historyDetailsX(Dialog dialog) { return dialog.innerLeft() + historyListWidth(dialog) + DIVIDER; }
    public static int historyLogTop(Dialog dialog) { return dialog.contentTop() + 32 + 3 * 10 + 14; }

    private static int clamp(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(maximum, value)); }
}
