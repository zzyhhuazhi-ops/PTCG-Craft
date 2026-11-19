import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.effect.EffectTarget;
import dev.hua.ptcglive.engine.effect.EffectTargetResolution;
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

/** Target resolution is derived from server public field state, never from a client UUID list. */
public final class ServerEffectTargetTest {
    private static final UUID A = new UUID(0, 7451), B = new UUID(0, 7452);
    public static void main(String[] args) {
        ServerMatch match = fixture();
        UUID ownActive = match.activePokemonView(A).pokemonId();
        var allOpponent = match.resolveEffectTarget(A, ownActive, EffectTarget.ALL_OPPONENT_POKEMON);
        if (allOpponent.requiresSelection() || allOpponent.fixedTargets().size() != 2
            || allOpponent.fixedTargets().stream().anyMatch(target -> !(target instanceof EffectTargetResolution.PokemonTarget)))
            throw new AssertionError("服务端没有从双方公开场地正确解析全体对方宝可梦目标");
        var deck = match.resolveEffectTarget(A, ownActive, EffectTarget.DECK);
        if (deck.fixedTargets().size() != 1 || !(deck.fixedTargets().getFirst() instanceof EffectTargetResolution.ZoneTarget))
            throw new AssertionError("服务端隐藏区域目标错误泄露为卡牌实例");
        var select = match.resolveEffectTarget(A, ownActive, EffectTarget.SELECT_OPPONENT_POKEMON);
        if (!select.requiresSelection() || !select.fixedTargets().isEmpty())
            throw new AssertionError("选择目标被错误当作客户端可指定的固定实例");
        rejects(() -> match.resolveEffectTarget(A, match.activePokemonView(B).pokemonId(), EffectTarget.SELF));
        System.out.println("ServerEffectTargetTest passed: server target resolution exposes only legal public references");
    }
    private static ServerMatch fixture() {
        List<CardDefinition> definitions = List.of(new CardDefinition("basic", "basic", CardCategory.POKEMON, TrainerCategory.NONE,
            "H", Set.of("international"), true, false, 60, Map.of(), EffectSupport.SUPPORTED, null));
        CombatCatalog combat = new CombatCatalog(Map.of("basic", new PokemonProfile(70, 0, null, null, 0, null, 0, 1, List.of())), Map.of());
        for (int seed = 1; seed < 20_000; seed++) {
            ServerMatch candidate = new ServerMatch(A, B, deck(A), deck(B), BundledRules.loadRulesets().get("standard-international-2026"),
                new CardDatabase(definitions), combat, new ServerRandom(seed));
            candidate.callCoin(A, dev.hua.ptcglive.engine.CoinSide.HEADS);
            UUID winner = candidate.publicCoinResult() == dev.hua.ptcglive.engine.CoinSide.HEADS ? A : B;
            candidate.chooseWhoGoesFirst(winner, winner.equals(A));
            if (!candidate.needsMulligan(A) && !candidate.needsMulligan(B)) {
                OpeningTestSupport.complete(candidate, A, B, 1);
                if (!candidate.benchPokemonView(A).isEmpty() && !candidate.benchPokemonView(B).isEmpty()) return candidate;
            }
        }
        throw new AssertionError("未找到可验证 Effect 目标的测试开局");
    }
    private static List<CardInstance> deck(UUID owner) {
        List<CardInstance> cards = new ArrayList<>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "basic"));
        return cards;
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("对方场上宝可梦被错误接受为己方 Effect 来源");
    }
}
