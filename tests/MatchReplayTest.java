import dev.hua.ptcglive.engine.MatchReplayEvent;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.rules.BundledRules;
import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.CardDatabase;
import dev.hua.ptcglive.rules.CardDefinition;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.rules.EffectSupport;
import dev.hua.ptcglive.rules.TrainerCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Replay events correlate public actions with random audit positions without exposing hidden card identities. */
public final class MatchReplayTest {
    private static final UUID A = new UUID(0, 8401);
    private static final UUID B = new UUID(0, 8402);
    public static void main(String[] args) {
        CardDefinition basic = new CardDefinition("basic", "测试基础宝可梦", CardCategory.POKEMON, TrainerCategory.NONE,
            "H", Set.of("international"), true, false, 4, Map.of(), EffectSupport.SUPPORTED, null);
        ServerMatch match = new ServerMatch(A, B, deck(A), deck(B), BundledRules.loadRulesets().get("standard-international-2026"),
            new CardDatabase(List.of(basic)), new ServerRandom(8403));
        OpeningTestSupport.complete(match, A, B, 0);
        List<MatchReplayEvent> beforeConcede = match.replayTranscript();
        check(!beforeConcede.isEmpty() && beforeConcede.getFirst().operation().equals("猜硬币并由服务器抛掷"), "回放没有记录开局公开动作");
        long prior = 0; int priorRandom = -1;
        for (MatchReplayEvent event : beforeConcede) {
            check(event.sequence() == ++prior && event.randomEventsThrough() >= priorRandom, "回放序号或随机审计关联不连续");
            check(!event.operation().contains("basic") && !event.operation().contains("00000000"), "回放事件泄露了卡牌身份");
            priorRandom = event.randomEventsThrough();
        }
        String clientJson = new com.google.gson.Gson().toJson(match.clientView(A));
        check(!clientJson.contains("服务器抽牌") && !clientJson.contains("猜硬币并由服务器抛掷"), "服务器回放被错误同步给客户端");
        match.concede(match.currentPlayer());
        check(match.replayTranscript().getLast().operation().equals("认输"), "结束动作没有进入服务器回放");
        System.out.println("MatchReplayTest passed: public-fact server replay correlates actions and randomness without client leakage");
    }
    private static List<CardInstance> deck(UUID owner) {
        List<CardInstance> cards = new ArrayList<>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(), i + 1), owner, "basic"));
        return cards;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
