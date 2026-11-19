package dev.hua.ptcglive.client;

import dev.hua.ptcglive.CardItems;
import dev.hua.ptcglive.collection.BoxEditorSession;
import dev.hua.ptcglive.network.BoxIntentPayload;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Portrait card-box editor backed by the original owner-only BoxEditorSession. */
public final class PhoneCardBoxScreen extends Screen {
    private enum Pane { BOX, INVENTORY, COLLECTION, RULES }
    private static final int REQUEST_TIMEOUT_TICKS = 80;
    private final PhoneScreen parent;
    private final CardBoxArtCache artwork = new CardBoxArtCache(68, 256);
    private PagedRows<BoxEditorSession.CardRow> availableRows = new PagedRows<>();
    private PagedRows<BoxEditorSession.CardRow> refreshedRows;
    private int refreshThroughBatch;
    private BoxEditorSession.Snapshot snapshot;
    private TextFieldWidget nameField, searchField;
    private String draftName, draftQuery, message = "", pendingAction = "";
    private Pane pane = Pane.BOX;
    private final SmoothMotion rulesScroll = new SmoothMotion();
    private final SmoothMotion cardScroll = new SmoothMotion();
    private int pollTicks;
    private boolean pending, confirmDelete, exportRequested;

    public PhoneCardBoxScreen(BoxEditorSession.Snapshot snapshot, PhoneScreen parent) {
        super(Text.literal("卡盒")); this.snapshot = snapshot; this.parent = parent;
        draftName = snapshot.nameZh(); draftQuery = snapshot.query();
        availableRows.accept(availableKey(snapshot), snapshot.collectionPage(), snapshot.collectionPages(), snapshot.collection(), true);
    }
    public UUID sessionId() { return snapshot.sessionId(); }

    public void update(BoxEditorSession.Snapshot next, String notice) {
        capture();
        if (next == null || next.deleted()) {
            if (client != null && client.player != null && notice != null && !notice.isBlank()) client.player.sendMessage(Text.literal(notice), false);
            returnToPhone(false); return;
        }
        if (next.revision() == snapshot.revision()) { if (notice != null && !notice.isBlank()) message = notice; return; }
        String completedAction = pendingAction;
        boolean changedQuery = !availableKey(snapshot).equals(availableKey(next));
        if (pendingAction.equals("RENAME") || draftName.equals(snapshot.nameZh())) draftName = next.nameZh();
        if (exportRequested && !next.exportText().isEmpty() && client != null) {
            client.keyboard.setClipboard(next.exportText()); message = "已复制卡表文本；未复制任何卡牌。"; exportRequested = false;
        } else if (notice != null && !notice.isBlank()) message = notice;
        snapshot = next; pending = false; pendingAction = ""; pollTicks = 0;
        if (isSourcePane()) pane = next.source().equals("INVENTORY") ? Pane.INVENTORY : Pane.COLLECTION;
        boolean reset = changedQuery || resetsAvailable(completedAction);
        if(reset)refreshedRows=null;
        if(refreshedRows!=null && completedAction.equals("REFRESH_BATCH")) {
            refreshedRows.accept(availableKey(next),next.collectionPage(),next.collectionPages(),next.collection(),false);
            if(refreshedRows.nextBatch()>Math.min(refreshThroughBatch,next.collectionPages()-1)) {
                availableRows=refreshedRows;refreshedRows=null;
            }
        } else if(List.of("ADD","TAKE","FILL_MISSING").contains(completedAction)) {
            // A disappearing row shifts later batch boundaries. Rebuild all loaded
            // batches off-screen, retaining the visible extent and scroll offset.
            refreshThroughBatch=Math.max(0,availableRows.nextBatch()-1);
            refreshedRows=new PagedRows<>();
        } else availableRows.accept(availableKey(next), next.collectionPage(), next.collectionPages(), next.collection(),reset);
        if(reset)cardScroll.jump(0);
        clearAndInit();
    }

