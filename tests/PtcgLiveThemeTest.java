import dev.hua.ptcglive.client.PtcgLiveTheme;

/** 主题类的纯计算：等比缩放居中与稀有度配色；所有界面共用，这里兜底。 */
public final class PtcgLiveThemeTest {
    public static void main(String[] args) {
        // 竖版卡图缩进竖框：按高度缩放，左右留白对称。
        var portrait = PtcgLiveTheme.fit(733, 1024, 10, 20, 56, 78);
        check(portrait.height() == 78, "竖图应按高度贴满框：" + portrait);
        check(portrait.width() == 55, "竖图宽度应等比缩放：" + portrait);
        check(portrait.y() == 20 && portrait.x() == 10, "竖图应居中（宽差 1 像素时靠左）：" + portrait);
        // 横版图缩进竖框：按宽度缩放，上下留白对称。
        var landscape = PtcgLiveTheme.fit(200, 100, 0, 0, 56, 78);
        check(landscape.width() == 56 && landscape.height() == 28, "横图应按宽度贴满框：" + landscape);
        check(landscape.x() == 0 && landscape.y() == 25, "横图应垂直居中：" + landscape);
        // 极小框与零尺寸框永远至少画 1×1，不会抛异常也不会画负数。
        var tiny = PtcgLiveTheme.fit(733, 1024, 3, 3, 0, 0);
        check(tiny.width() == 1 && tiny.height() == 1 && tiny.x() == 3 && tiny.y() == 3, "极小框应退化为 1×1：" + tiny);
        try { PtcgLiveTheme.fit(0, 10, 0, 0, 10, 10); throw new AssertionError("无效卡图尺寸应明确拒绝"); }
        catch (IllegalArgumentException expected) { }
        // 正好同比例时不留白。
        var exact = PtcgLiveTheme.fit(50, 70, 7, 9, 100, 140);
        check(exact.x() == 7 && exact.y() == 9 && exact.width() == 100 && exact.height() == 140, "同比例应铺满：" + exact);

        // 稀有度颜色：沿用开包画廊原先的分级，大小写不敏感，空值落到普通色。
        check(PtcgLiveTheme.rarityColor("SR") == PtcgLiveTheme.rarityColor("ur"), "SR 与 UR 同一档");
        check(PtcgLiveTheme.rarityColor("RR") == PtcgLiveTheme.rarityColor("ex"), "RR 与 EX 同一档");
        check(PtcgLiveTheme.rarityColor("稀有") == PtcgLiveTheme.rarityColor("R"), "稀有与 R 同一档");
        check(PtcgLiveTheme.rarityColor("非凡") == PtcgLiveTheme.rarityColor("U"), "非凡与 U 同一档");
        int common = PtcgLiveTheme.rarityColor("C");
        check(PtcgLiveTheme.rarityColor("") == common && PtcgLiveTheme.rarityColor(null) == common, "空稀有度应为普通色");
        check(PtcgLiveTheme.rarityColor("SR") != PtcgLiveTheme.rarityColor("RR")
            && PtcgLiveTheme.rarityColor("RR") != PtcgLiveTheme.rarityColor("R")
            && PtcgLiveTheme.rarityColor("R") != PtcgLiveTheme.rarityColor("U")
            && PtcgLiveTheme.rarityColor("U") != common, "五档颜色应互不相同");

        // 能量圆点：数据里的 {R} 符号、中文、英文卡名都认成同一属性；认不出的特殊能量是浅灰。
        check(PtcgLiveTheme.energyColor("基本{R}能量") == PtcgLiveTheme.energyColor("基本火能量")
            && PtcgLiveTheme.energyColor("基本火能量") == PtcgLiveTheme.energyColor("Basic Fire Energy"), "火能量三种写法应同色");
        check(PtcgLiveTheme.energyColor("基本{W}能量") != PtcgLiveTheme.energyColor("基本{G}能量"), "水与草应不同色");
        check(PtcgLiveTheme.energyColor("双重涡轮能量") == PtcgLiveTheme.energyColor(null), "认不出的能量应落到浅灰");

        // 尺寸常量：宽高比在合理范围，内缩不会吃掉最小卡面。
        check(PtcgLiveTheme.CARD_ASPECT > 0.7f && PtcgLiveTheme.CARD_ASPECT < 0.73f, "卡片宽高比应接近 63:88");
        check(PtcgLiveTheme.CARD_HEIGHT_PER_WIDTH > 1.38 && PtcgLiveTheme.CARD_HEIGHT_PER_WIDTH < 1.41, "卡片高宽比应接近 88:63");
        check(PtcgLiveTheme.CARD_INSET >= 0 && PtcgLiveTheme.CARD_INSET * 2 < 16, "内缩不能吃掉 16 像素的最小卡面");
        System.out.println("PtcgLiveThemeTest passed: fit keeps aspect and stays inside the box; rarity colors form five distinct tiers");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
