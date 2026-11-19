import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.rules.BundledRules;
import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.CardDatabase;
import dev.hua.ptcglive.rules.CardDefinition;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.rules.EffectSupport;
import dev.hua.ptcglive.rules.MatchSetupRules;
import dev.hua.ptcglive.rules.Ruleset;
import dev.hua.ptcglive.rules.TrainerCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A ServerMatch must use the frozen setup snapshot, not a literal Standard deck size. */
public final class MatchSetupRulesTest {
    private static final UUID A = new UUID(0, 8201);
    private static final UUID B = new UUID(0, 8202);

    public static void main(String[] args) {
        Ruleset base = BundledRules.loadRulesets().get("standard-international-2026");
        Ruleset fortyCardPractice = new Ruleset(base.id(), "test-40-card", "测试四十张规则包", base.region(), base.effectiveDate(),
            base.legalRegulationMarks(), base.bannedCardIds(), base.deckTagLimits(), base.sources(), base.changeLogZh(),
            base.legalBasicEnergyIds(), base.setupProcedure(), new MatchSetupRules(40, 7, 6, 5),
            base.firstTurnRules(), base.turnActionRules());
        CardDefinition basic = new CardDefinition("basic", "测试基础宝可梦", CardCategory.POKEMON, TrainerCategory.NONE,
            "H", Set.of("international"), true, false, 4, Map.of(), EffectSupport.SUPPORTED, null);
        ServerMatch match = new ServerMatch(A, B, deck(A), deck(B), fortyCardPractice,
            new CardDatabase(List.of(basic)), new ServerRandom(8203));
        OpeningTestSupport.complete(match, A, B, 0);
        if (match.privateView(A).prizes().size() != 6 || match.privateView(B).prizes().size() != 6)
            throw new AssertionError("规则包奖赏数量没有用于开局");
        try {
            new ServerMatch(A, B, deck(A).subList(0, 39), deck(B), fortyCardPractice,
                new CardDatabase(List.of(basic)), new ServerRandom(8204));
            throw new AssertionError("服务端接受了不符合自定义规则包张数的卡组");
        } catch (IllegalArgumentException expected) { }
        System.out.println("MatchSetupRulesTest passed: setup deck size and prizes are read from the ruleset snapshot");
    }

    private static List<CardInstance> deck(UUID owner) {
        List<CardInstance> cards = new ArrayList<>();
        for (int i = 0; i < 40; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "basic"));
        return cards;
    }
}
