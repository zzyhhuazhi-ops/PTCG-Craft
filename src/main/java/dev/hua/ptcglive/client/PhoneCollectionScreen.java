package dev.hua.ptcglive.client;

import dev.hua.ptcglive.collection.CollectionProgress;
import dev.hua.ptcglive.collection.CollectionView;
import dev.hua.ptcglive.collection.CollectionFilters;
import dev.hua.ptcglive.network.CollectionIntentPayload;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Phone presentation of the existing collection session; all mutations still use CollectionNetworking. */
public final class PhoneCollectionScreen extends Screen {
    private final PhoneScreen parent;
    private final CardBoxArtCache art = new CardBoxArtCache(24, 256);
    private PagedRows<CollectionView.Row> loadedRows = new PagedRows<>();
    private PagedRows<CollectionView.Row> refreshedRows;
    private int refreshThroughBatch;
    private boolean refreshNeeded;
    private CollectionView view;
    private TextFieldWidget search;
    private String selected = "", message = "", pendingAction = "";
    private boolean pending, overview;
    private boolean multiSelect;
    private final java.util.Set<String> selectedCards = new java.util.LinkedHashSet<>();
    private CollectionFilterPanel filterPanel;
    private String filterQuery="", optionQuery="";
    private boolean filterDirty;
    private final SmoothMotion cardScroll = new SmoothMotion();
    private final SmoothMotion overviewScroll = new SmoothMotion();
    private int ticks;

    public PhoneCollectionScreen(CollectionView view, PhoneScreen parent) {
        super(Text.literal("图鉴")); this.view = view; this.parent = parent;
        loadedRows.accept(dataKey(view), view.page(), view.pages(), view.rows(), true);
    }
    public String sessionId() { return view.sessionId(); }

    public void update(CollectionView next, String notice) {
        if (next == null) {
            if (client != null && client.player != null && notice != null && !notice.isBlank()) client.player.sendMessage(Text.literal(notice), false);
            returnToPhone(false); return;
        }
        String completedAction = pendingAction; String draft = search == null ? filterPanel == null ? next.query() : filterQuery : search.getText();
        if(completedAction.equals("SEARCH"))draft=next.query();
        boolean changedQuery = !dataKey(view).equals(dataKey(next));
        view = next; pending = false; ticks = 0; message = notice == null ? "" : notice;
        if (changedQuery) refreshedRows = null;
        boolean refreshing = refreshedRows != null && (completedAction.equals("REFRESH") || completedAction.equals("REFRESH_PAGE"));
        if (refreshing) {
            refreshedRows.accept(dataKey(next), next.page(), next.pages(), next.rows(), next.page() == 0);
            if (refreshedRows.nextBatch() > Math.min(refreshThroughBatch, next.pages() - 1)) {
                loadedRows = refreshedRows;
                refreshedRows = null;
            }
        } else if (!List.of("ADD","BOX_TAKE","DEPOSIT","DEPOSIT_SELECTED","DEPOSIT_ALL","WITHDRAW","PREVIEW").contains(completedAction)) {
            loadedRows.accept(dataKey(next), next.page(), next.pages(), next.rows(), changedQuery);
        }
        if (next.updatedRows() != null) {
            var updates = new java.util.HashMap<String, CollectionView.Row>();
            next.updatedRows().forEach(row -> updates.put(row.card().id(), row));
            loadedRows.update(row -> updates.getOrDefault(row.card().id(), row));
            selectedCards.removeIf(id -> updates.containsKey(id) && updates.get(id).inventory() == 0);
        }
        if (changedQuery) { cardScroll.jump(0); selectedCards.clear(); }
        // Moving the last copy into a box changes membership and batch boundaries.
        // Rebuild in the background without temporarily shrinking the visible list.
        if (List.of("ADD","BOX_TAKE","DEPOSIT","DEPOSIT_SELECTED","DEPOSIT_ALL","WITHDRAW").contains(completedAction)) refreshNeeded = true;
        pendingAction = "";
        clearAndInit(); if (search != null) search.setText(draft);
    }

    private PhoneLayout.Frame frame() { return PhoneLayout.forScreen(width, height); }
    private PhoneButton button(String label, int x, int y, int w, int color, boolean light, Runnable action) {
        return addDrawableChild(new PhoneButton(x, y, w, 18, label, color, light, ignored -> action.run()));
    }

