package dev.hua.ptcglive.client;

/**
 * PTCG Live 风格的共用配色与尺寸。这里只有数值和纯计算，不引用任何
 * Minecraft 类，所以 tests/ 下的纯 JUnit 风格测试可以直接加载它。
 * 所有界面的颜色都应从这里取；绘制动作在 {@link CardPainter}。
 */
public final class PtcgLiveTheme {
    // ---- 卡面 ----
    /** 卡框底色：卡图四周露出的深蓝。 */
    public static final int CARD_BACKDROP = 0xff1d2838;
    /** 普通卡框。 */
    public static final int CARD_BORDER = 0xff8090a0;
    /** 鼠标悬停的卡框。 */
    public static final int CARD_HOVER = 0xffffffff;
    /** 卡图右上角的张数角标：金底深字。 */
    public static final int BADGE_FILL = 0xffe6c45a;
    public static final int BADGE_TEXT = 0xff1d2838;
    /** 隐藏卡牌 / 暂无卡图 的卡背底色。 */
    public static final int CARD_HIDDEN = 0xff233a56;
    /** 尚未翻开的开包结果。 */
    public static final int CARD_FACEDOWN = 0xff0a1220;
    public static final int CARD_FACEDOWN_BORDER = 0xff7c96b4;

    // ---- 强调 ----
    /** 金色：可操作目标、分区标题、高清标记。 */
    public static final int ACCENT = 0xffe6c45a;
    /** 淡金：卡背边框、拖动提示框。 */
    public static final int ACCENT_SOFT = 0xffc3a563;
    /** 已选中的卡。 */
    public static final int SELECTED = 0xff55ff99;
    /** 面板与落点边框。 */
    public static final int PANEL_BORDER = 0xff48627c;
    /** 唯一允许的半透明底：开包左侧信息栏。 */
    public static final int PANEL_FILL = 0x80101826;
    /** 双方席位标签。 */
    public static final int SEAT_OWN = 0xffe5d6ff;
    public static final int SEAT_OPPONENT = 0xffffd4e5;

    // ---- 原版容器窗口（颜色取自 1.21.1 的 generic_54.png）----
    public static final int VANILLA_OUTLINE = 0xff000000;
    public static final int VANILLA_PANEL = 0xffc6c6c6;
    public static final int VANILLA_HIGHLIGHT = 0xffffffff;
    public static final int VANILLA_SHADOW = 0xff555555;
    /** 凹陷格子：左上深、右下亮、中间灰。 */
    public static final int SLOT_FILL = 0xff8b8b8b;
    public static final int SLOT_SHADOW = 0xff373737;
    public static final int SLOT_HIGHLIGHT = 0xffffffff;
    /** 原版格子悬停高光。 */
    public static final int SLOT_HOVER = 0x80ffffff;
    /** 拖动卡牌经过可放下的一栏时，盖在那一栏格子上的淡白色。 */
    public static final int DROP_TARGET = 0x40ffffff;
    /** 原版窗口上的标签文字（不带阴影），以及满足条件时的深绿、提示时的深褐。 */
    public static final int LABEL = 0x404040;
    public static final int LABEL_OK = 0x1f6f1f;
    public static final int LABEL_NOTICE = 0x7a4a00;
    /** 窗口上需要强调的标签（卡包中文名）。 */
    public static final int LABEL_STRONG = 0x202020;
    /** 未翻开的卡背：深蓝底、红白精灵球。 */
    public static final int CARD_BACK = 0xff24408e, CARD_BACK_EDGE = 0xff1a2e66;
    public static final int CARD_BACK_RED = 0xffd93636, CARD_BACK_WHITE = 0xfff2f2f2, CARD_BACK_LINE = 0xff111111;
    /** 凹陷文字区的底色（规则检查）。 */
    public static final int TEXT_AREA = 0xff000000;
    /** 卡桌大厅：座位底色、列表选中行外框、弹窗下压暗大厅的半透明黑。 */
    public static final int SEAT_FILL = 0xffb9b9b9, LIST_SELECTED = 0xff808080, DIALOG_DIM = 0x80000000;
    /** 黑色文字区里的“可以 / 不行”文字。 */
    public static final int GOOD_ON_DARK = 0x7fd37f, BAD_ON_DARK = 0xff7777, MUTED_ON_DARK = 0x808080;
    /** 浅灰窗口上的错误文字。 */
    public static final int LABEL_ERROR = 0x8b2020;
    /** 对局记录的胜 / 负 / 平色块。 */
    public static final int RESULT_WIN = 0xff2e7d32, RESULT_LOSS = 0xff9a2a2a, RESULT_DRAW = 0xff5a5a5a;

