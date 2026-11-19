package dev.hua.ptcglive.client;

import dev.hua.ptcglive.engine.MatchSession;
import dev.hua.ptcglive.engine.MatchPhase;
import dev.hua.ptcglive.engine.field.SpecialCondition;
import dev.hua.ptcglive.network.MatchIntentPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import java.util.*;
import static dev.hua.ptcglive.client.PtcgLiveTheme.*;

/** A compact, paginated Chinese battle panel driven exclusively by recipient-safe server snapshots. */
public final class MatchScreen extends Screen implements MatchDisplay {
    private MatchSession.Snapshot snapshot;
    private String message = "";
    private int page;
    private int scroll, maxScroll;
    private boolean confirmConcede;
    private boolean unavailable;
    private int refreshTicks;
    private long acknowledgedPresentationRevision=-1;
    public MatchScreen(MatchSession.Snapshot snapshot) { super(Text.literal("宝可梦卡牌对局")); this.snapshot = snapshot; }
    public UUID matchId() { return snapshot.game().matchId(); }
    public void update(MatchSession.Snapshot next, String message) {
        if (!unavailable && next != null && next.equals(snapshot) && Objects.equals(message, this.message)) {
            refreshTicks = 0; return; // periodic unchanged refresh must not reset a pending concede confirmation
        }
        unavailable = next == null; refreshTicks = 0;
        if (next != null) {
            if (next.revision() != snapshot.revision() || !Objects.equals(next.game().viewingSeat(), snapshot.game().viewingSeat())) page = 0;
            snapshot = next;
        }
        this.message = message; confirmConcede = false; clearAndInit();
    }
    private int perPage() { return Math.max(3, (height - 96) / 22); }
    @Override protected void init() {
        if (unavailable) {
            addDrawableChild(ButtonWidget.builder(Text.literal("关闭面板"), b -> close()).dimensions(width / 2 - 45, height - 24, 90, 20).build());
            return;
        }
        int left = width / 2 + 8, buttonWidth = Math.max(100, width / 2 - 20);
        int pages = Math.max(1, (snapshot.options().size() + perPage() - 1) / perPage());
        page = Math.min(page, pages - 1);
        for (int i = page * perPage(); i < Math.min(snapshot.options().size(), (page + 1) * perPage()); i++) {
            var option = snapshot.options().get(i);
            String label = option.labelZh();
            ButtonWidget button = ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(label, buttonWidth - 8)), b -> {
                if (label.startsWith("认输") && !confirmConcede) {
                    confirmConcede = true; b.setMessage(Text.literal("再次点击确认认输")); return;
                }
                send(option.index(), snapshot.game().viewingSeat());
            }).dimensions(left, 47 + (i % perPage()) * 22, buttonWidth, 20).build();
            button.setTooltip(Tooltip.of(Text.literal(label))); addDrawableChild(button);
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("上一页"), b -> { page = Math.max(0, page - 1); clearAndInit(); })
            .dimensions(left, height - 46, buttonWidth / 2 - 2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("下一页"), b -> { page = Math.min(pages - 1, page + 1); clearAndInit(); })
            .dimensions(left + buttonWidth / 2 + 2, height - 46, buttonWidth / 2 - 2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("刷新"), b -> send(-1, snapshot.game().viewingSeat()))
            .dimensions(10, height - 24, 60, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("区域与卡面"), b -> client.setScreen(new MatchRegionScreen(this, snapshot)))
            .dimensions(10, height - 46, Math.max(100, width / 2 - 20), 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("卡桌拖放"), b -> client.setScreen(new MatchTableScreen(this, snapshot)))
            .dimensions(10, height - 68, Math.max(100, width / 2 - 20), 20).build());
        if (snapshot.practice() && snapshot.controllableSeats().size() == 2) addDrawableChild(ButtonWidget.builder(Text.literal("切换操控方"), b -> {
            UUID current = snapshot.game().viewingSeat();
            UUID next = snapshot.controllableSeats().stream().filter(s -> !s.equals(current)).findFirst().orElseThrow(); send(-1, next);
        }).dimensions(74, height - 24, 94, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("关闭面板"), b -> close()).dimensions(width - 92, height - 24, 82, 20).build());
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        if (unavailable) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("对局已结束或不可访问，请重新打开卡桌。"), width / 2, height / 2, ERROR);
            super.render(context, mouseX, mouseY, delta); return;
        }
        String caption = snapshot.practice() ? "单人自打／练习（不计赛事或奖励）" : snapshot.game().viewingSeat() == null ? "观战（公开信息）" : "双人对局";
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(caption), width / 2, 9, TEXT);
        String phase = phaseName(snapshot.game().phase()) + " · 第 " + snapshot.game().turnNumber() + " 回合";
        context.drawTextWithShadow(textRenderer, phase, 10, 28, ACCENT);
        context.drawTextWithShadow(textRenderer, "操作 " + (page + 1) + "/" + Math.max(1, (snapshot.options().size() + perPage() - 1) / perPage()), width / 2 + 8, 28, ACCENT);
        int y = 47 - scroll, maxWidth = width / 2 - 20;
        List<String> lines = new ArrayList<>();
        var game = snapshot.game();
        lines.add("共用场地：" + (game.stadium() == null ? "无" : name(game.stadium().definitionId())));
        for (var setup : game.setupPlayers()) {
            int seat = game.players().stream().map(p -> p.id()).toList().indexOf(setup.id()) + 1;
            lines.add("席位 " + seat + " · 重抽 " + setup.mulligans() + " 次" + (setup.needsMulligan() ? "（无基础宝可梦，等待重抽）" : ""));
        }
        for (var reveal : game.mulliganReveals()) {
            int seat = game.players().stream().map(p -> p.id()).toList().indexOf(reveal.playerId()) + 1;
            lines.add("席位 " + seat + " 第 " + reveal.attempt() + " 次重抽展示：");
            lines.add(String.join("、", reveal.cardIds().stream().map(this::name).toList()));
        }
        for (int i = 0; i < game.players().size(); i++) {
            var p = game.players().get(i);
            String who = p.id().equals(game.viewingSeat()) ? "我方" : "席位 " + (i + 1);
            lines.add(who + (p.id().equals(game.currentPlayer()) ? "（当前回合）" : ""));
            lines.add("手牌 " + p.handSize() + " · 牌库 " + p.deckSize() + " · 奖赏 " + p.prizeCount() + " · 放逐 " + p.lostZone().size());
            if (p.active() == null) lines.add("战斗位：未公开或等待替补");
            else {
                var a = p.active();
                lines.add("战斗位：" + name(a.topCardId()));
                lines.add("体力 " + a.hitPoints() + " · 伤害 " + a.damage() + " · 能量 " + a.attachedEnergyIds().size());
                if (!a.attachedTools().isEmpty()) lines.add("工具：" + String.join("、", a.attachedTools().stream().map(c -> name(c.definitionId())).toList()));
                if (!a.conditions().isEmpty()) lines.add("状态：" + String.join("、", a.conditions().stream().map(MatchScreen::condition).toList()));
                if (!a.continuousEffectZh().isBlank()) lines.add(a.continuousEffectZh());
            }
            lines.add("备战：" + (p.bench().isEmpty() ? "无" : String.join("、", p.bench().stream().map(b -> name(b.topCardId())).toList())));
            lines.add("弃牌：" + p.discard().size() + " 张");
            if (!p.lostZone().isEmpty()) lines.add("放逐：" + String.join("、", p.lostZone().stream().map(c -> name(c.definitionId())).toList()));
        }
        if (game.viewingSeat() != null) lines.add("我的手牌：" + String.join("、", game.hand().stream().map(c -> name(c.definitionId())).toList()));
        if (game.result() != null) lines.add(game.result().winner() == null ? "本局平局；已由服务器按规则记录" : "本局已结束；卡盒已解锁");
        context.enableScissor(8, 44, width / 2 - 4, height - 72);
        for (String line : lines) for (var wrapped : textRenderer.wrapLines(Text.literal(line), maxWidth)) {
            context.drawTextWithShadow(textRenderer, wrapped, 10, y, TEXT_BODY); y += 11;
        }
        maxScroll = Math.max(0, y + scroll - (height - 72));
        scroll = Math.min(scroll, maxScroll);
        context.disableScissor();
        if (!message.isBlank()) context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(message, maxWidth), 10, height - 64, ERROR);
        super.render(context, mouseX, mouseY, delta);
    }
    /** This is an in-world overlay, never a blurred pause/menu screen. */
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }
    private String name(String id) { return snapshot.cardNames().getOrDefault(id, id); }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX < width / 2.0) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (verticalAmount * 22)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    private static String condition(SpecialCondition condition) { return switch (condition) {
        case POISONED -> "中毒"; case BURNED -> "灼伤"; case ASLEEP -> "睡眠"; case PARALYZED -> "麻痹"; case CONFUSED -> "混乱";
    }; }
    private static String phaseName(MatchPhase phase) { return switch (phase) {
        case MULLIGAN_REVEAL -> "展示重抽手牌"; case MULLIGAN_DRAW -> "重抽补偿"; case MULLIGAN_BENCH -> "补抽宝可梦备战";
        case COIN_CALL -> "猜硬币"; case TURN_ORDER -> "选择先后手";
        case OPENING_POKEMON -> "开局设置"; case TURN -> "行动阶段"; case PRIZE_SELECTION -> "选择奖赏卡";
        case CHOOSE_REPLACEMENT -> "选择替补"; case POKEMON_CHECKUP -> "回合间检查"; case TURN_END -> "回合末结算"; case FINISHED -> "对局结束";
    }; }
    private void send(int option, UUID seat) {
        ClientPlayNetworking.send(new MatchIntentPayload(matchId().toString(), seat == null ? "" : seat.toString(), Math.toIntExact(snapshot.revision()), option));
    }
    @Override public void tick() {
        // This text-only fallback has no finite battle animations to await.
        if(!unavailable&&snapshot.computer()!=null&&snapshot.revision()!=acknowledgedPresentationRevision
            &&client!=null&&client.getNetworkHandler()!=null){
            send(MatchIntentPayload.PRESENTATION_FINISHED,snapshot.game().viewingSeat());
            acknowledgedPresentationRevision=snapshot.revision();
        }
        if (!unavailable && ++refreshTicks >= 40) { refreshTicks = 0; send(-1, snapshot.game().viewingSeat()); }
        super.tick();
    }
    @Override public void close() {
        if (client != null && client.getNetworkHandler() != null) send(-2, snapshot.game().viewingSeat());
        super.close();
    }
    @Override public boolean shouldPause() { return false; }
}
