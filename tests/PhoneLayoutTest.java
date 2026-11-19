import dev.hua.ptcglive.client.PhoneLayout;

/** The portrait frame stays usable and fully visible at common Minecraft GUI sizes. */
public final class PhoneLayoutTest {
    public static void main(String[] args) {
        for (int[] size : new int[][]{{320, 240}, {427, 240}, {480, 270}, {640, 360}, {854, 480}, {1100, 760}, {1920, 1080}, {3840, 2160}}) {
            var frame = PhoneLayout.forScreen(size[0], size[1]);
            check(frame.x() >= 0 && frame.y() >= 0, "手机不能越过左上边界");
            check(frame.right() <= size[0] && frame.bottom() <= size[1], "手机不能越过右下边界");
            check(frame.width() > 0 && frame.height() > 0 && frame.innerWidth() > 0, "手机和内容区必须可用");
            check(frame.width() <= 420, "宽屏也应保持竖屏手机宽度");
            if (size[0] >= 296) check(frame.width() >= 280, "常见屏幕至少保留 280 像素操作宽度");
        }
        rejects(() -> PhoneLayout.forScreen(0, 240));
        rejects(() -> PhoneLayout.forScreen(320, 0));
        System.out.println("PhoneLayoutTest passed: portrait frame remains inside 8 common GUI sizes");
    }
    private static void rejects(Runnable action) { try { action.run(); throw new AssertionError("无效尺寸应拒绝"); } catch (IllegalArgumentException expected) { } }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
