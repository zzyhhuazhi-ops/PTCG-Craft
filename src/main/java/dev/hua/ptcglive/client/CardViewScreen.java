package dev.hua.ptcglive.client;

import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.CardItems;
import dev.hua.ptcglive.network.CardHistoryNetworking;
import dev.hua.ptcglive.rules.CardBoxService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.google.gson.Gson;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.List;
import static dev.hua.ptcglive.client.PtcgLiveTheme.*;

/** At most one full-resolution texture is retained; removed() releases it. */
public final class CardViewScreen extends Screen {
    private static final Identifier TEXTURE = Identifier.of(CardItems.MOD_ID, "card_preview");
    /** Screen-only adapter for an already-issued server action. */
    public record ServerAction(String labelZh, Runnable submit) { }
    private final CardCatalog.Entry card;
    private final Screen parent;
    private final List<ServerAction> serverActions;
    private final List<String> actionHints;
    private boolean loaded;
    private CardArtLoader.Ticket imageRequest;
    private boolean imageFailed;
    private boolean highDefinition;
    private boolean highDefinitionFound;
    private long resourceGeneration = -1;
    private int imageWidth, imageHeight;
    private double zoom = 1;
    private int actionPage;
    private CardBoxService.CardHistory history;
    private final dev.hua.ptcglive.view.HistoryRequestState historyRequest=new dev.hua.ptcglive.view.HistoryRequestState();
    private int historyScroll,historyOverflow,historyX,historyY,historyPanelWidth,historyPanelHeight;

    public static void initializeHistory(){
        ClientPlayNetworking.registerGlobalReceiver(CardHistoryNetworking.Reply.ID,(reply,ctx)->ctx.client().execute(()->{
            if(ctx.client().currentScreen instanceof CardViewScreen shown&&shown.card.id().equals(reply.card())){
                shown.history=new Gson().fromJson(reply.json(),CardBoxService.CardHistory.class);
                shown.historyRequest.receive();
            }
        }));
    }

    public CardViewScreen(CardCatalog.Entry card) {
        this(card, null, List.of());
    }
    public CardViewScreen(CardCatalog.Entry card, Screen parent) {
        this(card, parent, List.of());
    }
    public CardViewScreen(CardCatalog.Entry card, Screen parent, List<ServerAction> serverActions) {
        this(card,parent,serverActions,List.of());
    }
    public CardViewScreen(CardCatalog.Entry card, Screen parent, List<ServerAction> serverActions, List<String> actionHints) {
        super(Text.literal(card.label()));
        this.card = card; this.parent = parent;
        this.serverActions = List.copyOf(serverActions == null ? List.of() : serverActions);
        this.actionHints = List.copyOf(actionHints == null ? List.of() : actionHints);
        PhoneRecentCards.remember(card);
    }
    public Screen parentScreen() { return parent; }
    @Override public void close() { if (parent == null) super.close(); else client.setScreen(parent); }
    @Override public void tick() {
        if(historyRequest.tick())requestHistory();
        if (parent instanceof MatchRegionScreen region) region.tick();
        if (resourceGeneration != CardArtResources.generation()) { removed(); loadCard(); }
        else if(!loaded&&!imageFailed&&imageRequest==null)loadCard();
        super.tick();
    }

