package dev.hua.ptcglive.client;

import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.CardItems;
import dev.hua.ptcglive.engine.MatchSession;
import dev.hua.ptcglive.network.MatchIntentPayload;
import dev.hua.ptcglive.view.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.*;
import static dev.hua.ptcglive.client.PtcgLiveTheme.*;

/** Artwork-led match table, built solely from a recipient-filtered match snapshot. */
public final class MatchRegionScreen extends Screen implements MatchDisplay {
    private static final Map<String, CardCatalog.Entry> ART = CardItems.cards().stream()
        .collect(java.util.stream.Collectors.toUnmodifiableMap(c -> c.card().id(), c -> c.card()));
    private final MatchScreen parent;
    private final CardThumbnailCache textures = new CardThumbnailCache();
    private MatchSession.Snapshot snapshot;
    private MatchRegionModel model;
    private CardGridLayout layout;
    private int zone, page, refreshTicks;
    private String message = "", previewKey = "";
    private UUID previewSeat;
    private boolean unavailable;
    public MatchRegionScreen(MatchScreen parent, MatchSession.Snapshot snapshot) {
        super(Text.literal("对局区域与卡面")); this.parent = parent; this.snapshot = snapshot;
        model = MatchRegionModel.from(snapshot); chooseOwnHand();
    }
    public UUID matchId() { return snapshot.game().matchId(); }
    public boolean previewAllowed() { return !unavailable && model.allowsPreview(previewSeat, previewKey); }
    @Override public void update(MatchSession.Snapshot next, String notice) {
        parent.update(next, notice); message = notice; refreshTicks = 0;
        if (next == null) { unavailable = true; clearAndInit(); return; }
        boolean changedSeat = !Objects.equals(next.game().viewingSeat(), snapshot.game().viewingSeat());
        snapshot = next; model = MatchRegionModel.from(next); unavailable = false;
        if (changedSeat) { chooseOwnHand(); page = 0; }
        clearAndInit();
    }
    private void chooseOwnHand() {
        zone = 0;
        for (int i = 0; i < model.zones().size(); i++) if (model.zones().get(i).id().equals(model.viewingSeat() + ":hand")) zone = i;
    }
    private MatchRegionModel.Zone current() { return model.zones().get(zone); }
    private int pages() { return Math.max(1, (current().size() + layout.capacity() - 1) / layout.capacity()); }
    @Override protected void init() {
        layout = CardGridLayout.forScreen(width, height);
        addDrawableChild(ButtonWidget.builder(Text.literal("返回对局"), b -> close()).dimensions(width - 90, height - 24, 80, 20).build());
        if (unavailable) return;
        page = Math.min(page, pages() - 1);
        addDrawableChild(ButtonWidget.builder(Text.literal("上一区域"), b -> changeZone(-1)).dimensions(8, 28, 72, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("下一区域"), b -> changeZone(1)).dimensions(width - 80, 28, 72, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("上一页"), b -> { page = Math.max(0, page - 1); clearAndInit(); })
            .dimensions(8, height - 46, 64, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("下一页"), b -> { page = Math.min(pages() - 1, page + 1); clearAndInit(); })
            .dimensions(width - 72, height - 46, 64, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("刷新"), b -> refresh()).dimensions(8, height - 24, 54, 20).build());
        if (snapshot.practice() && snapshot.controllableSeats().size() == 2)
            addDrawableChild(ButtonWidget.builder(Text.literal("切换操控方"), b -> {
                UUID next = snapshot.controllableSeats().stream().filter(s -> !s.equals(model.viewingSeat())).findFirst().orElseThrow();
                sendRefresh(next);
            }).dimensions(68, height - 24, 92, 20).build());
    }
    private void changeZone(int step) { zone = Math.floorMod(zone + step, model.zones().size()); page = 0; message = ""; clearAndInit(); }
    private void sendRefresh(UUID seat) {
        ClientPlayNetworking.send(new MatchIntentPayload(matchId().toString(), seat == null ? "" : seat.toString(), Math.toIntExact(snapshot.revision()), -1));
    }
    private void refresh() { if (!unavailable) sendRefresh(model.viewingSeat()); }
    @Override public void tick() { if (++refreshTicks >= 40) { refreshTicks = 0; refresh(); } super.tick(); }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Intentionally no fullscreen tint: PTCG Craft surfaces leave the world visible.
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 9, TEXT);
        if (unavailable) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("对局不可访问，请返回卡桌重新打开。"), width / 2, height / 2, ERROR);
            super.render(context, mouseX, mouseY, delta); return;
        }
        var current = current();
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(current.nameZh() + " · " + current.size() + " 张", width - 174)), width / 2, 33, NOTICE);
        String note = message.isBlank() ? current.noteZh() : message;
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(note, width - 16), 8, 52, TEXT_DIM);
        for (int local = 0; local < layout.capacity(); local++) {
            int index = page * layout.capacity() + local;
            if (index >= current.size()) break;
            var cell = layout.cell(local);
            int x = cell.x() + (cell.width() - 56) / 2, y = cell.y(), w = 56, h = 78;
            if (index < current.cards().size()) {
                var card = current.cards().get(index); var art = textures.get(card.definitionId());
                if (art != null) CardPainter.card(context, textRenderer, art, card.nameZh(), x, y, w, h, CARD_BORDER);
                else CardPainter.back(context, textRenderer, "暂无卡图", x, y, w, h);
                context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(card.nameZh(), cell.width() - 6), cell.x() + 3, y + 81, TEXT);
                context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(card.detailZh(), cell.width() - 6), cell.x() + 3, y + 93, TEXT_SOFT);
            } else {
                CardPainter.cardBack(context, x, y, w, h);
                context.drawCenteredTextWithShadow(textRenderer, Text.literal("不提供卡面"), cell.x() + cell.width() / 2, y + 82, TEXT_DIM);
            }
        }
        if (current.size() == 0) context.drawCenteredTextWithShadow(textRenderer, Text.literal("此区域为空或尚未公开"), width / 2, 96, TEXT_DIM);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("第 " + (page + 1) + "/" + pages() + " 页 · 左键选操作 · 右键查看原图"), width / 2, height - 40, TEXT_SOFT);
        super.render(context, mouseX, mouseY, delta);
        int hit = layout.hit(mouseX, mouseY), index = page * layout.capacity() + hit;
        if (hit >= 0 && index < current.cards().size()) {
            var card = current.cards().get(index);
            context.drawTooltip(textRenderer, List.of(Text.literal(card.nameZh()), Text.literal(card.detailZh()), Text.literal(card.definitionId())), Optional.empty(), mouseX, mouseY);
        }
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (unavailable || (button != 0 && button != 1)) return false;
        int hit = layout.hit(x, y), index = page * layout.capacity() + hit;
        if (hit < 0 || index >= current().cards().size()) return false;
        var card = current().cards().get(index);
        if (button == 1) {
            var art = ART.get(card.definitionId());
            if (art == null) { message = "此测试卡或未安装卡牌没有对应卡图。"; return true; }
            previewKey = card.key(); previewSeat = model.viewingSeat();
            client.setScreen(new CardViewScreen(art, this)); return true;
        }
        var actions = MatchActionMatcher.forCards(snapshot, List.of(card.key())).stream()
            .filter(option -> !MatchActionMatcher.isAttackOption(option) || MatchActionMatcher.isAvailableAttackOption(option)).toList();
        if (actions.isEmpty()) { message = "这张牌当前没有可执行操作；可右键查看原图。"; return true; }
        client.setScreen(new MatchCardActionScreen(this, snapshot, card, actions)); return true;
    }
    @Override public void close() { client.setScreen(parent); }
    @Override public void removed() { ScreenArtLifetime.removed(this, textures::close); }
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }
}
