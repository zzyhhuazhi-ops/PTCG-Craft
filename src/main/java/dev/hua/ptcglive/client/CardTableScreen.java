package dev.hua.ptcglive.client;

import com.google.gson.Gson;
import dev.hua.ptcglive.network.TableIntentPayload;
import dev.hua.ptcglive.table.TableLobbyView;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import static dev.hua.ptcglive.client.PtcgLiveLobbyLayout.*;
import static dev.hua.ptcglive.client.PtcgLiveTheme.*;

/**
 * Card-table lobby drawn as a vanilla container window: two seats (own seat at the
 * bottom), read-only current rules on the left, spectators on the right, a settings
 * page and two dialogs (单人练习, 对局记录).  It renders only the recipient-specific
 * {@link TableLobbyView.Lobby} sent by the server and sends {@link TableIntentPayload}s;
 * every action is validated by the server.
 */
public final class CardTableScreen extends Screen {
    private enum Mode { LOBBY, SETTINGS, PRACTICE, HISTORY }
    private static final Gson JSON = new Gson();
    private static final int POLL_TICKS = 40, MESSAGE_TICKS = 100;
    private static final int KEY_ESCAPE = 256;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final BlockPos tablePos;
    private TableLobbyView.Lobby lobby;
    private TableLobbyView.History history;
    private Mode mode = Mode.LOBBY;
    private String message = "正在向服务器读取卡桌大厅…";
    private boolean success = true, opened, deckListOpen, confirmEnd;
    private int messageTicks = MESSAGE_TICKS, pollTicks;
    /** Right edge of the footer hint (left edge of the right-hand footer buttons), set in init. */
    private int footerHintRight;
    // settings page
    private String selectedRule;
    private int ruleScroll;
    // practice dialog
    private String practiceFirst = "", practiceSecond = "";
    // history dialog
    private int historySelected, logScroll;

    public CardTableScreen(BlockPos tablePos) {
        super(Text.literal("宝可梦卡牌对战桌")); this.tablePos = tablePos;
    }
    public BlockPos tablePos() { return tablePos; }

    // ---- server replies ----
    /** A lobby view (empty JSON when the request failed before the table could be read) and the request outcome. */
    public void applyLobby(boolean success, String messageZh, String snapshotJson) {
        TableLobbyView.Lobby next = snapshotJson == null || snapshotJson.isBlank() ? lobby : JSON.fromJson(snapshotJson, TableLobbyView.Lobby.class);
        if (messageZh != null && !messageZh.isBlank()) { message = messageZh; this.success = success; messageTicks = MESSAGE_TICKS; }
        boolean changed = !Objects.equals(next, lobby);
        lobby = next;
        if (lobby != null && !lobby.exists() && mode != Mode.LOBBY && mode != Mode.HISTORY) mode = Mode.LOBBY;
        if (changed) { confirmEnd = false; clearAndInit(); }
    }
    public void applyHistory(String historyJson) {
        history = JSON.fromJson(historyJson, TableLobbyView.History.class);
        historySelected = 0; logScroll = 0; mode = Mode.HISTORY; deckListOpen = false; clearAndInit();
    }

    // ---- helpers over the lobby view ----
    private PtcgLiveLobbyLayout layout() { return PtcgLiveLobbyLayout.forScreen(width, height); }
    private boolean ready() { return lobby != null && lobby.exists(); }
    private boolean isHost() { return ready() && "HOST".equals(lobby.role()); }
    private boolean isGuest() { return ready() && "GUEST".equals(lobby.role()); }
    private boolean seated() { return isHost() || isGuest(); }
    private boolean spectating() { return ready() && "SPECTATOR".equals(lobby.role()); }
    private boolean waiting() { return ready() && "WAITING".equals(lobby.status()); }
    /** Seat shown at {@code index} (0 upper, 1 lower): the viewer's own seat is always the lower one. */
    private TableLobbyView.Seat seatAt(int index) {
        boolean hostBelow = !isGuest();
        return (index == 1) == hostBelow ? lobby.host() : lobby.guest();
    }
    private boolean seatIsHost(int index) { return (index == 1) == !isGuest(); }
    private boolean ownSeat(int index) { return seated() && index == 1; }
    private boolean ownReady() { return seated() && (isHost() ? lobby.host().ready() : lobby.guest() != null && lobby.guest().ready()); }
    private TableLobbyView.Box ownBox() {
        if (!ready() || lobby.myBoxId().isBlank()) return null;
        return lobby.myBoxes().stream().filter(box -> box.id().equals(lobby.myBoxId())).findFirst().orElse(null);
    }
    private TableLobbyView.Box boxById(String id) {
        return lobby == null ? null : lobby.myBoxes().stream().filter(box -> box.id().equals(id)).findFirst().orElse(null);
    }
    private void send(String action) { send(action, ""); }
    private void send(String action, String argument) { ClientPlayNetworking.send(new TableIntentPayload(tablePos, action, argument)); }
    private void setMode(Mode next) { mode = next; deckListOpen = false; confirmEnd = false; clearAndInit(); }