    @Override protected void init() {
        if(filterPanel!=null){initFilters();return;}
        PhoneLayout.Frame f = frame(); int x = f.innerX(), w = f.innerWidth(), y = f.y() + 43;
        button("‹", f.x() + 10, f.y() + 10, 22, PhoneUi.SURFACE_ALT, false, this::close);
        button("×", f.right() - 30, f.y() + 10, 20, PhoneUi.SURFACE_ALT, false, this::close);
        search = new TextFieldWidget(textRenderer, x, y, Math.max(60, w - 52), 18, Text.literal("搜索卡牌"));
        search.setMaxLength(80); search.setText(view.query()); search.setPlaceholder(Text.literal(overview?"搜索卡包名称或系列":"搜索名称、编号、属性或标记")); addDrawableChild(search);
        button("搜索", x + w - 48, y, 48, PhoneUi.BLUE, true, () -> { if (overview) overviewScroll.jump(0); else request("PAGE", "", 0); });

        int row = y + 22, third = (w - 8) / 3;
        button(overview ? "返回卡牌" : "收藏进度", x, row, third, overview ? PhoneUi.BLUE : PhoneUi.SURFACE_ALT, overview, () -> {
            overview = !overview; cardScroll.jump(0); overviewScroll.jump(0); clearAndInit();
        });
        button("筛选／排序 " + view.filters().activeCount(), x + third + 4, row, third, PhoneUi.SURFACE_ALT, false, this::openFilters).active=!overview;
        var box = view.boxes().stream().filter(value -> value.id().equals(view.selectedBox())).findFirst();
        PhoneButton boxButton = button("卡盒：" + box.map(CollectionView.Box::name).orElse("暂无"), x + 2 * (third + 4), row,
            w - 2 * (third + 4), PhoneUi.SURFACE_ALT, false, this::nextBox);
        boxButton.active = !view.boxes().isEmpty();

        if (!overview) {
            String[] filters = {"全部", "未存入", "已存入"};
            for (int i = 0; i < filters.length; i++) {
                final String mode = new String[]{"ALL","NOT_DEPOSITED","DEPOSITED"}[i];
                button(filters[i], x + i * (third + 4), row + 22, third,
                    view.filters().ownership().equals(mode) ? PhoneUi.BLUE : PhoneUi.SURFACE_ALT, view.filters().ownership().equals(mode),
                    () -> searchWith(view.filters().withOwnership(mode),false));
            }
            int bottom = f.bottom(), actionY = bottom - 54, quarter = (w - 12) / 4;
            button(multiSelect ? "已选 " + selectedCards.size() : "多选", x, bottom - 76, third,
                PhoneUi.SURFACE_ALT, false, () -> { multiSelect = !multiSelect; selectedCards.clear(); clearAndInit(); });
            button("批量存入", x + third + 4, bottom - 76, third, PhoneUi.BLUE, true, () -> {
                if (selectedCards.isEmpty()) { message = "请多选背包中的卡牌"; return; }
                request("DEPOSIT_SELECTED", new com.google.gson.Gson().toJson(selectedCards), 0);
            });
            button("背包全存", x + 2 * (third + 4), bottom - 76, third, PhoneUi.GREEN, true,
                () -> request("DEPOSIT_ALL", "", 0));
            button("存入", x, actionY, quarter, PhoneUi.BLUE, true, () -> request("DEPOSIT", selected, 1));
            button("取出", x + quarter + 4, actionY, quarter, PhoneUi.BLUE, true, () -> request("WITHDRAW", selected, 1));
            button("加入盒", x + 2 * (quarter + 4), actionY, quarter, PhoneUi.GREEN, true, () -> request("ADD", selected, 1));
            button("清空选择", x + 3 * (quarter + 4), actionY, w - 3 * (quarter + 4), PhoneUi.SURFACE_ALT, false,
                () -> { selected = ""; selectedCards.clear(); clearAndInit(); });
            if (progressMode()) button("返回全部", x, bottom - 32, 72, PhoneUi.SURFACE_ALT, false, () -> { search.setText(""); request("PROGRESS", "", 0); });
            button("刷新", x + w - 52, bottom - 32, 52, PhoneUi.SURFACE_ALT, false, () -> request("REFRESH", "", 0));
        }
    }

