import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.List;
import java.util.UUID;

/** Attached Tool removal is public, atomic, owner-correct and unavailable to client intents. */
public final class ToolRemovalEffectTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        UUID active = match.activePokemonView(PersistentTrainerTest.A).pokemonId();
        CardInstance capacityTool = PersistentTrainerTest.hand(match, PersistentTrainerTest.A, "tool-extra");
        CardInstance ordinaryTool = PersistentTrainerTest.hand(match, PersistentTrainerTest.A, "balloon");
        match.playTrainer(PersistentTrainerTest.A, capacityTool.instanceId(), active);
        match.playTrainer(PersistentTrainerTest.A, ordinaryTool.instanceId(), active);
        match.discardToolByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A, active, capacityTool.instanceId());
        var activeView = match.activePokemonView(PersistentTrainerTest.A);
        if (!activeView.attachedTools().equals(List.of(ordinaryTool)) || !match.privateView(PersistentTrainerTest.A).discard().contains(capacityTool))
            throw new AssertionError("工具移除没有从场上原子转入正确拥有者弃牌区");
        try { match.discardToolByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A, active, capacityTool.instanceId()); throw new AssertionError("同一工具被重复弃置"); }
        catch (IllegalArgumentException expected) { }
        if (!match.replayTranscript().getLast().operation().equals("已审核效果弃置场上的宝可梦工具"))
            throw new AssertionError("工具移除没有进入服务器回放");
        System.out.println("ToolRemovalEffectTest passed: attached Tool discard is owner-correct, atomic and replayed");
    }
}
