import dev.hua.ptcglive.engine.EffectSourceZone;
import dev.hua.ptcglive.engine.MatchSession;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.rules.BundledRules;
import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.CardDatabase;
import dev.hua.ptcglive.rules.CardDefinition;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.rules.EffectSupport;
import dev.hua.ptcglive.rules.Ruleset;
import dev.hua.ptcglive.rules.TrainerCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Public Lost Zone faces, private-zone filtering and server-only movement. */
public final class LostZoneTest {
    private static final UUID A = new UUID(0, 8301);
    private static final UUID B = new UUID(0, 8302);

    public static void main(String[] args) {
        Ruleset rules = BundledRules.loadRulesets().get("standard-international-2026");
        CardDefinition basic = new CardDefinition("basic", "测试基础宝可梦", CardCategory.POKEMON, TrainerCategory.NONE,
            "H", Set.of("international"), true, false, 4, Map.of(), EffectSupport.SUPPORTED, null);
        ServerMatch match = new ServerMatch(A, B, deck(A), deck(B), rules, new CardDatabase(List.of(basic)), new ServerRandom(8303));
        OpeningTestSupport.complete(match, A, B, 0);
        while (!match.currentPlayer().equals(A)) match.endTurn(match.currentPlayer());
        CardInstance sent = match.privateView(A).hand().getFirst();
        match.sendToLostZoneByEffect(A, EffectSourceZone.HAND, sent.instanceId());

        check(!match.privateView(A).hand().contains(sent) && match.privateView(A).lostZone().equals(List.of(sent)),
            "服务端没有原子地将卡牌从手牌移入放逐区");
        check(match.publicView().get(A).lostZoneSize() == 1, "公开快照没有放逐区计数");
        for (UUID viewer : List.of(A, B)) {
            var visible = match.clientView(viewer).players().stream().filter(p -> p.id().equals(A)).findFirst().orElseThrow();
            check(visible.lostZone().equals(List.of(sent)), "公开放逐卡面没有同步给应看到的玩家");
        }
        var session = MatchSession.multiplayer(match, A, B);
        check(session.view(B, B).cardNames().containsKey(sent.definitionId()), "接收者没有取得公开放逐卡的中文名称");
        rejects(() -> match.sendToLostZoneByEffect(B, EffectSourceZone.HAND, sent.instanceId()));
        check(session.view(B, B).game().hand().stream().noneMatch(c -> c.ownerId().equals(A)), "对手仍不应获得我方手牌");
        System.out.println("LostZoneTest passed: public Lost Zone, private hand filtering and server-only moves are enforced");
    }

    private static List<CardInstance> deck(UUID owner) {
        List<CardInstance> result = new ArrayList<>();
        for (int i = 0; i < 60; i++) result.add(new CardInstance(UUID.randomUUID(), owner, "basic"));
        return result;
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法放逐操作被接受");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
