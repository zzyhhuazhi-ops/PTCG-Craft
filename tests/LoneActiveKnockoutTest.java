import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Server regression: an attack which KOs the only Pokémon ends the match without a replacement or Prize prompt. */
public final class LoneActiveKnockoutTest {
    private static final UUID A = new UUID(0, 1411), B = new UUID(0, 1412);
    public static void main(String[] args) {
        ServerMatch match = fixture();
        while (!match.currentPlayer().equals(A) || !match.mayAttack(A)) match.endTurn(match.currentPlayer());
        match.attack(A, "finish");
        check(match.phase() == MatchPhase.FINISHED, "唯一战斗宝可梦被击倒后没有立即结束对局");
        check(match.result() != null && A.equals(match.result().winner()) && match.result().reason() == MatchResult.Reason.NO_POKEMON,
            "唯一战斗宝可梦被击倒后没有以无宝可梦自动判负");
        var defeated = match.clientView(A).players().stream().filter(player -> B.equals(player.id())).findFirst().orElseThrow();
        check(defeated.active() == null && defeated.bench().isEmpty(), "结束对局前仍错误要求选择替补宝可梦");
        check(match.privateView(A).prizes().size() == 6, "自动无宝可梦胜利不应要求手动选择奖赏卡");
        simultaneousLastPrizes(1, null);
        simultaneousLastPrizes(3, A);
        System.out.println("LoneActiveKnockoutTest passed: immediate NO_POKEMON, simultaneous last-Prize tie and two-vs-one win conditions");
    }
    private static void simultaneousLastPrizes(int defenderPrizes, UUID winner) {
        var attack = new AttackDefinition("recoil", "同时击倒验证", Map.of(), 100, EnergyType.COLORLESS,
            EffectSupport.SUPPORTED, null, List.of(), null, null, List.of(), AttackDefinition.DamageTarget.OPPONENT_ACTIVE, 100);
        var combat = new CombatCatalog(Map.of(
            "attacker", new PokemonProfile(100, 0, null, null, 0, null, 0, 3, List.of(attack)),
            "defender", new PokemonProfile(100, 0, null, null, 0, null, 0, defenderPrizes, List.of())), Map.of());
        // A test-only three-Prize game reaches the final-Prize boundary without
        // inventing an illegal six-Prize Pokemon or mutating private match state.
        var base = MatchRulesRegressionTest.RULE;
        var threePrizes = new Ruleset(base.id(), "test-three-prizes", "同时胜利边界测试", base.region(), base.effectiveDate(),
            base.legalRegulationMarks(), base.bannedCardIds(), base.deckTagLimits(), base.sources(), base.changeLogZh(),
            base.legalBasicEnergyIds(), base.setupProcedure(), new MatchSetupRules(60, 7, 3, 5),
            base.firstTurnRules(), base.turnActionRules());
        var m = new ServerMatch(A, B, deck(A, "attacker"), deck(B, "defender"), threePrizes,
            new CardDatabase(List.of(definition("attacker"), definition("defender"))), combat, new ServerRandom(14));
        OpeningTestSupport.complete(m, A, B, 0);
        m.playBasicPokemonToBench(A, m.privateView(A).hand().getFirst().instanceId());
        m.endTurn(A); m.endTurn(B); m.attack(A, "recoil");
        check(m.phase() == MatchPhase.PRIZE_SELECTION, "反伤对手可同时拿完奖赏时被提前判负");
        int choices = 0;
        while (m.phase() == MatchPhase.PRIZE_SELECTION && choices++ < 12) m.takePrizeAt(m.pendingChoicePlayer(), 0);
        check(m.phase() == MatchPhase.FINISHED && Objects.equals(m.result().winner(), winner), "同时胜利条件的胜者错误");
        check(m.result().reason() == (winner == null ? MatchResult.Reason.DRAW : MatchResult.Reason.PRIZES),
            "相同／不同胜利条件数量的结果错误");
    }
    private static ServerMatch fixture() {
        var attack = new AttackDefinition("finish", "终结测试", Map.of(), 100, EnergyType.COLORLESS,
            EffectSupport.SUPPORTED, null);
        var definitions = new CardDatabase(List.of(definition("attacker"), definition("defender")));
        var combat = new CombatCatalog(Map.of(
            "attacker", new PokemonProfile(100, 0, null, null, 0, null, 0, 1, List.of(attack)),
            "defender", new PokemonProfile(100, 0, null, null, 0, null, 0, 1, List.of())), Map.of());
        ServerMatch match = new ServerMatch(A, B, deck(A, "attacker"), deck(B, "defender"),
            MatchRulesRegressionTest.RULE, definitions, combat, new ServerRandom(14));
        OpeningTestSupport.complete(match, A, B, 0);
        return match;
    }
    private static CardDefinition definition(String id) {
        return new CardDefinition(id, id, CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"), true,
            false, 4, Map.of(), EffectSupport.SUPPORTED, null);
    }
    private static List<CardInstance> deck(UUID owner, String id) {
        var cards = new ArrayList<CardInstance>();
        for (int index = 0; index < 60; index++) cards.add(new CardInstance(UUID.randomUUID(), owner, id));
        return cards;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
