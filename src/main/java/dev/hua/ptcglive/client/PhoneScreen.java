package dev.hua.ptcglive.client;

import dev.hua.ptcglive.PackItems;
import dev.hua.ptcglive.economy.PhoneView;
import dev.hua.ptcglive.network.PhoneIntentPayload;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Titanium portrait home and the existing shop, exchange and ledger views. */
public final class PhoneScreen extends Screen {
    private PhoneView view;
    private TextFieldWidget search, quantity;
    private String selectedPack = "", selectedMineral = "", message = "";
    private boolean confirmCheckout, pending;
    private final SmoothMotion transitionSlide = new SmoothMotion();
    private final SmoothMotion shopScroll = new SmoothMotion();
    private final SmoothMotion mineralScroll = new SmoothMotion();
    private final SmoothMotion historyScroll = new SmoothMotion();

    public PhoneScreen(PhoneView view) { super(Text.literal("PTCG CRAFT 手机")); this.view = view; }
    public String sessionId() { return view.sessionId(); }
    void resumeFromChild() { pending = false; message = ""; clearAndInit(); }
    void resumeAfterRecycling() { resumeFromChild(); request("REFRESH", "", 0, false); }

    public void update(PhoneView next, String notice) {
        if (next == null) {
            if (client != null && client.player != null && notice != null && !notice.isBlank()) client.player.sendMessage(Text.literal(notice), false);
            super.close(); return;
        }
        String oldTab = view.tab(), oldEra = view.era();
        view = next; message = notice == null ? "" : notice; pending = false;
        if (!oldEra.equals(next.era())) { shopScroll.jump(0); selectedPack = ""; confirmCheckout = false; }
        transitionSlide.jump(8); transitionSlide.target(0);
        if (!oldTab.equals(next.tab())) {
            shopScroll.jump(0); mineralScroll.jump(0); historyScroll.jump(0);
        }
        clearAndInit();
    }

    private PhoneLayout.Frame frame() { return PhoneLayout.forScreen(width, height); }
    private PhoneButton button(String label, int x, int y, int w, int h, int color, boolean light, Runnable action) {
        return addDrawableChild(new PhoneButton(x, y, w, h, label, color, light, ignored -> action.run()));
    }
    private PhoneButton cardButton(String label, String subtitle, int x, int y, int w, int h, int color, Runnable action) {
        return addDrawableChild(new PhoneButton(x, y, w, h, label, h >= 40 ? subtitle : "", color, true, ignored -> action.run()));
    }

    @Override protected void init() {
        PhoneLayout.Frame f = frame();
        button("×", f.right() - 30, f.y() + 10, 20, 18, PhoneUi.SURFACE_ALT, false, this::close);
        if ("HOME".equals(view.tab())) initHome(f);
        else {
            button("‹", f.x() + 10, f.y() + 10, 22, 18, PhoneUi.SURFACE_ALT, false, () -> request("TAB", "HOME", 0, false));
            switch (view.tab()) {
                case "SHOP" -> initShop(f);
                case "EXCHANGE" -> initExchange(f);
                case "HISTORY" -> initHistory(f);
                case "BATTLE" -> button("对战邀请",f.innerX(),f.bottom()-48,f.innerWidth(),20,PhoneUi.BLUE,true,()->BattleSocialScreen.open(this,null));
                case "SETTINGS" -> { }
                default -> request("TAB", "HOME", 0, false);
            }
        }
    }

