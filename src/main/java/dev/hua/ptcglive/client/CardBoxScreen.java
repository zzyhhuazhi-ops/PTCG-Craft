package dev.hua.ptcglive.client;

import dev.hua.ptcglive.collection.BoxEditorSession;
import dev.hua.ptcglive.CardItems;
import dev.hua.ptcglive.network.BoxIntentPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.util.*;
import static dev.hua.ptcglive.client.PtcgLiveCollectionLayout.*;
import static dev.hua.ptcglive.client.PtcgLiveTheme.*;

/**
 * Two-column card-box editor drawn as a vanilla container window over the dimmed
 * world: the box on the left, the owner's unboxed collection on the right.  No inventory mutation occurs here: each edit waits for a fresh
 * owner-only server snapshot, and the collection is paged by the server in pages
 * of exactly one grid (the client reports its grid capacity).
 */
public final class CardBoxScreen extends Screen {
    private static final int POLL_TICKS = 40, MESSAGE_TICKS = 100, DRAG_THRESHOLD = 4;
    private static final int KEY_ESCAPE = 256, KEY_ENTER = 257, KEY_KEYPAD_ENTER = 335;
    private record Hit(int side, BoxEditorSession.CardRow row) { }

    private BoxEditorSession.Snapshot snapshot;
    private final Screen returnScreen;
    private TextFieldWidget nameField, searchField;
    private String draftName, draftQuery, message = "", pendingAction = "";
    private int boxPage, pollTicks, messageTicks, rulesScroll, maxRulesScroll;
    private boolean pending, confirmDelete, exportRequested, rulesOpen;
    /** Two full pages of downscaled art stay resident; enabled HD packs are still preferred as the source. */
    private final CardBoxArtCache artwork = new CardBoxArtCache(2 * BoxEditorSession.MAX_PAGE_SIZE + 8, 256);
    /** Left-button press on a card: a release without movement moves one copy, a drag needs to end on the other column. */
    private Hit pressed;
    private double pressX, pressY;
    private boolean dragging;

    public CardBoxScreen(BoxEditorSession.Snapshot snapshot) {
        this(snapshot,null);
    }
    public CardBoxScreen(BoxEditorSession.Snapshot snapshot,Screen returnScreen) {
        super(Text.literal("卡盒编辑器")); this.snapshot = snapshot;
        this.returnScreen=returnScreen;
        this.draftName = snapshot.nameZh(); this.draftQuery = snapshot.query();
    }
    public UUID sessionId() { return snapshot.sessionId(); }

    public void update(BoxEditorSession.Snapshot next, String notice) {
        capture();
        if (next == null || next.deleted()) {
            if (client != null && client.player != null) client.player.sendMessage(Text.literal(notice), false);
            close(); return;
        }
        // Opening hands the screen the same snapshot it was built with. Every real server reply carries a
        // new revision, so an echo must not cancel the page-size request init() may already have sent.
        if (next.revision() == snapshot.revision()) { if (!notice.isBlank()) showMessage(notice); return; }
        boolean rebuildNeeded = pending || !next.contents().equals(snapshot.contents()) || !next.collection().equals(snapshot.collection())
            || !next.rules().equals(snapshot.rules()) || !next.nameZh().equals(snapshot.nameZh())
            || !next.rulesetId().equals(snapshot.rulesetId()) || next.collectionPage() != snapshot.collectionPage()
            || next.collectionPages() != snapshot.collectionPages() || next.collectionPageSize() != snapshot.collectionPageSize()
            || next.totalCards() != snapshot.totalCards() || !next.query().equals(snapshot.query())
            || !next.source().equals(snapshot.source());
        if (pendingAction.equals("RENAME") || draftName.equals(snapshot.nameZh())) draftName = next.nameZh();
        if (exportRequested && !next.exportText().isEmpty() && client != null) {
            client.keyboard.setClipboard(next.exportText()); showMessage("已复制卡表文本；没有复制任何卡牌。"); exportRequested = false;
        } else if (!notice.isBlank() || !(pendingAction.equals("REFRESH") || pendingAction.equals("PAGE_SIZE"))) showMessage(notice);
        snapshot = next; pending = false; pollTicks = 0; pendingAction = "";
        if (rebuildNeeded) clearAndInit(); // an unchanged periodic refresh must not steal text-field focus/cursor
    }

