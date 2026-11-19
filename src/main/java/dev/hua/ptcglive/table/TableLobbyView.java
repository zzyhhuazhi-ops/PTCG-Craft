package dev.hua.ptcglive.table;

import dev.hua.ptcglive.engine.MatchResult;
import dev.hua.ptcglive.rules.DeckBox;
import dev.hua.ptcglive.rules.DeckIssue;
import dev.hua.ptcglive.rules.DeckValidationReport;
import dev.hua.ptcglive.rules.Ruleset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Recipient-specific, display-only model for the card-table screen.  It holds
 * public lobby facts (seats, ready state, whether a deck is chosen, spectators,
 * rules) plus the viewer's <em>own</em> card boxes.  It never carries another
 * player's box identity, box name or contents.  Every action is still a
 * {@code TableIntentPayload} validated by the server.
 */
public final class TableLobbyView {
    public enum Role { HOST, GUEST, SPECTATOR, VISITOR }
    public record Person(String uuid, String name) { }
    /** A seat: who sits there and only whether a deck is chosen, never which one. */
    public record Seat(String uuid, String name, boolean ready, boolean deckChosen) { }
    /** One installed ruleset; {@code myDeckZh} is the viewer's chosen box checked against it ("" when none is chosen). */
    public record Rule(String id, String nameZh, String version, String effectiveDate, String regionZh,
                       boolean casualOnly, boolean matchEnabled, String myDeckZh, boolean myDeckOk, String policySummaryZh) {
        public Rule(String id, String nameZh, String version, String effectiveDate, String regionZh,
                    boolean casualOnly, boolean matchEnabled, String myDeckZh, boolean myDeckOk) {
            this(id, nameZh, version, effectiveDate, regionZh, casualOnly, matchEnabled, myDeckZh, myDeckOk, "");
        }
    }
    /** One of the viewer's own boxes, checked against the table's current ruleset. */
    public record Box(String id, String nameZh, int cards, String legalityZh, boolean canStart) { }
    public record Lobby(boolean exists, String status, String statusZh, String role, Seat host, Seat guest,
                        List<Person> spectators, Rule rule, List<Rule> rulesets, String formatZh, int timeLimitMinutes,
                        List<Box> myBoxes, String myBoxId, boolean matchRunning, String noticeZh) {
        public Lobby {
            spectators = List.copyOf(spectators); rulesets = List.copyOf(rulesets); myBoxes = List.copyOf(myBoxes);
            Objects.requireNonNull(status); Objects.requireNonNull(role);
        }
    }
    public record ReplayLine(long sequence, String actor, String operation) { }
    /** One finished table match from the viewer's side; {@code outcome} is WIN, LOSS or DRAW. */
    public record HistoryRecord(String matchId, String opponentUuid, String opponentName, String outcome, String reasonZh,
                                long finishedAtMillis, boolean viewerWasHost, int randomAudit, List<ReplayLine> replay) { }
    public record History(List<HistoryRecord> records) { }

    /** The lobby screen shows the most recent finished matches only. */
    public static final int HISTORY_LIMIT = 8;
    /** Replay lines per match sent to the client; the full transcript stays on the server for audits. */
    public static final int REPLAY_LIMIT = 400;

    private TableLobbyView() { }

