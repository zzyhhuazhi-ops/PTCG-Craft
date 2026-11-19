import dev.hua.ptcglive.engine.CoinSide;
import dev.hua.ptcglive.engine.MatchPhase;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.rules.BundledCombatData;
import dev.hua.ptcglive.rules.BundledRules;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.rules.Ruleset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-only regression for attack cost, weakness, Knock Out, Prize choice and replacement. */
public final class ServerMatchCombatTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000091");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000092");
    private static final Ruleset RULESET = new Ruleset("standard-international-2026", "2026.1.0", "国际 Standard（2026 赛季）", "international",
        LocalDate.of(2026, 4, 10), java.util.Set.of("H", "I", "J"), java.util.Set.of(), Map.of(), List.of(), List.of());

    public static void main(String[] args) {
        ServerMatch match = new ServerMatch(A, B, deck(A, "fixture-attacker-h"), deck(B, "fixture-defender-h"), RULESET,
            BundledRules.loadCardDefinitions(), BundledCombatData.load(), new ServerRandom(7));
        resolveSetup(match);

        CardInstance energy = find(match.privateView(A).hand(), "fixture-energy-h");
        match.attachBasicEnergy(A, energy.instanceId());
        match.endTurn(A);
        match.endTurn(B);
        match.attack(A, "fixture-zap");
        if (match.phase() != MatchPhase.PRIZE_SELECTION || match.lastAttack() == null || match.lastAttack().finalDamage() != 60
            || !match.lastAttack().weaknessApplied() || !match.lastAttack().knockedOut())
            throw new AssertionError("攻击、弱点或击倒没有由服务器正确结算");

        CardInstance prize = match.privateView(A).prizes().getFirst();
        match.takePrize(A, prize.instanceId());
        if (match.phase() != MatchPhase.CHOOSE_REPLACEMENT || match.publicView().get(A).prizeCount() != 5)
            throw new AssertionError("奖赏卡选择或公开计数错误");
        var replacement = match.benchPokemonView(B).getFirst();
        match.chooseReplacement(B, replacement.pokemonId());
        if (match.phase() != MatchPhase.TURN || !match.currentPlayer().equals(B)
            || !"fixture-defender-h".equals(match.publicView().get(B).activeCardId())
            || match.privateView(B).discard().stream().noneMatch(card -> card.definitionId().equals("fixture-defender-h")))
            throw new AssertionError("击倒弃牌、替补选择或回合交接错误: phase=" + match.phase()
                + ", current=" + match.currentPlayer() + ", active=" + match.publicView().get(B).activeCardId()
                + ", discard=" + match.privateView(B).discard().stream().map(CardInstance::definitionId).toList());
        System.out.println("ServerMatchCombatTest passed: 攻击费用、弱点、击倒、奖赏选择、替补与隐藏区域均已验证");
    }

    private static void resolveSetup(ServerMatch match) {
        OpeningTestSupport.complete(match, A, B, 1);
        if (!match.currentPlayer().equals(A)) throw new AssertionError("测试要求 A 为先手");
    }

    private static List<CardInstance> deck(UUID owner, String pokemonId) {
        List<CardInstance> cards = new ArrayList<>();
        for (int i = 0; i < 20; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, pokemonId));
        for (int i = 20; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "fixture-energy-h"));
        return cards;
    }
    private static CardInstance find(List<CardInstance> cards, String definitionId) {
        return cards.stream().filter(card -> card.definitionId().equals(definitionId)).findFirst()
            .orElseThrow(() -> new AssertionError("未在应有区域找到卡牌：" + definitionId));
    }
}
