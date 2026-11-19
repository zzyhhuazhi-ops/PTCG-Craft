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

/** Server-only regression for in-turn Basic placement and a costed retreat. */
public final class ServerMatchFieldActionTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final Ruleset RULESET = new Ruleset("standard-international-2026", "2026.1.0", "国际 Standard（2026 赛季）", "international",
        LocalDate.of(2026, 4, 10), java.util.Set.of("H", "I", "J"), java.util.Set.of(), Map.of(), List.of(), List.of());

    public static void main(String[] args) {
        ServerMatch match = fixture();
        setup(match);
        int beforeBench = match.benchPokemonView(A).size();
        CardInstance handBasic = find(match.privateView(A).hand(), "fixture-basic-h");
        match.playBasicPokemonToBench(A, handBasic.instanceId());
        if (match.benchPokemonView(A).size() != beforeBench + 1)
            throw new AssertionError("从手牌放置基础宝可梦没有进入备战区");

        CardInstance energy = find(match.privateView(A).hand(), "fixture-energy-h");
        UUID formerActive = match.activePokemonView(A).pokemonId();
        match.attachBasicEnergy(A, energy.instanceId());
        UUID replacement = match.benchPokemonView(A).getFirst().pokemonId();
        match.retreat(A, replacement, List.of(energy.instanceId()));
        if (!match.activePokemonView(A).pokemonId().equals(replacement)
            || match.benchPokemonView(A).stream().noneMatch(view -> view.pokemonId().equals(formerActive))
            || match.privateView(A).discard().stream().noneMatch(card -> card.instanceId().equals(energy.instanceId())))
            throw new AssertionError("撤退位置交换或能量弃置错误");
        try { match.retreat(A, formerActive, List.of()); throw new AssertionError("同回合第二次撤退被接受"); }
        catch (IllegalStateException expected) { }
        System.out.println("ServerMatchFieldActionTest passed: 手牌基础宝可梦、撤退费用、弃置和战斗位交换均由服务器验证");
    }

    private static void setup(ServerMatch match) {
        OpeningTestSupport.complete(match, A, B, 1);
        if (!match.currentPlayer().equals(A)) throw new AssertionError("测试要求 A 为先手");
    }
    private static ServerMatch fixture() {
        for (int seed = 0; seed < 1000; seed++) {
            var aDeck = deck(A); var bDeck = deck(B);
            var m = new ServerMatch(A, B, aDeck, bDeck, RULESET, BundledRules.loadCardDefinitions(), BundledCombatData.load(), new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m, A, B);
            if (m.privateView(A).hand().stream().filter(c -> c.definitionId().equals("fixture-basic-h")).count() >= 3
                && m.privateView(B).hand().stream().filter(c -> c.definitionId().equals("fixture-basic-h")).count() >= 2
                && m.privateView(A).hand().stream().anyMatch(c -> c.definitionId().equals("fixture-energy-h")))
                return new ServerMatch(A, B, aDeck, bDeck, RULESET, BundledRules.loadCardDefinitions(), BundledCombatData.load(), new ServerRandom(seed));
        }
        throw new AssertionError("Could not find deterministic field-action fixture after coin-first shuffle");
    }
    private static List<CardInstance> deck(UUID owner) {
        List<CardInstance> cards = new ArrayList<>();
        for (int i = 0; i < 20; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "fixture-basic-h"));
        for (int i = 20; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "fixture-energy-h"));
        return cards;
    }
    private static CardInstance find(List<CardInstance> cards, String definitionId) {
        return cards.stream().filter(card -> card.definitionId().equals(definitionId)).findFirst().orElseThrow();
    }
}