    @Override protected void init() {
        if(!historyRequest.received()&&!historyRequest.timedOut())requestHistory();
        if (!loaded) loadCard();
        if (phoneMode()) {
            addDrawableChild(new PhoneButton(frame().x() + 10, frame().y() + 10, 22, 18, "‹", PhoneUi.SURFACE_ALT, false, ignored -> close()));
            addPhoneActionButtons();
        }
        else addServerActionButtons();
        if(!(parent instanceof MatchDisplay)) {
            int bx=phoneMode()?frame().innerX():8, by=phoneMode()?frame().bottom()-21:height-23;
            int buttonWidth=phoneMode()?(frame().innerWidth()-4)/2:Math.min(88,(width-20)/2);
            addDrawableChild(ButtonWidget.builder(Text.literal("推荐卡组"),b->net.minecraft.client.gui.screen.ConfirmLinkScreen.open(this,CardAcquisitionScreen.DECKS_URL)).dimensions(bx,by,buttonWidth,18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("获取卡包"),b->client.setScreen(new CardAcquisitionScreen(card,this,List.of()))).dimensions(bx+buttonWidth+4,by,buttonWidth,18).build());
        }
    }
    private void requestHistory(){
        if(ClientPlayNetworking.canSend(CardHistoryNetworking.Query.ID))ClientPlayNetworking.send(new CardHistoryNetworking.Query(card.id()));
        else historyRequest.unsupported();
    }
    private int historyWidth(){return Math.min(148,Math.max(88,width/4));}
    private void drawHistory(DrawContext context,int x,int y,int panelWidth,int panelHeight){
        historyX=x;historyY=y;historyPanelWidth=panelWidth;historyPanelHeight=panelHeight;
        context.fill(x,y,x+panelWidth,y+panelHeight,0xD9101B29);
        context.enableScissor(x,y,x+panelWidth,y+panelHeight);
        int lineY=y+8-historyScroll;
        var lines=new java.util.ArrayList<String>();lines.add("个人履历");
        if(!historyRequest.received()){
            lines.add(historyRequest.timedOut()?"履历读取失败":"正在读取履历…");
            if(historyRequest.timedOut())lines.add("点击此处重试");
        }
        else {
            String first=history==null?"尚无获得记录":history.firstAcquired()==0?"历史时间未知":java.time.Instant.ofEpochMilli(history.firstAcquired()).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            long matches=history==null?0:history.matches(),wins=history==null?0:history.wins();
            lines.add("首次获得：");lines.add(first);
            lines.add("参战场次："+matches);lines.add("累计获胜："+wins);
            lines.add("已完成场次："+(history==null?0:history.completedMatches()));
            lines.add("胜率："+(history==null?"—":history.winRate()));
            lines.add("击倒次数："+(history==null?0:history.knockouts()));
            if(history!=null&&history.legacyResultsUnknown())lines.add("旧对局结果不完整，胜率仅统计更新后完成的对局");
        }
        var wrapped=lines.stream().map(line->textRenderer.wrapLines(Text.literal(line),Math.max(1,panelWidth-16))).toList();
        int contentHeight=16+wrapped.stream().mapToInt(rows->rows.size()*12+3).sum()+3;
        historyOverflow=Math.max(0,contentHeight-panelHeight);historyScroll=Math.clamp(historyScroll,0,historyOverflow);
        for(int i=0;i<lines.size();i++){
            for(var line:wrapped.get(i)){
                context.drawText(textRenderer,line,x+8,lineY,i==0?0xFFE17A:0xD1E5F5,false);lineY+=12;
            }
            lineY+=i==0?6:3;
        }
        context.disableScissor();
        if(historyOverflow>0){int thumb=Math.max(8,panelHeight*panelHeight/contentHeight);int sy=y+(panelHeight-thumb)*historyScroll/historyOverflow;context.fill(x+panelWidth-3,sy,x+panelWidth-1,sy+thumb,0xFF93B6DA);}
    }
    private boolean overHistory(double x,double y){return x>=historyX&&x<historyX+historyPanelWidth&&y>=historyY&&y<historyY+historyPanelHeight;}

    private void addPhoneActionButtons() {
        if (serverActions.isEmpty()) return;
        PhoneLayout.Frame frame = frame(); int x = frame.innerX(), width = frame.innerWidth(), gap = 4, columns = 2;
        int buttonWidth = (width - gap) / columns, rows = (serverActions.size() + columns - 1) / columns;
        int top = frame.bottom() - 23 - rows * 20;
        for (int i = 0; i < serverActions.size(); i++) {
            ServerAction action = serverActions.get(i); int column = i % columns, row = i / columns;
            addDrawableChild(new PhoneButton(x + column * (buttonWidth + gap), top + row * 20,
                column == 0 ? buttonWidth : width - buttonWidth - gap, 18, action.labelZh(), PhoneUi.BLUE, true, ignored -> {
                    action.submit().run(); close();
                }));
        }
    }