    // ---- 对战桌（PTCG Live 红蓝桌垫）----
    /** 桌垫：上半对方红、下半我方蓝，半透明，世界只透出一点。 */
    public static final int MAT_OPPONENT = 0xc89e1f2a, MAT_OWN = 0xc81f4fa8;
    /** 中间八边形对战区与它的银色描边。 */
    public static final int ARENA = 0xd0141c28, ARENA_EDGE = 0xff9aa4b0;
    /** 卡位托盘（备战区、战斗位、牌堆、头像栏）与双方的描边色。 */
    public static final int TRAY = 0xd80c1118, TRAY_EDGE_OPPONENT = 0xffc0505a, TRAY_EDGE_OWN = 0xff4f7fd0;
    /** 空卡位：更深的底和淡白描边。 */
    public static final int SLOT_EMPTY = 0x70000000, SLOT_EMPTY_LINE = 0x50ffffff;
    /** 青色：这张牌现在可以由你操作（出牌、拖动、点开招式）。 */
    public static final int OPERABLE = 0xff4fd8ff;
    /** 卡面右上角的剩余 HP：白底深字，受伤时红字。 */
    public static final int HP_PILL = 0xfff4f4f4, HP_TEXT = 0x1d2838, HP_HURT = 0xc62828;
    /** 牌堆右下角的张数标签。 */
    public static final int COUNT_TAB = 0xe0101826;
    /** 对战桌按钮：深底灰边，悬停白边；主要按钮金底深字。 */
    public static final int TABLE_BUTTON = 0xe01d2838, TABLE_BUTTON_EDGE = 0xff8090a0, TABLE_BUTTON_HOVER = 0xffffffff;
    public static final int TABLE_BUTTON_DISABLED = 0xe0262a30, TABLE_BUTTON_DISABLED_TEXT = 0x777777;
    /** 六边形“结束回合”：金底深字；不能结束时灰色。 */
    public static final int END_TURN = 0xffe6c45a, END_TURN_EDGE = 0xff7a5a12, END_TURN_TEXT = 0x2a1d05;
    public static final int END_TURN_DISABLED = 0xff5a6270, END_TURN_DISABLED_EDGE = 0xff30353d;
    /** 中间弹层（检索选牌、展示、查看弃牌区、拖放多选菜单）的底色。 */
    public static final int OVERLAY_PANEL = 0xe6101826;
    /** 能量圆点的描边。 */
    public static final int ORB_OUTLINE = 0xff1a1a1a;

    // ---- 文字 ----
    public static final int TEXT = 0xffffff;
    public static final int TEXT_BODY = 0xdddddd;
    public static final int TEXT_SOFT = 0xcccccc;
    public static final int TEXT_DIM = 0xaaaaaa;
    /** 服务器回执、等待提示。 */
    public static final int NOTICE = 0xffdd88;
    public static final int ERROR = 0xff9999;
    public static final int SUCCESS = 0x88ff99;

    // ---- 尺寸 ----
    /** 卡片宽 / 高。布局 record 由高算宽时统一用它。 */
    public static final float CARD_ASPECT = 0.714f;
    /** 卡片高 / 宽。沿用同伴原值；与 CARD_ASPECT 并不严格互为倒数，重设计时再统一。 */
    public static final double CARD_HEIGHT_PER_WIDTH = 1.39;
    /** 卡图距卡框的像素。 */
    public static final int CARD_INSET = 2;

