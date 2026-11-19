import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.view.MatchActionMatcher;
import java.util.*;

/** Unpayable declarations may explain a restriction, but cannot become buttons or execute on the server. */
public final class AttackAvailabilityTest {
    private static final UUID A = new UUID(0, 1421), B = new UUID(0, 1422);
    public static void main(String[] args) {
        var attack = new AttackDefinition("costly", "费用测试", Map.of(EnergyType.GRASS, 1), 10, EnergyType.GRASS,
            EffectSupport.SUPPORTED, null);
        var definitions = new CardDatabase(List.of(definition("attacker"), definition("defender")));
        var combat = new CombatCatalog(Map.of(
            "attacker", new PokemonProfile(100, 0, null, null, 0, null, 0, 1, List.of(attack)),
            "defender", new PokemonProfile(100, 0, null, null, 0, null, 0, 1, List.of())), Map.of());
        ServerMatch match = new ServerMatch(A, B, deck(A, "attacker"), deck(B, "defender"),
            MatchRulesRegressionTest.RULE, definitions, combat, new ServerRandom(15));
        OpeningTestSupport.complete(match, A, B, 0);
        while (!match.currentPlayer().equals(A) || !match.mayAttack(A)) match.endTurn(match.currentPlayer());
        check(!match.canPayActiveAttackCost(A, attack), "无能量战斗宝可梦错误通过攻击费用校验");
        var session=MatchSession.multiplayer(match,A,B);
        var snapshot = session.view(A, A);
        check(snapshot.options().stream().noneMatch(MatchActionMatcher::isAvailableAttackOption),
            "能量不足的攻击仍被显示为可用技能按钮");
        check(MatchActionMatcher.cardFaceActions(snapshot.options(),"field:"+match.activePokemonView(A).pokemonId(),true).isEmpty(),
            "能量不足的攻击进入了统一卡面操作面板");
        var random=match.randomTranscript();var field=match.clientView(A);
        for(var option:snapshot.options()) if(MatchActionMatcher.isAttackOption(option)) {
            boolean rejected=false;
            try {session.submit(A,A,snapshot.revision(),option.index());}catch(IllegalStateException expected){rejected=true;}
            check(rejected,"伪造点击能量不足的攻击未被服务端拒绝");
        }
        check(random.equals(match.randomTranscript())&&field.equals(match.clientView(A)),"拒绝攻击改变了状态或随机数");
        System.out.println("AttackAvailabilityTest passed: insufficient Energy hides buttons and rejects forged submission without mutation");
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
