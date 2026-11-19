import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.List;
import java.util.UUID;

/** A card-declared free retreat is server-selected, does not spend Energy and never consumes the manual retreat action. */
public final class ComposedRetreatEffectTest {
    private static final UUID A = new UUID(0, 8821), B = new UUID(0, 8822), WATCH = new UUID(0, 8866);
    private static final String RETREAT = "{\"type\":\"retreat\",\"target\":\"SELECT_OWN_POKEMON\",\"mode\":\"FREE\"}";

    public static void main(String[] args) {
        freeRetreatUsesIssuedBenchTarget();
        paidOrMalformedRetreatFailsClosed();
        System.out.println("ComposedRetreatEffectTest passed: free card-effect retreat uses issued Bench target without Energy or manual-retreat consumption");
    }

    private static void freeRetreatUsesIssuedBenchTarget() {
        ServerMatch match = ComposedEnergyAttachmentTest.fixture(RETREAT, 0);
        UUID active = match.activePokemonView(A).pokemonId();
        UUID bench = match.benchPokemonView(A).getFirst().pokemonId();
        CardInstance energy = hand(match, A, "grass");
        CardInstance trainer = hand(match, A, "component");
        match.attachEnergy(A, energy.instanceId(), active);
        var session = MatchSession.multiplayer(match, A, B); session.addSpectator(WATCH);
        match.playTrainer(A, trainer.instanceId(), null);
        var prompt = match.effectPokemonPrompt(A);
        check(prompt.targets().stream().anyMatch(target -> target.pokemonId().equals(bench)), "免费撤退没有给出当前己方备战候选");
        check(session.view(B, B).game().choicePlayer() == null || !session.view(B, B).game().choicePlayer().equals(B),
            "对方被错误设为己方免费撤退的选择者");
        rejects(() -> match.chooseEffectPokemon(A, prompt.resolutionId(), prompt.step(), active));
        rejects(() -> match.chooseEffectPokemon(B, prompt.resolutionId(), prompt.step(), bench));
        match.chooseEffectPokemon(A, prompt.resolutionId(), prompt.step(), bench);
        check(match.activePokemonView(A).pokemonId().equals(bench)
            && match.benchPokemonView(A).stream().anyMatch(view -> view.pokemonId().equals(active) && view.attachedEnergyIds().contains(energy.instanceId())),
            "免费撤退没有保留附着能量并正确交换战斗位");
        check(!match.retreated() && !match.hasPendingEffectChoice() && match.privateView(A).discard().contains(trainer),
            "卡牌免费撤退错误消耗手动撤退次数或未完成结算");
    }

    private static void paidOrMalformedRetreatFailsClosed() {
        for (String malformed : List.of(
            RETREAT.replace("FREE", "PAY_COST"),
            RETREAT.replace("SELECT_OWN_POKEMON", "OWN_ACTIVE"),
            "{\"type\":\"retreat\",\"target\":\"SELECT_OWN_POKEMON\"}")) {
            rejects(() -> ComposedTrainerIntegrationTest.item(malformed));
        }
    }

    private static CardInstance hand(ServerMatch match, UUID owner, String id) {
        return match.privateView(owner).hand().stream().filter(card -> card.definitionId().equals(id)).findFirst().orElseThrow();
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("服务器接受了未经支持的撤退卡文或伪造目标");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
