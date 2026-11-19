import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.SpecialCondition;
import java.util.*;

/** Integration regressions for the boundary between an effect and its KO/prize checkpoint. */
public final class EffectResolutionBoundaryTest {
    private static final UUID A = new UUID(0, 7521), B = new UUID(0, 7522);
    public static void main(String[] args) {
        nonAttackKnockoutKeepsTurn(false);
        nonAttackKnockoutKeepsTurn(true);
        lethalCountersDoNotInterruptFollowingEffects();
        simultaneousKnockouts();
        leavePlayThenIndependentText();
        leavePlayDependentTextFailsClosed();
        malformedProgramDoesNotPartlyExecute();
        failedSelectedEffectCanRetry();
        System.out.println("EffectResolutionBoundaryTest passed: effect checkpoint, same-turn KO, validation and retry");
    }
    private static void nonAttackKnockoutKeepsTurn(boolean active) {
        ServerMatch match = EffectSelectionAuthorityTest.fixture();
        UUID target = active ? match.activePokemonView(B).pokemonId() : match.benchPokemonView(B).getFirst().pokemonId();
        match.applySpecialConditionByEffect(A, A, SpecialCondition.POISONED);
        int turn = match.turnNumber(), ownDamage = match.activePokemonView(A).damage();
        match.knockOutPokemonByEffect(A, B, target);
        settle(match);
        check(match.currentPlayer().equals(A) && match.turnNumber() == turn && match.phase() == MatchPhase.TURN,
            "非攻击效果击倒并拿奖后错误结束回合");
        check(match.activePokemonView(A).damage() == ownDamage, "非攻击击倒错误触发了中毒的回合间结算");
        match.endTurn(A);
        check(match.currentPlayer().equals(B) && match.activePokemonView(A).damage() == ownDamage + 10,
            "实际结束回合时未恰好执行一次回合间结算");
    }
    private static void lethalCountersDoNotInterruptFollowingEffects() {
        ServerMatch match = EffectSelectionAuthorityTest.fixture();
        int hand = match.privateView(A).hand().size();
        run(match, effect("damage_counter", EffectTarget.OPPONENT_ACTIVE, "12"), effect("draw", EffectTarget.SELF, "2"));
        check(match.privateView(A).hand().size() == hand + 2 && match.phase() == MatchPhase.PRIZE_SELECTION,
            "效果尚未全部执行就开始拿奖或中止抽牌");
        settle(match);
        check(match.currentPlayer().equals(A), "Effect 结算不应自行执行结束攻击/回合");
    }
    private static void simultaneousKnockouts() {
        ServerMatch match = EffectSelectionAuthorityTest.fixture();
        int aPrizes = match.privateView(A).prizes().size(), bPrizes = match.privateView(B).prizes().size();
        run(match, effect("damage_counter", EffectTarget.OPPONENT_ACTIVE, "12"),
            effect("damage_counter", EffectTarget.OWN_ACTIVE, "12"));
        check(match.publicView().get(A).activeCardId() == null && match.publicView().get(B).activeCardId() == null,
            "连锁指示物未统一进行击倒检查");
        settle(match);
        check(match.privateView(A).prizes().size() == aPrizes - 1 && match.privateView(B).prizes().size() == bPrizes - 1,
            "同时击倒的奖赏没有分别结算");
        check(match.currentPlayer().equals(A) && match.phase() == MatchPhase.TURN, "同时替补没有返回原回合");
    }
    private static void leavePlayThenIndependentText() {
        ServerMatch match = EffectSelectionAuthorityTest.fixture();
        int hand = match.privateView(A).hand().size();
        var departed = PokemonIdentityTestSupport.physical(match,B,match.activePokemonView(B).pokemonId());
        run(match, bare("return_to_hand", EffectTarget.OPPONENT_ACTIVE), effect("draw", EffectTarget.SELF, "2"));
        check(match.privateView(A).hand().size() == hand + 2, "离场后的独立抽牌步骤被错误拦截或未执行");
        check(match.privateView(B).hand().stream().anyMatch(card -> card.instanceId().equals(departed)), "离场宝可梦没有回到实际拥有者手牌");
        check(match.phase() == MatchPhase.CHOOSE_REPLACEMENT && match.pendingChoicePlayer().equals(B),
            "战斗宝可梦离场后没有在整段独立文本结束后要求替补");
        settle(match);
        check(match.currentPlayer().equals(A) && match.phase() == MatchPhase.TURN, "替补后没有回到原效果控制者的回合");
    }
    private static void leavePlayDependentTextFailsClosed() {
        ServerMatch match = EffectSelectionAuthorityTest.fixture();
        var before = match.clientView(A);
        rejects(() -> run(match, bare("return_to_hand", EffectTarget.OPPONENT_ACTIVE),
            effect("damage_counter", EffectTarget.OPPONENT_ACTIVE, "1")));
        check(before.equals(match.clientView(A)), "已离场对象的后续引用没有在预检阶段原子拒绝");
    }
    private static void malformedProgramDoesNotPartlyExecute() {
        for (EffectDefinition bad : List.of(effect("heal", EffectTarget.SELF, "11"),
            effect("damage_counter", EffectTarget.OPPONENT_ACTIVE, "61"), effect("draw", EffectTarget.SELF, "61"),
            new EffectDefinition("draw", EffectTarget.SELF, Map.of("amount", "1"), List.of(), List.of(effect("draw", EffectTarget.SELF, "1"))),
            new EffectDefinition("heal", EffectTarget.SELF, Map.of("amount", "10", "ammount", "20"), List.of(), List.of()))) {
            ServerMatch match = EffectSelectionAuthorityTest.fixture();
            UUID own = match.activePokemonView(A).pokemonId();
            match.placeDamageCountersByEffect(A, A, own, 3);
            var before = match.clientView(A); var cards = match.privateView(A);
            int replay = match.replayTranscript().size(), random = match.randomTranscript().size();
            rejects(() -> run(match, effect("draw", EffectTarget.SELF, "1"), bad));
            check(before.equals(match.clientView(A)) && cards.equals(match.privateView(A))
                && replay == match.replayTranscript().size() && random == match.randomTranscript().size(),
                "无效后续 Effect 已造成前置效果/日志/随机数变更");
        }
    }
    private static void failedSelectedEffectCanRetry() {
        ServerMatch match = EffectSelectionAuthorityTest.fixture();
        UUID own = match.activePokemonView(A).pokemonId(), bench = match.benchPokemonView(B).getFirst().pokemonId();
        var tx = match.openEffectPokemonSelection(A, own, EffectTarget.SELECT_OPPONENT_POKEMON, 1, 1, 1);
        var bad = program(new EffectDefinition("poison", EffectTarget.SELECT_OPPONENT_POKEMON, Map.of(), List.of(), List.of()));
        rejects(() -> match.executeEffectProgramWithPokemonSelectionByRule(A, own, bad, EffectTrigger.ON_CARD_PLAYED,
            tx, tx.transactionId(), 1, List.of(bench)));
        check(!tx.consumed(), "备战区无效状态目标在校验失败时消耗了选择授权");
        match.executeEffectProgramWithPokemonSelectionByRule(A, own, bad, EffectTrigger.ON_CARD_PLAYED,
            tx, tx.transactionId(), 1, List.of(match.activePokemonView(B).pokemonId()));
        check(tx.consumed() && match.activePokemonView(B).conditions().contains(SpecialCondition.POISONED), "有效重试未结算");
    }
    private static void run(ServerMatch match, EffectDefinition... effects) {
        match.executeEffectProgramByRule(A, match.activePokemonView(A).pokemonId(), program(effects), EffectTrigger.ON_CARD_PLAYED);
    }
    private static EffectProgram program(EffectDefinition... effects) {
        return new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_CARD_PLAYED, List.of(), List.of(effects))));
    }
    private static EffectDefinition effect(String type, EffectTarget target, String amount) {
        return new EffectDefinition(type, target, Map.of("amount", amount), List.of(), List.of());
    }
    private static EffectDefinition bare(String type, EffectTarget target) {
        return new EffectDefinition(type, target, Map.of(), List.of(), List.of());
    }
    private static void settle(ServerMatch match) {
        while (match.phase() == MatchPhase.PRIZE_SELECTION) match.takePrizeAt(match.pendingChoicePlayer(), 0);
        while (match.phase() == MatchPhase.CHOOSE_REPLACEMENT) {
            UUID owner = match.pendingChoicePlayer();
            match.chooseReplacement(owner, match.benchPokemonView(owner).getFirst().pokemonId());
        }
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("服务器接受了无效效果");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
