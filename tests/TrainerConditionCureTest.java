import dev.hua.ptcglive.engine.MatchSession;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.engine.field.SpecialCondition;
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

/** A Trainer that cures the acting player's Active Pokémon never lets the client pick a hidden or foreign target. */
public final class TrainerConditionCureTest {
    private static final UUID A = new UUID(0, 7441);
    private static final UUID B = new UUID(0, 7442);

    public static void main(String[] args) {
        ServerMatch match = fixture();
        CardInstance cure = hand(match, A, "cure");
        MatchSession beforeCondition = MatchSession.multiplayer(match, A, B);
        if (beforeCondition.view(A, A).options().stream().anyMatch(option -> option.cardKeys().contains("hand:" + cure.instanceId())))
            throw new AssertionError("没有异常状态时仍生成了会失败的解除异常训练家操作");
        var before = match.privateView(A);
        rejects(() -> match.playTrainer(A, cure.instanceId(), null));
        if (!before.equals(match.privateView(A))) throw new AssertionError("无异常状态时错误消耗了解除异常训练家卡");

        match.endTurn(A);
        match.applySpecialConditionByEffect(B, A, SpecialCondition.POISONED);
        match.applySpecialConditionByEffect(B, A, SpecialCondition.PARALYZED);
        if (!match.activePokemonView(A).conditions().containsAll(Set.of(SpecialCondition.POISONED, SpecialCondition.PARALYZED)))
            throw new AssertionError("测试异常状态没有由服务器附加到己方战斗宝可梦");
        match.endTurn(B);

        MatchSession activeSession = MatchSession.multiplayer(match, A, B);
        if (activeSession.view(A, A).options().stream().noneMatch(option -> option.cardKeys().contains("hand:" + cure.instanceId())))
            throw new AssertionError("有异常状态时没有生成服务器验证的解除异常训练家操作");
        var stateBeforeForgedTarget = match.privateView(A);
        rejects(() -> match.playTrainer(A, cure.instanceId(), UUID.randomUUID()));
        if (!stateBeforeForgedTarget.equals(match.privateView(A))) throw new AssertionError("伪造目标参数错误消耗了解除异常训练家卡");

        match.playTrainer(A, cure.instanceId(), null);
        if (!match.activePokemonView(A).conditions().isEmpty() || !match.privateView(A).discard().contains(cure))
            throw new AssertionError("解除异常训练家没有清空己方战斗位状态或进入弃牌区");
        if (match.replayTranscript().stream().noneMatch(event -> event.operation().equals("已审核效果移除异常状态")))
            throw new AssertionError("解除异常状态没有写入服务器公开事实回放");
        System.out.println("TrainerConditionCureTest passed: Active-only condition cure is atomic and server-authoritative");
    }

    private static ServerMatch fixture() {
        List<CardDefinition> definitions = List.of(
            definition("basic", CardCategory.POKEMON, TrainerCategory.NONE, true),
            definition("cure", CardCategory.TRAINER, TrainerCategory.ITEM, false));
        CombatCatalog combat = new CombatCatalog(
            Map.of("basic", new PokemonProfile(70, 0, null, null, 0, null, 0, 1, List.of())), Map.of(),
            Map.of("cure", new TrainerProfile(TrainerCategory.ITEM,
                new TrainerEffect(TrainerEffect.Opcode.CURE_OWN_ACTIVE_SPECIAL_CONDITIONS, 0, 0))));
        for (long seed = 1; seed < 20_000; seed++) {
            ServerMatch candidate = new ServerMatch(A, B, deck(A, true), deck(B, false),
                BundledRules.loadRulesets().get("standard-international-2026"), new CardDatabase(definitions), combat, new ServerRandom(seed));
            candidate.callCoin(A, dev.hua.ptcglive.engine.CoinSide.HEADS);
            UUID winner = candidate.publicCoinResult() == dev.hua.ptcglive.engine.CoinSide.HEADS ? A : B;
            candidate.chooseWhoGoesFirst(winner, winner.equals(A));
            if (!candidate.needsMulligan(A) && !candidate.needsMulligan(B)) {
                OpeningTestSupport.complete(candidate, A, B, 0);
                if (candidate.privateView(A).hand().stream().anyMatch(card -> card.definitionId().equals("cure"))) return candidate;
            }
        }
        throw new AssertionError("未找到可验证解除异常训练家的测试开局");
    }

    private static List<CardInstance> deck(UUID owner, boolean hasCures) {
        List<CardInstance> deck = new ArrayList<>();
        for (int i = 0; i < 60; i++) deck.add(new CardInstance(UUID.randomUUID(), owner, i < 12 || !hasCures ? "basic" : "cure"));
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
        throw new AssertionError("非法解除异常训练家效果被接受");
    }
}