    private void openFilters() {
        if(pending||refreshedRows!=null||refreshNeeded){message="正在同步，请稍候";return;}
        filterQuery=search.getText().strip();optionQuery="";
        filterPanel=new CollectionFilterPanel(view,()->{filterDirty=true;ticks=0;});filterDirty=true;ticks=0;clearAndInit();
    }
    private void initFilters() {
        search=null;var f=frame();int x=f.innerX(),w=f.innerWidth();filterPanel.layout(f);
        boolean counted=filterPanel.draft().equals(view.previewFilters())&&filterQuery.equals(view.previewQuery())&&!filterDirty;
        button(counted?"查看 "+view.previewCount()+" 张卡":"应用筛选",x,f.y()+41,w/2,PhoneUi.BLUE,true,()->{
            var applied=filterPanel.draft();if(searchWith(applied,false)){filterPanel=null;filterDirty=false;clearAndInit();search.setText(filterQuery);}
        }).active=!pending;
        button("取消",x+w/2+4,f.y()+41,w/4-4,0xff777d84,true,this::cancelFilters);
        button("清除",x+w*3/4+4,f.y()+41,w/4-4,0xff9b9fa4,true,()->{filterPanel.clear();clearAndInit();});
        var field=new TextFieldWidget(textRenderer,x,f.y()+66,w,18,Text.literal("查找筛选选项"));
        field.setMaxLength(60);field.setText(optionQuery);field.setPlaceholder(Text.literal("查找系列、属性或其他选项"));
        field.setChangedListener(q->{optionQuery=q;filterPanel.query(q);});addDrawableChild(field);
    }
    private void cancelFilters() {filterPanel=null;filterDirty=false;clearAndInit();search.setText(filterQuery);}
    private boolean searchWith(CollectionFilters filter,boolean preview) {
        if(pending||refreshedRows!=null){message="正在同步，请稍候";return false;}
        pending=true;pendingAction=preview?"PREVIEW":"SEARCH";ticks=0;
        String query=filterPanel!=null?filterQuery:search==null?view.query():search.getText();
        ClientPlayNetworking.send(new CollectionIntentPayload(view.sessionId(),view.revision(),pendingAction,new com.google.gson.Gson().toJson(filter),0,query));return true;
    }

    private void nextBox() {
        if (view.boxes().isEmpty()) return;
        int index = 0;
        for (int i = 0; i < view.boxes().size(); i++) if (view.boxes().get(i).id().equals(view.selectedBox())) index = i;
        request("BOX", view.boxes().get((index + 1) % view.boxes().size()).id(), 0);
    }
    private boolean progressMode() { return view.progressPack() != null && !view.progressPack().isEmpty(); }
    private String dataKey(CollectionView value) {
        return value.query() + "|" + value.filters() + "|" + value.progressPack() + "|" + value.progressFilter();
    }
    private List<CollectionView.Row> rows() {
        return loadedRows.rows().stream()
            .filter(row -> view.filters().acceptsOwnership(row.inventory(),row.collection())).toList();
    }
    private int columns() { return frame().width() < 360 ? 3 : 4; }
    private int gridTop() { return frame().y() + (progressMode() ? 122 : 110); }
    private int gridBottom() { return frame().bottom() - 80; }
    private int cellHeight() { return Math.max(55, Math.min(102, (gridBottom() - gridTop()) / Math.max(1, Math.min(3, rowsRequired())))); }
    private int rowsRequired() { return Math.max(1, (rows().size() + columns() - 1) / columns()); }
    private int maxCardScroll() { return Math.max(0, rowsRequired() * cellHeight() - (gridBottom() - gridTop())); }
    private int maxOverviewScroll() {
        PhoneLayout.Frame f = frame(); return Math.max(0, matchingPacks().size() * 39 - (f.bottom() - 17 - (f.y() + 88)));
    }

