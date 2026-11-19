package dev.hua.ptcglive.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Responsive geometry for the pack-opening screen, drawn as a vanilla
 * container window over the dimmed world.  Every element owns its own band so
 * nothing overlaps at any GUI size:
 * <pre>
 *   header   title ........................ [取消][确认抽取]  (results: [完成])
 *   content  [pack art + info] | 卡池 N 张 ............ 第 p / P 页
 *                              | pool slots (touching, rarity ring)
 *                              | [<]   滚轮翻页 · 右键看原图   [>]
 *   status   server message
 * </pre>
 * The results phase replaces the info column and pool with one row of large
 * slots plus a name and rarity line under each card.  Pure arithmetic only;
 * tests/PackGalleryLayoutTest covers it.
 */
public record PackGalleryLayout(int screenWidth, int screenHeight,
                                int windowX, int windowY, int windowWidth, int windowHeight,
                                int barY, int contentTop, int pagerY, int statusY,
                                int infoWidth, int galleryX, int galleryWidth, int gridTop,
                                int columns, int rows, int cardWidth, int cardHeight, int totalCards) {
    /** Minecraft never scales the GUI below 320×240; smaller sizes are laid out as 320×240 instead of failing. */
    public static final int MIN_WIDTH = 320, MIN_HEIGHT = 240;
    public static final int RESULTS_PER_PAGE = 5;
    public static int resultPages(int count) { return Math.max(1, (count + RESULTS_PER_PAGE - 1) / RESULTS_PER_PAGE); }
    public static final int PADDING = 7, BUTTON_HEIGHT = 20, GAP = 4, LABEL_HEIGHT = 20, DIVIDER = 10;
    public static final int CONFIRM_WIDTH = 64, CANCEL_WIDTH = 40, DONE_WIDTH = 48, PAGER_BUTTON_WIDTH = 24;
    /** The pack info column is shown only when the window is at least this wide. */
    public static final int INFO_MIN_WINDOW = 400, INFO_MIN_WIDTH = 88, INFO_MAX_WIDTH = 120;
    /** Text lines reserved under the pack art in the info column. */
    public static final int INFO_LINES = 6, INFO_LINE_HEIGHT = 10;
    /** Sunken bevel (1 px) plus the rarity ring (1 px) on every side of a card. */
    public static final int SLOT_BORDER = 2;
    /** Pool rows are sized around this slot height; cards never grow taller than MAX_CARD_HEIGHT. */
    public static final int PREFERRED_SLOT_HEIGHT = 50, MAX_CARD_HEIGHT = 92, MIN_CARD_HEIGHT = 20;
    /** Results: two label lines (name, rarity) under each card, and an upper bound for the big cards. */
    public static final int RESULT_LABELS = 22, MAX_RESULT_CARD_HEIGHT = 180;
    public static final int FILTER_HEIGHT = 16, FILTER_GAP = 2;

    public record FilterSlot(int x, int y, int width) { }

    /** Stable button widths keep toggling a rarity from moving neighbouring controls. */
    private static List<FilterSlot> filterSlots(int left, int top, int width, List<Integer> buttonWidths) {
        var slots = new ArrayList<FilterSlot>();
        int x = left, y = top;
        for (int requested : buttonWidths) {
            int w = Math.max(1, Math.min(width, requested));
            if (x > left && x + w > left + width) { x = left; y += FILTER_HEIGHT + FILTER_GAP; }
            slots.add(new FilterSlot(x, y, w));
            x += w + FILTER_GAP;
        }
        return List.copyOf(slots);
    }

    public List<FilterSlot> filterSlots(List<Integer> buttonWidths) {
        return filterSlots(galleryX, contentTop + LABEL_HEIGHT, galleryWidth, buttonWidths);
    }

    public static PackGalleryLayout forScreen(int width, int height, int totalCards) {
        return forScreen(width, height, totalCards, List.of());
    }

    public static PackGalleryLayout forScreen(int width, int height, int totalCards, List<Integer> filterWidths) {
        if (width < 1 || height < 1 || totalCards < 0) throw new IllegalArgumentException("卡包画廊尺寸无效");
        width = Math.max(MIN_WIDTH, width); height = Math.max(MIN_HEIGHT, height);
        int margin = clamp(Math.min(width, height) / 30, 4, 32);
        int windowX = margin, windowY = margin, windowWidth = width - margin * 2, windowHeight = height - margin * 2;
        int innerLeft = windowX + PADDING, innerRight = windowX + windowWidth - PADDING;
        int barY = windowY + PADDING;
        int contentTop = barY + BUTTON_HEIGHT + 6;
        int statusY = windowY + windowHeight - PADDING - 9;
        int pagerY = statusY - GAP - BUTTON_HEIGHT;

        int infoWidth = windowWidth >= INFO_MIN_WINDOW ? clamp(windowWidth / 5, INFO_MIN_WIDTH, INFO_MAX_WIDTH) : 0;
        int galleryX = innerLeft + (infoWidth > 0 ? infoWidth + DIVIDER : 0);
        int galleryWidth = innerRight - galleryX;
        int gridTop = contentTop + LABEL_HEIGHT;
        var filters = filterSlots(galleryX, gridTop, galleryWidth, filterWidths);
        if (!filters.isEmpty()) gridTop = filters.getLast().y() + FILTER_HEIGHT + GAP;
        int gridHeight = pagerY - GAP - gridTop;

        int rows = Math.max(1, Math.round(gridHeight / (float) PREFERRED_SLOT_HEIGHT));
        int cardHeight = Math.min(MAX_CARD_HEIGHT, gridHeight / rows - SLOT_BORDER * 2);
        while (cardHeight < MIN_CARD_HEIGHT && rows > 1) cardHeight = Math.min(MAX_CARD_HEIGHT, gridHeight / --rows - SLOT_BORDER * 2);
        cardHeight = Math.max(1, cardHeight);
        int cardWidth = Math.max(1, Math.min(galleryWidth - SLOT_BORDER * 2, Math.round(cardHeight * PtcgLiveTheme.CARD_ASPECT)));
        int columns = Math.max(1, galleryWidth / (cardWidth + SLOT_BORDER * 2));
        return new PackGalleryLayout(width, height, windowX, windowY, windowWidth, windowHeight, barY, contentTop, pagerY, statusY,
            infoWidth, galleryX, galleryWidth, gridTop, columns, rows, cardWidth, cardHeight, totalCards);
    }

    // ---- window and header ----
    public int innerLeft() { return windowX + PADDING; }
    public int innerRight() { return windowX + windowWidth - PADDING; }
    public int innerWidth() { return innerRight() - innerLeft(); }
    public int confirmX() { return innerRight() - CONFIRM_WIDTH; }
    public int cancelX() { return confirmX() - GAP - CANCEL_WIDTH; }
    public int doneX() { return innerRight() - DONE_WIDTH; }
    /** Width available for the title left of the header buttons. */
    public int titleWidth(boolean results) { return (results ? doneX() : cancelX()) - 8 - innerLeft(); }
    /** Bottom of the content area above the status line. */
    public int columnBottom() { return pagerY + BUTTON_HEIGHT; }

    // ---- pack info column ----
    public boolean showInfo() { return infoWidth > 0; }
    public int dividerX() { return innerLeft() + infoWidth + DIVIDER / 2 - 1; }
    /** Side of the square sunken slot holding the pack art, leaving room for the info lines below it. */
    public int artSize() {
        int room = columnBottom() - contentTop - INFO_LINES * INFO_LINE_HEIGHT - 6;
        return Math.max(32, Math.min(infoWidth, room));
    }
    public int artX() { return innerLeft() + (infoWidth - artSize()) / 2; }
    public int infoTextTop() { return contentTop + artSize() + 5; }

    // ---- pool grid and pager ----
    public int slotWidth() { return cardWidth + SLOT_BORDER * 2; }
    public int slotHeight() { return cardHeight + SLOT_BORDER * 2; }
    public int gridLeft() { return galleryX + (galleryWidth - columns * slotWidth()) / 2; }
    public int gridWidth() { return columns * slotWidth(); }
    public int gridHeight() { return rows * slotHeight(); }
    public int slotX(int column) { return gridLeft() + column * slotWidth(); }
    public int slotY(int row) { return gridTop + row * slotHeight(); }
    public int pagerLeftX() { return galleryX; }
    public int pagerRightX() { return galleryX + galleryWidth - PAGER_BUTTON_WIDTH; }
    public int capacity() { return columns * rows; }
    public int pages() { return Math.max(1, (totalCards + capacity() - 1) / capacity()); }
    public int pageStart(int page) {
        if (page < 0 || page >= pages()) throw new IllegalArgumentException("卡包画廊页码无效");
        return page * capacity();
    }
    public int pageEndExclusive(int page) { return Math.min(totalCards, pageStart(page) + capacity()); }
    /** Slot index within one page under the point, or -1. */
    public int slotIndexAt(double x, double y) {
        double relativeX = x - gridLeft(), relativeY = y - gridTop;
        if (relativeX < 0 || relativeY < 0) return -1;
        int column = (int) (relativeX / slotWidth()), row = (int) (relativeY / slotHeight());
        return column < columns && row < rows ? row * columns + column : -1;
    }

    // ---- results row ----
    /** One centred row of large result slots with two label lines under each card. */
    public record ResultRow(int left, int top, int gap, int cardWidth, int cardHeight, int count) {
        public int slotWidth() { return cardWidth + SLOT_BORDER * 2; }
        public int slotHeight() { return cardHeight + SLOT_BORDER * 2; }
        public int slotX(int index) { return left + index * (slotWidth() + gap); }
        public int labelY() { return top + slotHeight() + 3; }
        public int width() { return count * slotWidth() + Math.max(0, count - 1) * gap; }
        /** Index of the result slot under the point, or -1. */
        public int indexAt(double x, double y) {
            if (y < top || y >= top + slotHeight()) return -1;
            for (int i = 0; i < count; i++) if (x >= slotX(i) && x < slotX(i) + slotWidth()) return i;
            return -1;
        }
    }
    public ResultRow resultRow(int count) {
        int n = Math.max(1, count), gap = clamp(windowWidth / 60, 4, 12);
        // Configurable packs may contain several pages; reserve the same pager band.
        int top = contentTop, bottom = pagerY - GAP;
        int widest = (innerWidth() - (n - 1) * gap) / n - SLOT_BORDER * 2;
        int tallest = bottom - top - RESULT_LABELS - SLOT_BORDER * 2;
        int cardHeight = Math.max(1, Math.min(MAX_RESULT_CARD_HEIGHT, Math.min(tallest, (int) (widest / PtcgLiveTheme.CARD_ASPECT))));
        int cardWidth = Math.max(1, Math.min(widest, Math.round(cardHeight * PtcgLiveTheme.CARD_ASPECT)));
        int rowWidth = n * (cardWidth + SLOT_BORDER * 2) + (n - 1) * gap;
        int left = innerLeft() + (innerWidth() - rowWidth) / 2;
        int slack = bottom - top - (cardHeight + SLOT_BORDER * 2) - RESULT_LABELS;
        return new ResultRow(left, top + Math.max(0, slack / 2), gap, cardWidth, cardHeight, n);
    }
    private static int clamp(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(maximum, value)); }
}
