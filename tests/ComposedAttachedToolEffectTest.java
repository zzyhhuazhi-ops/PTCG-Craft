import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.List;
import java.util.UUID;

/** JSON attached-Tool discard/return uses the same server-issued public selection boundary as attached Energy. */
public final class ComposedAttachedToolEffectTest {
    private static final UUID A = new UUID(0, 8821), B = new UUID(0, 8822), WATCH = new UUID(0, 8844);

    public static void main(String[] args) {
        discardOwnToolAndKeepPromptPrivate();
        returnOpponentToolToActualOwner();
        malformedToolProgramsFailBeforePlay();
        System.out.println("ComposedAttachedToolEffectTest passed: public Tool selection, owner-correct discard/return, privacy and schema checks");
    }

    private static void discardOwnToolAndKeepPromptPrivate() {
        ServerMatch match = ComposedEnergyAttachmentTest.fixture("{\"type\":\"discard_tool\",\"target\":\"OWN_ACTIVE\",\"amount\":1,\"minimum\":1}", 0);
        prepareTools(match, A);
        UUID active = match.activePokemonView(A).pokemonId();
        CardInstance tool = hand(match, A, "tool");
        match.playTrainer(A, tool.instanceId(), active);
        var session = MatchSession.multiplayer(match, A, B); session.addSpectator(WATCH);
        match.playTrainer(A, hand(match, A, "component").instanceId(), null);
        var prompt = match.effectCardPrompt(A);
        check(prompt.candidates().equals(List.of(tool)), "通用工具选择没有只列出当前公开附着工具");
        check(session.view(B, B).game().effectSelection() == null && session.view(WATCH, null).game().effectSelection() == null,
            "对手或旁观者收到工具选择提示");
        rejects(() -> match.submitEffectCards(B, prompt.resolutionId(), prompt.step(), List.of(tool.instanceId())));
        rejects(() -> match.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(UUID.randomUUID())));
        match.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(tool.instanceId()));
        check(match.activePokemonView(A).attachedTools().isEmpty() && match.privateView(A).discard().contains(tool),
            "组合弃置工具没有原子地移入实际拥有者弃牌区");
        check(!match.hasPendingEffectChoice(), "弃置工具后效果未完成");
    }

    private static void returnOpponentToolToActualOwner() {
        ServerMatch match = ComposedEnergyAttachmentTest.fixture("{\"type\":\"return_tool\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1,\"minimum\":1}", 10);
        match.endTurn(A);
        prepareTools(match, B);
        UUID active = match.activePokemonView(B).pokemonId();
        CardInstance tool = hand(match, B, "tool");
        match.playTrainer(B, tool.instanceId(), active);
        match.endTurn(B);
        int handBefore = match.privateView(B).hand().size();
        match.playTrainer(A, hand(match, A, "component").instanceId(), null);
        var prompt = match.effectCardPrompt(A);
        check(prompt.candidates().stream().map(CardInstance::instanceId).toList().equals(List.of(tool.instanceId())),
            "对方公开附着工具没有成为回手候选");
        match.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(tool.instanceId()));
        check(match.activePokemonView(B).attachedTools().isEmpty() && match.privateView(B).hand().contains(tool)
            && match.privateView(B).hand().size() == handBefore + 1, "工具回手没有回到实际拥有者的私有手牌");
    }

    private static void malformedToolProgramsFailBeforePlay() {
        for (String malformed : List.of(
            "{\"type\":\"discard_tool\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"amount\":1,\"minimum\":1}",
            "{\"type\":\"return_tool\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}",
            "{\"type\":\"discard_tool\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1,\"minimum\":1,\"hidden\":true}")) {
            rejects(() -> ComposedTrainerIntegrationTest.item(malformed));
        }
    }

    private static void prepareTools(ServerMatch match, UUID owner) {
        match.drawCardsByEffect(owner, owner, 60);
    }
    private static CardInstance hand(ServerMatch match, UUID owner, String id) {
        return match.privateView(owner).hand().stream().filter(card -> card.definitionId().equals(id)).findFirst().orElseThrow();
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("服务器接受了伪造的工具选择");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
