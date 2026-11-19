package dev.hua.ptcglive.engine.tournament;

import dev.hua.ptcglive.engine.MatchResult;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-authoritative tournament score and time state. It intentionally records
 * facts and asks for a referee decision; it never invents penalties or a winner.
 */
public final class TournamentSeries {
    public enum Status { READY, PLAYING, FINISH_ACTIVE_TURN, EXTRA_TURNS, AWAITING_TIEBREAKER_OR_JUDGE, TIEBREAKER, COMPLETE }
    public enum TimeCallPoint { DURING_TURN, DURING_CHECKUP }
    public enum GameKind { REGULAR, TIEBREAKER }

    public record Score(UUID firstPlayer, UUID secondPlayer, int firstWins, int secondWins, int draws) {
        public int wins(UUID player) {
            if (player.equals(firstPlayer)) return firstWins;
            if (player.equals(secondPlayer)) return secondWins;
            throw new IllegalArgumentException("玩家不属于本场赛事");
        }
    }
    public record GameRecord(int number, GameKind kind, UUID winner, MatchResult.Reason winReason, String conclusionZh, Instant recordedAt) { }
    public record TiebreakerSetup(TiebreakerPolicy policy, int prizeCardsPerPlayer, boolean winsOnFirstPrizeAdvantage, String reasonZh) { }
    public record JudgeRecord(Instant recordedAt, String recordedByZh, String messageZh) { }
    public record SeriesResult(UUID winner, boolean draw, String conclusionZh) { }

    private final UUID firstPlayer;
    private final UUID secondPlayer;
    private final TournamentSettings settings;
    private final Clock clock;
    private final List<GameRecord> games = new ArrayList<>();
    private final List<JudgeRecord> judgeRecords = new ArrayList<>();
    private Status status = Status.READY;
    private int remainingExtraTurns;
    private SeriesResult result;

    public TournamentSeries(UUID firstPlayer, UUID secondPlayer, TournamentSettings settings, Clock clock) {
        if (Objects.requireNonNull(firstPlayer).equals(Objects.requireNonNull(secondPlayer)))
            throw new IllegalArgumentException("赛事对局需要两名不同玩家");
        this.firstPlayer = firstPlayer;
        this.secondPlayer = secondPlayer;
        this.settings = Objects.requireNonNull(settings);
        this.clock = Objects.requireNonNull(clock);
    }

    public TournamentSettings settings() { return settings; }
    public Status status() { return status; }
    public int remainingExtraTurns() { return remainingExtraTurns; }
    public List<GameRecord> games() { return List.copyOf(games); }
    public List<JudgeRecord> judgeRecords() { return List.copyOf(judgeRecords); }
    public boolean isComplete() { return status == Status.COMPLETE; }
    public SeriesResult result() { if (result == null) throw new IllegalStateException("赛事尚未结束"); return result; }
    public Score score() {
        int firstWins = 0, secondWins = 0, draws = 0;
        for (GameRecord game : games) {
            if (game.winner == null) draws++;
            else if (game.winner.equals(firstPlayer)) firstWins++;
            else if (game.winner.equals(secondPlayer)) secondWins++;
            else throw new IllegalStateException("赛事记录包含未知玩家");
        }
        return new Score(firstPlayer, secondPlayer, firstWins, secondWins, draws);
    }

    public void startGame() {
        require(status == Status.READY, "当前不能开始新局");
        if (games.size() >= settings.format().maximumGames()) throw new IllegalStateException("本场赛事已达到最大局数");
        status = Status.PLAYING;
        judgeRecords.add(new JudgeRecord(clock.instant(), "系统", "第 " + (games.size() + 1) + " 局已开始；限时由赛事设置记录，裁判可另行确认。"));
    }

    /** Records the moment time is called. The active player still completes their turn, then +3 turns are played. */
    public void callTime(TimeCallPoint point) {
        require(status == Status.PLAYING, "只有进行中的对局可以宣布限时");
        Objects.requireNonNull(point, "限时发生点");
        if (point == TimeCallPoint.DURING_TURN) {
            status = Status.FINISH_ACTIVE_TURN;
            remainingExtraTurns = 0;
            judgeRecords.add(new JudgeRecord(clock.instant(), "系统（待裁判确认）", "已记录限时：当前玩家完成本回合后进入额外 3 个完整回合。"));
        } else {
            status = Status.EXTRA_TURNS;
            remainingExtraTurns = 3;
            judgeRecords.add(new JudgeRecord(clock.instant(), "系统（待裁判确认）", "已记录在 Pokémon Checkup 宣布限时：下一回合起为额外 3 个完整回合。"));
        }
    }

    /** Call after a complete turn only; this method never resolves an unfinished game by itself. */
    public void completeTurn() {
        if (status == Status.FINISH_ACTIVE_TURN) {
            status = Status.EXTRA_TURNS;
            remainingExtraTurns = 3;
            return;
        }
        require(status == Status.EXTRA_TURNS, "当前不是限时额外回合");
        if (--remainingExtraTurns == 0) {
            status = Status.AWAITING_TIEBREAKER_OR_JUDGE;
            judgeRecords.add(new JudgeRecord(clock.instant(), "系统（待裁判确认）", "额外 3 个完整回合已结束；请由裁判记录结果、平局或启动决胜局。"));
        }
    }

