import dev.hua.ptcglive.engine.CoinSide;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.effect.EffectDefinition;
import dev.hua.ptcglive.engine.effect.EffectProgram;
import dev.hua.ptcglive.engine.effect.EffectTarget;
import dev.hua.ptcglive.engine.effect.EffectTrigger;
import dev.hua.ptcglive.engine.effect.TriggeredEffects;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Exercises the server bridge for safe composed Effects; no GUI or client authorizes these effects. */
public final class ServerEffectProgramExecutionTest {
    private static final UUID A = new UUID(0, 7481), B = new UUID(0, 7482);
    public static void main(String[] args) {
        ServerMatch match = fixture();
        UUID ownActive = match.activePokemonView(A).pokemonId();
        UUID opponentActive = match.activePokemonView(B).pokemonId();

        int handBefore = match.privateView(A).hand().size();
        match.executeEffectProgramByRule(A, ownActive, program(new EffectDefinition("repeat", EffectTarget.SELF, Map.of("count", "2"), List.of(), List.of(
            new EffectDefinition("draw", EffectTarget.SELF, Map.of("amount", "1"), List.of(), List.of())))), EffectTrigger.ON_ATTACK);
        check(match.privateView(A).hand().size() == handBefore + 2, "Repeat + Draw 没有通过服务器牌库结算");

        match.executeEffectProgramByRule(A, ownActive, program(new EffectDefinition("poison", EffectTarget.OPPONENT_ACTIVE, Map.of(), List.of(), List.of())), EffectTrigger.ON_ATTACK);
        check(match.activePokemonView(B).conditions().contains(SpecialCondition.POISONED), "异常状态 Effect 没有进入服务器战场");

        int opponentDamage = match.activePokemonView(B).damage();
        var choice = match.openEffectPokemonSelection(A, ownActive, EffectTarget.SELECT_OPPONENT_POKEMON, 1, 1, 73);
        var prompt = choice.promptFor(A);
        match.executeEffectProgramWithPokemonSelectionByRule(A, ownActive,
            program(new EffectDefinition("damage_counter", EffectTarget.SELECT_OPPONENT_POKEMON, Map.of("amount", "1"), List.of(), List.of())),
            EffectTrigger.ON_ATTACK, choice, prompt.transactionId(), prompt.revision(), List.of(prompt.candidateIds().getFirst()));
        check(match.activePokemonView(B).damage() == opponentDamage + 10,
            "带服务器选择事务的公开目标 Effect 没有正确结算伤害指示物");

        match.placeDamageCountersByEffect(A, A, ownActive, 2);
        int damageBeforeHeal = match.activePokemonView(A).damage();
        match.executeEffectProgramByRule(A, ownActive, program(new EffectDefinition("heal", EffectTarget.SELF, Map.of("amount", "10"), List.of(), List.of())), EffectTrigger.ON_ATTACK);
        check(match.activePokemonView(A).damage() == damageBeforeHeal - 10, "Heal Effect 没有通过服务器战场结算");

        int beforeRejected = match.privateView(A).hand().size();
        EffectProgram unsafe = program(
            new EffectDefinition("draw", EffectTarget.SELF, Map.of("amount", "1"), List.of(), List.of()),
            new EffectDefinition("damage", EffectTarget.OPPONENT_ACTIVE, Map.of("amount", "10"), List.of(), List.of()));
        rejects(() -> match.executeEffectProgramByRule(A, ownActive, unsafe, EffectTrigger.ON_ATTACK));
        check(match.privateView(A).hand().size() == beforeRejected, "未实现 Effect 在预检失败前已部分改变对局状态");
        check(match.activePokemonView(B).pokemonId().equals(opponentActive), "预检失败不应改变战斗宝可梦");

        EffectProgram once = new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ONCE_PER_TURN, List.of(), List.of(
            new EffectDefinition("draw", EffectTarget.SELF, Map.of("amount", "1"), List.of(), List.of())))));
        int beforeOnce = match.privateView(A).hand().size();
        match.executeEffectProgramByRule(A, ownActive, once, EffectTrigger.ONCE_PER_TURN);
        match.executeEffectProgramByRule(A, ownActive, once, EffectTrigger.ONCE_PER_TURN);
        check(match.privateView(A).hand().size() == beforeOnce + 1,
            "ONCE_PER_TURN 触发器没有由同一场服务器对局限制使用次数");
        System.out.println("ServerEffectProgramExecutionTest passed: parsed safe Effect components use server battle and hidden-zone rules");
    }

    private static EffectProgram program(EffectDefinition... effects) {
        return new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_ATTACK, List.of(), List.of(effects))));
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
                if (candidate.currentPlayer().equals(A)) return candidate;
            }
        }
        throw new AssertionError("未找到可测试服务器 Effect 程序的开局");
    }
    private static List<CardInstance> deck(UUID owner) {
        List<CardInstance> cards = new ArrayList<>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "basic"));
        return cards;
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("尚需攻击伤害上下文的 Effect 被错误放行");
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
