package dev.hua.ptcglive.client;

/** Pure layout calculations shared by every portrait phone screen. */
public final class PhoneLayout {
    public record Frame(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public int innerX() { return x + 10; }
        public int innerWidth() { return width - 20; }
    }

    public static Frame forScreen(int screenWidth, int screenHeight) {
        if (screenWidth < 1 || screenHeight < 1) throw new IllegalArgumentException("手机界面尺寸无效");
        int margin = Math.min(8, Math.max(3, Math.min(screenWidth, screenHeight) / 40));
        int availableWidth = Math.max(1, screenWidth - margin * 2);
        int availableHeight = Math.max(1, screenHeight - margin * 2);
        int minimum = Math.min(280, availableWidth);
        int target = Math.max(minimum, availableHeight * 9 / 16);
        int width = Math.min(availableWidth, Math.min(420, target));
        return new Frame((screenWidth - width) / 2, margin, width, availableHeight);
    }

    private PhoneLayout() { }
}