    private PtcgLiveCollectionLayout layout() { return PtcgLiveCollectionLayout.forScreen(width, height); }
    private void capture() {
        if (nameField != null) draftName = nameField.getText();
        if (searchField != null) draftQuery = searchField.getText();
    }
    private void rebuild() { capture(); clearAndInit(); }
    private void showMessage(String text) { message = text == null ? "" : text; messageTicks = message.isBlank() ? 0 : MESSAGE_TICKS; }

    // ---- pages ----
    private int boxPages(PtcgLiveCollectionLayout layout) {
        return Math.max(1, (snapshot.contents().size() + layout.cardsPerPage() - 1) / layout.cardsPerPage());
    }
    private List<BoxEditorSession.CardRow> pageRows(int side, PtcgLiveCollectionLayout layout) {
        int per = layout.cardsPerPage();
        if (side == COLLECTION) return snapshot.collection().subList(0, Math.min(per, snapshot.collection().size()));
        var rows = snapshot.contents(); int page = Math.min(boxPage, boxPages(layout) - 1);
        return rows.subList(Math.min(rows.size(), page * per), Math.min(rows.size(), (page + 1) * per));
    }
    private int page(int side, PtcgLiveCollectionLayout layout) { return side == BOX ? Math.min(boxPage, boxPages(layout) - 1) : snapshot.collectionPage(); }
    private int pages(int side, PtcgLiveCollectionLayout layout) { return side == BOX ? boxPages(layout) : Math.max(1, snapshot.collectionPages()); }
    private void turn(int side, int step) {
        if (pending) return;
        var layout = layout();
        int target = page(side, layout) + step;
        if (target < 0 || target >= pages(side, layout)) return;
        if (side == BOX) { boxPage = target; rebuild(); }
        else request("PAGE", Integer.toString(layout.cardsPerPage()), target, snapshot.query());
    }
    /**
     * The server pages the collection in pages of exactly one grid.  After the
     * window or GUI scale changes, ask for the new size and keep the first
     * visible card on screen.
     */
    private void syncPageSize(PtcgLiveCollectionLayout layout) {
        int wanted = layout.cardsPerPage(), current = Math.max(1, snapshot.collectionPageSize());
        if (pending || current == wanted) return;
        int page = snapshot.collectionPage() * current / wanted;
        pending = true; pendingAction = "PAGE_SIZE";
        ClientPlayNetworking.send(new BoxIntentPayload(sessionId().toString(), snapshot.revision(), "PAGE", Integer.toString(wanted), page, snapshot.query()));
    }

