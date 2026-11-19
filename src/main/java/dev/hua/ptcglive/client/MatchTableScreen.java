package dev.hua.ptcglive.client;

import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.CardItems;
import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import dev.hua.ptcglive.engine.MatchClientView;
import dev.hua.ptcglive.engine.MatchPhase;
import dev.hua.ptcglive.engine.MatchResult;
import dev.hua.ptcglive.engine.MatchSession;
import dev.hua.ptcglive.engine.effect.EffectTrigger;
import dev.hua.ptcglive.engine.field.BattlefieldState.PokemonView;
import dev.hua.ptcglive.engine.field.SpecialCondition;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.network.MatchIntentPayload;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.view.EffectCardPage;
import dev.hua.ptcglive.view.EffectChoicePage;
import dev.hua.ptcglive.view.CopyAttackChoicePage;
import dev.hua.ptcglive.view.MatchActionMatcher;
import dev.hua.ptcglive.view.MatchRegionModel;
import dev.hua.ptcglive.view.MatchPresentation;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.IntConsumer;
import static dev.hua.ptcglive.client.PtcgLiveTheme.*;

/**
 * PTCG Live 风格的对战桌（红蓝桌垫）。拖放、点击只是在服务器已经下发的操作里
 * 按可见卡的锚点做筛选；所有效果仍由服务器结算与复核，客户端从不自造操作。
 */
final class MatchTableScreen extends Screen implements MatchDisplay {
    private static final Map<String, CardCatalog.Entry> ART = CardItems.cards().stream()
        .collect(java.util.stream.Collectors.toUnmodifiableMap(c -> c.card().id(), c -> c.card()));
    private static final int TEXT=BattleUi.INK, TEXT_SOFT=BattleUi.MUTED, TEXT_DIM=BattleUi.MUTED,
        ACCENT=0xFF277F9C, ACCENT_SOFT=0xFF93C8D4, SEAT_OWN=0xFF247BA8, SEAT_OPPONENT=0xFFA36077,
        NOTICE=0xFF935D2F, ERROR=0xFFAD4651, SUCCESS=0xFF328665;
    private static final String END_TURN_LABEL = "结束回合", CONCEDE_LABEL = "认输并结束本局";
    private static final String PRIZE_LABEL = "拿取背面朝上的奖赏卡：位置 ";

    /** 画在桌上、可点击或拖动的一张卡。 */
    private record Placed(MatchRegionModel.Card card, int x, int y, int width, int height) {
        boolean contains(double px, double py) { return px >= x && px < x + width && py >= y && py < y + height; }
    }
    /** 空桌面落点：它们本身不产生操作，只用来在服务器选项里筛选。 */
    private enum DropZone { OWN_ACTIVE, OWN_BENCH, ARENA }
    /** 一次拖放对应多个服务器操作时，在落点弹出的小菜单。 */
    private record Popup(int x, int y, List<MatchSession.Option> options) { }
    /** 点开的弃牌区／放逐区（只读）；zoneId 是 MatchRegionModel 的区域编号。 */
    private record PileView(String zoneId, String titleZh) { }
    private enum OverlayKind { EFFECT, MULLIGAN, PILE }
    /** 中间弹层这一页的内容。 */
    private record Overlay(OverlayKind kind, String titleZh, String detailZh, List<MatchRegionModel.Card> cards, int page, int pages) { }
    /** 弹层里放卡的网格。 */
    private record Grid(int x, int y, int cardWidth, int cardHeight, int stepX, int stepY, int columns, int rows) {
        int capacity() { return columns * rows; }
        int cardX(int index) { return x + (index % columns) * stepX; }
        int cardY(int index) { return y + (index / columns) * stepY; }
    }
    private record Line(String text, int color) { }

    private final MatchScreen parent;
    /** Bounded, battle-only linear filtering keeps moving/tilting faces stable at GUI scale. */
    private final CardBoxArtCache artwork = new CardBoxArtCache(64, 512, true);
    private MatchSession.Snapshot snapshot;
    private MatchRegionModel model;
    private PtcgLiveTableLayout cachedLayout;
    private boolean perspective=true, cameraDebug;
    private BattleTablePreset tablePreset=BattleTablePreset.B;
    private PocketTableCamera.Settings cameraSettings=tablePreset.settings();
    private PocketTableCamera cachedCamera;
    private final Map<String,Double> tableHover=new HashMap<>();
    private long hoverUpdated;
    private final BattleTableEnvironment tableEnvironment=new BattleTableEnvironment();

    private MatchRegionModel.Card dragging;
    private MatchRegionModel.Card selectedSource;
    /** 开始拖动时那张卡所在的位置（升起的手牌按升起后的位置算）。 */
    private Rect dragOrigin;
    private int dragWidth, dragHeight;
    private double grabX,grabY;
    private long dragStarted;
    private boolean draggingCounter;
    private String message = "";
    private int flowPage, selectionPage, mulliganPage, pilePage, popupPage;
    private boolean menuOpen, concedeArmed, endTurnArmed, effectCollapsed;
    private Popup popup;
    private BattleCardPanel cardPanel;
    private PileView pileView;
    private int hoveredHand = -1;
    private int selectedPrizeSlot = -1;
    private long prizeChoiceStarted,prizeSelectedAt;
    /** 提示区里文字能写到的最低 y（再往下是按钮），init 时算好。 */
    private int promptTextBottom;
    private record AnimatedCue(MatchPresentation.Cue cue, long started, Rect previousTarget,
                               int previousWidth, int previousHeight, Rect previousLogical, int coinIndex, int coinCount,
                               long batch, EnergyType type, String previousCard, int coinHeadsThrough, int previousDamage,
                               EnergyTransferAnimation.Link energyTransfer,EnergyDiscardAnimation.Slot energyDiscard,int toolIndex,UUID toolInstance,int toolReturnOrder,StadiumAnimation.Departure stadium,StadiumAnimation.Departure rejectedStadium,DiscardAnimation.Arrival pileArrival) { }
    private final List<AnimatedCue> animations = new ArrayList<>();
    private final Map<AnimatedCue,List<CardInstance>> drawnCards=new HashMap<>();
    private final List<UUID> visibleNumberTargets=new ArrayList<>();
    private static final SpecialCondition[] CONDITIONS=SpecialCondition.values();
    private final Map<AnimatedCue,BattleAnimationEngine.Definition> animationDefinitions=new HashMap<>();
    private final BattleAudio battleAudio = new BattleAudio();
    private final BattlePresentationClock presentationClock=new BattlePresentationClock();
    private BattleAnimationEngine.Quality animationQuality=BattleAnimationEngine.Quality.HIGH;
    private boolean shakeEnabled=true;
    private AnimatedCue lastElectricCue;
    private final LightningAnimationRenderer.Pool electricPool=new LightningAnimationRenderer.Pool();
    private final Map<AnimatedCue,LightningAnimationRenderer.State> electricEffects=new HashMap<>();
    private boolean soundEnabled=true,logOpen;
    private String attentionKey="";
    private int logOffset,handOffset;
    private long sentAt;
    private long frameNow;
    private long introStarted;
    private long resultStarted;
    private long orderedBatchEnd;
    private boolean awaitingServer, disconnected, socialWaiting;
    private boolean presentationLocked, controlsWaitingForCue;
    private UUID displayedReveal;
    private long revealStarted;
    private final CardSelectionAnimation cardSelectionAnimation=new CardSelectionAnimation();
    private String displayedChoice="";
    private long choiceStarted;

    MatchTableScreen(MatchSession.Snapshot snapshot) { this(null, snapshot); }
    MatchTableScreen(MatchScreen parent, MatchSession.Snapshot snapshot) {
        super(Text.literal("卡牌对战桌")); this.parent = parent; this.snapshot = snapshot; this.model = MatchRegionModel.from(snapshot);
        if(snapshot.game().turnNumber()<=1&&snapshot.game().result()==null)introStarted=presentationClock.now();
        if(snapshot.game().result()!=null)resultStarted=presentationClock.now();
    }
    @Override public UUID matchId() { return snapshot.game().matchId(); }
    public void socialNotice(String notice){socialWaiting=false;message=notice;clearAndInit();}

    // ================= 按钮 =================

    @Override protected void init() {
        presentationLocked=battleInputLocked();
        controlsWaitingForCue=finitePresentationLocked();
        initControls();
        if(presentationLocked)for(var child:children())
            if(child instanceof net.minecraft.client.gui.widget.ClickableWidget widget)widget.active=false;
    }

    private void initControls() {
        String attention=dev.hua.ptcglive.view.BattleAttention.key(snapshot);
        if(!attention.isEmpty()&&!attention.equals(attentionKey)&&soundEnabled)
            battleAudio.enqueue(snapshot.game().phase()==MatchPhase.PRIZE_SELECTION?"prize":"turn",presentationClock.now(),.8f);
        attentionKey=attention;
        dragging=null;dragOrigin=null;draggingCounter=false;
        var layout = layout(); int k = layout.ui();
        if (cardPanel != null) {
            var shown = cardPanel;
            shown.resize(width, height,tableMode());
            for (var button : shown.buttons(index -> {
                if (cardPanel != shown || shown.revision != snapshot.revision() || awaitingServer || disconnected) return;
                if (cardFaceOptions(shown.card).stream().noneMatch(o -> o.index() == index)) return;
                cardPanel = null; selectedSource = null; submit(index); clearAndInit();
            }, shown::close, this::clearAndInit)) addDrawableChild(button);
            return;
        }
        if (snapshot.game().result()!=null) { addResultButtons(layout); return; }
        // Choice sheets own the foreground; ordinary turn buttons must not cover their tray.
        if((selectionOverlay()||choiceOverlay())&&!finitePresentationLocked()&&overlay()!=null){
            promptTextBottom=layout.prompt().bottom();addOverlayButtons(layout,overlay(),flowOptions());return;
        }
        // The menu owns input: underlying turn buttons can overlap it in tall/narrow windows.
        if(menuOpen&&!battleInputLocked()){
            promptTextBottom=layout.prompt().bottom();addMenuButtons(layout);return;
        }
        addDrawableChild(new TableButton(layout.menuButton(), "菜单", TableButton.Style.NORMAL, k, b -> {
            menuOpen = !menuOpen; concedeArmed = false; clearAndInit();
        }));
        if (canSwitchSeat()&&!effectCollapsed)
            addDrawableChild(new TableButton(layout.switchButton(), "切换视角", TableButton.Style.NORMAL, k, b -> switchSeat()));
        var endTurn = option(END_TURN_LABEL);
        var end = new TableButton(layout.endTurn(), endTurnArmed?"仍然结束":END_TURN_LABEL, TableButton.Style.END_TURN, k,
            b -> endTurn.ifPresent(option -> {
                boolean otherActions=snapshot.options().stream().anyMatch(o->o.enabled()&&!o.labelZh().equals(END_TURN_LABEL)&&!o.labelZh().equals(CONCEDE_LABEL));
                if(otherActions&&!endTurnArmed){endTurnArmed=true;message="仍有合法动作；可以继续操作，或点击“仍然结束”。";clearAndInit();}
                else {endTurnArmed=false;submit(option.index());}
            }));
        end.active = endTurn.isPresent() && popup == null && pileView == null && !awaitingServer && !disconnected;
        addDrawableChild(end);
        promptTextBottom = layout.prompt().bottom();
        if(battleInputLocked()){
            if((selectionOverlay()||choiceOverlay())&&!finitePresentationLocked())addOverlayButtons(layout,overlay(),flowOptions());
            return;
        }
        if (popup != null) addPopupButtons(layout);
        else if (pileView != null) addOverlayButtons(layout, pileOverlay(), List.of());
        else if (overlay() != null) addOverlayButtons(layout, overlay(), flowOptions());
        else addPromptButtons(layout);
        if(effectCollapsed)addDrawableChild(new TableButton(layout.switchButton(),"恢复选择",TableButton.Style.PRIMARY,k,b->{effectCollapsed=false;clearAndInit();}));
        if(cameraDebug)addCameraButtons();
    }

    private boolean canSwitchSeat() { return snapshot.practice() && snapshot.controllableSeats().size() == 2; }
    private void switchSeat() {
        if(battleInputLocked())return;
        if(awaitingServer||disconnected){ message="请等待服务器确认当前操作。";return; }
        UUID current = snapshot.game().viewingSeat();
        UUID next = snapshot.controllableSeats().stream().filter(seat -> !seat.equals(current)).findFirst().orElseThrow();
        ClientPlayNetworking.send(new MatchIntentPayload(matchId().toString(), next.toString(), Math.toIntExact(snapshot.revision()), -1));
    }

    /** 菜单：刷新 · 认输（要再点一次确认）· 关闭对战桌。 */
    private void addMenuButtons(PtcgLiveTableLayout layout) {
        int k = layout.ui(), height = layout.buttonHeight(), step = height + 3 * k;
        int width = Math.max(layout.rightColumnWidth(), 64 * k), x = layout.screenWidth() - layout.margin() - width;
        int y = menuTop(layout);
        addDrawableChild(new TableButton(x, y, width, height, "刷新", TableButton.Style.NORMAL, k, b -> {
            menuOpen = false; refresh(); clearAndInit();
        }));
        var concede = option(CONCEDE_LABEL);
        var concedeButton = new TableButton(x, y + step, width, height, concedeArmed ? "确认认输" : "认输",
            concedeArmed ? TableButton.Style.PRIMARY : TableButton.Style.NORMAL, k, b -> {
                if (!concedeArmed) { concedeArmed = true; clearAndInit(); return; }
                menuOpen = false; concedeArmed = false;
                concede.ifPresent(option -> submit(option.index()));
                clearAndInit();
            });
        concedeButton.active = concede.isPresent();
        if (concedeArmed) concedeButton.setTooltip(Tooltip.of(Text.literal("再点一次就认输，本局立即结束。")));
        addDrawableChild(concedeButton);
        addDrawableChild(new TableButton(x, y + 2 * step, width, height, "关闭对战桌", TableButton.Style.NORMAL, k, b -> close()));
        addDrawableChild(new TableButton(x,y+3*step,width,height,"对局记录",TableButton.Style.NORMAL,k,b->{logOpen=true;logOffset=0;menuOpen=false;clearAndInit();}));
        addDrawableChild(new TableButton(x,y+4*step,width,height,"动画："+presentationClock.speed()+"×",TableButton.Style.NORMAL,k,b->{presentationClock.toggle();clearAndInit();}));
        addDrawableChild(new TableButton(x,y+5*step,width,height,soundEnabled?"音效：开":"音效：关",TableButton.Style.NORMAL,k,b->{soundEnabled=!soundEnabled;if(!soundEnabled)battleAudio.clear();clearAndInit();}));
        addDrawableChild(new TableButton(x,y+6*step,width,height,"特效画质："+animationQuality.label(),TableButton.Style.NORMAL,k,b->{animationQuality=animationQuality.next();clearAndInit();}));
        addDrawableChild(new TableButton(x,y+7*step,width,height,shakeEnabled?"震动：开":"震动：关",TableButton.Style.NORMAL,k,b->{shakeEnabled=!shakeEnabled;clearAndInit();}));
        addDrawableChild(new TableButton(x,y+8*step,width,height,perspective?"牌桌：透视":"牌桌：平面",TableButton.Style.NORMAL,k,b->toggleTableMode()));
        addDrawableChild(new TableButton(x,y+9*step,width,height,"视角："+tablePreset.label,TableButton.Style.NORMAL,k,b->cycleTablePreset()));
        addDrawableChild(new TableButton(x,y+10*step,width,height,"镜头调试",TableButton.Style.NORMAL,k,b->{cameraDebug=!cameraDebug;menuOpen=false;clearAndInit();}));
    }

    private int menuTop(PtcgLiveTableLayout l){
        return Math.max(2*l.ui(),Math.min((canSwitchSeat()?l.switchButton():l.menuButton()).bottom()+3*l.ui(),
            height-11*(l.buttonHeight()+3*l.ui())-2*l.ui()));
    }

    private void toggleTableMode(){perspective=!perspective;cachedCamera=null;tableHover.clear();dragging=null;dragOrigin=null;clearAndInit();}
    private void cycleTablePreset(){
        if(battleInputLocked())return;
        tablePreset=tablePreset.next();cameraSettings=tablePreset.settings();perspective=true;
        cachedCamera=null;tableHover.clear();dragging=null;dragOrigin=null;selectedSource=null;popup=null;
        clearAndInit();
    }

