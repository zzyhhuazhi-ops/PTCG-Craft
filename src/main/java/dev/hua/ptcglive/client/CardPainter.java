package dev.hua.ptcglive.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import static dev.hua.ptcglive.client.PtcgLiveTheme.*;

/**
 * 画一张卡、一张卡背、一块面板。只负责绘制：不读对局快照、不发网络包，
 * 边框颜色由调用方根据自己的规则算好后传入。
 */
final class CardPainter {
    static void ghostCard(DrawContext context, TextRenderer font, CardArt art, String name,
                          int x, int y, int width, int height) {
        context.setShaderColor(1, 1, 1, 0.35f);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        try { slotCard(context, font, art, name, x, y, width, height); }
        finally { context.setShaderColor(1, 1, 1, 1); com.mojang.blaze3d.systems.RenderSystem.disableBlend(); }
    }
    /** 底色 + 边框 + 等比居中的卡图；art 为 null 时写卡名和“卡图不可用”。 */
    static void card(DrawContext context, TextRenderer font, CardArt art, String nameZh,
                     int x, int y, int width, int height, int border) {
        context.fill(x, y, x + width, y + height, CARD_BACKDROP);
        context.drawBorder(x, y, width, height, border);
        if (art == null) { missing(context, font, nameZh, x, y, width, height); return; }
        art(context, art, x + CARD_INSET, y + CARD_INSET, width - 2 * CARD_INSET, height - 2 * CARD_INSET);
    }

    /** 只画等比居中的卡图，不画底色和边框。 */
    static void art(DrawContext context, CardArt art, int x, int y, int width, int height) {
        var fit = fit(art.width(), art.height(), x, y, width, height);
        context.drawTexture(art.texture(), fit.x(), fit.y(), 0, 0, fit.width(), fit.height(), fit.width(), fit.height());
    }

