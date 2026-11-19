import dev.hua.ptcglive.engine.MatchSession;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
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

/** A trainer-directed forced switch may target only public opponent Benched Pokémon. */
public final class TrainerForcedSwitchTest {
    private static final UUID A = new UUID(0, 7421);
    private static final UUID B = new UUID(0, 7422);

    public static void main(String[] args) {
        ServerMatch match = fixture();
        CardInstance trainer = hand(match, A, "gust");
        UUID opponentBench = match.benchPokemonView(B).getFirst().pokemonId();
        UUID ownBench = match.benchPokemonView(A).getFirst().pokemonId();
        MatchSession session = MatchSession.multiplayer(match, A, B);
        if (session.view(A, A).options().stream().noneMatch(option -> option.cardKeys().contains("hand:" + trainer.instanceId())
            && option.cardKeys().contains("field:" + opponentBench)))
            throw new AssertionError("对方备战区没有获得服务器验证的强制交换训练家操作");
        if (session.view(A, A).options().stream().anyMatch(option -> option.cardKeys().contains("hand:" + trainer.instanceId())
            && option.cardKeys().contains("field:" + ownBench)))
            throw new AssertionError("强制交换训练家错误提供了己方宝可梦目标");

        var ownBefore = match.privateView(A);
        var opponentBefore = match.privateView(B);
        rejects(() -> match.playTrainer(A, trainer.instanceId(), ownBench));
        if (!ownBefore.equals(match.privateView(A)) || !opponentBefore.equals(match.privateView(B)))
            throw new AssertionError("伪造己方目标时训练家或场上状态被错误改变");

        UUID formerActive = match.activePokemonView(B).pokemonId();
        match.playTrainer(A, trainer.instanceId(), opponentBench);
        if (!match.activePokemonView(B).pokemonId().equals(opponentBench)
            || match.benchPokemonView(B).stream().noneMatch(pokemon -> pokemon.pokemonId().equals(formerActive))
            || !match.privateView(A).discard().contains(trainer))
            throw new AssertionError("强制交换没有正确替换对方战斗宝可梦，或未弃置训练家卡");
        if (!match.replayTranscript().stream().anyMatch(event -> event.operation().equals("已审核效果交换战斗宝可梦")))
            throw new AssertionError("强制交换没有写入公开事实回放");
        System.out.println("TrainerForcedSwitchTest passed: versioned forced switch targets only opponent Bench and stays server-authoritative");
    }

    private static ServerMatch fixture() {
        List<CardDefinition> definitions = List.of(
            definition("basic", CardCategory.POKEMON, TrainerCategory.NONE, true),
            definition("gust", CardCategory.TRAINER, TrainerCategory.ITEM, false));
        CombatCatalog combat = new CombatCatalog(
            Map.of("basic", new PokemonProfile(70, 0, null, null, 0, null, 0, 1, List.of())), Map.of(),
            Map.of("gust", new TrainerProfile(TrainerCategory.ITEM,
                new TrainerEffect(TrainerEffect.Opcode.SWITCH_OPPONENT_ACTIVE, 0, 0))));
        for (long seed = 1; seed < 20_000; seed++) {
            ServerMatch candidate = new ServerMatch(A, B, deck(A, true), deck(B, false),
                BundledRules.loadRulesets().get("standard-international-2026"), new CardDatabase(definitions), combat, new ServerRandom(seed));
            candidate.callCoin(A, dev.hua.ptcglive.engine.CoinSide.HEADS);
            UUID winner = candidate.publicCoinResult() == dev.hua.ptcglive.engine.CoinSide.HEADS ? A : B;
            candidate.chooseWhoGoesFirst(winner, winner.equals(A));
            if (!candidate.needsMulligan(A) && !candidate.needsMulligan(B)) {
                OpeningTestSupport.complete(candidate, A, B, 1);
                if (candidate.privateView(A).hand().stream().anyMatch(card -> card.definitionId().equals("gust"))) return candidate;
            }
        }
        throw new AssertionError("未找到可验证强制交换的测试开局");
    }

    private static List<CardInstance> deck(UUID owner, boolean withGust) {
        List<CardInstance> deck = new ArrayList<>();
        for (int i = 0; i < 60; i++) deck.add(new CardInstance(UUID.randomUUID(), owner, withGust && i >= 32 ? "gust" : "basic"));
        return deck;
    }

    private static CardDefinition definition(String id, CardCategory category, TrainerCategory trainerCategory, boolean basic) {
        return new CardDefinition(id, id, category, trainerCategory, "H", Set.of("international"), basic, false,
            60, Map.of(), EffectSupport.SUPPORTED, null);
    }

    private static CardInstance hand(ServerMatch match, UUID player, String id) {
        return match.privateView(player).hand().stream().filter(card -> card.definitionId().equals(id)).findFirst().orElseThrow();
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法强制交换目标被接受");
    }
}
