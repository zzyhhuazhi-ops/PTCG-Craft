package dev.hua.ptcglive.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Every battle action shares the same pill, focus, disabled and tooltip treatment. */
final class TableButton extends ButtonWidget {
    /** LABEL 只写居中文字（翻页的“1 / 3”），不画底也不响应点击。 */
    enum Style { NORMAL, PRIMARY, END_TURN, LABEL }
    private final Style style;
    private final int scale;
    private final BattleMotion.ButtonHover focus=new BattleMotion.ButtonHover();
    private net.minecraft.client.gui.tooltip.Tooltip detail;

    TableButton(PtcgLiveTableLayout.Rect area, String label, Style style, int scale, PressAction onPress) {
        this(area.x(), area.y(), area.width(), area.height(), label, style, scale, onPress);
    }
    TableButton(int x, int y, int width, int height, String label, Style style, int scale, PressAction onPress) {
        super(x, y, width, height, Text.literal(label), onPress, DEFAULT_NARRATION_SUPPLIER);
        this.style = style; this.scale = Math.max(1, scale);
    }

    @Override public void setTooltip(net.minecraft.client.gui.tooltip.Tooltip tooltip) { detail=tooltip;super.setTooltip(null); }
    @Override protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        var font = MinecraftClient.getInstance().textRenderer;
        long now=System.nanoTime();
        double hover=focus.sample(isSelected(),now);
        if (style == Style.LABEL)
            BattleUi.centeredText(context, font, font.trimToWidth(getMessage().getString(), Math.max(0, getWidth() / scale)),
                getX() + getWidth() / 2, getY() + (getHeight() - 8 * scale) / 2, BattleUi.MUTED, false, scale);
        else BattleUi.button(context,font,getMessage().getString(),new PtcgLiveTableLayout.Rect(getX(),getY(),getWidth(),getHeight()),
            style==Style.PRIMARY||style==Style.END_TURN,active,hover,scale);
        if(detail!=null&&focus.tooltipReady(now))
            BattleUi.orderedTooltip(context,font,detail.getLines(MinecraftClient.getInstance()),mouseX,mouseY);
    }
}