    /** 隐藏卡牌 / 暂无卡图：蓝底淡金边加一行字。 */
    static void back(DrawContext context, TextRenderer font, String label, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, CARD_HIDDEN);
        context.drawBorder(x, y, width, height, ACCENT_SOFT);
        context.drawCenteredTextWithShadow(font, Text.literal(label), x + width / 2, y + height / 2, TEXT_BODY);
    }

    /** 面板：只画边框和居中标题，背景保持全透明。 */
    static void panel(DrawContext context, TextRenderer font, String titleZh, int x, int y, int width, int height) {
        context.drawBorder(x, y, width, height, PANEL_BORDER);
        if (titleZh != null && !titleZh.isBlank())
            context.drawCenteredTextWithShadow(font, Text.literal(titleZh), x + width / 2, y + 5, ACCENT);
    }

    /** 面板：边框、左上角标题、右上角附注（灰色，放不下时截断）。背景保持全透明。 */
    static void panel(DrawContext context, TextRenderer font, String titleZh, String info, int x, int y, int width, int height, int border) {
        context.drawBorder(x, y, width, height, border);
        int titleWidth = 0;
        if (titleZh != null && !titleZh.isBlank()) {
            context.drawTextWithShadow(font, titleZh, x + 6, y + 5, ACCENT);
            titleWidth = font.getWidth(titleZh);
        }
        if (info != null && !info.isBlank()) {
            String fitted = font.trimToWidth(info, Math.max(0, width - 12 - titleWidth - 8));
            context.drawTextWithShadow(font, fitted, x + width - 6 - font.getWidth(fitted), y + 5, TEXT_DIM);
        }
    }

    /** 卡图右上角的“×张数”角标。 */
    static void countBadge(DrawContext context, TextRenderer font, int count, int x, int y, int cardWidth) {
        String label = "×" + count;
        int badgeWidth = Math.min(cardWidth, font.getWidth(label) + 4), left = x + cardWidth - badgeWidth;
        context.fill(left, y, x + cardWidth, y + 11, BADGE_FILL);
        context.drawText(font, label, left + 2, y + 2, BADGE_TEXT, false);
    }

    /** 原版容器窗口：黑色描边（四角缺角）、左上 2 像素白色高光、右下 2 像素深灰阴影、浅灰底。 */
    static void vanillaWindow(DrawContext context, int x, int y, int width, int height) {
        context.fill(x + 1, y, x + width - 1, y + height, VANILLA_OUTLINE);
        context.fill(x, y + 1, x + width, y + height - 1, VANILLA_OUTLINE);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, VANILLA_PANEL);
        context.fill(x + 1, y + 1, x + width - 2, y + 3, VANILLA_HIGHLIGHT);
        context.fill(x + 1, y + 1, x + 3, y + height - 2, VANILLA_HIGHLIGHT);
        context.fill(x + 2, y + height - 3, x + width - 1, y + height - 1, VANILLA_SHADOW);
        context.fill(x + width - 3, y + 2, x + width - 1, y + height - 1, VANILLA_SHADOW);
        context.fill(x + 3, y + 3, x + width - 3, y + height - 3, VANILLA_PANEL);
    }

    /** 凹陷区域：左上 1 像素深色、右下 1 像素亮色，中间填 fill。格子和文字区都用它。 */
    static void sunken(DrawContext context, int x, int y, int width, int height, int fill) {
        context.fill(x, y, x + width, y + height, fill);
        context.fill(x, y, x + width - 1, y + 1, SLOT_SHADOW);
        context.fill(x, y, x + 1, y + height - 1, SLOT_SHADOW);
        context.fill(x + 1, y + height - 1, x + width, y + height, SLOT_HIGHLIGHT);
        context.fill(x + width - 1, y + 1, x + width, y + height, SLOT_HIGHLIGHT);
    }

    /** 原版格子。 */
    static void slot(DrawContext context, int x, int y, int width, int height) { sunken(context, x, y, width, height, SLOT_FILL); }

    /** 格子里的卡：有卡图就铺满格子内部；没有就画带卡名的回退卡面。 */
    static void slotCard(DrawContext context, TextRenderer font, CardArt art, String nameZh, int x, int y, int width, int height) {
        if (art != null) art(context, art, x, y, width, height);
        else card(context, font, null, nameZh, x, y, width, height, CARD_BORDER);
    }

    /** 原版物品堆叠的张数：右下角白字带阴影，1 张时不显示。 */
    static void stackCount(DrawContext context, TextRenderer font, int count, int x, int y, int width, int height) {
        if (count <= 1) return;
        String label = Integer.toString(count);
        context.drawText(font, label, x + width - 1 - font.getWidth(label), y + height - 9, TEXT, true);
    }

    /** 竖直刻线：左深右亮，用作两栏之间的分隔。 */
    static void groove(DrawContext context, int x, int top, int bottom) {
        context.fill(x, top, x + 1, bottom, VANILLA_SHADOW);
        context.fill(x + 1, top, x + 2, bottom, VANILLA_HIGHLIGHT);
    }

    /** 原版窗口上的标签文字：不带阴影。 */
    static void label(DrawContext context, TextRenderer font, String text, int x, int y, int color) {
        context.drawText(font, text, x, y, color, false);
    }

    /** All concealed cards share the selected physical card back. */
    static void cardBack(DrawContext context,int x,int y,int width,int height){
        BattleCosmetics.draw(context,BattleCosmetics.cardBack(),x,y,width,height);
    }

    /** 玩家头像：在线玩家用其皮肤，离线或未知玩家用按 UUID 决定的原版默认皮肤。 */
    static void playerHead(DrawContext context, java.util.UUID player, int x, int y, int size) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        var handler = client.getNetworkHandler();
        var entry = handler == null ? null : handler.getPlayerListEntry(player);
        var skin = entry != null ? entry.getSkinTextures() : net.minecraft.client.util.DefaultSkinHelper.getSkinTextures(player);
        net.minecraft.client.gui.PlayerSkinDrawer.draw(context, skin, x, y, size);
    }

    /** 列表选中行：灰色外框、黑色内底，与原版世界列表一致。 */
    static void selectedRow(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, LIST_SELECTED);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, TEXT_AREA);
    }

    /** 竖直滚动条：放在列表右侧 5 像素宽的槽里。 */
    static void scrollbar(DrawContext context, int x, int top, int bottom, int first, int visible, int total) {
        if (total <= visible) return;
        int height = bottom - top, thumb = Math.max(8, height * visible / total);
        int thumbTop = top + (height - thumb) * first / Math.max(1, total - visible);
        context.fill(x, top, x + 5, bottom, TEXT_AREA);
        context.fill(x, thumbTop, x + 5, thumbTop + thumb, LIST_SELECTED);
        context.fill(x, thumbTop, x + 4, thumbTop + thumb - 1, 0xffc0c0c0);
    }

    // ---- 对战桌（PTCG Live 红蓝桌垫）----

    /** 对战桌贴图：用户画的伤害指示物、异常状态、GX/VSTAR 标记，都在 textures/gui/match/。 */
    private static net.minecraft.util.Identifier matchTexture(String name) {
        return net.minecraft.util.Identifier.of(dev.hua.ptcglive.CardItems.MOD_ID, "textures/gui/match/" + name + ".png");
    }

    /** 整张像素贴图放大 scale 倍画到 (x, y)。 */
    private static void sprite(DrawContext context, String name, int x, int y, int width, int height, int scale) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        context.drawTexture(matchTexture(name), x, y, width * scale, height * scale, 0, 0, width, height, width, height);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    /** 放大 scale 倍写字（scale 为 1 时就是普通 drawText）。 */
    static void text(DrawContext context, TextRenderer font, String text, int x, int y, int color, boolean shadow, int scale) {
        if (scale == 1) { context.drawText(font, text, x, y, color, shadow); return; }
        var matrices = context.getMatrices();
        matrices.push(); matrices.translate(x, y, 0); matrices.scale(scale, scale, 1);
        context.drawText(font, text, 0, 0, color, shadow);
        matrices.pop();
    }

    static void centeredText(DrawContext context, TextRenderer font, String text, int centerX, int y, int color, boolean shadow, int scale) {
        text(context, font, text, centerX - font.getWidth(text) * scale / 2, y, color, shadow, scale);
    }

    /** 1 像素圆角的实心矩形：三块互不重叠，半透明颜色也不会出现深色接缝。 */
    static void roundRect(DrawContext context, int x, int y, int width, int height, int color) {
        if (width < 3 || height < 3) { context.fill(x, y, x + width, y + height, color); return; }
        context.fill(x + 1, y, x + width - 1, y + 1, color);
        context.fill(x, y + 1, x + width, y + height - 1, color);
        context.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
    }

    static void roundBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x + 1, y, x + width - 1, y + 1, color);
        context.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    /** 卡位托盘：深色半透明底，edge 为 0 时不描边。 */
    static void tray(DrawContext context, PtcgLiveTableLayout.Rect area, int edge) {
        roundRect(context, area.x(), area.y(), area.width(), area.height(), TRAY);
        if (edge != 0) roundBorder(context, area.x(), area.y(), area.width(), area.height(), edge);
    }

    /** 空卡位：更深的底、淡白描边，放得下时中间写一个灰色标签（如“放逐”）。 */
    static void emptySlot(DrawContext context, TextRenderer font, String label, int x, int y, int width, int height, int scale) {
        roundRect(context, x, y, width, height, SLOT_EMPTY);
        roundBorder(context, x, y, width, height, SLOT_EMPTY_LINE);
        if (label != null && !label.isEmpty() && font.getWidth(label) * scale <= width - 2)
            centeredText(context, font, label, x + width / 2, y + height / 2 - 4 * scale, TEXT_DIM, true, scale);
    }

    /** 卡框换色并在外面加一圈半透明光晕：青＝可操作，金＝可放下或可选目标，绿＝已选。 */
    static void glow(DrawContext context, int x, int y, int width, int height, int color) {
        roundBorder(context, x - 1, y - 1, width + 2, height + 2, (color & 0xffffff) | 0x90000000);
        context.drawBorder(x, y, width, height, color);
    }

    /**
     * 方案 B 红蓝桌垫：上半对方红、下半我方蓝（以中线为界），中间一块八边形深色对战区。
     * 全是 fill，用 draw(Runnable) 合成一次提交，不然八边形的斜边每行一次绘制。
     */
    static void tableMat(DrawContext context, PtcgLiveTableLayout layout, int arenaEdge) {
        var arena = layout.arena();
        int cut = arena.height() / 4;
        context.draw(() -> {
            context.fill(0, 0, layout.screenWidth(), layout.centerY(), MAT_OPPONENT);
            context.fill(0, layout.centerY(), layout.screenWidth(), layout.screenHeight(), MAT_OWN);
            for (int y = arena.y(); y < arena.bottom(); y++) {
                int inset = Math.max(0, Math.max(cut - (y - arena.y()), cut - (arena.bottom() - 1 - y)));
                context.fill(arena.x() + inset, y, arena.right() - inset, y + 1, ARENA);
                context.fill(arena.x() + inset - 1, y, arena.x() + inset, y + 1, arenaEdge);
                context.fill(arena.right() - inset, y, arena.right() - inset + 1, y + 1, arenaEdge);
            }
            context.fill(arena.x() + cut, arena.y() - 1, arena.right() - cut, arena.y(), arenaEdge);
            context.fill(arena.x() + cut, arena.bottom(), arena.right() - cut, arena.bottom() + 1, arenaEdge);
        });
    }

    /** 卡面右上角的剩余 HP：白底深字，受过伤时红字。right/top 是卡面内的右上角。 */
    static void hpPill(DrawContext context, TextRenderer font, int remaining, boolean hurt, int right, int top, int scale) {
        String label = Integer.toString(remaining);
        int width = (font.getWidth(label) + 3) * scale;
        roundRect(context, right - width, top, width, 10 * scale, HP_PILL);
        text(context, font, label, right - width + 2 * scale, top + scale, hurt ? HP_HURT : HP_TEXT, false, scale);
    }

    /** 伤害指示物：用户画的橙色底座放大 2 倍，数字贴图 1 倍叠在中间（相邻数字共用 1 像素描边）。 */
    static void damageBadge(DrawContext context, int damage, int centerX, int centerY, int scale) {
        sprite(context, "damage_base", centerX - 9 * scale, centerY - 9 * scale, 9, 9, 2 * scale);
        String digits = Integer.toString(Math.max(0, damage));
        int width = (7 + 6 * (digits.length() - 1)) * scale, x = centerX - width / 2;
        for (int i = 0; i < digits.length(); i++)
            sprite(context, "damage_digit_" + digits.charAt(i), x + 6 * scale * i, centerY - 4 * scale, 7, 9, scale);
    }

    /** 纯色能量圆点（正式能量图标之前的占位）：深色描边、纯色填充，直径 diameter。 */
    static void energyOrb(DrawContext context, int x, int y, int diameter, int rgb) {
        double radius = diameter / 2.0, inner = radius - 1;
        for (int dy = 0; dy < diameter; dy++) {
            double row = dy + 0.5 - radius;
            int outer = (int) Math.floor(radius - Math.sqrt(Math.max(0, radius * radius - row * row)) + 0.5);
            context.fill(x + outer, y + dy, x + diameter - outer, y + dy + 1, ORB_OUTLINE);
            if (dy == 0 || dy == diameter - 1) continue;
            int fill = (int) Math.floor(radius - Math.sqrt(Math.max(0, inner * inner - row * row)) + 0.5);
            if (diameter - fill > fill) context.fill(x + fill, y + dy, x + diameter - fill, y + dy + 1, 0xff000000 | rgb);
        }
    }

    /** 异常状态标记（11×11）：poison / burn / asleep / paralyzed / confused。 */
    static void statusIcon(DrawContext context, String condition, int x, int y, int scale) {
        sprite(context, "status_" + condition, x, y, 11, 11, scale);
    }

    /** GX / VSTAR 标记（19×11）：未用是彩色正面，已用是灰色背面。 */
    static void plate(DrawContext context, boolean vstar, boolean used, int x, int y, int scale) {
        sprite(context, (vstar ? "vstar" : "gx") + (used ? "_back" : "_front"), x, y, 19, 11, scale);
    }

    /** 牌堆右下角的张数标签（深底白字）。right/bottom 是牌堆卡面的右下角。 */
    static void countTab(DrawContext context, TextRenderer font, int count, int right, int bottom, int scale) {
        String label = Integer.toString(Math.max(0, count));
        int width = (font.getWidth(label) + 4) * scale;
        roundRect(context, right - width, bottom - 10 * scale, width, 10 * scale, COUNT_TAB);
        text(context, font, label, right - width + 2 * scale, bottom - 9 * scale, TEXT, true, scale);
    }

    /** 左右尖的六边形“结束回合”（两行字，与 PTCG Live 的 END / TURN 一致）。 */
    static void endTurnHex(DrawContext context, TextRenderer font, int x, int y, int width, int height,
                           boolean enabled, boolean hovered, int scale) {
        int fill = enabled ? END_TURN : END_TURN_DISABLED;
        int edge = hovered && enabled ? TABLE_BUTTON_HOVER : enabled ? END_TURN_EDGE : END_TURN_DISABLED_EDGE;
        int half = height / 2, tip = Math.max(4, height / 4);
        context.draw(() -> {
            for (int dy = 0; dy < height; dy++) {
                int inset = (int) (tip * Math.abs(dy - half + 0.5) / half);
                context.fill(x + inset, y + dy, x + width - inset, y + dy + 1, edge);
                if (dy > 0 && dy < height - 1) context.fill(x + inset + 1, y + dy, x + width - inset - 1, y + dy + 1, fill);
            }
        });
        int color = enabled ? END_TURN_TEXT : TABLE_BUTTON_DISABLED_TEXT;
        centeredText(context, font, "结束", x + width / 2, y + half - 9 * scale, color, false, scale);
        centeredText(context, font, "回合", x + width / 2, y + half + scale, color, false, scale);
    }

    /** 对战桌按钮：深底灰边（主要按钮金底），悬停白边，不可用时变灰。文字放不下时截断。 */
    static void tableButton(DrawContext context, TextRenderer font, String label, int x, int y, int width, int height,
                            boolean primary, boolean enabled, boolean hovered, int scale) {
        int fill = !enabled ? TABLE_BUTTON_DISABLED : primary ? END_TURN : TABLE_BUTTON;
        int edge = hovered && enabled ? TABLE_BUTTON_HOVER : primary && enabled ? END_TURN_EDGE : TABLE_BUTTON_EDGE;
        roundRect(context, x, y, width, height, fill);
        roundBorder(context, x, y, width, height, edge);
        String fitted = font.trimToWidth(label, Math.max(0, (width - 6) / scale));
        int color = !enabled ? TABLE_BUTTON_DISABLED_TEXT : primary ? END_TURN_TEXT : TEXT;
        centeredText(context, font, fitted, x + width / 2, y + (height - 8 * scale) / 2, color, enabled && !primary, scale);
    }

    /** 缺图时的中文回退：卡名居中，够高时再写一行“卡图不可用”。 */
    private static void missing(DrawContext context, TextRenderer font, String nameZh, int x, int y, int width, int height) {
        String title = font.trimToWidth(nameZh == null ? "" : nameZh, Math.max(12, width - 4));
        context.drawCenteredTextWithShadow(font, Text.literal(title), x + width / 2, y + Math.max(3, height / 2 - 6), TEXT);
        if (height >= 44) context.drawCenteredTextWithShadow(font, Text.literal("卡图暂未显示"), x + width / 2, y + height / 2 + 5, TEXT_DIM);
    }

    private CardPainter() { }
}
