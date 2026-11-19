package dev.hua.ptcglive.client;

import dev.hua.ptcglive.engine.MatchSession;
import dev.hua.ptcglive.network.MatchIntentPayload;
import dev.hua.ptcglive.view.MatchRegionModel;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import java.util.List;
import static dev.hua.ptcglive.client.PtcgLiveTheme.*;

/**
 * Card-face action chooser. The client only displays a recipient-filtered list
 * of current server options; it never turns a card click into a custom command.
 */
final class MatchCardActionScreen extends Screen implements MatchDisplay {
    private final MatchRegionScreen parent;
    private final MatchRegionModel.Card card;
    private final CardThumbnailCache artwork = new CardThumbnailCache();
    private MatchSession.Snapshot snapshot;
    private List<MatchSession.Option> options;
    private int page;

    MatchCardActionScreen(MatchRegionScreen parent, MatchSession.Snapshot snapshot,
                          MatchRegionModel.Card card, List<MatchSession.Option> options) {
        super(Text.literal("卡牌操作"));
        this.parent = parent; this.snapshot = snapshot; this.card = card; this.options = List.copyOf(options);
    }
    @Override public java.util.UUID matchId() { return snapshot.game().matchId(); }
    @Override protected void init() {
        int capacity = capacity(); int pages = pages(); page = Math.min(page, pages - 1);
        int left = Math.max(150, width / 3), buttonWidth = width - left - 18;
        for (int local = 0; local < capacity; local++) {
            int index = page * capacity + local; if (index >= options.size()) break;
            var option = options.get(index); String label = option.labelZh();
            var button = ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(label, buttonWidth - 8)), b -> submit(option.index()))
                .dimensions(left, 50 + local * 22, buttonWidth, 20).build();
            button.setTooltip(Tooltip.of(Text.literal(label))); addDrawableChild(button);
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("上一页"), b -> { page = Math.max(0, page - 1); clearAndInit(); })
            .dimensions(left, height - 46, buttonWidth / 2 - 2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("下一页"), b -> { page = Math.min(pages - 1, page + 1); clearAndInit(); })
            .dimensions(left + buttonWidth / 2 + 2, height - 46, buttonWidth / 2 - 2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回区域"), b -> close()).dimensions(width / 2 - 50, height - 24, 100, 20).build());
    }
    private int capacity() { return Math.max(2, (height - 112) / 22); }
    private int pages() { return Math.max(1, (options.size() + capacity() - 1) / capacity()); }
    private void submit(int option) {
        ClientPlayNetworking.send(new MatchIntentPayload(snapshot.game().matchId().toString(),
            snapshot.game().viewingSeat() == null ? "" : snapshot.game().viewingSeat().toString(),
            Math.toIntExact(snapshot.revision()), option));
    }
    @Override public void update(MatchSession.Snapshot next, String notice) {
        // Any server update can invalidate targets/options. Return to a rebuilt
        // region screen instead of risking a stale local selection.
        parent.update(next, notice);
        if (client != null) client.setScreen(parent);
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("选择“" + card.nameZh() + "”的操作"), width / 2, 12, TEXT);
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(card.detailZh(), width / 3 - 16), 10, 32, TEXT_SOFT);
        var art = artwork.get(card.definitionId());
        int x = 18, y = 58, w = Math.max(80, width / 3 - 36), h = Math.min(height - 128, (int) (w * CARD_HEIGHT_PER_WIDTH));
        if (art != null) CardPainter.card(context, textRenderer, art, card.nameZh(), x, y, w, h, CARD_BORDER);
        else CardPainter.back(context, textRenderer, "暂无卡图", x, y, w, h);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("第 " + (page + 1) + "/" + pages() + " 页"), width * 2 / 3, height - 66, TEXT_SOFT);
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }
    @Override public void close() { if (client != null) client.setScreen(parent); }
    @Override public void removed() { artwork.close(); }
    @Override public boolean shouldPause() { return false; }
}
