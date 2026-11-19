import com.google.gson.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.engine.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

public final class RegionalPracticeTest {
    public static void main(String[] args) {
        var all = BundledRules.loadRulesets();
        var cards = BundledRules.loadCardDefinitions(); var combat = BundledCombatData.load();
        var validator = new DeckValidator(cards, combat);
        var a = new UUID(0, 941);
        var b = new UUID(0, 942);
        for (var id : List.of("practice-cn-2024", "practice-cn-2025", "practice-cn-2026-0116")) {
            var rule = all.get(id); var deck = deck(a);
            var report = validator.validate(rule, deck);
            check(report.canStartMatch(), id + ": " + report.issues());
            check(!report.canStartOfficialMatch() && report.statusZh().equals("仅休闲可用"), "练习错误宣称赛事合法");
            var match = new ServerMatch(a, b, deck, deck(b), rule, cards, combat, new ServerRandom(9));
            OpeningTestSupport.complete(match, a, b, 0);
            check(match.phase() == MatchPhase.TURN && match.clientView(null).hand().isEmpty(), "练习未复用对局核心／泄露手牌");
            var unmapped = new ArrayList<>(deck); unmapped.set(0, new CardInstance(UUID.randomUUID(), a, "sv5_en_014"));
            check(!validator.validate(rule, unmapped).canStartMatch(), "没有简中对应的英版卡被放行");
            check(!validator.validate(rule, deck.subList(0, 59)).canStartMatch(), "练习绕过张数限制");
            var excess = new ArrayList<>(deck); excess.set(8, new CardInstance(UUID.randomUUID(), a, "swsh9_en_028"));
            check(!validator.validate(rule, excess).canStartMatch(), "练习绕过同名限制");
        }
        check(!validator.validate(all.get("standard-cn-2025"), deck(a)).canStartMatch(), "练习映射污染正式简中规则");
        JsonObject data;
        try (var in = RegionalPracticeTest.class.getResourceAsStream("/data/ptcglive/rulesets/practice-cn-2025.json");
             var reader = new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8)) {
            data = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) { throw new AssertionError(e); }
        data.getAsJsonObject("edition").addProperty("casualOnly", false);
        rejects(() -> BundledRules.ruleset(data));
        data.getAsJsonObject("edition").addProperty("casualOnly", true);
        data.getAsJsonObject("cardPool").addProperty("asOfDate", "2025-01-17");
        data.getAsJsonArray("legalRegulationMarks").remove(1); // F rotated out: a proxy must use CN mark, not English admission.
        check(!BundledRules.ruleset(data).allows(cards.find("swsh9_en_028").orElseThrow()), "代理卡绕过目标规则标记");
        data.getAsJsonArray("legalRegulationMarks").add("F");
        data.getAsJsonArray("bannedCardIds").add("swsh9_en_028");
        check(!BundledRules.ruleset(data).allows(cards.find("swsh9_en_028").orElseThrow()), "代理卡绕过禁用清单");
        data.getAsJsonArray("bannedCardIds").remove(0);
        var mapping = data.getAsJsonObject("cardPool").getAsJsonObject("practiceMappings").getAsJsonObject("swsh9_en_028");
        mapping.addProperty("legalFrom", "2025-01-18");
        check(!BundledRules.ruleset(data).allows(cards.find("swsh9_en_028").orElseThrow()), "尚未在简中发布的代理卡被放行");
        mapping.addProperty("unknownBypass", true); rejects(() -> BundledRules.ruleset(data));
        System.out.println("RegionalPracticeTest passed: three dated casual packages, explicit equivalents, real decks and shared server opening; no official/region/date/mark/size/hidden-info bypass");
    }
    private static List<CardInstance> deck(UUID owner) {
        var result = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) result.add(new CardInstance(new UUID(owner.getLeastSignificantBits(), i + 1), owner,
            i < 4 ? "swsh9_en_028" : "sve_en_003"));
        return result;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void rejects(Runnable r) { try { r.run(); } catch (IllegalArgumentException e) { return; } throw new AssertionError("应拒绝无效规则数据"); }
}