    /** 等比缩放后的绘制矩形。 */
    public record Fit(int x, int y, int width, int height) { }

    /** 把 artWidth×artHeight 的图等比缩进 box 并居中；结果至少 1×1。 */
    public static Fit fit(int artWidth, int artHeight, int boxX, int boxY, int boxWidth, int boxHeight) {
        if (artWidth < 1 || artHeight < 1) throw new IllegalArgumentException("卡图尺寸无效");
        int innerWidth = Math.max(1, boxWidth), innerHeight = Math.max(1, boxHeight);
        double scale = Math.min((double) innerWidth / artWidth, (double) innerHeight / artHeight);
        int width = Math.max(1, (int) (artWidth * scale)), height = Math.max(1, (int) (artHeight * scale));
        return new Fit(boxX + (innerWidth - width) / 2, boxY + (innerHeight - height) / 2, width, height);
    }

    /** 开包画廊按中文稀有度着色的卡框。 */
    public static int rarityColor(String rarityZh) {
        return switch (rarityTier(rarityZh)) {
            case 4 -> 0xffffc64d; case 3 -> 0xffb774ff; case 2 -> 0xff67b7ff; case 1 -> 0xff74dc91; default -> 0xffc8c8c8;
        };
    }
    /** 浅灰窗口上的稀有度文字：与卡框同一档，但颜色更深，保证看得清。 */
    public static int rarityLabelColor(String rarityZh) {
        return switch (rarityTier(rarityZh)) {
            case 4 -> 0x8a5a00; case 3 -> 0x5a2a90; case 2 -> 0x1f55a0; case 1 -> 0x2a6e2a; default -> LABEL;
        };
    }
    /**
     * 能量圆点的颜色（正式能量图标做好之前的纯色占位）。按卡名认属性：
     * 卡名里的 {G}/{R}… 符号、中文属性字或英文属性词；认不出的（多数特殊能量）用浅灰。
     */
    public static int energyColor(String cardName) {
        String name = cardName == null ? "" : cardName;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (name.contains("{G}") || name.contains("草") || lower.contains("grass")) return 0x4caf50;
        if (name.contains("{R}") || name.contains("火") || lower.contains("fire")) return 0xe53935;
        if (name.contains("{W}") || name.contains("水") || lower.contains("water")) return 0x1e88e5;
        if (name.contains("{L}") || name.contains("雷") || lower.contains("lightning")) return 0xfdd835;
        if (name.contains("{P}") || name.contains("超") || lower.contains("psychic")) return 0x9c4dcc;
        if (name.contains("{F}") || name.contains("斗") || lower.contains("fighting")) return 0xc0702a;
        if (name.contains("{D}") || name.contains("恶") || lower.contains("darkness")) return 0x37474f;
        if (name.contains("{M}") || name.contains("钢") || lower.contains("metal")) return 0x9e9e9e;
        if (name.contains("{Y}") || name.contains("妖") || lower.contains("fairy")) return 0xf06292;
        if (name.contains("{N}") || name.contains("龙") || lower.contains("dragon")) return 0xc9a227;
        if (name.contains("{C}") || name.contains("无色") || lower.contains("colorless")) return 0xeeeeee;
        return 0xc8c8c8;
    }

    /** 0 普通 · 1 非凡 · 2 稀有 · 3 RR/EX/V · 4 SR/UR/HR。 */
    private static int rarityTier(String rarityZh) {
        String value = rarityZh == null ? "" : rarityZh.toUpperCase(java.util.Locale.ROOT);
        if (value.contains("SR") || value.contains("UR") || value.contains("HR")) return 4;
        if (value.contains("RR") || value.contains("EX") || value.contains("V")) return 3;
        if (value.contains("稀有") || value.equals("R")) return 2;
        if (value.contains("非凡") || value.equals("U")) return 1;
        return 0;
    }

    private PtcgLiveTheme() { }
}