    private boolean phoneMode() {
        return parent instanceof PhoneScreen || parent instanceof PhoneCollectionScreen || parent instanceof PhoneCardBoxScreen;
    }
    private PhoneLayout.Frame frame() { return PhoneLayout.forScreen(width, height); }

    private void addServerActionButtons() {
        if (serverActions.isEmpty() && actionHints.isEmpty()) return;
        int actionWidth = actionAreaWidth();
        int actionX = actionAreaX();
        var layout = actionLayout();
        actionPage = layout.page();
        for (int index = layout.first(); index < layout.end(); index++) {
            if(index>=serverActions.size())continue; // explanatory text, deliberately not a disabled attack button
            ServerAction action = serverActions.get(index);
            String label = action.labelZh().startsWith("撤退 → ") ? action.labelZh()
                : action.labelZh().startsWith("使用特性：") ? action.labelZh().replaceFirst("^使用特性：", "发动特性：")
                : "发动技能：" + compactActionName(action.labelZh());
            var button = ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(label, actionWidth - 8)), ignored -> {
                action.submit().run();
                close();
            }).dimensions(actionX, layout.rowY(index), actionWidth, 20).build();
            button.setTooltip(Tooltip.of(Text.literal(action.labelZh())));
            addDrawableChild(button);
        }
        if (layout.pages() > 1) {
            int half = (actionWidth - 4) / 2;
            var previous = addDrawableChild(ButtonWidget.builder(Text.literal("上一页"), ignored -> changeActionPage(-1))
                .dimensions(actionX, layout.navigationY(), half, 20).build());
            var next = addDrawableChild(ButtonWidget.builder(Text.literal("下一页"), ignored -> changeActionPage(1))
                .dimensions(actionX + half + 4, layout.navigationY(), half, 20).build());
            previous.active = actionPage > 0;
            next.active = actionPage + 1 < layout.pages();
        }
    }

    private CardActionLayout actionLayout() { return CardActionLayout.forScreen(width, height, serverActions.size()+actionHints.size(), actionPage); }
    private void changeActionPage(int delta) {
        actionPage = Math.clamp(actionPage + delta, 0, actionLayout().pages() - 1);
        clearChildren();
        addServerActionButtons();
    }

    private boolean hasActionColumn() { return !serverActions.isEmpty() || !actionHints.isEmpty(); }
    private int actionAreaWidth() { return !hasActionColumn() ? 0 : Math.min(190, Math.max(118, width / 3)); }
    /** Left edge of the attack-button column; its “可用技能” header is centred on the same column. */
    private int actionAreaX() { return Math.max(4, width - actionAreaWidth() - 6); }

    // Bands, so text never sits on the card (even when zoomed): title (y 6) · card area
    // [IMAGE_TOP, height - HUD_RESERVE). The screen is transparent and the vanilla HUD is still
    // drawn underneath, so the bottom HUD_RESERVE pixels (hotbar 22, experience bar, hearts and
    // food from height - 39) stay clear of the card. Status and hint sit in that bottom band's
    // right corner, right of the centred hotbar (HOTBAR_HALF_WIDTH each side of the centre).
    private static final int TITLE_Y = 6, IMAGE_TOP = 19, HUD_RESERVE = 40, HOTBAR_HALF_WIDTH = 91;
    private static final int CORNER_MARGIN = 6, CORNER_LINE = 10, CORNER_LINES = 3;
    /** Side margin kept inside the viewport so centred text never touches the attack column. */
    private static final int TEXT_MARGIN = 8;
    private int imageBottom() { return Math.max(IMAGE_TOP + 1, height - HUD_RESERVE); }
    /** The longest option that fits, or the last one trimmed. */
    private Text fitting(int width, List<String> options) {
        for (String option : options) if (textRenderer.getWidth(option) <= width) return Text.literal(option);
        return Text.literal(textRenderer.trimToWidth(options.getLast(), Math.max(0, width)));
    }
    private int cardViewportWidth() { return Math.max(72, width - actionAreaWidth() - (hasActionColumn() ? 10 : 0)); }
    private static String compactActionName(String label) {
        String value = label.replaceFirst("^(攻击|复制攻击)：", "");
        int detail = value.indexOf('（');
        return detail < 0 ? value : value.substring(0, detail);
    }

    /**
     * A card preview is a direct toggle, not a confirmation dialog.  Keeping
     * the closing gesture on the same mouse button means opening a held card
     * with right click and closing it with the next right click works from
     * every parent screen without exposing a redundant "完成" button.
     */
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(button==0&&overHistory(mouseX,mouseY)&&historyRequest.timedOut()) {historyRequest.retry();historyScroll=0;requestHistory();return true;}
        if (button == 1) {
            close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void loadCard() {
        if(imageRequest!=null)return;
        resourceGeneration=CardArtResources.generation();imageFailed=false;
        var resources=client.getResourceManager();var selection=CardArtResources.select(resources,card.id());highDefinitionFound=selection.highDefinition();
        imageRequest=CardArtLoader.request(resources,selection.resource(),CardArtResources.thumbnail(resources,card.id()),selection.highDefinition(),0,TEXTURE,
            art->{imageRequest=null;imageWidth=art.width();imageHeight=art.height();highDefinition=art.highDefinition();loaded=true;},
            ()->{imageRequest=null;imageFailed=true;});
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (phoneMode()) { renderPhone(context); super.render(context, mouseX, mouseY, delta); return; }
        // Intentionally no fullscreen tint: PTCG Craft surfaces leave the world visible.
        int viewportLeft=historyWidth()+16;
        int viewportWidth = Math.max(1,cardViewportWidth()-viewportLeft), textWidth = Math.max(0, viewportWidth - TEXT_MARGIN * 2);
        int cardCenter=viewportLeft+viewportWidth/2;
        int imageBottom = imageBottom(), availableHeight = Math.max(1, imageBottom - IMAGE_TOP - 4);
        if (loaded) {
            double scale = Math.min((viewportWidth - 24.0) / imageWidth, (double) availableHeight / imageHeight) * zoom;
            int drawWidth = Math.max(1, (int) (imageWidth * scale));
            int drawHeight = Math.max(1, (int) (imageHeight * scale));
            // When zoomed, move the pointer over the viewing area to inspect all edges.
            double horizontal = Math.clamp((double) (mouseX-viewportLeft) / Math.max(1, viewportWidth), 0, 1);
            double vertical = Math.clamp((double) (mouseY - IMAGE_TOP) / Math.max(1, imageBottom - IMAGE_TOP), 0, 1);
            int x = drawWidth<=viewportWidth?Math.max(viewportLeft,(cardViewportWidth()-drawWidth)/2):viewportLeft+(int)((viewportWidth-drawWidth)*horizontal);
            if(drawWidth<=viewportWidth)cardCenter=x+drawWidth/2;
            int y = IMAGE_TOP + 2 + (drawHeight <= availableHeight ? (availableHeight - drawHeight) / 2
                : (int) ((availableHeight - drawHeight) * vertical));
            // The card is clipped to its own band, so a zoomed card never covers the title, status or hint.
            context.enableScissor(viewportLeft, IMAGE_TOP, viewportLeft+viewportWidth, imageBottom);
            context.drawTexture(TEXTURE, x, y, 0, 0, drawWidth, drawHeight, drawWidth, drawHeight);
            context.disableScissor();
        } else {
            context.drawCenteredTextWithShadow(textRenderer, imageFailed?Text.translatable("screen.ptcglive_mc.failed"):Text.literal("正在加载卡图…"),
                viewportLeft+viewportWidth / 2, (IMAGE_TOP + imageBottom) / 2 - 4, ERROR);
        }
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(title.getString(), textWidth)),
            cardCenter, TITLE_Y, TEXT);
        drawHistory(context,8,IMAGE_TOP+2,historyWidth(),Math.min(156,availableHeight));
        drawCornerInfo(context);
        if (hasActionColumn()) context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("技能／特性／撤退" + (actionLayout().pages() > 1 ? " " + (actionPage + 1) + "/" + actionLayout().pages() : "")),
            actionAreaX() + actionAreaWidth() / 2, 8, ACCENT);
        super.render(context, mouseX, mouseY, delta);
        var actions=actionLayout();
        for(int i=Math.max(actions.first(),serverActions.size());i<actions.end();i++) {
            String hint=actionHints.get(i-serverActions.size());int rowY=actions.rowY(i);
            context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(hint,actionAreaWidth()-8),actionAreaX()+4,rowY+5,NOTICE);
            if(mouseX>=actionAreaX()&&mouseX<actionAreaX()+actionAreaWidth()&&mouseY>=rowY&&mouseY<rowY+20)
                context.drawOrderedTooltip(textRenderer,textRenderer.wrapLines(Text.literal(hint),Math.max(80,Math.min(240,width-16))),mouseX,mouseY);
        }
    }

    private void renderPhone(DrawContext context) {
        PhoneLayout.Frame frame = frame(); PhoneUi.shell(context, frame);
        PhoneUi.header(context, textRenderer, frame, "    卡牌详情", card.nameZh() + " · " + card.id());
        int x = frame.innerX(), width = frame.innerWidth(), imageTop = frame.y() + 43;
        int actionRows = (serverActions.size() + 1) / 2;
        int actionsHeight = actionRows * 20;
        int informationHeight = Math.min(118, Math.max(58, frame.height() / 5) + actionsHeight);
        int imageBottom = frame.bottom() - informationHeight - 10;
        PhoneUi.card(context, x, imageTop, width, Math.max(30, imageBottom - imageTop), PhoneUi.SURFACE);
        int historyWidth=Math.min(124,Math.max(80,width/3)),cardLeft=x+historyWidth+6,cardWidth=Math.max(1,width-historyWidth-6);
        if (loaded) {
            int availableWidth = Math.max(1, cardWidth - 8), availableHeight = Math.max(1, imageBottom - imageTop - 10);
            double scale = Math.min((double) availableWidth / imageWidth, (double) availableHeight / imageHeight) * zoom;
            int drawWidth = Math.max(1, (int) (imageWidth * scale)), drawHeight = Math.max(1, (int) (imageHeight * scale));
            int drawX = cardLeft + (cardWidth - drawWidth) / 2, drawY = imageTop + 5 + Math.max(0, (availableHeight - drawHeight) / 2);
            context.enableScissor(cardLeft, imageTop + 2, x + width - 2, imageBottom - 2);
            context.drawTexture(TEXTURE, drawX, drawY, 0, 0, drawWidth, drawHeight, drawWidth, drawHeight);
            context.disableScissor();
        } else context.drawCenteredTextWithShadow(textRenderer, imageFailed?"卡面暂不可用":"正在加载卡图…", cardLeft + cardWidth / 2,
            (imageTop + imageBottom) / 2, PhoneUi.ERROR);
        drawHistory(context,x+2,imageTop+2,historyWidth-2,Math.max(1,imageBottom-imageTop-4));

        int infoY = imageBottom + 5; PhoneUi.card(context, x, infoY, width, frame.bottom() - infoY - 7, PhoneUi.SURFACE);
        int metadataBottom = frame.bottom() - 27 - actionsHeight, lineY = infoY + 7;
        context.drawText(textRenderer, textRenderer.trimToWidth(card.nameZh(), width - 16), x + 8, lineY, PhoneUi.INK, false); lineY += 12;
        if (lineY <= metadataBottom) { context.drawText(textRenderer, "编号  " + card.printedNumber(), x + 8, lineY, PhoneUi.MUTED, false); lineY += 12; }
        if (lineY <= metadataBottom) { context.drawText(textRenderer, "系列  " + card.set() + " · " + card.era(), x + 8, lineY, PhoneUi.MUTED, false); lineY += 12; }
        if (!card.rarityZh().isBlank() && lineY <= metadataBottom) context.drawText(textRenderer,
            textRenderer.trimToWidth("稀有度 " + card.rarityZh() + " · 标记 " + (card.regulationMark().isEmpty() ? "无/未核对" : card.regulationMark()), width - 16),
            x + 8, lineY, PhoneUi.BLUE, false);
        String source = highDefinition ? "资源包卡面" : highDefinitionFound ? "卡面读取失败，使用缩略图" : "卡牌缩略图";
    }

    /**
     * Status and hint, right-aligned in the bottom-right corner beside the hotbar: the hint on the
     * last line, the status (up to two lines) above it, each falling back to shorter wording.
     */
    private void drawCornerInfo(DrawContext context) {
        int right = width - CORNER_MARGIN, available = Math.max(0, right - (width / 2 + HOTBAR_HALF_WIDTH + 4));
        String pack = CardArtResources.requiredHighDefinitionPack(card.id(), card.set());
        List<List<String>> statusChoices = !loaded ? (imageFailed ? List.of(List.of("卡面不可用：" + card.id()), List.of("卡面不可用")) : List.of(List.of("正在加载卡图…")))
            : highDefinition ? List.of(List.of("已加载高清原图：" + imageWidth + " × " + imageHeight), List.of("高清原图 " + imageWidth + " × " + imageHeight), List.of("高清原图"))
            : highDefinitionFound ? List.of(List.of("高清图读取失败，暂用缩略图"), List.of("高清图读取失败", "暂用缩略图"), List.of("缩略图"))
            : List.of(List.of("当前为缩略图；所需高清包：" + pack), List.of("当前为缩略图", "所需高清包：" + pack), List.of("当前为缩略图", pack), List.of("当前为缩略图"), List.of("缩略图"));
        List<String> status = statusChoices.getLast();
        for (var choice : statusChoices) if (choice.stream().allMatch(line -> textRenderer.getWidth(line) <= available)) { status = choice; break; }
        String hint = Text.translatable("screen.ptcglive_mc.hint").getString();
        String shownHint = fitting(available, List.of(hint, "滚轮缩放 · Esc 关闭", "Esc 关闭")).getString();
        List<String> lines = new java.util.ArrayList<>(status);
        lines.add(shownHint);
        while (lines.size() > CORNER_LINES) lines.removeFirst();
        int y = height - CORNER_MARGIN - lines.size() * CORNER_LINE + 1;
        for (String line : lines) {
            String shown = textRenderer.trimToWidth(line, available);
            context.drawTextWithShadow(textRenderer, shown, right - textRenderer.getWidth(shown), y, TEXT_DIM);
            y += CORNER_LINE;
        }
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if(overHistory(mouseX,mouseY)){historyScroll=Math.clamp(historyScroll-(int)(vertical*24),0,historyOverflow);return true;}
        if (hasActionColumn() && mouseX >= actionAreaX() && actionLayout().pages() > 1) {
            if (vertical != 0) changeActionPage(vertical < 0 ? 1 : -1);
            return true;
        }
        zoom = Math.clamp(zoom + vertical * 0.25, 1, 4);
        return true;
    }

    @Override public void removed() {
        if(imageRequest!=null){imageRequest.cancel();imageRequest=null;}imageFailed=false;
        if (loaded && client != null) {
            client.getTextureManager().destroyTexture(TEXTURE);
            loaded = false;
        }
    }

    @Override public boolean shouldPause() { return false; }

    // Screen.render calls this AFTER our card render. The vanilla implementation
    // blurs everything already on screen, including the card and its title.
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }
}