    // ---- widgets ----
    @Override protected void init() {
        var layout = layout();
        syncPageSize(layout);
        int bar = layout.barY();
        nameField = new TextFieldWidget(textRenderer, layout.nameX(), bar, layout.nameWidth(), BUTTON_HEIGHT, Text.literal("卡组名称"));
        nameField.setMaxLength(40); nameField.setText(draftName); addDrawableChild(nameField);
        action("保存名称", "保存卡组名称", layout.saveX(), bar, SAVE_WIDTH, this::rename);
        int sourceWidth = (RESERVED_WIDTH - GAP) / 2;
        action("背包", "只从背包取卡加入卡盒", layout.reservedX(), bar, sourceWidth,
            () -> send("SOURCE", "INVENTORY")).active = !pending && !snapshot.source().equals("INVENTORY");
        action("图鉴", "只从图鉴取卡加入卡盒", layout.reservedX() + sourceWidth + GAP, bar, sourceWidth,
            () -> send("SOURCE", "COLLECTION")).active = !pending && !snapshot.source().equals("COLLECTION");
        button(rulesOpen ? "收起" : "规则", rulesOpen ? "收起规则检查" : "规则检查", layout.rulesX(), bar, RULES_WIDTH, this::toggleRules);
        button("×", "关闭", layout.closeX(), bar, CLOSE_WIDTH, this::close);
        searchField = null;
        if (rulesOpen) { initRules(layout); return; }

        boxPage = Math.min(boxPage, boxPages(layout) - 1);
        int tool = (layout.columnWidth() - 3 * GAP) / 4, x = layout.panelX(BOX), y = layout.toolbarY();
        boolean compact = tool < 56;
        action(compact ? "排序" : "按编号排序", "按编号排序", x, y, tool, () -> send("SORT", ""));
        action(compact ? "复制" : "复制卡表", "把卡表文本复制到剪贴板（不复制卡牌）", x + tool + GAP, y, tool,
            () -> { exportRequested = true; send("EXPORT", ""); });
        String delete = confirmDelete ? (compact ? "确认" : "确认删空盒") : (compact ? "删除" : "删除空盒");
        action(compact ? "导入" : "导入Live", "从剪贴板导入60张Live卡表；多余实卡退回图鉴，缺卡保留占位", x + 2 * (tool + GAP), y, tool,
            () -> importLive());
        action(delete, "只能删除空卡盒及手中凭证", x + 3 * (tool + GAP), y, tool, this::deleteEmpty).active = !pending && snapshot.totalCards() == 0;

        searchField = new TextFieldWidget(textRenderer, layout.searchX(), y, layout.searchWidth(), BUTTON_HEIGHT, Text.literal("搜索" + sourceLabel() + "：名称或编号"));
        searchField.setMaxLength(40); searchField.setText(draftQuery); searchField.setPlaceholder(Text.literal("搜索名称或编号"));
        addDrawableChild(searchField);
        action("搜索", "按名称或编号搜索" + sourceLabel(), layout.searchButtonX(), y, SEARCH_BUTTON_WIDTH, this::search);

        for (int side = BOX; side <= COLLECTION; side++) {
            // An empty column has a single page: its pager row shows the empty-state hint instead of buttons.
            if (columnEmpty(side)) continue;
            final int s = side;
            action("<", "上一页", layout.pagerLeftX(side), layout.pagerY(), PAGER_BUTTON_WIDTH, () -> turn(s, -1));
            action(">", "下一页", layout.pagerRightX(side), layout.pagerY(), PAGER_BUTTON_WIDTH, () -> turn(s, 1));
        }
    }
    private void initRules(PtcgLiveCollectionLayout layout) {
        var installed = snapshot.rules().stream().filter(r -> !r.version().equals("未知")).toList();
        int width = Math.min(160, layout.rulesAreaWidth());
        action("切换所选规则包", "在已安装的规则包之间切换", layout.rulesAreaX(), layout.toolbarY(), width, () -> {
            if (installed.isEmpty()) return;
            int current = -1;
            for (int i = 0; i < installed.size(); i++) if (installed.get(i).id().equals(snapshot.rulesetId())) current = i;
            send("RULESET", installed.get((current + 1) % installed.size()).id());
        });
    }
    private ButtonWidget button(String label, String tooltip, int x, int y, int w, Runnable action) {
        var button = ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(label, w - 6)), b -> action.run())
            .dimensions(x, y, w, BUTTON_HEIGHT).build();
        button.setTooltip(Tooltip.of(Text.literal(tooltip))); return addDrawableChild(button);
    }
    private ButtonWidget action(String label, String tooltip, int x, int y, int w, Runnable action) {
        var button = button(label, tooltip, x, y, w, action); button.active = !pending; return button;
    }
    private void toggleRules() { rulesOpen = !rulesOpen; rulesScroll = 0; pressed = null; dragging = false; rebuild(); }
    private void rename() { if (nameField != null) send("RENAME", nameField.getText()); }
    private void search() { if (searchField != null) request("PAGE", Integer.toString(layout().cardsPerPage()), 0, searchField.getText()); }
    private void deleteEmpty() {
        if (!confirmDelete) { confirmDelete = true; showMessage("仅删除空盒及手中凭证；再次点击确认。"); rebuild(); }
        else send("DELETE_EMPTY", "");
    }

    // ---- server intents ----
    private void send(String action, String argument) { request(action, argument, snapshot.collectionPage(), snapshot.query()); }
    private void request(String action, String argument, int remotePage, String query) {
        if (pending && !action.equals("REFRESH")) return;
        capture();
        if (!action.equals("REFRESH")) { pending = true; pendingAction = action; }
        else if (!pending) pendingAction = action;
        ClientPlayNetworking.send(new BoxIntentPayload(sessionId().toString(), snapshot.revision(), action, argument, remotePage, query));
        if (!action.equals("REFRESH")) clearAndInit();
    }
    private void importLive() {
        String text = client.keyboard.getClipboard();
        if (text.length() > dev.hua.ptcglive.collection.LiveDeckParser.MAX_TEXT) { showMessage("剪贴板内容过长，请仅复制卡组文本"); return; }
        send("IMPORT_LIVE", text);
    }
    private void move(Hit hit) { confirmDelete = false; send(hit.row().missing() ? "FILL_MISSING" : hit.side() == COLLECTION ? "ADD" : "TAKE", hit.row().definitionId()); }
    private String sourceLabel() { return snapshot.source().equals("INVENTORY") ? "背包" : "图鉴"; }
    @Override public void tick() {
        // Also invalidates an editor left open when a table locks this box or the held credential changes.
        if (++pollTicks >= POLL_TICKS) { pollTicks = 0; send("REFRESH", ""); }
        if (messageTicks > 0) messageTicks--;
        super.tick();
    }

    // ---- drawing ----
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Vanilla container look: dim the world exactly like a chest or furnace does (no blur), then the window.
        renderInGameBackground(context);
        var layout = layout();
        CardPainter.vanillaWindow(context, layout.windowX(), layout.windowY(), layout.windowWidth(), layout.windowHeight());
        if (layout.showTitle()) CardPainter.label(context, textRenderer, title.getString(), layout.panelX(BOX), layout.barY() + 6, LABEL);
        drawStatus(context, layout);
        if (rulesOpen) drawRules(context, layout);
        else {
            CardPainter.groove(context, layout.dividerX(), layout.columnTop(), layout.columnBottom());
            for (int side = BOX; side <= COLLECTION; side++) drawColumn(context, layout, side, mouseX, mouseY);
        }
        super.render(context, mouseX, mouseY, delta);
        if (rulesOpen) return;
        if (dragging && pressed != null) {
            // Like a carried item stack: the card is centred on the cursor.
            int w = layout.cardWidth(), h = layout.cardHeight();
            CardPainter.slotCard(context, textRenderer, artwork.get(pressed.row().definitionId()), pressed.row().nameZh(),
                mouseX - w / 2, mouseY - h / 2, w, h);
            return;
        }
        var hit = hit(layout, mouseX, mouseY);
        if (hit != null) context.drawTooltip(textRenderer, details(hit), mouseX, mouseY);
        else if (mouseY >= layout.columnTop() && mouseY < layout.toolbarY()
            && mouseX >= layout.panelX(BOX) && mouseX < layout.panelX(BOX) + layout.columnWidth())
            context.drawTooltip(textRenderer, Text.literal("最后修改：" + snapshot.lastModified().replace('T', ' ')), mouseX, mouseY);
    }
    private void drawStatus(DrawContext context, PtcgLiveCollectionLayout layout) {
        var selected = snapshot.rules().stream().filter(r -> r.id().equals(snapshot.rulesetId())).findFirst();
        boolean notice = !message.isBlank() && (messageTicks > 0 || confirmDelete);
        String text = notice ? message : "已放入 " + snapshot.totalCards() + "/60 张 · "
            + selected.map(r -> r.nameZh() + "：" + r.statusZh()).orElse("需更新规则数据") + (pending ? " · 等待服务器" : "");
        int color = notice ? LABEL_NOTICE : selected.map(BoxEditorSession.RuleRow::canStart).orElse(false) ? LABEL_OK : LABEL;
        CardPainter.label(context, textRenderer, textRenderer.trimToWidth(text, layout.rulesAreaWidth()), layout.panelX(BOX), layout.statusY(), color);
    }
    private void drawColumn(DrawContext context, PtcgLiveCollectionLayout layout, int side, int mouseX, int mouseY) {
        int x = layout.panelX(side), w = layout.columnWidth();
        // Column label like vanilla's inventory label: name on the left, count on the right.
        String name = side == BOX ? "卡盒内" : sourceLabel();
        String info = side == BOX ? snapshot.totalCards() + "/60" : snapshot.collectionCards() + " 张";
        int labelY = layout.columnTop() + 1;
        CardPainter.label(context, textRenderer, name, x, labelY, LABEL);
        String fitted = textRenderer.trimToWidth(info, Math.max(0, w - textRenderer.getWidth(name) - 8));
        CardPainter.label(context, textRenderer, fitted, x + w - textRenderer.getWidth(fitted), labelY, LABEL);

        // Every slot of the page is drawn, empty or not, so the page capacity reads like a chest.
        var rows = pageRows(side, layout);
        int cardW = layout.cardWidth(), cardH = layout.cardHeight();
        Hit hovered = dragging ? null : hit(layout, mouseX, mouseY);
        for (int i = 0; i < layout.cardsPerPage(); i++) {
            int slotX = layout.cardX(side, i % layout.columns()), slotY = layout.cardY(i / layout.columns());
            CardPainter.slot(context, slotX, slotY, layout.slotWidth(), layout.slotHeight());
            if (i >= rows.size()) continue;
            var row = rows.get(i);
            int cardX = slotX + SLOT_BORDER, cardY = slotY + SLOT_BORDER;
            if (row.missing()) CardPainter.ghostCard(context, textRenderer, artwork.get(row.definitionId()), row.nameZh(), cardX, cardY, cardW, cardH);
            else CardPainter.slotCard(context, textRenderer, artwork.get(row.definitionId()), row.nameZh(), cardX, cardY, cardW, cardH);
            CardPainter.stackCount(context, textRenderer, row.count(), cardX, cardY, cardW, cardH);
            if (row.missing()) context.drawText(textRenderer, "缺" + row.count(), cardX + 1, cardY + 1, 0xffff5555, true);
            boolean isHovered = hovered != null && hovered.side() == side && hovered.row().equals(row);
            boolean isSource = dragging && pressed != null && pressed.side() == side && pressed.row().equals(row);
            if (isHovered || isSource) context.fill(cardX, cardY, cardX + cardW, cardY + cardH, SLOT_HOVER);
        }
        if (dragging && pressed != null && pressed.side() != side && layout.panelAt(mouseX, mouseY) == side) {
            int gridX = layout.gridLeft(side);
            context.fill(gridX, layout.gridTop(), gridX + layout.gridWidth(), layout.gridTop() + layout.gridHeight(), DROP_TARGET);
        }
        if (columnEmpty(side)) {
            // The empty slots already show the column is empty; the hint goes in the pager row, never over the slots.
            String hint = longestFitting(layout.columnWidth() - 4, emptyHints(side));
            CardPainter.label(context, textRenderer, hint, x + (w - textRenderer.getWidth(hint)) / 2, layout.pagerY() + 6, LABEL);
            return;
        }
        // Page number between the two pager buttons, with the column's hint when it fits.
        int left = layout.pagerLeftX(side) + PAGER_BUTTON_WIDTH + GAP, right = layout.pagerRightX(side) - GAP;
        String pageText = (page(side, layout) + 1) + " / " + pages(side, layout);
        String withHint = pageText + " · " + (side == BOX ? "左键取回图鉴 · 右键看原图" : "左键放入 · 右键看原图");
        String shown = textRenderer.getWidth(withHint) <= right - left ? withHint : pageText;
        CardPainter.label(context, textRenderer, shown, (left + right - textRenderer.getWidth(shown)) / 2, layout.pagerY() + 6, LABEL);
    }
    private boolean columnEmpty(int side) { return side == BOX ? snapshot.contents().isEmpty() : snapshot.collection().isEmpty(); }
    /** Empty-state hints for one column, longest first; the longest that fits the pager row is shown. */
    private List<String> emptyHints(int side) {
        if (side == BOX) return List.of("卡盒是空的，点右侧卡牌放入", "卡盒是空的");
        if (!snapshot.query().isBlank()) return List.of("没有匹配“" + snapshot.query() + "”的收藏", "没有匹配的收藏");
        return List.of(sourceLabel() + "暂无可用卡牌", "暂无可用卡牌");
    }
    private String longestFitting(int width, List<String> options) {
        for (String option : options) if (textRenderer.getWidth(option) <= width) return option;
        return textRenderer.trimToWidth(options.getLast(), width);
    }
    private void drawRules(DrawContext context, PtcgLiveCollectionLayout layout) {
        int x = layout.rulesAreaX(), w = layout.rulesAreaWidth(), labelY = layout.columnTop() + 1;
        CardPainter.label(context, textRenderer, "规则检查", x, labelY, LABEL);
        String modified = textRenderer.trimToWidth("最后修改：" + snapshot.lastModified().replace('T', ' '), w - textRenderer.getWidth("规则检查") - 8);
        CardPainter.label(context, textRenderer, modified, x + w - textRenderer.getWidth(modified), labelY, LABEL);
        int top = layout.gridTop(), bottom = layout.columnBottom();
        CardPainter.sunken(context, x, top, w, bottom - top, TEXT_AREA);
        int lineY = top + 5 - rulesScroll;
        context.enableScissor(x + 1, top + 1, x + w - 1, bottom - 1);
        for (var rule : snapshot.rules()) {
            List<String> lines = new ArrayList<>();
            lines.add((rule.id().equals(snapshot.rulesetId()) ? "【已选】" : "") + rule.nameZh() + " · " + rule.version());
            lines.add(rule.statusZh() + "；可在卡桌开局：" + (rule.canStart() ? "是" : "否"));
            lines.addAll(rule.reasons());
            boolean first = true;
            for (String line : lines) {
                for (var wrapped : textRenderer.wrapLines(Text.literal(line), w - 12)) {
                    context.drawTextWithShadow(textRenderer, wrapped, x + 6, lineY, first ? TEXT : TEXT_BODY); lineY += 12;
                }
                first = false;
            }
            lineY += 8;
        }
        context.disableScissor();
        maxRulesScroll = Math.max(0, lineY + rulesScroll - (bottom - 4)); rulesScroll = Math.min(rulesScroll, maxRulesScroll);
    }
    private List<Text> details(Hit hit) {
        if (hit.row().missing()) return List.of(Text.literal(hit.row().nameZh()), Text.literal("缺少 " + hit.row().count() + " 张 · " + hit.row().definitionId()),
            Text.literal("左键：从背包或图鉴补入一张 · 右键：查看卡面"));
        var art = artwork.get(hit.row().definitionId());
        return List.of(Text.literal(hit.row().nameZh()),
            Text.literal("×" + hit.row().count() + " · " + hit.row().definitionId()),
            Text.literal(hit.side() == BOX ? "已被此卡盒占用" : "来自" + sourceLabel() + " · 可加入 " + hit.row().count()),
            Text.literal(art != null && art.highDefinition() ? "高清原图" : "缩略图"),
            Text.literal(hit.side() == BOX ? "左键或拖到右侧：取回图鉴一张 · 右键看原图" : "左键或拖到左侧：从" + sourceLabel() + "放入一张 · 右键看原图"));
    }
    /**
     * Screen.render calls this after our own drawing; the dimming and the window
     * are drawn first in render() instead, so this must stay empty.
     */
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    // ---- input ----
    private Hit hit(PtcgLiveCollectionLayout layout, double mouseX, double mouseY) {
        if (rulesOpen) return null;
        int side = layout.panelAt(mouseX, mouseY);
        if (side < 0) return null;
        int index = layout.cardIndexAt(side, mouseX, mouseY);
        var rows = pageRows(side, layout);
        return index >= 0 && index < rows.size() ? new Hit(side, rows.get(index)) : null;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        var hit = hit(layout(), x, y);
        if (hit == null) return false;
        if (button == 1) { openPreview(hit.row().definitionId()); return true; }
        if (button == 0) { pressed = hit; pressX = x; pressY = y; dragging = false; return true; }
        return false;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double deltaX, double deltaY) {
        if (button == 0 && pressed != null) {
            if (!dragging && Math.hypot(x - pressX, y - pressY) > DRAG_THRESHOLD) dragging = true;
            return true;
        }
        return super.mouseDragged(x, y, button, deltaX, deltaY);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && pressed != null) {
            var source = pressed; boolean dragged = dragging; pressed = null; dragging = false;
            if (!dragged) { move(source); return true; }
            int side = layout().panelAt(x, y);
            if (side >= 0 && side != source.side()) move(source);
            else if (side < 0) showMessage("拖到另一侧才会移动；服务器会再次校验。");
            return true;
        }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (rulesOpen) { rulesScroll = Math.max(0, Math.min(maxRulesScroll, rulesScroll - (int) (vertical * 24))); return true; }
        if (vertical == 0) return super.mouseScrolled(x, y, horizontal, vertical);
        int side = layout().panelAt(x, y);
        if (side < 0) return super.mouseScrolled(x, y, horizontal, vertical);
        turn(side, vertical > 0 ? -1 : 1);
        return true;
    }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == KEY_ESCAPE && rulesOpen) { toggleRules(); return true; }
        if (keyCode == KEY_ENTER || keyCode == KEY_KEYPAD_ENTER) {
            if (searchField != null && searchField.isFocused()) { search(); return true; }
            if (nameField != null && nameField.isFocused()) { rename(); return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public void close() {
        ClientPlayNetworking.send(new BoxIntentPayload(sessionId().toString(), snapshot.revision(), "CLOSE", "", 0, ""));
        if(returnScreen!=null){client.setScreen(returnScreen);if(returnScreen instanceof AiPracticeScreen ai)ai.refresh();}
        else super.close();
    }
    private void openPreview(String definitionId) {
        CardItems.cards().stream().filter(card -> card.card().id().equals(definitionId)).findFirst()
            .ifPresentOrElse(card -> client.setScreen(new CardViewScreen(card.card(), this)), () -> showMessage("此卡没有可打开的卡面定义：" + definitionId));
    }
    @Override public void removed() { ScreenArtLifetime.removed(this, artwork::close); }
    @Override public boolean shouldPause() { return false; }
}