    // ---- widgets ----
    @Override protected void init() {
        if (!opened) { opened = true; send("OPEN"); }
        var layout = layout();
        switch (mode) {
            case LOBBY -> initLobby(layout);
            case SETTINGS -> initSettings(layout);
            case PRACTICE -> initPractice(layout);
            case HISTORY -> initHistory(layout);
        }
    }
    private ButtonWidget button(String label, String tooltip, int x, int y, int w, boolean active, Runnable action) {
        var button = ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(label, w - 6)), b -> action.run()).dimensions(x, y, w, BUTTON_HEIGHT).build();
        if (tooltip != null) button.setTooltip(Tooltip.of(Text.literal(tooltip)));
        button.active = active;
        return addDrawableChild(button);
    }
    private void initLobby(PtcgLiveLobbyLayout layout) {
        button("设置", "规则包、赛制与结束卡桌", layout.settingsX(), layout.barY(), SETTINGS_WIDTH, ready(), () -> {
            selectedRule = lobby.rule().id(); ruleScroll = -1; setMode(Mode.SETTINGS);
        });
        button("×", "关闭", layout.closeX(), layout.barY(), CLOSE_WIDTH, true, this::close);
        if(!dev.hua.ptcglive.duel.DuelLetters.virtualPosition(tablePos))button("邀请", "在线玩家邀请、接受与取消",layout.settingsX()-62,layout.barY(),56,true,()->BattleSocialScreen.open(this,tablePos));
        footerHintRight = layout.innerRight();
        if (lobby == null) return;
        if (!lobby.exists()) {
            footerHintRight = layout.innerRight() - 64 - GAP;
            button("重新开桌", "以你为房主开一张新桌", layout.innerRight() - 64, layout.footerY(), 64, true, () -> send("OPEN"));
            return;
        }
        // Seats: join an empty seat, own deck picker, guest may leave.
        for (int index = 0; index < 2; index++) {
            var seat = seatAt(index);
            if (seat == null) {
                if (!seated() && waiting()) {
                    int y = layout.seatY(index) + layout.seatHeight() / 2 - 2;
                    button("加入对局", "坐到这个空位", layout.seatsX() + (layout.seatsWidth() - layout.joinButtonWidth()) / 2, y,
                        layout.joinButtonWidth(), true, () -> send("JOIN"));
                }
                continue;
            }
            if (ownSeat(index) && waiting()) {
                var own = ownBox();
                String label = "卡组：" + (own == null ? "选择卡组" : own.nameZh() + "（" + own.cards() + "/60）") + " ▾";
                button(label, "选择本局使用的卡组", layout.seatTextX(), layout.deckButtonY(index), layout.deckButtonWidth(), true,
                    () -> deckListOpen = !deckListOpen);
                if (isGuest()) button("离开", "离开座位", layout.leaveX(), layout.seatY(index) + 4, LEAVE_WIDTH, true, () -> send("LEAVE"));
            }
        }
        // Spectators: the viewer can register or leave as a spectator when not seated.
        if (!seated()) {
            String label = spectating() ? "离开观战" : "观战";
            Runnable action = () -> send(spectating() ? "LEAVE" : "SPECTATE");
            if (layout.wide()) button(label, null, layout.spectatorsX(), layout.spectateButtonY(), layout.sideWidth(), true, action);
        }
        // Footer.
        int footerWidth = layout.footerButtonWidth();
        button(layout.wide() ? "对局记录" : "记录", "我最近的对局记录", layout.historyX(), layout.footerY(), footerWidth, true, () -> send("HISTORY"));
        boolean practiceAllowed = isHost() && lobby.guest() == null && waiting();
        button(layout.wide() ? "双方自控" : "自控", practiceAllowed ? "一个人操控双方，不是电脑对手" : "只有尚无对手的空闲卡桌的房主可以练习",
            layout.practiceX(), layout.footerY(), footerWidth, practiceAllowed, () -> setMode(Mode.PRACTICE));
        if(practiceAllowed){int buttonWidth=Math.min(100,layout.seatsWidth()-16);button("挑战电脑","与服务器控制的电脑对手进行真实对局",layout.seatsX()+(layout.seatsWidth()-buttonWidth)/2,layout.seatY(0)+layout.seatHeight()/2,buttonWidth,true,()->send("AI_MENU"));}
        List<ButtonSpec> actions = new ArrayList<>();
        if (lobby.matchRunning()) {
            if (seated()) actions.add(new ButtonSpec("进入对局", MATCH_WIDTH, true, () -> send("MATCH")));
            else if (spectating()) actions.add(new ButtonSpec("观看对局", MATCH_WIDTH, true, () -> send("MATCH")));
            else actions.add(new ButtonSpec("观战", READY_WIDTH, true, () -> send("SPECTATE")));
        } else if (seated() && waiting()) {
            var own = ownBox();
            boolean canReady = own != null && own.canStart();
            actions.add(ownReady() ? new ButtonSpec("取消准备", READY_WIDTH, true, () -> send("CANCEL_READY"))
                : new ButtonSpec("准备", READY_WIDTH, canReady, () -> send("READY")));
            if (isHost()) {
                if(!dev.hua.ptcglive.duel.DuelLetters.virtualPosition(tablePos))actions.add(new ButtonSpec("连接屏幕", 76, true, () -> send("CONNECT_SCREENS")));
                boolean canStart = lobby.guest() != null && lobby.host().ready() && lobby.guest().ready();
                actions.add(new ButtonSpec("开始对战", START_WIDTH, canStart, () -> send("START")));
            }
        } else if (!seated() && !layout.wide()) {
            actions.add(new ButtonSpec(spectating() ? "离开观战" : "观战", READY_WIDTH, true, () -> send(spectating() ? "LEAVE" : "SPECTATE")));
        }
        int x = layout.innerRight();
        for (int i = actions.size() - 1; i >= 0; i--) {
            var spec = actions.get(i); x -= spec.width();
            button(spec.label(), null, x, layout.footerY(), spec.width(), spec.active(), spec.action());
            x -= GAP;
        }
        footerHintRight = x;
    }
    private record ButtonSpec(String label, int width, boolean active, Runnable action) { }

    private void initSettings(PtcgLiveLobbyLayout layout) {
        button("返回", "回到大厅", layout.backX(), layout.barY(), BACK_WIDTH, true, () -> setMode(Mode.LOBBY));
        if (!ready()) return;
        int labelWidth = OPTION_LABEL_WIDTH, top = layout.optionsTop();
        button(lobby.formatZh(), "目前只有这一种赛制", layout.innerLeft() + labelWidth, top, layout.formatWidth(), false, () -> { });
        boolean canEnd = isHost() && waiting();
        button(confirmEnd ? "确认结束" : "结束卡桌", "解散本桌，双方回到空闲", layout.innerLeft() + labelWidth, top + BUTTON_HEIGHT + GAP, END_WIDTH, canEnd, () -> {
            if (!confirmEnd) { confirmEnd = true; message = "再点一次“确认结束”解散本桌"; success = true; messageTicks = MESSAGE_TICKS; clearAndInit(); }
            else { confirmEnd = false; send("END"); setMode(Mode.LOBBY); }
        });
        if (isHost()) {
            boolean changed = selectedRule != null && !selectedRule.equals(lobby.rule().id());
            button("取消", "放弃这次修改", layout.cancelX(), layout.footerY(), CANCEL_WIDTH, true, () -> { selectedRule = lobby.rule().id(); setMode(Mode.LOBBY); });
            button("应用", "把卡桌换成选中的规则包", layout.applyX(), layout.footerY(), APPLY_WIDTH, changed && waiting(), () -> {
                send("RULESET", selectedRule); setMode(Mode.LOBBY);
            });
        }
    }
    private void initPractice(PtcgLiveLobbyLayout layout) {
        var dialog = layout.practiceDialog();
        int footer = dialog.footerY();
        button("继续上次练习", "重新打开你上一局练习", dialog.innerLeft(), footer,
            Math.min(RESUME_WIDTH, practiceColumnWidth(dialog)), true, () -> send("RESUME_PRACTICE"));
        button("取消", null, dialog.innerRight() - START_PRACTICE_WIDTH - GAP - PRACTICE_CANCEL_WIDTH, footer, PRACTICE_CANCEL_WIDTH, true,
            () -> setMode(Mode.LOBBY));
        button("开始练习", null, dialog.innerRight() - START_PRACTICE_WIDTH, footer, START_PRACTICE_WIDTH, practiceProblem() == null,
            () -> send("PRACTICE", practiceFirst + "," + practiceSecond));
    }
    /** Why the practice cannot start yet, or {@code null} when it can. */
    private String practiceProblem() {
        var first = boxById(practiceFirst); var second = boxById(practiceSecond);
        if (first == null || second == null) return "请给甲乙双方各选一副卡组";
        if (practiceFirst.equals(practiceSecond)) return "甲乙双方需要两副不同的卡组";
        if (!first.canStart() || !second.canStart()) return "有卡组在当前规则下不可用";
        return null;
    }
    private void initHistory(PtcgLiveLobbyLayout layout) {
        var dialog = layout.historyDialog();
        button("关闭", null, dialog.innerRight() - CLOSE_DIALOG_WIDTH, dialog.innerTop(), CLOSE_DIALOG_WIDTH, true, () -> setMode(Mode.LOBBY));
    }

    @Override public void tick() {
        if (++pollTicks >= POLL_TICKS) { pollTicks = 0; send("REFRESH"); }
        if (messageTicks > 0) messageTicks--;
        super.tick();
    }

    // ---- drawing ----
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Vanilla container look: dim the world like a chest or furnace (no blur), then the window.
        renderInGameBackground(context);
        var layout = layout();
        CardPainter.vanillaWindow(context, layout.windowX(), layout.windowY(), layout.windowWidth(), layout.windowHeight());
        if (mode == Mode.SETTINGS) renderSettings(context, layout);
        else {
            renderLobby(context, layout);
            if (mode == Mode.PRACTICE || mode == Mode.HISTORY) {
                context.fill(0, 0, width, height, DIALOG_DIM);
                if (mode == Mode.PRACTICE) renderPractice(context, layout); else renderHistory(context, layout);
            }
        }
        super.render(context, mouseX, mouseY, delta);
        if (mode == Mode.LOBBY && deckListOpen) renderDeckList(context, layout, mouseX, mouseY);
        if (mode == Mode.SETTINGS && ready() && mouseX >= layout.innerLeft()
            && mouseX < layout.innerLeft() + layout.listWidth() && mouseY >= layout.listTop() + 2 && mouseY < layout.listBottom()) {
            int index = ruleScroll + (mouseY - layout.listTop() - 2) / LIST_ROW;
            if (index >= 0 && index < lobby.rulesets().size()) {
                var rule = lobby.rulesets().get(index);
                String summary = rule.policySummaryZh();
                if (summary != null && !summary.isBlank()) {
                    var tooltip = textRenderer.wrapLines(Text.literal(rule.nameZh() + "\n" + summary), Math.max(90, Math.min(260, width - 24)));
                    context.drawOrderedTooltip(textRenderer, tooltip, mouseX, mouseY);
                }
            }
        }
    }
    /** Screen.render calls this after our own drawing; the dimming and window are drawn first in render(). */
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    private void label(DrawContext context, String text, int x, int y, int color) { CardPainter.label(context, textRenderer, text, x, y, color); }
    private void labelRight(DrawContext context, String text, int right, int y, int color) { label(context, text, right - textRenderer.getWidth(text), y, color); }
    private void labelCenter(DrawContext context, String text, int center, int y, int color) { label(context, text, center - textRenderer.getWidth(text) / 2, y, color); }
    private void dark(DrawContext context, String text, int x, int y, int color) { context.drawTextWithShadow(textRenderer, text, x, y, color); }
    private String fit(String text, int width) {
        if (textRenderer.getWidth(text) <= width) return text;
        return textRenderer.trimToWidth(text, Math.max(0, width - textRenderer.getWidth("…"))) + "…";
    }
    private String longest(int width, String... options) {
        for (String option : options) if (textRenderer.getWidth(option) <= width) return option;
        return fit(options[options.length - 1], width);
    }

    private void renderLobby(DrawContext context, PtcgLiveLobbyLayout layout) {
        String status = lobby == null ? "读取中" : lobby.statusZh();
        String tableName=dev.hua.ptcglive.duel.DuelLetters.virtualPosition(tablePos)?"战书临时卡桌":"卡牌对战桌";
        label(context, longest(layout.settingsX() - 70 - layout.innerLeft(), tableName+" · " + status, tableName), layout.innerLeft(), layout.barY() + 6, LABEL);
        if (ready()) {
            if (layout.wide()) { renderRules(context, layout); renderSpectators(context, layout); }
            else {
                String audience = "观众 " + lobby.spectators().size();
                labelRight(context, audience, layout.innerRight(), layout.contentTop() + 1, LABEL);
                label(context, fit("规则：" + lobby.rule().nameZh(), layout.innerWidth() - textRenderer.getWidth(audience) - 8),
                    layout.innerLeft(), layout.contentTop() + 1, LABEL);
            }
            renderSeats(context, layout);
        } else if (lobby != null) {
            int center = layout.seatsX() + layout.seatsWidth() / 2, middle = (layout.contentTop() + layout.bodyBottom()) / 2;
            labelCenter(context, "空桌".equals(lobby.statusZh()) ? "这张卡桌还没有人开桌" : "本桌已结束", center, middle - 4, LABEL);
        }
        // Footer hint: the latest server message for a while, otherwise what to do next.
        int left = layout.footerHintLeft(), right = footerHintRight - 4;
        boolean showMessage = messageTicks > 0 && !message.isBlank();
        String hint = showMessage ? message : stateHint();
        int color = showMessage && !success ? LABEL_ERROR : LABEL_NOTICE;
        String shown = fit(hint, right - left);
        labelCenter(context, shown, (left + right) / 2, layout.footerY() + 6, color);
    }
    private String stateHint() {
        if (lobby == null) return "正在读取…";
        if (!lobby.exists()) return "本桌已结束，可以重新开桌";
        if (!lobby.noticeZh().isBlank()) return lobby.noticeZh();
        if (lobby.matchRunning()) return "对局进行中";
        if (lobby.guest() == null) return isHost() ? "等待对手加入" : "有空位，可以加入对局";
        if (lobby.host().ready() && lobby.guest().ready()) return isHost() ? "双方已准备，可以开始" : "等待房主开始";
        return "双方选好卡组并准备后，由房主开始";
    }
    private void renderRules(DrawContext context, PtcgLiveLobbyLayout layout) {
        int x = layout.rulesX(), w = layout.sideWidth(), top = layout.sideListTop(), bottom = layout.bodyBottom();
        label(context, "当前规则", x, layout.contentTop() + 1, LABEL);
        CardPainter.sunken(context, x, top, w, bottom - top, TEXT_AREA);
        var rule = lobby.rule();
        List<Object[]> lines = new ArrayList<>();
        lines.add(new Object[]{rule.nameZh(), TEXT});
        lines.add(null);
        lines.add(new Object[]{"版本 " + rule.version(), TEXT_SOFT});
        if (!rule.effectiveDate().isBlank()) lines.add(new Object[]{rule.effectiveDate() + " 起生效", TEXT_SOFT});
        if (!rule.regionZh().isBlank()) lines.add(new Object[]{rule.regionZh(), TEXT_SOFT});
        if (rule.casualOnly()) lines.add(new Object[]{"只能休闲对局", TEXT_SOFT});
        if (!rule.matchEnabled()) lines.add(new Object[]{"资料待核验，不能开局", BAD_ON_DARK});
        lines.add(null);
        lines.add(new Object[]{"赛制", TEXT});
        lines.add(new Object[]{lobby.formatZh(), TEXT_SOFT});
        lines.add(new Object[]{"限时 " + lobby.timeLimitMinutes() + " 分钟", TEXT_SOFT});
        drawLines(context, lines, x + 5, top + 5, w - 10, bottom - 2);
    }
    /** Wrapped lines in a dark text area; {@code null} adds a small gap. Lines past {@code bottom} are dropped. */
    private void drawLines(DrawContext context, List<Object[]> lines, int x, int y, int width, int bottom) {
        for (Object[] line : lines) {
            if (line == null) { y += 4; continue; }
            for (var wrapped : textRenderer.wrapLines(Text.literal((String) line[0]), width)) {
                if (y + 9 > bottom) return;
                context.drawTextWithShadow(textRenderer, wrapped, x, y, (Integer) line[1]); y += 10;
            }
        }
    }
    private void renderSpectators(DrawContext context, PtcgLiveLobbyLayout layout) {
        int x = layout.spectatorsX(), w = layout.sideWidth(), top = layout.sideListTop(), bottom = layout.spectatorListBottom(!seated());
        label(context, "观众（" + lobby.spectators().size() + "）", x, layout.contentTop() + 1, LABEL);
        CardPainter.sunken(context, x, top, w, bottom - top, TEXT_AREA);
        int y = top + 4;
        for (var person : lobby.spectators()) {
            if (y + 18 > bottom) break;
            CardPainter.sunken(context, x + 4, y, 18, 18, SLOT_FILL);
            CardPainter.playerHead(context, UUID.fromString(person.uuid()), x + 5, y + 1, 16);
            boolean me = client != null && client.player != null && client.player.getUuidAsString().equals(person.uuid());
            dark(context, fit(person.name() + (me ? "（你）" : ""), w - 30), x + 26, y + 5, TEXT);
            y += SPECTATOR_ROW;
        }
        if (lobby.spectators().isEmpty()) dark(context, fit("还没有观众", w - 10), x + 5, top + 5, MUTED_ON_DARK);
    }
    private void renderSeats(DrawContext context, PtcgLiveLobbyLayout layout) {
        int x = layout.seatsX(), w = layout.seatsWidth();
        int vs = layout.vsLineY();
        context.fill(x, vs - 1, x + w, vs, VANILLA_SHADOW); context.fill(x, vs, x + w, vs + 1, VANILLA_HIGHLIGHT);
        context.fill(x + w / 2 - 12, vs - 5, x + w / 2 + 12, vs + 5, VANILLA_PANEL);
        labelCenter(context, "VS", x + w / 2, vs - 4, LABEL);
        for (int index = 0; index < 2; index++) {
            int y = layout.seatY(index), h = layout.seatHeight();
            CardPainter.sunken(context, x, y, w, h, SEAT_FILL);
            var seat = seatAt(index);
            if (seat == null) {
                int middle = y + h / 2;
                labelCenter(context, lobby.matchRunning()?"电脑对局进行中":"空位 · 等待玩家加入", x + w / 2, waiting() ? middle - 14 : middle - 4, LABEL);
                continue;
            }
            int a = layout.avatarSize(), ax = layout.avatarX(), ay = layout.avatarY(index);
            CardPainter.slot(context, ax, ay, a, a);
            CardPainter.playerHead(context, UUID.fromString(seat.uuid()), ax + 1, ay + 1, a - 2);
            int tx = layout.seatTextX(), right = ownSeat(index) && isGuest() && waiting() ? layout.leaveX() - 4 : layout.seatTextRight();
            String tags = (seatIsHost(index) ? "（房主）" : "") + (ownSeat(index) ? "（你）" : "");
            String name = fit(seat.name(), Math.max(20, right - tx - textRenderer.getWidth(tags) - 2));
            label(context, name, tx, y + 6, LABEL_STRONG);
            label(context, tags, tx + textRenderer.getWidth(name) + 2, y + 6, LABEL);
            boolean running = lobby.matchRunning();
            String state = running ? "● 对局中" : seat.ready() ? "● 已准备" : "○ 未准备";
            label(context, state, tx, y + 17, running || seat.ready() ? LABEL_OK : LABEL);
            if (ownSeat(index) && waiting()) {
                var own = ownBox();
                String legality = own == null ? "· 还没选卡组" : own.canStart() ? "· 卡组" + own.legalityZh() : "· 卡组不可用";
                int lx = tx + textRenderer.getWidth(state) + 6;
                if (lx + textRenderer.getWidth(legality) <= layout.seatTextRight())
                    label(context, legality, lx, y + 17, own == null ? LABEL : own.canStart() ? LABEL_OK : LABEL_ERROR);
            } else if (layout.deckButtonY(index) > y + 28) {
                // Another player's deck: only whether one is chosen, never which.
                label(context, "卡组：" + (running ? "对局中" : seat.deckChosen() ? "已选择" : "未选择"), tx, layout.deckButtonY(index) + 6, LABEL);
            }
        }
    }
    /** The own-deck dropdown, drawn above the deck button over everything else. */
    private DeckList deckList(PtcgLiveLobbyLayout layout) {
        int rows = Math.max(1, lobby.myBoxes().size()), rowHeight = 12;
        int w = Math.min(layout.innerWidth(), Math.max(layout.deckButtonWidth(), 200)), h = rows * rowHeight + 4;
        int x = Math.min(layout.seatTextX(), layout.innerRight() - w), buttonY = layout.deckButtonY(1);
        int y = buttonY - h - 2;
        if (y < layout.contentTop()) y = buttonY + BUTTON_HEIGHT + 2;
        return new DeckList(x, y, w, h, rowHeight);
    }
    private record DeckList(int x, int y, int width, int height, int rowHeight) { }
    private void renderDeckList(DrawContext context, PtcgLiveLobbyLayout layout, int mouseX, int mouseY) {
        if (!ready()) return;
        var list = deckList(layout);
        CardPainter.sunken(context, list.x(), list.y(), list.width(), list.height(), TEXT_AREA);
        if (lobby.myBoxes().isEmpty()) { dark(context, "你还没有卡盒", list.x() + 4, list.y() + 4, MUTED_ON_DARK); return; }
        for (int i = 0; i < lobby.myBoxes().size(); i++) {
            var box = lobby.myBoxes().get(i);
            int y = list.y() + 2 + i * list.rowHeight();
            boolean hover = mouseX >= list.x() && mouseX < list.x() + list.width() && mouseY >= y && mouseY < y + list.rowHeight();
            if (box.id().equals(lobby.myBoxId()) || hover) CardPainter.selectedRow(context, list.x() + 1, y, list.width() - 2, list.rowHeight());
            String legality = fit(box.legalityZh(), list.width() / 2 - 6);
            dark(context, fit(box.nameZh() + "（" + box.cards() + "/60）", list.width() - textRenderer.getWidth(legality) - 14), list.x() + 4, y + 2, TEXT);
            dark(context, legality, list.x() + list.width() - 4 - textRenderer.getWidth(legality), y + 2, box.canStart() ? GOOD_ON_DARK : BAD_ON_DARK);
        }
    }

    private void renderSettings(DrawContext context, PtcgLiveLobbyLayout layout) {
        label(context, "卡桌设置", layout.innerLeft(), layout.barY() + 6, LABEL);
        if (!ready()) return;
        var rules = lobby.rulesets();
        if (selectedRule == null) selectedRule = lobby.rule().id();
        int selectedIndex = Math.max(0, indexOfRule(selectedRule));
        int visible = layout.visibleListRows();
        if (ruleScroll < 0) ruleScroll = Math.max(0, selectedIndex - visible / 2);
        ruleScroll = Math.max(0, Math.min(Math.max(0, rules.size() - visible), ruleScroll));
        // Ruleset list.
        int lx = layout.innerLeft(), lw = layout.listWidth(), top = layout.listTop(), bottom = layout.listBottom();
        label(context, "规则包", lx, layout.contentTop() + 1, LABEL);
        CardPainter.sunken(context, lx, top, lw, bottom - top, TEXT_AREA);
        for (int i = 0; i < visible && ruleScroll + i < rules.size(); i++) {
            var rule = rules.get(ruleScroll + i);
            int y = top + 2 + i * LIST_ROW;
            if (rule.id().equals(selectedRule)) CardPainter.selectedRow(context, lx + 2, y, lw - 9, LIST_ROW);
            if (rule.id().equals(lobby.rule().id())) dark(context, "●", lx + 4, y + 2, GOOD_ON_DARK);
            String suffix = !rule.matchEnabled() ? "【不能开局】" : rule.casualOnly() ? "【休闲】" : "";
            dark(context, fit(rule.nameZh() + suffix, lw - 24), lx + 14, y + 2, isHost() ? TEXT : TEXT_SOFT);
        }
        CardPainter.scrollbar(context, lx + lw - 6, top + 1, bottom - 1, ruleScroll, visible, rules.size());
        // Details of the highlighted ruleset, including the viewer's own deck against it.
        int dx = layout.detailsX(), dw = layout.detailsWidth();
        label(context, "规则说明", dx, layout.contentTop() + 1, LABEL);
        CardPainter.sunken(context, dx, top, dw, bottom - top, TEXT_AREA);
        var rule = rules.isEmpty() ? lobby.rule() : rules.get(selectedIndex);
        List<Object[]> lines = new ArrayList<>();
        lines.add(new Object[]{rule.nameZh() + (rule.casualOnly() ? "【休闲】" : ""), TEXT});
        lines.add(null);
        lines.add(new Object[]{"版本 " + rule.version() + (rule.version().startsWith("archive") ? "（历史存档）" : ""), TEXT_SOFT});
        if (!rule.effectiveDate().isBlank()) lines.add(new Object[]{rule.effectiveDate() + " 起生效", TEXT_SOFT});
        if (!rule.regionZh().isBlank()) lines.add(new Object[]{rule.regionZh(), TEXT_SOFT});
        lines.add(new Object[]{!rule.matchEnabled() ? "资料待核验，不能开局" : rule.casualOnly() ? "只能休闲对局，不计赛事" : "可在卡桌开局",
            !rule.matchEnabled() ? BAD_ON_DARK : TEXT_SOFT});
        lines.add(null);
        var own = ownBox();
        if (own == null) lines.add(new Object[]{seated() ? "你还没选卡组" : "入座并选好卡组后，这里会显示它能否在此规则下使用", MUTED_ON_DARK});
        else {
            lines.add(new Object[]{"我的卡组：" + own.nameZh() + "（" + own.cards() + "/60）", TEXT});
            lines.add(new Object[]{rule.myDeckZh(), rule.myDeckOk() ? GOOD_ON_DARK : BAD_ON_DARK});
        }
        drawLines(context, lines, dx + 5, top + 5, dw - 10, bottom - 2);
        // Option rows and footer hint.
        int optionsTop = layout.optionsTop();
        label(context, "赛制", layout.innerLeft(), optionsTop + 6, LABEL);
        int noteX = layout.innerLeft() + OPTION_LABEL_WIDTH + layout.formatWidth() + 6;
        label(context, longest(layout.innerRight() - noteX, "目前只有这一种赛制", "仅此一种"), noteX, optionsTop + 6, 0x606060);
        label(context, "危险操作", layout.innerLeft(), optionsTop + BUTTON_HEIGHT + GAP + 6, LABEL);
        int endNote = layout.innerLeft() + OPTION_LABEL_WIDTH + END_WIDTH + 6;
        if (isHost()) label(context, longest(layout.innerRight() - endNote, "解散本桌，双方回到空闲", "解散本桌"), endNote, optionsTop + BUTTON_HEIGHT + GAP + 6, 0x606060);
        boolean changed = !selectedRule.equals(lobby.rule().id());
        boolean showMessage = messageTicks > 0 && !message.isBlank();
        String hint = showMessage ? message : !isHost() ? "只有房主可以修改设置，你可以查看规则说明"
            : !waiting() ? "对局进行中，结束后才能修改" : changed ? "应用后双方需要重新准备，卡组按新规则重新检查" : "当前规则包已选中";
        int hintRight = isHost() ? layout.cancelX() - 6 : layout.innerRight();
        label(context, longest(hintRight - layout.innerLeft(), hint, changed ? "应用后双方需重新准备" : "只有房主可以修改"),
            layout.innerLeft(), layout.footerY() + 6, showMessage && !success ? LABEL_ERROR : LABEL_NOTICE);
    }
    private int indexOfRule(String id) {
        var rules = lobby.rulesets();
        for (int i = 0; i < rules.size(); i++) if (rules.get(i).id().equals(id)) return i;
        return -1;
    }

    private void renderPractice(DrawContext context, PtcgLiveLobbyLayout layout) {
        var dialog = layout.practiceDialog();
        CardPainter.vanillaWindow(context, dialog.x(), dialog.y(), dialog.width(), dialog.height());
        int left = dialog.innerLeft(), right = dialog.innerRight(), top = dialog.innerTop();
        label(context, "单人练习", left, top + 6, LABEL);
        int subtitleWidth = dialog.innerWidth() - textRenderer.getWidth("单人练习") - 12;
        labelRight(context, longest(subtitleWidth, "一个人操控双方，不计赛事和奖励", "不计赛事和奖励"), right, top + 6, 0x606060);
        if (!ready()) return;
        label(context, longest(dialog.innerWidth(), "规则：" + lobby.rule().nameZh() + " · 在设置里更换", "规则：" + lobby.rule().nameZh()), left, top + 20, LABEL);
        int columnTop = practiceColumnTop(dialog), listTop = practiceListTop(dialog), listBottom = practiceListBottom(dialog), w = practiceColumnWidth(dialog);
        for (int side = 0; side < 2; side++) {
            int x = practiceColumnX(dialog, side);
            String chosen = side == 0 ? practiceFirst : practiceSecond, other = side == 0 ? practiceSecond : practiceFirst;
            label(context, side == 0 ? "甲方" : "乙方", x, columnTop + 1, LABEL);
            var picked = boxById(chosen);
            if (picked != null) labelRight(context, fit(picked.nameZh(), w - 30), x + w, columnTop + 1, picked.canStart() ? LABEL_OK : LABEL_ERROR);
            CardPainter.sunken(context, x, listTop, w, listBottom - listTop, TEXT_AREA);
            var boxes = lobby.myBoxes();
            if (boxes.isEmpty()) { dark(context, fit("你还没有卡盒", w - 10), x + 5, listTop + 5, MUTED_ON_DARK); continue; }
            for (int i = 0; i < boxes.size(); i++) {
                int y = listTop + 2 + i * PRACTICE_ROW;
                if (y + PRACTICE_ROW > listBottom - 1) break;
                var box = boxes.get(i);
                boolean taken = box.id().equals(other);
                if (box.id().equals(chosen)) CardPainter.selectedRow(context, x + 2, y, w - 4, PRACTICE_ROW - 1);
                dark(context, fit(box.nameZh() + "（" + box.cards() + "/60）", w - 12), x + 5, y + 2, taken ? MUTED_ON_DARK : TEXT);
                String note = taken ? "已给" + (side == 0 ? "乙方" : "甲方") : box.legalityZh();
                dark(context, fit(note, w - 12), x + 5, y + 11, taken ? MUTED_ON_DARK : box.canStart() ? GOOD_ON_DARK : BAD_ON_DARK);
            }
        }
        String problem = practiceProblem();
        boolean showMessage = messageTicks > 0 && !message.isBlank() && !success;
        label(context, fit(showMessage ? message : problem == null ? "选好了，可以开始练习" : problem, dialog.innerWidth()), left, practiceHintY(dialog),
            showMessage ? LABEL_ERROR : problem == null ? LABEL_OK : LABEL_NOTICE);
    }

    private void renderHistory(DrawContext context, PtcgLiveLobbyLayout layout) {
        var dialog = layout.historyDialog();
        CardPainter.vanillaWindow(context, dialog.x(), dialog.y(), dialog.width(), dialog.height());
        int left = dialog.innerLeft(), right = dialog.innerRight(), top = dialog.contentTop(), bottom = dialog.innerBottom();
        var records = history == null ? List.<TableLobbyView.HistoryRecord>of() : history.records();
        label(context, records.isEmpty() ? "对局记录" : "对局记录（最近 " + records.size() + " 场）", left, dialog.innerTop() + 6, LABEL);
        if (records.isEmpty()) {
            CardPainter.sunken(context, left, top, dialog.innerWidth(), bottom - top, TEXT_AREA);
            int middle = (top + bottom) / 2;
            String title = history == null ? "正在读取…" : "还没有已保存的对局记录";
            context.drawCenteredTextWithShadow(textRenderer, title, (left + right) / 2, middle - 10, TEXT);
            if (history != null) context.drawCenteredTextWithShadow(textRenderer,
                longest(dialog.innerWidth() - 12, "和别人打完一局后会出现在这里；单人练习不计入", "打完一局后会出现在这里"), (left + right) / 2, middle + 2, TEXT_DIM);
            return;
        }
        historySelected = Math.max(0, Math.min(records.size() - 1, historySelected));
        // Match list.
        int lw = historyListWidth(dialog);
        CardPainter.sunken(context, left, top, lw, bottom - top, TEXT_AREA);
        for (int i = 0; i < records.size(); i++) {
            int y = top + 2 + i * HISTORY_ROW;
            if (y + HISTORY_ROW > bottom - 1) break;
            var record = records.get(i);
            if (i == historySelected) CardPainter.selectedRow(context, left + 2, y, lw - 4, HISTORY_ROW - 1);
            drawOutcome(context, record.outcome(), left + 5, y + 4);
            CardPainter.sunken(context, left + 22, y + 3, 18, 18, SLOT_FILL);
            if (!record.opponentUuid().isBlank()) CardPainter.playerHead(context, UUID.fromString(record.opponentUuid()), left + 23, y + 4, 16);
            String date = TIME.format(Instant.ofEpochMilli(record.finishedAtMillis())).substring(5);
            dark(context, date, left + lw - 5 - textRenderer.getWidth(date), y + 3, TEXT_DIM);
            dark(context, fit(record.opponentName(), lw - 54 - textRenderer.getWidth(date)), left + 44, y + 3, TEXT);
            dark(context, fit(record.reasonZh(), lw - 50), left + 44, y + 13, TEXT_SOFT);
        }
        // Details and the public-action log.
        var record = records.get(historySelected);
        int dx = historyDetailsX(dialog), dw = right - dx;
        CardPainter.slot(context, dx, top, 26, 26);
        if (!record.opponentUuid().isBlank()) CardPainter.playerHead(context, UUID.fromString(record.opponentUuid()), dx + 1, top + 1, 24);
        label(context, fit("对手 " + record.opponentName(), dw - 32), dx + 32, top + 2, LABEL_STRONG);
        String result = switch (record.outcome()) { case "WIN" -> "胜"; case "LOSS" -> "负"; default -> "平"; };
        label(context, fit(result + " · " + record.reasonZh(), dw - 32), dx + 32, top + 14,
            "WIN".equals(record.outcome()) ? LABEL_OK : "LOSS".equals(record.outcome()) ? LABEL_ERROR : LABEL);
        int y = top + 32;
        for (String line : List.of("结束于 " + TIME.format(Instant.ofEpochMilli(record.finishedAtMillis())),
                "你的座位：" + (record.viewerWasHost() ? "房主" : "客方"),
                "公开动作 " + record.replay().size() + " 条 · 随机审计 " + record.randomAudit() + " 条")) {
            label(context, fit(line, dw), dx, y, LABEL); y += 10;
        }
        label(context, "公开动作", dx, y + 3, LABEL);
        int logTop = historyLogTop(dialog), visible = Math.max(1, (bottom - logTop - 6) / LOG_ROW);
        CardPainter.sunken(context, dx, logTop, dw, bottom - logTop, TEXT_AREA);
        var replay = record.replay();
        logScroll = Math.max(0, Math.min(Math.max(0, replay.size() - visible), logScroll));
        for (int i = 0; i < visible && logScroll + i < replay.size(); i++) {
            var line = replay.get(logScroll + i);
            int ly = logTop + 3 + i * LOG_ROW;
            String number = String.valueOf(line.sequence());
            dark(context, number, dx + 22 - textRenderer.getWidth(number), ly, MUTED_ON_DARK);
            dark(context, fit(line.actor() + "：" + line.operation(), dw - 34), dx + 26, ly, TEXT_BODY);
        }
        if (replay.isEmpty()) dark(context, "这一局没有公开动作", dx + 5, logTop + 4, MUTED_ON_DARK);
        CardPainter.scrollbar(context, dx + dw - 6, logTop + 1, bottom - 1, logScroll, visible, replay.size());
    }
    private void drawOutcome(DrawContext context, String outcome, int x, int y) {
        int color = "WIN".equals(outcome) ? RESULT_WIN : "LOSS".equals(outcome) ? RESULT_LOSS : RESULT_DRAW;
        String text = "WIN".equals(outcome) ? "胜" : "LOSS".equals(outcome) ? "负" : "平";
        context.fill(x, y, x + 14, y + 14, color);
        context.drawCenteredTextWithShadow(textRenderer, text, x + 7, y + 3, TEXT);
    }

    // ---- input ----
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        var layout = layout();
        // The open deck list sits above buttons, so it gets the click first.
        if (mode == Mode.LOBBY && deckListOpen && ready()) {
            var list = deckList(layout);
            if (mouseX >= list.x() && mouseX < list.x() + list.width() && mouseY >= list.y() && mouseY < list.y() + list.height()) {
                int index = (int) ((mouseY - list.y() - 2) / list.rowHeight());
                if (button == 0 && index >= 0 && index < lobby.myBoxes().size()) {
                    deckListOpen = false;
                    send("SELECT_BOX", lobby.myBoxes().get(index).id());
                }
                return true;
            }
            // Any click outside closes the list; a click on the deck button itself must not reopen it.
            deckListOpen = false;
            super.mouseClicked(mouseX, mouseY, button);
            deckListOpen = false;
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || !ready()) return false;
        switch (mode) {
            case SETTINGS -> {
                int lx = layout.innerLeft(), top = layout.listTop();
                if (mouseX >= lx && mouseX < lx + layout.listWidth() - 7 && mouseY >= top + 2 && mouseY < layout.listBottom()) {
                    int index = ruleScroll + (int) ((mouseY - top - 2) / LIST_ROW);
                    if (index < lobby.rulesets().size()) { selectedRule = lobby.rulesets().get(index).id(); clearAndInit(); }
                    return true;
                }
            }
            case PRACTICE -> {
                var dialog = layout.practiceDialog();
                int listTop = practiceListTop(dialog), w = practiceColumnWidth(dialog);
                for (int side = 0; side < 2; side++) {
                    int x = practiceColumnX(dialog, side);
                    if (mouseX < x || mouseX >= x + w || mouseY < listTop + 2 || mouseY >= practiceListBottom(dialog)) continue;
                    int index = (int) ((mouseY - listTop - 2) / PRACTICE_ROW);
                    if (index >= lobby.myBoxes().size()) return true;
                    String id = lobby.myBoxes().get(index).id();
                    if (id.equals(side == 0 ? practiceSecond : practiceFirst)) return true; // already given to the other side
                    if (side == 0) practiceFirst = id; else practiceSecond = id;
                    clearAndInit(); return true;
                }
            }
            case HISTORY -> {
                var dialog = layout.historyDialog();
                int top = dialog.contentTop(), left = dialog.innerLeft();
                if (history != null && mouseX >= left && mouseX < left + historyListWidth(dialog) && mouseY >= top + 2) {
                    int index = (int) ((mouseY - top - 2) / HISTORY_ROW);
                    if (index < history.records().size()) { historySelected = index; logScroll = 0; }
                    return true;
                }
            }
            default -> { }
        }
        return false;
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical == 0) return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        int step = vertical > 0 ? -1 : 1;
        if (mode == Mode.SETTINGS) { ruleScroll = Math.max(0, ruleScroll + step); return true; }
        if (mode == Mode.HISTORY) { logScroll = Math.max(0, logScroll + step * 3); return true; }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == KEY_ESCAPE) {
            if (mode != Mode.LOBBY) { setMode(Mode.LOBBY); return true; }
            if (deckListOpen) { deckListOpen = false; return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public boolean shouldPause() { return false; }
}
