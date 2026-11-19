package dev.hua.ptcglive.client;

import dev.hua.ptcglive.CardItems;
import dev.hua.ptcglive.network.PackIntentPayload;
import dev.hua.ptcglive.packs.PackUiSnapshot;
import dev.hua.ptcglive.packs.PackBatchPolicy;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import static dev.hua.ptcglive.client.PackGalleryLayout.*;
import static dev.hua.ptcglive.client.PtcgLiveTheme.*;

/**
 * The pack browser uses a vanilla window; opening and results are transparent overlays. It is a
 * viewer/intent sender only: probability rows and the revealed cards are server
 * snapshots.  Every element owns its own band (see {@link PackGalleryLayout}).
 */
public final class PackOpeningScreen extends Screen {
    private static final double PAGE_TURN = .16;
    private final PackRevealAnimation animation = new PackRevealAnimation();
    private long lastAnimationNanos;
    private double pageSeconds = PAGE_TURN;
    private int outgoingPage = -1, pageDirection = 1;
    private net.minecraft.util.Identifier packBackTexture;
    private record Rect(int x, int y, int width, int height) {
        boolean contains(double px, double py) { return px >= x && px < x + width && py >= y && py < y + height; }
    }
    private PackUiSnapshot snapshot;
    private Screen browseParent;
    private final java.util.Set<String> rarities=new java.util.LinkedHashSet<>();
    private List<Integer> filterWidths = List.of();
    private List<PackUiSnapshot.CardRow> filteredPool=List.of();
    private static String rarity(PackUiSnapshot.CardRow card){return dev.hua.ptcglive.CardLabel.rarityCode(card.rarityZh());}
    private void filterPool(){filteredPool=snapshot.cards().stream().filter(c->rarities.contains(rarity(c))).toList();page=0;}
    private List<String> availableRarities(){return snapshot.cards().stream().map(PackOpeningScreen::rarity).distinct().sorted().toList();}
    private boolean browsing(){return snapshot.phase()==PackUiSnapshot.Phase.BROWSE;}
    /** HD source art is reduced before upload, allowing an entire pool page to stay GPU-safe. */
    private final CardBoxArtCache galleryArtwork = new CardBoxArtCache(256, 96);
    /** Result cards keep their original decoded resolution for a crisp reveal. */
    private final CardBoxArtCache revealArtwork = new CardBoxArtCache();
    private String message = "";
    private int page, packCount = 1;
    private boolean pending, cancelSent, closedByServer;
    private float tiltX, tiltY, tiltVelocityX, tiltVelocityY, hoverLift, foilTime;
    private long lastFrameNanos;
    private boolean dragging;
    private double dragStartX, dragX;
    private int dismissDirection = -1;
    private dev.hua.ptcglive.packs.PackDestination destination=dev.hua.ptcglive.packs.PackDestination.INVENTORY;

    public PackOpeningScreen(PackUiSnapshot snapshot) { this(snapshot,null); }
    public PackOpeningScreen(PackUiSnapshot snapshot,Screen parent) { super(Text.literal("PTCG 抽卡")); this.snapshot=snapshot;this.browseParent=parent;animation.reset(snapshot.results().size());snapshot.cards().forEach(c->rarities.add(rarity(c)));filterPool(); }
    public String sessionId() { return snapshot.sessionId(); }
    public void update(PackUiSnapshot next, String notice) {
        boolean newResults = next.phase() == PackUiSnapshot.Phase.RESULTS
            && (snapshot.phase() != PackUiSnapshot.Phase.RESULTS || !snapshot.results().equals(next.results()));
        snapshot = next; message = notice == null ? "" : notice; pending = false;
        if (newResults) { animation.reset(next.results().size()); page = 0; lastAnimationNanos = 0; outgoingPage = -1; packBackTexture = null; }
        clearAndInit();
    }
    public void closeFromServer(String notice) { closedByServer = true; message = notice == null ? "" : notice; close(); }

    private boolean results() { return snapshot.phase() == PackUiSnapshot.Phase.RESULTS; }
    private PackGalleryLayout layout() { return PackGalleryLayout.forScreen(width, height, filteredPool.size(), results() ? List.of() : filterWidths); }
    private int pages() { return results() ? PackGalleryLayout.resultPages(snapshot.results().size()) : layout().pages(); }
    private int resultStart() { return page * PackGalleryLayout.RESULTS_PER_PAGE; }
    private List<PackUiSnapshot.CardRow> visibleResults() {
        return snapshot.results().subList(resultStart(), Math.min(snapshot.results().size(), resultStart() + PackGalleryLayout.RESULTS_PER_PAGE));
    }