    /** 提示区底部的流程按钮（猜硬币、确认、效果选项……），放不下时最后一行是翻页。 */
    private void addResultButtons(PtcgLiveTableLayout layout) {
        var area=resultArea(layout);int k=layout.ui(),h=layout.buttonHeight(),gap=4*k;
        if(snapshot.practice()||snapshot.game().viewingSeat()==null) {
            var back=new TableButton(area.x()+16*k,area.bottom()-h-12*k,area.width()-32*k,h,"返回",TableButton.Style.PRIMARY,k,b->{
                if(!battleInputLocked())close();
            });
            back.active=!battleInputLocked();
            addDrawableChild(back);return;
        }
        boolean computer=snapshot.computer()!=null;
        String[] labels={computer?"再来一局":"同卡组再战","更换卡组","离开"};
        String[] actions={computer?"AI_AGAIN":"REMATCH",computer?"AI_CHANGE":"CHANGE",computer?"AI_LEAVE":"LEAVE"};
        for(int i=0;i<labels.length;i++) {
            String action=actions[i];
            var button=new TableButton(area.x()+16*k,area.bottom()-12*k-(3-i)*(h+gap),area.width()-32*k,h,labels[i],
                i==0?TableButton.Style.PRIMARY:TableButton.Style.NORMAL,k,b->{
                    if(socialWaiting||disconnected||battleInputLocked())return;socialWaiting=true;message="正在等待服务器确认……";
                    if(computer)ClientPlayNetworking.send(new dev.hua.ptcglive.network.TableIntentPayload(net.minecraft.util.math.BlockPos.fromLong(snapshot.computer().tablePosition()),action,matchId().toString()));
                    else BattleSocialScreen.matchAction(action,matchId());
                    if(action.endsWith("LEAVE"))close();else clearAndInit();
                });
            button.active=!socialWaiting&&!disconnected&&!battleInputLocked();addDrawableChild(button);
        }
    }
    private PokemonView victoryCard(){
        var result=snapshot.game().result();
        if(result==null||result.winner()==null||!result.winner().equals(snapshot.game().viewingSeat()))return null;
        for(var player:snapshot.game().players())if(player.id().equals(result.winner()))return player.active();
        return null;
    }
    private Rect resultArea(PtcgLiveTableLayout layout){
        var area=layout.resultSheet();
        if(victoryCard()==null||width-area.width()<96*layout.ui())return area;
        int shift=Math.min(area.x()/2,area.height()/3);
        return new Rect(area.x()+shift,area.y(),area.width(),area.height());
    }
    private long resultBannerDuration(){
        var result=snapshot.game().result();
        if(result==null||animationQuality==BattleAnimationEngine.Quality.OFF)return 0;
        if(result.winner()==null)return 1_850_000_000L;
        var viewer=snapshot.game().viewingSeat();
        return viewer!=null&&!viewer.equals(result.winner())?2_400_000_000L:0;
    }
    private void drawResultSheet(DrawContext c,PtcgLiveTableLayout layout) {
        var result=snapshot.game().result();var area=resultArea(layout);int k=layout.ui(),cx=area.x()+area.width()/2;
        double elapsed=Math.max(0,(frameNow-resultStarted)/1e9),p=BattleMotion.out(elapsed/.3);
        boolean won=Objects.equals(result.winner(),snapshot.game().viewingSeat())&&result.winner()!=null;
        boolean draw=result.winner()==null,effects=animationQuality!=BattleAnimationEngine.Quality.OFF;
        boolean lost=!won&&!draw&&snapshot.game().viewingSeat()!=null;
        double banner=resultBannerDuration()/1e9;
        if(elapsed<banner){
            c.fill(0,0,width,height,BattleEffects.alpha(lost?0xFF202B37:0xFF43556D,p*.74));
            double label=BattleMotion.envelope(elapsed,banner,.18,.28);
            int w=Math.min(width-16*k,244*k),h=28*k;
            if(lost)drawDefeatBanner(c,(width-w)/2,(height-h)/2,w,h,label);
            else BattleUi.round(c,(width-w)/2,(height-h)/2,w,h,h/2,BattleEffects.alpha(0xFF34485D,label*.90));
            if(label>=.03)BattleUi.centeredText(c,textRenderer,lost?"落败":"平局",width/2,height/2-8*k,
                BattleEffects.alpha(0xFFFFFFFF,label),false,2*k);
            c.fill(0,0,width,height,BattleEffects.alpha(0xFFEAF2F6,BattleMotion.smooth((elapsed-banner+.28)/.28)));
            return;
        }
        boolean pale=draw||lost;
        c.fillGradient(0,0,width,height,BattleEffects.alpha(won?0xFFBBE9E8:pale?0xFFEAF2F6:0xFF415D74,p*(pale?1:.84)),
            BattleEffects.alpha(won?0xFFE5CEF6:pale?0xFFDEEBEF:0xFF243345,p*(pale?1:.89)));
        var card=victoryCard();
        if(card!=null&&area.x()>layout.resultSheet().x()){
            int w=Math.min(area.x()-22*k,(int)(area.height()*.62)),h=(int)(w*1.395);
            int x=area.x()-12*k-w/2,y=height/2;
            double enter=effects?BattleMotion.out(elapsed/.42):1;
            var m=c.getMatrices();m.push();m.translate(x,y+(1-enter)*h*.14,0);
            m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees((float)(-4-5*(1-enter))));
            float scale=(float)(.86+.14*enter);m.scale(scale,scale,1);
            if(effects)BattleSceneEffects.result(c,new Rect(-w/2,-h/2,w,h),elapsed,true);
            BattleUi.card(c,textRenderer,artwork.get(card.topCardId()),name(card.topCardId()),-w/2,-h/2,w,h,CARD_BORDER);
            m.pop();
        }
        BattleUi.panel(c,area,k);
        if(lost)drawDefeatBanner(c,area.x()+8*k,area.y()+5*k,area.width()-16*k,4*k,1);
        String title=draw?"平局":snapshot.game().viewingSeat()==null?"对局结束":won?"胜利":"落败";
        BattleUi.centeredText(c,textRenderer,title,cx,area.y()+15*k,won?0xFF287BA1:BattleUi.INK,false,2*k);
        int y=area.y()+42*k;
        for(String line:wrap(resultText(result),(area.width()-24*k)/k)) {
            if(y>=area.y()+65*k)break;
            BattleUi.centeredText(c,textRenderer,line,cx,y,BattleUi.MUTED,false,k);y+=11*k;
        }
        var seats=snapshot.game().players();
        for(int i=0;i<Math.min(2,seats.size());i++) {
            UUID seat=seats.get(i).id();int px=area.x()+(i==0?20*k:area.width()/2+6*k);
            CardPainter.playerHead(c,seat,px,area.y()+72*k,18*k);
            BattleUi.text(c,textRenderer,textRenderer.trimToWidth(playerName(seat),Math.max(1,area.width()/2/k-48)),px+23*k,area.y()+77*k,BattleUi.INK,false,k);
        }
        String detail=message.isBlank()?"第 "+snapshot.game().turnNumber()+" 回合 · 对战结束":message;
        BattleUi.centeredText(c,textRenderer,textRenderer.trimToWidth(detail,(area.width()-24*k)/k),cx,area.y()+101*k,BattleUi.MUTED,false,k);
    }

    private void addPromptButtons(PtcgLiveTableLayout layout) {
        var flow = flowOptions();
        if (flow.isEmpty()) return;
        var area = layout.prompt(); int k = layout.ui(), height = layout.buttonHeight(), step = height + 3 * k;
        int rows = Math.max(1, (area.height() - 24 * k) / step);
        boolean paged = flow.size() > rows;
        int perPage = paged ? Math.max(1, rows - 1) : flow.size(), pages = (flow.size() + perPage - 1) / perPage;
        flowPage = Math.max(0, Math.min(flowPage, pages - 1));
        var shown = flow.subList(flowPage * perPage, Math.min(flow.size(), flowPage * perPage + perPage));
        int y = area.bottom() - (shown.size() + (paged ? 1 : 0)) * step + 3 * k;
        promptTextBottom = y - 4 * k;
        for (var option : shown) { addFlowButton(option, area.x(), y, area.width(), height, k); y += step; }
        if (paged) addPager(area.x(), y, area.width(), height, k, flowPage, pages, page -> { flowPage = page; clearAndInit(); });
    }

    private void addFlowButton(MatchSession.Option option, int x, int y, int width, int height, int k) {
        boolean primary = option.labelZh().startsWith("确认");
        var button = new TableButton(x, y, width, height, option.labelZh(),
            primary ? TableButton.Style.PRIMARY : TableButton.Style.NORMAL, k, b -> submit(option.index()));
        button.setTooltip(Tooltip.of(Text.literal(option.labelZh())));
        button.active=option.enabled()&&!awaitingServer&&!disconnected;
        addDrawableChild(button);
    }

    /** ◀ 1 / 3 ▶ 一行。 */
    private void addPager(int x, int y, int width, int height, int k, int page, int pages, IntConsumer go) {
        int arrow = Math.min(width / 3, 20 * k);
        var previous = new TableButton(x, y, arrow, height, "◀", TableButton.Style.NORMAL, k, b -> go.accept(page - 1));
        previous.active = page > 0;
        var next = new TableButton(x + width - arrow, y, arrow, height, "▶", TableButton.Style.NORMAL, k, b -> go.accept(page + 1));
        next.active = page < pages - 1;
        var label = new TableButton(x + arrow, y, width - 2 * arrow, height, (page + 1) + " / " + pages, TableButton.Style.LABEL, k, b -> { });
        label.active = false;
        addDrawableChild(previous); addDrawableChild(label); addDrawableChild(next);
    }

    /** 弹层：底部一排流程按钮（或“关闭”），卡多于一页时两侧加翻页箭头。 */
    private void addOverlayButtons(PtcgLiveTableLayout layout, Overlay content, List<MatchSession.Option> flow) {
        if (content == null) return;
        if(choiceOverlay()){
            if(CopyAttackChoicePage.active(snapshot)){addCopyAttackButtons(layout);return;}
            var page=EffectChoicePage.from(snapshot,flowPage);int k=layout.ui();
            int w=Math.min(width-32*k,Math.max(150*k,Math.round(height*.40f))),h=Math.max(layout.buttonHeight(),Math.round(height*.065f));
            int y=Math.round(height*.73f),x=(width-w)/2;
            for(var option:page.options()){addFlowButton(option,x,y,w,h,k);y+=h+8*k;}
            if(page.pages()>1)addPager(x,Math.round(height*.93f),w,layout.buttonHeight(),k,page.page(),page.pages(),p->{flowPage=p;clearAndInit();});
            addDrawableChild(new TableButton(4*k,overlayArea(layout).y()+31*k,62*k,layout.buttonHeight(),"收起看场面",TableButton.Style.NORMAL,k,b->{effectCollapsed=true;clearAndInit();}));
            return;
        }
        var area = overlayArea(layout); int k = layout.ui(), height = layout.buttonHeight();
        int y = selectionOverlay()?CardSelectionAnimation.tray(width,this.height).bottom()-height/2:area.bottom() - 4 * k - height;
        if(content.kind()==OverlayKind.EFFECT)addDrawableChild(new TableButton(selectionOverlay()?area.x()+4*k:publicRevealOverlay()?area.x()+4*k:area.right()-66*k,selectionOverlay()?area.y()+31*k:publicRevealOverlay()?y:Math.max(0,area.y()-height-2*k),62*k,height,"收起看场面",TableButton.Style.NORMAL,k,b->{effectCollapsed=true;clearAndInit();}));
        if (content.kind() == OverlayKind.PILE) {
            int width = 60 * k;
            addDrawableChild(new TableButton(area.x() + area.width() / 2 - width / 2, y, width, height, "关闭",
                TableButton.Style.NORMAL, k, b -> { pileView = null; clearAndInit(); }));
        } else if (!flow.isEmpty()) {
            int gap = 4 * k, each = (area.width() - 8 * k - (flow.size() - 1) * gap) / flow.size();
            int widest = flow.stream().mapToInt(option -> (textRenderer.getWidth(option.labelZh()) + 14) * k).max().orElse(60 * k);
            int width = Math.min(each, Math.max(60 * k, widest));
            int x = area.x() + area.width() / 2 - (flow.size() * width + (flow.size() - 1) * gap) / 2;
            for (var option : flow) { addFlowButton(option, x, y, width, height, k); x += width + gap; }
        }
        if (content.pages() > 1) {
            var band = overlayBand(layout); int arrow = 16 * k, arrowY = band.y() + band.height() / 2 - height / 2;
            var previous = new TableButton(area.x() + 4 * k, arrowY, arrow, height, "◀", TableButton.Style.NORMAL, k,
                b -> { turnOverlayPage(content, -1); clearAndInit(); });
            previous.active = content.page() > 0;
            var next = new TableButton(area.right() - 4 * k - arrow, arrowY, arrow, height, "▶", TableButton.Style.NORMAL, k,
                b -> { turnOverlayPage(content, 1); clearAndInit(); });
            next.active = content.page() < content.pages() - 1;
            addDrawableChild(previous); addDrawableChild(next);
        }
    }

    private void addCopyAttackButtons(PtcgLiveTableLayout layout) {
        var page=CopyAttackChoicePage.from(snapshot,flowPage);int k=layout.ui(),i=0;
        var attack=snapshot.game().resolvingAttack();
        var pokemon=attack==null?null:publicPokemon(snapshot,attack.pokemonId());
        int tint=pokemon==null?0xFFD9B5E8:BattleEffects.color(pokemon.pokemonType());
        for(var option:page.attacks()){
            var button=new CopyAttackPanel.AttackButton(CopyAttackPanel.row(width,height,k,i++),
                CopyAttackChoicePage.title(option),CopyAttackChoicePage.detail(snapshot,option),tint,k,
                ()->BattleMotion.out((presentationClock.now()-choiceStarted)/(double)CardSelectionAnimation.ENTRY_NS),b->submit(option.index()));
            button.active=option.enabled()&&!awaitingServer&&!disconnected;addDrawableChild(button);
        }
        int y=height-layout.buttonHeight()-5*k;
        for(var option:page.decline())addFlowButton(option,8*k,y,Math.min(width/3,142*k),layout.buttonHeight(),k);
        if(page.pages()>1)addPager(width/2-48*k,y,96*k,layout.buttonHeight(),k,page.page(),page.pages(),p->{flowPage=p;clearAndInit();});
        addDrawableChild(new TableButton(width-76*k,y,68*k,layout.buttonHeight(),"收起看场面",TableButton.Style.NORMAL,k,
            b->{effectCollapsed=true;clearAndInit();}));
    }

    private void turnOverlayPage(Overlay content, int delta) {
        int page = Math.max(0, Math.min(content.pages() - 1, content.page() + delta));
        switch (content.kind()) {
            case EFFECT -> selectionPage = page;
            case MULLIGAN -> mulliganPage = page;
            case PILE -> pilePage = page;
        }
    }

    /** 拖放多选菜单：标题一行、每个操作一个按钮，最下面是“取消”（多页时两侧翻页）。 */
    private int popupRows(PtcgLiveTableLayout layout) {
        int k = layout.ui(), step = layout.buttonHeight() + 2 * k;
        int fit = Math.max(1, (layout.screenHeight() - 8 - 14 * k - step - 4 * k) / step);
        return Math.max(1, Math.min(popup.options().size(), Math.min(8, fit)));
    }
    private Rect popupRect(PtcgLiveTableLayout layout) {
        int k = layout.ui(), step = layout.buttonHeight() + 2 * k, rows = popupRows(layout);
        int widest = popup.options().stream().mapToInt(option -> textRenderer.getWidth(option.labelZh())).max().orElse(60);
        int width = Math.min(layout.screenWidth() - 8, Math.max(120 * k, (widest + 14) * k));
        int height = 14 * k + rows * step + step + 2 * k;
        int x = Math.max(4, Math.min(popup.x() - width / 2, layout.screenWidth() - width - 4));
        int y = Math.max(4, Math.min(popup.y() - height / 2, layout.screenHeight() - height - 4));
        return new Rect(x, y, width, height);
    }
    private void addPopupButtons(PtcgLiveTableLayout layout) {
        var area = popupRect(layout); int k = layout.ui(), height = layout.buttonHeight(), step = height + 2 * k, rows = popupRows(layout);
        int pages = (popup.options().size() + rows - 1) / rows;
        popupPage = Math.max(0, Math.min(popupPage, pages - 1));
        int y = area.y() + 14 * k;
        for (var option : popup.options().subList(popupPage * rows, Math.min(popup.options().size(), popupPage * rows + rows))) {
            var button = new TableButton(area.x() + 4 * k, y, area.width() - 8 * k, height, option.labelZh(), TableButton.Style.NORMAL, k, b -> {
                popup = null; submit(option.index()); clearAndInit();
            });
            button.setTooltip(Tooltip.of(Text.literal(option.labelZh())));
            addDrawableChild(button); y += step;
        }
        // 最下面一行：[◀]  [取消]  [▶]（只有一页时只有“取消”）。
        int bottom = area.bottom() - 2 * k - height, inner = area.width() - 8 * k, arrow = Math.min(inner / 4, 20 * k);
        if (pages > 1) {
            var previous = new TableButton(area.x() + 4 * k, bottom, arrow, height, "◀", TableButton.Style.NORMAL, k,
                b -> { popupPage--; clearAndInit(); });
            previous.active = popupPage > 0;
            var next = new TableButton(area.right() - 4 * k - arrow, bottom, arrow, height, "▶", TableButton.Style.NORMAL, k,
                b -> { popupPage++; clearAndInit(); });
            next.active = popupPage < pages - 1;
            addDrawableChild(previous); addDrawableChild(next);
        }
        int cancelWidth = Math.min(50 * k, inner - (pages > 1 ? 2 * (arrow + 2 * k) : 0));
        addDrawableChild(new TableButton(area.x() + area.width() / 2 - cancelWidth / 2, bottom, cancelWidth, height, "取消",
            TableButton.Style.NORMAL, k, b -> { popup = null; clearAndInit(); }));
    }

    // ================= 服务器快照 =================

    @Override public void update(MatchSession.Snapshot next, String notice) {
        if(next!=null && next.revision()<snapshot.revision())return;
        boolean duplicatePrizeReply=awaitingServer&&hasPrizeOptions()&&next!=null&&next.revision()==snapshot.revision()&&(notice==null||notice.isBlank());
        if(!duplicatePrizeReply)awaitingServer=false;socialWaiting=false;
        if (parent != null) parent.update(next, notice);
        message = notice == null ? "" : notice; dragging = null; draggingCounter = false; popup = null; cardPanel = null; concedeArmed = false;
        if(next==null||next.revision()!=snapshot.revision()){selectedSource=null;endTurnArmed=false;}
        if (next == null) { disconnected=true; animations.clear(); animationDefinitions.clear(); clearElectricEffects(); drawnCards.clear(); battleAudio.clear(); clearAndInit(); if (client != null && parent != null) client.setScreen(parent); return; }
        boolean seatChanged = !Objects.equals(next.game().viewingSeat(), snapshot.game().viewingSeat());
        boolean ordered=snapshot.events()!=null&&next.events()!=null;
        boolean journalGap=MatchPresentation.journalGap(snapshot,next);
        if(seatChanged||journalGap||!ordered&&next.computer()==null&&next.revision()>snapshot.revision()+1){
            animations.clear();drawnCards.clear();clearElectricEffects();battleAudio.clear();orderedBatchEnd=0;
            lastElectricCue=null;selectedPrizeSlot=-1;prizeSelectedAt=0;prizeChoiceStarted=0;
            displayedReveal=null;revealStarted=0;displayedChoice="";choiceStarted=0;
            cardSelectionAnimation.sync(null,0);
            if(next.game().result()!=null)resultStarted=presentationClock.now();
        }
        long now=presentationClock.now();
        Map<UUID,Rect> previousTargets = new HashMap<>();
        Map<UUID,Rect> previousLogical=new HashMap<>();
        Map<UUID,Rect> discardOrigins=new HashMap<>();
        if(width>0&&height>0)for(var p:logicalPlacements())if(p.card().key().startsWith("field:"))
            previousLogical.put(UUID.fromString(p.card().key().substring(6)),rect(p));
        if (width > 0 && height > 0) for (var placed : boardPlacements()) {
            if(placed.card().key().startsWith("hand:"))discardOrigins.put(UUID.fromString(placed.card().key().substring(5)),rect(placed));
            if (placed.card().key().startsWith("field:"))
                previousTargets.put(UUID.fromString(placed.card().key().substring(6)),
                    new Rect(placed.x(), placed.y(), placed.width(), placed.height()));
        }
        if(width>0&&height>0)for(var placed:selectedPlacements())
            discardOrigins.put(UUID.fromString(placed.card().key().substring(7)),rect(placed));
        animations.removeIf(a->animationExpired(a,now));
        animationDefinitions.keySet().retainAll(animations);
        int firstNewAnimation=animations.size();
        var cues=MatchPresentation.changes(snapshot,next);
        if(DrawCardAnimation.firstDeal(snapshot.game(),next.game()))
            cues=cues.stream().map(c->DrawCardAnimation.draw(c)
                ?new MatchPresentation.Cue(c.kind(),c.seat(),null,c.amount(),"开局发牌",null):c).toList();
        drawnCards.keySet().retainAll(animations);
        var drawn=DrawCardAnimation.revealedCards(snapshot.game(),next.game(),cues);
        long batchStart=now;
        long[] legacyOffsets=ordered?null:BattleAnimationTiming.legacyHitOffsets(cues);
        if(ordered) {
            batchStart=Math.max(batchStart,orderedBatchEnd);
            for(var active:animations)
                batchStart=Math.max(batchStart,active.started()+eventSpacing(active.cue(),active.type(),active.previousCard(),""));
        }
        // Keep confirmed tosses readable across multiple network updates.
        for(var active:animations) if(!ordered&&active.cue().kind()==MatchPresentation.Kind.COIN)
            batchStart=Math.max(batchStart,active.started()+coinDuration(active.cue()));
        var visualDamageBefore=new HashMap<UUID,Integer>();
        var transferredEnergy=new HashSet<UUID>();
        var presentedTools=new HashSet<UUID>();
        var presentedDiscards=new HashSet<UUID>();
        int coinIndex=0,coinHeads=0,coinCount=0,returnIndex=0;
        long coinStart=0;
        long orderedOffset=0;
        int spreadStart=0,spreadEnd=0;
        long spreadDuration=0;
        String moveType="";
        for(int cueIndex=0;cueIndex<cues.size();cueIndex++) {
            var cue=cues.get(cueIndex);
            if(ordered&&cue.kind()==MatchPresentation.Kind.ATTACK){
                for(var prior:animations)if(prior.cue().kind()==MatchPresentation.Kind.DAMAGE)
                    orderedOffset=Math.max(orderedOffset,prior.started()-batchStart+animationDuration(prior));
            }
            int toolReturnOrder=isToolReturn(cue)?returnIndex++:0;
            if(!isToolReturn(cue))returnIndex=0;
            boolean coin=cue.kind()==MatchPresentation.Kind.COIN;
            if(coin&&(cueIndex==0||cues.get(cueIndex-1).kind()!=MatchPresentation.Kind.COIN)){
                coinIndex=0;coinHeads=0;coinCount=CoinAnimation.groupSize(cues,cueIndex);
                coinStart=batchStart+(ordered?orderedOffset:BattleAnimationTiming.offset(cue,cues));
            }
            if(coin&&cue.text().endsWith("正面"))coinHeads++;
            long start=coin?coinStart:batchStart+(ordered?orderedOffset:BattleAnimationTiming.offset(cue,cues)+legacyOffsets[cueIndex]);
            if(cue.kind()==MatchPresentation.Kind.RESULT){
                for(var prior:animations)
                    start=Math.max(start,prior.started()+animationDuration(prior));
                if(ordered)orderedOffset=start-batchStart;
            }
            var oldMon=publicPokemon(snapshot,cue.pokemon());
            var nextMon=publicPokemon(next,cue.pokemon());
            var mon=nextMon!=null?nextMon:oldMon;
            EnergyType visualType=mon==null?null:mon.pokemonType();
            // The attack's actual damage type can differ from the printed Pokemon type.
            if(cue.kind()==MatchPresentation.Kind.ATTACK) {
                for(int i=cueIndex+1;i<cues.size();i++) {
                    var following=cues.get(i);
                    if(following.kind()==MatchPresentation.Kind.ATTACK)break;
                    if(following.kind()==MatchPresentation.Kind.DAMAGE&&following.text().startsWith("招式伤害·")&&!following.text().substring(5).isEmpty()) {
                        visualType=EnergyType.valueOf(following.text().substring(5));break;
                    }
                }
            }
            if(cue.kind()==MatchPresentation.Kind.DAMAGE) {
                visualType=null;
                if(cue.text().startsWith("招式伤害·")&&!cue.text().substring(5).isEmpty())
                    visualType=EnergyType.valueOf(cue.text().substring(5));
            }
            if(ordered&&(cueIndex==0||cue.kind()==MatchPresentation.Kind.ATTACK)) {
                int groupEnd=cueIndex+1;
                while(groupEnd<cues.size()&&cues.get(groupEnd).kind()!=MatchPresentation.Kind.ATTACK)groupEnd++;
                moveType=BattleAnimationEngine.moveType(cues.subList(cueIndex,groupEnd));
            }
            boolean simultaneousTransfer=cueIndex+1<cues.size()&&(sameEnergyTransfer(cue,cues.get(cueIndex+1))||sameEnergyDiscard(cue,cues.get(cueIndex+1)));
            boolean nextToolReturn=isToolReturn(cue)&&cueIndex+1<cues.size()&&isToolReturn(cues.get(cueIndex+1));
            boolean pairedRetreat=cueIndex+1<cues.size()&&(RetreatAnimation.pair(cue,cues.get(cueIndex+1))||SwitchAnimation.pair(cue,cues.get(cueIndex+1)));
            boolean pairedDeal=DrawCardAnimation.deal(cue)&&cueIndex+1<cues.size()
                &&DrawCardAnimation.deal(cues.get(cueIndex+1))&&cue.amount()==cues.get(cueIndex+1).amount()
                &&!Objects.equals(cue.seat(),cues.get(cueIndex+1).seat());
            boolean simultaneousSetupReveal=PokemonEntryAnimation.setupReveal(cue)&&cueIndex+1<cues.size()
                &&PokemonEntryAnimation.setupReveal(cues.get(cueIndex+1));
            if(ordered&&cueIndex>=spreadEnd){
                spreadStart=cueIndex;
                spreadEnd=BattleAnimationTiming.spreadEnd(cues,cueIndex);
                spreadDuration=0;
            }
            if(coin){if(coinIndex==coinCount-1)orderedOffset+=CoinAnimation.duration(coinCount);}
            else if(ordered&&spreadEnd-spreadStart>1){
                spreadDuration=Math.max(spreadDuration,eventSpacing(cue,visualType,mon==null?null:mon.topCardId(),moveType));
            } else if(ordered&&!simultaneousTransfer&&!nextToolReturn&&!pairedRetreat&&!pairedDeal&&!simultaneousSetupReveal)orderedOffset+=eventSpacing(cue,visualType,mon==null?null:mon.topCardId(),moveType)
                +(long)(toolReturnOrder*ToolAttachmentAnimation.returnStagger()*1e6);
            EnergyTransferAnimation.Link transfer=null;
            if(isEnergyTransfer(cue)&&nextMon!=null)try{
                UUID sourceId=UUID.fromString(cue.text().substring(cue.text().indexOf('·')+1));
                var oldSource=publicPokemon(snapshot,sourceId);var nextSource=publicPokemon(next,sourceId);
                if(oldSource!=null)transfer=EnergyTransferAnimation.link(sourceId,oldSource.attachedEnergyIds(),oldSource.attachedEnergyCardIds(),
                    nextSource==null?List.of():nextSource.attachedEnergyIds(),nextMon.attachedEnergyIds(),cue.cardId(),transferredEnergy,previousTargets.get(sourceId));
            }catch(IllegalArgumentException ignored){}
            var discard=isEnergyDiscard(cue)&&oldMon!=null?EnergyDiscardAnimation.removed(oldMon.attachedEnergyIds(),oldMon.attachedEnergyCardIds(),
                nextMon==null?List.of():nextMon.attachedEnergyIds(),cue.cardId(),transferredEnergy):null;
            int toolSlot=cue.kind()==MatchPresentation.Kind.TOOL?ToolAttachmentAnimation.slot(oldMon==null?List.of():oldMon.attachedTools(),
                nextMon==null?List.of():nextMon.attachedTools(),cue.cardId(),cue.amount()>0,presentedTools):0;
            UUID toolInstance=cue.kind()==MatchPresentation.Kind.TOOL&&cue.amount()<0&&oldMon!=null&&toolSlot<oldMon.attachedTools().size()
                ?oldMon.attachedTools().get(toolSlot).instanceId():null;
            animations.add(new AnimatedCue(cue,start,previousTargets.get(cue.pokemon()),width,height,previousLogical.get(cue.pokemon()),
                coin?coinIndex++:-1,coin?coinCount:0,next.revision(),visualType,oldMon==null?null:oldMon.topCardId(),coinHeads,
                oldMon==null?-1:visualDamageBefore.getOrDefault(cue.pokemon(),oldMon.damage()),transfer,discard,toolSlot,toolInstance,toolReturnOrder,cue.kind()==MatchPresentation.Kind.STADIUM?StadiumAnimation.departure(snapshot,next):null,
                cue.kind()==MatchPresentation.Kind.STADIUM?StadiumAnimation.rejected(snapshot,next,cue):null,
                (cue.kind()==MatchPresentation.Kind.DISCARD||cue.kind()==MatchPresentation.Kind.LOST)?DiscardAnimation.claim(
                    snapshot.game().players().stream().filter(p->Objects.equals(p.id(),cue.seat())).findFirst().map(p->cue.kind()==MatchPresentation.Kind.LOST?p.lostZone():p.discard()).orElse(List.of()),
                    next.game().players().stream().filter(p->Objects.equals(p.id(),cue.seat())).findFirst().map(p->cue.kind()==MatchPresentation.Kind.LOST?p.lostZone():p.discard()).orElse(List.of()),
                    cue.cardId(),discardOrigins,presentedDiscards):null));
            if(!drawn.isEmpty()&&DrawCardAnimation.draw(cue)&&Objects.equals(cue.seat(),next.game().viewingSeat()))
                drawnCards.put(animations.getLast(),drawn);
            if(ordered&&spreadEnd-spreadStart>1&&cueIndex+1==spreadEnd){
                for(int i=animations.size()-(spreadEnd-spreadStart);i<animations.size();i++)
                    spreadDuration=Math.max(spreadDuration,animationDuration(animations.get(i)));
                orderedOffset+=spreadDuration;
            }
            if(oldMon!=null&&(cue.kind()==MatchPresentation.Kind.DAMAGE||cue.kind()==MatchPresentation.Kind.HEAL))
                visualDamageBefore.put(cue.pokemon(),Math.max(0,visualDamageBefore.getOrDefault(cue.pokemon(),oldMon.damage())+(cue.kind()==MatchPresentation.Kind.DAMAGE?cue.amount():-cue.amount())));
            if(cue.kind()==MatchPresentation.Kind.DAMAGE&&visualType==EnergyType.LIGHTNING&&cue.text().startsWith("招式伤害·"))lastElectricCue=animations.getLast();
            if(soundEnabled&&coin)battleAudio.enqueue("coin_land",start+(long)(CoinAnimation.landing(coinIndex-1)*1e6),.45f);
        }
        if(ordered&&!cues.isEmpty())orderedBatchEnd=batchStart+orderedOffset;
        if(soundEnabled)for(int i=firstNewAnimation;i<animations.size();i++) {
            var animation=animations.get(i);
            var cue=animation.cue();boolean hit=cue.kind()==MatchPresentation.Kind.DAMAGE&&cue.text().startsWith("招式伤害·");
            boolean attack=cue.kind()==MatchPresentation.Kind.ATTACK&&!cue.text().contains("攻击失败");
            var sounds=cue.kind()==MatchPresentation.Kind.EVOLVE?BattleAnimationEngine.sounds(BattleAnimationEngine.evolution()):
                isItemPlay(cue)?TrainerPlayAnimation.sounds(Objects.equals(cue.seat(),topSeat())):
                hit||attack?BattleAnimationEngine.sounds(attackAnimation(animation),hit,attackContact(animation)):List.<BattleAnimationEngine.Sound>of();
            boolean compensate=cue.kind()==MatchPresentation.Kind.EVOLVE||isItemPlay(cue);
            long outputLead=compensate?BattleAudio.outputLatencyNanos():0;
            if(sounds.isEmpty())battleAudio.enqueue(BattleSoundPlan.event(cue,animation.type(),Objects.equals(cue.seat(),next.game().viewingSeat())),
                animation.started()+(cue.kind()==MatchPresentation.Kind.COIN?(long)(CoinAnimation.start(animation.coinIndex())*1e6):0),.55f);
            else for(var sound:sounds)battleAudio.enqueue(sound.name(),animation.started()+(compensate?
                BattleSoundPlan.compensatedOffsetNanos(sound.offsetMs(),outputLead):(long)(sound.offsetMs()*1e6)),.55f);
        }
        if(next.game().result()==null)resultStarted=0;
        else if(snapshot.game().result()==null)resultStarted=animations.stream().filter(a->a.cue().kind()==MatchPresentation.Kind.RESULT)
            .mapToLong(AnimatedCue::started).max().orElse(now);
        while(animations.size()>dev.hua.ptcglive.engine.BattleEventJournal.LIMIT){
            var expired=animations.removeFirst();
            animationDefinitions.remove(expired);
            drawnCards.remove(expired);
        }
        if (next.revision() != snapshot.revision() || seatChanged) flowPage = 0;
        if (!Objects.equals(EffectCardPage.identity(snapshot), EffectCardPage.identity(next)) || seatChanged) {
            selectionPage = 0; effectCollapsed=false;
            // A read-only pile must never cover the new authoritative payment/selection prompt.
            if (EffectCardPage.identity(next)!=null) {pileView=null;menuOpen=false;}
        }
        if (next.game().mulliganReveals().size() != snapshot.game().mulliganReveals().size()) mulliganPage = 0;
        snapshot = next; model = MatchRegionModel.from(next);
        if(battleInputLocked()){menuOpen=false;pileView=null;logOpen=false;}
        if (pileView != null && model.zones().stream().noneMatch(zone -> zone.id().equals(pileView.zoneId()))) pileView = null;
        clearAndInit();
    }
    private long stadiumDuration(MatchPresentation.Cue cue){
        for(var a:animations)if(a.cue()==cue)return StadiumAnimation.duration(cue,a.stadium()!=null);
        return StadiumAnimation.duration(cue,snapshot.game().stadium()!=null);
    }
    private static void drawDefeatBanner(DrawContext c,int x,int y,int w,int h,double opacity){
        if(w<h||h<2||opacity<=0)return;
        int r=h/2;
        var matrices=c.getMatrices();
        matrices.push();
        matrices.translate(x+w,y,0);
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(90));
        int orange=BattleEffects.alpha(0xFFFF9851,opacity),coral=BattleEffects.alpha(0xFFEB515D,opacity);
        c.fillGradient(0,r,h,w-r,coral,orange);
        for(int i=0;i<r;i++){
            double d=r-i-.5;
            int inset=(int)Math.round(r-Math.sqrt(r*r-d*d));
            c.fill(inset,i,h-inset,i+1,coral);
            c.fill(inset,w-i-1,h-inset,w-i,orange);
        }
        matrices.pop();
    }
    private long eventSpacing(MatchPresentation.Cue cue,EnergyType visualType,String sourceCard,String moveType) {
        if(checkupDamage(cue))return displayDuration(cue);
        if(cue.kind()==MatchPresentation.Kind.LOST)return LostZoneAnimation.DURATION_NS;
        if(cue.kind()==MatchPresentation.Kind.DISCARD)return DiscardAnimation.DURATION_NS;
        if(cue.kind()==MatchPresentation.Kind.SHUFFLE)return ShuffleAnimation.DURATION_NS;
        if(cue.kind()==MatchPresentation.Kind.COIN)return coinDuration(cue);
        if(cue.kind()==MatchPresentation.Kind.ABILITY)return AbilityAnimation.DURATION_NS;
        if(cue.kind()==MatchPresentation.Kind.STADIUM)return stadiumDuration(cue);
        if(SwitchAnimation.applies(cue))return SwitchAnimation.DURATION_NS;
        if(RetreatAnimation.applies(cue))return RetreatAnimation.DURATION_NS;
        if(cue.kind()==MatchPresentation.Kind.DEVOLVE)return DevolutionAnimation.DURATION_NS;
        if(PokemonEntryAnimation.applies(cue))return PokemonEntryAnimation.duration(entryReveal(cue));
        if(DrawCardAnimation.draw(cue))return DrawCardAnimation.arrival(cue,Objects.equals(cue.seat(),snapshot.game().viewingSeat()),cue.amount()-1);
        if(isItemPlay(cue)||isSupporterPlay(cue)||isToolPlay(cue))return feedbackDuration(cue);
        if(cue.kind()==MatchPresentation.Kind.TOOL)return feedbackDuration(cue);
        if(cue.kind()==MatchPresentation.Kind.EVOLVE)return (long)(BattleAnimationEngine.evolution().durationMs*1e6);
        if(isEnergyDiscard(cue))return (long)(BattleAnimationEngine.energyDiscard().durationMs*1e6);
        if(isEnergyTransfer(cue))return (long)(BattleAnimationEngine.energyTransfer().durationMs*1e6);
        if(cue.kind()==MatchPresentation.Kind.PRIZE)return feedbackDuration(cue);
        if(cue.kind()==MatchPresentation.Kind.LEAVE&&cue.text().startsWith("击倒"))
            return (long)(BattleAnimationEngine.knockout().durationMs*1e6);
        if(cue.kind()==MatchPresentation.Kind.ATTACK&&!cue.text().contains("攻击失败")) {
            var definition=BattleAnimationEngine.resolve(cue.cardId()==null?sourceCard:cue.cardId(),cue.text(),moveType,visualType);
            if(definition!=null)return (long)(definition.timeline().impactMs*1e6);
        }
        if(cue.kind()==MatchPresentation.Kind.DAMAGE&&visualType==EnergyType.LIGHTNING&&cue.text().startsWith("招式伤害·"))
            return (long)(BattleAnimationEngine.resolve(null,null,moveType,visualType).electric().durationMs()*1e6);
        if(cue.kind()==MatchPresentation.Kind.DAMAGE&&(visualType==EnergyType.FIRE||visualType==EnergyType.WATER)&&cue.text().startsWith("招式伤害·")) {
            var t=BattleAnimationEngine.resolve(null,null,moveType,visualType).timeline();
            return (long)(Math.max(t.durationMs-t.impactMs,t.sample("hp.endMs",0,0))*1e6);
        }
        if(cue.kind()==MatchPresentation.Kind.DAMAGE&&cue.text().startsWith("招式伤害·"))
            return snapshot.computer()==null?BattleAnimationTiming.HIT_STEP:Math.max(BattleAnimationTiming.HIT_STEP,dev.hua.ptcglive.view.AiPresentationTiming.nanos(cue));
        if(snapshot.computer()!=null)return dev.hua.ptcglive.view.AiPresentationTiming.nanos(cue);
        return cue.kind()==MatchPresentation.Kind.COIN?1_500_000_000L:cue.kind()==MatchPresentation.Kind.ATTACK?650_000_000L:240_000_000L;
    }
    private void refresh() {
        if(battleInputLocked())return;
        ClientPlayNetworking.send(new MatchIntentPayload(matchId().toString(), seat(), Math.toIntExact(snapshot.revision()), -1));
    }
    private void submit(int option) {
        if(battleInputLocked())return;
        if(disconnected){message="对局连接已结束，请重新打开卡桌。";return;}
        if(awaitingServer){message="已发送操作，请等待服务器回复，不要重复点击。";return;}
        awaitingServer=true;sentAt=System.nanoTime();message="正在等待服务器确认……";
        ClientPlayNetworking.send(new MatchIntentPayload(matchId().toString(), seat(), Math.toIntExact(snapshot.revision()), option));
    }
    private String seat() { return snapshot.game().viewingSeat() == null ? "" : snapshot.game().viewingSeat().toString(); }
    private PtcgLiveTableLayout layout() {
        if (cachedLayout == null || cachedLayout.screenWidth() != width || cachedLayout.screenHeight() != height)
            cachedLayout = PtcgLiveTableLayout.forScreen(width, height);
        return cachedLayout;
    }
    private Optional<MatchSession.Option> option(String label) {
        return snapshot.options().stream().filter(option -> option.labelZh().equals(label)).findFirst();
    }
    private List<MatchSession.Option> forCards(String... keys) { return MatchActionMatcher.forCards(snapshot, List.of(keys)); }
    private String name(String cardId) { return snapshot.cardNames().getOrDefault(cardId, cardId); }

    /** 没有卡面锚点的流程操作；结束回合、认输、拿奖赏卡各有自己的位置，不在这里重复。 */
    private List<MatchSession.Option> flowOptions() {
        return MatchActionMatcher.flowOptions(snapshot);
    }
    private boolean hasOwnOptions() {
        return snapshot.options().stream().anyMatch(option -> !option.labelZh().equals(CONCEDE_LABEL));
    }

    // ================= 座位与区域 =================

    /** 两个座位总是同时显示；观战只看公开信息。 */
    private UUID topSeat() {
        UUID own = snapshot.game().viewingSeat();
        return snapshot.game().players().stream().map(MatchClientView.Player::id).filter(id -> !id.equals(own)).findFirst()
            .orElse(snapshot.game().players().isEmpty() ? null : snapshot.game().players().getFirst().id());
    }
    private UUID bottomSeat() {
        UUID own = snapshot.game().viewingSeat();
        if (own != null) return own;
        return snapshot.game().players().size() < 2 ? null : snapshot.game().players().get(1).id();
    }
    private MatchClientView.Player player(UUID id) {
        return snapshot.game().players().stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElse(null);
    }
    private MatchRegionModel.Zone zone(UUID player, String suffix) { return zone(player + suffix); }
    private MatchRegionModel.Zone zone(String id) {
        return model.zones().stream().filter(z -> z.id().equals(id)).findFirst().orElse(new MatchRegionModel.Zone("", "", List.of(), 0, ""));
    }
    private String playerName(UUID seat) {
        if(snapshot.computer()!=null&&snapshot.computer().seat().equals(seat))return "电脑对手";
        var handler = client == null ? null : client.getNetworkHandler();
        var entry = handler == null ? null : handler.getPlayerListEntry(seat);
        if (entry != null) return entry.getProfile().getName();
        if (snapshot.practice()) {
            var players = snapshot.game().players();
            return !players.isEmpty() && players.getFirst().id().equals(seat) ? "甲方" : "乙方";
        }
        return seat.equals(snapshot.game().viewingSeat()) ? "我方" : "对方";
    }
    private String sideName(UUID seat) {
        if(snapshot.computer()!=null&&snapshot.computer().seat().equals(seat))return "电脑对手";
        UUID own = snapshot.game().viewingSeat();
        return own == null ? playerName(seat) : seat.equals(own) ? "我方" : "对方";
    }

    // ================= 卡的位置 =================

    /** 手牌一行：平时是自己的手牌；效果附能时换成待附着的能量。 */
    private List<MatchRegionModel.Card> handCards() {
        var game = snapshot.game(); UUID own = game.viewingSeat();
        if (game.effectAttachment() != null) return zone("effect-attachment").cards();
        if (own == null || game.damageAllocation() != null) return List.of();
        var cards=zone(own, ":hand").cards();
        if(!drawnCards.isEmpty())cards=cards.stream().filter(c->!drawingHandCard(c.key())).toList();
        handOffset=Math.max(0,Math.min(handOffset,Math.max(0,cards.size()-10)));
        return cards.subList(handOffset,Math.min(cards.size(),handOffset+10));
    }
    private boolean drawingHandCard(String key){
        long now=presentationClock.now();
        for(var e:drawnCards.entrySet())for(int i=0;i<e.getValue().size();i++)
            if(key.equals("hand:"+e.getValue().get(i).instanceId())&&now-e.getKey().started()
                <=DrawCardAnimation.arrival(e.getKey().cue(),true,i))return true;
        return false;
    }
    /** 悬停的手牌：已经升起的那张只要鼠标还在它整张卡上就保持升起，避免上下抖动。 */
    private int handIndexAt(double x, double y, PtcgLiveTableLayout layout) {
        var cards = handCards();
        if (cards.isEmpty() || dragging != null || draggingCounter) return -1;
        var row = layout.handRow(cards.size());
        if (hoveredHand >= 0 && hoveredHand < cards.size()) {
            int left = row.x(hoveredHand);
            if (x >= left && x < left + layout.activeWidth() && y >= layout.handRaisedY()
                && y < layout.handRaisedY() + layout.activeHeight()) return hoveredHand;
        }
        if (y < layout.handY() || y >= layout.handY() + layout.activeHeight()) return -1;
        for (int i = cards.size() - 1; i >= 0; i--) if (x >= row.x(i) && x < row.x(i) + layout.activeWidth()) return i;
        return -1;
    }
    /** 桌面上的卡（不含弹层），按绘制顺序：后面的盖住前面的。 */
    private List<Placed> logicalPlacements() {
        var layout = layout(); var result = new ArrayList<Placed>();
        UUID top = topSeat(), bottom = bottomSeat();
        if (top != null) addField(result, top, true, layout);
        if (bottom != null) addField(result, bottom, false, layout);
        var stadium = zone("stadium").cards();
        if(stadium.size()==2)stadium=stadium.stream().sorted(Comparator.comparing(MatchRegionModel.Card::definitionId)).toList();
        for(int i=0;i<stadium.size();i++){
            var r=StadiumAnimation.partSlot(layout,stadium.size(),i);
            result.add(new Placed(stadium.get(i),r.x(),r.y(),r.width(),r.height()));
        }
        var hand = handCards(); var row = layout.handRow(hand.size());
        if(selectionOverlay()&&!finitePresentationLocked())return result;
        for (int i = 0; i < hand.size(); i++)
            if (i != hoveredHand) result.add(new Placed(hand.get(i), row.x(i), layout.handY(), layout.activeWidth(), layout.activeHeight()));
        if (hoveredHand >= 0 && hoveredHand < hand.size())
            result.add(new Placed(hand.get(hoveredHand), row.x(hoveredHand), layout.handRaisedY(), layout.activeWidth(), layout.activeHeight()));
        return result;
    }
    private boolean tableMode(){return perspective&&PocketTableRenderer.ready();}
    private static Rect rect(Placed p){return new Rect(p.x(),p.y(),p.width(),p.height());}
    private boolean onTable(MatchRegionModel.Card card){return card.key().startsWith("field:")||zone("stadium").cards().stream().anyMatch(c->c.key().equals(card.key()));}
    private PocketTableCamera camera(){
        if(cachedCamera==null||cachedCamera.screenWidth!=width||cachedCamera.screenHeight!=height||!cachedCamera.settings.equals(cameraSettings)){
            var l=layout();
            cachedCamera=new PocketTableCamera(width,height,tablePreset.viewport(l),cameraSettings,tablePreset.fill);
        }
        return cachedCamera;
    }
    private PtcgLiveTableLayout previousLayout(AnimatedCue a){return PtcgLiveTableLayout.forScreen(Math.max(320,a.previousWidth()),Math.max(240,a.previousHeight()));}
    private Rect tableRect(Rect logical){return tableMode()?camera().bounds(camera().card(logical,layout())):logical;}
    private boolean tableContains(Rect logical,double x,double y){return tableMode()?Double.isFinite(camera().card(logical,layout()).intersect(camera().ray(x,y))):logical.contains(x,y);}
    private Placed projected(Placed p){if(!tableMode()||!onTable(p.card()))return p;var r=camera().bounds(tablePose(p,true));return new Placed(p.card(),r.x(),r.y(),r.width(),r.height());}
    private List<Placed> boardPlacements(){return logicalPlacements().stream().map(this::projected).toList();}
    private PocketTableCamera.Pose tablePose(Placed p,boolean visual){
        var pose=camera().card(rect(p),layout());double hover=tableHover.getOrDefault(p.card().key(),0.);
        if(camera().portrait&&p.card().key().startsWith("stadium:")&&zone("stadium").cards().size()==2){
            var slot=StadiumAnimation.slot(layout());var anchor=camera().card(slot,layout());
            double dx=(p.x()+p.width()/2.-slot.x()-slot.width()/2.)/layout().benchWidth()*camera().cardWidth;
            pose=new PocketTableCamera.Pose(anchor.x()+dx,anchor.y(),anchor.z(),pose.width(),pose.height(),0,0);
        }
        pose=pose.move(0,hover*.12,0).angles(hover*8,0);
        if(!p.card().key().startsWith("field:"))return pose;
        UUID id=UUID.fromString(p.card().key().substring(6));
        for(var a:animations)if(id.equals(a.cue().pokemon())){
            double elapsed=animationElapsed(a,frameNow);var kind=a.cue().kind();
            if(SwitchAnimation.applies(a.cue())&&a.previousLogical()!=null){
                var old=camera().card(a.previousLogical(),previousLayout(a));
                if(elapsed<0)return old;
                return SwitchAnimation.incoming(a.cue())?pose:RetreatAnimation.pose(old,pose,false,
                    Objects.equals(a.cue().seat(),topSeat()),SwitchAnimation.outgoingTime(elapsed*1000));
            }
            if(RetreatAnimation.applies(a.cue())&&elapsed*1000<RetreatAnimation.LAND_MS&&a.previousLogical()!=null)
                return RetreatAnimation.pose(camera().card(a.previousLogical(),previousLayout(a)),pose,
                    RetreatAnimation.incoming(a.cue()),Objects.equals(a.cue().seat(),topSeat()),elapsed*1000);
            if(kind==MatchPresentation.Kind.MOVE&&!RetreatAnimation.applies(a.cue())&&elapsed<.72&&a.previousLogical()!=null){
                var old=camera().card(a.previousLogical(),previousLayout(a));double t=BattleMotion.flight(elapsed,.72);
                return new PocketTableCamera.Pose(old.x()+(pose.x()-old.x())*t,.025+Math.sin(Math.PI*t)*.45,old.z()+(pose.z()-old.z())*t,
                    old.width()+(pose.width()-old.width())*t,old.height()+(pose.height()-old.height())*t,Math.sin(Math.PI*t)*12,0);
            }
            if(kind==MatchPresentation.Kind.ATTACK&&elapsed>=0&&elapsed<attackContact(a)+attackRecovery(a)&&!a.cue().text().contains("攻击失败")){
                var definition=attackAnimation(a);double ms=BattleAnimationEngine.attackTime(definition,elapsed,attackContact(a));
                double lift=BattleAnimationEngine.value(definition,"card.lift",ms,BattleMotion.attack(elapsed,attackContact(a)));
                double direction=Objects.equals(a.cue().seat(),bottomSeat())?-1:1;
                pose=pose.move(BattleAnimationEngine.value(definition,"card.x",ms,0)*6,Math.max(0,lift)*.18,
                    lift*direction*.55+BattleAnimationEngine.value(definition,"card.y",ms,0)*4)
                    .scale(BattleAnimationEngine.value(definition,"card.scale",ms,1+Math.max(0,lift)*.045))
                    .angles(pose.tilt(),-direction*BattleAnimationEngine.value(definition,"card.rotation",ms,lift*2.5));
            }
            if(visual&&shakeEnabled&&kind==MatchPresentation.Kind.DAMAGE&&a.cue().text().startsWith("招式伤害·")&&elapsed>=0&&elapsed<.38){
                double recoil=a.type()==EnergyType.LIGHTNING?attackAnimation(a).electric().shake(elapsed*1000):BattleMotion.recoil(elapsed);
                pose=pose.move(recoil*.045,0,0);
            }
        }
        return pose;
    }
    private void updateTableHover(int x,int y,boolean blocked){
        long now=System.nanoTime();double dt=hoverUpdated==0?0:Math.min(.1,(now-hoverUpdated)/1e9);hoverUpdated=now;
        var found=blocked||dragging!=null?null:at(x,y);String key=found!=null&&onTable(found.card())?found.card().key():null;
        if(key!=null)tableHover.putIfAbsent(key,0.);
        for(var it=tableHover.entrySet().iterator();it.hasNext();){var e=it.next();double value=Math.clamp(e.getValue()+(e.getKey().equals(key)?1:-1)*dt/ .12,0,1);if(value==0&& !e.getKey().equals(key))it.remove();else e.setValue(value);}
    }
    private boolean tableCardVisible(MatchRegionModel.Card card){
        if(card.key().startsWith("stadium:"))return animations.stream().noneMatch(a->a.cue().kind()==MatchPresentation.Kind.STADIUM&&!animationExpired(a,frameNow));
        if(!card.key().startsWith("field:"))return true;
        UUID id=UUID.fromString(card.key().substring(6));
        for(var a:animations)if(id.equals(a.cue().pokemon())){
            var kind=a.cue().kind();double e=animationElapsed(a,frameNow);
            if(kind==MatchPresentation.Kind.ABILITY&&e>=0&&e*1000<AbilityAnimation.LAND_MS)return false;
            if(SwitchAnimation.applies(a.cue())&&SwitchAnimation.incoming(a.cue())&&e>=0&&e*1000<SwitchAnimation.LAND_MS)return false;
            if(kind==MatchPresentation.Kind.EVOLVE&&animationQuality!=BattleAnimationEngine.Quality.OFF&&e<BattleAnimationEngine.evolution().impactMs/1000)return false;
            if(kind==MatchPresentation.Kind.DEVOLVE&&e<DevolutionAnimation.LAND_MS/1000)return false;
            if(PokemonEntryAnimation.applies(a.cue())&&e*1000<PokemonEntryAnimation.contact(entryReveal(a.cue())))return false;
        }
        return true;
    }
    private PocketTableCamera.Pose prizePose(boolean opponent,int index){
        var l=layout();return camera().card(new Rect(l.prizeX()+index*l.prizeFan(),l.benchY(opponent),l.benchWidth(),l.benchHeight()),l).move(0,index*.003,0);
    }
    private boolean prizeContains(boolean opponent,double x,double y){
        var p=player(opponent?topSeat():bottomSeat());if(p==null)return false;var ray=camera().ray(x,y);
        for(int i=0;i<p.prizeCount();i++)if(Double.isFinite(prizePose(opponent,i).intersect(ray)))return true;return false;
    }
    private double shuffleMs(UUID seat){
        if(animationQuality==BattleAnimationEngine.Quality.OFF)return -1;
        for(var a:animations)if(a.cue().kind()==MatchPresentation.Kind.SHUFFLE&&Objects.equals(seat,a.cue().seat())){
            double ms=(frameNow-a.started())/1e6;
            if(ms>=0&&ms<ShuffleAnimation.DURATION_NS/1e6)return ms;
        }
        return -1;
    }
    private void drawPerspectiveTable(DrawContext c){
        var cam=camera();var l=layout();
        var own=player(bottomSeat());var opponentPlayer=player(topSeat());
        tableEnvironment.update(own==null||own.active()==null?null:own.active().pokemonType(),
            opponentPlayer==null||opponentPlayer.active()==null?null:opponentPlayer.active().pokemonType(),System.nanoTime());
        PocketTableRenderer.begin(c,cam,tableEnvironment,frameNow/1e9,animationQuality);
        try{
            PocketTableRenderer.table(cam);
            drawTurnTiles(c,cam);
            for(boolean opponent:new boolean[]{true,false}){
                var seat=opponent?topSeat():bottomSeat();var p=player(seat);if(p==null)continue;
                int y=l.benchY(opponent);
                for(int i=0;i<5;i++)PocketTableRenderer.slot(cam.card(new Rect(l.benchX(i),y,l.benchWidth(),l.benchHeight()),l),0xFFFFFFFF);
                PocketTableRenderer.slot(cam.card(new Rect(l.activeX(),l.activeY(opponent),l.activeWidth(),l.activeHeight()),l),0xFFFFFFFF);
                for(int x:new int[]{l.deckX(),l.discardX(),l.lostX(),l.prizeX()})PocketTableRenderer.slot(cam.card(cardRect(l,x,opponent),l),0xFFFFFFFF);
                if(p.deckSize()+pendingDealCards(seat,false)>0){
                    var deck=cam.card(cardRect(l,l.deckX(),opponent),l);double ms=shuffleMs(seat);
                    if(ms<0)PocketTableRenderer.card(deck,null,CARD_BORDER,1);
                    else for(int layer=0;layer<ShuffleAnimation.LAYERS;layer++)PocketTableRenderer.card(ShuffleAnimation.pose(deck,ms,layer),null,CARD_BORDER,1);
                }
                for(int i=0;i<p.prizeCount();i++)PocketTableRenderer.card(prizePose(opponent,i),null,hasPrizeOptions()&&!opponent?ACCENT:CARD_BORDER,1);
                var visibleDiscard=visibleToolDiscard(p.discard(),seat);
                var discardPose=cam.card(cardRect(l,l.discardX(),opponent),l);
                if(opponent)discardPose=discardPose.angles(discardPose.tilt(),180);
                if(!visibleDiscard.isEmpty())PocketTableRenderer.card(discardPose,artwork.get(visibleDiscard.getLast().definitionId()),CARD_BORDER,1);
                if(animationQuality!=BattleAnimationEngine.Quality.OFF)for(var a:animations)if(toolDiscard(a)&&seat.equals(toolPileSeat(a.cue()))){
                    double ms=animationElapsed(a,frameNow)*1000;
                    if(ms>=ToolAttachmentAnimation.discardStart()&&ms<ToolAttachmentAnimation.discardEnd())
                        PocketTableRenderer.card(ToolAttachmentAnimation.discardPose(discardPose,ms),artwork.get(a.cue().cardId()),CARD_BORDER,1);
                }
                var visibleLost=visibleToolLost(p.lostZone(),seat);
                if(!visibleLost.isEmpty())PocketTableRenderer.card(cam.card(cardRect(l,l.lostX(),opponent),l),artwork.get(visibleLost.getLast().definitionId()),CARD_BORDER,1);
            }
            PocketTableRenderer.slot(cam.card(new Rect(l.stadiumX(),l.stadiumY(),l.benchWidth(),l.benchHeight()),l),0xFFFFFFFF);
            for(var p:logicalPlacements()){
                if(!onTable(p.card())||dragging!=null&&dragging.key().equals(p.card().key())||cardPanel!=null&&cardPanel.card.key().equals(p.card().key()))continue;
                var pose=tablePose(p,true);
                if(!tableCardVisible(p.card())){
                    for(var a:animations)if(p.card().key().equals("field:"+a.cue().pokemon())&&a.previousCard()!=null
                        &&(a.cue().kind()==MatchPresentation.Kind.EVOLVE&&animationElapsed(a,frameNow)*1000<BattleAnimationEngine.evolution().impactMs
                            ||a.cue().kind()==MatchPresentation.Kind.DEVOLVE&&animationElapsed(a,frameNow)<0))
                        PocketTableRenderer.card(pose,artwork.get(a.previousCard()),CARD_BORDER,1);
                    continue;
                }
                var pokemon=fieldPokemon(p.card());
                if(pokemon.isPresent()){
                    var energy=pokemon.get().attachedEnergyCardIds();
                    // Bounded visible stack, all counts and names remain in the HUD/inspection.
                    for(int i=Math.min(3,energy.size())-1;i>=0;i--)PocketTableRenderer.card(pose.move((i+1)*.06,-.004*(i+1),.07*(i+1)),artwork.get(energy.get(i)),CARD_BORDER,1);
                }
                int border=borderColor(p.card());
                double hover=tableHover.getOrDefault(p.card().key(),0.);
                boolean marked=border==SELECTED||border==ACCENT;
                if(animationQuality!=BattleAnimationEngine.Quality.OFF&&(marked||hover>0)){
                    int tint=border==SELECTED?0xFFA4F6D4:BattleSceneEffects.CYAN;
                    PocketTableRenderer.focus(pose,BattleEffects.alpha(tint,marked?.85:.65*hover));
                }
                boolean dim=(dragging!=null||selectedSource!=null)&&border==CARD_BORDER;
                PocketTableRenderer.card(pose,artwork.get(p.card().definitionId()),border,1,dim?0xBBC4CC:0xFFFFFF);
            }
            for(var a:animations)if(a.cue().kind()==MatchPresentation.Kind.LEAVE&&a.previousLogical()!=null){
                double e=animationElapsed(a,frameNow);boolean ko=a.cue().text().startsWith("击倒");
                double alpha=e<0?1:ko?KnockoutAnimation.sample(e*1000).cardAlpha():0;
                if(alpha>0)PocketTableRenderer.card(cam.card(a.previousLogical(),previousLayout(a)),artwork.get(a.cue().cardId()),CARD_BORDER,alpha);
            }
        }finally{PocketTableRenderer.end();}
    }
    private void drawTableLabels(DrawContext c){
        var l=layout();
        for(boolean opponent:new boolean[]{true,false}){
            var p=player(opponent?topSeat():bottomSeat());if(p==null)continue;
            tableLabel(c,cardRect(l,l.deckX(),opponent),"牌库 "+(p.deckSize()+pendingDealCards(p.id(),false)));
            tableLabel(c,cardRect(l,l.discardX(),opponent),"弃牌 "+visibleToolDiscard(p.discard(),p.id()).size());
            tableLabel(c,cardRect(l,l.lostX(),opponent),"放逐 "+visibleToolLost(p.lostZone(),p.id()).size());
            tableLabel(c,cardRect(l,l.prizeX(),opponent),"奖赏 "+p.prizeCount());
        }
        tableLabel(c,new Rect(l.stadiumX(),l.stadiumY(),l.benchWidth(),l.benchHeight()),"场地");
    }
    private void tableLabel(DrawContext c,Rect logical,String title){
        var r=tableRect(logical);int y=r.bottom()+2;
        if(camera().portrait){
            y=r.bottom()-textRenderer.fontHeight-2;
            int half=textRenderer.getWidth(title)/2+3;
            c.fill(midX(r)-half,y-1,midX(r)+half,y+textRenderer.fontHeight+1,0xD5223040);
        }
        BattleUi.centeredText(c,textRenderer,title,midX(r),y,0xFFF2F7FF,true,1);
    }
    private boolean inspectTableCard(MatchRegionModel.Card card){
        if(currentOverlay()!=null)return true;
        dragging=null;dragOrigin=null;popup=null;menuOpen=false;
        var rows=dev.hua.ptcglive.view.BattleCardActions.rows(snapshot,card.key(),awaitingServer||disconnected?List.of():cardFaceOptions(card),attackHints(card));
        cardPanel=new BattleCardPanel(card,fieldPokemon(card).orElse(null),snapshot.revision(),rows);clearAndInit();return true;
    }
    private Rect cameraDebugRect(){return new Rect(8,8,212,166);}
    private void addCameraButtons(){
        var r=cameraDebugRect();
        for(int i=0;i<6;i++){final int row=i;
            for(int direction:new int[]{-1,1})addDrawableChild(new TableButton(r.x()+153+(direction>0?25:0),r.y()+24+i*20,22,17,direction>0?"+":"−",TableButton.Style.NORMAL,1,b->{
                var s=cameraSettings;cameraSettings=switch(row){
                    case 0->s.pitch(s.pitch()+direction*2);case 1->s.fov(s.fov()+direction*2);
                    case 2->s.height(s.height()+direction);case 3->s.distance(s.distance()+direction);
                    case 4->s.aspect(s.aspect()+direction*.1);default->s.spacing(s.spacing()+direction*.05);
                };cachedCamera=null;clearAndInit();
            }));
        }
        addDrawableChild(new TableButton(r.right()-26,r.y()+3,22,17,"×",TableButton.Style.NORMAL,1,b->{cameraDebug=false;clearAndInit();}));
    }
    private void drawCameraDebug(DrawContext c){
        var r=cameraDebugRect();BattleUi.panel(c,r,1);BattleUi.text(c,textRenderer,"固定镜头 · 自动完整取景",r.x()+6,r.y()+7,TEXT,false,1);
        var s=cameraSettings;String[] labels={String.format(Locale.ROOT,"俯角 %.1f°",s.pitch()),String.format(Locale.ROOT,"垂直 FOV %.1f°",s.fov()),
            String.format(Locale.ROOT,"高度 %.1f → %.1f",s.height(),camera().actualHeight),String.format(Locale.ROOT,"距离 %.1f → %.1f",s.distance(),camera().actualDistance),
            String.format(Locale.ROOT,"桌面比例 %.2f → %.2f",s.aspect(),PocketTableCamera.TABLE_WIDTH/camera().tableDepth),String.format(Locale.ROOT,"双方间距 %.2f",s.spacing())};
        for(int i=0;i<labels.length;i++)BattleUi.text(c,textRenderer,labels[i],r.x()+6,r.y()+29+i*20,TEXT,false,1);
        BattleUi.text(c,textRenderer,"俯角、高度、距离联动",r.x()+6,r.bottom()-13,TEXT_DIM,false,1);
    }

    private void addField(List<Placed> result, UUID seat, boolean opponent, PtcgLiveTableLayout layout) {
        var bench = zone(seat, ":bench").cards(); int y = layout.benchY(opponent), count = bench.size();
        // 五格以内各占一格；场地卡让备战区变大时在托盘里互相压住，一只都不藏。
        int step = count <= 5 ? layout.benchWidth() + layout.slotGap()
            : Math.max(2, (layout.trayWidth() - 2 * layout.pad() - layout.benchWidth()) / (count - 1));
        for (int i = 0; i < count; i++)
            result.add(new Placed(bench.get(i), layout.trayX() + layout.pad() + i * step, y, layout.benchWidth(), layout.benchHeight()));
        var active = zone(seat, ":active").cards();
        if (!active.isEmpty())
            result.add(new Placed(active.getFirst(), layout.activeX(), layout.activeY(opponent), layout.activeWidth(), layout.activeHeight()));
    }
    private Overlay currentOverlay() { return pileView != null ? pileOverlay() : overlay(); }
    private List<Placed> overlayPlacements(Overlay content) {
        if (content == null) return List.of();
        if(choiceOverlay()){
            if(content.cards().isEmpty())return List.of();
            if(CopyAttackChoicePage.active(snapshot)){
                var r=CopyAttackPanel.card(width,height);
                return List.of(new Placed(content.cards().getFirst(),r.x(),r.y(),r.width(),r.height()));
            }
            int h=Math.round(height*.54f),w=Math.round(h*CARD_ASPECT);
            return List.of(new Placed(content.cards().getFirst(),(width-w)/2,Math.round(height*.16f),w,h));
        }
        var grid = grid(layout(), content.cards().size(), content.kind() != OverlayKind.PILE);
        var result = new ArrayList<Placed>();
        for (int i = 0; i < content.cards().size(); i++)
            result.add(new Placed(content.cards().get(i), grid.cardX(i), grid.cardY(i), grid.cardWidth(), grid.cardHeight()));
        return result;
    }
    /** 鼠标下最上面的卡；弹层打开时，弹层范围里只认弹层的卡。 */
    private Placed at(double x, double y) {
        var content = currentOverlay();
        if (content != null && overlayArea(layout()).contains(x, y)) {
            if(selectionOverlay())for(var placed:selectedPlacements())if(placed.contains(x,y))return placed;
            var cards = overlayPlacements(content);
            for (int i = cards.size() - 1; i >= 0; i--) if (cards.get(i).contains(x, y)) return cards.get(i);
            return null;
        }
        var board = logicalPlacements();
        Placed closest=null;double closestDistance=Double.POSITIVE_INFINITY;
        var ray=tableMode()?camera().ray(x,y):null;
        for (int i = board.size() - 1; i >= 0; i--) {
            var p=board.get(i);
            if(!tableMode()||!onTable(p.card())){if(p.contains(x,y))return p;}
            else {
                double distance=tablePose(p,false).intersect(ray);
                if(distance<closestDistance-1e-5){closest=p;closestDistance=distance;}
            }
        }
        return closest==null?null:projected(closest);
    }

    // ================= 中间弹层 =================

    /** 检索选牌、公开展示、超量工具（EffectCardPage 每页 5 张），以及开局重抽展示。附能另放在手牌行。 */
    private Overlay overlay() {
        if(effectCollapsed||finitePresentationLocked())return null;
        var game = snapshot.game();
        if (EffectCardPage.identity(snapshot) != null && game.effectAttachment() == null) {
            var page = EffectCardPage.from(snapshot, selectionPage);
            if (game.toolOverflow() != null) {
                var overflow = game.toolOverflow(); boolean mine = overflow.owner().equals(game.viewingSeat());
                return new Overlay(OverlayKind.EFFECT, (overflow.bench()?"备战区超出上限：":"工具超出容量：上限 ") + overflow.capacity() + " 张，还需弃置 " + overflow.excess() + " 张",
                    mine ? (overflow.bench()?"点击要弃置的备战宝可梦":"点击要弃置的工具") : "等待所属玩家选择", page.cards(), page.page(), page.pages());
            }
            if (game.effectReveal() != null) {
                var reveal = game.effectReveal(); boolean mine = reveal.confirmingPlayer().equals(game.viewingSeat());
                // titleZh: a hand reveal (“对手公开手牌 N 张”); a searched-card reveal has none.
                return new Overlay(OverlayKind.EFFECT, reveal.titleZh() != null ? reveal.titleZh() : "公开展示 " + reveal.cardIds().size() + " 张",
                    mine ? "看完后点确认 · 右键看大图" : "等待对手确认 · 右键看大图", page.cards(), page.page(), page.pages());
            }
            var selection = game.effectSelection();
            if (selection != null) return new Overlay(OverlayKind.EFFECT, choiceSource()+" · 步骤 "+selection.step()+" · "+selection.labelZh(),
                (selection.minimum()==selection.maximum()?"恰好 "+selection.minimum():"最少 "+selection.minimum()+" / 最多 "+selection.maximum())
                    +" 张 · 已选 "+selection.selected().size()+(selection.minimum()==0?" · 可以不选":"")
                    +(snapshot.options().stream().anyMatch(o->o.enabled()&&(o.labelZh().startsWith("确认选牌")||o.labelZh().startsWith("确认撤退")))?" · 可以确认":" · 尚未满足数量／能量要求")+" · 标号为选择顺序",
                page.cards(), page.page(), page.pages());
        }
        if (game.phase() == MatchPhase.MULLIGAN_REVEAL && !game.mulliganReveals().isEmpty()) {
            var cards = new ArrayList<MatchRegionModel.Card>();
            for (int r = 0; r < game.mulliganReveals().size(); r++) {
                var reveal = game.mulliganReveals().get(r);
                String who = sideName(reveal.playerId()) + " · 第 " + reveal.attempt() + " 次重抽展示";
                for (int i = 0; i < reveal.cardIds().size(); i++) {
                    String id = reveal.cardIds().get(i);
                    cards.add(new MatchRegionModel.Card("mulligan:" + r + ":" + i, id, name(id), who));
                }
            }
            int per = 5, pages = Math.max(1, (cards.size() + per - 1) / per);
            mulliganPage = Math.max(0, Math.min(mulliganPage, pages - 1));
            return new Overlay(OverlayKind.MULLIGAN, "开局手牌没有基础宝可梦：展示后重抽", "只读 · 右键看大图",
                cards.subList(mulliganPage * per, Math.min(cards.size(), mulliganPage * per + per)), mulliganPage, pages);
        }
        if(choiceOverlay()){
            String id=game.resolvingTrainer()==null?null:game.resolvingTrainer().definitionId();
            UUID pokemon=game.resolvingAbility()!=null?game.resolvingAbility().pokemonId():game.resolvingAttack()!=null?game.resolvingAttack().pokemonId():null;
            if(id==null&&pokemon!=null){var mon=publicPokemon(snapshot,pokemon);if(mon!=null)id=mon.topCardId();}
            var cards=id==null?List.<MatchRegionModel.Card>of():List.of(new MatchRegionModel.Card("choice-source:"+id,id,name(id),"效果来源 · 只读"));
            var page=EffectChoicePage.from(snapshot,flowPage);
            if(CopyAttackChoicePage.active(snapshot)){
                var copy=CopyAttackChoicePage.from(snapshot,flowPage);
                return new Overlay(OverlayKind.EFFECT,"请选择要复制的招式",message.isBlank()?choiceSource():message,cards,copy.page(),copy.pages());
            }
            return new Overlay(OverlayKind.EFFECT,choiceSource()+" · 请选择效果",message.isBlank()?"按卡牌说明继续结算 · 右键查看来源卡":message,cards,page.page(),page.pages());
        }
        return null;
    }
    /** 弃牌区／放逐区：最上面的牌排在最前，按弹层大小铺满成网格。 */
    private Overlay pileOverlay() {
        if (pileView == null) return null;
        var cards = new ArrayList<>(zone(pileView.zoneId()).cards());
        Collections.reverse(cards);
        int per = Math.max(1, grid(layout(), 1000, false).capacity()), pages = Math.max(1, (cards.size() + per - 1) / per);
        pilePage = Math.max(0, Math.min(pilePage, pages - 1));
        return new Overlay(OverlayKind.PILE, pileView.titleZh() + " · " + cards.size() + " 张", "只读 · 最上面的牌在最前 · 右键看大图",
            cards.subList(pilePage * per, Math.min(cards.size(), pilePage * per + per)), pilePage, pages);
    }
    /** 弹层中间放卡的区域：上面两行字，下面一排按钮，左右各留翻页箭头的位置。 */
    private Rect overlayBand(PtcgLiveTableLayout layout) {
        if(selectionOverlay())return CardSelectionAnimation.candidates(width,height,layout.ui());
        var area = overlayArea(layout); int k = layout.ui(), arrow = 24 * k;
        int top = area.y() + 26 * k, bottom = area.bottom() - layout.buttonHeight() - 8 * k;
        return new Rect(area.x() + arrow, top, Math.max(10, area.width() - 2 * arrow), Math.max(10, bottom - top));
    }
    /** singleRow：检索／展示一行放下这一页；否则（查看牌堆）按卡大小铺成网格。 */
    private Grid grid(PtcgLiveTableLayout layout, int count, boolean singleRow) {
        var band = overlayBand(layout); int gap = Math.max(4, layout.slotGap() * 2);
        int height = Math.min(singleRow&&publicRevealOverlay() ? Math.round(this.height*.65f) : singleRow ? Math.round(layout.activeHeight() * 1.3f) : layout.activeHeight(), band.height());
        int width = Math.max(10, Math.round(height * CARD_ASPECT));
        if (singleRow && count > 1 && count * width + (count - 1) * gap > band.width()) {
            width = Math.max(10, (band.width() - (count - 1) * gap) / count); height = Math.round(width / CARD_ASPECT);
        }
        int columns = singleRow ? Math.max(1, count) : Math.max(1, (band.width() + gap) / (width + gap));
        int rows = singleRow ? 1 : Math.max(1, (band.height() + gap) / (height + gap));
        int shownColumns = Math.max(1, Math.min(columns, count)), shownRows = Math.max(1, Math.min(rows, (count + columns - 1) / columns));
        int spanX = shownColumns * width + (shownColumns - 1) * gap, spanY = shownRows * height + (shownRows - 1) * gap;
        return new Grid(band.x() + (band.width() - spanX) / 2, band.y() + (band.height() - spanY) / 2,
            width, height, width + gap, height + gap, columns, rows);
    }

    // ================= 绘制 =================

    private long acknowledgedPresentationRevision=-1;
    private boolean presentationReadyToAcknowledge(){
        return !disconnected&&snapshot.computer()!=null&&snapshot.revision()!=acknowledgedPresentationRevision&&!battleInputLocked();
    }
    private void acknowledgePresentation(){
        if(!presentationReadyToAcknowledge()||client==null||client.getNetworkHandler()==null)return;
        ClientPlayNetworking.send(new MatchIntentPayload(matchId().toString(),seat(),Math.toIntExact(snapshot.revision()),MatchIntentPayload.PRESENTATION_FINISHED));
        acknowledgedPresentationRevision=snapshot.revision();
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        frameNow=presentationClock.now();
        syncReveal();
        if(snapshot.game().effectSelection()==null||!finitePresentationLocked())cardSelectionAnimation.sync(snapshot.game().effectSelection(),frameNow);
        String choice=choiceIdentity();
        if(choice.isEmpty()){displayedChoice="";choiceStarted=0;}
        else if(!choice.equals(displayedChoice)&&!finitePresentationLocked()){displayedChoice=choice;choiceStarted=frameNow;}
        // A queued cue can hand off directly to a locked choice entrance. The
        // overall lock stays true, but its controls must change at that boundary.
        if(presentationLocked!=battleInputLocked()||controlsWaitingForCue!=finitePresentationLocked())clearAndInit();
        if (cardPanel != null && cardPanel.finished()) { cardPanel = null; clearAndInit(); }
        battleAudio.tick(frameNow);
        recycleElectricEffects(frameNow);
        var layout = layout(); int k = layout.ui();
        boolean modal = presentationLocked || cardPanel != null || popup != null || pileView != null || hasPrizeOptions();
        hoveredHand = modal ? -1 : handIndexAt(mouseX, mouseY, layout);
        boolean arenaTarget = dragging != null && !zoneOptions(dragging, DropZone.ARENA).isEmpty();
        context.getMatrices().push();
        if(tableMode()){
            updateTableHover(mouseX,mouseY,modal||menuOpen||currentOverlay()!=null||cameraDebug);
            drawPerspectiveTable(context);
        }else {
            BattleUi.table(context,layout,arenaTarget);
            drawTurnTiles(context,null);
        }
        if(!tableMode()&&snapshot.game().phase()==MatchPhase.TURN&&snapshot.game().result()==null)
            BattleSceneEffects.turnEdge(context,layout.arena(),Objects.equals(snapshot.game().currentPlayer(),bottomSeat()),frameNow/1e9);
        UUID top = topSeat(), bottom = bottomSeat();
        if(tableMode())drawTableLabels(context);
        else{
            if (top != null) drawSide(context, layout, top, true, mouseX, mouseY);
            if (bottom != null) drawSide(context, layout, bottom, false, mouseX, mouseY);
            drawStadiumSlot(context, layout);
        }
        if (top != null) drawAvatar(context, layout, top, true);
        if (bottom != null) drawAvatar(context, layout, bottom, false);
        if(snapshot.game().result()==null)drawRightColumn(context, layout);
        var content = currentOverlay();
        if (snapshot.game().result()==null && content == null && popup == null && cardPanel == null && !hasPrizeOptions()) drawPrompt(context, layout);
        for (var placed : boardPlacements())
            if ((dragging == null || !dragging.key().equals(placed.card().key()))
                && (cardPanel == null || !cardPanel.card.key().equals(placed.card().key()))) {
                if(tableMode()&&onTable(placed.card())){
                    if(tableCardVisible(placed.card())){
                        fieldPokemon(placed.card()).ifPresent(mon->drawPokemonState(context,mon,placed,k));
                        var allocation=snapshot.game().damageAllocation();
                        if(allocation!=null&&placed.card().key().startsWith("field:")){
                            int pending=allocation.counters().getOrDefault(UUID.fromString(placed.card().key().substring(6)),0);
                            if(pending>0)BattleUi.centeredText(context,textRenderer,"+"+pending,placed.x()+placed.width()/2,placed.y()+placed.height()/2,ACCENT,false,k);
                        }
                    }
                }else drawCard(context, placed, layout);
            }
        var hand = handCards();
        if (!hand.isEmpty()&&!(selectionOverlay()&&!finitePresentationLocked())) {
            String handHint = hoveredHand >= 0 && hoveredHand < hand.size() ? hand.get(hoveredHand).nameZh()
                : "手牌 " + (handOffset+1)+"—"+(handOffset+hand.size()) + " · 滚轮查看更多 · 拖放 / 右键大图";
            BattleUi.centeredText(context, textRenderer,
                textRenderer.trimToWidth(handHint, (layout.rightColumnX() - 2 * layout.margin()) / k),
                layout.rightColumnX() / 2, layout.handHintY(), tableMode()?0xFFF1F6FF:TEXT, tableMode(), k);
        }
        context.draw();
        context.getMatrices().push();
        context.getMatrices().translate(0,0,100);
        drawQueuedStadium(context,layout);
        drawFeedback(context,layout);
        context.getMatrices().pop();
        context.getMatrices().pop();
        // Buffered board text has its own depth. Later draw order alone does not
        // cover it: give modal panels and their controls a real foreground layer.
        if (content != null || popup != null || menuOpen || cardPanel != null) context.draw();
        context.getMatrices().push();
        if (content != null) {
            context.getMatrices().translate(0, 0, 200);
            context.fill(0,0,width,height,publicRevealOverlay()?0x22406480:selectionOverlay()||choiceOverlay()?0x880F192C:0x55406480);
            drawOverlay(context, layout, content);
        }
        if (popup != null) {
            context.getMatrices().translate(0, 0, 200);
            drawPopupPanel(context, layout);
        }
        if(menuOpen){
            context.getMatrices().translate(0,0,200);
            int menuW=Math.max(layout.rightColumnWidth(),64*k),menuX=width-layout.margin()-menuW;
            int menuY=menuTop(layout);
            BattleUi.panel(context,new Rect(menuX-2*k,menuY-2*k,menuW+4*k,11*(layout.buttonHeight()+3*k)+k),k);
        }
        if (cardPanel != null) {
            context.getMatrices().translate(0, 0, 300);
            var origin = boardPlacements().stream().filter(p -> p.card().key().equals(cardPanel.card.key())).findFirst()
                .map(p -> new Rect(p.x(),p.y(),p.width(),p.height())).orElse(new Rect(layout.activeX(),layout.ownActiveY(),layout.activeWidth(),layout.activeHeight()));
            var raw=tableMode()?logicalPlacements().stream().filter(p->p.card().key().equals(cardPanel.card.key())&&onTable(p.card())).findFirst().orElse(null):null;
            cardPanel.draw(context, artwork, origin, width, height,raw!=null?camera():null,raw==null?null:tablePose(raw,false));
        }
        if(snapshot.game().result()!=null) {
            context.getMatrices().translate(0,0,400);
            boolean ready=frameNow>=resultStarted;
            boolean showButtons=ready&&!presentationLocked;
            for(var child:children())if(child instanceof net.minecraft.client.gui.widget.ClickableWidget widget)widget.visible=showButtons;
            if(ready)drawResultSheet(context,layout);
        }
        if(cameraDebug)drawCameraDebug(context);
        super.render(context, mouseX, mouseY, delta);
        context.getMatrices().pop();
        if (dragging != null) {
            boolean effects=animationQuality!=BattleAnimationEngine.Quality.OFF;
            double lift=effects?BattleMotion.out((frameNow-dragStarted)/120e6):0;
            int w=(int)Math.round(dragWidth*(1+.06*lift)),h=(int)Math.round(dragHeight*(1+.06*lift));
            int x=(int)Math.round(mouseX-grabX*w),y=(int)Math.round(mouseY-grabY*h);
            if(effects){
                BattleEffects.shadow(context,x+w/2,y+h+(int)(h*.04*lift),w,h,lift);
                BattleSceneEffects.focus(context,x,y,w,h,BattleSceneEffects.CYAN,frameNow/1e9);
            }
            BattleUi.card(context,textRenderer,artwork.get(dragging.definitionId()),dragging.nameZh(),x,y,w,h,ACCENT);
            var target=at(mouseX,mouseY);var drop=dropZoneAt(mouseX,mouseY);
            boolean sameCard=dragAtOrigin(dragging,mouseX,mouseY)||target!=null&&dragging.key().equals(target.card().key());
            var choices=dropOptions(dragging,target,drop,sameCard).stream()
                .filter(o->!MatchActionMatcher.isAttackOption(o)).toList();
            String landing=choices.isEmpty()?"此处不是可用落点":choices.size()==1?choices.getFirst().labelZh():"松开后选择操作（"+choices.size()+"项）";
            BattleUi.orderedTooltip(context,textRenderer,textRenderer.wrapLines(Text.literal(landing),Math.max(80,Math.min(220,width-16))),mouseX,mouseY);
        }
        if (draggingCounter) BattleUi.damageBadge(context, 10, mouseX, mouseY, k);
        if (snapshot.game().result()==null && cardPanel == null && dragging == null && !draggingCounter && popup == null && !menuOpen && !hasPrizeOptions()) drawHoverTooltip(context, layout, mouseX, mouseY);
        if(prizeChoicesVisible()) {
            if(prizeChoiceStarted==0)prizeChoiceStarted=frameNow;
            context.draw();context.getMatrices().push();context.getMatrices().translate(0,0,500);
            drawPrizeChoices(context,layout,mouseX,mouseY);context.getMatrices().pop();
        }else prizeChoiceStarted=0;
        if(logOpen)drawEventLog(context);
        acknowledgePresentation();
    }

    private String choiceSource() {
        var g=snapshot.game();
        if(g.resolvingAbility()!=null)return g.resolvingAbility().nameZh();
        if(g.resolvingAttack()!=null)return g.resolvingAttack().nameZh();
        if(g.resolvingTrainer()!=null)return name(g.resolvingTrainer().definitionId());
        return "当前效果";
    }
    private void drawEventLog(DrawContext context) {
        int x=12,y=20,w=Math.max(40,width-24),bottom=height-20;
        BattleUi.panel(context,new Rect(x,y,w,bottom-y),1);
        BattleUi.text(context,textRenderer,"本局公开记录 · 滚轮查看 · 点击或 Esc 收起",x+8,y+6,BattleUi.INK,false,1);
        var rows=new ArrayList<net.minecraft.text.OrderedText>();
        if(snapshot.events()!=null)for(var e:snapshot.events())rows.addAll(textRenderer.wrapLines(BattleUi.label("第 "+e.turn()+" 回合 · "+
            (e.cue().seat()==null?"场面":sideName(e.cue().seat()))+" · "+e.text()),w-16));
        int capacity=Math.max(1,(bottom-y-28)/12);
        logOffset=Math.max(0,Math.min(logOffset,Math.max(0,rows.size()-capacity)));
        int from=Math.max(0,rows.size()-capacity-logOffset);
        for(int i=0;i<capacity&&from+i<rows.size();i++)context.drawText(textRenderer,rows.get(from+i),x+8,y+24+i*12,BattleUi.INK,false);
    }

    /** 不调用原版的世界模糊或压暗：桌垫本身就是半透明底。 */
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    /** 一侧的托盘与牌堆：备战区五格、战斗位、放逐区、奖赏卡、牌库、弃牌区。只画公开数量与公开卡面。 */
    private void drawSide(DrawContext context, PtcgLiveTableLayout layout, UUID seat, boolean opponent, int mouseX, int mouseY) {
        var player = player(seat);
        if (player == null) return;
        int k = layout.ui(), edge = opponent ? TRAY_EDGE_OPPONENT : TRAY_EDGE_OWN;
        int y = layout.benchY(opponent), width = layout.benchWidth(), height = layout.benchHeight();
        boolean benchTarget = !opponent && dragging != null && !zoneOptions(dragging, DropZone.OWN_BENCH).isEmpty();
        BattleUi.tray(context, layout.benchTray(opponent), benchTarget ? BattleUi.CYAN : edge);
        for (int i = player.bench().size(); i < 5; i++) BattleUi.emptySlot(context, textRenderer, "", layout.benchX(i), y, width, height, k);
        boolean activeTarget = !opponent && dragging != null && !zoneOptions(dragging, DropZone.OWN_ACTIVE).isEmpty();
        var slot = layout.activeSlot(opponent);
        BattleUi.tray(context, slot, activeTarget ? BattleUi.CYAN : edge);
        if (player.active() == null)
            BattleUi.emptySlot(context, textRenderer, "战斗位", layout.activeX(), layout.activeY(opponent), layout.activeWidth(), layout.activeHeight(), k);
        BattleUi.tray(context, layout.lostTray(opponent), 0);
        drawPile(context, visibleToolLost(player.lostZone(),seat), layout.lostX(), y, width, height, "放逐", k, false);
        boolean taking = !opponent && seat.equals(snapshot.game().viewingSeat()) && hasPrizeOptions();
        BattleUi.tray(context, layout.prizeTray(opponent), taking ? BattleUi.CYAN : 0);
        int prizes = player.prizeCount();
        if (prizes == 0) BattleUi.emptySlot(context, textRenderer, "奖赏", layout.prizeX(), y, width, height, k);
        int hoveredPrize = taking ? prizeIndexAt(mouseX, mouseY, layout) : -1;
        for (int i = 0; i < prizes; i++) BattleUi.cardBack(context, layout.prizeX() + i * layout.prizeFan(), y, width, height);
        if (hoveredPrize >= 0) BattleSceneEffects.focus(context,layout.prizeX()+hoveredPrize*layout.prizeFan(),y,width,height,BattleSceneEffects.CYAN,frameNow/1e9);
        if (prizes > 0) BattleUi.countTab(context, textRenderer, prizes, layout.prizeX() + (prizes - 1) * layout.prizeFan() + width, y + height, k);
        BattleUi.tray(context, layout.pileTray(opponent), 0);
        int deckSize=player.deckSize()+pendingDealCards(seat,false);
        if (deckSize > 0) {
            double ms=shuffleMs(seat);
            if(ms<0)BattleUi.cardBack(context, layout.deckX(), y, width, height);
            else for(int layer=0;layer<ShuffleAnimation.LAYERS;layer++){
                var s=ShuffleAnimation.sample(ms);
                int dx=(int)Math.round(width*(layer*.008*s.edge()+(layer==ShuffleAnimation.LAYERS-1?s.lift()*.12:0)));
                int dy=(int)Math.round(height*ShuffleAnimation.flatLift(ms,layer));
                tiltedCard(context,null,layout.deckX()+width/2+dx,y+height/2-dy,width,height,CARD_BORDER,layer==ShuffleAnimation.LAYERS-1?s.turn():0);
            }
            BattleUi.countTab(context, textRenderer, deckSize, layout.deckX() + width, y + height, k);
        } else BattleUi.emptySlot(context, textRenderer, "牌库", layout.deckX(), y, width, height, k);
        drawPile(context, visibleToolDiscard(player.discard(),seat), layout.discardX(), y, width, height, "弃牌", k, opponent);
    }

    private static boolean toolDiscard(AnimatedCue a){
        return a.cue().kind()==MatchPresentation.Kind.TOOL&&a.cue().amount()<0&&a.cue().text().startsWith("工具弃置");
    }
    private static boolean toolLost(AnimatedCue a){
        return a.cue().kind()==MatchPresentation.Kind.TOOL&&a.cue().amount()<0&&a.cue().text().startsWith("工具放逐");
    }
    private static UUID toolPileSeat(MatchPresentation.Cue cue){
        if(cue.text().startsWith("工具弃置·")||cue.text().startsWith("工具放逐·"))try{
            return UUID.fromString(cue.text().substring(cue.text().indexOf('·')+1));
        }catch(IllegalArgumentException ignored){}
        return cue.seat();
    }
    private static boolean isToolReturn(MatchPresentation.Cue cue){
        return cue.kind()==MatchPresentation.Kind.TOOL&&cue.amount()<0&&cue.text().startsWith("工具回手·");
    }
    private static UUID toolReturnOwner(MatchPresentation.Cue cue){
        if(!isToolReturn(cue))return null;
        try{return UUID.fromString(cue.text().split("·",-1)[1]);}catch(IllegalArgumentException|IndexOutOfBoundsException ignored){return null;}
    }
    /** Public attached instance only: never associate a return with another hidden card of the same name. */
    private static UUID returnedToolInstance(MatchPresentation.Cue cue){
        if(!isToolReturn(cue))return null;
        try{return UUID.fromString(cue.text().split("·",-1)[2]);}catch(IllegalArgumentException|IndexOutOfBoundsException ignored){return null;}
    }
    private double toolReturnHandMs(AnimatedCue animation,double elapsed){
        return elapsed*1000-animation.toolReturnOrder()*ToolAttachmentAnimation.returnStagger();
    }
    /** Hide only the incoming public instance until its visual landing; the confirmed pile is untouched. */
    private List<CardInstance> visibleToolDiscard(List<CardInstance> cards,UUID seat){
        return visibleToolPile(cards,seat,false);
    }
    private List<CardInstance> visibleToolLost(List<CardInstance> cards,UUID seat){
        return visibleToolPile(cards,seat,true);
    }
    private List<CardInstance> visibleToolPile(List<CardInstance> cards,UUID seat,boolean lost){
        List<CardInstance> visible=cards;
        if(animationQuality!=BattleAnimationEngine.Quality.OFF)for(var a:animations)
            if(a.pileArrival()!=null&&lost==(a.cue().kind()==MatchPresentation.Kind.LOST)&&Objects.equals(seat,a.cue().seat())
                &&animationElapsed(a,frameNow)*1000<(lost?ToolLostAnimation.LAND_MS:DiscardAnimation.LAND_MS)){
                if(visible==cards)visible=new ArrayList<>(cards);
                var id=a.pileArrival().card().instanceId();visible.removeIf(card->card.instanceId().equals(id));
            }
        for(var a:animations)if(a.stadium()!=null&&a.stadium().lost()==lost&&seat.equals(a.stadium().card().ownerId())
            &&animationElapsed(a,frameNow)*1000<StadiumAnimation.exitEnd(a.cue(),a.rejectedStadium()!=null)){
            if(visible==cards)visible=new ArrayList<>(cards);
            visible.removeIf(card->card.instanceId().equals(a.stadium().card().instanceId()));
        }
        for(var a:animations)if(a.rejectedStadium()!=null&&a.rejectedStadium().lost()==lost&&seat.equals(a.rejectedStadium().card().ownerId())
            &&animationElapsed(a,frameNow)*1000<StadiumAnimation.REJECTED_LAND_MS){
            if(visible==cards)visible=new ArrayList<>(cards);
            visible.removeIf(card->card.instanceId().equals(a.rejectedStadium().card().instanceId()));
        }
        if(animationQuality==BattleAnimationEngine.Quality.OFF)return visible;
        for(var a:animations)if((lost?toolLost(a):toolDiscard(a))&&seat.equals(toolPileSeat(a.cue()))&&a.toolInstance()!=null
            &&animationElapsed(a,frameNow)*1000<(lost?ToolLostAnimation.LAND_MS:ToolAttachmentAnimation.discardEnd())){
            if(visible==cards)visible=new ArrayList<>(cards);
            visible.removeIf(card->card.instanceId().equals(a.toolInstance()));
        }
        return visible;
    }

    /** 公开牌堆：空时是带标签的空格，否则最上面一张朝上加张数。 */
    private void drawPile(DrawContext context, List<CardInstance> cards, int x, int y, int width, int height, String label, int k, boolean opponent) {
        if (cards.isEmpty()) { BattleUi.emptySlot(context, textRenderer, label, x, y, width, height, k); return; }
        var top = cards.getLast();
        if(opponent)tiltedCard(context,top.definitionId(),x+width/2,y+height/2,width,height,CARD_BORDER,180);
        else BattleUi.card(context, textRenderer, artwork.get(top.definitionId()), name(top.definitionId()), x, y, width, height, CARD_BORDER);
        BattleUi.countTab(context, textRenderer, cards.size(), x + width, y + height, k);
    }

    private void drawStadiumSlot(DrawContext context, PtcgLiveTableLayout layout) {
        int k = layout.ui(); var slot = layout.stadiumSlot();
        BattleUi.tray(context, slot, 0);
        if (zone("stadium").cards().isEmpty())
            BattleUi.emptySlot(context, textRenderer, "", layout.stadiumX(), layout.stadiumY(), layout.benchWidth(), layout.benchHeight(), k);
        BattleUi.centeredText(context, textRenderer, "场地", slot.x() + slot.width() / 2, slot.y() - 10 * k, TEXT_DIM, false, k);
    }

    /** 头像栏：头像、名字、回合中标记、GX/VSTAR 标记；对方再加手牌数。 */
    private void drawAvatar(DrawContext context, PtcgLiveTableLayout layout, UUID seat, boolean opponent) {
        var player = player(seat);
        if (player == null) return;
        var area = layout.avatar(opponent); int k = layout.ui();
        if(tableMode()&&!camera().portrait){
            var center=camera().project(camera().card(new Rect(layout.activeX(),layout.activeY(opponent),layout.activeWidth(),layout.activeHeight()),layout));
            area=new Rect(area.x(),(int)Math.round(center.y())-area.height()/2,area.width(),area.height());
        }
        else if(tableMode()&&opponent)area=new Rect(area.x(),4*k,area.width(),area.height());
        var game = snapshot.game();
        boolean current = game.phase() == MatchPhase.TURN && seat.equals(game.currentPlayer()) && game.result() == null;
        if(tableMode())BattleUi.panel(context,area,k);
        else BattleUi.tray(context, area, current ? BattleSceneEffects.CYAN : opponent ? TRAY_EDGE_OPPONENT : TRAY_EDGE_OWN);
        CardPainter.playerHead(context, seat, area.x() + 3 * k, area.y() + 3 * k, 16 * k);
        int textX = area.x() + 22 * k, textWidth = Math.max(0, (area.right() - 2 * k - textX) / k);
        BattleUi.text(context, textRenderer, textRenderer.trimToWidth(playerName(seat), textWidth), textX, area.y() + 3 * k,
            opponent ? SEAT_OPPONENT : SEAT_OWN, false, k);
        String role = current ? "回合中" : seat.equals(game.viewingSeat()) ? "我方" : game.viewingSeat() == null ? "选手" : "对手";
        BattleUi.text(context, textRenderer, textRenderer.trimToWidth(role, textWidth), textX, area.y() + 12 * k,
            current ? ACCENT : TEXT_DIM, false, k);
        int plateY = area.y() + 22 * k;
        CardPainter.plate(context, false, player.gxUsed(), area.x() + 3 * k, plateY, k);
        CardPainter.plate(context, true, player.vstarUsed(), area.x() + 24 * k, plateY, k);
        if (opponent) BattleUi.text(context, textRenderer, "手牌 " + Math.max(0,player.handSize()-pendingDealCards(seat,true)), area.x() + 3 * k, plateY + 14 * k, TEXT_SOFT, false, k);
    }

    /** 右侧栏：“第 n 回合”、结束回合按钮（是控件）、谁的回合或当前阶段。 */
    private void drawRightColumn(DrawContext context, PtcgLiveTableLayout layout) {
        int k = layout.ui(); var end = layout.endTurn(); int centerX = end.x() + end.width() / 2, room = end.width() / k;
        var game = snapshot.game();
        String turn = game.turnNumber() > 0 ? "第 " + game.turnNumber() + " 回合" : "开局";
        BattleUi.centeredText(context, textRenderer, textRenderer.trimToWidth(turn, room), centerX, end.y() - 12 * k, tableMode()?0xFFF1F6FF:TEXT, tableMode(), k);
        String status; int color;
        if (game.result() != null) { status = "对局结束"; color = NOTICE; }
        else if (game.phase() == MatchPhase.TURN && game.currentPlayer() != null) {
            boolean mine = game.currentPlayer().equals(game.viewingSeat());
            status = game.viewingSeat() == null ? playerName(game.currentPlayer()) + "回合" : mine ? "我方回合" : "对方回合";
            color = mine ? SEAT_OWN : SEAT_OPPONENT;
        } else { status = phaseName(game.phase()); color = TEXT_SOFT; }
        BattleUi.centeredText(context, textRenderer, textRenderer.trimToWidth(status, room), centerX, end.bottom() + 4 * k, tableMode()?0xFFCCEEFF:color, true, k);
    }

    /** 提示区：标题（谁的回合／阶段）、这一步该怎么做、服务器回执；伤害分配时左上角是指示物。 */
    private void drawPrompt(DrawContext context, PtcgLiveTableLayout layout) {
        var area = layout.prompt(); int k = layout.ui(), y = area.y(), room = Math.max(10, area.width() / k);
        if(battleInputLocked()){
            if(tableMode()){
                int lineY=(canSwitchSeat()?layout.switchButton():layout.menuButton()).bottom()+8*k;
                for(String line:wrap("特效播放中，请稍候",Math.max(8,layout.rightColumnWidth()/k))){
                    BattleUi.centeredText(context,textRenderer,line,layout.rightColumnX()+layout.rightColumnWidth()/2,lineY,0xFFF1F6FF,true,k);
                    lineY+=10*k;
                }
            }else BattleUi.text(context,textRenderer,"特效播放中，请稍候",area.x(),y,TEXT_SOFT,false,k);
            return;
        }
        var allocation = snapshot.game().damageAllocation();
        if(tableMode()&&(!promptLines().isEmpty()||allocation!=null))BattleUi.panel(context,new Rect(area.x()-3*k,area.y()-3*k,area.width()+6*k,area.height()+6*k),k);
        if (allocation != null) {
            var tray = layout.counterTray();
            if (!draggingCounter) BattleUi.damageBadge(context, 10, tray.x() + tray.size() / 2, tray.y() + tray.size() / 2, k);
            BattleUi.text(context, textRenderer, "剩余 " + (allocation.budget() - allocation.allocated()) + " / " + allocation.budget(),
                tray.x() + tray.size() + 4 * k, tray.y() + (tray.size() - 8 * k) / 2, ACCENT, false, k);
            y = tray.y() + tray.size() + 4 * k;
        }
        for (var line : promptLines()) {
            for (String row : wrap(line.text(), room)) {
                if (y + 9 * k > promptTextBottom) return;
                BattleUi.text(context, textRenderer, row, area.x(), y, line.color(), false, k);
                y += 10 * k;
            }
            y += 3 * k;
        }
    }

    /** 按像素宽度逐字折行（中文没有空格，按字断开）。 */
    private List<String> wrap(String text, int width) {
        var rows = new ArrayList<String>(); var row = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i); String next = new String(Character.toChars(codePoint));
            if (row.length() > 0 && textRenderer.getWidth(row + next) > width) { rows.add(row.toString()); row.setLength(0); }
            row.append(next); i += Character.charCount(codePoint);
        }
        if (row.length() > 0) rows.add(row.toString());
        return rows;
    }

    private void drawOverlay(DrawContext context, PtcgLiveTableLayout layout, Overlay content) {
        if(selectionOverlay()){drawCardSelection(context,layout,content);return;}
        if(choiceOverlay()){
            var area=overlayArea(layout);int k=layout.ui();
            if(CopyAttackChoicePage.active(snapshot)){
                var r=CopyAttackPanel.heading(width,height,k);
                BattleUi.round(context,r.x(),r.y(),r.width(),r.height(),r.height()/2,0xF8FFFFFF);
                BattleUi.centeredText(context,textRenderer,textRenderer.trimToWidth(content.titleZh(),r.width()/k-16),width/2,r.y()+6*k,TEXT,false,k);
                BattleUi.centeredText(context,textRenderer,textRenderer.trimToWidth(content.detailZh(),r.width()/k-16),width/2,r.y()+19*k,TEXT_SOFT,false,k);
            }else{
            BattleUi.panel(context,new Rect(8*k,area.y(),width-16*k,27*k),k);
            BattleUi.centeredText(context,textRenderer,textRenderer.trimToWidth(content.titleZh(),(width-24*k)/k),width/2,area.y()+4*k,TEXT,false,k);
            BattleUi.centeredText(context,textRenderer,textRenderer.trimToWidth(content.detailZh(),(width-24*k)/k),width/2,area.y()+15*k,TEXT_SOFT,false,k);
            }
            double p=BattleMotion.out((frameNow-choiceStarted)/(double)CardSelectionAnimation.ENTRY_NS);
            context.getMatrices().push();context.getMatrices().translate(0,16*k*(1-p),0);
            for(var card:overlayPlacements(content))drawCard(context,card,layout);
            context.getMatrices().pop();return;
        }
        var area = overlayArea(layout); int k = layout.ui(), centerX = area.x() + area.width() / 2, room = Math.max(10, (area.width() - 8 * k) / k);
        boolean reveal=publicRevealOverlay();
        if(reveal)BattleUi.panel(context,new Rect(area.x(),area.y(),area.width(),27*k),k);
        else BattleUi.panel(context,area,k);
        BattleUi.centeredText(context, textRenderer, textRenderer.trimToWidth(content.titleZh(), room), centerX, area.y() + 4 * k, ACCENT, false, k);
        // 服务器回执（比如选满了还在点）优先显示在第二行，不然被弹层挡住就看不到了。
        String detail = !message.isBlank() ? message
            : content.detailZh() + (content.pages() > 1 ? " · 第 " + (content.page() + 1) + " / " + content.pages() + " 页" : "");
        BattleUi.centeredText(context, textRenderer, textRenderer.trimToWidth(detail, room), centerX, area.y() + 15 * k,
            message.isBlank() ? TEXT_SOFT : NOTICE, true, k);
        if (content.cards().isEmpty()) {
            var band = overlayBand(layout);
            BattleUi.centeredText(context, textRenderer, "没有卡牌", centerX, band.y() + band.height() / 2 - 4 * k, TEXT_DIM, false, k);
        }
        int index=0;
        for (var placed : overlayPlacements(content)) {
            if(!reveal){drawCard(context,placed,layout);continue;}
            double p=CardRevealAnimation.progress((frameNow-revealStarted)/1e6,index++);
            if(p<=0)continue;
            double cx=placed.x()+placed.width()/2.,cy=placed.y()+placed.height()/2.;
            var matrices=context.getMatrices();matrices.push();
            matrices.translate(cx+placed.width()*.50*(1-p),cy+placed.height()*.40*(1-p),0);
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees((float)(10*(1-p))));
            matrices.scale((float)(.12+.88*p),(float)(.80+.20*p),1);matrices.translate(-cx,-cy,0);
            drawCard(context,placed,layout);matrices.pop();
        }
    }

    private boolean publicRevealOverlay(){return snapshot.game().effectReveal()!=null&&!effectCollapsed&&pileView==null;}
    private boolean selectionOverlay(){var g=snapshot.game();return g.effectSelection()!=null&&g.effectReveal()==null&&g.effectAttachment()==null&&g.toolOverflow()==null&&!effectCollapsed&&pileView==null;}
    private boolean choiceOverlay(){return EffectCardPage.identity(snapshot)==null&&!EffectChoicePage.all(snapshot).isEmpty()&&!effectCollapsed&&pileView==null&&snapshot.game().result()==null;}
    private String choiceIdentity(){return !choiceOverlay()?"":choiceSource()+EffectChoicePage.all(snapshot).stream().map(o->o.type()+":"+o.index()+":"+o.commandId()+":"+o.labelZh()).toList();}
    private boolean choiceAnimating(){String id=choiceIdentity();return !id.isEmpty()&&(!id.equals(displayedChoice)||presentationClock.now()-choiceStarted<CardSelectionAnimation.ENTRY_NS);}
    private Rect overlayArea(PtcgLiveTableLayout layout){return selectionOverlay()||choiceOverlay()?CardSelectionAnimation.area(width,height):publicRevealOverlay()?CardRevealAnimation.area(width,height,layout.ui()):layout.overlay();}
    private List<Placed> selectedPlacements(){
        var prompt=snapshot.game().effectSelection();if(prompt==null)return List.of();
        var cards=model.zone("effect-selection").cards();var result=new ArrayList<Placed>();
        int start=Math.max(0,prompt.selected().size()-5),count=Math.max(1,Math.min(5,prompt.maximum()));
        for(int i=start;i<prompt.selected().size();i++){
            String key="effect:"+prompt.selected().get(i);
            var card=cards.stream().filter(c->c.key().equals(key)).findFirst().orElse(null);if(card==null)continue;
            var r=CardSelectionAnimation.slot(width,height,layout().ui(),count,i-start);
            result.add(new Placed(card,r.x(),r.y(),r.width(),r.height()));
        }
        return result;
    }
    private void drawCardSelection(DrawContext c,PtcgLiveTableLayout layout,Overlay content){
        int k=layout.ui();var area=overlayArea(layout);var tray=CardSelectionAnimation.tray(width,height);
        double entry=cardSelectionAnimation.entry(frameNow);
        BattleUi.panel(c,new Rect(8*k,area.y(),width-16*k,27*k),k);
        int room=Math.max(1,(width-24*k)/k);
        BattleUi.centeredText(c,textRenderer,textRenderer.trimToWidth(content.titleZh(),room),width/2,area.y()+4*k,TEXT,false,k);
        String detail=message.isBlank()?content.detailZh():message;
        BattleUi.centeredText(c,textRenderer,textRenderer.trimToWidth(detail,room),width/2,area.y()+15*k,message.isBlank()?TEXT_SOFT:NOTICE,false,k);
        c.getMatrices().push();c.getMatrices().translate(0,22*k*(1-entry),0);
        c.fillGradient(tray.x(),tray.y(),tray.right(),tray.bottom(),0xF5EEF0FF,0xF5D3E4F3);
        c.fill(0,tray.y(),width,tray.y()+k,0xFFFFFFFF);
        c.fill(0,tray.bottom()-k,width,tray.bottom(),0xFFA8C7DE);
        var prompt=snapshot.game().effectSelection();int count=Math.max(1,Math.min(5,prompt.maximum()));
        int start=Math.max(0,prompt.selected().size()-5);
        for(int i=0;i<count;i++){
            var r=CardSelectionAnimation.slot(width,height,k,count,i);
            BattleUi.round(c,r.x(),r.y(),r.width(),r.height(),3*k,0xFFBACDDE);
            BattleUi.round(c,r.x()+k,r.y()+k,r.width()-2*k,r.height()-2*k,2*k,0xFFDBE4EF);
            BattleUi.centeredText(c,textRenderer,Integer.toString(start+i+1),midX(r),midY(r)-4*k,0xFF8EACC3,false,k);
        }
        for(var placed:selectedPlacements()){
            var id=UUID.fromString(placed.card().key().substring(7));
            if(!cardSelectionAnimation.flying(id,frameNow))drawCard(c,placed,layout);
        }
        c.getMatrices().pop();
        var candidates=overlayPlacements(content);
        for(var placed:candidates)drawCard(c,placed,layout);
        for(var placed:selectedPlacements()){
            var id=UUID.fromString(placed.card().key().substring(7));if(!cardSelectionAnimation.flying(id,frameNow))continue;
            var from=candidates.stream().filter(p->p.card().key().equals(placed.card().key())).findFirst().orElse(new Placed(placed.card(),width/2-placed.width()/2,Math.round(height*.82f),placed.width(),placed.height()));
            double p=cardSelectionAnimation.progress(frameNow);
            int x=(int)Math.round(from.x()+(placed.x()-from.x())*p),y=(int)Math.round(from.y()+(placed.y()-from.y())*p);
            int w=(int)Math.round(from.width()+(placed.width()-from.width())*p),h=(int)Math.round(from.height()+(placed.height()-from.height())*p);
            BattleUi.round(c,x-3*k,y-3*k,w+6*k,h+6*k,4*k,BattleEffects.alpha(0xFF6FFFF0,.6*Math.sin(Math.PI*p)));
            drawCard(c,new Placed(placed.card(),x,y,w,h),layout);
        }
        String footer="候选卡 "+(content.page()+1)+" / "+content.pages()+" 页 · 点击选择／取消 · 右键查看";
        if(start>0)footer="卡槽显示第 "+(start+1)+"—"+prompt.selected().size()+" 张 · "+footer;
        if(content.cards().isEmpty())footer="没有可选卡牌 · 请按规则确认或返回";
        BattleUi.centeredText(c,textRenderer,textRenderer.trimToWidth(footer,room),width/2,Math.round(height*.76f),0xFFF0F7FF,true,k);
    }
    private void syncReveal(){
        var reveal=snapshot.game().effectReveal();
        if(reveal==null){displayedReveal=null;revealStarted=0;}
        else if(!reveal.id().equals(displayedReveal)&&!finitePresentationLocked()){
            displayedReveal=reveal.id();revealStarted=frameNow;
        }
    }
    private boolean revealAnimating(){
        var reveal=snapshot.game().effectReveal();
        return reveal!=null&&!effectCollapsed&&(!reveal.id().equals(displayedReveal)||presentationClock.now()-revealStarted<CardRevealAnimation.READ_NS);
    }

    private void drawPopupPanel(DrawContext context, PtcgLiveTableLayout layout) {
        var area = popupRect(layout); int k = layout.ui(), rows = popupRows(layout);
        int pages = (popup.options().size() + rows - 1) / rows;
        BattleUi.panel(context,area,k);
        String title = "选择要执行的操作" + (pages > 1 ? "（" + (popupPage + 1) + " / " + pages + "）" : "");
        BattleUi.centeredText(context, textRenderer, textRenderer.trimToWidth(title, Math.max(10, (area.width() - 8 * k) / k)),
            area.x() + area.width() / 2, area.y() + 3 * k, ACCENT, false, k);
    }

    /** Only server-confirmed cues enter this queue; finite cues hold local input until expiry. */
    private long displayDuration(MatchPresentation.Cue cue) {
        if(cue.kind()==MatchPresentation.Kind.LOST)return LostZoneAnimation.DURATION_NS;
        if(cue.kind()==MatchPresentation.Kind.DISCARD)return DiscardAnimation.DURATION_NS;
        if(cue.kind()==MatchPresentation.Kind.SHUFFLE)return ShuffleAnimation.DURATION_NS;
        if(cue.kind()==MatchPresentation.Kind.COIN)return coinDuration(cue);
        if(cue.kind()==MatchPresentation.Kind.ABILITY)return AbilityAnimation.DURATION_NS;
        if(cue.kind()==MatchPresentation.Kind.STADIUM)return stadiumDuration(cue);
        if(SwitchAnimation.applies(cue))return SwitchAnimation.DURATION_NS;
        if(RetreatAnimation.applies(cue))return RetreatAnimation.DURATION_NS;
        if(cue.kind()==MatchPresentation.Kind.DEVOLVE)return DevolutionAnimation.DURATION_NS;
        if(PokemonEntryAnimation.applies(cue))return PokemonEntryAnimation.duration(entryReveal(cue));
        if(DrawCardAnimation.draw(cue))return DrawCardAnimation.arrival(cue,Objects.equals(cue.seat(),snapshot.game().viewingSeat()),cue.amount()-1);
        if(isItemPlay(cue)||isSupporterPlay(cue)||isToolPlay(cue))return feedbackDuration(cue);
        if(cue.kind()==MatchPresentation.Kind.TOOL)return feedbackDuration(cue);
        if(cue.kind()==MatchPresentation.Kind.EVOLVE)return (long)(BattleAnimationEngine.evolution().durationMs*1e6);
        if(isEnergyDiscard(cue))return (long)(BattleAnimationEngine.energyDiscard().durationMs*1e6);
        if(isEnergyTransfer(cue))return (long)(BattleAnimationEngine.energyTransfer().durationMs*1e6);
        if(cue.kind()==MatchPresentation.Kind.PRIZE)return feedbackDuration(cue);
        if(cue.kind()==MatchPresentation.Kind.LEAVE&&cue.text().startsWith("击倒"))
            return (long)(BattleAnimationEngine.knockout().durationMs*1e6);
        return snapshot.computer()!=null?dev.hua.ptcglive.view.AiPresentationTiming.nanos(cue):feedbackDuration(cue);
    }
    /** Preserve each existing motion's complete phases when shortening its presentation window. */
    private double animationElapsed(AnimatedCue animation,long now) {
        if(animation.cue().kind()==MatchPresentation.Kind.LOST)return (now-animation.started())/1e9;
        if(animation.cue().kind()==MatchPresentation.Kind.DISCARD)return (now-animation.started())/1e9;
        if(animation.cue().kind()==MatchPresentation.Kind.SHUFFLE)return (now-animation.started())/1e9;
        if(animation.cue().kind()==MatchPresentation.Kind.COIN)return (now-animation.started())/1e9;
        if(animation.cue().kind()==MatchPresentation.Kind.ABILITY)return (now-animation.started())/1e9;
        if(animation.cue().kind()==MatchPresentation.Kind.STADIUM)return (now-animation.started())/1e9;
        if(SwitchAnimation.applies(animation.cue()))return (now-animation.started())/1e9;
        if(RetreatAnimation.applies(animation.cue()))return (now-animation.started())/1e9;
        if(animation.cue().kind()==MatchPresentation.Kind.DEVOLVE)return (now-animation.started())/1e9;
        if(PokemonEntryAnimation.applies(animation.cue()))return (now-animation.started())/1e9;
        if(DrawCardAnimation.draw(animation.cue()))return (now-animation.started())/1e9;
        // Contact is scheduled in real time. AI cadence must not pull the card home before its hit.
        if(animation.cue().kind()==MatchPresentation.Kind.ATTACK
            ||animation.cue().kind()==MatchPresentation.Kind.DAMAGE&&animation.cue().text().startsWith("招式伤害·"))
            return (now-animation.started())/1e9;
        if(animation.cue().kind()==MatchPresentation.Kind.EVOLVE)return (now-animation.started())/1e9;
        if(isEnergyDiscard(animation.cue()))return (now-animation.started())/1e9;
        if(isEnergyTransfer(animation.cue()))return (now-animation.started())/1e9;
        if(animation.cue().kind()==MatchPresentation.Kind.LEAVE&&animation.cue().text().startsWith("击倒"))
            return (now-animation.started())/1e9;
        return (now-animation.started())/1e9*feedbackDuration(animation.cue())/displayDuration(animation.cue());
    }
    private static boolean isSupporterPlay(MatchPresentation.Cue cue){
        return cue.kind()==MatchPresentation.Kind.PLAY&&cue.pokemon()==null&&TrainerPlayAnimation.supporter(cue.cardId());
    }
    private static boolean isToolPlay(MatchPresentation.Cue cue){
        return cue.kind()==MatchPresentation.Kind.PLAY&&cue.pokemon()==null&&TrainerPlayAnimation.tool(cue.cardId());
    }
    private static boolean isItemPlay(MatchPresentation.Cue cue){
        return cue.kind()==MatchPresentation.Kind.PLAY&&cue.pokemon()==null&&TrainerPlayAnimation.item(cue.cardId());
    }
    private double attackContact(AnimatedCue attack) {
        long impact=Long.MAX_VALUE;
        for(var next:animations){
            if(next.batch()!=attack.batch()||next.started()<attack.started())continue;
            if(next.cue().kind()==MatchPresentation.Kind.ATTACK&&next.started()>attack.started())break;
            if(next.cue().kind()==MatchPresentation.Kind.DAMAGE&&next.cue().text().startsWith("招式伤害·"))
                impact=Math.min(impact,next.started());
        }
        return impact==Long.MAX_VALUE?.65:(impact-attack.started())/1e9;
    }
    private BattleAnimationEngine.Definition attackAnimation(AnimatedCue animation) {
        return animationDefinitions.computeIfAbsent(animation,this::resolveAttackAnimation);
    }
    private BattleAnimationEngine.Definition resolveAttackAnimation(AnimatedCue animation) {
        AnimatedCue source=animation;
        if(animation.cue().kind()==MatchPresentation.Kind.DAMAGE) {
            source=null;
            for(var candidate:animations)if(candidate.batch()==animation.batch()
                &&candidate.started()<=animation.started()&&candidate.cue().kind()==MatchPresentation.Kind.ATTACK)
                if(source==null||candidate.started()>source.started())source=candidate;
        }
        String card=source==null?null:source.cue().cardId()!=null?source.cue().cardId():source.previousCard();
        var group=new ArrayList<MatchPresentation.Cue>();
        if(source!=null) {
            long until=Long.MAX_VALUE;
            for(var candidate:animations)if(candidate.batch()==source.batch()&&candidate.started()>source.started()
                &&candidate.cue().kind()==MatchPresentation.Kind.ATTACK)until=Math.min(until,candidate.started());
            for(var candidate:animations)if(candidate.batch()==source.batch()&&candidate.started()>=source.started()&&candidate.started()<until)
                group.add(candidate.cue());
        }
        return BattleAnimationEngine.resolve(card,source==null?null:source.cue().text(),BattleAnimationEngine.moveType(group),animation.type());
    }
    private double attackRecovery(AnimatedCue animation) {
        var d=attackAnimation(animation);
        if(d!=null&&animation.type()==EnergyType.LIGHTNING)return d.electric().durationMs()/1000;
        return d==null?.37:(d.timeline().durationMs-d.timeline().impactMs)/1000;
    }
    private boolean animationExpired(AnimatedCue animation,long now) {
        return now-animation.started()>animationDuration(animation);
    }
    private long animationDuration(AnimatedCue animation) {
        long duration=displayDuration(animation.cue());
        if(isToolReturn(animation.cue()))duration+=(long)(animation.toolReturnOrder()*ToolAttachmentAnimation.returnStagger()*1e6);
        if(animation.cue().kind()==MatchPresentation.Kind.ATTACK)
            duration=Math.max(duration,(long)((attackContact(animation)+attackRecovery(animation))*1e9));
        if(animation.cue().kind()==MatchPresentation.Kind.DAMAGE&&animation.cue().text().startsWith("招式伤害·")) {
            var definition=attackAnimation(animation);
            double delay=damagePopupMs(animation)/1000;
            duration=Math.max(duration,(long)(Math.max(Math.max(1.1+delay,attackRecovery(animation)),definition.timeline().sample("hp.endMs",0,0)/1000)*1e9));
        }
        return duration;
    }

    /** Finite presentation only: queued cues also hold input; ambient pulses never do. */
    private boolean battleInputLocked() {
        return !disconnected&&(finitePresentationLocked()||revealAnimating()||choiceAnimating()
            ||!effectCollapsed&&cardSelectionAnimation.locked(snapshot.game().effectSelection(),presentationClock.now())
            ||prizeChoiceAnimating());
    }
    private boolean finitePresentationLocked() {
        if(disconnected)return false;
        if(cardPanel!=null&&cardPanel.animating())return true;
        // OFF reduces particles, but card movement and announcements can still be playing.
        long now=presentationClock.now();
        if(introStarted!=0&&now-introStarted<1_200_000_000L)return true;
        if(resultStarted!=0&&now-resultStarted<Math.max(300_000_000L,resultBannerDuration()))return true;
        for(var animation:animations)if(!animationExpired(animation,now))return true;
        return false;
    }
    private static long feedbackDuration(MatchPresentation.Cue cue) {
        return BattleAnimationTiming.duration(cue);
    }
    private static PokemonView publicPokemon(MatchSession.Snapshot view,UUID id) {
        if(id==null)return null;
        for(var player:view.game().players()) {
            if(player.active()!=null&&player.active().pokemonId().equals(id))return player.active();
            for(var mon:player.bench())if(mon.pokemonId().equals(id))return mon;
        }
        return null;
    }
    private static double unit(double value) { return Math.max(0, Math.min(1, value)); }
    private static int between(int from, int to, double progress) { return (int)Math.round(from+(to-from)*unit(progress)); }
    private static int midX(Rect rect) { return rect.x()+rect.width()/2; }
    private static int midY(Rect rect) { return rect.y()+rect.height()/2; }
    private Rect cueTarget(AnimatedCue animation, List<Placed> positions) {
        UUID pokemon=animation.cue().pokemon();
        if (pokemon != null) for (var placed:positions)
            if (placed.card().key().equals("field:"+pokemon))
                return new Rect(placed.x(),placed.y(),placed.width(),placed.height());
        return previousCueTarget(animation);
    }
    private Rect previousCueTarget(AnimatedCue animation) {
        if(tableMode()&&animation.previousLogical()!=null)return camera().bounds(camera().card(animation.previousLogical(),previousLayout(animation)));
        var old=animation.previousLogical()!=null?animation.previousLogical():animation.previousTarget();
        if(old==null||animation.previousWidth()==width&&animation.previousHeight()==height)return old;
        if(animation.previousWidth()<=0||animation.previousHeight()<=0)return null;
        return new Rect((int)Math.round((double)old.x()*width/animation.previousWidth()),
            (int)Math.round((double)old.y()*height/animation.previousHeight()),
            Math.max(1,(int)Math.round((double)old.width()*width/animation.previousWidth())),
            Math.max(1,(int)Math.round((double)old.height()*height/animation.previousHeight())));
    }
    private Rect handAnchor(PtcgLiveTableLayout layout, UUID seat) {
        if (Objects.equals(seat,topSeat())) return new Rect(layout.activeX()+layout.activeWidth()/2,
            layout.margin()+layout.benchHeight()/3,1,1);
        return new Rect(layout.screenWidth()/2,layout.handY()+layout.activeHeight()/3,1,1);
    }
    private void flyingCard(DrawContext context,String cardId,int x,int y,int w,int h,int border) {
        BattleEffects.shadow(context,x,y+h/2+2,w,h,.55);
        if (cardId==null) BattleUi.cardBack(context,x-w/2,y-h/2,w,h);
        else BattleUi.card(context,textRenderer,artwork.get(cardId),name(cardId),x-w/2,y-h/2,w,h,border);
    }
    private void tiltedCard(DrawContext context,String cardId,int x,int y,int w,int h,int border,double angle) {
        var matrices=context.getMatrices();matrices.push();matrices.translate(x,y,0);
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees((float)angle));
        flyingCard(context,cardId,0,0,w,h,border);matrices.pop();
    }
    private void drawEventRibbon(DrawContext context,PtcgLiveTableLayout layout,String label,double elapsed,double duration) {
        double enter=BattleMotion.out(elapsed/.22),opacity=BattleMotion.envelope(elapsed,duration,.16,.25);
        int k=layout.ui(),w=Math.min(layout.arena().width()-8*k,Math.max(120*k,(textRenderer.getWidth(label)+32)*k));
        int h=18*k,cx=layout.activeX()+layout.activeWidth()/2;
        int y=Math.max(layout.margin(),layout.handY()-h-8*k)+(int)((1-enter)*6*k);
        if(opacity<4/255.0)return;
        BattleEffects.shadow(context,cx,y+h,w,h,opacity);
        BattleUi.round(context,cx-w/2,y,w,h,7*k,BattleEffects.alpha(0xFFF9FEFF,opacity*.96));
        context.fill(cx-w/2+6*k,y+h-2*k,cx+w/2-6*k,y+h-k,BattleEffects.alpha(BattleSceneEffects.CYAN,opacity*.75));
        BattleUi.centeredText(context,textRenderer,textRenderer.trimToWidth(label,(w-12*k)/k),cx,y+5*k,
            BattleEffects.alpha(0xFF26344D,opacity),false,k);
    }
    private void drawOpeningVersus(DrawContext c,PtcgLiveTableLayout l,double elapsed) {
        double opacity=BattleMotion.envelope(elapsed,1.2,.12,.24);
        if(opacity<.02)return;
        var arena=l.arena();
        int k=l.ui(),cx=arena.x()+arena.width()/2,cy=l.centerY();
        int half=Math.min(arena.width()/5,height/3), gap=14*k;
        int band=Math.min(height/6,38*k);
        double travel=(1-BattleMotion.out(elapsed/.28))*width;
        c.fill(0,0,width,height,BattleEffects.alpha(0xFF272E47,.69*opacity));
        var matrices=c.getMatrices();
        for(int i=0;i<2;i++){
            boolean opponent=i==0;
            int side=opponent?-1:1,y=cy+side*band/4;
            matrices.push();
            matrices.translate(cx+side*(half+gap+travel),y,0);
            matrices.push();
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(-7));
            c.fillGradient(-half,-band/2,half,band/2,
                BattleEffects.alpha(opponent?0xFFF44156:0xFF06D8F4,opacity),
                BattleEffects.alpha(opponent?0xFFFF875F:0xFF138BEA,opacity));
            matrices.pop();
            UUID seat=opponent?topSeat():bottomSeat();
            if(seat!=null){
                int badgeX=side*half/2,r=Math.min(band/2,13*k);
                BattleEffects.disc(c,badgeX,0,r,BattleEffects.alpha(0xFFECFCFF,opacity));
                c.draw();
                matrices.translate(0,0,1);
                CardPainter.playerHead(c,seat,badgeX-r*2/3,-r*2/3,4*r/3);
                BattleUi.centeredText(c,textRenderer,textRenderer.trimToWidth(playerName(seat),Math.max(1,half/k-4)),
                    -side*half/4,-4*k,BattleEffects.alpha(0xFFFFFFFF,opacity),true,k);
            }
            matrices.pop();
        }
        double burst=Math.clamp((elapsed-.18)/.34,0,1);
        if(animationQuality!=BattleAnimationEngine.Quality.OFF && burst>0 && burst<1)BattleEffects.ring(c,cx,cy,(12+48*BattleMotion.out(burst))*k,1,
            BattleEffects.alpha(0xFFE7FAFF,(1-burst)*opacity));
        if(elapsed>.24)BattleUi.centeredText(c,textRenderer,"VS",cx,cy-8*k,
            BattleEffects.alpha(0xFFFFFFFF,opacity),true,2*k);
    }
    private void drawAttackRibbon(DrawContext c,PtcgLiveTableLayout l,String label,EnergyType type,double elapsed,double duration) {
        double opacity=BattleMotion.envelope(elapsed,Math.min(duration,1.15),.10,.22);if(opacity<.02)return;
        var arena=l.arena();
        int k=l.ui(),w=Math.min(arena.width()*43/100,210*k),h=28*k;
        String text=label.replaceFirst("^(攻击：|发动招式：|使用招式：)","");
        double slide=(1-BattleMotion.out(elapsed/.18))*Math.min(20*k,arena.width()*.04);
        c.getMatrices().push();c.getMatrices().translate(arena.x()+arena.width()*.70+slide,l.centerY(),0);
        c.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(-3));
        BattleUi.round(c,-w/2,-h/2+3*k,w,h,5*k,BattleEffects.alpha(0xFF36516C,opacity*.7));
        BattleUi.round(c,-w/2,-h/2,w,h,5*k,BattleEffects.alpha(BattleEffects.color(type),opacity));
        c.fill(-w/2+8*k,-h/2+2*k,w/2-8*k,-h/2+3*k,BattleEffects.alpha(0xFFFFFFFF,opacity*.85));
        BattleUi.centeredText(c,textRenderer,textRenderer.trimToWidth(text,(w-20*k)/k),0,-4*k,BattleEffects.alpha(BattleUi.INK,opacity),false,k);
        c.getMatrices().pop();
    }
    private void drawAttackIntroduction(DrawContext context,PtcgLiveTableLayout layout,AnimatedCue animation,double seconds) {
        if(animation.cue().text().contains("攻击失败"))return;
        var definition=attackAnimation(animation);if(definition==null)return;
        double ms=BattleAnimationEngine.attackTime(definition,seconds,attackContact(animation));
        double reveal=definition.timeline().sample("reveal.scale",ms,0);if(reveal<.01)return;
        double dim=definition.timeline().sample("reveal.dim",ms,0);
        context.fill(0,0,width,height,BattleEffects.alpha(0xFF102032,dim));
        var arena=layout.arena();
        int h=(int)(Math.min(Math.min(height*.72,layout.activeHeight()*3.4),arena.width()*.39/.716)*reveal),w=(int)(h*.716);
        var matrix=context.getMatrices();matrix.push();
        matrix.translate(arena.x()+arena.width()*.28,layout.centerY(),0);
        matrix.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees((float)definition.timeline().sample("reveal.tilt",ms,0)));
        String card=animation.cue().cardId()!=null?animation.cue().cardId():animation.previousCard();
        flyingCard(context,card,0,0,w,h,CARD_BORDER);
        for(int i=0;i<(animationQuality==BattleAnimationEngine.Quality.LOW?2:5);i++) {
            double q=(ms*.0013+i*.19)%1;
            BattleEffects.line(context,-w*.9+q*w*.5,-h*.45+i*h*.20,w*.8+q*w*.5,-h*.70+i*h*.20,
                Math.max(1,layout.ui()),BattleEffects.alpha(0xFFF1FAFF,dim*.45));
        }
        matrix.pop();
    }
    private void drawTurnTiles(DrawContext context,PocketTableCamera camera){
        var game=snapshot.game();
        if(animationQuality==BattleAnimationEngine.Quality.OFF || game.phase()!=MatchPhase.TURN || game.currentPlayer()==null || !PocketTableRenderer.ready())return;
        boolean own=Objects.equals(game.currentPlayer(),bottomSeat());
        double progress=1;
        for(var animation:animations){
            var cue=animation.cue();
            if(game.turnNumber()>1 && cue.kind()==MatchPresentation.Kind.TURN && cue.seat()!=null){
                own=Objects.equals(cue.seat(),bottomSeat());
                progress=Math.clamp((animationElapsed(animation,frameNow)-.65)/.55,0,1);
                break;
            }
        }
        if(camera!=null)PocketTableRenderer.turnTiles(camera,progress,own);
        else PocketTableRenderer.flatTurnTiles(context,layout(),progress,own,animationQuality);
    }
    private void drawSceneAnnouncement(DrawContext context,PtcgLiveTableLayout layout,String label,
                                        MatchPresentation.Kind kind,double elapsed,double duration){
        if(kind==MatchPresentation.Kind.TURN&&!label.equals("对战开始")){
            BattleSceneEffects.turnBanner(context,textRenderer,layout.arena(),layout.centerY(),label,
                snapshot.game().turnNumber(),label.equals("你的回合"),elapsed,duration);return;
        }
        double a=BattleMotion.envelope(elapsed,duration,.18,.30);if(a<4/255.0)return;
        boolean result=kind==MatchPresentation.Kind.RESULT,won=label.equals("胜利");
        if(result)BattleSceneEffects.result(context,layout.arena(),elapsed,won);
        else BattleSceneEffects.sweep(context,layout.arena(),elapsed,duration,BattleSceneEffects.CYAN);
        int k=layout.ui(),w=Math.min(layout.arena().width()-12*k,(result?172:146)*k),h=(result?42:29)*k;
        int x=layout.activeX()+layout.activeWidth()/2,y=layout.centerY()-h/2;
        var matrices=context.getMatrices();matrices.push();matrices.translate(x,y+h/2.0,0);
        float scale=(float)(.96+.04*BattleMotion.out(elapsed/.26));matrices.scale(scale,scale,1);
        int tint=won?BattleSceneEffects.GOLD:BattleSceneEffects.CYAN;
        BattleEffects.shadow(context,0,h/2+3*k,w,h,a*.8);
        BattleUi.round(context,-w/2,-h/2,w,h,9*k,BattleEffects.alpha(0xFFF5FCFF,a*.97));
        context.fill(-w/2+10*k,h/2-4*k,w/2-10*k,h/2-3*k,BattleEffects.alpha(tint,a));
        BattleUi.centeredText(context,textRenderer,label,0,-5*k,BattleEffects.alpha(0xFF26394D,a),false,k);
        matrices.pop();
    }
    private void drawFeedback(DrawContext context,PtcgLiveTableLayout layout) {
        long now=frameNow;
        animations.removeIf(a->animationExpired(a,now));
        drawnCards.keySet().retainAll(animations);
        if(currentOverlay()!=null||cardPanel!=null||popup!=null||pileView!=null||menuOpen){drawCompactFeedback(context,layout);return;}
        double intro=(now-introStarted)/1e9;
        if(introStarted!=0&&intro<1.2&&animations.stream().noneMatch(a->a.cue().kind()==MatchPresentation.Kind.TURN))
            drawOpeningVersus(context,layout,intro);
        int k=layout.ui();
        var positions=boardPlacements();
        // Departing targets belong underneath every hit layer, even when their
        // leave event is later in the same already-confirmed event batch.
        for(var animation:animations)if(!tableMode()&&now<animation.started()&&animation.cue().kind()==MatchPresentation.Kind.LEAVE){
            var old=previousCueTarget(animation);
            if(old!=null)flyingCard(context,animation.cue().cardId(),midX(old),midY(old),old.width(),old.height(),CARD_BORDER);
        }
        for (var animation:animations) {
            var cue=animation.cue();
            if(now<animation.started())continue;
            double elapsed=animationElapsed(animation,now);
            if(cue.kind()==MatchPresentation.Kind.RESULT&&snapshot.game().result()!=null)continue;
            if(cue.kind()==MatchPresentation.Kind.TURN || cue.kind()==MatchPresentation.Kind.RESULT || cue.kind()==MatchPresentation.Kind.CHECKUP) {
                String text=cue.kind()==MatchPresentation.Kind.TURN
                    ? Objects.equals(cue.seat(),snapshot.game().viewingSeat())?"你的回合":"对手的回合"
                    : cue.kind()==MatchPresentation.Kind.RESULT
                    ? cue.seat()==null?"平局":snapshot.game().viewingSeat()==null?"对局结束":Objects.equals(cue.seat(),snapshot.game().viewingSeat())?"胜利":"失败"
                    : "宝可梦检查";
                if(cue.kind()==MatchPresentation.Kind.TURN&&snapshot.game().turnNumber()<=1)text="对战开始";
                drawSceneAnnouncement(context,layout,text,cue.kind(),elapsed,feedbackDuration(cue)/1e9);
                continue;
            }
            if (cue.kind()==MatchPresentation.Kind.DRAW) {
                drawDrawCue(context,layout,animation,elapsed);
                continue;
            }
            if (cue.kind()==MatchPresentation.Kind.MOVE || cue.kind()==MatchPresentation.Kind.LEAVE
                || cue.kind()==MatchPresentation.Kind.DISCARD || cue.kind()==MatchPresentation.Kind.LOST
                || cue.kind()==MatchPresentation.Kind.SHUFFLE) {
                drawZoneCue(context,layout,animation,positions,elapsed);
                continue;
            }
            if (cue.kind()==MatchPresentation.Kind.TOOL || cue.kind()==MatchPresentation.Kind.ENERGY) {
                drawAttachmentCue(context,layout,animation,positions,elapsed);
                continue;
            }
            if (cue.kind()==MatchPresentation.Kind.STATUS) {
                drawStatusCue(context,layout,animation,positions,elapsed);
                continue;
            }
            if (cue.kind()==MatchPresentation.Kind.PLAY || cue.kind()==MatchPresentation.Kind.EVOLVE || cue.kind()==MatchPresentation.Kind.DEVOLVE
                || cue.kind()==MatchPresentation.Kind.STADIUM) {
                drawPublicCardCue(context,layout,animation,positions,elapsed);
                continue;
            }
            if (cue.kind()==MatchPresentation.Kind.COIN) {
                drawCoinCue(context,layout,animation,elapsed);
                continue;
            }
            if (cue.kind()==MatchPresentation.Kind.PRIZE) {
                drawPrizeCue(context,layout,cue,elapsed);
                continue;
            }
            if (cue.kind()==MatchPresentation.Kind.ATTACK || cue.kind()==MatchPresentation.Kind.ABILITY
                || cue.kind()==MatchPresentation.Kind.KNOCKOUT) {
                // Settlement marker; each confirmed departing stack has its own visual cue.
                if(cue.kind()==MatchPresentation.Kind.KNOCKOUT)continue;
                if(cue.kind()==MatchPresentation.Kind.ATTACK) {
                    drawAttackIntroduction(context,layout,animation,(now-animation.started())/1e9);
                    drawAttackRibbon(context,layout,cue.text(),animation.type(),elapsed,feedbackDuration(cue)/1e9);
                    continue;
                }
                drawAbilityCue(context,layout,animation,positions,elapsed*1000);
                continue;
            }
            Rect target=cueTarget(animation,positions);
            if(target==null)continue;
            int color=cue.kind()==MatchPresentation.Kind.DAMAGE?0xFFFF7060:cue.kind()==MatchPresentation.Kind.HEAL?0xFF72EDAD:ACCENT;
            int y=Math.max(0,midY(target)-(int)(BattleMotion.out(elapsed/1.1)*19*k));
            int offset=cue.kind()==MatchPresentation.Kind.ENERGY?10*k:cue.kind()==MatchPresentation.Kind.STATUS?20*k:0;
            String label=cue.kind()==MatchPresentation.Kind.DAMAGE?Integer.toString(cue.amount()):cue.text();
            if(cue.kind()==MatchPresentation.Kind.DAMAGE) {
                if(cue.text().startsWith("招式伤害·")) {
                    var definition=attackAnimation(animation);
                    // Electric reference geometry is relative to the target card, not its damage numeral.
                    int size=animation.type()==EnergyType.LIGHTNING||usesCardSizedHit(animation.type())?target.width()
                        :(int)(target.width()*Math.clamp(.8+cue.amount()/400.0,.85,1.4));
                    if(animationQuality!=BattleAnimationEngine.Quality.OFF) {
                        if(definition!=null&&animation.type()==EnergyType.LIGHTNING) {
                            double ms=(now-animation.started())/1e6;
                            if(ms>=0&&ms<definition.electric().durationMs()) {
                                var state=electricEffects.computeIfAbsent(animation,a->electricPool.acquire(definition.electric(),Objects.hash(a.cue().kind().name(),a.cue().seat(),a.cue().pokemon(),a.cue().amount(),a.cue().text(),a.cue().cardId())));
                                state.sample(ms);
                                var mesh=BattleEffects.mesh(context);
                                for(int layer=1;layer<=6;layer++) {
                                    if(layer==4&&animationQuality==BattleAnimationEngine.Quality.HIGH){
                                        boolean drawn=BattleGlowShader.draw(context,midX(target),midY(target),size*1.4,state,ms);
                                        mesh=BattleEffects.mesh(context);
                                        if(drawn)continue;
                                    }
                                    LightningAnimationRenderer.layer(mesh,state,midX(target),midY(target),size,ms,animationQuality,layer);
                                }
                            }
                        } else if((animation.type()==EnergyType.FIRE||animation.type()==EnergyType.WATER)&&definition.timeline().hasTrack("burst.shell")) {
                            ElementalImpactRenderer.draw(context,definition,animation.type(),midX(target),midY(target),size,(now-animation.started())/1e9,animationQuality);
                        } else BattleEffects.attackImpact(context,midX(target),midY(target),size,elapsed,animation.type(),animationQuality);
                    }
                }
                else if(!checkupDamage(cue)&&animationQuality!=BattleAnimationEngine.Quality.OFF)
                    BattleEffects.counterImpact(context,midX(target),midY(target),target.width(),elapsed);
                color=0xFFFFFFFF;
            }
            if(cue.kind()==MatchPresentation.Kind.HEAL&&animationQuality!=BattleAnimationEngine.Quality.OFF)
                BattleSceneEffects.heal(context,midX(target),midY(target),target.width(),elapsed);
            if(cue.kind()==MatchPresentation.Kind.DAMAGE||cue.kind()==MatchPresentation.Kind.HEAL)continue;
            BattleUi.centeredText(context,textRenderer,textRenderer.trimToWidth(label,Math.max(40,target.width()+24)),
                midX(target),Math.min(height-10*k,y+offset),color,true,k);
        }
        visibleNumberTargets.clear();
        for(int i=animations.size()-1;i>=0;i--)
            drawFeedbackNumber(context,layout,animations.get(i),positions,now);
    }
    private static boolean checkupDamage(MatchPresentation.Cue cue){
        return cue.kind()==MatchPresentation.Kind.DAMAGE&&(cue.text().equals("中毒伤害")||cue.text().equals("灼伤伤害"));
    }
    private static boolean usesCardSizedHit(EnergyType type){
        return type!=null&&switch(type){
            case COLORLESS, DARKNESS, FIGHTING, DRAGON, METAL, GRASS, PSYCHIC, FAIRY -> true;
            default -> false;
        };
    }
    private double damagePopupMs(AnimatedCue animation){
        var d=attackAnimation(animation);
        if(animation.type()==EnergyType.LIGHTNING)return d.electric().damagePopupMs();
        double delay=animation.type()==null?0:switch(animation.type()){
            case COLORLESS -> BattleEffects.COLORLESS_POPUP_MS;
            case DARKNESS, FIGHTING, METAL -> 150;
            case DRAGON -> 175;
            case GRASS -> 130;
            case PSYCHIC, FAIRY -> 230;
            default -> 0;
        };
        return d.timeline().sample("number.delayMs",0,delay);
    }
    private double hitHpProgress(AnimatedCue animation,long now){
        double ms=(now-animation.started())/1e6;
        if(ms<0)return 0;
        if(animation.cue().kind()==MatchPresentation.Kind.HEAL)
            return BattleMotion.smooth((animationElapsed(animation,now)-.52)/.29);
        if(checkupDamage(animation.cue()))return BattleMotion.smooth((animationElapsed(animation,now)-.55)/.25);
        if(!animation.cue().text().startsWith("招式伤害·"))return 1;
        var d=attackAnimation(animation);
        if(usesCardSizedHit(animation.type())&&!d.timeline().hasTrack("hp.startMs")&&!d.timeline().hasTrack("hp.endMs")){
            double start=switch(animation.type()){
                case GRASS -> 305;
                case METAL -> 395;
                case PSYCHIC, FAIRY -> 475;
                default -> 570;
            };
            return BattleMotion.smooth((ms-start)/230);
        }
        return animation.type()==EnergyType.LIGHTNING?d.electric().hpProgress(ms):BattleMotion.hpProgress(d.timeline(),ms);
    }
    private double hitNumberOpacity(AnimatedCue animation,long now){
        double ms=(now-animation.started())/1e6;
        return animation.type()==EnergyType.LIGHTNING?attackAnimation(animation).electric().numberOpacity(ms)
            :BattleMotion.numberOpacity((ms-damagePopupMs(animation))/1000);
    }
    private void drawFeedbackNumber(DrawContext context,PtcgLiveTableLayout layout,AnimatedCue animation,List<Placed> positions,long now){
        var cue=animation.cue();
        if(cue.kind()!=MatchPresentation.Kind.DAMAGE&&cue.kind()!=MatchPresentation.Kind.HEAL)return;
        if(now<animation.started()||visibleNumberTargets.contains(cue.pokemon()))return;
        Rect target=cueTarget(animation,positions);if(target==null)return;
        double elapsed=animationElapsed(animation,now);
        if(cue.kind()==MatchPresentation.Kind.DAMAGE&&cue.text().startsWith("招式伤害·"))elapsed=(now-animation.started())/1e9-damagePopupMs(animation)/1000;
        double rise=cue.kind()==MatchPresentation.Kind.DAMAGE&&cue.text().startsWith("招式伤害·")
            ?attackAnimation(animation).timeline().sample("number.rise",0,usesCardSizedHit(animation.type())?0:10):10;
        boolean heal=cue.kind()==MatchPresentation.Kind.HEAL;
        int k=layout.ui(),y=midY(target)-(int)(BattleMotion.out(Math.max(0,elapsed)/1.1)*(heal?0:rise)*k);
        boolean electric=cue.kind()==MatchPresentation.Kind.DAMAGE&&animation.type()==EnergyType.LIGHTNING&&cue.text().startsWith("招式伤害·");
        boolean elemental=cue.kind()==MatchPresentation.Kind.DAMAGE&&(animation.type()==EnergyType.FIRE||animation.type()==EnergyType.WATER
            ||usesCardSizedHit(animation.type()))&&cue.text().startsWith("招式伤害·");
        double hitMs=(now-animation.started())/1e6;
        double opacity=electric?attackAnimation(animation).electric().numberOpacity(hitMs):BattleMotion.numberOpacity(elapsed);if(opacity<4/255.0)return;
        visibleNumberTargets.add(cue.pokemon());
        var matrices=context.getMatrices();matrices.push();matrices.translate(midX(target),Math.min(height-10*k,y),0);
        float scale=(float)(electric?attackAnimation(animation).electric().numberScale(hitMs)*1.2:BattleMotion.numberScale(elapsed));
        boolean checkup=checkupDamage(cue);
        float cardScale=elemental||checkup||heal?target.width()/50f:k;
        var number=Text.literal((cue.kind()==MatchPresentation.Kind.HEAL?"+":"")+cue.amount())
            .styled(style->style.withFont(net.minecraft.util.Identifier.of("ptcglive_mc","battle_numbers")));
        int numberWidth=textRenderer.getWidth(number);
        float fittedScale=Math.min(scale*cardScale,target.width()/(float)(numberWidth+3));
        matrices.scale(fittedScale,fittedScale,1);
        int left=-numberWidth/2,numberY=electric||elemental||checkup||heal?-textRenderer.fontHeight/2:0,outline=BattleEffects.alpha(electric?0xFF494738:0xFF233747,opacity);
        if(checkup) {
            boolean poison=cue.text().equals("中毒伤害");
            String label=poison?"中毒":"灼伤";
            int half=textRenderer.getWidth(label)/2+3,top=numberY-textRenderer.fontHeight-3;
            BattleUi.round(context,-half,top,half*2,numberY-top-1,3,
                BattleEffects.alpha(poison?0xFF904DB1:0xFFBE4431,opacity));
            BattleUi.centeredText(context,textRenderer,label,0,top+1,BattleEffects.alpha(0xFFFFFFFF,opacity),false,1);
        }
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)if(dx!=0||dy!=0)context.drawText(textRenderer,number,left+dx,numberY+dy,outline,false);
        // Keep the fill in front of every overlapping outline glyph in the text batch.
        matrices.translate(0,0,.1f);
        context.drawText(textRenderer,number,left,numberY,BattleEffects.alpha(cue.kind()==MatchPresentation.Kind.HEAL?0xFF72EDAD:0xFFFFFFFF,opacity),false);
        matrices.pop();
    }
    private void recycleElectricEffects(long now){
        var iterator=electricEffects.entrySet().iterator();
        while(iterator.hasNext()){
            var e=iterator.next();
            if(!animations.contains(e.getKey())||(now-e.getKey().started())/1e6>=e.getValue().profile.durationMs()){
                electricPool.release(e.getValue());iterator.remove();
            }
        }
    }
    private void clearElectricEffects(){for(var state:electricEffects.values())electricPool.release(state);electricEffects.clear();}
    private void drawPrizeCue(DrawContext context,PtcgLiveTableLayout layout,MatchPresentation.Cue cue,double elapsed) {
        if(animationQuality==BattleAnimationEngine.Quality.OFF)return;
        if(Objects.equals(cue.seat(),snapshot.game().viewingSeat())&&cue.amount()==1) {
            double ms=elapsed*1000;if(ms<0||ms>=PrizeCardAnimation.END_MS)return;
            var center=PrizeCardAnimation.reveal(width,height);
            var from=selectedPrizeSlot>=0?PrizeCardAnimation.choice(width,height,selectedPrizeSlot)
                :tableRect(cardRect(layout,layout.prizeX(),false));
            var hand=centeredRect(handAnchor(layout,cue.seat()),layout.benchWidth(),layout.benchHeight());
            context.fill(0,0,width,height,BattleEffects.alpha(0xFF050916,PrizeCardAnimation.dim(ms)));
            double ghost=PrizeCardAnimation.dim(ms)*.52;
            for(int slot=0;slot<6;slot++)if(slot==selectedPrizeSlot||snapshot.game().prizeSlots().contains(slot)){
                var r=PrizeCardAnimation.choice(width,height,slot);
                CardPainter.roundRect(context,r.x(),r.y(),r.width(),r.height(),BattleEffects.alpha(0xFF8390AF,ghost*.28));
                CardPainter.roundBorder(context,r.x(),r.y(),r.width(),r.height(),BattleEffects.alpha(0xFFCCCAE2,ghost));
                context.setShaderColor(1,1,1,(float)ghost);
                try{BattleCosmetics.draw(context,BattleCosmetics.cardBack(),r.x(),r.y(),r.width(),r.height());}
                finally{context.setShaderColor(1,1,1,1);}
            }
            var pose=PrizeCardAnimation.pose(from,center,hand,ms);
            PrizeCardAnimation.mesh(BattleEffects.mesh(context),new Rect(center.x(),center.y()-(int)(center.height()*.23),center.width(),center.height()),ms,animationQuality);
            if(cue.cardId()==null||!EvolutionCardRenderer.draw(context,artwork.get(cue.cardId()),pose))
                flyingCard(context,null,(int)pose.x(),(int)pose.y(),pose.drawnWidth(),(int)pose.height(),ACCENT);
            if(ms<PrizeCardAnimation.HOLD_MS){
                var own=player(cue.seat());drawPrizeBar(context,layout,"已拿取奖赏卡 · 剩余 "+(own==null?0:own.prizeCount())+" 张");
            }
            return;
        }
        Rect pile=tableRect(cardRect(layout,layout.prizeX(),Objects.equals(cue.seat(),topSeat())));
        Rect hand=centeredRect(handAnchor(layout,cue.seat()),layout.benchWidth()*2/3,layout.benchHeight()*2/3);
        if(Objects.equals(cue.seat(),topSeat()))hand=new Rect(hand.x(),-hand.height(),hand.width(),hand.height());
        int count=Math.min(6,Math.max(1,cue.amount()));
        for(int i=0;i<count;i++) {
            double t=elapsed-i*.09;if(t<0||t>=.267)continue;
            double p=BattleMotion.out(t/.267);int x=between(midX(pile),midX(hand),p),y=between(midY(pile),midY(hand),p);
            int w=between(pile.width(),hand.width(),p),h=between(pile.height(),hand.height(),p);
            var m=context.getMatrices();m.push();m.translate(x,y,0);m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees((float)(-45*Math.sin(Math.PI*p))));
            // An opponent's confirmed prize stays face down, with no reveal flash.
            flyingCard(context,null,0,0,w,h,ACCENT);m.pop();
        }
    }
    /** Modal controls own the middle of the table; confirmed cues move to the unused right rail. */
    private void drawCompactFeedback(DrawContext context,PtcgLiveTableLayout layout) {
        long now=frameNow;
        animations.removeIf(a->animationExpired(a,now));
        int k=layout.ui(),x=layout.rightColumnX(),w=layout.rightColumnWidth();
        int top=layout.switchButton().bottom()+3*k;
        if(menuOpen) {
            top=Math.max(top,menuTop(layout)+11*(layout.buttonHeight()+3*k));
        }
        int bottom=layout.endTurn().y()-15*k;
        if(bottom-top<14*k) {top=Math.max(top,layout.endTurn().bottom()+16*k);bottom=layout.screenHeight()-3*k;}
        int rowHeight=14*k,capacity=Math.max(0,(bottom-top)/rowHeight);
        var labels=new ArrayList<String>();
        for(var animation:animations) {
            if(now<animation.started()||animation.cue().kind()==MatchPresentation.Kind.COIN&&animation.coinIndex()>0)continue;
            labels.add(compactLabel(animation,now));
        }
        int shown=Math.min(capacity,labels.size());
        for(int i=0;i<shown;i++) {
            int y=top+i*rowHeight;
            CardPainter.roundRect(context,x,y,w,rowHeight-2*k,0xEEF5FCFF);
            CardPainter.roundBorder(context,x,y,w,rowHeight-2*k,BattleSceneEffects.CYAN);
            String label=i==shown-1&&labels.size()>shown?"另有 "+(labels.size()-shown+1)+" 项":labels.get(i);
            BattleUi.centeredText(context,textRenderer,textRenderer.trimToWidth(label,Math.max(8,w/k-6)),
                x+w/2,y+2*k,0xFF26394D,false,k);
        }
    }
    private String compactLabel(AnimatedCue animation,long now) {
        var cue=animation.cue();
        return switch(cue.kind()) {
            case COIN -> {
                double ms=animationElapsed(animation,now)*1000;
                if(animation.coinCount()==1)yield !CoinAnimation.landed(ms,0)?"掷硬币":cue.text().endsWith("正面")?"硬币 正面":cue.text().endsWith("反面")?"硬币 反面":"硬币结果";
                long done=animations.stream().filter(a->a.started()==animation.started()&&a.batch()==animation.batch()
                    &&a.cue().kind()==MatchPresentation.Kind.COIN&&CoinAnimation.landed(ms,a.coinIndex())).count();
                yield "掷币 · 已完成 "+done;
            }
            case DRAW -> "抽牌 +"+cue.amount();
            case PLAY -> cue.text().equals("能量附着")?"附能":cue.pokemon()==null?"训练家出牌":"宝可梦登场";
            case EVOLVE -> "进化";
            case DEVOLVE -> "退化";
            case STADIUM -> cue.cardId()==null?"场地离场":"场地更新";
            case MOVE -> cue.text();
            case LEAVE -> cue.text();
            case TOOL, ENERGY -> cue.text().split("·",2)[0];
            case STATUS -> cue.text().replace('·',' ');
            case DISCARD -> "进入弃牌区";
            case LOST -> "进入放逐区";
            case SHUFFLE -> "洗牌";
            case ATTACK -> "招式发动";
            case ABILITY -> "特性发动";
            case DAMAGE -> checkupDamage(cue)?cue.text()+" -"+cue.amount():
                cue.text().startsWith("招式伤害·")&&now-animation.started()<damagePopupMs(animation)*1e6?"招式命中":"伤害 -"+cue.amount();
            case HEAL -> "回复 +"+cue.amount();
            case PRIZE -> "拿取奖赏卡 ×"+cue.amount();
            default -> cue.text();
        };
    }
    private int pendingDealCards(UUID seat,boolean hand){
        int pending=0;
        for(var a:animations){
            var cue=a.cue();
            if(!DrawCardAnimation.deal(cue)||!Objects.equals(cue.seat(),seat))continue;
            double elapsed=(frameNow-a.started())/1e6;
            for(int i=0;i<cue.amount();i++)
                if(elapsed<DrawCardAnimation.launchMs(cue,false,i)+(hand?DrawCardAnimation.HIDDEN_MS:0))pending++;
        }
        return pending;
    }
    private Rect drawStage(){
        int h=(int)Math.min(height*.47,width*.75/CARD_ASPECT),w=(int)(h*CARD_ASPECT);
        return new Rect((width-w)/2,(height-h)/2,w,h);
    }
    private Rect drawnHandTarget(PtcgLiveTableLayout layout,AnimatedCue animation){
        return drawnHandTarget(layout,animation,0);
    }
    private Rect drawnHandTarget(PtcgLiveTableLayout layout,AnimatedCue animation,int index){
        var revealed=drawnCards.getOrDefault(animation,List.of());
        var card=index<revealed.size()?revealed.get(index):null;
        if(card!=null){
            var cards=zone(snapshot.game().viewingSeat(),":hand").cards();
            var future=revealed.subList(index+1,revealed.size());
            if(!future.isEmpty())cards=cards.stream().filter(c->future.stream().noneMatch(f->c.key().equals("hand:"+f.instanceId()))).toList();
            int offset=Math.clamp(handOffset,0,Math.max(0,cards.size()-10)),size=Math.min(10,cards.size()-offset);
            for(int i=0;i<size;i++)if(cards.get(offset+i).key().equals("hand:"+card.instanceId()))
                return new Rect(layout.handRow(size).x(i),layout.handY(),layout.activeWidth(),layout.activeHeight());
        }
        return centeredRect(handAnchor(layout,animation.cue().seat()),layout.benchWidth()*2/3,layout.benchHeight()*2/3);
    }
    private void drawDrawCue(DrawContext context,PtcgLiveTableLayout layout,AnimatedCue animation,double elapsed) {
        var cue=animation.cue();
        if(DrawCardAnimation.draw(cue)){
            boolean own=Objects.equals(cue.seat(),snapshot.game().viewingSeat());
            var revealed=drawnCards.getOrDefault(animation,List.of());
            for(int i=0;i<cue.amount();i++){
            boolean revealAtCenter=own&&!DrawCardAnimation.deal(cue);
            double ms=elapsed*1000-DrawCardAnimation.launchMs(cue,own,i);
            if(ms<0||ms>DrawCardAnimation.duration(revealAtCenter)/1e6)continue;
            var card=i<revealed.size()?revealed.get(i):null;
            var deck=pileAnchor(layout,cue.seat(),MatchPresentation.Kind.SHUFFLE);
            var target=drawnHandTarget(layout,animation,i);
            if(!own&&DrawCardAnimation.deal(cue)){
                int step=Math.min(target.width()/2,width/Math.max(1,cue.amount()*2));
                int x=target.x()+(2*i+1-cue.amount())*step/2;
                target=new Rect(x,Math.max(target.y(),deck.height()/3),target.width(),target.height());
            }
            var pose=DrawCardAnimation.pose(deck,drawStage(),target,revealAtCenter,ms);
            if(!own&&DrawCardAnimation.deal(cue)){
                double roll=Math.toRadians(pose.tilt());
                double halfHeight=(Math.abs(Math.sin(roll))*pose.width()+Math.abs(Math.cos(roll))*pose.height())*.5;
                double safeY=Math.max(pose.y(),halfHeight+2*layout.ui());
                if(safeY>pose.y())pose=new EvolutionAnimation.Pose(pose.x(),safeY,pose.width(),pose.height(),
                    pose.flip(),pose.tilt(),pose.yaw(),pose.pitch());
            }
            // A missing/ambiguous private identity stays a back, even during the center reveal.
            if(card==null)pose=new EvolutionAnimation.Pose(pose.x(),pose.y(),pose.width(),pose.height(),0,pose.tilt(),pose.yaw(),pose.pitch());
            if(!EvolutionCardRenderer.drawTrainer(context,card==null?null:artwork.get(card.definitionId()),pose,pose.height()*3))
                tiltedCard(context,card!=null&&pose.front()?card.definitionId():null,(int)pose.x(),(int)pose.y(),pose.drawnWidth(),(int)pose.height(),CARD_BORDER,pose.tilt());
            }
            return;
        }
        boolean opponent=Objects.equals(cue.seat(),topSeat());
        int k=layout.ui(),count=Math.min(8,Math.max(1,cue.amount()));
        var deck=pileAnchor(layout,cue.seat(),MatchPresentation.Kind.SHUFFLE);
        int fromX=midX(deck),fromY=midY(deck);
        var hand=handAnchor(layout,cue.seat());
        int w=Math.max(10,layout.benchWidth()*2/3),h=Math.max(15,layout.benchHeight()*2/3);
        for(int i=0;i<count;i++) {
            double time=elapsed-BattleMotion.stagger(count,i),p=unit(time/.45);
            if(time<0||p>=1)continue;
            double eased=BattleMotion.out(p);
            int x=between(fromX,midX(hand)+(i-count/2)*3*k,eased);
            int y=between(fromY,midY(hand),eased)-(int)(Math.sin(Math.PI*p)*layout.benchHeight()*.35);
            y=Math.clamp(y,h/2+2,Math.max(h/2+2,height-h/2-2));
            // The draw cue never carries a visible card identity, including for the opponent.
            BattleSceneEffects.glint(context,x-w*.4,y+h*.15,w*.12*(1-p),BattleEffects.alpha(BattleSceneEffects.WHITE,Math.sin(Math.PI*p)*.7));
            tiltedCard(context,null,x,y,w,h,CARD_BORDER,(opponent?-1:1)*Math.sin(Math.PI*p)*9);
        }
        BattleSceneEffects.landing(context,centeredRect(hand,w,h),elapsed-.43,BattleSceneEffects.CYAN);
        if(elapsed>.42)BattleUi.centeredText(context,textRenderer,"手牌 +"+cue.amount(),midX(hand),
            Math.max(0,Math.min(height-10*k,midY(hand)-13*k)),ACCENT,true,k);
    }
    private void drawPublicCardCue(DrawContext context,PtcgLiveTableLayout layout,AnimatedCue animation,
                                   List<Placed> positions,double elapsed) {
        var cue=animation.cue();
        if(cue.kind()==MatchPresentation.Kind.STADIUM){drawStadiumCue(context,layout,animation,elapsed*1000);return;}
        boolean trainerPlay=cue.kind()==MatchPresentation.Kind.PLAY&&cue.pokemon()==null;
        boolean fromHand=trainerPlay||cue.kind()==MatchPresentation.Kind.PLAY
            &&(cue.text().equals("从手牌上场")||cue.text().equals("能量附着"))
            ||cue.kind()==MatchPresentation.Kind.STADIUM&&cue.text().equals("从手牌放置场地");
        Rect target=cue.kind()==MatchPresentation.Kind.STADIUM?tableRect(layout.stadiumSlot())
            :trainerPlay?new Rect(layout.activeX()+layout.activeWidth()/2-layout.benchWidth()/2,
                layout.centerY()-layout.benchHeight()/2,layout.benchWidth(),layout.benchHeight())
            :cueTarget(animation,positions);
        if(target==null||cue.cardId()==null)return;
        if(cue.kind()==MatchPresentation.Kind.PLAY&&cue.text().equals("能量附着")){
            drawEnergyAttachment(context,layout,animation,handAnchor(layout,cue.seat()),target,elapsed);
            return;
        }
        if(cue.kind()==MatchPresentation.Kind.EVOLVE) {
            if(animationQuality==BattleAnimationEngine.Quality.OFF)return;
            var timeline=BattleAnimationEngine.evolution();double ms=elapsed*1000;
            if(ms>=timeline.impactMs){EvolutionAnimation.landing(BattleEffects.mesh(context),target,timeline,ms,animationQuality);return;}
            var stage=evolutionStage(layout);
            Rect origin=handAnchor(layout,cue.seat());
            if(tableMode()&&Objects.equals(cue.seat(),topSeat())){
                var far=tableRect(new Rect(layout.activeX(),layout.opponentBenchY(),layout.benchWidth(),layout.benchHeight()));
                origin=new Rect(far.x(),midY(far),1,1);
            }
            var pose=EvolutionAnimation.pose(timeline,origin,target,stage,ms);
            if(!tableMode()&&ms<timeline.impactMs&&animation.previousCard()!=null)
                flyingCard(context,animation.previousCard(),midX(target),midY(target),target.width(),target.height(),CARD_BORDER);
            EvolutionAnimation.behind(context,pose,timeline,ms,animationQuality,layout.handHintY());
            if(!EvolutionCardRenderer.draw(context,pose.front()?artwork.get(cue.cardId()):null,pose))
                tiltedCard(context,pose.front()?cue.cardId():null,(int)Math.round(pose.x()),(int)Math.round(pose.y()),pose.drawnWidth(),(int)Math.round(pose.height()),CARD_BORDER,pose.tilt());
            EvolutionAnimation.front(context,pose,target,timeline,ms,animationQuality);
            return;
        }
        if(cue.kind()==MatchPresentation.Kind.DEVOLVE) {
            double ms=elapsed*1000;
            if(ms>=DevolutionAnimation.LAND_MS)return;
            var lifted=DevolutionAnimation.lifted(target,ms);
            String card=DevolutionAnimation.previous(ms)&&animation.previousCard()!=null?animation.previousCard():cue.cardId();
            var logical=tableMode()?logicalPlacements().stream().filter(p->p.card().key().equals("field:"+cue.pokemon())).findFirst().orElse(null):null;
            if(logical!=null)PocketTableRenderer.inspect(context,camera(),tablePose(logical,false),artwork.get(card),lifted,DevolutionAnimation.lift(ms)*.3);
            else flyingCard(context,card,midX(lifted),midY(lifted),lifted.width(),lifted.height(),CARD_BORDER);
            if(animationQuality!=BattleAnimationEngine.Quality.OFF)
                DevolutionAnimation.draw(BattleEffects.mesh(context),lifted,ms,animationQuality==BattleAnimationEngine.Quality.LOW);
            return;
        }
        if(trainerPlay) {
            if(animationQuality==BattleAnimationEngine.Quality.OFF)return;
            boolean opponent=Objects.equals(cue.seat(),topSeat());
            Rect hand=handAnchor(layout,cue.seat());
            int expandedH=(int)Math.min(height*.44,width*.72/CARD_ASPECT),expandedW=Math.round(expandedH*CARD_ASPECT);
            Rect center=new Rect(width/2-expandedW/2,height/2-expandedH/2,expandedW,expandedH);
            double ms=elapsed*1000;
            if(TrainerPlayAnimation.timeline(cue.cardId()).sample("card.visible",TrainerPlayAnimation.flashTime(cue.cardId(),ms,opponent),0)>.5){
                var pose=TrainerPlayAnimation.pose(hand,center,opponent,cue.cardId(),ms);
                double focal=TrainerPlayAnimation.focal(cue.cardId(),center,pose,ms);
                if(!EvolutionCardRenderer.drawTrainer(context,artwork.get(cue.cardId()),pose,focal))
                    tiltedCard(context,pose.front()?cue.cardId():null,(int)pose.x(),(int)pose.y(),pose.drawnWidth(),(int)pose.height(),CARD_BORDER,pose.tilt());
            }
            TrainerPlayAnimation.flash(context,center,cue.cardId(),ms,opponent,animationQuality);
            return;
        }
        if(PokemonEntryAnimation.applies(cue)){drawPokemonEntry(context,layout,animation,target,elapsed*1000);return;}
        double p=unit(elapsed/.70),eased=BattleMotion.out(p);
        // A field Pokémon may have come from hand, deck or discard. Only the
        // separate confirmed Trainer cue identifies a hand play and its seat.
        Rect start=fromHand?handAnchor(layout,cue.seat()):cue.kind()==MatchPresentation.Kind.STADIUM
            ?new Rect(Math.min(width-layout.benchWidth()/2,layout.stadiumX()+layout.benchWidth()*2),layout.centerY(),1,1)
            :new Rect(layout.activeX()+layout.activeWidth()/2,layout.centerY(),1,1);
        int x=between(midX(start),midX(target),eased),y=between(midY(start),midY(target),eased)
            -(int)(Math.sin(Math.PI*p)*layout.benchHeight()*.22);
        int w=Math.max(1,between(target.width()*2/3,target.width(),eased));
        int h=Math.max(1,between(target.height()*2/3,target.height(),eased));
        y-=(int)(BattleMotion.landing(p)*3*layout.ui());
        tiltedCard(context,cue.cardId(),x,y,w,h,ACCENT,(1-eased)*(Objects.equals(cue.seat(),topSeat())?5:-5));
        if(elapsed>.48&&elapsed<1.10)BattleSceneEffects.landing(context,target,elapsed-.48,BattleSceneEffects.CYAN);
        if(cue.kind()==MatchPresentation.Kind.STADIUM)BattleSceneEffects.sweep(context,layout.arena(),elapsed,1.3,BattleSceneEffects.CYAN);
    }
    private void drawAbilityCue(DrawContext context,PtcgLiveTableLayout layout,AnimatedCue a,List<Placed> positions,double ms){
        var target=previousCueTarget(a);if(target==null)target=cueTarget(a,positions);
        var mon=publicPokemon(snapshot,a.cue().pokemon());
        String card=a.previousCard()!=null?a.previousCard():mon==null?null:mon.topCardId();
        if(target==null||card==null){
            AbilityAnimation.banner(context,textRenderer,new Rect(width/2-55,height/2,110,1),a.cue().text(),ms,width);
            return;
        }
        var focus=AbilityAnimation.focus(target,ms);
        if(animationQuality!=BattleAnimationEngine.Quality.OFF){
            context.fill(0,0,width,height,BattleEffects.alpha(0xFF172336,AbilityAnimation.opacity(ms)*.34));
            context.draw();
            // Keep the dimmed color, but do not let its full-screen depth hide the later HUD.
            com.mojang.blaze3d.systems.RenderSystem.clear(256,false);
        }
        AbilityAnimation.backlight(context,focus,ms,animationQuality);
        if(ms<AbilityAnimation.LAND_MS){
            if(tableMode()&&a.previousLogical()!=null)
                PocketTableRenderer.inspect(context,camera(),camera().card(a.previousLogical(),previousLayout(a)),artwork.get(card),focus,AbilityAnimation.lift(ms));
            else flyingCard(context,card,midX(focus),midY(focus),focus.width(),focus.height(),CARD_BORDER);
        }
        AbilityAnimation.foreground(context,focus,ms,animationQuality);
        AbilityAnimation.banner(context,textRenderer,focus,a.cue().text(),ms,width);
    }
    private void drawQueuedStadium(DrawContext context,PtcgLiveTableLayout layout){
        for(var a:animations)if(a.cue().kind()==MatchPresentation.Kind.STADIUM){
            if(animationElapsed(a,frameNow)<0&&a.stadium()!=null){
                var target=tableRect(StadiumAnimation.slot(layout));var card=a.stadium().card().definitionId();
                if(tableMode())PocketTableRenderer.inspect(context,camera(),camera().card(StadiumAnimation.slot(layout),layout),artwork.get(card),target,0);
                else flyingCard(context,card,midX(target),midY(target),target.width(),target.height(),CARD_BORDER);
            }
            return;
        }
    }
    private void drawStadiumCue(DrawContext context,PtcgLiveTableLayout layout,AnimatedCue a,double ms){
        var cue=a.cue();var target=tableRect(StadiumAnimation.slot(layout));
        boolean rejected=a.rejectedStadium()!=null;
        if(a.stadium()!=null&&ms<StadiumAnimation.exitEnd(cue,rejected)){
            var old=a.stadium().card();var parked=cue.cardId()==null||rejected?target:StadiumAnimation.parked(target,ms);
            double progress=StadiumAnimation.exit(cue,ms,rejected);
            var destination=pileAnchor(layout,old.ownerId(),a.stadium().lost()?MatchPresentation.Kind.LOST:MatchPresentation.Kind.DISCARD);
            var pose=StadiumAnimation.flight(parked,destination,progress);
            if(tableMode()&&progress==0){
                var origin=camera().card(StadiumAnimation.slot(layout),layout);
                double shift=cue.cardId()==null||rejected?0:-origin.width()*.8*StadiumAnimation.appear(ms);
                PocketTableRenderer.inspect(context,camera(),origin.move(shift,0,0),artwork.get(old.definitionId()),parked,0);
            }else flyingCard(context,old.definitionId(),(int)pose.x(),(int)pose.y(),(int)pose.width(),(int)pose.height(),CARD_BORDER);
            if(animationQuality!=BattleAnimationEngine.Quality.OFF&&progress>0&&progress<.5)
                BattleSceneEffects.landing(context,parked,(ms-StadiumAnimation.exitStart(cue,rejected))/1000,BattleSceneEffects.CYAN);
        }
        if(cue.cardId()==null)return;
        boolean opponent=Objects.equals(cue.seat(),topSeat());
        int h=(int)Math.min(height*.44,width*.72/CARD_ASPECT),w=Math.round(h*CARD_ASPECT);
        var center=new Rect(width/2-w/2,height/2-h/2,w,h);
        if(ms<StadiumAnimation.APPEAR_MS){
            if(TrainerPlayAnimation.timeline(cue.cardId()).sample("card.visible",TrainerPlayAnimation.flashTime(cue.cardId(),ms,opponent),0)>.5){
                var pose=TrainerPlayAnimation.pose(handAnchor(layout,cue.seat()),center,opponent,cue.cardId(),ms);
                if(!EvolutionCardRenderer.drawTrainer(context,artwork.get(cue.cardId()),pose,TrainerPlayAnimation.focal(cue.cardId(),center,pose,ms)))
                    tiltedCard(context,pose.front()?cue.cardId():null,(int)pose.x(),(int)pose.y(),pose.drawnWidth(),(int)pose.height(),CARD_BORDER,pose.tilt());
            }
            TrainerPlayAnimation.flash(context,center,cue.cardId(),ms,opponent,animationQuality);
        }else if(rejected){
            if(ms<StadiumAnimation.REJECTED_LAND_MS){
                var card=a.rejectedStadium().card();
                var destination=pileAnchor(layout,card.ownerId(),a.rejectedStadium().lost()?MatchPresentation.Kind.LOST:MatchPresentation.Kind.DISCARD);
                var pose=StadiumAnimation.flight(center,destination,StadiumAnimation.rejectedFlight(ms));
                flyingCard(context,card.definitionId(),(int)pose.x(),(int)pose.y(),(int)pose.width(),(int)pose.height(),CARD_BORDER);
            }
        }else{
            double p=StadiumAnimation.appear(ms),scale=.18+.82*p+.13*Math.sin(Math.PI*p);
            if(tableMode())PocketTableRenderer.inspect(context,camera(),camera().card(StadiumAnimation.slot(layout),layout).scale(scale),artwork.get(cue.cardId()),target,0);
            else flyingCard(context,cue.cardId(),midX(target),midY(target),(int)(target.width()*scale),(int)(target.height()*scale),CARD_BORDER);
        }
        if(!rejected&&animationQuality!=BattleAnimationEngine.Quality.OFF&&ms>=StadiumAnimation.APPEAR_MS&&ms<2500)
            BattleSceneEffects.landing(context,target,(ms-StadiumAnimation.APPEAR_MS)/1000,0xFF72F838);
    }
    private boolean entryReveal(MatchPresentation.Cue cue){
        return PokemonEntryAnimation.reveal(cue,Objects.equals(cue.seat(),topSeat()));
    }
    private void drawPokemonEntry(DrawContext context,PtcgLiveTableLayout layout,AnimatedCue animation,Rect target,double ms){
        var cue=animation.cue();boolean reveal=entryReveal(cue);double contact=PokemonEntryAnimation.contact(reveal);
        if(ms>=contact){
            if(animationQuality!=BattleAnimationEngine.Quality.OFF&&ms<contact+PokemonEntryAnimation.TAIL_MS)
                BattleSceneEffects.landing(context,target,(ms-contact)/1000*.58/(PokemonEntryAnimation.TAIL_MS/1000),0xFFF5DA);
            return;
        }
        if(PokemonEntryAnimation.setupReveal(cue)){
            double flip=PokemonEntryAnimation.landing(false,ms);
            var pose=new EvolutionAnimation.Pose(midX(target),midY(target)-Math.sin(Math.PI*flip)*target.height()*.08,
                target.width(),target.height(),flip,0,0,0);
            if(!EvolutionCardRenderer.drawTrainer(context,artwork.get(cue.cardId()),pose,target.height()*4.5))
                tiltedCard(context,pose.front()?cue.cardId():null,(int)pose.x(),(int)pose.y(),pose.drawnWidth(),target.height(),CARD_BORDER,0);
            return;
        }
        var hand=handAnchor(layout,cue.seat());
        Rect origin=cue.text().equals("从手牌上场")?centeredRect(hand,target.width(),target.height())
            :new Rect(target.x(),target.y()-target.height()/3,target.width(),target.height());
        if(reveal){
            origin=PokemonEntryAnimation.lifted(target);
            if(ms<1200){
                int h=(int)Math.min(height*.48,width*.72/CARD_ASPECT),w=Math.round(h*CARD_ASPECT);
                var stage=new Rect(width/2-w/2,height/2-h/2,w,h);
                var pose=PokemonEntryAnimation.revealPose(hand,stage,origin,ms);
                if(!EvolutionCardRenderer.drawTrainer(context,artwork.get(cue.cardId()),pose,h*4.5))
                    tiltedCard(context,pose.front()?cue.cardId():null,(int)pose.x(),(int)pose.y(),pose.drawnWidth(),(int)pose.height(),CARD_BORDER,pose.tilt());
                return;
            }
        }
        var logical=tableMode()?logicalPlacements().stream().filter(p->p.card().key().equals("field:"+cue.pokemon())).findFirst().orElse(null):null;
        if(logical!=null)PocketTableRenderer.inspect(context,camera(),tablePose(logical,false),artwork.get(cue.cardId()),origin,1-PokemonEntryAnimation.landing(reveal,ms));
        else {
            var pose=PokemonEntryAnimation.flatPose(origin,target,reveal,ms);
            tiltedCard(context,cue.cardId(),(int)pose.x(),(int)pose.y(),(int)pose.width(),(int)pose.height(),CARD_BORDER,0);
        }
    }
    private Rect pileAnchor(PtcgLiveTableLayout layout,UUID seat,MatchPresentation.Kind kind) {
        boolean opponent=Objects.equals(seat,topSeat());
        int x=kind==MatchPresentation.Kind.LOST?layout.lostX()
            :kind==MatchPresentation.Kind.SHUFFLE?layout.deckX():layout.discardX();
        return tableRect(cardRect(layout,x,opponent));
    }
    private static Rect centeredRect(Rect around,int w,int h) {
        return new Rect(midX(around)-w/2,midY(around)-h/2,w,h);
    }
    private Rect centerAnchor(PtcgLiveTableLayout layout) {
        if(tableMode()){var p=camera().project(new PocketTableCamera.Vec(0,0,0));return new Rect((int)p.x(),(int)p.y(),1,1);}
        return new Rect(layout.activeX()+layout.activeWidth()/2,layout.centerY(),1,1);
    }
    private void drawFlightCard(DrawContext context,String cardId,Rect from,Rect to,double elapsed,
                                double duration,int color,boolean concealAtEnd) {
        if(from==null||to==null)return;
        double p=unit(elapsed/duration);
        if(p>=1)return;
        double eased=BattleMotion.flight(elapsed,duration);
        int x=between(midX(from),midX(to),eased),y=between(midY(from),midY(to),eased)
            -(int)(Math.sin(Math.PI*p)*Math.min(16,Math.max(4,layout().benchHeight()/5)));
        int w=Math.max(6,between(Math.max(6,from.width()),Math.max(6,to.width()),eased));
        int h=Math.max(9,between(Math.max(9,from.height()),Math.max(9,to.height()),eased));
        if(concealAtEnd&&p>.55)BattleUi.cardBack(context,x-w/2,y-h/2,w,h);
        else tiltedCard(context,cardId,x,y,w,h,color,Math.sin(Math.PI*p)*(midX(to)<midX(from)?-6:6));
    }
    private void drawSwitchCue(DrawContext context,PtcgLiveTableLayout layout,AnimatedCue animation,List<Placed> positions,double ms){
        if(ms>=SwitchAnimation.LAND_MS)return;
        var cue=animation.cue();Rect from=previousCueTarget(animation),to=cueTarget(animation,positions);
        if(from==null||to==null)return;
        if(!SwitchAnimation.incoming(cue)){
            if(!tableMode()){
                var p=RetreatAnimation.flat(from,to,false,Objects.equals(cue.seat(),topSeat()),SwitchAnimation.outgoingTime(ms));
                flyingCard(context,cue.cardId(),(int)p.x(),(int)p.y(),(int)p.width(),(int)p.height(),CARD_BORDER);
            }
            return;
        }
        int h=(int)Math.min(height*.48,width*.72/CARD_ASPECT),w=Math.round(h*CARD_ASPECT);
        var stage=new Rect(midX(to)-w/2,(int)(height*.45)-h/2,w,h);
        if(ms<SwitchAnimation.RETURN_MS){
            if(animationQuality!=BattleAnimationEngine.Quality.OFF){
                if(SwitchAnimation.replacement(cue))SwitchAnimation.replacementGlow(BattleEffects.mesh(context),stage,ms,animationQuality==BattleAnimationEngine.Quality.LOW);
                else SwitchAnimation.glow(BattleEffects.mesh(context),stage,ms,animationQuality==BattleAnimationEngine.Quality.LOW);
            }
            var pose=SwitchAnimation.replacement(cue)?SwitchAnimation.replacementReveal(from,stage,ms):SwitchAnimation.reveal(from,stage,ms);
            if(!EvolutionCardRenderer.drawTrainer(context,artwork.get(cue.cardId()),pose,h*4.5))
                tiltedCard(context,pose.front()?cue.cardId():null,(int)pose.x(),(int)pose.y(),pose.drawnWidth(),(int)pose.height(),CARD_BORDER,pose.tilt());
        }else{
            var logical=tableMode()?logicalPlacements().stream().filter(p->p.card().key().equals("field:"+cue.pokemon())).findFirst().orElse(null):null;
            if(logical!=null){
                var origin=camera().card(rect(logical),layout);
                if(SwitchAnimation.replacement(cue))origin=origin.move(0,origin.height()*SwitchAnimation.replacementLift(ms),0);
                PocketTableRenderer.inspect(context,camera(),origin,artwork.get(cue.cardId()),stage,1-SwitchAnimation.landing(ms));
            }
            else{
                var p=SwitchAnimation.flatLanding(stage,to,ms);
                double lift=SwitchAnimation.replacement(cue)?to.height()*SwitchAnimation.replacementLift(ms):0;
                flyingCard(context,cue.cardId(),(int)p.x(),(int)(p.y()-lift),(int)p.width(),(int)p.height(),CARD_BORDER);
            }
        }
    }
    private Rect discardOrigin(PtcgLiveTableLayout layout,AnimatedCue animation){
        var from=animation.pileArrival()==null?null:animation.pileArrival().origin();
        if(from==null)return centeredRect(centerAnchor(layout),layout.benchWidth(),layout.benchHeight());
        return new Rect((int)Math.round(from.x()*width/(double)Math.max(1,animation.previousWidth())),
            (int)Math.round(from.y()*height/(double)Math.max(1,animation.previousHeight())),
            Math.max(1,(int)Math.round(from.width()*width/(double)Math.max(1,animation.previousWidth()))),
            Math.max(1,(int)Math.round(from.height()*height/(double)Math.max(1,animation.previousHeight()))));
    }
    private void drawDiscardCue(DrawContext context,PtcgLiveTableLayout layout,AnimatedCue animation,double ms){
        if(animationQuality==BattleAnimationEngine.Quality.OFF||ms>=DiscardAnimation.LAND_MS)return;
        var from=discardOrigin(layout,animation);var cue=animation.cue();boolean opponent=Objects.equals(cue.seat(),topSeat());
        var logical=cardRect(layout,layout.discardX(),opponent);var to=tableRect(logical);
        double p=DiscardAnimation.progress(ms);
        if(tableMode()){
            var pose=camera().card(logical,layout);if(opponent)pose=pose.angles(0,180);
            var raised=new Rect(from.x(),from.y()-(int)(Math.sin(Math.PI*p)*from.height()*.22),from.width(),from.height());
            PocketTableRenderer.inspect(context,camera(),pose,artwork.get(cue.cardId()),raised,1-p);
        }else{
            var r=DiscardAnimation.flat(from,to,ms);
            tiltedCard(context,cue.cardId(),midX(r),midY(r),r.width(),r.height(),CARD_BORDER,(opponent?180*p:0)+18*Math.sin(Math.PI*p));
        }
    }
    private void drawLostCue(DrawContext context,PtcgLiveTableLayout layout,AnimatedCue animation,double ms){
        if(animationQuality==BattleAnimationEngine.Quality.OFF)return;
        var cue=animation.cue();var destination=pileAnchor(layout,cue.seat(),MatchPresentation.Kind.LOST);
        ToolLostAnimation.mesh(BattleEffects.mesh(context),destination,ms,animationQuality);
        if(ms<ToolLostAnimation.LAND_MS){
            var pose=LostZoneAnimation.pose(discardOrigin(layout,animation),destination,ms);
            if(!EvolutionCardRenderer.drawTrainer(context,artwork.get(cue.cardId()),pose,destination.height()*4.5))
                flyingCard(context,cue.cardId(),(int)pose.x(),(int)pose.y(),pose.drawnWidth(),(int)pose.height(),CARD_BORDER);
        }
    }
    private void drawZoneCue(DrawContext context,PtcgLiveTableLayout layout,AnimatedCue animation,
                             List<Placed> positions,double elapsed) {
        var cue=animation.cue();
        if(cue.kind()==MatchPresentation.Kind.LOST){drawLostCue(context,layout,animation,elapsed*1000);return;}
        if(cue.kind()==MatchPresentation.Kind.DISCARD){drawDiscardCue(context,layout,animation,elapsed*1000);return;}
        if(cue.kind()==MatchPresentation.Kind.SHUFFLE)return; // Render the deck itself in its table plane.
        if(cue.kind()==MatchPresentation.Kind.MOVE) {
            if(SwitchAnimation.applies(cue)){
                drawSwitchCue(context,layout,animation,positions,elapsed*1000);return;
            }
            if(tableMode())return;
            Rect from=previousCueTarget(animation),to=cueTarget(animation,positions);
            if(RetreatAnimation.applies(cue)){
                if(from!=null&&to!=null&&elapsed*1000<RetreatAnimation.LAND_MS){
                    var p=RetreatAnimation.flat(from,to,RetreatAnimation.incoming(cue),Objects.equals(cue.seat(),topSeat()),elapsed*1000);
                    flyingCard(context,cue.cardId(),(int)p.x(),(int)p.y(),(int)p.width(),(int)p.height(),CARD_BORDER);
                }
                return;
            }
            if(from!=null&&to!=null&&!from.equals(to)) {
                BattleSceneEffects.trail(context,from,to,elapsed,.72,BattleSceneEffects.CYAN);
                BattleSceneEffects.landing(context,to,elapsed-.50,BattleSceneEffects.CYAN);
                drawFlightCard(context,cue.cardId(),from,to,elapsed,.72,ACCENT,false);
            }
            return;
        }
        Rect from=cue.kind()==MatchPresentation.Kind.LEAVE?previousCueTarget(animation):centerAnchor(layout);
        MatchPresentation.Kind destination=cue.kind();
        String route=cue.text();
        if(route.contains("放逐区"))destination=MatchPresentation.Kind.LOST;
        else if(route.contains("弃牌区"))destination=MatchPresentation.Kind.DISCARD;
        else if(route.contains("牌库"))destination=MatchPresentation.Kind.SHUFFLE;
        Rect to=route.endsWith("手牌")?handAnchor(layout,cue.seat())
            :destination==MatchPresentation.Kind.DISCARD||destination==MatchPresentation.Kind.LOST
                ||destination==MatchPresentation.Kind.SHUFFLE?pileAnchor(layout,cue.seat(),destination):null;
        if(from==null)return;
        if(cue.kind()==MatchPresentation.Kind.LEAVE&&route.startsWith("击倒")) {
            // The already-confirmed stack dissolves at its old position; no visual pile flight.
            var art=tableMode()?null:artwork.get(cue.cardId());
            if(!tableMode()&&art==null&&KnockoutAnimation.sample(elapsed*1000).cardAlpha()>.5&&animationQuality!=BattleAnimationEngine.Quality.OFF)
                flyingCard(context,cue.cardId(),midX(from),midY(from),from.width(),from.height(),CARD_BORDER);
            KnockoutAnimation.draw(context,art,from,elapsed*1000,animationQuality);
            return;
        }
        if(to==null) {
            double p=unit(elapsed/.72);
            if(p<.72) {
                int w=Math.max(6,(int)(from.width()*(1-p))),h=Math.max(9,(int)(from.height()*(1-p)));
                flyingCard(context,cue.cardId(),midX(from),midY(from)-(int)(p*18*layout.ui()),w,h,0xFFFF786C);
            }
            return;
        }
        boolean hiddenDestination=route.endsWith("手牌")||destination==MatchPresentation.Kind.SHUFFLE;
        BattleSceneEffects.trail(context,from,to,elapsed,.72,destination==MatchPresentation.Kind.LOST?0xFFE3B6FF:BattleSceneEffects.CYAN);
        drawFlightCard(context,cue.cardId(),from,to,elapsed,.72,
            destination==MatchPresentation.Kind.LOST?0xFFFF8CA8:ACCENT,hiddenDestination);
    }
    private void drawEnergyAttachment(DrawContext context,PtcgLiveTableLayout layout,AnimatedCue animation,Rect source,Rect target,double elapsed){
        var mon=publicPokemon(snapshot,animation.cue().pokemon());
        int count=mon==null?0:mon.attachedEnergyCardIds().size();
        int index=mon==null?0:Math.max(0,mon.attachedEnergyCardIds().lastIndexOf(animation.cue().cardId()));
        if(!tableMode()&&count>0)target=EnergyStackLayout.of(target.x(),target.y(),target.width(),target.height(),count).front();
        EnergyAttachmentAnimation.draw(context,source,target,layout.ui(),index,elapsed*1000,energyColor(name(animation.cue().cardId())),animationQuality);
    }
    private Rect evolutionStage(PtcgLiveTableLayout layout){
        var arena=layout.arena();
        var stage=new Rect(layout.activeX()+layout.activeWidth()/2-arena.width()/2,layout.opponentBenchY(),arena.width(),layout.ownBenchY()+layout.benchHeight()-layout.opponentBenchY());
        if(!tableMode())return stage;
        var projected=tableRect(stage);
        return new Rect(midX(projected)-stage.width()/2,midY(projected)-stage.height()/2,stage.width(),stage.height());
    }
    private static boolean isEnergyTransfer(MatchPresentation.Cue cue){
        return cue.kind()==MatchPresentation.Kind.ENERGY&&cue.text().startsWith("能量转移·");
    }
    private static boolean sameEnergyTransfer(MatchPresentation.Cue a,MatchPresentation.Cue b){
        return isEnergyTransfer(a)&&isEnergyTransfer(b)&&Objects.equals(a.seat(),b.seat())&&Objects.equals(a.pokemon(),b.pokemon())&&a.text().equals(b.text());
    }
    private static boolean isEnergyDiscard(MatchPresentation.Cue cue){
        return cue.kind()==MatchPresentation.Kind.ENERGY&&cue.amount()<0&&cue.text().equals("能量弃置");
    }
    private static boolean sameEnergyDiscard(MatchPresentation.Cue a,MatchPresentation.Cue b){
        return isEnergyDiscard(a)&&isEnergyDiscard(b)&&Objects.equals(a.seat(),b.seat())&&Objects.equals(a.pokemon(),b.pokemon());
    }
    private double energyContactMs(AnimatedCue animation){
        var cue=animation.cue();
        if(cue.kind()==MatchPresentation.Kind.PLAY&&cue.text().equals("能量附着"))return EnergyAttachmentAnimation.contactMs(true);
        return animation.energyTransfer()==null?0:EnergyTransferAnimation.contactMs();
    }
    private List<String> displayedEnergies(PokemonView pokemon){
        List<String> cards=pokemon.attachedEnergyCardIds();
        if(animations.isEmpty()||animationQuality==BattleAnimationEngine.Quality.OFF)return cards;
        var pending=new ArrayList<Map.Entry<Integer,String>>();
        for(var a:animations){
            double ms=animationElapsed(a,frameNow)*1000;
            if(a.energyTransfer()!=null&&a.energyTransfer().source().equals(pokemon.pokemonId())&&ms<EnergyTransferAnimation.LEAVE_MS)
                pending.add(Map.entry(a.energyTransfer().sourceIndex(),a.energyTransfer().cardId()));
            if(a.energyDiscard()!=null&&Objects.equals(a.cue().pokemon(),pokemon.pokemonId())&&ms<EnergyDiscardAnimation.REMOVE_MS)
                pending.add(Map.entry(a.energyDiscard().index(),a.energyDiscard().cardId()));
        }
        if(pending.isEmpty())return cards;
        var visible=new ArrayList<>(cards);
        pending.sort(Map.Entry.comparingByKey());
        for(var slot:pending)visible.add(Math.min(slot.getKey(),visible.size()),slot.getValue());
        return visible;
    }
    private boolean discardedEnergyHidden(UUID pokemon,int index,long now){
        if(animationQuality==BattleAnimationEngine.Quality.OFF)return false;
        for(var a:animations)if(a.energyDiscard()!=null&&Objects.equals(pokemon,a.cue().pokemon())&&index==a.energyDiscard().index()){
            double ms=animationElapsed(a,now)*1000;
            if(ms>=0&&ms<EnergyDiscardAnimation.REMOVE_MS)return true;
        }
        return false;
    }
    private void drawEnergyTransfer(DrawContext context,PtcgLiveTableLayout layout,AnimatedCue animation,List<Placed> positions,Rect target,double elapsed){
        var link=animation.energyTransfer();Rect source=null;
        for(var placed:positions)if(placed.card().key().equals("field:"+link.source())){source=rect(placed);break;}
        if(source==null&&link.previousBounds()!=null){
            var old=link.previousBounds();
            source=new Rect((int)Math.round(old.x()*(double)width/Math.max(1,animation.previousWidth())),(int)Math.round(old.y()*(double)height/Math.max(1,animation.previousHeight())),
                (int)Math.round(old.width()*(double)width/Math.max(1,animation.previousWidth())),(int)Math.round(old.height()*(double)height/Math.max(1,animation.previousHeight())));
        }
        var mon=publicPokemon(snapshot,animation.cue().pokemon());
        if(!tableMode()){
            if(source!=null)source=EnergyStackLayout.of(source.x(),source.y(),source.width(),source.height(),link.sourceCount()).front();
            if(mon!=null)target=EnergyStackLayout.of(target.x(),target.y(),target.width(),target.height(),mon.attachedEnergyCardIds().size()).front();
        }
        if(source==null){
            if(elapsed*1000>=EnergyTransferAnimation.contactMs())EnergyAttachmentAnimation.draw(context,null,target,layout.ui(),link.targetIndex(),elapsed*1000-EnergyTransferAnimation.contactMs(),energyColor(name(link.cardId())),animationQuality);
            return;
        }
        EnergyTransferAnimation.mesh(BattleEffects.mesh(context),EnergyAttachmentAnimation.icon(source,layout.ui(),link.sourceIndex()),
            EnergyAttachmentAnimation.icon(target,layout.ui(),link.targetIndex()),target.width(),elapsed*1000,animationQuality);
    }
    private void drawAttachmentCue(DrawContext context,PtcgLiveTableLayout layout,AnimatedCue animation,
                                   List<Placed> positions,double elapsed) {
        var cue=animation.cue();
        Rect pokemon=cueTarget(animation,positions);
        if(pokemon==null)return;
        boolean energy=cue.kind()==MatchPresentation.Kind.ENERGY;
        if(!energy&&(cue.text().endsWith("附着")||toolDiscard(animation)||isToolReturn(cue))&&!cue.text().contains("转移·")){
            if(isToolReturn(cue)){
                if(publicPokemon(snapshot,cue.pokemon())!=null)
                    ToolAttachmentAnimation.drawReturn(context,ToolAttachmentAnimation.icon(pokemon,animation.toolIndex()),elapsed*1000,animationQuality);
            }
            else ToolAttachmentAnimation.draw(context,ToolAttachmentAnimation.icon(pokemon,animation.toolIndex()),elapsed*1000,cue.amount()<0,animationQuality);
            double handMs=toolReturnHandMs(animation,elapsed);
            if(isToolReturn(cue)&&Objects.equals(toolReturnOwner(cue),topSeat())
                &&handMs>=ToolAttachmentAnimation.returnStart()&&handMs<ToolAttachmentAnimation.returnEnd()){
                // Opponent hand stays concealed. A back enters from its own screen edge, never from the host.
                var hand=handAnchor(layout,topSeat());double p=ToolAttachmentAnimation.returnProgress(handMs);
                int w=layout.benchWidth()*2/3,h=layout.benchHeight()*2/3;
                flyingCard(context,null,midX(hand),midY(hand)-(int)((1-p)*h),w,h,CARD_BORDER);
            }
            if(toolDiscard(animation)&&!tableMode()&&elapsed*1000>=ToolAttachmentAnimation.discardStart()
                &&elapsed*1000<ToolAttachmentAnimation.discardEnd()){
                var destinationSeat=toolPileSeat(cue);var pile=pileAnchor(layout,destinationSeat,MatchPresentation.Kind.DISCARD);
                double left=1-ToolAttachmentAnimation.discardProgress(elapsed*1000);
                tiltedCard(context,cue.cardId(),midX(pile),midY(pile)-(int)(pile.height()*1.5*left),
                    (int)(pile.width()*(1+.06*left)),(int)(pile.height()*(1+.06*left)),CARD_BORDER,(Objects.equals(destinationSeat,topSeat())?180:0)+ToolAttachmentAnimation.discardAngle(elapsed*1000));
            }
            return;
        }
        if(energy&&animation.energyDiscard()!=null){
            var slot=animation.energyDiscard();
            if(!tableMode())pokemon=EnergyStackLayout.of(pokemon.x(),pokemon.y(),pokemon.width(),pokemon.height(),slot.sourceCount()).front();
            EnergyDiscardAnimation.mesh(BattleEffects.mesh(context),EnergyAttachmentAnimation.icon(pokemon,layout.ui(),slot.index()),elapsed*1000,
                cue.pokemon().hashCode()+slot.index(),animationQuality);
            return;
        }
        if(energy&&animation.energyTransfer()!=null){drawEnergyTransfer(context,layout,animation,positions,pokemon,elapsed);return;}
        if(!energy&&cue.amount()<0){
            double ms=elapsed*1000;
            ToolAttachmentAnimation.draw(context,ToolAttachmentAnimation.icon(pokemon,animation.toolIndex()),ms,true,animationQuality);
            if(toolLost(animation)&&animationQuality!=BattleAnimationEngine.Quality.OFF){
                var destination=pileAnchor(layout,toolPileSeat(cue),MatchPresentation.Kind.LOST);
                ToolLostAnimation.mesh(BattleEffects.mesh(context),destination,ms,animationQuality);
                if(ms>=ToolLostAnimation.START_MS&&ms<ToolLostAnimation.LAND_MS){
                    var pose=ToolLostAnimation.pose(pokemon,destination,ms);
                    if(!EvolutionCardRenderer.drawTrainer(context,artwork.get(cue.cardId()),pose,destination.height()*4.5))
                        flyingCard(context,cue.cardId(),(int)pose.x(),(int)pose.y(),pose.drawnWidth(),(int)pose.height(),CARD_BORDER);
                }
            }
            return;
        }
        int w=Math.max(8,layout.benchWidth()/2),h=Math.max(11,layout.benchHeight()/2);
        Rect attached=centeredRect(pokemon,w,h);
        String action=cue.text();
        int color=energy?0xFFFFD86B:0xFF96D8FF;
        Rect from,to;
        if(action.contains("转移·")) {
            Rect source=null;
            try {
                String id=action.substring(action.indexOf('·')+1);
                for(var placed:positions)if(placed.card().key().equals("field:"+UUID.fromString(id))) {
                    source=new Rect(placed.x(),placed.y(),placed.width(),placed.height());break;
                }
            } catch(IllegalArgumentException ignored) { }
            // Without matching public energy instance IDs, do not invent a transfer flight.
            from=energy?null:source==null?centerAnchor(layout):centeredRect(source,w,h);
            to=attached;
        } else if(action.endsWith("附着")) {
            // An effect may attach from any zone; only a separate hand-play cue identifies a hand source.
            from=energy?null:centerAnchor(layout);to=attached;
        } else {
            from=attached;
            to=action.endsWith("弃置")?pileAnchor(layout,energy?cue.seat():toolPileSeat(cue),MatchPresentation.Kind.DISCARD)
                :action.endsWith("放逐")||action.startsWith("工具放逐·")?pileAnchor(layout,energy?cue.seat():toolPileSeat(cue),MatchPresentation.Kind.LOST):null;
            if(to!=null)to=centeredRect(to,w,h);
        }
        if(to!=null&&energy&&(action.endsWith("附着")||action.contains("转移·")))
            drawEnergyAttachment(context,layout,animation,from,pokemon,elapsed);
        else if(to!=null)drawFlightCard(context,cue.cardId(),from,to,elapsed,.67,color,false);
        else if(elapsed<.55) {
            double p=unit(elapsed/.55);
            int shrink=Math.max(6,(int)(w*(1-p)));
            flyingCard(context,cue.cardId(),midX(from),midY(from)-(int)(p*8*layout.ui()),
                shrink,Math.max(9,(int)(h*(1-p))),color);
        }
        if(!energy&&action.endsWith("附着")&&elapsed>.40&&elapsed<.98)
            BattleSceneEffects.landing(context,pokemon,elapsed-.40,BattleSceneEffects.CYAN);
    }
    private void drawStatusCue(DrawContext context,PtcgLiveTableLayout layout,AnimatedCue animation,
                               List<Placed> positions,double elapsed) {
        Rect target=cueTarget(animation,positions);
        if(target==null)return;
        String[] parts=animation.cue().text().split("·",2);
        String condition=parts[0],change=parts.length>1?parts[1]:"附加";
        String sprite=switch(condition) {
            case "中毒" -> "poison";case "灼伤" -> "burn";case "睡眠" -> "asleep";
            case "麻痹" -> "paralyzed";case "混乱" -> "confused";default -> null;
        };
        if(sprite==null)return;
        int k=layout.ui(),cx=midX(target),cy=midY(target);
        double a=BattleMotion.envelope(elapsed,1.0,.12,.25);
        if(a<4/255.0)return;
        boolean clear=change.equals("解除");
        boolean renderedBadge=!clear&&(condition.equals("混乱")||condition.equals("麻痹"));
        if(animationQuality!=BattleAnimationEngine.Quality.OFF)
            BattleSceneEffects.status(context,target,condition,clear,elapsed);
        if(!renderedBadge||animationQuality==BattleAnimationEngine.Quality.OFF) {
            boolean atEdge=clear||condition.equals("睡眠");
            int iconY=atEdge?cy-(int)(target.height()*.17):cy-(int)(BattleMotion.out(elapsed)*10*k);
            int iconX=atEdge?cx+(int)(target.width()*.37):cx;
            var matrices=context.getMatrices();matrices.push();matrices.translate(iconX,iconY,0);
            float scale=(float)(clear?1-BattleMotion.smooth(elapsed/.42):BattleMotion.out(elapsed/.18));
            matrices.scale(scale,scale,1);
            CardPainter.statusIcon(context,sprite,-5*k,-5*k,k);
            matrices.pop();
        }
        String label=condition+(change.equals("解除")?"解除":"附加");
        BattleUi.centeredText(context,textRenderer,label,cx,
            Math.max(0,Math.min(height-10*k,target.y()-12*k)),BattleEffects.alpha(clear?0xFFB6FFE2:BattleSceneEffects.WHITE,a),true,k);
    }
    private long coinDuration(MatchPresentation.Cue cue){
        for(var a:animations)if(a.cue()==cue)return CoinAnimation.duration(a.coinCount());
        return CoinAnimation.duration(1);
    }
    private void drawCoinCue(DrawContext context,PtcgLiveTableLayout layout,AnimatedCue animation,double elapsed) {
        int count=Math.max(1,animation.coinCount()),index=Math.max(0,animation.coinIndex());
        double ms=elapsed*1000;
        var text=animation.cue().text();int result=text.endsWith("正面")?1:text.endsWith("反面")?0:-1;
        if(count==1 && text.startsWith("硬币·开局：")){
            CoinAnimation.opening(context,textRenderer,width,height,layout.ui(),ms,result,animationQuality);
            return;
        }
        if(index==0)CoinAnimation.background(context,width,height,ms,count,animationQuality);
        CoinAnimation.coin(context,layout.activeX()+layout.activeWidth()/2,width,height,ms,index,count,result,animationQuality);
        if(index==count-1&&CoinAnimation.opacity(ms,count)>.04){
            int heads=0,tails=0;
            for(var a:animations)if(a.started()==animation.started()&&a.batch()==animation.batch()
                &&a.cue().kind()==MatchPresentation.Kind.COIN&&CoinAnimation.landed(ms,a.coinIndex())){
                if(a.cue().text().endsWith("正面"))heads++;
                else if(a.cue().text().endsWith("反面"))tails++;
            }
            String label=heads+tails==0?"投掷硬币":"正面 "+heads+"   反面 "+tails;
            BattleUi.centeredText(context,textRenderer,label,layout.activeX()+layout.activeWidth()/2,
                (int)(height*.84),BattleEffects.alpha(0xFFFFFFFF,CoinAnimation.opacity(ms,count)),true,layout.ui());
        }
    }
    private void drawCard(DrawContext context, Placed placed, PtcgLiveTableLayout layout) {
        if(placed.card().key().startsWith("stadium:")&&!tableCardVisible(placed.card()))return;
        if(animationQuality!=BattleAnimationEngine.Quality.OFF&&placed.card().key().startsWith("hand:")){
            for(var a:animations)if(isToolReturn(a.cue())
                &&placed.card().key().equals("hand:"+returnedToolInstance(a.cue()))){
                double ms=toolReturnHandMs(a,animationElapsed(a,frameNow));
                if(ms<ToolAttachmentAnimation.returnStart())return;
                if(ms<ToolAttachmentAnimation.returnEnd()){
                    double left=1-ToolAttachmentAnimation.returnProgress(ms);
                    var matrices=context.getMatrices();matrices.push();
                    matrices.translate(0,(height-placed.y())*left,0);
                    drawStationaryCard(context,placed,layout);matrices.pop();return;
                }
            }
        }
        if(placed.card().key().startsWith("field:")) {
            UUID id=UUID.fromString(placed.card().key().substring(6));long now=frameNow;
            for(var animation:animations) if(id.equals(animation.cue().pokemon())) {
                var kind=animation.cue().kind();double elapsed=animationElapsed(animation,now);
                if(kind==MatchPresentation.Kind.ABILITY&&elapsed>=0&&elapsed*1000<AbilityAnimation.LAND_MS)return;
                if(kind==MatchPresentation.Kind.EVOLVE&&animationQuality==BattleAnimationEngine.Quality.OFF)continue;
                boolean arriving=kind==MatchPresentation.Kind.PLAY&&!animation.cue().text().equals("能量附着")||kind==MatchPresentation.Kind.EVOLVE
                    ||kind==MatchPresentation.Kind.DEVOLVE||kind==MatchPresentation.Kind.MOVE;
                double visibleUntil=SwitchAnimation.applies(animation.cue())?SwitchAnimation.LAND_MS/1000:RetreatAnimation.applies(animation.cue())?RetreatAnimation.LAND_MS/1000:PokemonEntryAnimation.applies(animation.cue())?PokemonEntryAnimation.contact(entryReveal(animation.cue()))/1000:kind==MatchPresentation.Kind.DEVOLVE?DevolutionAnimation.LAND_MS/1000:kind==MatchPresentation.Kind.MOVE?.72:kind==MatchPresentation.Kind.EVOLVE?BattleAnimationEngine.evolution().impactMs/1000:feedbackDuration(animation.cue())/1e9;
                if(arriving&&elapsed<visibleUntil) {
                    if(elapsed<0&&animation.previousCard()!=null) {
                        Rect old=kind==MatchPresentation.Kind.MOVE?previousCueTarget(animation):null;
                        flyingCard(context,animation.previousCard(),old==null?placed.x()+placed.width()/2:midX(old),
                            old==null?placed.y()+placed.height()/2:midY(old),placed.width(),placed.height(),CARD_BORDER);
                    }
                    return;
                }
                if(kind==MatchPresentation.Kind.ATTACK&&now>=animation.started()
                    &&(now-animation.started())/1e9<attackContact(animation)+attackRecovery(animation)&&!animation.cue().text().contains("攻击失败")) {
                    double seconds=(now-animation.started())/1e9;
                    var definition=attackAnimation(animation);
                    double ms=definition==null?0:BattleAnimationEngine.attackTime(definition,seconds,attackContact(animation));
                    boolean measuredPosition=definition!=null&&(definition.timeline().hasTrack("card.x")||definition.timeline().hasTrack("card.y"));
                    double lift=BattleAnimationEngine.value(definition,"card.lift",ms,measuredPosition?0:BattleMotion.attack(seconds,attackContact(animation))),direction=Objects.equals(animation.cue().seat(),bottomSeat())?-1:1;
                    var matrix=context.getMatrices();matrix.push();
                    matrix.translate(placed.x()+placed.width()/2.0+BattleAnimationEngine.value(definition,"card.x",ms,0)*layout.arena().width(),
                        placed.y()+placed.height()/2.0+lift*direction*layout.activeHeight()*.42+BattleAnimationEngine.value(definition,"card.y",ms,0)*layout.arena().height(),0);
                    float scale=(float)BattleAnimationEngine.value(definition,"card.scale",ms,1+Math.max(0,lift)*.045);matrix.scale(scale,scale,1);
                    matrix.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees((float)(-direction*BattleAnimationEngine.value(definition,"card.rotation",ms,lift*2.5))));
                    matrix.translate(-placed.x()-placed.width()/2.0,-placed.y()-placed.height()/2.0,0);
                    if(definition!=null&&animation.type()==EnergyType.LIGHTNING)
                        LightningAnimationRenderer.charge(BattleEffects.mesh(context),definition,placed.x()+placed.width()/2.0,placed.y()+placed.height()/2.0,placed.width(),ms,animationQuality);
                    drawStationaryCard(context,placed,layout);matrix.pop();return;
                }
                if(kind==MatchPresentation.Kind.DAMAGE&&animation.type()==EnergyType.LIGHTNING&&animation.cue().text().startsWith("招式伤害·")) {
                    double ms=(now-animation.started())/1e6;
                    double recoil=shakeEnabled&&animationQuality!=BattleAnimationEngine.Quality.OFF?attackAnimation(animation).electric().shake(ms):0;
                    if(recoil!=0){var matrix=context.getMatrices();matrix.push();matrix.translate(recoil*1.2*layout.ui(),0,0);
                        drawStationaryCard(context,placed,layout);matrix.pop();return;}
                    continue;
                }
                if(shakeEnabled&&animationQuality!=BattleAnimationEngine.Quality.OFF&&kind==MatchPresentation.Kind.DAMAGE&&animation.cue().text().startsWith("招式伤害·")&&elapsed>=0&&elapsed<.38) {
                    var matrix=context.getMatrices();matrix.push();
                    matrix.translate(placed.x()+placed.width()/2.0+BattleMotion.recoil(elapsed)*4*layout.ui(),placed.y()+placed.height()/2.0,0);
                    float scale=(float)BattleMotion.hitScale(elapsed);matrix.scale(scale,scale,1);
                    matrix.translate(-placed.x()-placed.width()/2.0,-placed.y()-placed.height()/2.0,0);
                    drawStationaryCard(context,placed,layout);matrix.pop();return;
                }
            }
        }
        drawStationaryCard(context,placed,layout);
    }
    private void drawStationaryCard(DrawContext context, Placed placed, PtcgLiveTableLayout layout) {
        var pokemon=fieldPokemon(placed.card());
        if(pokemon.isPresent()&&!displayedEnergies(pokemon.get()).isEmpty()) {
            var ids=displayedEnergies(pokemon.get());
            var fan=EnergyStackLayout.of(placed.x(),placed.y(),placed.width(),placed.height(),ids.size());
            for(int i=fan.layers().size()-1;i>=0;i--) {
                var r=fan.layers().get(i);String id=ids.get(i);
                BattleUi.card(context,textRenderer,artwork.get(id),name(id),r.x(),r.y(),r.width(),r.height(),ACCENT_SOFT);
            }
            var r=fan.front();
            placed=new Placed(placed.card(),r.x(),r.y(),r.width(),r.height());
        }
        var card = placed.card(); int k = layout.ui(), border = borderColor(card);
        boolean marked=border==SELECTED||border==ACCENT;
        int visualBorder=border==SELECTED?0xFFA4F6D4:border==ACCENT?BattleSceneEffects.CYAN:border;
        if(marked&&animationQuality!=BattleAnimationEngine.Quality.OFF)
            BattleSceneEffects.focus(context,placed.x(),placed.y(),placed.width(),placed.height(),visualBorder,frameNow/1e9);
        BattleUi.card(context, textRenderer, artwork.get(card.definitionId()), card.nameZh(),
            placed.x(), placed.y(), placed.width(), placed.height(), visualBorder);
        if (border == SELECTED) {
            int size = 10 * k, x = placed.x() + placed.width() - size - 1;
            CardPainter.roundRect(context, x, placed.y() + 1, size, size, SELECTED);
            var selection=snapshot.game().effectSelection();
            String mark=selection!=null&&card.key().startsWith("effect:")?Integer.toString(selection.selected().indexOf(UUID.fromString(card.key().substring(7)))+1):"✓";
            BattleUi.text(context, textRenderer, mark, x + 2 * k, placed.y() + 2 * k, 0x103020, false, k);
        }
        if((dragging!=null||selectedSource!=null)&&border==CARD_BORDER)
            context.fill(placed.x(),placed.y(),placed.x()+placed.width(),placed.y()+placed.height(),0x2838495B);
        if(pokemon.isPresent())drawPokemonState(context,pokemon.get(),placed,k);
        var allocation = snapshot.game().damageAllocation();
        if (allocation != null && card.key().startsWith("field:")) {
            int pending = allocation.counters().getOrDefault(UUID.fromString(card.key().substring(6)), 0);
            if (pending > 0) BattleUi.centeredText(context, textRenderer, "+" + pending, placed.x() + placed.width() / 2,
                placed.y() + placed.height() / 2, ACCENT, false, k);
        }
    }

    /** 场上宝可梦的公开状态：道具小卡、右上角剩余 HP、伤害指示物、底部能量圆点、左侧异常状态。 */
    private void drawPokemonState(DrawContext context, PokemonView pokemon, Placed placed, int k) {
        int x = placed.x(), y = placed.y(), width = placed.width(), height = placed.height();
        Rect target=new Rect(x,y,width,height);
        for (int i = 0; i < pokemon.attachedTools().size(); i++) {
            var tool = pokemon.attachedTools().get(i);
            boolean pending=false;
            if(animationQuality!=BattleAnimationEngine.Quality.OFF)for(var a:animations)
                if(a.cue().kind()==MatchPresentation.Kind.TOOL&&a.cue().amount()>0&&a.toolIndex()==i
                    &&pokemon.pokemonId().equals(a.cue().pokemon())&&frameNow<a.started()){pending=true;break;}
            if(!pending)ToolAttachmentAnimation.badge(context,artwork.get(tool.definitionId()),ToolAttachmentAnimation.icon(target,i));
        }
        if(animationQuality!=BattleAnimationEngine.Quality.OFF)for(var a:animations)
            if(a.cue().kind()==MatchPresentation.Kind.TOOL&&a.cue().amount()<0&&pokemon.pokemonId().equals(a.cue().pokemon())
                &&animationElapsed(a,frameNow)<0)
                ToolAttachmentAnimation.badge(context,artwork.get(a.cue().cardId()),ToolAttachmentAnimation.icon(target,a.toolIndex()));
        int visualDamage=pokemon.damage();long now=frameNow;
        // The snapshot contains the final total. Undo only the unplayed fraction of each change.
        for(var a:animations){
            var kind=a.cue().kind();
            if((kind!=MatchPresentation.Kind.DAMAGE&&kind!=MatchPresentation.Kind.HEAL)||
                !pokemon.pokemonId().equals(a.cue().pokemon())||a.previousDamage()<0)continue;
            int delta=kind==MatchPresentation.Kind.HEAL?-Math.min(a.previousDamage(),a.cue().amount()):a.cue().amount();
            visualDamage-=delta-(int)Math.round(delta*hitHpProgress(a,now));
        }
        visualDamage=Math.max(0,visualDamage);
        int remaining = Math.max(0, pokemon.hitPoints() - visualDamage);
        // A projected card may be much smaller than the HUD's global scale. Keep its HP in the top band.
        int hpScale=Math.max(1,Math.min(k,height/40));
        BattleUi.hp(context, textRenderer, remaining, pokemon.pokemonType(), x + width - 1, y + 1, hpScale);
        boolean hitNumber=animations.stream().anyMatch(a->pokemon.pokemonId().equals(a.cue().pokemon())
            &&(a.cue().kind()==MatchPresentation.Kind.DAMAGE&&a.cue().text().startsWith("招式伤害·")
                ?hitNumberOpacity(a,now)>0:(checkupDamage(a.cue())||a.cue().kind()==MatchPresentation.Kind.HEAL)
                    &&BattleMotion.numberOpacity(animationElapsed(a,now))>0));
        if (visualDamage > 0&&!hitNumber) BattleUi.damageBadge(context, visualDamage, x + width - 10 * k, y + height * 45 / 100, k);
        var energies = displayedEnergies(pokemon);
        int visibleEnergy=energies.size();
        if(animationQuality!=BattleAnimationEngine.Quality.OFF)for(var a:animations)
            if(pokemon.pokemonId().equals(a.cue().pokemon())&&energyContactMs(a)>0
                &&animationElapsed(a,now)*1000<energyContactMs(a))visibleEnergy=Math.max(0,visibleEnergy-1);
        int diameter=EnergyAttachmentAnimation.diameter(width,k),step=EnergyAttachmentAnimation.step(width,k),room=EnergyAttachmentAnimation.room(width,k);
        int shown = Math.min(room, visibleEnergy);
        if (shown < visibleEnergy && shown > 1) shown--;   // 给“+n”留位置
        for (int i = 0; i < shown; i++)
            if(!discardedEnergyHidden(pokemon.pokemonId(),i,now))BattleUi.energyOrb(context, x + 2 + i * step, y + height - diameter - 2 * k, diameter, energyColor(name(energies.get(i))));
        int extra=visibleEnergy-shown;
        for(int i=shown;i<visibleEnergy;i++)if(discardedEnergyHidden(pokemon.pokemonId(),i,now))extra--;
        if (extra > 0)
            BattleUi.text(context, textRenderer, "+" + extra, x + 3 + shown * step, y + height - 10 * k, TEXT, false, k);
        int conditionMask=0;
        for(var condition:pokemon.conditions())conditionMask|=1<<condition.ordinal();
        for(int i=animations.size()-1;i>=0;i--){
            var a=animations.get(i);
            if(a.cue().kind()!=MatchPresentation.Kind.STATUS||!pokemon.pokemonId().equals(a.cue().pokemon())||now>=a.started())continue;
            for(var condition:CONDITIONS)if(a.cue().text().startsWith(MatchPresentation.conditionName(condition))){
                int bit=1<<condition.ordinal();
                if(a.cue().text().endsWith("附加"))conditionMask&=~bit;
                if(a.cue().text().endsWith("解除"))conditionMask|=bit;
                break;
            }
        }
        int conditionRow=0;
        for(var condition:CONDITIONS)if((conditionMask&(1<<condition.ordinal()))!=0)
            CardPainter.statusIcon(context,conditionSprite(condition),x-13*k,y+2+conditionRow++*12*k,k);
        if(snapshot.guidance()!=null&&snapshot.guidance().fieldStatus().getOrDefault("field:"+pokemon.pokemonId(),List.of()).stream().anyMatch(s->s.contains("次数已使用")))
            BattleUi.text(context,textRenderer,"特性已用",x+2*k,y+height-22*k,NOTICE,true,k);
    }

    /** 卡框颜色：绿＝已选，金＝可放下或可选目标，青＝现在可以由你操作，其余普通灰框。 */
    private int borderColor(MatchRegionModel.Card card) {
        String key = card.key(); var game = snapshot.game();
        if (key.startsWith("effect:") && game.effectSelection() != null
            && game.effectSelection().selected().stream().anyMatch(id -> key.equals("effect:" + id))) return SELECTED;
        if (dragging != null) return canTarget(dragging, card) ? ACCENT : CARD_BORDER;
        if(selectedSource!=null)return selectedSource.key().equals(key)?SELECTED:canTarget(selectedSource,card)?ACCENT:CARD_BORDER;
        if (key.startsWith("field:")) {
            if (game.effectAttachment() != null && !forCards(key).isEmpty()) return ACCENT;
            if (game.damageAllocation() != null && !forCards(key, "counter:add").isEmpty()) return ACCENT;
            if (!forCards(key, MatchSession.POKEMON_SELECTION_KEY).isEmpty()) return ACCENT;
            return actorOptions(card).isEmpty() && pokemonAttackOptions(card).isEmpty() ? CARD_BORDER : OPERABLE;
        }
        boolean operable = key.startsWith("hand:") || key.startsWith("effect:") || key.startsWith("effect-source:") || key.startsWith("tool-overflow:");
        return operable && !forCards(key).isEmpty() ? OPERABLE : CARD_BORDER;
    }

    private void drawHoverTooltip(DrawContext context, PtcgLiveTableLayout layout, int mouseX, int mouseY) {
        var hovered = at(mouseX, mouseY);
        if (hovered != null) {
            var lines = new ArrayList<Text>(); lines.add(Text.literal(hovered.card().nameZh()));
            for (String part : hovered.card().detailZh().split(" · "))
                if (!part.isBlank()) lines.add(Text.literal(part).formatted(Formatting.GRAY));
            if(forCards(hovered.card().key()).isEmpty())
                lines.add(Text.literal(snapshot.guidance()==null?MatchPresentation.blockedCard(snapshot,hovered.card().key()):snapshot.guidance().blockedCards().getOrDefault(hovered.card().key(),MatchPresentation.blockedCard(snapshot,hovered.card().key()))).formatted(Formatting.YELLOW));
            if(isOwnPokemon(hovered.card()))for(String hint:attackHints(hovered.card()))
                lines.add(Text.literal(hint).formatted(Formatting.YELLOW));
            fieldPokemon(hovered.card()).ifPresent(p -> {
                if(!p.attachedEnergyCardIds().isEmpty()) lines.add(Text.literal("附着能量："+String.join("、",
                    p.attachedEnergyCardIds().stream().map(this::name).toList())).formatted(Formatting.AQUA));
                if(!p.continuousEffectZh().isBlank())lines.add(Text.literal(p.continuousEffectZh()).formatted(Formatting.YELLOW));
                if(p.evolutionCardIds().size()>1)lines.add(Text.literal("进化叠层："+String.join("、",p.evolutionCardIds().stream().map(this::name).toList())));
                if(snapshot.guidance()!=null)snapshot.guidance().fieldStatus().getOrDefault(hovered.card().key(),List.of()).forEach(s->lines.add(Text.literal(s)));
            });
            BattleUi.tooltip(context,textRenderer, lines, mouseX, mouseY);
            return;
        }
        if (currentOverlay() != null && overlayArea(layout).contains(mouseX, mouseY)) return;
        if (prizeIndexAt(mouseX, mouseY, layout) >= 0 && hasPrizeOptions()) {
            BattleUi.tooltip(context,textRenderer, Text.literal("点击拿取这张奖赏卡"), mouseX, mouseY);
            return;
        }
        for (boolean opponent : new boolean[] {true, false}) {
            UUID seat = opponent ? topSeat() : bottomSeat(); var player = seat == null ? null : player(seat);
            if (player == null) continue;
            if (tableContains(cardRect(layout, layout.discardX(), opponent),mouseX,mouseY))
                BattleUi.tooltip(context,textRenderer, Text.literal(sideName(seat) + "弃牌区 · " + player.discard().size() + " 张 · 点击查看"), mouseX, mouseY);
            else if (tableContains(cardRect(layout, layout.lostX(), opponent),mouseX,mouseY))
                BattleUi.tooltip(context,textRenderer, Text.literal(sideName(seat) + "放逐区 · " + player.lostZone().size() + " 张 · 点击查看"), mouseX, mouseY);
            else if (tableContains(cardRect(layout, layout.deckX(), opponent),mouseX,mouseY))
                BattleUi.tooltip(context,textRenderer, Text.literal(sideName(seat) + "牌库 · " + (player.deckSize()+pendingDealCards(seat,false)) + " 张"), mouseX, mouseY);
            else if (tableMode()?prizeContains(opponent,mouseX,mouseY):layout.prizeTray(opponent).contains(mouseX, mouseY))
                BattleUi.tooltip(context,textRenderer, Text.literal(sideName(seat) + "奖赏卡 · 剩 " + player.prizeCount() + " 张"), mouseX, mouseY);
            else continue;
            return;
        }
    }

    private static Rect cardRect(PtcgLiveTableLayout layout, int x, boolean opponent) {
        return new Rect(x, layout.benchY(opponent), layout.benchWidth(), layout.benchHeight());
    }

    // ================= 文字 =================

    private List<Line> promptLines() {
        var lines = new ArrayList<Line>();
        // Turn ownership already appears beside the end-turn button and avatar.
        // Keep this area empty during ordinary play; only pending choices/results need instructions.
        if (snapshot.game().phase() != MatchPhase.TURN || snapshot.game().result() != null)
            lines.add(new Line(promptTitle(), ACCENT));
        String hint = hint();
        if (!hint.isBlank()) lines.add(new Line(hint, TEXT_SOFT));
        if (!message.isBlank()) lines.add(new Line(message, NOTICE));
        return lines;
    }
    private String promptTitle() {
        var game = snapshot.game();
        if (game.result() != null) return resultText(game.result());
        if (game.phase() == MatchPhase.TURN && game.currentPlayer() != null) {
            if (game.viewingSeat() == null) return playerName(game.currentPlayer()) + " 的回合";
            return game.currentPlayer().equals(game.viewingSeat()) ? "你的回合" : "对手的回合";
        }
        return phaseName(game.phase());
    }
    private String resultText(MatchResult result) {
        UUID own = snapshot.game().viewingSeat();
        String who = result.winner() == null ? "平局" : own == null ? playerName(result.winner()) + " 获胜"
            : result.winner().equals(own) ? "你赢了" : "你输了";
        String reason = switch (result.reason()) {
            case PRIZES -> "奖赏卡拿完"; case NO_POKEMON -> "场上没有宝可梦"; case DECK_OUT -> "牌库无牌可抽"; case CARD_EFFECT -> "卡牌效果直接获胜";
            case CONCESSION -> "认输"; case TIME -> "超时"; case DRAW -> "双方同时满足胜利条件"; case SUDDEN_DEATH -> "突然死亡";
        };
        return "对局结束：" + who + "（" + reason + "）";
    }
    private static String phaseName(MatchPhase phase) {
        return switch (phase) {
            case MULLIGAN_REVEAL -> "重抽展示"; case MULLIGAN_DRAW -> "重抽补偿"; case MULLIGAN_BENCH -> "补抽备战";
            case COIN_CALL -> "猜硬币"; case TURN_ORDER -> "决定先后手"; case OPENING_POKEMON -> "开局放置";
            case TURN -> "回合中"; case POKEMON_CHECKUP -> "宝可梦检查"; case PRIZE_SELECTION -> "拿取奖赏卡";
            case CHOOSE_REPLACEMENT -> "选择替补"; case FINISHED -> "对局结束";
            default -> "回合间";
        };
    }
    /** 这一步该怎么做；结算中的效果与触发顺序沿用服务器的说明顺序。 */
    private String hint() {
        var game = snapshot.game(); UUID own = game.viewingSeat();
        if(disconnected)return "对局已中断，请重新打开卡桌；不能继续发送操作。";
        if(battleInputLocked())return "特效播放中，结束后可继续操作。";
        if(awaitingServer)return System.nanoTime()-sentAt>5_000_000_000L
            ?"服务器尚未回复，可在菜单点击刷新；不会自动重复发送操作。":"操作已发送，正在等待服务器确认。";
        if (dragging != null) return "正在拖动「" + dragging.nameZh() + "」：放到青色高亮的位置，由服务器复核。";
        if (draggingCounter) return "把伤害指示物放到青色高亮的宝可梦上。";
        if (game.toolOverflow() != null) return "";
        if (game.damageAllocation() != null)
            return game.damageAllocation().actionZh()+"；左键加1个，Shift+左键撤回，完成后确认。";
        if (game.effectAttachment() != null) {
            var attachment = game.effectAttachment();
            return attachment.actionZh().equals("分配能量")
                ? "已分配 " + attachment.assignedTargets().size() + " / " + attachment.cards().size() + " 张能量：把下方每张卡拖到青色高亮的宝可梦，全部分配后点确认。"
                : "已选 " + attachment.cards().size() + " 张能量：拖到青色高亮的宝可梦或直接点击它，服务器确认后" + attachment.actionZh() + "。";
        }
        if (MatchActionMatcher.hasPokemonChoice(snapshot)) return "点击青色边框的宝可梦作为这一步的目标；每一步分别选择，由服务器复核。";
        if (game.triggerOrder() != null) {
            var trigger = game.triggerOrder(); boolean mine = trigger.choosingPlayer().equals(own);
            if (trigger.optionalDecision()) return mine ? "选择是否使用触发效果。" : "等待效果拥有者确认。";
            if (trigger.trigger() == EffectTrigger.ON_KNOCKOUT)
                return mine ? "击倒：选择触发顺序，全部完成后同时移场与拿奖。" : "击倒：等待有排序权的玩家排列效果。";
            if (trigger.trigger() == EffectTrigger.ON_TURN_END)
                return mine ? "回合末：选择效果顺序，完成后进行宝可梦检查。" : "回合末：等待指定玩家排列效果。";
            if (game.phase() == MatchPhase.POKEMON_CHECKUP)
                return mine ? "宝可梦检查：选择卡牌效果或异常状态的先后顺序。" : "宝可梦检查：等待下一回合的玩家排列顺序。";
            if (trigger.trigger() == EffectTrigger.ON_DAMAGE)
                return mine ? "受到伤害：选择效果顺序，全部完成后检查击倒。" : "受到伤害：等待受伤一方排列效果顺序。";
            return mine ? "选择同时触发的效果的结算顺序。" : "等待当前回合玩家排列触发顺序。";
        }
        if (game.resolvingAttack() != null) return "正在结算攻击：" + game.resolvingAttack().nameZh()
            + (game.resolvingAttack().phase() == EffectTrigger.BEFORE_ATTACK ? " · 先完成伤害前的效果，再计算伤害。" : " · 完成伤害后的效果，再检查击倒并结束回合。");
        if (game.resolvingAbility() != null) return "正在结算：" + game.resolvingAbility().nameZh() + " · 等待效果选择。";
        if (game.resolvingTrainer() != null) return "正在结算：" + name(game.resolvingTrainer().definitionId()) + " · 等待效果选择。";
        if (game.result() != null) return "可以在“菜单”里关闭对战桌。";
        if (own == null) return "观战中：只显示公开信息。右键任意卡看大图。";
        boolean acting = hasOwnOptions();
        return switch (game.phase()) {
            case MULLIGAN_REVEAL -> acting ? "看完对方的展示后点确认。" : "等待对手确认重抽展示。";
            case MULLIGAN_DRAW -> acting ? "对手重抽了：选择要额外抽几张。" : "等待对手选择额外抽牌。";
            case MULLIGAN_BENCH -> acting ? "可以把补抽到的基础宝可梦拖进备战区，完成后点确认。" : "等待对手设置备战区。";
            case COIN_CALL -> acting ? "猜硬币的正反面，猜中的一方决定先后手。" : "等待对手猜硬币。";
            case TURN_ORDER -> acting ? "选择先手还是后手。" : "等待对手决定先后手。";
            case OPENING_POKEMON -> acting ? "把基础宝可梦从手牌拖到战斗位，再拖进备战区，完成后点「确认开局设置」。" : "等待对手放置开局宝可梦。";
            case TURN -> "";
            case PRIZE_SELECTION -> hasPrizeOptions() ? "先选择背面朝上的奖赏卡，再确认拿取；服务器确认后翻开并拿入手牌。" : "等待对手拿取奖赏卡。";
            case CHOOSE_REPLACEMENT -> acting ? "战斗宝可梦被击倒：把一只备战宝可梦拖到战斗位。" : "等待对手选择替补。";
            default -> "";
        };
    }
    private static String conditionSprite(SpecialCondition condition) {
        return switch (condition) {
            case POISONED -> "poison"; case BURNED -> "burn"; case ASLEEP -> "asleep"; case PARALYZED -> "paralyzed"; case CONFUSED -> "confused";
        };
    }

    // ================= 场上宝可梦 =================

    /** 只渲染公开的场上状态；手牌与奖赏卡的私有信息从不经过这里。 */
    private Optional<PokemonView> fieldPokemon(MatchRegionModel.Card card) {
        if (!card.key().startsWith("field:")) return Optional.empty();
        UUID id;
        try { id = UUID.fromString(card.key().substring("field:".length())); }
        catch (IllegalArgumentException malformed) { return Optional.empty(); }
        return snapshot.game().players().stream().flatMap(player -> {
            var candidates = new ArrayList<PokemonView>();
            if (player.active() != null) candidates.add(player.active());
            candidates.addAll(player.bench());
            return candidates.stream();
        }).filter(candidate -> candidate.pokemonId().equals(id)).findFirst();
    }
    private boolean isOwnActive(MatchRegionModel.Card card) {
        UUID own = snapshot.game().viewingSeat();
        if (own == null || !card.key().startsWith("field:")) return false;
        var player = player(own);
        return player != null && player.active() != null && card.key().equals(fieldKey(player.active().pokemonId()));
    }
    private boolean isOwnPokemon(MatchRegionModel.Card card) {
        UUID own=snapshot.game().viewingSeat();
        if(own==null||!card.key().startsWith("field:"))return false;
        var field=player(own);
        return field!=null&&(field.active()!=null&&card.key().equals(fieldKey(field.active().pokemonId()))
            ||field.bench().stream().anyMatch(p->card.key().equals(fieldKey(p.pokemonId()))));
    }
    /** 按服务器发给实际出招宝可梦的选项展示攻击；战斗位的限制不会遮蔽备战位。 */
    private List<MatchSession.Option> pokemonAttackOptions(MatchRegionModel.Card card) {
        if (!isOwnPokemon(card)) return List.of();
        if(isOwnActive(card)&&snapshot.attackBlockZh()!=null&&!snapshot.attackBlockZh().isBlank())return List.of();
        return forCards(card.key()).stream().filter(o->!o.cardKeys().isEmpty()&&o.cardKeys().getFirst().equals(card.key()))
            .filter(MatchActionMatcher::isAvailableAttackOption).toList();
    }
    private List<MatchSession.Option> cardFaceOptions(MatchRegionModel.Card card) {
        return MatchActionMatcher.cardFaceActions(snapshot.options(),card.key(),isOwnPokemon(card)
            &&(!isOwnActive(card)||snapshot.attackBlockZh()==null||snapshot.attackBlockZh().isBlank()));
    }
    private List<String> attackHints(MatchRegionModel.Card card) {
        if(!isOwnPokemon(card))return List.of();
        var result=new LinkedHashSet<String>();
        if(isOwnActive(card)&&snapshot.attackBlockZh()!=null&&!snapshot.attackBlockZh().isBlank())result.add(snapshot.attackBlockZh());
        for(var option:snapshot.options().stream().filter(o->!o.cardKeys().isEmpty()&&o.cardKeys().getFirst().equals(card.key())).toList())
            if(MatchActionMatcher.isAttackOption(option)&&!MatchActionMatcher.isAvailableAttackOption(option))
                result.add(option.labelZh());
        if(isOwnActive(card))for(var option:snapshot.options())
            if(option.labelZh().startsWith("攻击尚未支持：")||option.labelZh().startsWith("攻击组合尚未支持：")
                ||option.labelZh().startsWith("没有可复制的公开招式："))result.add(option.labelZh());
        if(result.isEmpty()&&pokemonAttackOptions(card).isEmpty())result.add("当前没有可用招式，可能受招式限制、使用次数或目标条件影响。");
        if(snapshot.guidance()!=null)for(var preview:snapshot.guidance().attacks())if(preview.sourceKey().equals(card.key())) {
            result.add(preview.name()+" · 能量 "+(preview.cost().isEmpty()?"无":preview.cost())+" · "+(preview.payable()?"支付满足":"支付不足"));
            result.add(preview.certainty()+(preview.damage()==null?"":" "+preview.damage())+"："+preview.explanation());
        }
        return List.copyOf(result);
    }
    /** 以这张卡为发起者（服务器选项的第一个锚点）的非攻击操作：特性、撤退、选择替补、出这张手牌…… */
    private List<MatchSession.Option> actorOptions(MatchRegionModel.Card card) {
        return snapshot.options().stream().filter(option -> option.enabled()&&!option.cardKeys().isEmpty() && option.cardKeys().getFirst().equals(card.key())
            && !MatchActionMatcher.isAttackOption(option)).toList();
    }
    private static String fieldKey(UUID pokemonId) { return "field:" + pokemonId; }
    private boolean canTarget(MatchRegionModel.Card source, MatchRegionModel.Card target) {
        return !source.key().equals(target.key()) && forCards(source.key(), target.key()).stream().anyMatch(option -> !MatchActionMatcher.isAttackOption(option));
    }

    // ================= 奖赏卡 =================

    private boolean ownPrizeAnimating(){
        if(animationQuality==BattleAnimationEngine.Quality.OFF)return false;
        return animations.stream().anyMatch(a->a.cue().kind()==MatchPresentation.Kind.PRIZE
            && Objects.equals(a.cue().seat(),snapshot.game().viewingSeat())
            && frameNow>=a.started()&&animationElapsed(a,frameNow)*1000<PrizeCardAnimation.END_MS);
    }
    private boolean prizeChoicesVisible(){
        return hasPrizeOptions()&&snapshot.game().result()==null&&!finitePresentationLocked()
            &&currentOverlay()==null&&cardPanel==null&&popup==null&&pileView==null&&!menuOpen&&!logOpen;
    }
    private boolean prizeChoicesReady(){return animationQuality==BattleAnimationEngine.Quality.OFF
        ||prizeChoiceStarted!=0&&(frameNow-prizeChoiceStarted)/1e6>=PrizeCardAnimation.CHOICE_ENTER_MS;}
    private boolean prizeChoiceAnimating(){
        if(animationQuality==BattleAnimationEngine.Quality.OFF||!prizeChoicesVisible())return false;
        return !prizeChoicesReady()||prizeSelectedAt!=0&&(presentationClock.now()-prizeSelectedAt)/1e6<PrizeCardAnimation.SELECTION_FLASH_MS;
    }
    private int prizeSlot(int index){
        var slots=snapshot.game().prizeSlots();return index<slots.size()?slots.get(index):index;
    }
    private Optional<MatchSession.Option> selectedPrizeOption(){
        var own=player(snapshot.game().viewingSeat());if(own==null)return Optional.empty();
        for(int i=0;i<own.prizeCount();i++)if(prizeSlot(i)==selectedPrizeSlot)return prizeOption(i).filter(MatchSession.Option::enabled);
        return Optional.empty();
    }
    private void drawPrizeChoices(DrawContext c,PtcgLiveTableLayout l,int mx,int my){
        c.fill(0,0,width,height,0xA0050916);
        var own=player(snapshot.game().viewingSeat());if(own==null)return;
        for(int slot=0;slot<6;slot++){
            var r=PrizeCardAnimation.choice(width,height,slot);
            CardPainter.roundBorder(c,r.x(),r.y(),r.width(),r.height(),0x665D7189);
        }
        for(int i=0;i<own.prizeCount();i++){
            int slot=prizeSlot(i);if(slot<0||slot>=6)continue;
            var r=PrizeCardAnimation.choice(width,height,slot);
            if(!prizeChoicesReady()){
                var pose=PrizeCardAnimation.choicePose(tableRect(cardRect(l,l.prizeX(),false)),r,slot,(frameNow-prizeChoiceStarted)/1e6);
                var m=c.getMatrices();m.push();m.translate(pose.x(),pose.y(),0);m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees((float)pose.tilt()));
                flyingCard(c,null,0,0,(int)pose.width(),(int)pose.height(),ACCENT);m.pop();continue;
            }
            boolean hover=r.contains(mx,my)&&prizeOption(i).filter(MatchSession.Option::enabled).isPresent();
            boolean selected=slot==selectedPrizeSlot;
            BattleUi.cardBack(c,r.x(),r.y(),r.width(),r.height());
            if(selected&&prizeSelectedAt>0&&animationQuality!=BattleAnimationEngine.Quality.OFF){
                double age=(frameNow-prizeSelectedAt)/1e6;
                double flash=age<50?.95:.65*Math.pow(1-Math.clamp((age-50)/(PrizeCardAnimation.SELECTION_FLASH_MS-50),0,1),2);
                c.fill(r.x(),r.y(),r.right(),r.bottom(),BattleEffects.alpha(age<50?0xFFFFFFFF:0xFFB8FFFF,flash));
            }
            CardPainter.roundBorder(c,r.x(),r.y(),r.width(),r.height(),selected?0xFFA9FFFF:0xFF477DFF);
            if(animationQuality!=BattleAnimationEngine.Quality.OFF)PrizeCardAnimation.frame(BattleEffects.mesh(c),r,selected||hover?0xFF55FFFF:0xFF3479FF,selected);
            BattleUi.centeredText(c,textRenderer,Integer.toString(slot+1),r.right()-7,r.bottom()-10,0xFFFFFFFF,true,1);
        }
        drawPrizeBar(c,l,awaitingServer?"正在等待服务器确认……":"选择一张奖赏卡 · 剩余 "+own.prizeCount()+" 张");
    }
    private void drawPrizeBar(DrawContext c,PtcgLiveTableLayout l,String label){
        var bar=PrizeCardAnimation.bar(width,height);int k=l.ui();
        BattleEffectMesh.ribbon(BattleEffects.mesh(c),bar.x(),bar.y(),bar.width(),bar.height(),0xFF0B8CC1,0xFF08618E);
        BattleEffectMesh.line(BattleEffects.mesh(c),bar.x(),bar.bottom(),bar.right(),bar.bottom(),1,0xFFFFE5A5);
        BattleUi.centeredText(c,textRenderer,label,width/2,midY(bar)-4*k,0xFFFFFFFF,false,k);
        var button=PrizeCardAnimation.confirm(width,height);
        boolean enabled=prizeChoicesVisible()&&!prizeChoiceAnimating()&&!awaitingServer&&!disconnected&&selectedPrizeOption().isPresent();
        CardPainter.roundRect(c,button.x(),button.y(),button.width(),button.height(),enabled?0xFFFFF1B5:0xFFBDC4C7);
        CardPainter.roundBorder(c,button.x(),button.y(),button.width(),button.height(),0xFFB99A5C);
        BattleUi.centeredText(c,textRenderer,"拿取",midX(button),midY(button)-4*k,enabled?0xFF283D4B:0xFF68767C,false,k);
    }

    private boolean hasPrizeOptions() {
        return snapshot.options().stream().anyMatch(option -> option.labelZh().startsWith(PRIZE_LABEL));
    }
    /** 自己的第几张奖赏卡在鼠标下：扇形里每张只露出左边一条，最上面一张整张可点。 */
    private int prizeIndexAt(double x, double y, PtcgLiveTableLayout layout) {
        UUID own = snapshot.game().viewingSeat();
        var player = own == null ? null : player(own);
        if (player == null || player.prizeCount() == 0) return -1;
        int top = layout.benchY(false), count = player.prizeCount();
        if(tableMode()){
            var ray=camera().ray(x,y);
            for(int i=count-1;i>=0;i--)if(Double.isFinite(prizePose(false,i).intersect(ray)))return i;
            return -1;
        }
        if (y < top || y >= top + layout.benchHeight()) return -1;
        for (int i = count - 1; i >= 0; i--) {
            int left = layout.prizeX() + i * layout.prizeFan(), right = left + (i == count - 1 ? layout.benchWidth() : layout.prizeFan());
            if (x >= left && x < right) return i;
        }
        return -1;
    }
    /** 第 index 张背面朝上的奖赏卡对应的服务器操作（prizeSlots 按顺序列出剩余位置）。 */
    private Optional<MatchSession.Option> prizeOption(int index) {
        var slots = snapshot.game().prizeSlots();
        int slot = index < slots.size() ? slots.get(index) : index;
        return option(PRIZE_LABEL + (slot + 1));
    }

    // ================= 鼠标与键盘 =================

    @Override public boolean mouseClicked(double x, double y, int button) {
        if(battleInputLocked())return true;
        if(logOpen){logOpen=false;return true;}
        if(snapshot.game().result()!=null){if(frameNow>=resultStarted)super.mouseClicked(x,y,button);return true;}
        if(ownPrizeAnimating())return true;
        if(prizeChoicesVisible()){
            if(!prizeChoicesReady())return true;
            if(button==0&&!awaitingServer&&!disconnected){
                if(PrizeCardAnimation.confirm(width,height).contains(x,y)){
                    selectedPrizeOption().ifPresent(option->submit(option.index()));return true;
                }
                var own=player(snapshot.game().viewingSeat());
                if(own!=null)for(int i=0;i<own.prizeCount();i++){
                    int slot=prizeSlot(i);if(slot<0||slot>=6)continue;
                    if(PrizeCardAnimation.choice(width,height,slot).contains(x,y)){
                        var option=prizeOption(i).filter(MatchSession.Option::enabled);
                        if(option.isPresent()&&selectedPrizeSlot!=slot){selectedPrizeSlot=slot;prizeSelectedAt=frameNow;}
                        break;
                    }
                }
            }
            return true;
        }
        if (cardPanel != null) {
            if (cardPanel.closing()) return true;
            if (button == 0 && super.mouseClicked(x,y,button)) return true;
            if (!cardPanel.contains(x,y)) cardPanel.close();
            return true;
        }
        if (super.mouseClicked(x, y, button)) return true;
        if(cameraDebug&&cameraDebugRect().contains(x,y))return true;
        var layout = layout();
        if (menuOpen) { menuOpen = false; concedeArmed = false; clearAndInit(); return true; }
        if (popup != null) {
            if (!popupRect(layout).contains(x, y)) { popup = null; clearAndInit(); }
            return true;
        }
        var placed = at(x, y);
        if (button == 1) {
            if (placed == null) return false;
            if(onTable(placed.card()))return inspectTableCard(placed.card());
            var art = ART.get(placed.card().definitionId());
            if (art == null) { message = "此测试卡或未安装卡牌没有对应卡图。"; return true; }
            client.setScreen(new CardViewScreen(art, this)); return true;
        }
        if (button != 0) return false;
        if(awaitingServer||disconnected){message=hint();return true;}
        if (pileView != null) {
            if (!layout.overlay().contains(x, y)) { pileView = null; clearAndInit(); }
            return true;
        }
        var allocation = snapshot.game().damageAllocation();
        if (allocation != null) {
            var tray = layout.counterTray();
            if (x >= tray.x() && x < tray.x() + tray.size() && y >= tray.y() && y < tray.y() + tray.size()) {
                draggingCounter = allocation.allocated() < allocation.budget();
                return true;
            }
        }
        if (placed == null) return clickTable(x, y, layout);
        var card = placed.card();
        if(selectedSource!=null&&!selectedSource.key().equals(card.key())&&!snapshot.game().resolvingCardEffect()) {
            var targets=forCards(selectedSource.key(),card.key()).stream().filter(o->!MatchActionMatcher.isAttackOption(o)).toList();
            if(targets.size()==1){submit(targets.getFirst().index());return true;}
            if(!targets.isEmpty()){popup=new Popup((int)x,(int)y,targets);popupPage=0;clearAndInit();return true;}
        }
        if (MatchActionMatcher.hasPokemonChoice(snapshot)) {
            var options = forCards(card.key(), MatchSession.POKEMON_SELECTION_KEY);
            if (options.size() == 1) submit(options.getFirst().index());
            else message = "这张卡不是服务器允许的目标，请点击青色边框的宝可梦。";
            return true;
        }
        if (card.key().startsWith("tool-overflow:")) {
            var options = forCards(card.key());
            if (options.size() == 1) {popup=new Popup((int)x,(int)y,options);popupPage=0;message="确认弃置这张卡；确认前可以取消。";clearAndInit();}
            else message = "只能由这些工具所属的玩家选择弃置。";
            return true;
        }
        if (allocation != null) return allocateCounter(card, hasShiftDown());
        if (snapshot.game().effectAttachment() != null && card.key().startsWith("field:")) {
            var options = forCards(card.key());
            if (options.size() == 1) submit(options.getFirst().index());
            else message = "此宝可梦不符合附能目标条件。";
            return true;
        }
        if (card.key().startsWith("reveal:") || card.key().startsWith("mulligan:")||card.key().startsWith("choice-source:")) {
            message = "展示的卡只能查看，不能拖动或选择。"; return true;
        }
        if (card.key().startsWith("effect:")) {
            var options = forCards(card.key());
            if (options.size() == 1) submit(options.getFirst().index());
            else message = "已达到可选数量上限；可以先点已选的卡取消。";
            return true;
        }
        var attacks = pokemonAttackOptions(card);
        if (!attacks.isEmpty() || isOwnActive(card) && snapshot.game().phase()==MatchPhase.TURN
            && !snapshot.game().resolvingCardEffect()) return openAttackCard(card);
        if (!mayStartDrag(card)) {
            if(onTable(card))return inspectTableCard(card);
            message = MatchPresentation.blockedCard(snapshot,card.key());
            return true;
        }
        dragging = card; dragWidth = placed.width(); dragHeight = placed.height(); message = "";
        dragOrigin = new Rect(placed.x(), placed.y(), placed.width(), placed.height());
        grabX=unit((x-placed.x())/Math.max(1,dragWidth));
        grabY=unit((y-placed.y())/Math.max(1,dragHeight));
        dragStarted=presentationClock.now();
        return true;
    }

    /** 没点到卡：自己的奖赏卡（拿奖）或任一方的弃牌区／放逐区（查看）。 */
    private boolean clickTable(double x, double y, PtcgLiveTableLayout layout) {
        int prize = prizeIndexAt(x, y, layout);
        if (prize >= 0 && hasPrizeOptions()) {
            var option = prizeOption(prize);
            if (option.isPresent()) submit(option.get().index());
            else message = "这张奖赏卡现在不能拿取。";
            return true;
        }
        for (boolean opponent : new boolean[] {true, false}) {
            UUID seat = opponent ? topSeat() : bottomSeat();
            if (seat == null) continue;
            if (tableContains(cardRect(layout, layout.discardX(), opponent),x,y)) return openPile(seat + ":discard", sideName(seat) + "弃牌区");
            if (tableContains(cardRect(layout, layout.lostX(), opponent),x,y)) return openPile(seat + ":lost", sideName(seat) + "放逐区");
        }
        return false;
    }
    private boolean openPile(String zoneId, String titleZh) {
        if (zone(zoneId).cards().isEmpty()) { message = titleZh + "里还没有卡。"; return true; }
        pileView = new PileView(zoneId, titleZh); pilePage = 0; menuOpen = false; clearAndInit();
        return true;
    }
    private boolean openAttackCard(MatchRegionModel.Card card) {
        if (awaitingServer || disconnected || currentOverlay() != null || snapshot.game().resolvingCardEffect()) return true;
        dragging=null; dragOrigin=null; selectedSource=null; popup=null; menuOpen=false;
        var rows=dev.hua.ptcglive.view.BattleCardActions.rows(snapshot,card.key(),cardFaceOptions(card),attackHints(card));
        cardPanel=new BattleCardPanel(card,fieldPokemon(card).orElse(null),snapshot.revision(),rows);
        message=""; clearAndInit(); return true;
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if(battleInputLocked()){dragging=null;dragOrigin=null;draggingCounter=false;return true;}
        if (cardPanel != null) return super.mouseReleased(x,y,button);
        if (button == 0 && draggingCounter) {
            draggingCounter = false; var target = at(x, y);
            if (target != null) return allocateCounter(target.card(), false);
            message = "请把伤害指示物放到青色高亮的宝可梦卡面上。"; return true;
        }
        if (button != 0 || dragging == null) return super.mouseReleased(x, y, button);
        var source = dragging; dragging = null;
        // 升起的手牌一按下就变成拖动的卡、原位落回手牌行；在它原来升起的位置松开仍算“点了这张卡”，
        // 不能落到下面那只备战宝可梦上。
        boolean releasedOnOrigin=dragAtOrigin(source,x,y);
        dragOrigin = null;
        var target = releasedOnOrigin ? null : at(x, y);
        boolean sameCard = releasedOnOrigin || target != null && target.card().key().equals(source.key());
        if(sameCard&&(isOwnActive(source)||source.key().startsWith("field:")&&!cardFaceOptions(source).isEmpty()))return openAttackCard(source);
        DropZone zone = dropZoneAt(x, y);
        var candidates=dropOptions(source,target,zone,sameCard);
        var actions = candidates.stream().filter(option -> !MatchActionMatcher.isAttackOption(option)).toList();
        if (actions.isEmpty() && !candidates.isEmpty()) {
            message = "攻击请左键点击己方战斗宝可梦，再在放大的卡面旁点击招式按钮。"; return true;
        }
        if(sameCard&&!actions.isEmpty()){selectedSource=source;popup=new Popup((int)x,(int)y,List.copyOf(actions));popupPage=0;clearAndInit();}
        else if (actions.size() == 1) submit(actions.getFirst().index());
        else if (actions.size() > 1) { popup = new Popup((int) x, (int) y, List.copyOf(actions)); popupPage = 0; clearAndInit(); }
        else message = "这个落点不符合当前规则，服务器没有允许这次拖放。";
        return true;
    }

    private boolean dragAtOrigin(MatchRegionModel.Card source,double x,double y){
        if(tableMode()&&onTable(source))
            return logicalPlacements().stream().filter(p->p.card().key().equals(source.key()))
                .anyMatch(p->Double.isFinite(tablePose(p,false).intersect(camera().ray(x,y))));
        return dragOrigin!=null&&dragOrigin.contains(x,y);
    }
    private List<MatchSession.Option> dropOptions(MatchRegionModel.Card source,Placed target,DropZone zone,boolean sameCard){
        if(!sameCard&&target!=null)return MatchActionMatcher.forDrop(snapshot,source.key(),target.card().key());
        if(sameCard&&(source.key().startsWith("hand:")||zone==null))return forCards(source.key());
        return zone==null?List.of():zoneOptions(source,zone);
    }
    private DropZone dropZoneAt(double x, double y) {
        var layout = layout();
        if(tableMode()){
            if(tableContains(new Rect(layout.activeX(),layout.ownActiveY(),layout.activeWidth(),layout.activeHeight()),x,y))return DropZone.OWN_ACTIVE;
            for(int i=0;i<Math.max(5,zone(bottomSeat(),":bench").cards().size());i++){
                var r=new Rect(layout.benchX(Math.min(i,4)),layout.ownBenchY(),layout.benchWidth(),layout.benchHeight());
                if(tableContains(r,x,y))return DropZone.OWN_BENCH;
            }
            if(camera().contains(layout.arena(),layout,x,y))return DropZone.ARENA;
            return null;
        }
        if (layout.activeSlot(false).contains(x, y)) return DropZone.OWN_ACTIVE;
        if (layout.benchTray(false).contains(x, y)) return DropZone.OWN_BENCH;
        if (layout.arena().contains(x, y)) return DropZone.ARENA;
        return null;
    }
    private List<MatchSession.Option> zoneOptions(MatchRegionModel.Card source, DropZone zone) {
        return forCards(source.key()).stream().filter(option -> switch (zone) {
            case OWN_ACTIVE -> option.labelZh().startsWith("设为战斗宝可梦：") || option.labelZh().startsWith("选择替补：");
            case OWN_BENCH -> option.labelZh().startsWith("备战：") || option.labelZh().startsWith("放入备战区：")
                || option.labelZh().startsWith("补抽基础宝可梦放入备战：");
            // 无目标的训练家（含场地）拖到中间对战区使用；攻击只从放大的战斗宝可梦发动。
            case ARENA -> MatchActionMatcher.isArenaPlay(option);
        }).toList();
    }
    private boolean allocateCounter(MatchRegionModel.Card card, boolean remove) {
        var options = forCards(card.key(), remove ? "counter:remove" : "counter:add");
        if (options.size() == 1) submit(options.getFirst().index());
        else message = remove ? "这张卡上没有可以撤回的指示物。" : "这个目标不在允许范围，或已达到总数／目标上限。";
        return true;
    }
    /** 只能从自己的手牌、场上或待附着能量开始拖，而且服务器要给了含这张卡的操作。 */
    private boolean mayStartDrag(MatchRegionModel.Card card) {
        UUID own = snapshot.game().viewingSeat();
        if (own == null) return false;
        if (card.key().startsWith("effect-source:") && snapshot.game().effectAttachment() != null) return !forCards(card.key()).isEmpty();
        boolean ownZone = List.of(":hand", ":active", ":bench").stream().map(suffix -> zone(own, suffix))
            .flatMap(zone -> zone.cards().stream()).anyMatch(candidate -> candidate.key().equals(card.key()));
        return ownZone && !forCards(card.key()).isEmpty();
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if(battleInputLocked())return true;
        if (verticalAmount == 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        int delta = verticalAmount < 0 ? 1 : -1;
        if(logOpen){logOffset=Math.max(0,logOffset-delta*3);return true;}
        if (cardPanel != null) { if (!cardPanel.closing()) {cardPanel.turn(delta);clearAndInit();} return true; }
        if (popup != null) { popupPage = Math.max(0, popupPage + delta); clearAndInit(); return true; }
        var content = currentOverlay();
        if (content != null) {
            if (content.pages() > 1) { turnOverlayPage(content, delta); clearAndInit(); }
            return true;
        }
        if (layout().prompt().contains(mouseX, mouseY)) { flowPage = Math.max(0, flowPage + delta); clearAndInit(); return true; }
        if(mouseY>=layout().handRaisedY()){handOffset=Math.max(0,handOffset+delta*2);hoveredHand=-1;return true;}
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if(battleInputLocked())return true;
        if(keyCode==GLFW.GLFW_KEY_F8&&(net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()||Boolean.getBoolean("ptcglive.vfxDebug"))){
            if(lastElectricCue==null){message="先播放一次电属性命中，再按 F8 查看该事件。";return true;}
            var cue=lastElectricCue.cue();
            client.setScreen(new ElectricImpactDebugScreen(this,cue.cardId(),cue.amount(),Objects.hash(cue.kind().name(),cue.seat(),cue.pokemon(),cue.amount(),cue.text(),cue.cardId()),attackAnimation(lastElectricCue).electric()));return true;
        }
        if (cardPanel != null) {
            if (keyCode==GLFW.GLFW_KEY_ESCAPE) {cardPanel.close();return true;}
            if (cardPanel.closing()) return true;
            return super.keyPressed(keyCode,scanCode,modifiers);
        }
        if(keyCode==GLFW.GLFW_KEY_ESCAPE&&logOpen){logOpen=false;return true;}
        if(keyCode==GLFW.GLFW_KEY_ESCAPE&&(dragging!=null||draggingCounter||selectedSource!=null||endTurnArmed)) {
            dragging=null;dragOrigin=null;draggingCounter=false;selectedSource=null;endTurnArmed=false;popup=null;clearAndInit();return true;
        }
        if(keyCode==GLFW.GLFW_KEY_ESCAPE&&popup==null&&pileView==null&&!menuOpen&&overlay()!=null&&overlay().kind()==OverlayKind.EFFECT){effectCollapsed=true;clearAndInit();return true;}
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && (popup != null || pileView != null || menuOpen)) {
            popup = null; pileView = null; menuOpen = false; concedeArmed = false; clearAndInit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public void close() {
        if(battleInputLocked())return;
        if (client != null && parent == null && client.getNetworkHandler() != null)
            ClientPlayNetworking.send(new MatchIntentPayload(matchId().toString(), seat(), Math.toIntExact(snapshot.revision()), -2));
        if (client != null) client.setScreen(parent);
    }
    @Override public void removed() { drawnCards.clear();clearElectricEffects();electricPool.clear();battleAudio.clear();ScreenArtLifetime.removed(this, artwork::close); }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        return battleInputLocked()||super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean keyReleased(int keyCode,int scanCode,int modifiers) {
        return battleInputLocked()||super.keyReleased(keyCode,scanCode,modifiers);
    }
    @Override public boolean charTyped(char chr,int modifiers) {
        return battleInputLocked()||super.charTyped(chr,modifiers);
    }
    @Override public boolean shouldPause() { return false; }
}
