package dev.hua.ptcglive.client;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import static dev.hua.ptcglive.client.PtcgLiveTheme.*;

/**
 * Compatibility-only notice for old client entry points.  The actual item never
 * opens this screen: its result is chosen and granted by the dedicated server.
 */
final class StarterDeck100Screen extends Screen {
    StarterDeck100Screen() { super(Text.literal("新手随机卡组")); }

    @Override protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("关闭"), button -> close())
            .dimensions(width / 2 - 45, height - 32, 90, 20).build());
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, TEXT);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("手持“新手随机卡组”右键消耗一个，随机获得两套不同卡组。"), width / 2, 44, ACCENT);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("每套 60 张；随机结果与两个完整卡盒均由服务器生成。"), width / 2, 64, TEXT_SOFT);
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override public boolean shouldPause() { return false; }
}
