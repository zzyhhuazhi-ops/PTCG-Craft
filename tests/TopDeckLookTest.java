import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.view.MatchRegionModel;
import java.util.*;

/** Great Ball's real SD100 data exercises private inspection, selection, reveal and shuffle end to end. */
public final class TopDeckLookTest {
    static final UUID A = StarterDeck100CombatTest.A, B = StarterDeck100CombatTest.B, WATCHER = new UUID(0, 37777);
    static final String BALL = "sd100_cn_373";
    public static void main(String[] args) {
        windowAndPrivacy(); decline(); emptyEligibleWindow(); smallAndEmptyDeck(); malformedData();
        System.out.println("TopDeckLookTest passed: top-seven window, ineligible faces, no hidden leaks, atomic forged/stale rejection, reveal, decline, shuffle and short decks");
    }
    static void check(boolean ok, String message) { StarterDeck100CombatTest.check(ok, message); }
    static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Invalid selection was accepted");
    }
    static List<CardInstance> top(ServerMatch m) {
        var deck = m.privateView(A).deck(); return List.copyOf(deck.subList(Math.max(0, deck.size() - 7), deck.size()));
    }
    static void play(ServerMatch m) { m.playTrainer(A, StarterDeck100CombatTest.hand(m, BALL).instanceId(), null); }
    static void windowAndPrivacy() {
        var m = StarterDeck100CombatTest.game(BALL); var expected = top(m);
        check(expected.stream().anyMatch(c -> m.cardDefinition(c.definitionId()).category() != CardCategory.POKEMON), "fixture needs ineligible cards");
        var session = MatchSession.multiplayer(m, A, B); session.addSpectator(WATCHER);
        var beforeDeck = m.privateView(A).deck(); int randomBefore = m.randomTranscript().size();
        play(m); var prompt = m.effectCardPrompt(A);
        check(prompt.inspectedCards().size() == 7 && new HashSet<>(prompt.inspectedCards()).equals(new HashSet<>(expected)), "wrong seven cards inspected");
        var legal = expected.stream().filter(c -> m.cardDefinition(c.definitionId()).category() == CardCategory.POKEMON).toList();
        check(!legal.isEmpty() && new HashSet<>(prompt.candidates()).equals(new HashSet<>(legal)), "filter applied before top window");
        check(m.privateView(A).deck().equals(beforeDeck) && m.randomTranscript().size() == randomBefore, "inspection moved or shuffled cards prematurely");
        var ownerView = session.view(A, A); var region = MatchRegionModel.from(ownerView);
        check(region.zone("effect-selection").cards().size() == 7, "ineligible inspected faces are absent in UI");
        for (var view : List.of(session.view(B, B), session.view(WATCHER, null))) {
            check(view.game().effectSelection() == null, "private prompt exposed");
            String json = new com.google.gson.Gson().toJson(view);
            for (var c : expected) check(!json.contains(c.instanceId().toString()), "inspected instance leaked");
        }
        for (var c : prompt.inspectedCards()) if (!prompt.candidates().contains(c))
            check(ownerView.options().stream().noneMatch(o -> o.cardKeys().contains("effect:" + c.instanceId())), "ineligible face got a selection action");
        var ineligible = expected.stream().filter(c -> !legal.contains(c)).findFirst().orElseThrow();
        var outside = beforeDeck.stream().filter(c -> !expected.contains(c)).findFirst().orElseThrow();
        var before = m.privateView(A);
        rejects(() -> m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(ineligible.instanceId())));
        rejects(() -> m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(outside.instanceId())));
        rejects(() -> m.submitEffectCards(B, prompt.resolutionId(), prompt.step(), List.of(legal.getFirst().instanceId())));
        rejects(() -> m.submitEffectCards(A, prompt.resolutionId(), prompt.step() + 1, List.of()));
        rejects(() -> m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(legal.getFirst().instanceId(), legal.getFirst().instanceId())));
        check(before.equals(m.privateView(A)) && randomBefore == m.randomTranscript().size(), "rejected selection mutated state");
        var selected = legal.getFirst();
        m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(selected.instanceId()));
        var reveal = m.effectCardReveal();
        check(reveal != null && reveal.cardIds().equals(List.of(selected.definitionId())), "selected card not revealed alone");
        check(!m.privateView(A).hand().contains(selected), "card transferred before opponent acknowledgement");
        m.acknowledgeEffectReveal(B, reveal.id());
        check(m.privateView(A).hand().contains(selected) && !m.hasPendingEffectChoice(), "chosen card not transferred");
        var remaining = new HashSet<>(beforeDeck); remaining.remove(selected);
        check(remaining.equals(new HashSet<>(m.privateView(A).deck())) && m.randomTranscript().size() > randomBefore, "remainder not conserved and shuffled");
        rejects(() -> m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(selected.instanceId())));
    }
    static void decline() {
        var m = StarterDeck100CombatTest.game(BALL); var deck = new HashSet<>(m.privateView(A).deck());
        int random = m.randomTranscript().size(); play(m); var p = m.effectCardPrompt(A);
        m.submitEffectCards(A, p.resolutionId(), p.step(), List.of());
        check(!m.hasPendingEffectChoice() && m.effectCardReveal() == null && deck.equals(new HashSet<>(m.privateView(A).deck()))
            && m.randomTranscript().size() > random, "declined search did not shuffle unchanged deck");
    }
    static ServerMatch withoutDeckPokemon() {
        for (int seed = 0; seed < 100; seed++) {
            var deck = new ArrayList<CardInstance>(); deck.add(StarterDeck100CombatTest.card(A, "sd100_cn_001"));
            for (int i = 1; i < 60; i++) deck.add(StarterDeck100CombatTest.card(A, i < 25 ? BALL : "sd100_cn_grass_energy"));
            var m = new ServerMatch(A, B, deck, StarterDeck100CombatTest.syntheticDeck(B, "sd100_cn_005", null), StarterDeck100CombatTest.RULE,
                StarterDeck100CombatTest.CARDS, StarterDeck100CombatTest.COMBAT, new ServerRandom(seed));
            OpeningTestSupport.complete(m, A, B, 0);
            if (m.privateView(A).hand().stream().anyMatch(c -> c.definitionId().equals(BALL))) return m;
        }
        throw new AssertionError("No test fixture");
    }
    static void emptyEligibleWindow() {
        var m = withoutDeckPokemon(); play(m); var p = m.effectCardPrompt(A);
        check(p.candidates().isEmpty() && p.inspectedCards().size() == 7 && m.hasPendingEffectChoice(), "ineligible top seven skipped inspection");
        var session = MatchSession.multiplayer(m, A, B);
        var view = session.view(A, A);
        var confirm = view.options().stream().filter(o -> o.labelZh().startsWith("确认选牌")).findFirst().orElseThrow();
        session.submit(A, A, view.revision(), confirm.index());
        check(!m.hasPendingEffectChoice(), "empty eligible window cannot finish");
    }
    static void smallAndEmptyDeck() {
        for (int remaining : List.of(3, 1, 0)) {
            var m = withoutDeckPokemon(); m.discardTopDeckCardsByEffect(A, A, m.privateView(A).deck().size() - remaining);
            var before = m.privateView(A); var trainer = StarterDeck100CombatTest.hand(m, BALL);
            if (remaining == 0) {
                rejects(() -> m.playTrainer(A, trainer.instanceId(), null));
                check(before.equals(m.privateView(A)), "empty-deck rejection consumed Great Ball");
            } else {
                play(m); var p = m.effectCardPrompt(A);
                check(p.inspectedCards().size() == remaining, "short deck must inspect every remaining card");
                m.submitEffectCards(A, p.resolutionId(), p.step(), List.of());
                check(m.privateView(A).deck().size() == remaining, "short-deck search lost cards");
            }
        }
    }
    static void malformedData() {
        var registry = EffectRegistry.standard();
        for (String json : List.of(
            "{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":0,\"top_count\":0}",
            "{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":0,\"top_count\":61}",
            "{\"type\":\"discard\",\"target\":\"HAND\",\"amount\":1,\"minimum\":0,\"top_count\":7}",
            "{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":8,\"minimum\":0,\"top_count\":7}"))
            rejects(() -> ZoneSelectionEffect.from(registry.parse(com.google.gson.JsonParser.parseString(json).getAsJsonObject())));
    }
}
