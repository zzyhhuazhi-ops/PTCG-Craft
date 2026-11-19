import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Server regression: recoil is resolved with attack damage before the shared KO/prize procedure. */
public final class RecoilAttackTest {
    private static final UUID A = new UUID(0, 1301), B = new UUID(0, 1302);
    public static void main(String[] args) {
        ServerMatch match = fixture();
        while (!match.currentPlayer().equals(A) || !match.mayAttack(A)) match.endTurn(match.currentPlayer());
        match.attack(A, "recoil");
        if (match.lastAttack().selfDamage() != 100 || match.phase() != MatchPhase.PRIZE_SELECTION)
            throw new AssertionError("反伤必须写入服务器攻击审计，并与攻击伤害共同进入击倒流程");
        if (!match.pendingChoicePlayer().equals(B)) throw new AssertionError("同时击倒时应先由下一回合玩家选择奖赏");
        match.takePrizeAt(B, 0);
        if (!match.pendingChoicePlayer().equals(A)) throw new AssertionError("双方各一张奖赏必须逐项由服务器处理");
        match.takePrizeAt(A, 0);
        if (match.result() == null || match.result().winner() != null || match.result().reason() != MatchResult.Reason.DRAW)
            throw new AssertionError("双方反伤同时无宝可梦时必须按规则记为平局");
        System.out.println("RecoilAttackTest passed: 反伤、同时击倒、奖赏顺序和平局均由服务器结算");
    }
    private static ServerMatch fixture() {
        var definitions = new CardDatabase(List.of(definition("recoiler"), definition("target")));
        var recoil = new AttackDefinition("recoil", "测试反伤", Map.of(), 100, EnergyType.COLORLESS,
            EffectSupport.SUPPORTED, null, List.of(), null, null, List.of(), AttackDefinition.DamageTarget.OPPONENT_ACTIVE, 100);
        var combat = new CombatCatalog(Map.of(
            "recoiler", new PokemonProfile(100, 0, null, null, 0, null, 0, 1, List.of(recoil)),
            "target", new PokemonProfile(100, 0, null, null, 0, null, 0, 1, List.of())), Map.of());
        ServerMatch match = new ServerMatch(A, B, deck(A, "recoiler"), deck(B, "target"),
            MatchRulesRegressionTest.RULE, definitions, combat, new ServerRandom(4));
        OpeningTestSupport.complete(match, A, B, 0);
        return match;
    }
    private static CardDefinition definition(String id) {
        return new CardDefinition(id, id, CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"), true,
            false, 4, Map.of(), EffectSupport.SUPPORTED, null);
    }
    private static List<CardInstance> deck(UUID owner, String id) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, id));
        return cards;
    }
}
