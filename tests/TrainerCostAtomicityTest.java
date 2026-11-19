import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** A missing cost-selection intent must not destroy the played card or consume turn/random state. */
public final class TrainerCostAtomicityTest {
    private static final UUID A = new UUID(0, 39801), B = new UUID(0, 39802);
    public static void main(String[] args) {
        var match = fixture();
        var trainer = match.privateView(A).hand().stream().filter(c -> c.definitionId().equals("cost")).findFirst().orElseThrow();
        var before = match.privateView(A); var opponent = match.privateView(B); var publicBefore = match.clientView(null);
        var randomBefore = match.randomTranscript(); var replayBefore = match.replayTranscript();
        rejects(() -> match.playTrainer(A, trainer.instanceId(), null));
        check(before.equals(match.privateView(A)) && opponent.equals(match.privateView(B))
            && publicBefore.equals(match.clientView(null)) && randomBefore.equals(match.randomTranscript())
            && replayBefore.equals(match.replayTranscript()), "未付成本的训练家被拒绝后丢失卡牌或改变状态");
        check(!match.canPlayTrainer(A, trainer.instanceId(), null), "无需成本的单击入口错误宣称可以使用带成本的训练家");
        var session = MatchSession.multiplayer(match, A, B);
        check(session.view(A, A).options().stream().noneMatch(o -> o.cardKeys().contains("hand:" + trainer.instanceId())),
            "卡桌生成了未接入成本选择的错误操作");
        // Compact record constructors must validate their arguments, not their not-yet-assigned fields.
        rejects(() -> new TrainerEffect(TrainerEffect.Opcode.TOOL_RETREAT_REDUCTION, 1, 0, 1, 2));
        rejects(() -> new TrainerEffect(TrainerEffect.Opcode.STADIUM_BASIC_RETREAT_REDUCTION, 1, 0, 0, 2));
        rejects(() -> new TrainerEffect(TrainerEffect.Opcode.HEAL, 10, 0, 0, 2));
        rejects(() -> new TrainerEffect(TrainerEffect.Opcode.SWITCH_ACTIVE, 0, 0, 0, 2));
        // A supported cost-bearing search remains valid data and uses its existing dedicated atomic entry point.
        new TrainerEffect(TrainerEffect.Opcode.SEARCH_POKEMON_TO_HAND, 1, 0, 0, 2);
        System.out.println("TrainerCostAtomicityTest passed: rejected costs preserve cards/state, unsupported single-click actions stay hidden, constructor guards hold");
    }
    private static ServerMatch fixture() {
        var cards = new CardDatabase(List.of(def("basic", CardCategory.POKEMON, TrainerCategory.NONE, true),
            def("cost", CardCategory.TRAINER, TrainerCategory.ITEM, false)));
        var combat = new CombatCatalog(Map.of("basic", new PokemonProfile(60, 0, null, null, 0, null, 0, 1, List.of())), Map.of(),
            Map.of("cost", new TrainerProfile(TrainerCategory.ITEM, new TrainerEffect(TrainerEffect.Opcode.DRAW, 3, 0, 0, 2))));
        for (long seed = 0; seed < 100; seed++) {
            var match = new ServerMatch(A, B, deck(A), deck(B), BundledRules.loadRulesets().get("standard-international-2026"), cards, combat, new ServerRandom(seed));
            OpeningTestSupport.decideOrder(match, A, B);
            if (match.needsMulligan(A) || match.needsMulligan(B)
                || match.privateView(A).hand().stream().noneMatch(c -> c.definitionId().equals("cost"))) continue;
            OpeningTestSupport.complete(match, A, B, 0);
            return match;
        }
        throw new AssertionError("无法创建带成本测试开局");
    }
    private static CardDefinition def(String id, CardCategory category, TrainerCategory trainer, boolean basic) {
        return new CardDefinition(id, id, category, trainer, "H", Set.of("international"), basic, false, 4, Map.of(), EffectSupport.SUPPORTED, null);
    }
    private static List<CardInstance> deck(UUID owner) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(), i + 1), owner, i % 2 == 0 ? "basic" : "cost"));
        return cards;
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("错误接受未支持的训练家成本组合");
    }
}
