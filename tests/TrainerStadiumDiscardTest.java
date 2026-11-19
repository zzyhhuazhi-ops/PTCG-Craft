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

/** A Stadium-removal Trainer is server-validated and returns the Stadium to its original owner's discard. */
public final class TrainerStadiumDiscardTest {
    private static final UUID A = new UUID(0, 7431);
    private static final UUID B = new UUID(0, 7432);

    public static void main(String[] args) {
        ServerMatch match = fixture();
        CardInstance remover = hand(match, A, "remove");
        MatchSession beforeStadium = MatchSession.multiplayer(match, A, B);
        if (beforeStadium.view(A, A).options().stream().anyMatch(option -> option.cardKeys().contains("hand:" + remover.instanceId())))
            throw new AssertionError("空场地时仍生成了会被服务器拒绝的弃场地训练家操作");
        var aBefore = match.privateView(A);
        rejects(() -> match.playTrainer(A, remover.instanceId(), null));
        if (!aBefore.equals(match.privateView(A))) throw new AssertionError("空场地弃置错误消耗了训练家卡");

        match.endTurn(A);
        CardInstance stadium = hand(match, B, "stadium");
        match.playTrainer(B, stadium.instanceId(), null);
        if (!match.clientView(null).stadium().equals(stadium)) throw new AssertionError("测试场地没有进入共享公开场地位");
        match.endTurn(B);

        MatchSession activeSession = MatchSession.multiplayer(match, A, B);
        if (activeSession.view(A, A).options().stream().noneMatch(option -> option.cardKeys().contains("hand:" + remover.instanceId())))
            throw new AssertionError("有场地时没有为当前玩家生成服务器验证的弃场地操作");
        if (activeSession.view(B, B).options().stream().anyMatch(option -> option.cardKeys().contains("hand:" + remover.instanceId())))
            throw new AssertionError("对手获得了当前玩家私有训练家卡操作");

        match.playTrainer(A, remover.instanceId(), null);
        if (match.clientView(null).stadium() != null || !match.privateView(B).discard().contains(stadium)
            || !match.privateView(A).discard().contains(remover))
            throw new AssertionError("弃置场地训练家没有清空共享场地或将卡放入正确的弃牌区");
        if (match.replayTranscript().stream().noneMatch(event -> event.operation().equals("已审核效果弃置场地")))
            throw new AssertionError("弃置场地的公开事实没有写入服务器回放");
        System.out.println("TrainerStadiumDiscardTest passed: Stadium removal is atomic, public and server-authoritative");
    }

    private static ServerMatch fixture() {
        List<CardDefinition> definitions = List.of(
            definition("basic", CardCategory.POKEMON, TrainerCategory.NONE, true),
            definition("remove", CardCategory.TRAINER, TrainerCategory.ITEM, false),
            definition("stadium", CardCategory.TRAINER, TrainerCategory.STADIUM, false));
        CombatCatalog combat = new CombatCatalog(
            Map.of("basic", new PokemonProfile(70, 0, null, null, 0, null, 0, 1, List.of())), Map.of(),
            Map.of("remove", new TrainerProfile(TrainerCategory.ITEM, new TrainerEffect(TrainerEffect.Opcode.DISCARD_STADIUM, 0, 0)),
                "stadium", new TrainerProfile(TrainerCategory.STADIUM, new TrainerEffect(TrainerEffect.Opcode.STADIUM_BASIC_RETREAT_REDUCTION, 1, 0))));
        for (long seed = 1; seed < 20_000; seed++) {
            ServerMatch candidate = new ServerMatch(A, B, deck(A, "remove"), deck(B, "stadium"),
                BundledRules.loadRulesets().get("standard-international-2026"), new CardDatabase(definitions), combat, new ServerRandom(seed));
            candidate.callCoin(A, dev.hua.ptcglive.engine.CoinSide.HEADS);
            UUID winner = candidate.publicCoinResult() == dev.hua.ptcglive.engine.CoinSide.HEADS ? A : B;
            candidate.chooseWhoGoesFirst(winner, winner.equals(A));
            if (!candidate.needsMulligan(A) && !candidate.needsMulligan(B)) {
                OpeningTestSupport.complete(candidate, A, B, 0);
                if (candidate.privateView(A).hand().stream().anyMatch(card -> card.definitionId().equals("remove"))
                    && candidate.privateView(B).hand().stream().anyMatch(card -> card.definitionId().equals("stadium"))) return candidate;
            }
        }
        throw new AssertionError("未找到可验证弃置场地训练家的测试开局");
    }

    private static List<CardInstance> deck(UUID owner, String trainer) {
        List<CardInstance> deck = new ArrayList<>();
        for (int i = 0; i < 60; i++) deck.add(new CardInstance(UUID.randomUUID(), owner, i < 12 ? "basic" : trainer));
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
        throw new AssertionError("非法弃置场地训练家效果被接受");
    }
}
