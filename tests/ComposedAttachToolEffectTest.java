import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.List;
import java.util.UUID;

/** Tool attachment is a private source choice followed by a server-issued public own-field target. */
public final class ComposedAttachToolEffectTest {
    private static final UUID A = new UUID(0, 8821), B = new UUID(0, 8822), WATCH = new UUID(0, 8855);
    private static final String ATTACH = "{\"type\":\"attach_tool\",\"target\":\"SELECT_OWN_POKEMON\",\"source\":\"HAND\",\"amount\":1,\"minimum\":1,\"target_scope\":\"OWN_BENCH\"}";

    public static void main(String[] args) {
        privateSourceThenPublicTarget();
        malformedProgramsFailClosed();
        System.out.println("ComposedAttachToolEffectTest passed: private Tool source, public own-field target, capacity boundary and schema checks");
    }

    private static void privateSourceThenPublicTarget() {
        ServerMatch match = ComposedEnergyAttachmentTest.fixture(ATTACH, 0);
        match.drawCardsByEffect(A, A, 60);
        CardInstance trainer = hand(match, A, "component");
        CardInstance tool = hand(match, A, "tool");
        UUID bench = match.benchPokemonView(A).getFirst().pokemonId();
        var session = MatchSession.multiplayer(match, A, B); session.addSpectator(WATCH);
        match.playTrainer(A, trainer.instanceId(), null);
        var source = match.effectCardPrompt(A);
        check(source.candidates().equals(List.of(tool)), "工具附着候选没有按工具类别和服务器目标容量过滤：" + source.candidates());
        check(session.view(B, B).game().effectSelection() == null && session.view(WATCH, null).game().effectSelection() == null,
            "对手或旁观者收到待附着工具的私有选择");
        rejects(() -> match.submitEffectCards(B, source.resolutionId(), source.step(), List.of(tool.instanceId())));
        match.submitEffectCards(A, source.resolutionId(), source.step(), List.of(tool.instanceId()));
        var target = match.effectAttachmentPrompt(A);
        check(target.actionZh().equals("附着工具") && target.targets().contains(bench)
            && !target.targets().contains(match.activePokemonView(A).pokemonId()), "工具附着没有仅给出服务器签发的己方备战目标");
        rejects(() -> match.chooseEffectAttachmentTarget(A, target.resolutionId(), target.step(), match.activePokemonView(A).pokemonId()));
        match.chooseEffectAttachmentTarget(A, target.resolutionId(), target.step(), bench);
        check(match.benchPokemonView(A).getFirst().attachedTools().equals(List.of(tool)) && !match.privateView(A).hand().contains(tool),
            "通用工具附着没有通过统一容量和区域规则完成");
        check(!match.hasPendingEffectChoice() && match.privateView(A).discard().contains(trainer), "工具附着完成后组合训练家没有正确收尾");
    }

    private static void malformedProgramsFailClosed() {
        for (String malformed : List.of(
            ATTACH.replace("\"amount\":1", "\"amount\":2"),
            ATTACH.replace("SELECT_OWN_POKEMON", "SELECT_OPPONENT_POKEMON"),
            ATTACH.replace("\"source\":\"HAND\"", "\"source\":\"LOST_ZONE\""),
            ATTACH.replace("\"target_scope\":\"OWN_BENCH\"", "\"target_scope\":\"OPPONENT_BENCH\""))) {
            rejects(() -> ComposedTrainerIntegrationTest.item(malformed));
        }
    }

    private static CardInstance hand(ServerMatch match, UUID owner, String definition) {
        return match.privateView(owner).hand().stream().filter(card -> card.definitionId().equals(definition)).findFirst().orElseThrow();
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("服务器接受了不受支持的工具附着数据或意图");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