    private int hit(double mouseX, double mouseY) {
        PhoneLayout.Frame f = frame(); int x = f.innerX(), w = f.innerWidth(), cols = columns(), cellW = w / cols;
        if (filterPanel!=null || overview || mouseX < x || mouseX >= x + cellW * cols || mouseY < gridTop() || mouseY >= gridBottom()) return -1;
        int row = (int) ((mouseY - gridTop() + cardScroll.value()) / cellHeight());
        int index = row * cols + (int) ((mouseX - x) / cellW); return index < rows().size() ? index : -1;
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        PhoneLayout.Frame f = frame(); PhoneUi.shell(context, f);
        if(filterPanel!=null){
            PhoneUi.panel(context,f.innerX(),f.y()+11,f.innerWidth(),24,PhoneUi.SURFACE_ALT);
            context.drawText(textRenderer,"卡库筛选／排序",f.innerX()+7,f.y()+19,0xffffffff,false);
            filterPanel.render(context,textRenderer,mouseX,mouseY,delta);super.render(context,mouseX,mouseY,delta);
            context.drawText(textRenderer,"同类多选 · 跨类组合 · 滚轮连续浏览",f.innerX(),f.bottom()-14,PhoneUi.MUTED,false);return;
        }
        PhoneUi.header(context, textRenderer, f, "    图鉴", "检索 "+view.total()+" 张 · 仅背包与图鉴，不计卡盒");
        if (overview) renderProgress(context, f, delta);
        else renderCards(context, f, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        String footer = message.isBlank() ? (multiSelect ? "点击勾选；批量存入所选卡的全部背包份数" : "左键选择 · 右键详情 · 滚轮浏览") : message;
        context.drawText(textRenderer, textRenderer.trimToWidth(footer, f.innerWidth() - 58), f.innerX(), f.bottom() - 11,
            message.isBlank() ? PhoneUi.MUTED : PhoneUi.ERROR, false);
        int index = hit(mouseX, mouseY);
        if (index >= 0) {
            var row = rows().get(index);
            context.drawTooltip(textRenderer, CardRentalInfo.append(List.of(Text.literal(row.card().label()), Text.literal(row.type() + " · " + row.card().set()),
                Text.literal(dev.hua.ptcglive.rules.PrintedRegulationMarks.labelZh(row.card().id())),
                Text.literal("背包 " + row.inventory() + " · 图鉴 " + row.collection()),
                Text.literal(row.discovered() ? "曾发现；历史记录不等于当前持有" : "未发现")),row.card()), mouseX, mouseY);
        }
    }

    private void renderCards(DrawContext context, PhoneLayout.Frame f, int mouseX, int mouseY, float delta) {
        if (progressMode()) {
            view.progress().stream().filter(p -> p.setId().equals(view.progressPack())).findFirst().ifPresent(p -> {
                String summary = p.nameZh() + " · " + p.collected() + "/" + p.total() + " · 缺 " + p.missing() + " · 重复 " + p.duplicates();
                context.drawText(textRenderer, textRenderer.trimToWidth(summary, f.innerWidth()), f.innerX(), f.y() + 109, PhoneUi.BLUE, false);
            });
        }
        int x = f.innerX(), w = f.innerWidth(), cols = columns(), cellW = w / cols, cellH = cellHeight();
        List<CollectionView.Row> rows = rows();
        cardScroll.clamp(0, maxCardScroll()); int offset = (int) Math.round(cardScroll.render(delta));
        context.enableScissor(x, gridTop(), x + w, gridBottom());
        for (int index = 0; index < rows.size(); index++) {
            var row = rows.get(index);
            int cardX = x + index % cols * cellW, cardY = gridTop() + index / cols * cellH - offset;
            if (cardY + cellH <= gridTop() || cardY >= gridBottom()) continue;
            int held = row.inventory() + row.collection();
            boolean marked = multiSelect ? selectedCards.contains(row.card().id()) : row.card().id().equals(selected);
            PhoneUi.card(context, cardX + 1, cardY, cellW - 3, cellH - 3, marked ? PhoneUi.SELECTED : PhoneUi.SURFACE);
            CardPainter.card(context, textRenderer, art.get(row.card().id()), row.card().nameZh(), cardX + 4, cardY + 3,
                cellW - 9, Math.max(14, cellH - 45), marked ? PhoneUi.GOLD : PhoneUi.BORDER);
            if(held==0)context.fill(cardX+4,cardY+3,cardX+cellW-5,cardY+Math.max(17,cellH-42),0x85000000);
            context.drawText(textRenderer, textRenderer.trimToWidth((marked && multiSelect ? "✓ " : "") + row.card().nameZh(), cellW - 8), cardX + 4, cardY + cellH - 40, PhoneUi.INK, false);
            String storage = row.collection() > 0 ? "已存入 " + row.collection() : "未存入";
            context.drawText(textRenderer, textRenderer.trimToWidth(storage, cellW - 8), cardX + 4, cardY + cellH - 30, row.collection() > 0 ? PhoneUi.GREEN : PhoneUi.MUTED, false);
            context.drawText(textRenderer, held == 0 ? "未持有" : "背包 " + row.inventory(), cardX + 4, cardY + cellH - 20, held == 0 ? PhoneUi.ERROR : PhoneUi.BLUE, false);
            context.drawText(textRenderer,textRenderer.trimToWidth(CardRentalInfo.summary(row.card()),cellW-8),cardX+4,cardY+cellH-10,PhoneUi.BLUE,false);
        }
        context.disableScissor();
        if(rows.isEmpty())context.drawText(textRenderer,"没有符合条件的卡牌，请调整筛选",x+4,gridTop()+8,PhoneUi.MUTED,false);
    }

    private List<CollectionProgress.Summary> matchingPacks() {
        String query = search == null ? "" : search.getText().strip().toLowerCase(Locale.ROOT);
        return view.progress().stream().filter(p -> (p.nameZh() + " " + p.setId()).toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    private void renderProgress(DrawContext context, PhoneLayout.Frame f, float delta) {
        var packs = matchingPacks(); int top = f.y() + 88, bottom = f.bottom() - 17, rowH = 39;
        overviewScroll.clamp(0, maxOverviewScroll()); int offset = (int) Math.round(overviewScroll.render(delta));
        context.enableScissor(f.innerX(), top, f.right() - 10, bottom);
        for (int i = 0; i < packs.size(); i++) {
            var p = packs.get(i); int y = top + i * rowH - offset;
            if (y + rowH <= top || y >= bottom) continue;
            PhoneUi.card(context, f.innerX(), y, f.innerWidth(), rowH - 3, PhoneUi.SURFACE);
            context.drawText(textRenderer, textRenderer.trimToWidth(p.nameZh(), f.innerWidth() - 14), f.innerX() + 7, y + 5, PhoneUi.INK, false);
            String info = p.collected() + "/" + p.total() + " · 缺 " + p.missing() + " · 重复 " + p.duplicates() + " · " + String.format(Locale.ROOT, "%.1f%%", p.percent());
            context.drawText(textRenderer, textRenderer.trimToWidth(info, f.innerWidth() - 14), f.innerX() + 7, y + 17, PhoneUi.MUTED, false);
            context.fill(f.innerX() + 7, y + 29, f.right() - 17, y + 32, PhoneUi.SURFACE_ALT);
            context.fill(f.innerX() + 7, y + 29, f.innerX() + 7 + (int) ((f.innerWidth() - 14) * p.percent() / 100), y + 32, PhoneUi.BLUE);
        }
        context.disableScissor();
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if(filterPanel!=null){if(button==0&&filterPanel.click(x,y))return true;return super.mouseClicked(x,y,button);}
        if (overview && button == 0) {
            PhoneLayout.Frame f = frame(); int top = f.y() + 88, rowH = 39;
            if (x >= f.innerX() && x < f.right() - 10 && y >= top && y < f.bottom() - 17) {
                int index = (int) ((y - top + overviewScroll.value()) / rowH); var packs = matchingPacks();
                if (index < packs.size() && !pending) { overview = false; search.setText(""); request("PROGRESS", packs.get(index).setId(), 0); return true; }
            }
        }
        int index = hit(x, y);
        if (index >= 0) {
            var row = rows().get(index);
            if (button == 0) {
                selected = row.card().id();
                if (multiSelect) {
                    if (row.inventory() == 0) message = "这张卡不在背包中";
                    else if (!selectedCards.remove(selected)) selectedCards.add(selected);
                    clearAndInit();
                }
                return true;
            }
            if (button == 1 && client != null) { client.setScreen(new CardAcquisitionScreen(row.card(), this, detailActions(row))); return true; }
        }
        return super.mouseClicked(x, y, button);
    }

    private List<CardViewScreen.ServerAction> detailActions(CollectionView.Row row) {
        var actions = new java.util.ArrayList<CardViewScreen.ServerAction>(); String id = row.card().id();
        if (row.inventory() > 0) actions.add(new CardViewScreen.ServerAction("存入图鉴", () -> request("DEPOSIT", id, 1)));
        if (row.collection() > 0) actions.add(new CardViewScreen.ServerAction("取出到背包", () -> request("WITHDRAW", id, 1)));
        if (row.inventory() + row.collection() > 0 && !view.selectedBox().isBlank()) actions.add(new CardViewScreen.ServerAction("加入卡盒", () -> request("ADD", id, 1)));
        return List.copyOf(actions);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical == 0) return super.mouseScrolled(x, y, horizontal, vertical);
        if(filterPanel!=null)return filterPanel.scroll(vertical);
        if (overview) { overviewScroll.move(-vertical * 29, 0, maxOverviewScroll()); return true; }
        cardScroll.move(-vertical * Math.max(34, cellHeight() * 0.72), 0, maxCardScroll());
        return true;
    }

    private boolean shouldPrefetch() {
        int viewport = Math.max(1, gridBottom() - gridTop());
        return filterPanel==null && !overview && loadedRows.hasMore() && cardScroll.target() >= Math.max(0, maxCardScroll() - viewport * 3);
    }
    private void prefetch() {
        if (pending || !loadedRows.hasMore()) return;
        pending = true; pendingAction = "PREFETCH"; ticks = 0;
        ClientPlayNetworking.send(new CollectionIntentPayload(view.sessionId(), view.revision(), "PAGE", "", loadedRows.nextBatch(), view.query()));
    }

    private void request(String action, String id, int amount) {
        if (pending || refreshedRows != null) { message = "正在同步数据，请稍候再操作"; return; }
        if (action.equals("PROGRESS")) selected = "";
        if (Arrays.asList("DEPOSIT", "WITHDRAW", "ADD", "BOX_TAKE").contains(action) && id.isEmpty()) { message = "请先选择卡牌"; return; }
        if (action.equals("REFRESH")) {
            refreshThroughBatch = Math.max(0, loadedRows.nextBatch() - 1);
            refreshedRows = new PagedRows<>();
        }
        pending = true; pendingAction = action; ticks = 0;
        ClientPlayNetworking.send(new CollectionIntentPayload(view.sessionId(), view.revision(), action, id, amount, search == null ? "" : search.getText()));
    }
    @Override public void tick() {
        super.tick(); cardScroll.tick(); overviewScroll.tick(); ticks++;
        if(filterPanel!=null)filterPanel.tick();
        if(!pending&&filterPanel!=null&&filterDirty&&ticks>=4){filterDirty=false;searchWith(filterPanel.draft(),true);clearAndInit();}
        else if (!pending && ticks >= 2 && refreshedRows != null) {
            pending = true; pendingAction = "REFRESH_PAGE"; ticks = 0;
            ClientPlayNetworking.send(new CollectionIntentPayload(view.sessionId(), view.revision(), "PAGE", "", refreshedRows.nextBatch(), view.query()));
        } else if (!pending && ticks >= 2 && refreshNeeded) {
            refreshNeeded = false; request("REFRESH", "", 0);
        } else if (!pending && ticks >= 2 && shouldPrefetch()) prefetch();
        else if (pending && ticks >= 80) { pending = false; refreshedRows = null; request("REFRESH", "", 0); }
    }
    private void returnToPhone(boolean notifyServer) {
        if (notifyServer) ClientPlayNetworking.send(new CollectionIntentPayload(view.sessionId(), view.revision(), "CLOSE", "", 0, ""));
        art.close(); if (parent != null) parent.resumeFromChild(); if (client != null) client.setScreen(parent);
    }
    @Override public void close() { if(filterPanel!=null){cancelFilters();return;}returnToPhone(true); }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }
    @Override public boolean shouldPause() { return false; }
}
