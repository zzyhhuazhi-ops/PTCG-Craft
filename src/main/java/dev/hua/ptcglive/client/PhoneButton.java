package dev.hua.ptcglive.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Metal controls with native narration/focus and release-to-activate pointer feedback. */
final class PhoneButton extends ButtonWidget {
    private final String subtitle;
    private final int color;
    private String appIcon = "";
    private boolean held;

    PhoneButton appIcon(String icon) { this.appIcon = icon; return this; }

    @Override public void onClick(double mouseX, double mouseY) { held = true; }
    @Override public void onRelease(double mouseX, double mouseY) {
        boolean activate = held && active && isMouseOver(mouseX, mouseY);
        held = false;
        if (activate) onPress();
    }

    PhoneButton(int x, int y, int width, int height, String label, int color, boolean lightText, PressAction action) {
        this(x, y, width, height, label, "", color, lightText, action);
    }

    PhoneButton(int x, int y, int width, int height, String label, String subtitle, int color, boolean lightText, PressAction action) {
        super(x, y, width, height, Text.literal(label), action, DEFAULT_NARRATION_SUPPLIER);
        this.subtitle = subtitle == null ? "" : subtitle;
        this.color = color;
    }

    @Override protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        if (held && org.lwjgl.glfw.GLFW.glfwGetMouseButton(MinecraftClient.getInstance().getWindow().getHandle(),
            org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) != org.lwjgl.glfw.GLFW.GLFW_PRESS) held = false;
        boolean pressed = active && held && isMouseOver(mouseX, mouseY);
        int shift = pressed ? 1 : 0;
        var text = MinecraftClient.getInstance().textRenderer;
        int ink = active ? PhoneUi.INK : PhoneUi.MUTED;
        if (!appIcon.isBlank()) {
            int size = Math.max(16, Math.min(52, Math.min(getWidth() - 12, getHeight() - 19)));
            int ix = getX() + (getWidth() - size) / 2, iy = getY() + Math.max(1, (getHeight() - size - 14) / 2) + shift;
            if (isSelected()) PhoneUi.panel(context, ix - 2, iy - 2, size + 4, size + 4, PhoneUi.BLUE);
            context.setShaderColor(1, 1, 1, active ? (pressed ? 0.65f : 1) : 0.4f);
            PhoneUi.icon(context, appIcon, ix, iy, size);
            context.setShaderColor(1, 1, 1, 1);
            String label = text.trimToWidth(getMessage().getString(), getWidth() - 4);
            context.drawText(text, label, getX() + (getWidth() - text.getWidth(label)) / 2, iy + size + 5, ink, true);
            return;
        }
        PhoneUi.metal(context, pressed ? "metal-pressed" : active && isSelected() ? "metal-hover" : "metal",
            getX(), getY() + shift, getWidth(), getHeight() - shift, 4);
        if (active && (color == PhoneUi.BLUE || color == PhoneUi.RED || isFocused()))
            context.fill(getX() + 5, getY() + getHeight() - 2, getX() + getWidth() - 5, getY() + getHeight() - 1,
                color == PhoneUi.RED ? PhoneUi.RED : PhoneUi.BLUE);
        if (subtitle.isBlank()) {
            String label = text.trimToWidth(getMessage().getString(), Math.max(0, getWidth() - 8));
            context.drawText(text, label, getX() + (getWidth() - text.getWidth(label)) / 2, getY() + (getHeight() - 8) / 2 + shift, ink, false);
        } else {
            context.drawText(text, text.trimToWidth(getMessage().getString(), Math.max(0, getWidth() - 16)), getX() + 8, getY() + 8 + shift, ink, false);
            context.drawText(text, text.trimToWidth(subtitle, Math.max(0, getWidth() - 16)), getX() + 8, getY() + Math.min(getHeight() - 12, 23) + shift, PhoneUi.MUTED, false);
        }
    }

}