    @Override protected void init() {
        var available = results() ? List.<String>of() : availableRarities();
        var widths = new ArrayList<Integer>();
        if (!results()) {
            widths.add(textRenderer.getWidth("全选") + 14);
            widths.add(textRenderer.getWidth("清空") + 14);
            for (String value : available) widths.add(Math.max(textRenderer.getWidth("☑ " + value), textRenderer.getWidth("☐ " + value)) + 14);
        }
        filterWidths = List.copyOf(widths);
        var layout = layout();
        if (results()) {
            action(animation.stage() == PackRevealAnimation.Stage.SUMMARY ? "完成" : "跳过演出",
                animation.stage() == PackRevealAnimation.Stage.SUMMARY ? "关闭开包结果" : "直接查看全部已入"+snapshot.destination().labelZh()+"的卡牌",
                (animation.stage() == PackRevealAnimation.Stage.SUMMARY ? layout.doneX() : width - DONE_WIDTH - 12), (animation.stage() == PackRevealAnimation.Stage.SUMMARY ? layout.barY() : 10), DONE_WIDTH, animation.stage() == PackRevealAnimation.Stage.SUMMARY ? this::close : this::skipReveal);
            if (animation.stage() == PackRevealAnimation.Stage.SUMMARY && pages() > 1) {
                action("<", "上一页结果", layout.pagerLeftX(), layout.pagerY(), PAGER_BUTTON_WIDTH, this::previous);
                action(">", "下一页结果", layout.pagerRightX(), layout.pagerY(), PAGER_BUTTON_WIDTH, this::next);
            }
            return;
        }
        page = Math.min(page, layout.pages() - 1);
        var slots = layout.filterSlots(filterWidths);
        filterButton("全选", "显示本卡包全部罕度", slots.get(0), () -> rarities.addAll(available));
        filterButton("清空", "取消全部罕度勾选", slots.get(1), rarities::clear);
        for (int i = 0; i < available.size(); i++) {
            String value = available.get(i);
            filterButton((rarities.contains(value) ? "☑ " : "☐ ") + value, "勾选显示 " + value + "；仅筛选卡池显示，不改变抽取概率", slots.get(i + 2),
                () -> { if (!rarities.remove(value)) rarities.add(value); });
        }
        if(browsing()) {
            action("返回", "返回商城或卡牌资料",layout.cancelX(),layout.barY(),CANCEL_WIDTH,this::close);
            action("前往购买", "进入该卡包商城",layout.confirmX(),layout.barY(),CONFIRM_WIDTH,()->ClientPlayNetworking.send(new PackIntentPayload(snapshot.setId(),"SHOP")));
            action("<","上一页",layout.pagerLeftX(),layout.pagerY(),PAGER_BUTTON_WIDTH,this::previous);
            action(">","下一页",layout.pagerRightX(),layout.pagerY(),PAGER_BUTTON_WIDTH,this::next);
            return;
        }
        action("取消", "取消抽取，不消耗卡包", layout.cancelX(), layout.barY(), CANCEL_WIDTH, this::cancel).active = !pending;
        action("抽 " + packCount + " 包", "确认抽取 " + packCount + " 包，共 " + (packCount * snapshot.info().cardsPerPack()) + " 张，存入"+destination.labelZh(), layout.confirmX(), layout.barY(), CONFIRM_WIDTH, this::confirm).active = !pending;
        int countX = layout.pagerLeftX() + PAGER_BUTTON_WIDTH + GAP;
        int countWidth = layout.pagerRightX() - GAP - countX;
        int half=(countWidth-GAP)/2;
        action("数量：" + packCount + " 包", "点击切换 1／4／5／8／10／16／32／64 包；不同封面可合并，取消不扣包", countX, layout.pagerY(), half,
            () -> { packCount = PackBatchPolicy.nextCount(packCount); clearAndInit(); }).active = !pending;
        action("存入："+destination.labelZh(), "点击切换背包／图鉴；图鉴存放不占背包格，不改变卡池与概率", countX+half+GAP, layout.pagerY(), countWidth-half-GAP,
            () -> { destination=destination==dev.hua.ptcglive.packs.PackDestination.INVENTORY?dev.hua.ptcglive.packs.PackDestination.COLLECTION:dev.hua.ptcglive.packs.PackDestination.INVENTORY; clearAndInit(); }).active=!pending;
        action("<", "上一页", layout.pagerLeftX(), layout.pagerY(), PAGER_BUTTON_WIDTH, this::previous).active = !pending;
        action(">", "下一页", layout.pagerRightX(), layout.pagerY(), PAGER_BUTTON_WIDTH, this::next).active = !pending;
    }
    private ButtonWidget action(String label, String tooltip, int x, int y, int w, Runnable callback) {
        ButtonWidget button = results() ? new ResultButton(x, y, w, BUTTON_HEIGHT, label, callback)
            : ButtonWidget.builder(Text.literal(label), ignored -> callback.run()).dimensions(x, y, w, BUTTON_HEIGHT).build();
        button.setTooltip(Tooltip.of(Text.literal(tooltip)));
        return addDrawableChild(button);
    }
    private final class ResultButton extends ButtonWidget {
        ResultButton(int x, int y, int w, int h, String label, Runnable action) {
            super(x, y, w, h, Text.literal(label), ignored -> action.run(), DEFAULT_NARRATION_SUPPLIER);
        }
        @Override protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            int color = isSelected() ? 0xff8fc6ff : 0xffffffff;
            context.drawCenteredTextWithShadow(textRenderer, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, color);
            if (isSelected()) context.fill(getX() + 5, getY() + getHeight() - 2, getX() + getWidth() - 5, getY() + getHeight() - 1, color);
        }
    }
    private void filterButton(String label, String tooltip, PackGalleryLayout.FilterSlot slot, Runnable change) {
        var button = ButtonWidget.builder(Text.literal(label), ignored -> { change.run(); filterPool(); clearAndInit(); })
            .dimensions(slot.x(), slot.y(), slot.width(), FILTER_HEIGHT).build();
        button.setTooltip(Tooltip.of(Text.literal(tooltip)));
        button.active = !pending;
        addDrawableChild(button);
    }
    private void confirm() {
        if (pending) return;
        pending = true; ClientPlayNetworking.send(new PackIntentPayload(snapshot.sessionId(), destination.action(packCount))); clearAndInit();
    }
    private void cancel() {
        if (cancelSent) return;
        cancelSent = true; ClientPlayNetworking.send(new PackIntentPayload(snapshot.sessionId(), "CANCEL")); close();
    }
    private void updateAnimation() {
        long now = System.nanoTime();
        double dt = lastAnimationNanos == 0 ? 0 : Math.min(.1, (now - lastAnimationNanos) / 1_000_000_000d);
        lastAnimationNanos = now;
        var before = animation.stage();
        animation.update(dt);
        pageSeconds = Math.min(PAGE_TURN, pageSeconds + dt);
        if (animation.stage() == PackRevealAnimation.Stage.HOLD && before != animation.stage())
            tiltX = tiltY = tiltVelocityX = tiltVelocityY = hoverLift = 0;
        if (before != PackRevealAnimation.Stage.SUMMARY && animation.stage() == PackRevealAnimation.Stage.SUMMARY) {
            page = 0; outgoingPage = -1; clearAndInit();
        }
    }

    // ---- drawing ----
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (results()) {
            updateAnimation();
            var resultLayout = layout();
            PackUiSnapshot.CardRow hovered = null;
            if (animation.stage() == PackRevealAnimation.Stage.SUMMARY) {
                context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth("拆包 · " + snapshot.packNameZh(), resultLayout.titleWidth(true)),
                    resultLayout.innerLeft(), resultLayout.barY() + 6, 0xffffffff);
                hovered = renderResults(context, resultLayout, mouseX, mouseY);
                String status = "已入" + snapshot.destination().labelZh() + " · 滚轮 / 箭头翻页 · 右键查看大图";
                context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(status, resultLayout.innerWidth()), resultLayout.innerLeft(), resultLayout.statusY(), 0xffe0e9f5);
            } else renderReveal(context, resultLayout, mouseX, mouseY, delta);
            super.render(context, mouseX, mouseY, delta);
            if (hovered != null) context.drawTooltip(textRenderer, details(hovered), mouseX, mouseY);
            return;
        }
        renderInGameBackground(context);
        var layout = layout();
        CardPainter.vanillaWindow(context, layout.windowX(), layout.windowY(), layout.windowWidth(), layout.windowHeight());
        String name = snapshot.packNameZh();
        String title = longestFitting(layout.titleWidth(results()),
            results() ? List.of("拆包 · " + name, "拆包结果") : List.of("开包 · " + name, name));
        CardPainter.label(context, textRenderer, title, layout.innerLeft(), layout.barY() + 6, LABEL);
        PackUiSnapshot.CardRow hovered = renderPreview(context, layout, mouseX, mouseY);
        drawStatus(context, layout);
        super.render(context, mouseX, mouseY, delta);
        if (hovered != null) context.drawTooltip(textRenderer, details(hovered), mouseX, mouseY);
    }
    private PackUiSnapshot.CardRow renderPreview(DrawContext context, PackGalleryLayout layout, int mouseX, int mouseY) {
        if (layout.showInfo()) renderInfo(context, layout);
        page = Math.min(page, layout.pages() - 1);
        int labelY = layout.contentTop() + 1;
        CardPainter.label(context, textRenderer, "显示 " + filteredPool.size() + " / " + snapshot.cards().size() + " 张", layout.galleryX(), labelY, LABEL);
        String pageText = "第 " + (page + 1) + " / " + layout.pages() + " 页";
        CardPainter.label(context, textRenderer, pageText, layout.innerRight() - textRenderer.getWidth(pageText), labelY, LABEL);

        // Every slot of the page is drawn, empty or not, like a chest.
        PackUiSnapshot.CardRow hovered = null;
        int start = layout.pageStart(page);
        for (int i = 0; i < layout.capacity(); i++) {
            int x = layout.slotX(i % layout.columns()), y = layout.slotY(i / layout.columns()), index = start + i;
            if (index >= filteredPool.size()) { CardPainter.slot(context, x, y, layout.slotWidth(), layout.slotHeight()); continue; }
            var card = filteredPool.get(index);
            boolean over = mouseX >= x && mouseX < x + layout.slotWidth() && mouseY >= y && mouseY < y + layout.slotHeight();
            drawSlotCard(context, galleryArtwork, card, x, y, layout.cardWidth(), layout.cardHeight(), over);
            if (over) hovered = card;
        }
        // Pager middle is reserved for the batch selector; wheel and right-click remain available.
        return hovered;
    }
    /** Left column: the held pack's 美版 art in a sunken slot, then its identity. */
    private void renderInfo(DrawContext context, PackGalleryLayout layout) {
        var info = snapshot.info();
        int art = layout.artSize(), artX = layout.artX(), top = layout.contentTop();
        CardPainter.slot(context, artX, top, art, art);
        if (!info.artTexture().isBlank()) {
            var texture = net.minecraft.util.Identifier.tryParse(info.artTexture());
            if (texture != null) {
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                context.drawTexture(texture, artX + 1, top + 1, art - 2, art - 2, 0, 0, 128, 128, 128, 128);
                com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add(info.chineseName());
        if ("美版借用封面".equals(info.artLabel())) lines.add("借用封面 · 自定义卡池");
        if (!info.englishName().isBlank() && !info.englishName().equalsIgnoreCase(info.chineseName())) lines.add(info.englishName());
        lines.add(info.setCode() + " · " + info.releaseDate());
        lines.add(info.eraZh() + (info.custom() ? "" : "（美版）"));
        lines.add("卡池 " + info.poolSize() + " 张");
        lines.add("每包 " + info.cardsPerPack() + " 张");
        lines.add(info.custom() ? "各印刷版本等概率" : "官方概率未公开");
        int y = layout.infoTextTop();
        boolean first = true;
        for (String text : lines) {
            if (text.isBlank()) continue;
            if (y + 9 > layout.columnBottom()) break;
            CardPainter.label(context, textRenderer, textRenderer.trimToWidth(text, layout.infoWidth()), layout.innerLeft(), y, first ? LABEL_STRONG : LABEL);
            y += INFO_LINE_HEIGHT; first = false;
        }
        CardPainter.groove(context, layout.dividerX(), layout.contentTop(), layout.columnBottom());
    }
    private PackUiSnapshot.CardRow renderResults(DrawContext context, PackGalleryLayout layout, int mouseX, int mouseY) {
        double t = smooth(pageSeconds / PAGE_TURN);
        double travel = width + 40;
        context.enableScissor(0, layout.contentTop(), width, layout.pagerY());
        if (outgoingPage >= 0 && t < 1)
            renderResultPage(context, layout, outgoingPage, -pageDirection * travel * t, -1, -1);
        var hovered = renderResultPage(context, layout, page, pageDirection * travel * (1 - t), t == 1 ? mouseX : -1, t == 1 ? mouseY : -1);
        context.disableScissor();
        String summary = "共 " + snapshot.results().size() + " 张 · 第 " + (page + 1) + "/" + pages() + " 页";
        context.drawCenteredTextWithShadow(textRenderer, summary, width / 2, layout.pagerY() + 6, 0xffffffff);
        return hovered;
    }
    private PackUiSnapshot.CardRow renderResultPage(DrawContext context, PackGalleryLayout layout, int index, double offset, int mouseX, int mouseY) {
        int start = index * PackGalleryLayout.RESULTS_PER_PAGE;
        var cards = snapshot.results().subList(start, Math.min(snapshot.results().size(), start + PackGalleryLayout.RESULTS_PER_PAGE));
        var row = layout.resultRow(cards.size());
        PackUiSnapshot.CardRow hovered = null;
        var matrices = context.getMatrices(); matrices.push(); matrices.translate(offset, 0, 0);
        for (int i = 0; i < cards.size(); i++) {
            int x = row.slotX(i) + SLOT_BORDER, y = row.top() + SLOT_BORDER;
            var card = cards.get(i);
            CardPainter.slotCard(context, textRenderer, revealArtwork.get(card.definitionId()), card.nameZh(), x, y, row.cardWidth(), row.cardHeight());
            if (mouseX >= x && mouseX < x + row.cardWidth() && mouseY >= y && mouseY < y + row.cardHeight()) hovered = card;
            int labelWidth = row.slotWidth() + row.gap() - 2, center = x + row.cardWidth() / 2;
            context.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(card.nameZh(), labelWidth), center, row.labelY(), 0xffffffff);
            context.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(card.rarityZh(), labelWidth), center, row.labelY() + 10, 0xffcbdff5);
        }
        matrices.pop();
        return hovered;
    }
    private int cardsPerPack() { return Math.max(1, snapshot.info().cardsPerPack()); }
    private int totalPacks() { return Math.max(1, (snapshot.results().size() + cardsPerPack() - 1) / cardsPerPack()); }
    private int packSize() { return snapshot.results().size(); }
    private PackUiSnapshot.CardRow currentResult() { return snapshot.results().get(animation.cardIndex()); }
    private static double smooth(double value) {
        double p = Math.max(0, Math.min(1, value));
        return p * p * (3 - 2 * p);
    }

    private Rect revealCardRect(PackGalleryLayout layout) {
        int top = 38;
        int h = Math.max(24, Math.min(280, (int) (height * 0.54)));
        h = Math.min(h, Math.max(24, height - top - 58));
        int w = Math.max(16, Math.min(width - 32, Math.round(h * CARD_ASPECT)));
        int y = Math.max(top, Math.min((height - h) / 2, height - h - 58));
        return new Rect((width - w) / 2, y, w, h);
    }
    private Rect queuedPack(int index) {
        int count = totalPacks();
        int columns = Math.min(count, Math.max(1, Math.min(16, (width - 24) / 26)));
        int rows = (count + columns - 1) / columns;
        int h = Math.max(8, Math.min(64, (int) (height * 0.18) / rows - 3));
        int w = Math.max(5, h * 2 / 3), gap = Math.max(3, w / 5);
        int rowCount = Math.min(columns, count - (index / columns) * columns);
        return new Rect((width - rowCount * (w + gap) + gap) / 2 + (index % columns) * (w + gap),
            38 + (index / columns) * (h + 3), w, h);
    }
    private PackUiSnapshot.CardRow renderReveal(DrawContext context, PackGalleryLayout layout, int mouseX, int mouseY, float delta) {
        if (snapshot.results().isEmpty()) return null;
        Rect card = revealCardRect(layout);
        updateCardMotion(card, mouseX, mouseY);
        boolean unpacking = animation.stage() == PackRevealAnimation.Stage.PACK || animation.stage() == PackRevealAnimation.Stage.OPENING;
        String caption = unpacking ? "拆包 × " + totalPacks()
            : "翻牌 " + (animation.cardIndex() + 1) + " / " + packSize();
        context.drawTextWithShadow(textRenderer, caption, 12, 16, 0xffedf5ff);
        context.drawCenteredTextWithShadow(textRenderer, "点击卡牌两侧 / 左右拖动抽卡 · 空格继续 · 右键查看卡面", width / 2, height - 18, 0xffedf5ff);
        if (unpacking) {
            for (int i = 0; i < Math.min(6, packSize()); i++) revealArtwork.get(snapshot.results().get(i).definitionId());
            if (animation.stage() == PackRevealAnimation.Stage.PACK) {
                for (int i = totalPacks() - 1; i >= 0; i--) drawWrapper(context, queuedPack(i), 0);
            } else {
                double t = animation.progress(), approach = smooth(t / .26);
                if (t < .26) {
                    for (int i = totalPacks() - 1; i >= 0; i--) {
                        Rect from = queuedPack(i);
                        var matrices = context.getMatrices(); matrices.push();
                        matrices.translate((card.x() - from.x()) * approach, (card.y() - from.y()) * approach, 0);
                        matrices.translate(from.x(), from.y(), 0);
                        matrices.scale((float) (1 + (card.width() / (double) from.width() - 1) * approach),
                            (float) (1 + (card.height() / (double) from.height() - 1) * approach), 1);
                        drawWrapper(context, new Rect(0, 0, from.width(), from.height()), 0);
                        matrices.pop();
                    }
                } else {
                    double tear = smooth((t - .26) / .52), drop = smooth((t - .76) / .24);
                    if (tear > .1) drawStack(context, card, Math.min(6, packSize()));
                    var matrices = context.getMatrices(); matrices.push(); matrices.translate(0, drop * height, 0);
                    drawWrapper(context, card, tear); matrices.pop();
                }
            }
            return null;
        }
        int remaining = packSize() - animation.cardIndex();
        if (animation.stage() == PackRevealAnimation.Stage.FIRST_ENTER) {
            int layers = Math.min(6, packSize());
            for (int i = layers - 1; i >= 0; i--) {
                double t = smooth((animation.progress() - i * .035) / .82);
                Rect layer = new Rect(card.x() + i * 2, card.y() + i * 2, card.width(), card.height());
                drawTurningCard(context, layer, 0, (1 - t) * (i % 2 == 0 ? 9 : -9),
                    (1 - t) * (i - layers / 2d) * 16, -(1 - t) * card.height() * .24,
                    snapshot.results().get(i), 1, layers - i);
            }
        } else if (animation.stage() == PackRevealAnimation.Stage.FLIP) {
            double turn = smooth(animation.progress());
            for (int i = Math.min(5, remaining - 1); i >= 0; i--) {
                Rect layer = new Rect(card.x() + i * 2, card.y() + i * 2, card.width(), card.height());
                drawTurningCard(context, layer, turn, 0, 0, -Math.sin(turn * Math.PI) * 8,
                    snapshot.results().get(animation.cardIndex() + i), 1, 6 - i);
            }
        } else {
            for (int i = Math.min(5, remaining - 1); i >= 1; i--) {
                Rect layer = new Rect(card.x() + i * 2, card.y() + i * 2, card.width(), card.height());
                drawTurningCard(context, layer, 1, 0, 0, 0,
                    snapshot.results().get(animation.cardIndex() + i), 1, 6 - i);
            }
            if (animation.stage() == PackRevealAnimation.Stage.SWITCH) {
                double slide = smooth(animation.progress());
                drawTurningCard(context, card, 1, dismissDirection * slide * 18,
                    dismissDirection * slide * (width / 2.0 + card.width() * 1.5), -slide * 36,
                    currentResult(), (float) (1 - slide), 7);
            } else {
                drawTurningCard(context, card, 1, dragging ? (dragX - dragStartX) * 0.04 : 0,
                    dragging ? (dragX - dragStartX) * 0.35 : 0, 0, currentResult(), 1, 7);
                drawSparkles(context, card, Math.min(1, animation.elapsed() / 1.2));
                context.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(currentResult().nameZh() + " · " + currentResult().rarityZh(), Math.max(20, width - 24)),
                    width / 2, card.y() + card.height() + 22, 0xffffffff);
            }
        }
        return null;
    }
    private void updateCardMotion(Rect card, int mouseX, int mouseY) {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0 : Math.min(0.05f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        foilTime += dt;
        boolean viewing = animation.stage() == PackRevealAnimation.Stage.HOLD;
        float targetX = viewing ? Math.clamp((mouseY - card.y() - card.height() / 2f) / card.height(), -1f, 1f) * -22 : 0;
        float targetY = viewing ? Math.clamp((mouseX - card.x() - card.width() / 2f) / card.width(), -1f, 1f) * 22 : 0;
        // Small integration steps keep the damped spring stable after a slow frame.
        for (float left = dt; left > 0; ) {
            float step = Math.min(left, 0.008f);
            tiltVelocityX += (42 * (targetX - tiltX) - 11 * tiltVelocityX) * step;
            tiltVelocityY += (42 * (targetY - tiltY) - 11 * tiltVelocityY) * step;
            tiltX += tiltVelocityX * step;
            tiltY += tiltVelocityY * step;
            left -= step;
        }
        float targetLift = viewing && card.contains(mouseX, mouseY) ? 1 : 0;
        hoverLift += (targetLift - hoverLift) * (1 - (float) Math.exp(-8 * dt));
    }
    private void drawStack(DrawContext context, Rect card, int count) {
        for (int i = count; i > 0; i--) {
            drawCardBack(context, card.x() + i * 2, card.y() + i * 2, card.width(), card.height());
        }
    }
    /** Each front-art variant has its own recorded, self-designed reverse texture. */
    private void drawWrapper(DrawContext context, Rect pack, double tear) {
        if (packBackTexture == null) {
            String art = snapshot.info().artTexture();
            var candidate = net.minecraft.util.Identifier.tryParse(art.replace("textures/item/booster_packs/", "textures/gui/pack_backs/"));
            if (candidate != null && client.getResourceManager().getResource(candidate).isPresent()) packBackTexture = candidate;
        }
        if (packBackTexture == null) {
            context.drawCenteredTextWithShadow(textRenderer, "缺少该卡包背图", pack.x() + pack.width() / 2, pack.y() + pack.height() / 2, 0xffffffff);
            return;
        }
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        int half = pack.width() / 2;
        for (int side = 0; side < 2; side++) {
            int w = side == 0 ? half : pack.width() - half;
            var matrices = context.getMatrices(); matrices.push();
            matrices.translate((side == 0 ? -1 : 1) * tear * pack.width() * .66, 0, 0);
            context.drawTexture(packBackTexture, pack.x() + side * half, pack.y(), w, pack.height(), side * 384, 0, 384, 1280, 768, 1280);
            matrices.pop();
        }
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }
    private void drawCardBack(DrawContext context, int x, int y, int w, int h) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        BattleCosmetics.draw(context,BattleCosmetics.cardBack(),x,y,w,h);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }
    private void drawTurningCard(DrawContext context, Rect card, double turn, double tilt, double dx, double dy,
                                 PackUiSnapshot.CardRow result, float alpha, int layer) {
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(card.x() + card.width() / 2f + dx, card.y() + card.height() / 2f + dy - hoverLift * 6, 100 + layer * 100);
        float scale = 1 + hoverLift * 0.065f;
        matrices.scale(scale, scale, 1);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(tiltX));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(tiltY));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) tilt));
        // Keep the full card geometry while rotating; avoid squeezing the card-back drawing primitives.
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (turn < 0.5 ? turn * 180 : (turn - 1) * 180)));
        matrices.translate(-card.width() / 2f, -card.height() / 2f, 0);
        if (turn < 0.5) drawCardBack(context, 0, 0, card.width(), card.height());
        else {
            var art = revealArtwork.get(result.definitionId());
            if (!PackCardFoil.draw(context, art, card.width(), card.height(), result.rarityZh(), foilTime, tiltX, tiltY, alpha)) {
                context.setShaderColor(1, 1, 1, alpha);
                CardPainter.slotCard(context, textRenderer, art, result.nameZh(), 0, 0, card.width(), card.height());
                context.setShaderColor(1, 1, 1, 1);
            }
        }
        matrices.pop();
    }
    private void drawSparkles(DrawContext context, Rect card, double t) {
        if (PackCardFoil.tier(currentResult().rarityZh()) < 2) return;
        int rgb = rarityColor(currentResult().rarityZh()) & 0xffffff;
        int alpha = (int) (180 * (1 - t));
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6 + 0.15 * t;
            int x = card.x() + card.width() / 2 + (int) (Math.cos(angle) * card.width() * (0.58 + t * 0.22));
            int y = card.y() + card.height() / 2 + (int) (Math.sin(angle) * card.height() * (0.55 + t * 0.15));
            context.fill(x - 2, y, x + 3, y + 1, (alpha << 24) | rgb);
            context.fill(x, y - 2, x + 1, y + 3, (alpha << 24) | rgb);
        }
    }
    /** A card in a sunken slot whose inner ring shows the rarity colour. */
    private void drawSlotCard(DrawContext context, CardBoxArtCache artwork, PackUiSnapshot.CardRow card,
                              int x, int y, int cardW, int cardH, boolean hovered) {
        CardPainter.sunken(context, x, y, cardW + SLOT_BORDER * 2, cardH + SLOT_BORDER * 2, rarityColor(card.rarityZh()));
        int cardX = x + SLOT_BORDER, cardY = y + SLOT_BORDER;
        CardPainter.slotCard(context, textRenderer, artwork.get(card.definitionId()), card.nameZh(), cardX, cardY, cardW, cardH);
        if (hovered) context.fill(cardX, cardY, cardX + cardW, cardY + cardH, SLOT_HOVER);
    }
    /** The server message (first line) sits alone on the bottom line, never over buttons. */
    private void drawStatus(DrawContext context, PackGalleryLayout layout) {
        String first = results() && animation.stage() != PackRevealAnimation.Stage.SUMMARY
            ? "卡牌已入"+snapshot.destination().labelZh()+" · 点击／空格／下滚轮加快演出 · 右键看大图"
            : message.isBlank() ? "" : message.split("\\n", 2)[0];
        if (first.isBlank()) return;
        CardPainter.label(context, textRenderer, textRenderer.trimToWidth(first, layout.innerWidth()), layout.innerLeft(), layout.statusY(), LABEL_NOTICE);
    }
    private String longestFitting(int width, List<String> options) {
        for (String option : options) if (textRenderer.getWidth(option) <= width) return option;
        return textRenderer.trimToWidth(options.getLast(), Math.max(0, width));
    }
    private List<Text> details(PackUiSnapshot.CardRow card) {
        var lines = new ArrayList<Text>(); lines.add(Text.literal(card.nameZh() + " · " + card.printedNumber() + " · " + card.rarityZh())); lines.add(Text.literal(card.typeZh()));
        if (card.chances().isEmpty()) lines.add(Text.literal("服务器已确定此开包结果。"));
        else for (var chance : card.chances()) lines.add(Text.literal(chance.positionsZh() + "：" + PackUiSnapshot.percent(chance.probability())));
        lines.add(Text.literal("右键查看高清原图"));
        return lines;
    }

    // ---- input ----
    private void skipReveal() {
        animation.skip(); page = 0; outgoingPage = -1; pageSeconds = PAGE_TURN; dragging = false; clearAndInit();
    }
    private void continueReveal() { animation.proceed(); dragging = false; }
    private void changePage(int target) {
        if (pending || target < 0 || target >= pages() || target == page) return;
        if (results() && pageSeconds < PAGE_TURN) return;
        if (results()) { outgoingPage = page; pageDirection = target > page ? 1 : -1; pageSeconds = 0; }
        page = target; clearAndInit();
    }
    private void previous() { changePage(page - 1); }
    private void next() { changePage(page + 1); }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical == 0) return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        if (results() && animation.stage() != PackRevealAnimation.Stage.SUMMARY) {
            if (vertical < 0) continueReveal();
            return true;
        }
        if (vertical > 0) previous(); else next();
        return true;
    }
    /** The card under the point: a pool card on the current page, or an already revealed result. */
    private PackUiSnapshot.CardRow cardAt(double mouseX, double mouseY) {
        var layout = layout();
        if (results()) {
            if (animation.stage() != PackRevealAnimation.Stage.SUMMARY) {
                return (animation.stage() == PackRevealAnimation.Stage.HOLD)
                    && revealCardRect(layout).contains(mouseX, mouseY)
                    ? currentResult() : null;
            }
            if (pageSeconds < PAGE_TURN) return null;
            var cards = visibleResults();
            int index = layout.resultRow(cards.size()).indexAt(mouseX, mouseY);
            return index >= 0 && index < cards.size() ? cards.get(index) : null;
        }
        int local = layout.slotIndexAt(mouseX, mouseY);
        if (local < 0) return null;
        int index = layout.pageStart(Math.min(page, layout.pages() - 1)) + local;
        return index < filteredPool.size() ? filteredPool.get(index) : null;
    }
    /** Pool cards and revealed results use the same direct right-click HD viewer as card boxes and the battle table. */
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            var selected = cardAt(mouseX, mouseY);
            if (selected != null) {
                try {
                    client.setScreen(new CardViewScreen(CardItems.byDefinitionId(selected.definitionId()).card(), this));
                } catch (IllegalArgumentException missing) {
                    message = "此卡没有可打开的卡面定义：" + selected.definitionId();
                }
                return true;
            }
        }
        if (button == 0 && results() && animation.stage() != PackRevealAnimation.Stage.SUMMARY) {
            if (super.mouseClicked(mouseX, mouseY, button)) return true;
            if (animation.stage() == PackRevealAnimation.Stage.HOLD && revealCardRect(layout()).contains(mouseX, mouseY)) {
                dragging = true;
                dragStartX = dragX = mouseX;
                return true;
            }
            if (animation.stage() == PackRevealAnimation.Stage.PACK) { continueReveal(); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && dragging) { dragX = mouseX; return true; }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            double distance = mouseX - dragStartX;
            dismissDirection = Math.abs(distance) > 8 ? (distance > 0 ? 1 : -1) : (mouseX > width / 2.0 ? 1 : -1);
            dragging = false;
            continueReveal();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (results() && animation.stage() != PackRevealAnimation.Stage.SUMMARY
            && (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            continueReveal(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    /**
     * Screen.render calls this after our own drawing; the dimming and the window
     * are drawn first in render() instead, so this must stay empty.
     */
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }
    @Override public void close() {
        if(browsing()){client.setScreen(browseParent);return;}
        if (!closedByServer && !results() && !cancelSent) {
            cancelSent = true; ClientPlayNetworking.send(new PackIntentPayload(snapshot.sessionId(), "CANCEL"));
        }
        super.close();
    }
    @Override public void removed() { lastAnimationNanos = lastFrameNanos = 0; ScreenArtLifetime.removed(this, () -> { galleryArtwork.close(); revealArtwork.close(); }); }
    @Override public boolean shouldPause() { return false; }
}
