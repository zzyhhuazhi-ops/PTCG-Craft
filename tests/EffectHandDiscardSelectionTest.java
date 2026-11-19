import dev.hua.ptcglive.engine.CoinSide;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.effect.EffectDefinition;
import dev.hua.ptcglive.engine.effect.EffectTarget;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.BundledRules;
import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.CardDatabase;
import dev.hua.ptcglive.rules.CardDefinition;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.rules.CombatCatalog;
import dev.hua.ptcglive.rules.EffectSupport;
import dev.hua.ptcglive.rules.TrainerCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** DiscardEffect hand choices remain private and are atomically revalidated by the server. */
public final class EffectHandDiscardSelectionTest {
    private static final UUID A = new UUID(0, 7511), B = new UUID(0, 7512);
    public static void main(String[] args) {
        ServerMatch match = fixture();
        EffectDefinition discard = new EffectDefinition("discard", EffectTarget.HAND,
            Map.of("amount", "2", "minimum", "2", "card_category", "POKEMON", "basic_pokemon_only", "true"), List.of(), List.of());
        var transaction = match.openEffectHandDiscard(A, discard, 97);
        rejects(() -> transaction.promptFor(B));
        var prompt = transaction.promptFor(A);
        if (prompt.candidateIds().size() < 2) throw new AssertionError("服务器没有生成符合筛选的私有手牌候选");
        int handBefore = match.privateView(A).hand().size(), discardBefore = match.privateView(A).discard().size();
        match.resolveEffectHandDiscardByRule(A, discard, transaction, prompt.transactionId(), prompt.revision(),
            List.of(prompt.candidateIds().get(0), prompt.candidateIds().get(1)));
        if (match.privateView(A).hand().size() != handBefore - 2 || match.privateView(A).discard().size() != discardBefore + 2)
            throw new AssertionError("DiscardEffect 没有通过服务器原子弃置结算");
        rejects(() -> match.resolveEffectHandDiscardByRule(A, discard, transaction, prompt.transactionId(), prompt.revision(), List.of()));
        System.out.println("EffectHandDiscardSelectionTest passed: DiscardEffect uses owner-private candidates and atomic server resolution");
    }
    private static ServerMatch fixture() {
        List<CardDefinition> definitions = List.of(new CardDefinition("basic", "basic", CardCategory.POKEMON, TrainerCategory.NONE,
            "H", Set.of("international"), true, false, 60, Map.of(), EffectSupport.SUPPORTED, null));
        CombatCatalog combat = new CombatCatalog(Map.of("basic", new PokemonProfile(70, 0, null, null, 0, null, 0, 1, List.of())), Map.of());
        for (int seed = 1; seed < 20_000; seed++) {
            ServerMatch candidate = new ServerMatch(A, B, deck(A), deck(B), BundledRules.loadRulesets().get("standard-international-2026"),
                new CardDatabase(definitions), combat, new ServerRandom(seed));
            candidate.callCoin(A, CoinSide.HEADS);
            UUID winner = candidate.publicCoinResult() == CoinSide.HEADS ? A : B;
            candidate.chooseWhoGoesFirst(winner, winner.equals(A));
            if (!candidate.needsMulligan(A) && !candidate.needsMulligan(B)) {
                OpeningTestSupport.complete(candidate, A, B, 0);
                if (candidate.currentPlayer().equals(B)) candidate.endTurn(B);
                if (candidate.currentPlayer().equals(A) && candidate.privateView(A).hand().size() >= 2) return candidate;
            }
        }
        throw new AssertionError("未找到可测试 DiscardEffect 私有手牌的开局");
    }
    private static List<CardInstance> deck(UUID owner) {
        var cards = new ArrayList<CardInstance>();
        for (int index = 0; index < 60; index++) cards.add(new CardInstance(UUID.randomUUID(), owner, "basic"));
        return cards;
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("越权或重复 DiscardEffect 事务被错误接受");
    }
}
