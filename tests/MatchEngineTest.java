import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.rules.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Regression coverage for server-only setup randomness and first-turn restrictions. */
public final class MatchEngineTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000022");
    private static final Ruleset RULESET = new Ruleset("standard-international-2026", "2026.1.0", "国际 Standard（2026 赛季）", "international",
        LocalDate.of(2026, 4, 10), java.util.Set.of("H", "I", "J"), java.util.Set.of(), Map.of(), List.of(), List.of());

    public static void main(String[] args) {
        CardDatabase data = BundledRules.loadCardDefinitions();
        ServerMatch match = new ServerMatch(A, B, deck(A, 4), deck(B, 4), RULESET, data, new ServerRandom(42));
        resolveSetup(match, data);
        UUID active = match.currentPlayer();
        if (match.turnNumber() != 1 || match.mayAttack(active) || match.mayPlaySupporter(active))
            throw new AssertionError("先手首回合攻击或 Supporter 限制错误");
        CardInstance energy = find(match.privateView(active).hand(), "fixture-energy-h");
        match.attachBasicEnergy(active, energy.instanceId());
        try { match.attachBasicEnergy(active, find(match.privateView(active).hand(), "fixture-energy-h").instanceId()); throw new AssertionError("同回合第二次能量附着被接受"); }
        catch (IllegalStateException expected) { }
        match.endTurn(active);
        if (match.currentPlayer().equals(active) || !match.mayAttack(match.currentPlayer()) || !match.mayPlaySupporter(match.currentPlayer()))
            throw new AssertionError("后手首回合限制错误");
        if (match.publicView().values().stream().anyMatch(v -> v.handSize() < 0 || v.deckSize() < 0) || match.randomTranscript().stream().noneMatch(e -> e.operation().equals("coin-flip")))
            throw new AssertionError("公开视图或随机复盘记录错误");

        boolean sawMulligan = false;
        for (long seed = 0; seed < 200 && !sawMulligan; seed++) {
            ServerMatch candidate = new ServerMatch(A, B, deck(A, 1), deck(B, 4), RULESET, data, new ServerRandom(seed));
            OpeningTestSupport.complete(candidate, A, B, 0);
            sawMulligan = candidate.mulliganCount(A) > 0;
            if (sawMulligan) {
                if (candidate.phase() != MatchPhase.TURN) throw new AssertionError("重抽后未正常完成开局");
            }
        }
        if (!sawMulligan) throw new AssertionError("mulligan 测试夹具未触发");
        System.out.println("MatchEngineTest passed: 服务器随机、mulligan、硬币、先后手、首回合、能量与隐藏视图均已验证");
    }
    private static void resolveSetup(ServerMatch match, CardDatabase data) {
        OpeningTestSupport.complete(match, A, B, 0);
    }
    private static List<CardInstance> deck(UUID owner, int basics) {
        List<CardInstance> result = new ArrayList<>();
        for (int i = 0; i < basics; i++) result.add(new CardInstance(UUID.randomUUID(), owner, "fixture-basic-h"));
        for (int i = basics; i < 60; i++) result.add(new CardInstance(UUID.randomUUID(), owner, "fixture-energy-h"));
        return result;
    }
    private static CardInstance find(List<CardInstance> cards, String id) { return cards.stream().filter(c -> c.definitionId().equals(id)).findFirst().orElseThrow(); }
    private static CardInstance findBasic(List<CardInstance> cards, CardDatabase data) { return cards.stream().filter(c -> data.find(c.definitionId()).orElseThrow().basicPokemon()).findFirst().orElseThrow(); }
}
