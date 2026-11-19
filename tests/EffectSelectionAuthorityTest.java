import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Negative tests check match authority, not just an echoed client revision. */
public final class EffectSelectionAuthorityTest {
    private static final UUID A = new UUID(0, 7521), B = new UUID(0, 7522);
    private static final EffectDefinition SEARCH = effect("search_deck", EffectTarget.DECK, 2, 1);
    public static void main(String[] args) {
        staleServerState();
        differentEffectCannotReuseTransaction();
        anotherMatchCannotReuseTransaction();
        forgedCandidateContainer();
        stableCandidatesAndFailedValidation();
        discardRecoveryAndShuffle();
        malformedFilterIsRejected();
        System.out.println("EffectSelectionAuthorityTest passed: current match state and exact issued effect authorize selections");
    }
    private static void staleServerState() {
        ServerMatch match = fixture();
        var tx = match.openEffectDeckSearch(A, SEARCH, 88);
        var prompt = tx.promptFor(A);
        match.drawCardsByEffect(A, A, 1);
        // Pick a card still in the deck so membership alone cannot reject the stale transaction.
        UUID remaining = prompt.candidateIds().stream().filter(id -> match.privateView(A).deck().stream()
            .anyMatch(c -> c.instanceId().equals(id))).findFirst().orElseThrow();
        var before = match.privateView(A);
        rejects(() -> match.resolveEffectDeckSearchByRule(A, SEARCH, tx, prompt.transactionId(), prompt.revision(), List.of(remaining)));
        check(before.equals(match.privateView(A)), "陈旧选择改变了隐藏区域");
    }
    private static void differentEffectCannotReuseTransaction() {
        ServerMatch match = fixture();
        var tx = match.openEffectDeckSearch(A, SEARCH, 88);
        var prompt = tx.promptFor(A);
        EffectDefinition different = effect("search_deck", EffectTarget.DECK, 2, 0);
        var before = match.privateView(A);
        rejects(() -> match.resolveEffectDeckSearchByRule(A, different, tx, prompt.transactionId(), 88, List.of(prompt.candidateIds().getFirst())));
        check(!tx.consumed() && before.equals(match.privateView(A)), "失败的效果替换消耗了事务或移动了牌");
        match.resolveEffectDeckSearchByRule(A, SEARCH, tx, prompt.transactionId(), 88, List.of(prompt.candidateIds().getFirst()));
    }
    private static void anotherMatchCannotReuseTransaction() {
        ServerMatch first = fixture(), second = fixture();
        var optional = effect("search_deck", EffectTarget.DECK, 1, 0);
        var tx = first.openEffectDeckSearch(A, optional, 88);
        var before = second.privateView(A);
        int randomBefore = second.randomTranscript().size();
        rejects(() -> second.resolveEffectDeckSearchByRule(A, optional, tx, tx.transactionId(), 88, List.of()));
        check(!tx.consumed() && before.equals(second.privateView(A)) && randomBefore == second.randomTranscript().size(),
            "跨对局空选择被接受并触发洗牌");
    }
    private static void forgedCandidateContainer() {
        ServerMatch match = fixture();
        UUID card = match.privateView(A).deck().getFirst().instanceId();
        var forged = EffectSelectionTransaction.open(A, 88, 1, 1, EffectSelectionTransaction.Visibility.ENTITLED_PLAYER_ONLY,
            List.of(new EffectSelectionTransaction.Candidate(card, new EffectTargetResolution.ZoneTarget(A, EffectTargetResolution.Zone.DECK))));
        rejects(() -> match.resolveEffectDeckSearchByRule(A, SEARCH, forged, forged.transactionId(), 88, List.of(card)));
    }
    private static void stableCandidatesAndFailedValidation() {
        ServerMatch match = fixture();
        var tx = match.openEffectDeckSearch(A, SEARCH, 88);
        var prompt = tx.promptFor(A);
        var sorted = match.privateView(A).deck().stream().sorted(Comparator.comparing(CardInstance::definitionId)
            .thenComparing(c -> c.instanceId().toString())).map(CardInstance::instanceId).toList();
        check(prompt.candidateIds().equals(sorted), "检索展示泄露了牌库顺序或候选顺序不稳定");
        UUID chosen = prompt.candidateIds().getFirst();
        var before = match.privateView(A);
        rejects(() -> match.resolveEffectDeckSearchByRule(A, SEARCH, tx, tx.transactionId(), 88, List.of(chosen, chosen)));
        check(!tx.consumed() && before.equals(match.privateView(A)), "无效选择已消耗或改变对局");
        match.resolveEffectDeckSearchByRule(A, SEARCH, tx, tx.transactionId(), 88, List.of(chosen));
        check(tx.consumed(), "成功的选择没有消费一次性授权");
        var retry = EffectSelectionTransaction.open(A, 1, 1, 1, EffectSelectionTransaction.Visibility.PUBLIC_FIELD,
            List.of(new EffectSelectionTransaction.Candidate(chosen, new EffectTargetResolution.ZoneTarget(A, EffectTargetResolution.Zone.DISCARD))));
        rejects(() -> retry.commit(A, retry.transactionId(), 1, List.of(chosen), selected -> { throw new IllegalStateException("失效目标"); }));
        check(!retry.consumed(), "服务器最终校验失败却消耗了选择事务");
        retry.submit(A, retry.transactionId(), 1, List.of(chosen));
    }
    private static void discardRecoveryAndShuffle() {
        for (String destination : List.of("HAND", "DECK_SHUFFLED")) {
            ServerMatch match = fixture();
            var cards = match.privateView(A).hand().subList(0, 2).stream().map(CardInstance::instanceId).toList();
            match.discardHandCardsByEffect(A, A, cards);
            var recovery = new EffectDefinition("search_discard_pile", EffectTarget.DISCARD,
                Map.of("amount", "2", "minimum", "2", "card_category", "POKEMON", "basic_pokemon_only", "true", "destination", destination),
                List.of(), List.of());
            var tx = match.openEffectZoneSelection(A, recovery, 1);
            rejects(() -> tx.promptFor(B));
            var before = match.privateView(A);
            int randomBefore = match.randomTranscript().size();
            match.resolveEffectZoneSelectionByRule(A, recovery, tx, tx.transactionId(), 1, cards);
            var after = match.privateView(A);
            check(after.discard().isEmpty(), "回收后弃牌未正确移除");
            if (destination.equals("HAND")) {
                check(after.hand().size() == before.hand().size() + 2 && after.deck().equals(before.deck()), "弃牌回手区域不守恒");
                check(randomBefore == match.randomTranscript().size(), "回手错误洗牌");
            } else {
                check(after.deck().size() == before.deck().size() + 2 && after.hand().equals(before.hand()), "弃牌洗回区域不守恒");
                check(match.randomTranscript().size() - randomBefore == after.deck().size() - 1, "回收整组牌应只洗一次牌库");
            }
            check(after.prizes().equals(before.prizes()) && tx.consumed(), "非击倒回收改变了奖赏或未消费事务");
        }
    }
    private static void malformedFilterIsRejected() {
        ServerMatch match = fixture();
        for (Map<String,String> args : List.of(Map.of("amount", "1", "card_categroy", "POKEMON"),
            Map.of("amount", "1", "definition_ids", "basic|"), Map.of("amount", "1", "basic_pokemon_only", "maybe"))) {
            var malformed = new EffectDefinition("search_deck", EffectTarget.DECK, args, List.of(), List.of());
            rejects(() -> match.openEffectDeckSearch(A, malformed, 1));
        }
        var lost = new EffectDefinition("search_discard_pile", EffectTarget.LOST_ZONE,
            Map.of("amount", "1", "destination", "HAND"), List.of(), List.of());
        rejects(() -> match.openEffectZoneSelection(A, lost, 1));
        var conditional = new EffectDefinition("search_deck", EffectTarget.DECK, Map.of("amount", "1"),
            List.of(new ConditionDefinition(ConditionType.IS_BASIC, Map.of(), List.of())), List.of());
        rejects(() -> match.openEffectDeckSearch(A, conditional, 1));
        check(EffectCardSelectionSpec.from(effect("discard", EffectTarget.HAND, 2, 2)).minimum() == 2, "弃牌数量未严格验证");
        var mandatory = new EffectDefinition("discard", EffectTarget.HAND, Map.of("amount", "2"), List.of(), List.of());
        check(EffectCardSelectionSpec.from(mandatory).minimum() == 2, "未标记可选的弃牌被错误放宽到零张");
    }
    static EffectDefinition effect(String type, EffectTarget target, int maximum, int minimum) {
        return new EffectDefinition(type, target, Map.of("amount", "" + maximum, "minimum", "" + minimum), List.of(), List.of());
    }
    static ServerMatch fixture() {
        var definition = new CardDefinition("basic", "测试基础", CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"),
            true, false, 60, Map.of(), EffectSupport.SUPPORTED, null);
        var combat = new CombatCatalog(Map.of("basic", new PokemonProfile(120, 0, null)), Map.of());
        ServerMatch match = new ServerMatch(A, B, deck(A), deck(B), BundledRules.loadRulesets().get("standard-international-2026"),
            new CardDatabase(List.of(definition)), combat, new ServerRandom(92));
        OpeningTestSupport.complete(match, A, B, 1);
        return match;
    }
    private static List<CardInstance> deck(UUID owner) {
        List<CardInstance> cards = new ArrayList<>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "basic"));
        return cards;
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("未获当前对局授权的 Effect 选择被接受");
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