    private PhoneLayout.Frame frame() { return PhoneLayout.forScreen(width, height); }
    private PhoneButton button(String label, int x, int y, int w, int color, boolean light, Runnable action) {
        PhoneButton result = addDrawableChild(new PhoneButton(x, y, w, 18, label, color, light, ignored -> action.run()));
        result.active = !pending && refreshedRows==null; return result;
    }
    private void capture() {
        if (nameField != null) draftName = nameField.getText();
        if (searchField != null) draftQuery = searchField.getText();
    }

    @Override protected void init() {
        PhoneLayout.Frame f = frame(); int x = f.innerX(), w = f.innerWidth();
        button("‹", f.x() + 10, f.y() + 10, 22, PhoneUi.SURFACE_ALT, false, this::close).active = true;
        button("×", f.right() - 30, f.y() + 10, 20, PhoneUi.SURFACE_ALT, false, this::close).active = true;

        int nameY = f.y() + 43;
        nameField = new TextFieldWidget(textRenderer, x, nameY, Math.max(70, w - 64), 18, Text.literal("卡盒名称"));
        nameField.setMaxLength(40); nameField.setText(draftName); addDrawableChild(nameField);
        button("保存", x + w - 60, nameY, 60, PhoneUi.BLUE, true, this::rename);

        int tabsY = nameY + 22, quarter = (w - 12) / 4;
        paneButton(Pane.BOX, "卡盒内", x, tabsY, quarter);
        paneButton(Pane.INVENTORY, "背包", x + quarter + 4, tabsY, quarter);
        paneButton(Pane.COLLECTION, "图鉴", x + 2 * (quarter + 4), tabsY, quarter);
        paneButton(Pane.RULES, "规则", x + 3 * (quarter + 4), tabsY, w - 3 * (quarter + 4));

        int toolsY = tabsY + 22;
        if (pane == Pane.BOX) initBoxTools(x, toolsY, w);
        else if (isSourcePane()) initSearch(x, toolsY, w);
        else initRules(x, toolsY, w);

        if (pane != Pane.RULES) {
            int wanted = pageSize();
            if (snapshot.collectionPageSize() != wanted && !pending) {
                pending = true; pendingAction = "PAGE_SIZE";
                ClientPlayNetworking.send(new BoxIntentPayload(sessionId().toString(), snapshot.revision(), "PAGE",
                    Integer.toString(wanted), snapshot.collectionPage() * Math.max(1, snapshot.collectionPageSize()) / wanted, snapshot.query()));
            }
        }
    }