    private void initHome(PhoneLayout.Frame f) {
        int x = f.innerX(), w = f.innerWidth(), gap = 6, assetY = f.y() + 43;
        int half = (w - gap) / 2;
        button("宝可币  " + view.balance(), x, assetY, half, 28, PhoneUi.GOLD, false, () -> request("TAB", "SHOP", 0, false));
        int minerals = view.minerals().stream().mapToInt(PhoneView.MineralRow::available).sum();
        button("可兑换矿物  " + minerals, x + half + gap, assetY, w - half - gap, 28, PhoneUi.SURFACE_ALT, false,
            () -> request("TAB", "EXCHANGE", 0, false));

        int gridTop = assetY + 35, quickY = f.bottom() - 31, gridBottom = quickY - 6;
        int cellW = (w - gap * 3) / 4;
        int rowH = Math.max(30, Math.min(108, (gridBottom - gridTop - gap) / 2));
        int startY = gridTop + Math.max(0, (gridBottom - gridTop - rowH * 2 - gap) / 2);
        String[] labels = {"卡包商城", "卡牌图鉴", "卡组管理", "矿物兑换", "卡牌回收", "交易记录", "设置", "对战"};
        String[] icons = {"shopping-bag", "book-open", "layers", "gem", "recycle", "receipt-text", "settings", "swords"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            var app = button(labels[i], x + (i % 4) * (cellW + gap), startY + (i / 4) * (rowH + gap),
                cellW, rowH, PhoneUi.SURFACE, false, () -> homeAction(index)).appIcon(icons[i]);
            if (i == 4) app.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("打开附近的回收站，需在 8 格内")));
        }

        var recent = PhoneRecentCards.latest();
        PhoneButton recentButton = button(recent == null ? "最近查看：暂无" : "最近查看：" + recent.nameZh(), x, quickY, half, 18,
            PhoneUi.SURFACE, false, () -> { if (recent != null && client != null) client.setScreen(new CardViewScreen(recent, this)); });
        recentButton.active = recent != null;
        button("继续整理卡盒", x + half + gap, quickY, w - half - gap, 18, PhoneUi.SURFACE, false,
            () -> request("OPEN_BOX", "", 0, false));
    }

    private void homeAction(int index) {
        switch (index) {
            case 0 -> request("TAB", "SHOP", 0, false);
            case 1 -> request("OPEN_COLLECTION", "", 0, false);
            case 2 -> request("OPEN_BOX", "", 0, false);
            case 3 -> request("TAB", "EXCHANGE", 0, false);
            case 4 -> request("OPEN_RECYCLE", "", 0, false);
            case 5 -> request("TAB", "HISTORY", 0, false);
            case 6 -> request("TAB", "SETTINGS", 0, false);
            case 7 -> request("TAB", "BATTLE", 0, false);
            default -> { }
        }
    }

    private void initShop(PhoneLayout.Frame f) {
        int x = f.innerX(), y = f.y() + 43, w = f.innerWidth();
        search = new TextFieldWidget(textRenderer, x, y, Math.max(60, w - 104), 18, Text.literal("搜索卡包"));
        search.setMaxLength(80); search.setText(view.search()); search.setPlaceholder(Text.literal("搜索卡包名称或编号")); addDrawableChild(search);
        button("搜索", x + w - 100, y, 46, 18, PhoneUi.BLUE, true, () -> request("SEARCH", search.getText(), 0, false));
        button("刷新", x + w - 50, y, 50, 18, PhoneUi.SURFACE_ALT, false, () -> request("REFRESH", "", 0, false));
        var categories = new java.util.ArrayList<String>();
        categories.add("");
        categories.addAll(dev.hua.ptcglive.packs.PackBrowseOrder.CATEGORIES);
        for (int i = 0; i < categories.size(); i++) {
            String era = categories.get(i);
            int left = x + (i % 4) * w / 4, right = x + (i % 4 + 1) * w / 4;
            boolean selected = era.equals(view.era());
            button(era.isBlank() ? "全部" : dev.hua.ptcglive.packs.PackBrowseOrder.label(era),
                left, y + 23 + (i / 4) * 22, right - left - 3, 18,
                selected ? PhoneUi.BLUE : PhoneUi.SURFACE_ALT, selected,
                () -> { if (!era.equals(view.era())) request(era.isBlank() ? "ERA" : "SHOP_ERA", era, 0, false); });
        }

        int bottom = f.bottom();
        int cartCount = view.cart().values().stream().mapToInt(Integer::intValue).sum();
        button(confirmCheckout ? "确认支付" : "购物车 " + cartCount + " 包", x, bottom - 49, Math.max(80, w - 76), 18,
            confirmCheckout ? PhoneUi.ORANGE : PhoneUi.BLUE, true, this::checkout);
        button("收藏", x + w - 70, bottom - 49, 70, 18, PhoneUi.SURFACE_ALT, false,
            () -> { if (!selectedPack.isBlank()) request("FAVORITE", selectedPack, 0, false); });
        quantity = new TextFieldWidget(textRenderer, x, bottom - 27, 34, 18, Text.literal("数量"));
        quantity.setMaxLength(9); quantity.setText("1"); addDrawableChild(quantity);
        int cursor = x + 38, quickWidth = Math.max(18, (w - 38 - 58) / 5);
        for (int quick : new int[]{1, 5, 10, 64, 100}) {
            int value = quick; button(Integer.toString(quick), cursor, bottom - 27, quickWidth - 2, 18, PhoneUi.SURFACE_ALT, false,
                () -> quantity.setText(Integer.toString(value))); cursor += quickWidth;
        }
        button("加入", x + w - 54, bottom - 27, 54, 18, PhoneUi.GREEN, true, this::addCart);
    }

    private void initExchange(PhoneLayout.Frame f) {
        int x = f.innerX(), y = f.bottom() - 27, w = f.innerWidth(), third = (w - 8) / 3;
        button("兑换 1", x, y, third, 18, PhoneUi.BLUE, true, () -> exchange(1));
        button("兑换 64", x + third + 4, y, third, 18, PhoneUi.BLUE, true, () -> exchange(64));
        button("全部兑换", x + 2 * (third + 4), y, w - 2 * (third + 4), 18, PhoneUi.ORANGE, true, () -> exchange(0));
    }
    private void initHistory(PhoneLayout.Frame f) { }

    private String pageTitle() {
        return switch (view.tab()) {
            case "SHOP" -> "卡包商城"; case "EXCHANGE" -> "矿物兑换"; case "HISTORY" -> "交易记录";
            case "BATTLE" -> "对战"; case "SETTINGS" -> "设置"; default -> "PTCG CRAFT 手机";
        };
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        PhoneLayout.Frame f = frame(); PhoneUi.shell(context, f);
        String subtitle = "HOME".equals(view.tab()) ? "卡牌 · 收藏 · 交易" : "宝可币 " + view.balance();
        PhoneUi.header(context, textRenderer, f, ("HOME".equals(view.tab()) ? "" : "    ") + pageTitle(), subtitle);
        context.getMatrices().push(); context.getMatrices().translate(transitionSlide.render(delta), 0, 0);
        switch (view.tab()) {
            case "SHOP" -> renderShop(context, f, delta);
            case "EXCHANGE" -> renderExchange(context, f, delta);
            case "HISTORY" -> renderHistory(context, f, delta);
            case "BATTLE" -> renderInfo(context, f, "对战仍由真实卡牌桌发起", "请在世界中右键卡牌对战桌，选择随身卡盒并进入大厅。手机不会绕过距离、卡盒锁定或规则检查。");
            case "SETTINGS" -> renderInfo(context, f, "当前没有独立手机设置", "界面会自动适配 GUI 缩放和窗口尺寸。卡牌、经济与存档设置继续沿用服务器现有配置。");
            default -> { }
        }
        context.getMatrices().pop(); super.render(context, mouseX, mouseY, delta);
        if (!message.isBlank()) {
            int toastY = switch (view.tab()) { case "SHOP" -> f.bottom() - 68; case "EXCHANGE" -> f.bottom() - 49; default -> f.bottom() - 16; };
            PhoneUi.card(context, f.innerX(), toastY, f.innerWidth(), 14, PhoneUi.SURFACE_ALT);
            context.drawText(textRenderer, textRenderer.trimToWidth(message, f.innerWidth() - 8), f.innerX() + 4, toastY + 3, PhoneUi.ERROR, false);
        }
    }

    private void renderShop(DrawContext context, PhoneLayout.Frame f, float delta) {
        int x = f.innerX(), y = f.y() + 110, w = f.innerWidth(), rowH = 40, bottom = f.bottom() - 54;
        shopScroll.clamp(0, maxShopScroll());
        int offset = (int) Math.round(shopScroll.render(delta));
        context.enableScissor(x, y, x + w, bottom);
        for (int i = 0; i < view.packs().size(); i++) {
            var row = view.packs().get(i); int rowY = y + i * rowH - offset; boolean selected = row.setId().equals(selectedPack);
            if (rowY + rowH <= y || rowY >= bottom) continue;
            PhoneUi.card(context, x, rowY, w, rowH - 3, selected ? PhoneUi.SELECTED : PhoneUi.SURFACE);
            context.drawItem(PackItems.get(row.setId()).getDefaultStack(), x + 7, rowY + 9);
            context.drawText(textRenderer, textRenderer.trimToWidth((row.favorite() ? "★ " : "") + row.nameZh(), w - 118), x + 30, rowY + 7, PhoneUi.INK, false);
            context.drawText(textRenderer, textRenderer.trimToWidth(dev.hua.ptcglive.packs.PackBrowseOrder.label(row.era()) + " · 右键查看卡池", w - 118), x + 30, rowY + 20, PhoneUi.MUTED, false);
            String price = row.price() + " 币" + (row.cartQuantity() > 0 ? " · 车" + row.cartQuantity() : "");
            context.drawText(textRenderer, price, x + w - textRenderer.getWidth(price) - 7, rowY + 14, PhoneUi.BLUE, false);
        }
        context.disableScissor();
    }

    private void renderExchange(DrawContext context, PhoneLayout.Frame f, float delta) {
        int x = f.innerX(), y = f.y() + 43, w = f.innerWidth(), bottom = f.bottom() - 32, rowH = 34;
        mineralScroll.clamp(0, maxMineralScroll()); int offset = (int) Math.round(mineralScroll.render(delta));
        context.enableScissor(x, y, x + w, bottom);
        for (int i = 0; i < view.minerals().size(); i++) {
            var row = view.minerals().get(i); int rowY = y + i * rowH - offset; boolean selected = row.itemId().equals(selectedMineral);
            if (rowY + rowH <= y || rowY >= bottom) continue;
            PhoneUi.card(context, x, rowY, w, rowH - 3, selected ? PhoneUi.SELECTED : PhoneUi.SURFACE);
            drawItem(context, row.itemId(), x + 7, rowY + 7);
            context.drawText(textRenderer, row.nameZh() + " ×" + row.available(), x + 31, rowY + 6, PhoneUi.INK, false);
            context.drawText(textRenderer, "1 个 = " + row.unitValue() + " 宝可币", x + 31, rowY + 18, PhoneUi.MUTED, false);
        }
        context.disableScissor();
    }

    private void renderHistory(DrawContext context, PhoneLayout.Frame f, float delta) {
        int x = f.innerX(), y = f.y() + 43, w = f.innerWidth(), bottom = f.bottom() - 14, rowH = 31;
        historyScroll.clamp(0, maxHistoryScroll()); int offset = (int) Math.round(historyScroll.render(delta));
        context.enableScissor(x, y, x + w, bottom);
        for (int i = 0; i < view.history().size(); i++) {
            var row = view.history().get(i); int rowY = y + i * rowH - offset;
            if (rowY + rowH <= y || rowY >= bottom) continue;
            PhoneUi.card(context, x, rowY, w, rowH - 3, PhoneUi.SURFACE);
            int color = row.delta() >= 0 ? PhoneUi.GREEN : PhoneUi.RED;
            String amount = (row.delta() >= 0 ? "+" : "") + row.delta();
            context.drawText(textRenderer, row.kind().equals("CARD_ORDER") ? "交易所订单" : row.kind().equals("CARD_APPRAISAL") ? "品质鉴定" : row.kind().equals("CARD_RENTAL") ? "租赁方块收益" : row.kind().equals("CARD_RECYCLE") ? "卡牌回收" : row.kind(), x + 7, rowY + 5, PhoneUi.INK, false);
            context.drawText(textRenderer, amount, x + w - textRenderer.getWidth(amount) - 7, rowY + 5, color, false);
            context.drawText(textRenderer, textRenderer.trimToWidth(row.detail() + " · 余额 " + row.balanceAfter(), w - 14), x + 7, rowY + 17, PhoneUi.MUTED, false);
        }
        context.disableScissor();
    }

    private int maxShopScroll() {
        PhoneLayout.Frame f = frame(); return Math.max(0, view.packs().size() * 40 - (f.bottom() - 54 - (f.y() + 110)));
    }
    private int maxMineralScroll() {
        PhoneLayout.Frame f = frame(); return Math.max(0, view.minerals().size() * 34 - (f.bottom() - 32 - (f.y() + 43)));
    }
    private int maxHistoryScroll() {
        PhoneLayout.Frame f = frame(); return Math.max(0, view.history().size() * 31 - (f.bottom() - 14 - (f.y() + 43)));
    }

    private void renderInfo(DrawContext context, PhoneLayout.Frame f, String heading, String body) {
        int x = f.innerX(), y = f.y() + 52, w = f.innerWidth(); PhoneUi.card(context, x, y, w, 84, PhoneUi.SURFACE);
        context.drawText(textRenderer, heading, x + 10, y + 11, PhoneUi.INK, false);
        int lineY = y + 29;
        for (var line : textRenderer.wrapLines(Text.literal(body), w - 20)) { context.drawText(textRenderer, line, x + 10, lineY, PhoneUi.MUTED, false); lineY += 11; }
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if ((button == 0 || button == 1) && "SHOP".equals(view.tab())) {
            PhoneLayout.Frame f = frame(); int y = f.y() + 110, bottom = f.bottom() - 54;
            if (mouseX >= f.innerX() && mouseX < f.right() - 10 && mouseY >= y && mouseY < bottom) {
                int index = (int) ((mouseY - y + shopScroll.value()) / 40);
                if (index >= 0 && index < view.packs().size()) { selectedPack = view.packs().get(index).setId(); if(button==1)ClientPlayNetworking.send(new dev.hua.ptcglive.network.PackIntentPayload(selectedPack,"BROWSE")); return true; }
            }
        } else if (button == 0 && "EXCHANGE".equals(view.tab())) {
            PhoneLayout.Frame f = frame(); int y = f.y() + 43, bottom = f.bottom() - 32;
            if (mouseX >= f.innerX() && mouseX < f.right() - 10 && mouseY >= y && mouseY < bottom) {
                int index = (int) ((mouseY - y + mineralScroll.value()) / 34);
                if (index >= 0 && index < view.minerals().size()) { selectedMineral = view.minerals().get(index).itemId(); return true; }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical == 0) return super.mouseScrolled(x, y, horizontal, vertical);
        if ("SHOP".equals(view.tab())) {
            shopScroll.move(-vertical * 30, 0, maxShopScroll());
            return true;
        }
        if ("EXCHANGE".equals(view.tab())) { mineralScroll.move(-vertical * 26, 0, maxMineralScroll()); return true; }
        if ("HISTORY".equals(view.tab())) { historyScroll.move(-vertical * 24, 0, maxHistoryScroll()); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    private void addCart() {
        if (selectedPack.isBlank()) { message = "请先选择一个卡包"; return; }
        int amount = positive(quantity == null ? "" : quantity.getText()); if (amount > 0) request("CART_ADD", selectedPack, amount, false);
    }
    private void checkout() {
        if (view.cart().isEmpty()) { message = "购物车为空"; return; }
        if (!confirmCheckout) { confirmCheckout = true; message = "请再次点击确认支付；背包不足时不会扣币"; clearAndInit(); return; }
        request("CHECKOUT", "", 0, true); confirmCheckout = false;
    }
    private void exchange(int amount) { if (selectedMineral.isBlank()) { message = "请先选择矿物"; return; } request("EXCHANGE", selectedMineral, amount, true); }
    private int positive(String text) { try { int value = Integer.parseInt(text); if (value > 0) return value; } catch (NumberFormatException ignored) { } message = "请输入正整数数量"; return 0; }
    private void request(String action, String argument, int amount, boolean transaction) {
        if (pending) return; pending = true;
        ClientPlayNetworking.send(new PhoneIntentPayload(view.sessionId(), action, argument, amount, transaction ? UUID.randomUUID().toString() : ""));
    }
    private static void drawItem(DrawContext context, String itemId, int x, int y) {
        context.drawItem(new ItemStack(Registries.ITEM.get(Identifier.of(itemId))), x, y);
    }
    @Override public void tick() {
        super.tick(); transitionSlide.tick(); shopScroll.tick(); mineralScroll.tick(); historyScroll.tick();
    }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { ClientPlayNetworking.send(new PhoneIntentPayload(view.sessionId(), "CLOSE", "", 0, "")); super.close(); }
}
