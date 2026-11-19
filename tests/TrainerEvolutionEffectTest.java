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

/** Reviewed Trainer evolution may use a declared timing exception, never the ordinary client action. */
public final class TrainerEvolutionEffectTest {
    private static final UUID A = new UUID(0, 7431);
    private static final UUID B = new UUID(0, 7432);

    public static void main(String[] args) {
        ServerMatch match = fixture();
        UUID active = match.activePokemonView(A).pokemonId();
        CardInstance trainer = hand(match, A, "evolve");
        CardInstance stage1 = hand(match, A, "stage1");
        if (match.canEvolve(A, active, "stage1"))
            throw new AssertionError("普通进化错误绕过了首回合／刚登场限制");
        if (!match.canEvolveByEffect(A, active, "stage1", EvolutionPermission.CARD_EFFECT_ALL_TIMING))
            throw new AssertionError("已审核训练家进化没有获得明确的时点许可");
        MatchSession session = MatchSession.multiplayer(match, A, B);
        if (session.view(A, A).options().stream().noneMatch(option -> option.cardKeys().contains("hand:" + trainer.instanceId())
            && option.cardKeys().contains("hand:" + stage1.instanceId()) && option.cardKeys().contains("field:" + active)))
            throw new AssertionError("手牌进化训练家没有生成包含两张本方手牌和场上目标的服务器操作");
        if (session.view(B, B).options().stream().anyMatch(option -> option.cardKeys().contains("hand:" + trainer.instanceId())
            || option.cardKeys().contains("hand:" + stage1.instanceId())))
            throw new AssertionError("对手收到了本方训练家或进化手牌的实例标识");

        var before = match.privateView(A);
        rejects(() -> match.playTrainerWithEvolution(A, trainer.instanceId(), active, trainer.instanceId()));
        if (!before.equals(match.privateView(A))) throw new AssertionError("伪造进化手牌时对局状态没有原子拒绝");
        match.playTrainerWithEvolution(A, trainer.instanceId(), active, stage1.instanceId());
        if (!match.activePokemonView(A).topCardId().equals("stage1") || match.privateView(A).hand().contains(stage1)
            || !match.privateView(A).discard().contains(trainer))
            throw new AssertionError("训练家手牌进化没有正确移动进化牌和训练家卡");
        if (!match.replayTranscript().stream().anyMatch(event -> event.operation().equals("已审核效果使宝可梦进化"))
            || !match.replayTranscript().getLast().operation().equals("使用已审核训练家卡并从手牌进化"))
            throw new AssertionError("训练家手牌进化没有写入受限回放记录");
        System.out.println("TrainerEvolutionEffectTest passed: versioned hand evolution uses explicit server timing permission and private choices");
    }

    private static ServerMatch fixture() {
        List<CardDefinition> definitions = List.of(
            definition("base", true), definition("stage1", false),
            new CardDefinition("evolve", "evolve", CardCategory.TRAINER, TrainerCategory.ITEM, "H", Set.of("international"), false,
                false, 60, Map.of(), EffectSupport.SUPPORTED, null));
        CombatCatalog combat = new CombatCatalog(Map.of(
            "base", new PokemonProfile(80, 1, null), "stage1", new PokemonProfile(110, 1, "base")), Map.of(),
            Map.of("evolve", new TrainerProfile(TrainerCategory.ITEM,
                new TrainerEffect(TrainerEffect.Opcode.EVOLVE_OWN_POKEMON_FROM_HAND, 0, 0))));
        for (long seed = 1; seed < 20_000; seed++) {
            ServerMatch candidate = new ServerMatch(A, B, deck(A, true), deck(B, false),
                BundledRules.loadRulesets().get("standard-international-2026"), new CardDatabase(definitions), combat, new ServerRandom(seed));
            candidate.callCoin(A, dev.hua.ptcglive.engine.CoinSide.HEADS);
            UUID winner = candidate.publicCoinResult() == dev.hua.ptcglive.engine.CoinSide.HEADS ? A : B;
            candidate.chooseWhoGoesFirst(winner, winner.equals(A));
            if (!candidate.needsMulligan(A) && !candidate.needsMulligan(B)) {
                OpeningTestSupport.complete(candidate, A, B, 0);
                if (candidate.privateView(A).hand().stream().anyMatch(card -> card.definitionId().equals("evolve"))
                    && candidate.privateView(A).hand().stream().anyMatch(card -> card.definitionId().equals("stage1"))) return candidate;
            }
        }
        throw new AssertionError("未找到可验证首回合手牌进化的测试开局");
    }

    private static List<CardInstance> deck(UUID owner, boolean includeTrainer) {
        List<CardInstance> deck = new ArrayList<>();
        for (int i = 0; i < 60; i++) deck.add(new CardInstance(UUID.randomUUID(), owner,
            !includeTrainer || i < 20 ? "base" : i < 40 ? "stage1" : "evolve"));
        return deck;
    }

    private static CardDefinition definition(String id, boolean basic) {
        return new CardDefinition(id, id, CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"), basic,
            false, 60, Map.of(), EffectSupport.SUPPORTED, null);
    }

    private static CardInstance hand(ServerMatch match, UUID player, String id) {
        return match.privateView(player).hand().stream().filter(card -> card.definitionId().equals(id)).findFirst().orElseThrow();
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法训练家手牌进化被接受");
    }
}