    private void paneButton(Pane target, String label, int x, int y, int w) {
        button(label, x, y, w, pane == target ? PhoneUi.BLUE : PhoneUi.SURFACE_ALT, pane == target, () -> {
            if (pending) return;
            capture(); pane = target; rulesScroll.jump(0); cardScroll.jump(0);
            if (isSourcePane() && !snapshot.source().equals(pane.name())) send("SOURCE", pane.name());
            else clearAndInit();
        });
    }
    private void initBoxTools(int x, int y, int w) {
        int third = (w - 12) / 4;
        button("编号排序", x, y, third, PhoneUi.SURFACE_ALT, false, () -> send("SORT", ""));
        button("复制卡表", x + third + 4, y, third, PhoneUi.SURFACE_ALT, false, () -> { exportRequested = true; send("EXPORT", ""); });
        button(third < 55 ? "导入" : "导入Live", x + 2 * (third + 4), y, third, PhoneUi.SURFACE_ALT, false, this::importLive)
            .setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("从剪贴板导入60张Live卡表；多余实卡退回图鉴，缺卡保留占位")));
        PhoneButton delete = button(confirmDelete ? "确认删除" : "删除空盒", x + 3 * (third + 4), y, w - 3 * (third + 4),
            confirmDelete ? PhoneUi.RED : PhoneUi.SURFACE_ALT, confirmDelete, this::deleteEmpty);
        delete.active = !pending && snapshot.totalCards() == 0;
    }
    private void initSearch(int x, int y, int w) {
        searchField = new TextFieldWidget(textRenderer, x, y, Math.max(60, w - 52), 18, Text.literal("搜索可用卡牌"));
        searchField.setMaxLength(40); searchField.setText(draftQuery); searchField.setPlaceholder(Text.literal("名称、编号，或标记:H")); addDrawableChild(searchField);
        button("搜索", x + w - 48, y, 48, PhoneUi.BLUE, true, this::search);
    }
    private void initRules(int x, int y, int w) {
        button("切换规则包", x, y, Math.min(100, w), PhoneUi.BLUE, true, this::nextRuleset);
    }

    private int gridTop() { return frame().y() + 109; }
    private int gridBottom() { return frame().bottom() - 47; }
    private int columns() { return frame().width() < 360 ? 3 : 4; }
    private int cellHeight() { return Math.max(50, Math.min(92, gridBottom() - gridTop())); }
    private int pageSize() { return BoxEditorSession.MAX_PAGE_SIZE; }
    private boolean isSourcePane() { return pane == Pane.INVENTORY || pane == Pane.COLLECTION; }
    private String sourceLabel() { return pane == Pane.INVENTORY ? "背包" : "图鉴"; }
    private String availableKey(BoxEditorSession.Snapshot value) { return value.source() + "|" + value.query() + "|" + value.collectionPageSize(); }
    private boolean resetsAvailable(String action) { return action.equals("SOURCE") || action.equals("IMPORT_LIVE"); }
    private List<BoxEditorSession.CardRow> rows() {
        return isSourcePane() ? (snapshot.source().equals(pane.name()) ? availableRows.rows() : List.of()) : snapshot.contents();
    }
    private int maxCardScroll() {
        int rowCount = Math.max(1, (rows().size() + columns() - 1) / columns());
        return Math.max(0, rowCount * cellHeight() - (gridBottom() - gridTop()));
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        PhoneLayout.Frame f = frame(); PhoneUi.shell(context, f);
        PhoneUi.header(context, textRenderer, f, "    卡盒", snapshot.totalCards() + "/60 张 · 背包与图鉴分开取卡");
        if (pane == Pane.RULES) renderRules(context, f, delta);
        else renderCards(context, f, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        String status = !message.isBlank() ? message : pending && !pendingAction.equals("PREFETCH") ? "等待服务器确认…"
            : pane == Pane.BOX ? "半透明为缺卡，点击补入 · 实卡点击取出" : isSourcePane() ? "左键从" + sourceLabel() + "加入卡盒 · 右键详情" : "";
        context.drawText(textRenderer, textRenderer.trimToWidth(status, f.innerWidth()), f.innerX(), f.bottom() - 41,
            message.isBlank() ? PhoneUi.MUTED : PhoneUi.ERROR, false);
    }

    private void renderCards(DrawContext context, PhoneLayout.Frame f, int mouseX, int mouseY, float delta) {
        int x = f.innerX(), w = f.innerWidth(), cols = columns(), cellW = w / cols, cellH = cellHeight();
        List<BoxEditorSession.CardRow> rows = rows();
        cardScroll.clamp(0, maxCardScroll()); int offset = (int) Math.round(cardScroll.render(delta));
        context.enableScissor(x, gridTop(), x + w, gridBottom());
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i); int cardX = x + i % cols * cellW, cardY = gridTop() + i / cols * cellH - offset;
            if (cardY + cellH <= gridTop() || cardY >= gridBottom()) continue;
            PhoneUi.card(context, cardX + 1, cardY, cellW - 3, cellH - 3, PhoneUi.SURFACE);
            if (row.missing()) CardPainter.ghostCard(context, textRenderer, artwork.get(row.definitionId()), row.nameZh(), cardX + 4, cardY + 3,
                cellW - 9, Math.max(18, cellH - 25));
            else CardPainter.card(context, textRenderer, artwork.get(row.definitionId()), row.nameZh(), cardX + 4, cardY + 3,
                cellW - 9, Math.max(18, cellH - 25), PhoneUi.BORDER);
            context.drawText(textRenderer, textRenderer.trimToWidth(row.nameZh(), cellW - 25), cardX + 4, cardY + cellH - 19, PhoneUi.INK, false);
            String count = (row.missing() ? "缺" : "×") + row.count(); context.drawText(textRenderer, count, cardX + cellW - textRenderer.getWidth(count) - 5, cardY + cellH - 19, row.missing() ? PhoneUi.ERROR : PhoneUi.BLUE, false);
            if (isSourcePane()) {
                String source = "来自" + sourceLabel();
                context.drawText(textRenderer, textRenderer.trimToWidth(source, cellW - 8), cardX + 4, cardY + cellH - 9, PhoneUi.MUTED, false);
            }
        }
        if (rows.isEmpty()) {
            String empty = pending ? "正在同步…" : pane == Pane.BOX ? "卡盒为空；从背包或图鉴加入卡牌" : sourceLabel() + "没有符合条件的可用卡牌";
            context.drawCenteredTextWithShadow(textRenderer, empty, f.x() + f.width() / 2, (gridTop() + gridBottom()) / 2, PhoneUi.MUTED);
        }
        context.disableScissor();
    }

    private void renderRules(DrawContext context, PhoneLayout.Frame f, float delta) {
        int x = f.innerX(), y = gridTop(), bottom = f.bottom() - 16;
        var selected = snapshot.rules().stream().filter(rule -> rule.id().equals(snapshot.rulesetId())).findFirst();
        if (selected.isEmpty()) { context.drawText(textRenderer, "所选规则包不可用", x, y, PhoneUi.ERROR, false); return; }
        var rule = selected.get();
        rulesScroll.clamp(0, maxRulesScroll(rule, bottom - y));
        PhoneUi.card(context, x, y, f.innerWidth(), 38, rule.canStart() ? PhoneUi.SURFACE_ALT : PhoneUi.SURFACE_ALT);
        context.drawText(textRenderer, rule.nameZh() + " · " + rule.statusZh(), x + 8, y + 7, rule.canStart() ? PhoneUi.GREEN : PhoneUi.ERROR, false);
        context.drawText(textRenderer, "版本 " + rule.version(), x + 8, y + 20, PhoneUi.MUTED, false);
        int lineY = y + 45 - (int) Math.round(rulesScroll.render(delta)); context.enableScissor(x, y + 42, f.right() - 10, bottom);
        for (String reason : rule.reasons()) for (var line : textRenderer.wrapLines(Text.literal("• " + reason), f.innerWidth() - 8)) {
            if (lineY >= y + 42 && lineY < bottom) context.drawText(textRenderer, line, x + 4, lineY, PhoneUi.INK, false); lineY += 11;
        }
        context.disableScissor();
    }

    private int maxRulesScroll(BoxEditorSession.RuleRow rule, int viewportHeight) {
        int lines = 0;
        for (String reason : rule.reasons()) lines += textRenderer.wrapLines(Text.literal("• " + reason), frame().innerWidth() - 8).size();
        return Math.max(0, 45 + lines * 11 - viewportHeight);
    }

    private int hit(double mouseX, double mouseY) {
        if (pane == Pane.RULES) return -1;
        PhoneLayout.Frame f = frame(); int cols = columns(), cellW = f.innerWidth() / cols;
        if (mouseX < f.innerX() || mouseX >= f.innerX() + cellW * cols || mouseY < gridTop() || mouseY >= gridBottom()) return -1;
        int index = (int) ((mouseY - gridTop() + cardScroll.value()) / cellHeight()) * cols + (int) ((mouseX - f.innerX()) / cellW);
        return index < rows().size() ? index : -1;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        int index = hit(x, y);
        if (index >= 0) {
            var row = rows().get(index);
            if (button == 0) { send(row.missing() ? "FILL_MISSING" : isSourcePane() ? "ADD" : "TAKE", row.definitionId()); return true; }
            if (button == 1) { openPreview(row.definitionId(), row.missing()); return true; }
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical == 0) return super.mouseScrolled(x, y, horizontal, vertical);
        if (pane == Pane.RULES) {
            var selected = snapshot.rules().stream().filter(rule -> rule.id().equals(snapshot.rulesetId())).findFirst();
            selected.ifPresent(rule -> rulesScroll.move(-vertical * 18, 0, maxRulesScroll(rule, frame().bottom() - 16 - gridTop())));
            return true;
        }
        cardScroll.move(-vertical * Math.max(36, cellHeight() * 0.72), 0, maxCardScroll()); return true;
    }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && searchField != null && searchField.isFocused()) { search(); return true; }
        if ((keyCode == 257 || keyCode == 335) && nameField != null && nameField.isFocused()) { rename(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void rename() { if (nameField != null) send("RENAME", nameField.getText()); }
    private void importLive() {
        String text = client.keyboard.getClipboard();
        if (text.length() > dev.hua.ptcglive.collection.LiveDeckParser.MAX_TEXT) { message = "剪贴板内容过长，请仅复制卡组文本"; return; }
        send("IMPORT_LIVE", text);
    }
    private void search() { if (searchField != null) request("PAGE", Integer.toString(pageSize()), 0, searchField.getText()); }

    private boolean shouldPrefetch() {
        int viewport = Math.max(1, gridBottom() - gridTop());
        return isSourcePane() && availableRows.hasMore()
            && cardScroll.target() >= Math.max(0, maxCardScroll() - viewport * 3);
    }
    private void prefetch() {
        if (pending || !availableRows.hasMore()) return;
        request("PREFETCH", Integer.toString(pageSize()), availableRows.nextBatch(), snapshot.query());
    }
    private void deleteEmpty() {
        if (!confirmDelete) { confirmDelete = true; message = "再次点击确认删除空盒及实体凭证"; clearAndInit(); }
        else send("DELETE_EMPTY", "");
    }
    private void nextRuleset() {
        var installed = snapshot.rules().stream().filter(rule -> !rule.version().equals("未知")).toList(); if (installed.isEmpty()) return;
        int current = -1; for (int i = 0; i < installed.size(); i++) if (installed.get(i).id().equals(snapshot.rulesetId())) current = i;
        send("RULESET", installed.get((current + 1) % installed.size()).id());
    }
    private void send(String action, String argument) { request(action, argument, snapshot.collectionPage(), snapshot.query()); }
    private void request(String action, String argument, int page, String query) {
        if (pending && !action.equals("REFRESH")) return;
        if(refreshedRows!=null && !action.equals("REFRESH_BATCH"))return;
        capture(); String wireAction = action.equals("PREFETCH")||action.equals("REFRESH_BATCH") ? "PAGE" : action;
        if (!action.equals("REFRESH")) { pending = true; pendingAction = action; }
        ClientPlayNetworking.send(new BoxIntentPayload(sessionId().toString(), snapshot.revision(), wireAction, argument, page, query));
        if (!action.equals("REFRESH") && !action.equals("PREFETCH") && !action.equals("REFRESH_BATCH")) clearAndInit();
    }
    private void openPreview(String definitionId, boolean missing) {
        CardItems.cards().stream().filter(card -> card.card().id().equals(definitionId)).findFirst()
            .ifPresentOrElse(card -> client.setScreen(new CardViewScreen(card.card(), this, List.of(new CardViewScreen.ServerAction(
                missing ? "补入缺卡" : isSourcePane() ? "从" + sourceLabel() + "加入卡盒" : "取回图鉴", () -> send(missing ? "FILL_MISSING" : isSourcePane() ? "ADD" : "TAKE", definitionId))))),
                () -> message = "卡面定义不存在：" + definitionId);
    }
    @Override public void tick() {
        super.tick(); rulesScroll.tick(); cardScroll.tick(); pollTicks++;
        if (!pending && pollTicks >= 2 && refreshedRows!=null)
            request("REFRESH_BATCH",Integer.toString(pageSize()),refreshedRows.nextBatch(),snapshot.query());
        else if (!pending && pollTicks >= 2 && shouldPrefetch()) prefetch();
        else if (pending && pollTicks >= REQUEST_TIMEOUT_TICKS) {
            pending = false;pollTicks=0;
            if(refreshedRows==null)send("REFRESH", "");
        }
    }
    private void returnToPhone(boolean notifyServer) {
        if (notifyServer) ClientPlayNetworking.send(new BoxIntentPayload(sessionId().toString(), snapshot.revision(), "CLOSE", "", 0, ""));
        artwork.close(); parent.resumeFromChild(); if (client != null) client.setScreen(parent);
    }
    @Override public void close() { returnToPhone(true); }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }
    @Override public boolean shouldPause() { return false; }
}
