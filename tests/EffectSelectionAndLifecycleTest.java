import dev.hua.ptcglive.engine.CoinSide;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.effect.ConditionDefinition;
import dev.hua.ptcglive.engine.effect.ConditionType;
import dev.hua.ptcglive.engine.effect.EffectDefinition;
import dev.hua.ptcglive.engine.effect.EffectExecutionContext;
import dev.hua.ptcglive.engine.effect.EffectLifecycleDispatcher;
import dev.hua.ptcglive.engine.effect.EffectProgram;
import dev.hua.ptcglive.engine.effect.EffectSelectionTransaction;
import dev.hua.ptcglive.engine.effect.EffectTarget;
import dev.hua.ptcglive.engine.effect.EffectTargetResolution;
import dev.hua.ptcglive.engine.effect.EffectTrigger;
import dev.hua.ptcglive.engine.effect.TriggeredEffects;
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

/** Regression contract for server-issued Effect selection and rate-limited lifecycle triggers. */
public final class EffectSelectionAndLifecycleTest {
    private static final UUID A = new UUID(0, 7471), B = new UUID(0, 7472);

    public static void main(String[] args) {
        transactionRejectsForgedAndStaleChoices();
        lifecycleHonorsOnceLimitsWithoutClientState();
        serverGeneratesAndRevalidatesPublicPokemonChoices();
        System.out.println("EffectSelectionAndLifecycleTest passed: Effect choices and lifecycle limits stay server-authoritative");
    }

    private static void transactionRejectsForgedAndStaleChoices() {
        UUID first = new UUID(0, 1), second = new UUID(0, 2);
        var transaction = EffectSelectionTransaction.open(A, 42, 1, 2,
            EffectSelectionTransaction.Visibility.ENTITLED_PLAYER_ONLY, List.of(
                new EffectSelectionTransaction.Candidate(first,
                    new EffectTargetResolution.ZoneTarget(A, EffectTargetResolution.Zone.DECK)),
                new EffectSelectionTransaction.Candidate(second,
                    new EffectTargetResolution.ZoneTarget(A, EffectTargetResolution.Zone.DECK))));
        rejects(() -> transaction.promptFor(B));
        var prompt = transaction.promptFor(A);
        rejects(() -> transaction.submit(A, prompt.transactionId(), 41, List.of(first)));
        rejects(() -> transaction.submit(A, prompt.transactionId(), 42, List.of(new UUID(0, 99))));
        rejects(() -> transaction.submit(A, prompt.transactionId(), 42, List.of(first, first)));
        var picked = transaction.submit(A, prompt.transactionId(), 42, List.of(second));
        check(picked.size() == 1 && picked.getFirst().id().equals(second) && transaction.consumed(), "合法的服务器候选选择未被接受");
        rejects(() -> transaction.submit(A, prompt.transactionId(), 42, List.of(first)));
    }

    private static void lifecycleHonorsOnceLimitsWithoutClientState() {
        EffectDefinition draw = new EffectDefinition("draw", EffectTarget.SELF, Map.of("amount", "1"), List.of(), List.of());
        EffectProgram program = new EffectProgram(List.of(
            new TriggeredEffects(EffectTrigger.ONCE_PER_TURN, List.of(), List.of(draw)),
            new TriggeredEffects(EffectTrigger.ONCE_PER_GAME, List.of(), List.of(draw))));
        var dispatcher = new EffectLifecycleDispatcher();
        var source = new EffectLifecycleDispatcher.Source(new UUID(0, 8), program);
        var context = new AtomicContext();
        var firstTurn = dispatcher.dispatch(source, EffectTrigger.ONCE_PER_TURN, 3, context);
        var repeatedTurn = dispatcher.dispatch(source, EffectTrigger.ONCE_PER_TURN, 3, context);
        var nextTurn = dispatcher.dispatch(source, EffectTrigger.ONCE_PER_TURN, 4, context);
        var firstGame = dispatcher.dispatch(source, EffectTrigger.ONCE_PER_GAME, 4, context);
        var repeatedGame = dispatcher.dispatch(source, EffectTrigger.ONCE_PER_GAME, 5, context);
        check(firstTurn.executedBindings() == 1 && repeatedTurn.skippedByUsageLimit() == 1 && nextTurn.executedBindings() == 1,
            "ONCE_PER_TURN 没有按服务器回合序号限制");
        check(firstGame.executedBindings() == 1 && repeatedGame.skippedByUsageLimit() == 1 && context.atomic == 3,
            "ONCE_PER_GAME 没有在同一对局生命周期内限制");
        dispatcher.clearCompletedMatch();
        check(dispatcher.dispatch(source, EffectTrigger.ONCE_PER_GAME, 6, context).executedBindings() == 1,
            "只应在已完成对局清理后重置一次每局使用记录");
    }

    private static void serverGeneratesAndRevalidatesPublicPokemonChoices() {
        ServerMatch match = fixture();
        UUID source = match.activePokemonView(A).pokemonId();
        var transaction = match.openEffectPokemonSelection(A, source, EffectTarget.SELECT_OPPONENT_POKEMON, 1, 1, 12);
        var prompt = transaction.promptFor(A);
        check(!prompt.candidateIds().isEmpty(), "服务器没有从公开对方场地生成选择候选");
        rejects(() -> transaction.promptFor(B));
        var selected = match.submitEffectPokemonSelection(A, transaction, prompt.transactionId(), prompt.revision(),
            List.of(prompt.candidateIds().getFirst()));
        check(selected.size() == 1 && selected.getFirst().owner().equals(B), "服务器没有保留公开目标的真实拥有方");
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
                OpeningTestSupport.complete(candidate, A, B, 1);
                if (candidate.activePokemonView(A) != null && candidate.activePokemonView(B) != null) return candidate;
            }
        }
        throw new AssertionError("未找到可测试的服务器公开 Effect 选择开局");
    }

    private static List<CardInstance> deck(UUID owner) {
        List<CardInstance> cards = new ArrayList<>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "basic"));
        return cards;
    }

    private static final class AtomicContext implements EffectExecutionContext {
        int atomic;
        @Override public CoinResult flipCoins(EffectDefinition source, int flips) { return new CoinResult(flips, 0); }
        @Override public int choose(EffectDefinition source, int optionCount) { return 0; }
        @Override public void executeAtomic(EffectDefinition effect) { atomic++; }
        @Override public int number(ConditionType type, EffectTarget target, Map<String, String> arguments) { return 0; }
        @Override public boolean hasStatus(EffectTarget target, String status) { return false; }
        @Override public boolean flag(ConditionType type, EffectTarget target, Map<String, String> arguments) { return false; }
        @Override public boolean coinResult(String expected) { return false; }
        @Override public boolean customCondition(ConditionDefinition condition) { return false; }
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("伪造、陈旧或重复的 Effect 选择被错误接受");
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
