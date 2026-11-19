package dev.hua.ptcglive.client;

/** Pure layout for server-issued attack choices; every choice stays reachable at small GUI scales. */
public record CardActionLayout(int x, int width, int top, int page, int pageSize, int pages, int count) {
    public static CardActionLayout forScreen(int width, int height, int count, int requestedPage) {
        int column = Math.min(190, Math.max(118, width / 3));
        int room = Math.max(24, height - 34 - 50);
        int pageSize = Math.max(1, room / 24);
        if (count > pageSize) pageSize = Math.max(1, (room - 26) / 24);
        int pages = Math.max(1, (count + pageSize - 1) / pageSize);
        return new CardActionLayout(width - column - 6, column, 34,
            Math.clamp(requestedPage, 0, pages - 1), pageSize, pages, count);
    }
    public int first() { return page * pageSize; }
    public int end() { return Math.min(count, first() + pageSize); }
    public int rowY(int index) { return top + (index - first()) * 24; }
    public int navigationY() { return top + pageSize * 24; }
}
