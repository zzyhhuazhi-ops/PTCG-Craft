import dev.hua.ptcglive.engine.MatchPhase;
import dev.hua.ptcglive.engine.MatchSession;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Test-first contract for selectable Benched targets and their Knock Out procedure. */
public final class BenchTargetAttackTest {
    private static final UUID A = new UUID(0, 1201), B = new UUID(0, 1202);
    public static void main(String[] args) {
        chosenBenchTarget();
        allBenchedTargetsAwardEachPrize();
        multipleChosenBenchTargetsRejectForgery();
        System.out.println("BenchTargetAttackTest passed: 单目标/多目标/全体目标、备战伤害、击倒奖赏和拒绝非法目标均由服务器验证");
    }
    private static void chosenBenchTarget() {
        ServerMatch match = fixture();
        while (!match.currentPlayer().equals(A) || !match.mayAttack(A)) match.endTurn(match.currentPlayer());
        UUID opponentActive = match.activePokemonView(B).pokemonId();
        UUID opponentBench = match.benchPokemonView(B).getFirst().pokemonId();
        rejects(() -> match.attack(A, "snipe", null));
        rejects(() -> match.attack(A, "snipe", opponentActive));
        match.attack(A, "snipe", opponentBench);
        if (match.lastAttack().finalDamage() != 100 || match.lastAttack().weaknessApplied() || match.lastAttack().resistanceApplied())
            throw new AssertionError("备战伤害不应错误结算弱点或抵抗");
        if (match.activePokemonView(B).pokemonId() == null || !match.benchPokemonView(B).isEmpty())
            throw new AssertionError("备战目标被击倒时不应错误移除对方战斗宝可梦");
        if (match.phase() != MatchPhase.PRIZE_SELECTION || !match.pendingChoicePlayer().equals(A))
            throw new AssertionError("击倒备战宝可梦后，奖赏选择应由服务器进入正确流程");
        match.takePrizeAt(A, 0);
        if (match.result() != null) throw new AssertionError("对方仍有战斗宝可梦时，击倒备战宝可梦不能直接判负");
    }
    private static void allBenchedTargetsAwardEachPrize() {
        var attack = new AttackDefinition("spread", "测试扩散", Map.of(), 100, EnergyType.COLORLESS,
            EffectSupport.SUPPORTED, null, List.of(), null, null, List.of(), AttackDefinition.DamageTarget.ALL_OPPONENT_BENCH);
        ServerMatch match = fixture(attack, 2);
        while (!match.currentPlayer().equals(A) || !match.mayAttack(A)) match.endTurn(match.currentPlayer());
        UUID forgedSingleTarget = match.benchPokemonView(B).getFirst().pokemonId();
        rejects(() -> match.attack(A, "spread", forgedSingleTarget));
        match.attack(A, "spread");
        if (!match.benchPokemonView(B).isEmpty() || match.activePokemonView(B) == null)
            throw new AssertionError("全体备战伤害不得移除对方战斗宝可梦");
        if (match.phase() != MatchPhase.PRIZE_SELECTION || !match.pendingChoicePlayer().equals(A))
            throw new AssertionError("同时击倒两只备战宝可梦后必须由服务器连续处理两张奖赏");
        match.takePrizeAt(A, 0);
        if (match.phase() != MatchPhase.PRIZE_SELECTION || !match.pendingChoicePlayer().equals(A))
            throw new AssertionError("多张奖赏不能被错误合并或跳过");
        match.takePrizeAt(A, 0);
        if (match.phase() == MatchPhase.PRIZE_SELECTION || match.result() != null)
            throw new AssertionError("两张奖赏处理完成后应继续对局而非错误结束");
    }
    private static void multipleChosenBenchTargetsRejectForgery() {
        var attack = new AttackDefinition("dual", "测试双目标", Map.of(), 100, EnergyType.COLORLESS,
            EffectSupport.SUPPORTED, null, List.of(), null, null, List.of(), AttackDefinition.DamageTarget.OPPONENT_BENCH, 0, 2);
        ServerMatch match = fixture(attack, 2);
        while (!match.currentPlayer().equals(A) || !match.mayAttack(A)) match.endTurn(match.currentPlayer());
        UUID first = match.benchPokemonView(B).get(0).pokemonId(), second = match.benchPokemonView(B).get(1).pokemonId();
        rejects(() -> match.attack(A, "dual", first));
        rejects(() -> match.attackTargets(A, "dual", List.of(first, first)));
        MatchSession session = MatchSession.multiplayer(match, A, B);
        var snapshot = session.view(A, A);
        var choices = snapshot.options().stream().filter(option -> option.labelZh().startsWith("攻击：测试双目标")).toList();
        if (choices.size() != 1 || choices.getFirst().cardKeys().size() != 3)
            throw new AssertionError("客户端只能收到服务器生成的唯一双目标组合及公开场上锚点");
        session.submit(A, A, snapshot.revision(), choices.getFirst().index());
        if (!match.benchPokemonView(B).isEmpty() || match.phase() != MatchPhase.PRIZE_SELECTION)
            throw new AssertionError("服务器应仅接受恰好两只不同的对方备战目标并同时处理击倒");
    }
    private static ServerMatch fixture() {
        var attack = new AttackDefinition("snipe", "测试狙击", Map.of(), 100, EnergyType.COLORLESS,
            EffectSupport.SUPPORTED, null, List.of(), null, null, List.of(), AttackDefinition.DamageTarget.OPPONENT_BENCH);
        return fixture(attack, 1);
    }
    private static ServerMatch fixture(AttackDefinition attack, int benches) {
        var definitions = new CardDatabase(List.of(
            definition("attacker"), definition("defender")));
        var combat = new CombatCatalog(Map.of(
            "attacker", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(attack)),
            "defender", new PokemonProfile(90, 0, null, null, 0, null, 0, 1, List.of())), Map.of());
        for (int seed = 0; seed < 100; seed++) {
            var match = new ServerMatch(A, B, deck(A, "attacker"), deck(B, "defender"),
                MatchRulesRegressionTest.RULE, definitions, combat, new ServerRandom(seed));
            OpeningTestSupport.complete(match, A, B, benches);
            return match;
        }
        throw new AssertionError("unable to create target fixture");
    }
    private static CardDefinition definition(String id) {
        return new CardDefinition(id, id, CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"), true,
            false, 4, Map.of(), EffectSupport.SUPPORTED, null);
    }
    private static List<CardInstance> deck(UUID owner, String id) {
        var result = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) result.add(new CardInstance(UUID.randomUUID(), owner, id));
        return result;
    }
    private static void rejects(Runnable operation) {
        try { operation.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法攻击目标被接受");
    }
}
