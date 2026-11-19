import dev.hua.ptcglive.engine.MatchPhase;
import dev.hua.ptcglive.engine.ServerMatch;
import java.util.UUID;

/** A reviewed direct-KO effect must share the ordinary discard, Prize and replacement pipeline. */
public final class DirectKnockoutEffectTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        UUID active = match.activePokemonView(PersistentTrainerTest.B).pokemonId();
        UUID bench = match.benchPokemonView(PersistentTrainerTest.B).getFirst().pokemonId();
        UUID physical=PokemonIdentityTestSupport.physical(match,PersistentTrainerTest.B,bench);
        var before = match.clientView(PersistentTrainerTest.A);
        rejects(() -> match.knockOutPokemonByEffect(PersistentTrainerTest.A, ServerMatch.InPlayTargetScope.OPPONENT_BENCH,
            PersistentTrainerTest.B, UUID.randomUUID()));
        check(before.equals(match.clientView(PersistentTrainerTest.A)), "无效直接击倒目标改变了服务器状态");
        rejects(() -> match.knockOutPokemonByEffect(PersistentTrainerTest.A, ServerMatch.InPlayTargetScope.OPPONENT_BENCH,
            PersistentTrainerTest.B, active));
        check(before.equals(match.clientView(PersistentTrainerTest.A)), "范围外直接击倒目标改变了服务器状态");

        match.knockOutPokemonByEffect(PersistentTrainerTest.A, ServerMatch.InPlayTargetScope.OPPONENT_BENCH,
            PersistentTrainerTest.B, bench);
        check(match.phase() == MatchPhase.PRIZE_SELECTION && match.pendingChoicePlayer().equals(PersistentTrainerTest.A),
            "直接击倒备战宝可梦没有进入统一奖赏选择");
        check(match.benchPokemonView(PersistentTrainerTest.B).isEmpty() && match.activePokemonView(PersistentTrainerTest.B).pokemonId().equals(active),
            "直接击倒备战宝可梦错误影响了战斗宝可梦");
        check(match.privateView(PersistentTrainerTest.B).discard().stream().anyMatch(card -> card.instanceId().equals(physical)),
            "直接击倒没有将宝可梦放入其拥有者弃牌区");
        var operations=match.replayTranscript().stream().map(event->event.operation()).toList();
        int direct=operations.indexOf("已审核效果直接击倒宝可梦");
        check(direct>=0 && operations.subList(direct+1,operations.size()).contains("击倒触发全部完成；同时移场并结算奖赏"),
            "直接击倒及随后统一移场未按顺序记录公开回放事实");
        System.out.println("DirectKnockoutEffectTest passed: direct KO uses server Prize, discard and public replay resolution");
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("服务器错误接受了不存在的直接击倒目标");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
