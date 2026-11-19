package dev.hua.ptcglive.client;

/**
 * 对战桌的整屏几何（PTCG Live 横版布局），只做纯计算，测试可以直接加载。
 *
 * <pre>
 *  [放逐][奖赏]  [ 对方备战区 5 格 ]  [牌库][弃牌]   [菜单]
 *  [对方头像]        [对方战斗位]        提示文字      第 n 回合
 *        [场地]                                     ⬡结束回合⬡
 *  [我方头像]        [我方战斗位]        提示按钮      我方回合
 *  [放逐][奖赏]  [ 我方备战区 5 格 ]  [牌库][弃牌]
 *                 [ 完整手牌与悬停信息 ]
 * </pre>
 *
 * 备战卡高 benchHeight，战斗卡与手牌高 activeHeight（= 1.25 倍）。从最大卡往下试，
 * 取第一个上下、左右都放得下的尺寸。对方牌区锚在屏幕顶边，我方手牌锚在屏幕底边；
 * 手牌说明放在我方备战区与手牌之间，不再占用手牌下方空间。
 * ui 是贴图与文字的放大倍数：卡够大（备战卡 ≥ 72）时为 2，让图标和字跟着卡一起变大。
 */
public record PtcgLiveTableLayout(int screenWidth, int screenHeight, int ui, int margin, int pad, int gap,
                                  int benchWidth, int benchHeight, int activeWidth, int activeHeight,
                                  int slotGap, int prizeFan, int trayX, int trayWidth,
                                  int opponentBenchY, int opponentActiveY, int ownActiveY, int ownBenchY, int centerY, int handY,
                                  int activeX, int prizeX, int lostX, int deckX, int discardX,
                                  int rightColumnX, int rightColumnWidth, int stadiumX, int stadiumY,
                                  int promptX, int promptRight) {
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) { return px >= x && px < x + width && py >= y && py < y + height; }
        public boolean overlaps(Rect other) {
            return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
        }
    }

    private static final int MAX_BENCH_HEIGHT = 112, MIN_BENCH_HEIGHT = 24;

    public static PtcgLiveTableLayout forScreen(int width, int height) {
        int margin = 3, pad = 3;
        int gap = clamp(Math.min(height / 54, width / 64), 5, 10), center = clamp(height / 45, 6, 14), rightColumn = clamp(width / 11, 44, 88);
        for (int bench = Math.min(MAX_BENCH_HEIGHT, height / 4); bench >= MIN_BENCH_HEIGHT; bench--) {
            int active = round(bench * 1.25), benchWidth = round(bench * PtcgLiveTheme.CARD_ASPECT), activeWidth = round(active * PtcgLiveTheme.CARD_ASPECT);
            int ui = bench >= 72 ? 2 : 1;
            int handGap = 6 + 10 * ui;
            int total = margin + 2 * (bench + 2 * pad) + 2 * active + 2 * gap + center
                + handGap + active + margin;
            int slotGap = clamp(benchWidth / 12, 2, 6), trayWidth = 5 * benchWidth + 4 * slotGap + 2 * pad;
            int fan = clamp(benchWidth / 6, 3, 8), prizeWidth = benchWidth + 5 * fan, pilesWidth = 2 * benchWidth + slotGap;
            int side = width / 2 - trayWidth / 2 - gap;
            int leftNeed = prizeWidth + 2 * pad + gap + benchWidth + pad, rightNeed = pilesWidth + pad + gap + rightColumn;
            if (total > height || side < Math.max(leftNeed, rightNeed) + margin) continue;
            int extra = height - total; center += extra;
            int opponentBench = margin + pad, opponentActive = margin + bench + 2 * pad + gap;
            int ownActive = opponentActive + active + center, centerY = opponentActive + active + center / 2;
            int ownBench = ownActive + active + gap + pad, hand = ownBench + bench + pad + handGap;
            int trayX = width / 2 - trayWidth / 2, activeX = width / 2 - activeWidth / 2;
            int prizeX = trayX - gap - prizeWidth, lostX = prizeX - pad - gap - pad - benchWidth;
            int deckX = trayX + trayWidth + gap, discardX = deckX + benchWidth + slotGap;
            int rightX = width - margin - rightColumn;
            int stadiumX = activeX - 3 * gap - benchWidth, stadiumY = centerY - bench / 2;
            return new PtcgLiveTableLayout(width, height, ui, margin, pad, gap, benchWidth, bench, activeWidth, active,
                slotGap, fan, trayX, trayWidth, opponentBench, opponentActive, ownActive, ownBench, centerY, hand,
                activeX, prizeX, lostX, deckX, discardX, rightX, rightColumn, stadiumX, stadiumY,
                activeX + activeWidth + 3 * gap, rightX - gap);
        }
        throw new IllegalArgumentException("界面尺寸过小，无法布置对局桌");
    }

    /** 第 index 个备战格（0–4）的左边。 */
    public int benchX(int index) { return trayX + pad + index * (benchWidth + slotGap); }
    public int benchY(boolean opponent) { return opponent ? opponentBenchY : ownBenchY; }
    public int activeY(boolean opponent) { return opponent ? opponentActiveY : ownActiveY; }
    public int prizeWidth() { return benchWidth + 5 * prizeFan; }
    /** 手牌完整显示并贴近底边；悬停只抬高两像素，不占用备战区。 */
    public int handPeek() { return screenHeight - handY; }
    public int handRaisedY() { return handY - 2; }
    public int handHintY() { return handY - 10 * ui; }

    /** Keep the cursor at the card centre, including at screen edges; never shift the drop anchor. */
    public Rect draggedCard(double mouseX, double mouseY, int width, int height) {
        return new Rect((int) Math.round(mouseX - width / 2.0),
            (int) Math.round(mouseY - height / 2.0), width, height);
    }

    /** 一行手牌：最多与卡同宽加 3 像素间距，放不下时互相压住，左右让出右侧栏的宽度。 */
    public record HandRow(int start, int spacing) {
        public int x(int index) { return start + index * spacing; }
    }
    public HandRow handRow(int count) {
        int room = screenWidth - 2 * (rightColumnWidth + margin + gap);
        int spacing = count <= 1 ? 0 : Math.max(2, Math.min(activeWidth + 3, (room - activeWidth) / (count - 1)));
        int span = activeWidth + Math.max(0, count - 1) * spacing;
        return new HandRow(screenWidth / 2 - span / 2, spacing);
    }

    // ---- 托盘（卡位外面那一圈深色底）----
    public Rect benchTray(boolean opponent) { return new Rect(trayX, benchY(opponent) - pad, trayWidth, benchHeight + 2 * pad); }
    public Rect activeSlot(boolean opponent) { return new Rect(activeX - 2, activeY(opponent) - 2, activeWidth + 4, activeHeight + 4); }
    public Rect prizeTray(boolean opponent) { return new Rect(prizeX - pad, benchY(opponent) - pad, prizeWidth() + 2 * pad, benchHeight + 2 * pad); }
    public Rect lostTray(boolean opponent) { return new Rect(lostX - pad, benchY(opponent) - pad, benchWidth + 2 * pad, benchHeight + 2 * pad); }
    public Rect pileTray(boolean opponent) {
        return new Rect(deckX - pad, benchY(opponent) - pad, 2 * benchWidth + slotGap + 2 * pad, benchHeight + 2 * pad);
    }
    public Rect stadiumSlot() { return new Rect(stadiumX - 2, stadiumY - 2, benchWidth + 4, benchHeight + 4); }

    // ---- 中间带：头像、提示、弹层 ----
    /** 头像栏：头像、名字、回合标记、GX/VSTAR 标记，对方再多一行手牌数。 */
    public Rect avatar(boolean opponent) {
        int x = margin + 2, width = Math.min(stadiumX - gap - x, 70 * ui);
        int height = (16 + 4 + 11 + 6 + (opponent ? 10 : 0)) * ui;
        int y = opponent ? opponentActiveY : ownActiveY + activeHeight - height;
        return new Rect(x, y, width, height);
    }
    public Rect prompt() { return new Rect(promptX, opponentActiveY, promptRight - promptX, ownActiveY + activeHeight - opponentActiveY); }
    /** 中间弹层：检索选牌、公开展示、超量工具、查看弃牌／放逐区。盖住中间带，不盖双方备战区和手牌。 */
    public Rect overlay() {
        int x = avatar(true).right() + gap;
        return new Rect(x, opponentActiveY - 4, promptRight - x, ownActiveY + activeHeight + 4 - (opponentActiveY - 4));
    }
    /** 中间对战区：无目标训练家（含场地）拖到这里使用。 */
    public Rect arena() {
        int x = lostX - pad, right = discardX + benchWidth + pad;
        return new Rect(x, opponentActiveY - 6, right - x, ownActiveY + activeHeight + 6 - (opponentActiveY - 6));
    }

    // ---- 右侧栏 ----
    public int buttonHeight() { return 16 * ui; }
    public Rect resultSheet() {
        int w=Math.min(256*ui,screenWidth-24*ui),h=Math.min(196*ui,screenHeight-24*ui);
        return new Rect((screenWidth-w)/2,(screenHeight-h)/2,w,h);
    }
    public Rect menuButton() { return new Rect(rightColumnX, margin, rightColumnWidth, buttonHeight()); }
    public Rect switchButton() { return new Rect(rightColumnX, margin + buttonHeight() + 3 * ui, rightColumnWidth, buttonHeight()); }
    public Rect endTurn() {
        int height = clamp(activeHeight * 6 / 10, 28, 40) * ui;
        return new Rect(rightColumnX, centerY - height / 2, rightColumnWidth, height);
    }

    /** 伤害指示物：放在提示区左上角（提示文字接在它下面，按钮在提示区底部），拖到高亮宝可梦上；不盖任何卡。 */
    public record CounterTray(int x, int y, int size) { }
    public CounterTray counterTray() {
        return new CounterTray(promptX, opponentActiveY, 18 * ui);
    }

    private static int round(double value) { return (int) Math.floor(value + 0.5); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
