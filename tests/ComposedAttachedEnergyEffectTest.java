import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.*;

/** JSON attached-Energy discard/return uses one server-issued public selection and preserves actual card ownership. */
public final class ComposedAttachedEnergyEffectTest {
    private static final UUID A = new UUID(0, 8821), B = new UUID(0, 8822), WATCH = new UUID(0, 8833);
    public static void main(String[] args) {
        discardAttachedEnergy();
        returnOpponentEnergy();
        moveAttachedEnergy();
        System.out.println("ComposedAttachedEnergyEffectTest passed: public Energy selection, discard, return, move and private prompt routing");
    }
    private static void discardAttachedEnergy() {
        ServerMatch match = ComposedEnergyAttachmentTest.fixture("{\"type\":\"discard_energy\",\"target\":\"OWN_ACTIVE\",\"amount\":1,\"minimum\":1}", 0);
        UUID active = match.activePokemonView(A).pokemonId();
        CardInstance energy = hand(match, A, "grass");
        match.attachEnergy(A, energy.instanceId(), active);
        var session = MatchSession.multiplayer(match, A, B); session.addSpectator(WATCH);
        match.playTrainer(A, trainer(match).instanceId(), null);
        var prompt = match.effectCardPrompt(A);
        check(prompt.candidates().equals(List.of(energy)), "附着能量选择没有只列出公开宿主上的能量");
        check(session.view(B, B).game().effectSelection() == null && session.view(WATCH, null).game().effectSelection() == null,
            "非操作者收到了附着能量选择提示");
        rejects(() -> match.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(UUID.randomUUID())));
        match.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(energy.instanceId()));
        check(match.activePokemonView(A).attachedEnergyIds().isEmpty() && match.privateView(A).discard().contains(energy),
            "通用弃置附着能量没有转入其拥有者弃牌区");
        check(!match.hasPendingEffectChoice(), "附着能量弃置后没有继续完成卡牌效果");
    }
    private static void returnOpponentEnergy() {
        ServerMatch match = ComposedEnergyAttachmentTest.fixture("{\"type\":\"return_energy\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1,\"minimum\":1}", 0);
        UUID active = match.activePokemonView(B).pokemonId();
        CardInstance energy = hand(match, B, "grass");
        match.attachEnergyByEffect(A, B, EffectSourceZone.HAND, energy.instanceId(), B, active);
        int handBefore = match.privateView(B).hand().size();
        match.playTrainer(A, trainer(match).instanceId(), null);
        var prompt = match.effectCardPrompt(A);
        check(prompt.candidates().stream().map(CardInstance::instanceId).toList().equals(List.of(energy.instanceId())),
            "对方公开附着能量没有成为回手选择候选");
        match.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(energy.instanceId()));
        check(match.activePokemonView(B).attachedEnergyIds().isEmpty() && match.privateView(B).hand().contains(energy)
            && match.privateView(B).hand().size() == handBefore + 1, "附着能量没有回到实际拥有者手牌");
    }
    private static void moveAttachedEnergy() {
        ServerMatch match = ComposedEnergyAttachmentTest.fixture("{\"type\":\"move_energy\",\"target\":\"OWN_ACTIVE\",\"amount\":1,\"minimum\":1,\"destination_scope\":\"OWN_BENCH\"}", 0);
        UUID source = match.activePokemonView(A).pokemonId();
        UUID destination = match.benchPokemonView(A).getFirst().pokemonId();
        CardInstance energy = hand(match, A, "grass");
        match.attachEnergy(A, energy.instanceId(), source);
        match.playTrainer(A, trainer(match).instanceId(), null);
        var selected = match.effectCardPrompt(A);
        match.submitEffectCards(A, selected.resolutionId(), selected.step(), List.of(energy.instanceId()));
        var target = match.effectAttachmentPrompt(A);
        check(target.actionZh().equals("移动能量") && target.targets().contains(destination) && !target.targets().contains(source),
            "移动能量没有只给出合法目的地");
        rejects(() -> match.chooseEffectAttachmentTarget(A, target.resolutionId(), target.step(), source));
        match.chooseEffectAttachmentTarget(A, target.resolutionId(), target.step(), destination);
        check(!match.activePokemonView(A).attachedEnergyIds().contains(energy.instanceId())
            && match.benchPokemonView(A).getFirst().attachedEnergyIds().contains(energy.instanceId()), "通用移动附着能量没有改变正确的公开宿主");
    }
    private static CardInstance hand(ServerMatch match, UUID owner, String id) {
        return match.privateView(owner).hand().stream().filter(card -> card.definitionId().equals(id)).findFirst().orElseThrow();
    }
    private static CardInstance trainer(ServerMatch match) { return hand(match, A, "component"); }
    private static void rejects(Runnable action) { try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; } throw new AssertionError("服务器接受了伪造的附着能量选择"); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
