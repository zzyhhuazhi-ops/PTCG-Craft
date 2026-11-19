import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.*;

/**
 * “Search your deck for up to N basic Energy cards …” / “Put N basic Energy
 * cards from your discard pile into your hand.”: basic_energy_only offers
 * Basic Energy cards only; Special Energy is never a candidate.
 */
public final class BasicEnergySelectionTest {
    private static final UUID A = new UUID(0, 8821);
    public static void main(String[] args) {
        deckSearch(); discardRecovery(); forgedSpecial(); schema();
        System.out.println("BasicEnergySelectionTest passed: basic-Energy selections exclude Special Energy server-side");
    }
    private static Set<String> ids(List<CardInstance> cards) { var s = new HashSet<String>(); for (var c : cards) s.add(c.definitionId()); return s; }
    private static void deckSearch() {
        ServerMatch m = ComposedEnergyAttachmentTest.fixture("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":2,\"minimum\":0,"
            + "\"card_category\":\"ENERGY\",\"basic_energy_only\":true}", 0);
        m.playTrainer(A, hand(m, "component").instanceId(), null);
        var prompt = m.effectCardPrompt(A);
        check(!prompt.candidates().isEmpty() && ids(prompt.candidates()).stream().allMatch(id -> id.equals("grass") || id.equals("fire")),
            "牌库检索基本能量的候选里出现了非基本能量：" + ids(prompt.candidates()));
        var two = prompt.candidates().stream().limit(2).map(CardInstance::instanceId).toList();
        int hand = m.privateView(A).hand().size();
        m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), two);
        check(m.privateView(A).hand().size() == hand + 2, "所选基本能量没有加入手牌");
    }
    private static void discardRecovery() {
        ServerMatch m = ComposedEnergyAttachmentTest.fixture("{\"type\":\"search_discard_pile\",\"target\":\"DISCARD\",\"amount\":1,\"minimum\":1,"
            + "\"card_category\":\"ENERGY\",\"basic_energy_only\":true,\"destination\":\"HAND\"}", 0);
        var special = m.privateView(A).deck().stream().filter(c -> c.definitionId().equals("double")).findFirst().orElseThrow();
        var basic = m.privateView(A).deck().stream().filter(c -> c.definitionId().equals("fire")).findFirst().orElseThrow();
        moveToDiscard(m, special); moveToDiscard(m, basic);
        m.playTrainer(A, hand(m, "component").instanceId(), null);
        var prompt = m.effectCardPrompt(A);
        check(prompt.candidates().stream().noneMatch(c -> c.instanceId().equals(special.instanceId()))
            && prompt.candidates().stream().anyMatch(c -> c.instanceId().equals(basic.instanceId())), "弃牌区取回基本能量的候选错误");
    }
    private static void forgedSpecial() {
        ServerMatch m = ComposedEnergyAttachmentTest.fixture("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":0,"
            + "\"card_category\":\"ENERGY\",\"basic_energy_only\":true}", 0);
        var special = m.privateView(A).deck().stream().filter(c -> c.definitionId().equals("double")).findFirst().orElseThrow();
        m.playTrainer(A, hand(m, "component").instanceId(), null);
        var prompt = m.effectCardPrompt(A);
        reject(() -> m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(special.instanceId())));
    }
    private static void moveToDiscard(ServerMatch m, CardInstance card) {
        // Put a known card into the discard pile through the server's own deck-search and hand-discard primitives.
        m.searchOwnDeckToHandByEffect(A, List.of(card.instanceId()));
        m.discardHandCardsByEffect(A, A, List.of(card.instanceId()));
    }
    private static void schema() {
        var effect = EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":0,"
            + "\"basic_energy_only\":true}").getAsJsonObject());
        reject(() -> ZoneSelectionEffect.forProgram(effect));
        var bad = EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":0,"
            + "\"card_category\":\"ENERGY\",\"basic_energy_only\":\"yes\"}").getAsJsonObject());
        reject(() -> ZoneSelectionEffect.forProgram(bad));
    }
    private static CardInstance hand(ServerMatch match, String id) {
        return match.privateView(A).hand().stream().filter(card -> card.definitionId().equals(id)).findFirst().orElseThrow();
    }
    private static void reject(Runnable action) { try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; } throw new AssertionError("非法的基本能量筛选被接受"); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
