package dev.hua.ptcglive.client;

import dev.hua.ptcglive.collection.BoxEditorSession;

/**
 * Responsive geometry for the card-box editor, drawn as a vanilla container
 * window (like a chest or furnace) over the dimmed world: the box on the left,
 * the owner's unboxed collection on the right.  Cards sit in sunken slots that
 * touch like a chest grid; both columns share one slot size, so the server can
 * page the collection by exactly one grid.  Pure arithmetic only;
 * tests/PtcgLiveCollectionLayoutTest covers it.
 */
public record PtcgLiveCollectionLayout(int screenWidth, int screenHeight,
                                       int windowX, int windowY, int windowWidth, int windowHeight,
                                       int barY, boolean showTitle, int nameX, int nameWidth, int saveX, int reservedX, int rulesX, int closeX,
                                       int columnTop, int columnWidth, int toolbarY, int gridTop, int pagerY, int statusY,
                                       int columns, int rows, int cardWidth, int cardHeight) {
    public static final int BOX = 0, COLLECTION = 1;
    /** Minecraft never scales the GUI below 320×240; smaller sizes are laid out as 320×240 instead of failing. */
    public static final int MIN_WIDTH = 320, MIN_HEIGHT = 240;
    /** Inner padding of the window, as in vanilla container textures. */
    public static final int PADDING = 7;
    public static final int BUTTON_HEIGHT = 20, GAP = 4, LABEL_HEIGHT = 11, DIVIDER = 10;
    public static final int TITLE_WIDTH = 52, SAVE_WIDTH = 56, RULES_WIDTH = 40, CLOSE_WIDTH = 20;
    /** Free top-bar slot between “保存名称” and “规则”, reserved for a future button; it is taken from the name field. */
    public static final int RESERVED_WIDTH = SAVE_WIDTH;
    /** The title is dropped before the name field gets narrower than this. */
    public static final int MIN_NAME_WIDTH = 80;
    public static final int SEARCH_BUTTON_WIDTH = 40, PAGER_BUTTON_WIDTH = 24;
    /** One-pixel sunken bevel around each card; slots touch each other like a chest grid. */
    public static final int SLOT_BORDER = 1;
    /** Cards stay between these heights unless the screen is too small, or a huge GUI would exceed the page cap. */
    public static final int MIN_CARD_HEIGHT = 48, MAX_CARD_HEIGHT = 116;
    /** Rows are sized around this card height, so a larger GUI shows more cards rather than bigger ones. */
    public static final int PREFERRED_CARD_HEIGHT = 68;
    /** A cramped screen shrinks the cards (down to MIN_CARD_HEIGHT) until a column shows at least this many. */
    public static final int TARGET_CARDS = 6;
    public static final int MAX_CARDS = BoxEditorSession.MAX_PAGE_SIZE;

    public static PtcgLiveCollectionLayout forScreen(int width, int height) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("卡盒界面尺寸无效");
        width = Math.max(MIN_WIDTH, width); height = Math.max(MIN_HEIGHT, height);
        int margin = clamp(Math.min(width, height) / 30, 4, 32);
        int windowX = margin, windowY = margin, windowWidth = width - margin * 2, windowHeight = height - margin * 2;
        int innerLeft = windowX + PADDING, innerRight = windowX + windowWidth - PADDING;

        // Top bar, right to left: × · 规则 · (reserved) · 保存名称 · name field · title.
        int barY = windowY + PADDING;
        int closeX = innerRight - CLOSE_WIDTH;
        int rulesX = closeX - GAP - RULES_WIDTH;
        int reservedX = rulesX - GAP - RESERVED_WIDTH;
        int saveX = reservedX - GAP - SAVE_WIDTH;
        boolean showTitle = saveX - GAP - (innerLeft + TITLE_WIDTH + GAP) >= MIN_NAME_WIDTH;
        int nameX = innerLeft + (showTitle ? TITLE_WIDTH + GAP : 0);
        int nameWidth = saveX - GAP - nameX;

        int columnTop = barY + BUTTON_HEIGHT + 6;
        int columnWidth = (windowWidth - PADDING * 2 - DIVIDER) / 2;
        int toolbarY = columnTop + LABEL_HEIGHT;
        int gridTop = toolbarY + BUTTON_HEIGHT + GAP;
        int statusY = windowY + windowHeight - PADDING - 9;
        int pagerY = statusY - GAP - BUTTON_HEIGHT;
        int gridHeight = pagerY - GAP - gridTop;

        int cardHeight = chooseCardHeight(columnWidth, gridHeight);
        int cardWidth = Math.min(columnWidth - SLOT_BORDER * 2, cardWidth(cardHeight));
        int columns = Math.max(1, columnWidth / (cardWidth + SLOT_BORDER * 2));
        int rows = Math.max(1, gridHeight / (cardHeight + SLOT_BORDER * 2));
        return new PtcgLiveCollectionLayout(width, height, windowX, windowY, windowWidth, windowHeight,
            barY, showTitle, nameX, nameWidth, saveX, reservedX, rulesX, closeX,
            columnTop, columnWidth, toolbarY, gridTop, pagerY, statusY, columns, rows, cardWidth, cardHeight);
    }

    private static int chooseCardHeight(int columnWidth, int gridHeight) {
        int border = SLOT_BORDER * 2, tallest = Math.max(1, gridHeight - border);
        // 1) As many rows of about PREFERRED_CARD_HEIGHT as fit, each row stretched to fill the height.
        int rows = Math.max(1, gridHeight / (PREFERRED_CARD_HEIGHT + border));
        int chosen = Math.max(1, Math.min(MAX_CARD_HEIGHT, gridHeight / rows - border));
        // 2) Too few cards on a cramped screen: add rows while the cards stay readable.
        while (capacity(columnWidth, gridHeight, chosen) < TARGET_CARDS) {
            int smaller = gridHeight / ++rows - border;
            if (smaller < MIN_CARD_HEIGHT) break;
            chosen = smaller;
        }
        // 3) A huge GUI: grow the cards until one page is within the server's page cap.
        while (capacity(columnWidth, gridHeight, chosen) > MAX_CARDS && chosen < tallest) chosen++;
        return chosen;
    }
    private static int cardWidth(int cardHeight) { return Math.max(1, Math.round(cardHeight * PtcgLiveTheme.CARD_ASPECT)); }
    private static int capacity(int columnWidth, int gridHeight, int cardHeight) {
        return columnWidth / (cardWidth(cardHeight) + SLOT_BORDER * 2) * (gridHeight / (cardHeight + SLOT_BORDER * 2));
    }

    /** Cards per page on either side; the collection page size sent to the server. */
    public int cardsPerPage() { return Math.max(1, Math.min(MAX_CARDS, columns * rows)); }
    public int slotWidth() { return cardWidth + SLOT_BORDER * 2; }
    public int slotHeight() { return cardHeight + SLOT_BORDER * 2; }
    public int panelX(int side) { return side == BOX ? windowX + PADDING : windowX + windowWidth - PADDING - columnWidth; }
    /** The grooved divider between the two columns. */
    public int dividerX() { return panelX(BOX) + columnWidth + (panelX(COLLECTION) - panelX(BOX) - columnWidth) / 2 - 1; }
    /** Bottom edge of both columns (the pager row's bottom). */
    public int columnBottom() { return pagerY + BUTTON_HEIGHT; }
    public int gridLeft(int side) { return panelX(side) + (columnWidth - columns * slotWidth()) / 2; }
    public int gridWidth() { return columns * slotWidth(); }
    public int gridHeight() { return rows * slotHeight(); }
    /** Top-left corner of a slot; the card itself starts SLOT_BORDER inside it. */
    public int cardX(int side, int column) { return gridLeft(side) + column * slotWidth(); }
    public int cardY(int row) { return gridTop + row * slotHeight(); }
    /** Three equal toolbar buttons in the box column. */
    public int toolButtonWidth() { return (columnWidth - GAP * 2) / 3; }
    public int searchX() { return panelX(COLLECTION); }
    public int searchWidth() { return columnWidth - GAP - SEARCH_BUTTON_WIDTH; }
    public int searchButtonX() { return panelX(COLLECTION) + columnWidth - SEARCH_BUTTON_WIDTH; }
    public int pagerLeftX(int side) { return panelX(side); }
    public int pagerRightX(int side) { return panelX(side) + columnWidth - PAGER_BUTTON_WIDTH; }
    /** The rules-check text area replaces both grids and spans the two columns. */
    public int rulesAreaX() { return panelX(BOX); }
    public int rulesAreaWidth() { return panelX(COLLECTION) + columnWidth - panelX(BOX); }

    /** Which column the point is in, or -1. */
    public int panelAt(double x, double y) {
        if (y < columnTop || y >= columnBottom()) return -1;
        for (int side = BOX; side <= COLLECTION; side++) if (x >= panelX(side) && x < panelX(side) + columnWidth) return side;
        return -1;
    }
    /** Index of the slot under the point within one page, or -1. */
    public int cardIndexAt(int side, double x, double y) {
        double relativeX = x - gridLeft(side), relativeY = y - gridTop;
        if (relativeX < 0 || relativeY < 0) return -1;
        int column = (int) (relativeX / slotWidth()), row = (int) (relativeY / slotHeight());
        if (column >= columns || row >= rows) return -1;
        int index = row * columns + column;
        return index < cardsPerPage() ? index : -1;
    }
    private static int clamp(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(maximum, value)); }
}
