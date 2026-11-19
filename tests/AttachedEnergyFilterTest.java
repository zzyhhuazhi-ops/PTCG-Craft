import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.EffectRegistry;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.*;

/**
 * “Discard a {R} Energy from …” / “Discard a Special Energy from …”: attached
 * Energy selection offers only cards currently providing that type, or of that
 * card kind. The server rechecks the issued candidate list at submission.
 */
public final class AttachedEnergyFilterTest {
    private static final UUID A = new UUID(0, 8821), B = new UUID(0, 8822);
    public static void main(String[] args) {
        candidates("\"energy_type\":\"FIRE\"", Set.of("fire"));
        candidates("\"card_kind\":\"SPECIAL\"", Set.of("double"));
        candidates("\"card_kind\":\"BASIC\"", Set.of("grass", "fire"));
        candidates("\"energy_type\":\"COLORLESS\",\"card_kind\":\"SPECIAL\"", Set.of("double"));
        allOfType();
        forgedSelection();
        var registry = EffectRegistry.standard();
        for (String bad : List.of("\"energy_type\":\"PINK\"", "\"card_kind\":\"RAINBOW\""))
            rejects(() -> registry.parse(JsonParser.parseString("{\"type\":\"discard_energy\",\"target\":\"OWN_ACTIVE\",\"amount\":1,\"minimum\":1," + bad + "}").getAsJsonObject()));
        System.out.println("AttachedEnergyFilterTest passed: attached Energy selection filters by current provided type and card kind");
    }
    private static ServerMatch fixture(String effect) {
        ServerMatch match = ComposedEnergyAttachmentTest.fixture(effect, 0);
        UUID active = match.activePokemonView(A).pokemonId();
        match.attachEnergy(A, hand(match, "grass").instanceId(), active);
        for (String id : List.of("fire", "double")) {
            var card = match.privateView(A).deck().stream().filter(c -> c.definitionId().equals(id)).findFirst().orElseThrow();
            match.attachEnergyByEffect(A, A, EffectSourceZone.DECK, card.instanceId(), A, active);
        }
        return match;
    }
    private static void candidates(String filter, Set<String> expected) {
        ServerMatch match = fixture("{\"type\":\"discard_energy\",\"target\":\"OWN_ACTIVE\",\"amount\":1,\"minimum\":1," + filter + "}");
        match.playTrainer(A, hand(match, "component").instanceId(), null);
        var prompt = match.effectCardPrompt(A);
        var ids = new HashSet<String>(); for (var card : prompt.candidates()) ids.add(card.definitionId());
        check(ids.equals(expected) && prompt.candidates().size() == expected.size(), "筛选 " + filter + " 的候选能量错误：" + ids);
        var chosen = prompt.candidates().getFirst();
        match.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(chosen.instanceId()));
        check(match.privateView(A).discard().contains(chosen) && match.activePokemonView(A).attachedEnergyIds().size() == 2, "筛选后的能量没有被弃置");
    }
    private static void allOfType() {
        ServerMatch match = fixture("{\"type\":\"discard_energy\",\"target\":\"OWN_ACTIVE\",\"all\":true,\"energy_type\":\"GRASS\"}");
        var attached = match.activePokemonView(A).attachedEnergyIds();
        match.playTrainer(A, hand(match, "component").instanceId(), null);
        check(match.activePokemonView(A).attachedEnergyIds().size() == attached.size() - 1
            && match.privateView(A).discard().stream().filter(c -> c.definitionId().equals("grass")).count() == 1
            && match.privateView(A).discard().stream().noneMatch(c -> c.definitionId().equals("fire") || c.definitionId().equals("double")),
            "all:true 加属性筛选没有只弃置该属性能量");
    }
    private static void forgedSelection() {
        ServerMatch match = fixture("{\"type\":\"discard_energy\",\"target\":\"OWN_ACTIVE\",\"amount\":1,\"minimum\":1,\"energy_type\":\"FIRE\"}");
        var grass = match.activePokemonView(A).attachedEnergyIds().getFirst();
        match.playTrainer(A, hand(match, "component").instanceId(), null);
        var prompt = match.effectCardPrompt(A);
        rejects(() -> match.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(grass)));
        check(match.activePokemonView(A).attachedEnergyIds().contains(grass), "不符合筛选的能量被弃置");
    }
    private static CardInstance hand(ServerMatch match, String id) {
        return match.privateView(A).hand().stream().filter(card -> card.definitionId().equals(id)).findFirst().orElseThrow();
    }
    private static void rejects(Runnable action) { try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; } throw new AssertionError("非法的附着能量筛选被接受"); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
