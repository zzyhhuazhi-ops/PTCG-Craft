package dev.hua.ptcglive.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Titanium phone: reflective hardware and quiet, high-contrast content surfaces. */
public final class PhoneUi {
    public static final int CANVAS = 0xff1b1f25;
    public static final int SURFACE = 0xff292e35;
    public static final int SURFACE_ALT = 0xff353c45;
    public static final int BORDER = 0xff505963;
    public static final int INK = 0xffedf0f4;
    public static final int MUTED = 0xffb2bbc6;
    public static final int BLUE = 0xff86bbff;
    public static final int RED = 0xfff39b9f;
    public static final int GREEN = 0xffa8cabd;
    public static final int ORANGE = 0xffd8bd99;
    public static final int PURPLE = 0xffbabdce;
    public static final int GOLD = 0xffd4d9df;
    public static final int FRAME = 0xff111419;
    public static final int ERROR = RED;
    public static final int SELECTED = 0xff30445d;

    private static net.minecraft.util.Identifier texture(String name) {
        return net.minecraft.util.Identifier.of("ptcglive", "textures/gui/phone/" + name + ".png");
    }

    /** Nine-slice preserves bevels at every GUI scale and window aspect ratio. */
    static void metal(DrawContext context, String name, int x, int y, int w, int h, int corner) {
        int d = Math.max(1, Math.min(corner, Math.min(w, h) / 2));
        int s = name.equals("shell") ? 48 : 40;
        int[] source = {0, s, 256 - s, 256};
        int[] xx = {x, x + d, x + w - d, x + w};
        int[] yy = {y, y + d, y + h - d, y + h};
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            if (xx[col + 1] <= xx[col] || yy[row + 1] <= yy[row]) continue;
            context.drawTexture(texture(name), xx[col], yy[row], xx[col + 1] - xx[col], yy[row + 1] - yy[row],
                source[col], source[row], source[col + 1] - source[col], source[row + 1] - source[row], 256, 256);
        }
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    static void icon(DrawContext context, String name, int x, int y, int size) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        context.drawTexture(texture("icon-" + name), x, y, size, size, 0, 0, 256, 256, 256, 256);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    public static void shell(DrawContext context, PhoneLayout.Frame frame) {
        context.fill(0, 0, frame.right() + frame.x(), frame.bottom() + frame.y(), 0x960a0c10);
        metal(context, "shell", frame.x(), frame.y(), frame.width(), frame.height(), 14);
        int cx = frame.x() + frame.width() / 2;
        context.fill(cx - 13, frame.y() + 5, cx + 13, frame.y() + 6, 0xff080a0e);
        context.fill(cx - 12, frame.y() + 6, cx + 12, frame.y() + 7, 0xff41464d);
    }

    public static void header(DrawContext context, TextRenderer text, PhoneLayout.Frame frame, String title, String subtitle) {
        int x = frame.innerX(), y = frame.y() + 13;
        context.drawText(text, text.trimToWidth(title, frame.innerWidth() - 50), x, y, INK, false);
        if (subtitle != null && !subtitle.isBlank())
            context.drawText(text, text.trimToWidth(subtitle, frame.innerWidth() - 8), x, y + 11, MUTED, false);
        context.fill(x, frame.y() + 36, frame.right() - 10, frame.y() + 37, BORDER);
    }

    public static void card(DrawContext context, int x, int y, int width, int height, int fill) {
        panel(context, x, y, width, height, BORDER);
        panel(context, x + 1, y + 1, width - 2, height - 2, fill);
    }

    /** Pixel-safe rounded rectangle; two-pixel clipped corners remain crisp at every GUI scale. */
    public static void panel(DrawContext context, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        if (width < 5 || height < 5) { context.fill(x, y, x + width, y + height, color); return; }
        context.fill(x + 2, y, x + width - 2, y + height, color);
        context.fill(x, y + 2, x + width, y + height - 2, color);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, color);
    }

    public static int hover(int color) {
        int r = Math.min(255, ((color >> 16) & 255) + 18);
        int g = Math.min(255, ((color >> 8) & 255) + 18);
        int b = Math.min(255, (color & 255) + 18);
        return (color & 0xff000000) | r << 16 | g << 8 | b;
    }

    private PhoneUi() { }
}