    public void recordGameWinner(UUID winner, MatchResult matchResult, String conclusionZh) {
        requireInProgressOrAwaiting();
        if (!winner.equals(firstPlayer) && !winner.equals(secondPlayer)) throw new IllegalArgumentException("胜者不属于本场赛事");
        if (matchResult == null || !winner.equals(matchResult.winner())) throw new IllegalArgumentException("对局结果与胜者不一致");
        appendGame(GameKind.REGULAR, winner, matchResult.reason(), conclusionZh);
    }

    /** A draw must be explicitly recorded by the server/referee and is not permitted where a winner is required. */
    public void recordGameDraw(String conclusionZh) {
        requireInProgressOrAwaiting();
        if (settings.requiresDeclaredWinner()) throw new IllegalStateException("此赛事必须产生胜者；请启动决胜局或由裁判记录裁定");
        appendGame(GameKind.REGULAR, null, MatchResult.Reason.DRAW, conclusionZh);
    }

    /** Creates instructions only. A client cannot silently choose or resolve a tiebreaker. */
    public TiebreakerSetup beginTiebreaker(String reasonZh) {
        require(status == Status.AWAITING_TIEBREAKER_OR_JUDGE, "当前不能启动决胜局");
        if (!settings.requiresDeclaredWinner()) throw new IllegalStateException("本赛事允许平局，不应自动进入决胜局");
        if (reasonZh == null || reasonZh.isBlank()) throw new IllegalArgumentException("必须记录启动决胜局的原因");
        status = Status.TIEBREAKER;
        judgeRecords.add(new JudgeRecord(clock.instant(), "系统（待裁判确认）", "决胜局已启动：" + reasonZh));
        boolean official = settings.tiebreakerPolicy() == TiebreakerPolicy.OFFICIAL_FULL_PRIZE;
        return new TiebreakerSetup(settings.tiebreakerPolicy(), official ? 6 : 1, official, reasonZh);
    }

    public void recordTiebreakerWinner(UUID winner, String conclusionZh) {
        require(status == Status.TIEBREAKER, "当前没有进行中的决胜局");
        if (!winner.equals(firstPlayer) && !winner.equals(secondPlayer)) throw new IllegalArgumentException("胜者不属于本场赛事");
        appendGame(GameKind.TIEBREAKER, winner, null, conclusionZh);
    }

    /** This is an audit entry, not an automatic penalty or a substitute for a referee. */
    public void recordJudgeDecision(String recordedByZh, String messageZh) {
        if (recordedByZh == null || recordedByZh.isBlank() || messageZh == null || messageZh.isBlank())
            throw new IllegalArgumentException("裁判记录的人员和内容均不能为空");
        judgeRecords.add(new JudgeRecord(clock.instant(), recordedByZh, messageZh));
    }

    /** An engine-detected draw in a winner-required event is escalated, never auto-resolved. */
    public void awaitJudgeForEngineDraw(String messageZh) {
        requireInProgressOrAwaiting();
        if (messageZh == null || messageZh.isBlank()) throw new IllegalArgumentException("必须记录需要裁判确认的原因");
        status = Status.AWAITING_TIEBREAKER_OR_JUDGE;
        judgeRecords.add(new JudgeRecord(clock.instant(), "系统（待裁判确认）", messageZh));
    }

    private void appendGame(GameKind kind, UUID winner, MatchResult.Reason reason, String conclusionZh) {
        if (conclusionZh == null || conclusionZh.isBlank()) throw new IllegalArgumentException("必须记录对局结论");
        games.add(new GameRecord(games.size() + 1, kind, winner, reason, conclusionZh, clock.instant()));
        judgeRecords.add(new JudgeRecord(clock.instant(), "系统", "第 " + games.size() + " 局结果已记录：" + conclusionZh));
        Score score = score();
        if (score.firstWins() >= settings.format().winsRequired()) { complete(firstPlayer, false, "达到所需胜局数"); return; }
        if (score.secondWins() >= settings.format().winsRequired()) { complete(secondPlayer, false, "达到所需胜局数"); return; }
        if (games.size() >= settings.format().maximumGames()) {
            if (score.firstWins() > score.secondWins()) complete(firstPlayer, false, "已达到最大局数，比分领先");
            else if (score.secondWins() > score.firstWins()) complete(secondPlayer, false, "已达到最大局数，比分领先");
            else if (settings.requiresDeclaredWinner()) {
                status = Status.AWAITING_TIEBREAKER_OR_JUDGE;
                judgeRecords.add(new JudgeRecord(clock.instant(), "系统（待裁判确认）", "最大局数后仍无胜者；请启动决胜局或由裁判裁定。"));
            } else complete(null, true, "已达到最大局数，赛事允许以平局记录");
        } else status = Status.READY;
    }

    private void complete(UUID winner, boolean draw, String conclusionZh) {
        result = new SeriesResult(winner, draw, conclusionZh);
        status = Status.COMPLETE;
    }
    private void requireInProgressOrAwaiting() {
        if (status != Status.PLAYING && status != Status.FINISH_ACTIVE_TURN && status != Status.EXTRA_TURNS
            && status != Status.AWAITING_TIEBREAKER_OR_JUDGE) throw new IllegalStateException("当前没有可记录结果的对局");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
