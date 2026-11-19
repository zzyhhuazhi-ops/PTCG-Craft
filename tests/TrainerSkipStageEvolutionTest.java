import dev.hua.ptcglive.engine.MatchSession;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.EvolutionPermission;
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

/** A skip-stage Trainer may skip exactly one intermediate stage but keeps normal timing restrictions. */
public final class TrainerSkipStageEvolutionTest {
    private static final UUID A = new UUID(0, 7441);
    private static final UUID B = new UUID(0, 7442);

    public static void main(String[] args) {
        ServerMatch match = fixture();
        UUID active = match.activePokemonView(A).pokemonId();
        CardInstance trainer = hand(match, A, "skip");
        CardInstance stage1 = hand(match, A, "stage1");
        CardInstance stage2 = hand(match, A, "stage2");
        EvolutionPermission permission = EvolutionPermission.CARD_EFFECT_SKIP_ONE_STAGE_STRICT_TIMING;
        if (match.canEvolveByEffectSkippingOneStage(A, active, "stage2", permission))
            throw new AssertionError("跳过一阶进化错误绕过了首回合或登场回合限制");
        rejects(() -> match.playTrainerWithEvolution(A, trainer.instanceId(), active, stage2.instanceId()));
        if (match.privateView(A).discard().contains(trainer)) throw new AssertionError("时点不足时跳级训练家被错误消耗");

        match.endTurn(A); match.endTurn(B);
        if (!match.canEvolveByEffectSkippingOneStage(A, active, "stage2", permission))
            throw new AssertionError("经过完整回合后无法进行受限的一阶跳过进化");
        if (match.canEvolveByEffectSkippingOneStage(A, active, "stage1", permission))
            throw new AssertionError("跳级进化错误接受了普通一阶进化牌");
        MatchSession session = MatchSession.multiplayer(match, A, B);
        if (session.view(A, A).options().stream().noneMatch(option -> option.cardKeys().contains("hand:" + trainer.instanceId())
            && option.cardKeys().contains("hand:" + stage2.instanceId()) && option.cardKeys().contains("field:" + active)))
            throw new AssertionError("跳级进化没有生成受限的服务器操作");
        if (session.view(A, A).options().stream().anyMatch(option -> option.cardKeys().contains("hand:" + trainer.instanceId())
            && option.cardKeys().contains("hand:" + stage1.instanceId())))
            throw new AssertionError("跳级进化错误向普通一阶进化牌生成操作");

        match.playTrainerWithEvolution(A, trainer.instanceId(), active, stage2.instanceId());
        if (!match.activePokemonView(A).topCardId().equals("stage2") || !match.privateView(A).discard().contains(trainer)
            || match.privateView(A).hand().contains(stage2))
            throw new AssertionError("跳过一阶进化没有正确移动二阶宝可梦与训练家卡");
        System.out.println("TrainerSkipStageEvolutionTest passed: one-stage skip is server-only, strict-timing and cannot accept a Stage 1");
    }

    private static ServerMatch fixture() {
        List<CardDefinition> definitions = List.of(
            pokemon("base", true), pokemon("stage1", false), pokemon("stage2", false),
            new CardDefinition("skip", "skip", CardCategory.TRAINER, TrainerCategory.ITEM, "H", Set.of("international"), false,
                false, 60, Map.of(), EffectSupport.SUPPORTED, null));
        CombatCatalog combat = new CombatCatalog(Map.of(
            "base", new PokemonProfile(80, 1, null), "stage1", new PokemonProfile(110, 1, "base"),
            "stage2", new PokemonProfile(150, 2, "stage1")), Map.of(),
            Map.of("skip", new TrainerProfile(TrainerCategory.ITEM,
                new TrainerEffect(TrainerEffect.Opcode.EVOLVE_OWN_BASIC_SKIP_STAGE_FROM_HAND, 0, 0))));
        for (long seed = 1; seed < 20_000; seed++) {
            ServerMatch candidate = new ServerMatch(A, B, deck(A, true), deck(B, false),
                BundledRules.loadRulesets().get("standard-international-2026"), new CardDatabase(definitions), combat, new ServerRandom(seed));
            candidate.callCoin(A, dev.hua.ptcglive.engine.CoinSide.HEADS);
            UUID winner = candidate.publicCoinResult() == dev.hua.ptcglive.engine.CoinSide.HEADS ? A : B;
            candidate.chooseWhoGoesFirst(winner, winner.equals(A));
            if (!candidate.needsMulligan(A) && !candidate.needsMulligan(B)) {
                OpeningTestSupport.complete(candidate, A, B, 0);
                if (candidate.privateView(A).hand().stream().anyMatch(card -> card.definitionId().equals("skip"))
                    && candidate.privateView(A).hand().stream().anyMatch(card -> card.definitionId().equals("stage1"))
                    && candidate.privateView(A).hand().stream().anyMatch(card -> card.definitionId().equals("stage2"))) return candidate;
            }
        }
        throw new AssertionError("未找到可验证跳过一阶进化的测试开局");
    }

    private static List<CardInstance> deck(UUID owner, boolean includeTrainer) {
        List<CardInstance> deck = new ArrayList<>();
        for (int i = 0; i < 60; i++) deck.add(new CardInstance(UUID.randomUUID(), owner,
            !includeTrainer || i < 20 ? "base" : i < 34 ? "stage1" : i < 48 ? "stage2" : "skip"));
        return deck;
    }

    private static CardDefinition pokemon(String id, boolean basic) {
        return new CardDefinition(id, id, CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"), basic,
            false, 60, Map.of(), EffectSupport.SUPPORTED, null);
    }

    private static CardInstance hand(ServerMatch match, UUID player, String id) {
        return match.privateView(player).hand().stream().filter(card -> card.definitionId().equals(id)).findFirst().orElseThrow();
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("时点不足的跳级进化被接受");
    }
}
