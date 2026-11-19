import dev.hua.ptcglive.engine.MatchSession;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.BundledRules;
import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.CardDatabase;
import dev.hua.ptcglive.rules.CardDefinition;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.rules.CombatCatalog;
import dev.hua.ptcglive.rules.EffectSupport;
import dev.hua.ptcglive.rules.TrainerCategory;
import dev.hua.ptcglive.rules.TrainerEffect;
import dev.hua.ptcglive.rules.TrainerProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Versioned Trainer random-hand disruption: no client-selected hidden card IDs. */
public final class TrainerRandomHandDiscardTest {
    private static final UUID A = new UUID(0, 7411);
    private static final UUID B = new UUID(0, 7412);

    public static void main(String[] args) {
        ServerMatch match = fixture();
        MatchSession session = MatchSession.multiplayer(match, A, B);
        CardInstance first = hand(match, A, "random");
        if (session.view(A, A).options().stream().noneMatch(option -> option.cardKeys().contains("hand:" + first.instanceId())))
            throw new AssertionError("版本化随机弃手牌训练家没有生成服务器验证的本方操作");
        if (session.view(B, B).options().stream().anyMatch(option -> option.cardKeys().contains("hand:" + first.instanceId())))
            throw new AssertionError("对手收到了随机弃手牌训练家的操作实例");

        List<CardInstance> handBefore = match.privateView(B).hand();
        int discardBefore = match.privateView(B).discard().size();
        int randomBefore = match.randomTranscript().size();
        match.playTrainer(A, first.instanceId(), null);
        List<CardInstance> discarded = match.privateView(B).discard().subList(discardBefore, match.privateView(B).discard().size());
        if (match.privateView(B).hand().size() != handBefore.size() - 2 || discarded.size() != 2
            || discarded.stream().anyMatch(card -> !handBefore.contains(card))
            || match.randomTranscript().size() != randomBefore + 2
            || match.randomTranscript().subList(randomBefore, randomBefore + 2).stream()
                .anyMatch(event -> !event.operation().startsWith("effect-random-hand-discard:"))
            || !match.privateView(A).discard().contains(first))
            throw new AssertionError("训练家随机弃置没有由服务器按版本化数量结算");
        if (match.clientView(A).hand().stream().anyMatch(card -> card.ownerId().equals(B)))
            throw new AssertionError("随机弃置前的对方手牌身份泄露给了客户端");

        // The opening leaves B with six cards; three exact-two effects empty it.
        for (int i = 0; i < 2; i++) match.playTrainer(A, hand(match, A, "random").instanceId(), null);
        if (!match.privateView(B).hand().isEmpty()) throw new AssertionError("随机弃置测试没有耗尽对方手牌");
        CardInstance rejected = hand(match, A, "random");
        var ownBefore = match.privateView(A);
        var opponentBefore = match.privateView(B);
        randomBefore = match.randomTranscript().size();
        rejects(() -> match.playTrainer(A, rejected.instanceId(), null));
        if (!ownBefore.equals(match.privateView(A)) || !opponentBefore.equals(match.privateView(B))
            || randomBefore != match.randomTranscript().size())
            throw new AssertionError("对方手牌不足时训练家随机弃置没有原子拒绝");
        System.out.println("TrainerRandomHandDiscardTest passed: versioned opponent random discard is server-only, audited and atomic");
    }

    private static ServerMatch fixture() {
        List<CardDefinition> definitions = List.of(
            definition("basic", CardCategory.POKEMON, TrainerCategory.NONE, true, false),
            definition("random", CardCategory.TRAINER, TrainerCategory.ITEM, false, false),
            definition("energy", CardCategory.ENERGY, TrainerCategory.NONE, false, true));
        CombatCatalog combat = new CombatCatalog(
            Map.of("basic", new PokemonProfile(70, 0, null, null, 0, null, 0, 1, List.of())),
            Map.of("energy", EnergyType.GRASS),
            Map.of("random", new TrainerProfile(TrainerCategory.ITEM,
                new TrainerEffect(TrainerEffect.Opcode.DISCARD_OPPONENT_RANDOM_HAND, 2, 0))));
        for (long seed = 1; seed < 20_000; seed++) {
            ServerMatch candidate = new ServerMatch(A, B, deck(A, true), deck(B, false),
                BundledRules.loadRulesets().get("standard-international-2026"), new CardDatabase(definitions), combat, new ServerRandom(seed));
            candidate.callCoin(A, dev.hua.ptcglive.engine.CoinSide.HEADS);
            UUID winner = candidate.publicCoinResult() == dev.hua.ptcglive.engine.CoinSide.HEADS ? A : B;
            candidate.chooseWhoGoesFirst(winner, winner.equals(A));
            if (!candidate.needsMulligan(A) && !candidate.needsMulligan(B)) {
                OpeningTestSupport.complete(candidate, A, B, 0);
                if (candidate.privateView(A).hand().stream().filter(card -> card.definitionId().equals("random")).count() >= 4
                    && candidate.privateView(B).hand().size() == 6) return candidate;
            }
        }
        throw new AssertionError("未找到足以验证随机弃置的测试开局");
    }

    private static List<CardInstance> deck(UUID owner, boolean randomTrainer) {
        List<CardInstance> deck = new ArrayList<>();
        for (int i = 0; i < 60; i++) deck.add(new CardInstance(UUID.randomUUID(), owner,
            i < 12 ? "basic" : randomTrainer && i < 58 ? "random" : "energy"));
        return deck;
    }

    private static CardDefinition definition(String id, CardCategory category, TrainerCategory trainerCategory,
                                             boolean basicPokemon, boolean basicEnergy) {
        return new CardDefinition(id, id, category, trainerCategory, "H", Set.of("international"), basicPokemon,
            basicEnergy, 60, Map.of(), EffectSupport.SUPPORTED, null);
    }

    private static CardInstance hand(ServerMatch match, UUID player, String id) {
        return match.privateView(player).hand().stream().filter(card -> card.definitionId().equals(id)).findFirst().orElseThrow();
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法随机弃置训练家效果被接受");
    }
}
