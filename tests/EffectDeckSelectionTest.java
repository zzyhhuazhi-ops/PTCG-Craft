import dev.hua.ptcglive.engine.CoinSide;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.effect.EffectDefinition;
import dev.hua.ptcglive.engine.effect.EffectSelectionTransaction;
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

/** Private SearchDeckEffect candidates are server-filtered and never visible to another seat. */
public final class EffectDeckSelectionTest {
    private static final UUID A = new UUID(0, 7501), B = new UUID(0, 7502);
    public static void main(String[] args) {
        ServerMatch match = fixture();
        EffectDefinition search = new EffectDefinition("search_deck", EffectTarget.DECK,
            Map.of("amount", "2", "minimum", "1", "card_category", "ENERGY", "basic_pokemon_only", "false"), List.of(), List.of());
        EffectSelectionTransaction transaction = match.openEffectDeckSearch(A, search, 88);
        rejects(() -> transaction.promptFor(B));
        var prompt = transaction.promptFor(A);
        if (prompt.candidateIds().size() < 2) throw new AssertionError("服务器没有按能量筛选生成私有牌库候选");
        int handBefore = match.privateView(A).hand().size(), deckBefore = match.privateView(A).deck().size();
        match.resolveEffectDeckSearchByRule(A, search, transaction, prompt.transactionId(), prompt.revision(),
            List.of(prompt.candidateIds().get(0), prompt.candidateIds().get(1)));
        if (match.privateView(A).hand().size() != handBefore + 2 || match.privateView(A).deck().size() != deckBefore - 2)
            throw new AssertionError("SearchDeckEffect 没有通过服务器检索与洗牌结算");
        if (match.privateView(A).hand().subList(handBefore, handBefore + 2).stream().anyMatch(card -> !card.definitionId().equals("energy")))
            throw new AssertionError("服务器允许不符合筛选的牌库卡进入手牌");
        rejects(() -> match.resolveEffectDeckSearchByRule(A, search, transaction, prompt.transactionId(), prompt.revision(), List.of()));
        System.out.println("EffectDeckSelectionTest passed: SearchDeckEffect uses owner-private server candidates and revalidated resolution");
    }

    private static ServerMatch fixture() {
        List<CardDefinition> definitions = List.of(
            new CardDefinition("basic", "basic", CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"), true, false, 60, Map.of(), EffectSupport.SUPPORTED, null),
            new CardDefinition("energy", "energy", CardCategory.ENERGY, TrainerCategory.NONE, "H", Set.of("international"), false, true, 99, Map.of(), EffectSupport.SUPPORTED, null));
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
                if (candidate.currentPlayer().equals(A) && candidate.privateView(A).deck().stream().filter(card -> card.definitionId().equals("energy")).count() >= 2)
                    return candidate;
            }
        }
        throw new AssertionError("未找到可测试私有牌库 Effect 选择的开局");
    }
    private static List<CardInstance> deck(UUID owner) {
        var cards = new ArrayList<CardInstance>();
        for (int index = 0; index < 20; index++) cards.add(new CardInstance(UUID.randomUUID(), owner, "basic"));
        for (int index = 0; index < 40; index++) cards.add(new CardInstance(UUID.randomUUID(), owner, "energy"));
        return cards;
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("越权或重复私有 SearchDeckEffect 事务被错误接受");
    }
}
