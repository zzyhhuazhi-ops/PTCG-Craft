import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.UUID;

/** Tool suppression is temporary, preserves attachments and is resolved only by the server. */
public final class ToolDisableEffectTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        UUID active = match.activePokemonView(PersistentTrainerTest.A).pokemonId();
        CardInstance balloon = PersistentTrainerTest.hand(match, PersistentTrainerTest.A, "balloon");
        match.playTrainer(PersistentTrainerTest.A, balloon.instanceId(), active);
        if (match.activeRetreatCost(PersistentTrainerTest.A) != 1) throw new AssertionError("测试夹具工具减费没有生效");

        match.disableToolEffectsByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A, active);
        if (match.activeRetreatCost(PersistentTrainerTest.A) != 3
            || !match.activePokemonView(PersistentTrainerTest.A).continuousEffectZh().contains("工具失效")
            || !match.activePokemonView(PersistentTrainerTest.A).attachedTools().contains(balloon))
            throw new AssertionError("工具失效没有保留附着卡并屏蔽其撤退效果");

        CardInstance extra = PersistentTrainerTest.hand(match, PersistentTrainerTest.A, "tool-extra");
        var before = match.clientView(PersistentTrainerTest.A);
        rejects(() -> match.playTrainer(PersistentTrainerTest.A, extra.instanceId(), active));
        if (!before.equals(match.clientView(PersistentTrainerTest.A)))
            throw new AssertionError("工具失效期间的额外工具槽拒绝没有保持原子性");

        match.endTurn(PersistentTrainerTest.A); match.endTurn(PersistentTrainerTest.B);
        if (match.activeRetreatCost(PersistentTrainerTest.A) != 1
            || match.activePokemonView(PersistentTrainerTest.A).continuousEffectZh().contains("工具失效"))
            throw new AssertionError("工具失效没有在效果拥有者下回合开始时恢复");
        match.playTrainer(PersistentTrainerTest.A, extra.instanceId(), active);
        if (match.activePokemonView(PersistentTrainerTest.A).attachedTools().size() != 2)
            throw new AssertionError("工具效果恢复后额外工具容量没有重新生效");
        if (!match.replayTranscript().stream().anyMatch(event -> event.operation().equals("已审核效果使宝可梦工具失效")))
            throw new AssertionError("工具失效没有记录公开回放事实");
        System.out.println("ToolDisableEffectTest passed: attached tools are suppressed server-side, restored on expiry, and never discarded");
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("服务器错误允许失效工具提供额外槽位");
    }
}
