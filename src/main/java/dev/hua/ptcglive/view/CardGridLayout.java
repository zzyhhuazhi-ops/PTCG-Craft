package dev.hua.ptcglive.view;

/** Testable GUI geometry; no Minecraft classes or hidden card state. */
public record CardGridLayout(int columns, int rows, int cellWidth, int cellHeight, int left, int top) {
    public record Cell(int x, int y, int width, int height) { }
    public static CardGridLayout forScreen(int width, int height) {
        int columns = Math.max(1, Math.min(8, (width - 20) / 88));
        int rows = Math.max(1, Math.min(4, (height - 112) / 108));
        return new CardGridLayout(columns, rows, Math.max(1, (width - 20) / columns), 108, 10, 64);
    }
    public int capacity() { return columns * rows; }
    public Cell cell(int index) {
        if (index < 0 || index >= capacity()) throw new IllegalArgumentException("网格位置超出当前页");
        return new Cell(left + (index % columns) * cellWidth, top + (index / columns) * cellHeight, cellWidth, cellHeight);
    }
    public int hit(double x, double y) {
        if (x < left || y < top || x >= left + columns * cellWidth || y >= top + rows * cellHeight) return -1;
        return (int) ((y - top) / cellHeight) * columns + (int) ((x - left) / cellWidth);
    }
}
