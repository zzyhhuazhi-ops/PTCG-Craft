import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.ConditionEffect;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.engine.field.SpecialCondition;
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

/** Coin-gated attack conditions are rolled, audited and applied only by the server. */
public final class AttackCoinConditionTest {
    private static final UUID A = new UUID(0, 6371), B = new UUID(0, 6372);
    public static void main(String[] args) {
        rejects(() -> new ConditionEffect.CoinCondition(0, 1));
        rejects(() -> new ConditionEffect.CoinCondition(1, 2));
        ServerMatch heads = null, tails = null;
        for (long seed = 1; seed < 5_000 && (heads == null || tails == null); seed++) {
            ServerMatch candidate = fixture(seed);
            candidate.attack(A, "coin-burn");
            if (candidate.activePokemonView(B).conditions().contains(SpecialCondition.BURNED)) heads = candidate;
            else tails = candidate;
        }
        if (heads == null || tails == null) throw new AssertionError("无法建立正反面均可复现的异常状态硬币测试");
        if (heads.randomTranscript().stream().noneMatch(event -> event.operation().startsWith("attack-condition:coin-burn"))
            || tails.randomTranscript().stream().noneMatch(event -> event.operation().startsWith("attack-condition:coin-burn")))
            throw new AssertionError("异常状态硬币没有写入服务器随机审计");
        System.out.println("AttackCoinConditionTest passed: coin-gated attack conditions are server-rolled, audited and conditional");
    }
    private static ServerMatch fixture(long seed) {
        CardDefinition attacker = definition("attacker"), defender = definition("defender");
        var burn = new AttackDefinition("coin-burn", "测试灼伤", Map.of(), 0, EnergyType.COLORLESS, EffectSupport.SUPPORTED, null,
            List.of(new ConditionEffect(ConditionEffect.Target.DEFENDER, SpecialCondition.BURNED,
                new ConditionEffect.CoinCondition(1, 1))));
        var combat = new CombatCatalog(Map.of(
            "attacker", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(burn)),
            "defender", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of())), Map.of());
        ServerMatch match = new ServerMatch(A, B, deck(A, "attacker"), deck(B, "defender"), MatchRulesRegressionTest.RULE,
            new CardDatabase(List.of(attacker, defender)), combat, new ServerRandom(seed));
        OpeningTestSupport.complete(match, A, B, 0);
        for (int turns = 0; turns < 4; turns++) {
            if (match.currentPlayer().equals(A) && match.mayAttack(A)) return match;
            match.endTurn(match.currentPlayer());
        }
        throw new AssertionError("测试夹具未到达甲方可攻击回合");
    }
    private static CardDefinition definition(String id) { return new CardDefinition(id, id, CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"), true, false, 4, Map.of(), EffectSupport.SUPPORTED, null); }
    private static List<CardInstance> deck(UUID owner, String id) { var cards = new ArrayList<CardInstance>(); for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, id)); return cards; }
    private static void rejects(Runnable action) { try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("无效异常状态硬币条件被接受"); }
}