    /**
     * @param lobby       the table, or {@code null} when none exists at this position
     * @param boxes       the viewer's own boxes
     * @param validate    validates one of the viewer's boxes against a ruleset id
     */
    public static Lobby of(CardTableLobby lobby, UUID viewer, Map<String, Ruleset> rulesets, Function<UUID, String> names,
                           List<DeckBox> boxes, BiFunction<UUID, String, DeckValidationReport> validate, String noticeZh) {
        Objects.requireNonNull(viewer, "查看者");
        if (lobby == null || lobby.status() == TableLobbyStatus.ENDED)
            return new Lobby(false, TableLobbyStatus.ENDED.name(), lobby == null ? "空桌" : "本桌已结束", Role.VISITOR.name(), null, null,
                List.of(), null, List.of(), "", 0, List.of(), "", false, noticeZh == null ? "" : noticeZh);
        var view = lobby.publicView();
        Role role = viewer.equals(lobby.host()) ? Role.HOST
            : lobby.guest().map(viewer::equals).orElse(false) ? Role.GUEST
            : lobby.spectators().contains(viewer) ? Role.SPECTATOR : Role.VISITOR;
        Seat host = new Seat(lobby.host().toString(), names.apply(lobby.host()), view.hostReady(), lobby.boxOf(lobby.host()).isPresent());
        Seat guest = lobby.guest().map(id -> new Seat(id.toString(), names.apply(id), view.guestReady(), lobby.boxOf(id).isPresent())).orElse(null);
        List<Person> spectators = lobby.spectators().stream().map(id -> new Person(id.toString(), names.apply(id))).toList();

        // Own boxes are validated against the table's current ruleset, exactly as before.
        List<Box> own = boxes.stream().map(box -> {
            DeckValidationReport report = validate.apply(box.id(), lobby.rulesetId());
            return new Box(box.id().toString(), box.nameZh(), box.contents().size(), legalityZh(report), report.canStartMatch());
        }).toList();
        UUID chosen = role == Role.HOST || role == Role.GUEST ? lobby.boxOf(viewer).orElse(null) : null;

        List<Rule> rules = new ArrayList<>();
        rulesets.values().stream().sorted(Comparator.comparingInt((Ruleset rule) -> dev.hua.ptcglive.rules.CurrentFormats.order(rule.id()))
            .thenComparing(Ruleset::id)).forEach(ruleset -> {
            String myDeck = ""; boolean myDeckOk = false;
            if (chosen != null) {
                DeckValidationReport report = validate.apply(chosen, ruleset.id());
                myDeck = legalityZh(report); myDeckOk = report.canStartMatch();
            }
            rules.add(new Rule(ruleset.id(), ruleset.displayNameZh(), ruleset.version(), ruleset.effectiveDate().toString(),
                regionZh(ruleset.region()), ruleset.edition().casualOnly(), ruleset.edition().matchEnabled(), myDeck, myDeckOk, ruleset.policySummaryZh()));
        });
        Rule current = rules.stream().filter(rule -> rule.id().equals(lobby.rulesetId())).findFirst()
            .orElse(new Rule(lobby.rulesetId(), "未安装的规则包", "未知", "", "", false, false, "", false));
        var format = TournamentSettingsCatalog.require(lobby.tournamentSettingId());
        return new Lobby(true, lobby.status().name(), lobby.status().labelZh(), role.name(), host, guest, spectators, current, rules,
            format.displayNameZh(), format.timeLimitMinutes(), own, chosen == null ? "" : chosen.toString(),
            lobby.match().isPresent(), noticeZh == null ? "" : noticeZh);
    }

    /** “可开局 / 休闲可开局 / 不可用：第一条错误”. */
    public static String legalityZh(DeckValidationReport report) {
        if (report.canStartMatch()) return report.casualOnly() ? "休闲可开局" : "可开局";
        return "不可用：" + report.issues().stream().filter(issue -> issue.severity() == DeckIssue.Severity.ERROR)
            .map(DeckIssue::messageZh).findFirst().orElse("卡组未通过检查");
    }

    /** The viewer's finished table matches, newest first, with public-fact replay lines only. */
    public static History history(List<CardTableManager.CompletedMatch> records, UUID viewer, Function<UUID, String> names) {
        Map<UUID, String> cache = new HashMap<>();
        Function<UUID, String> name = id -> id == null ? "未知玩家" : cache.computeIfAbsent(id, names);
        List<HistoryRecord> result = new ArrayList<>();
        for (int index = records.size() - 1; index >= 0 && result.size() < HISTORY_LIMIT; index--) {
            var record = records.get(index);
            boolean viewerWasHost = viewer.equals(record.firstPlayer());
            UUID opponent = viewerWasHost ? record.secondPlayer() : record.firstPlayer();
            String outcome = record.winner() == null ? "DRAW" : viewer.equals(record.winner()) ? "WIN" : "LOSS";
            var events = record.replay();
            List<ReplayLine> replay = events.subList(Math.max(0, events.size() - REPLAY_LIMIT), events.size()).stream()
                .map(event -> new ReplayLine(event.sequence(), name.apply(event.actor()), event.operation())).toList();
            result.add(new HistoryRecord(record.matchId().toString(), opponent == null ? "" : opponent.toString(), name.apply(opponent),
                outcome, reasonZh(record.reason(), outcome), record.finishedAt().toEpochMilli(), viewerWasHost,
                record.randomAudit().size(), replay));
        }
        return new History(result);
    }

    /** How a match ended, worded from the viewer's side. */
    public static String reasonZh(MatchResult.Reason reason, String outcome) {
        boolean won = "WIN".equals(outcome), lost = "LOSS".equals(outcome);
        return switch (reason) {
            case PRIZES -> won ? "拿完奖赏卡" : lost ? "对手拿完奖赏卡" : "双方同时拿完奖赏卡";
            case NO_POKEMON -> won ? "对手场上没有宝可梦" : lost ? "你场上没有宝可梦" : "双方场上都没有宝可梦";
            case DECK_OUT -> won ? "对手牌库耗尽" : lost ? "你的牌库耗尽" : "双方牌库耗尽";
            case CARD_EFFECT -> won ? "卡牌效果获胜" : lost ? "对手以卡牌效果获胜" : "卡牌效果结算";
            case CONCESSION -> won ? "对手认输" : lost ? "你认输" : "认输";
            case TIME -> "超时判定";
            case DRAW -> "平局";
            case SUDDEN_DEATH -> "骤死赛";
        };
    }

    public static String regionZh(String region) {
        return switch (region == null ? "" : region) {
            case "international" -> "国际赛区";
            case "cn" -> "简中赛区";
            case "cn-community" -> "简中社区";
            case "custom" -> "自定义";
            default -> region == null ? "" : region;
        };
    }
}
