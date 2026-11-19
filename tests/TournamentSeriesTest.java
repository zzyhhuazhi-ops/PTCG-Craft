import dev.hua.ptcglive.engine.MatchResult;
import dev.hua.ptcglive.engine.tournament.TiebreakerPolicy;
import dev.hua.ptcglive.engine.tournament.TournamentMatchFormat;
import dev.hua.ptcglive.engine.tournament.TournamentSeries;
import dev.hua.ptcglive.engine.tournament.TournamentSettings;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/** Test-first coverage for official time, score and Tiebreaker Game handling. */
public final class TournamentSeriesTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000071");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000072");

    public static void main(String[] args) {
        TournamentSettings bestOfThree = new TournamentSettings("international-bo3", "国际三局两胜", TournamentMatchFormat.BEST_OF_THREE,
            50, true, TiebreakerPolicy.OFFICIAL_FULL_PRIZE);
        TournamentSeries series = new TournamentSeries(A, B, bestOfThree, Clock.fixed(Instant.parse("2026-09-29T00:00:00Z"), ZoneOffset.UTC));
        series.startGame();
        series.callTime(TournamentSeries.TimeCallPoint.DURING_TURN);
        assertStatus(series, TournamentSeries.Status.FINISH_ACTIVE_TURN);
        series.completeTurn();
        assertExtraTurns(series, 3);
        series.completeTurn(); series.completeTurn(); series.completeTurn();
        assertStatus(series, TournamentSeries.Status.AWAITING_TIEBREAKER_OR_JUDGE);
        TournamentSeries.TiebreakerSetup setup = series.beginTiebreaker("限时结束后赛事必须产生胜者");
        if (setup.prizeCardsPerPlayer() != 6 || !setup.winsOnFirstPrizeAdvantage() || setup.policy() != TiebreakerPolicy.OFFICIAL_FULL_PRIZE)
            throw new AssertionError("国际决胜局必须正常设置 6 张奖赏并以首次奖赏优势获胜");
        series.recordTiebreakerWinner(A, "先取得奖赏优势");
        if (series.score().wins(A) != 1 || series.score().wins(B) != 0 || series.games().size() != 1)
            throw new AssertionError("决胜局没有正确记录为该局胜者");

        series.startGame();
        series.recordGameWinner(A, new MatchResult(A, MatchResult.Reason.PRIZES), "拿完奖赏卡");
        if (!series.isComplete() || !series.result().winner().equals(A) || series.score().wins(A) != 2)
            throw new AssertionError("三局两胜的比分或系列赛结果错误");
        try { series.startGame(); throw new AssertionError("已结束赛事仍可开始新局"); }
        catch (IllegalStateException expected) { }

        TournamentSettings casual = new TournamentSettings("custom-sudden-death", "自定义单奖赏加赛", TournamentMatchFormat.SINGLE_GAME,
            30, true, TiebreakerPolicy.LEGACY_SINGLE_PRIZE_SUDDEN_DEATH);
        TournamentSeries legacy = new TournamentSeries(A, B, casual, Clock.systemUTC());
        legacy.startGame(); legacy.callTime(TournamentSeries.TimeCallPoint.DURING_CHECKUP);
        legacy.completeTurn(); legacy.completeTurn(); legacy.completeTurn();
        TournamentSeries.TiebreakerSetup legacySetup = legacy.beginTiebreaker("房间明确选择旧规则");
        if (legacySetup.prizeCardsPerPlayer() != 1 || legacySetup.policy() != TiebreakerPolicy.LEGACY_SINGLE_PRIZE_SUDDEN_DEATH)
            throw new AssertionError("单奖赏 Sudden Death 只能在显式自定义规则中出现");

        TournamentSettings swiss = new TournamentSettings("international-swiss", "国际瑞士轮单局", TournamentMatchFormat.SINGLE_GAME,
            30, false, TiebreakerPolicy.OFFICIAL_FULL_PRIZE);
        TournamentSeries draw = new TournamentSeries(A, B, swiss, Clock.systemUTC());
        draw.startGame(); draw.callTime(TournamentSeries.TimeCallPoint.DURING_TURN); draw.completeTurn();
        draw.completeTurn(); draw.completeTurn(); draw.completeTurn();
        draw.recordGameDraw("瑞士轮允许未分胜负的对局记为平局");
        if (!draw.isComplete() || !draw.result().draw() || draw.games().getFirst().winner() != null)
            throw new AssertionError("允许平局的赛事未正确记录平局结果");
        if (draw.judgeRecords().isEmpty()) throw new AssertionError("时间与裁判记录必须可复盘");
        System.out.println("TournamentSeriesTest passed: 单局/三局两胜、限时+3回合、赛事决胜局、自定义 Sudden Death 与裁判记录均已验证");
    }

    private static void assertStatus(TournamentSeries series, TournamentSeries.Status expected) {
        if (series.status() != expected) throw new AssertionError("赛事状态应为 " + expected + "，实际为 " + series.status());
    }
    private static void assertExtraTurns(TournamentSeries series, int expected) {
        if (series.remainingExtraTurns() != expected) throw new AssertionError("剩余加时回合错误: " + series.remainingExtraTurns());
    }
}
