import dev.hua.ptcglive.engine.EffectSourceZone;
import dev.hua.ptcglive.engine.ServerMatch;
import java.util.UUID;

/** Tool effects share the server's ordinary capacity calculation instead of bypassing it. */
public final class ToolAttachmentEffectTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        UUID target = match.activePokemonView(PersistentTrainerTest.B).pokemonId();
        var tool = PersistentTrainerTest.hand(match, PersistentTrainerTest.B, "balloon");
        match.attachToolByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, EffectSourceZone.HAND, tool.instanceId(), PersistentTrainerTest.B, target);
        if (!match.activePokemonView(PersistentTrainerTest.B).attachedTools().contains(tool)
            || match.privateView(PersistentTrainerTest.B).hand().contains(tool))
            throw new AssertionError("服务器效果没有正确附着来源手牌中的工具");
        var before = match.clientView(PersistentTrainerTest.A);
        var otherTool = PersistentTrainerTest.hand(match, PersistentTrainerTest.B, "tool-extra");
        try { match.attachToolByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, EffectSourceZone.HAND, otherTool.instanceId(), PersistentTrainerTest.A, match.activePokemonView(PersistentTrainerTest.A).pokemonId()); }
        catch (IllegalStateException expected) {
            if (!before.equals(match.clientView(PersistentTrainerTest.A))) throw new AssertionError("跨方工具附着没有原子拒绝");
        }
        try { match.attachToolByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, EffectSourceZone.HAND, tool.instanceId(), PersistentTrainerTest.B, target); }
        catch (IllegalArgumentException expected) {
            if (!match.replayTranscript().getLast().operation().equals("已审核效果附着宝可梦工具"))
                throw new AssertionError("工具效果附着没有进入服务器回放");
            PersistentTrainerTest.conserve(match);
            System.out.println("ToolAttachmentEffectTest passed: server Tool attachment validates source identity and shares capacity rules");
            return;
        }
        throw new AssertionError("已附着工具被再次从来源区域附着");
    }
}
