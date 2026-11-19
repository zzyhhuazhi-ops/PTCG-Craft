import dev.hua.ptcglive.collection.BoxEditorSession;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.table.*;
import java.time.Clock;
import java.util.*;
import java.time.LocalDate;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Regional archives and unrestricted casual pools must share server validation, not bypass it. */
public final class RegionalRulesetTest {
    private static final UUID A = new UUID(0, 31001), B = new UUID(0, 31002);
    public static void main(String[] args) {
        var rules = BundledRules.loadRulesets();
        var open = rules.get("custom-open-all");
        check(open != null, "缺少允许全部年代／地区卡池的开放规则包");
        for (String region : List.of("international", "cn", "cn-community", "jp"))
            for (String mark : List.of("A", "F", "H", "J", "NONE", "SD100"))
                check(open.allows(card("sample", region, mark, EffectSupport.SUPPORTED)), "开放卡池错误限制地区或标记");
        var standard = rules.get("standard-international-2026");
        check(!standard.allows(card("sample", "cn", "H", EffectSupport.SUPPORTED)), "国际规则混入简中印刷");
        var cards = BundledRules.loadCardDefinitions();
        var combat = BundledCombatData.load();
        var validator = new DeckValidator(cards, combat);
        var deck = deck(A);
        var report = validator.validate(open, deck);
        check(report.issues().stream().noneMatch(i -> i.severity() == DeckIssue.Severity.ERROR), "开放合法测试卡组被拒绝");
        check(!report.canStartOfficialMatch(), "自定义开放规则错误宣称为正式赛事资格");
        var unknown = new ArrayList<>(deck);
        unknown.set(0, new CardInstance(UUID.randomUUID(), A, "fixture-unsupported-h"));
        check(has(validator.validate(open, unknown), "效果未支持"), "开放卡池忽略未实现卡文");
        check(has(validator.validate(open, deck.subList(0, 59)), "牌组张数"), "开放卡池绕过 60 张检查");
        var tooMany = new ArrayList<>(deck);
        tooMany.set(5, new CardInstance(UUID.randomUUID(), A, "fixture-basic-h"));
        check(has(validator.validate(open, tooMany), "同名数量超限"), "开放卡池绕过同名限制");
        var duplicate = new ArrayList<>(deck); duplicate.set(10, duplicate.get(9));
        check(has(validator.validate(open, duplicate), "卡牌实例重复"), "开放卡池允许复制实际卡牌");
        for (String id : List.of("standard-international-2023", "standard-international-2024", "standard-international-2025",
            "standard-cn-2023", "standard-cn-2024-0517", "standard-cn-2024-0618", "standard-cn-2025",
            "standard-cn-2026-0116", "standard-cn-2026-0716", "standard-cn-2026-0916")) {
            var archive = rules.get(id);
            check(archive != null && !archive.sources().isEmpty(), "缺少有来源的规则档案：" + id);
            check(has(validator.validate(archive, deck), "规则数据待核验"), "未核验完整历史规则被误判可开局：" + id);
            rejects(() -> new ServerMatch(A, B, deck(A), deck(B), archive, cards, combat, new ServerRandom(3)));
        }
        check(rules.get("standard-cn-2024-0517").legalRegulationMarks().equals(Set.of("C", "D", "E", "F")), "简中 2024 首次变更错误");
        check(rules.get("standard-cn-2024-0618").legalRegulationMarks().equals(Set.of("D", "E", "F")), "简中同年二次轮替丢失");
        check(rules.get("standard-cn-2026-0916").legalRegulationMarks().equals(Set.of("G", "H", "I", "J")), "简中当前标记被错误复制为国际标记");
        check(rules.get("standard-cn-2026-0116").coversDate(LocalDate.parse("2026-07-15"))
            && !rules.get("standard-cn-2026-0116").coversDate(LocalDate.parse("2026-07-16")), "简中 F/G/H 快照结束日期错误");
        check(rules.get("standard-cn-2026-0716").legalRegulationMarks().equals(Set.of("G", "H", "I"))
            && rules.get("standard-cn-2026-0716").coversDate(LocalDate.parse("2026-09-15"))
            && !rules.get("standard-cn-2026-0716").coversDate(LocalDate.parse("2026-09-16")), "简中 G/H/I 过渡快照错误");
        openTableAndBox(rules, cards, combat);
        datedPoolsAndParsing();
        System.out.println("RegionalRulesetTest passed: unrestricted casual pool, regional archives, server gates, box report and shared practice core");
    }
    private static void datedPoolsAndParsing() {
        JsonObject json;
        try (var in = RegionalRulesetTest.class.getResourceAsStream("/data/ptcglive/rulesets/standard-cn-2025.json");
             var reader = new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) { throw new AssertionError(e); }
        var pool = json.getAsJsonObject("cardPool");
        pool.getAsJsonObject("legalFromDates").addProperty("early", "2025-01-17");
        pool.getAsJsonObject("legalFromDates").addProperty("late", "2025-01-18");
        pool.getAsJsonObject("legalFromDates").addProperty("reprint", "2024-12-01");
        pool.getAsJsonArray("legalCardIds").add("reprint");
        var rule = BundledRules.ruleset(json);
        check(rule.allows(card("early", "cn", "G", EffectSupport.SUPPORTED)), "快照生效当日卡被误拒绝");
        check(!rule.allows(card("late", "cn", "G", EffectSupport.SUPPORTED)), "同标记未来印刷混入历史卡池");
        check(rule.cardPool().assess(rule, card("unknown", "cn", "G", EffectSupport.SUPPORTED)).status() == CardPoolPolicy.Status.NEEDS_DATA,
            "缺少印刷合法日期应要求更新资料，不应猜测合法");
        check(!rule.allows(card("early", "international", "G", EffectSupport.SUPPORTED)), "合法日期绕过地区限制");
        check(rule.allows(card("reprint", "cn", "A", EffectSupport.SUPPORTED)), "审核编号白名单未允许等价旧版卡");
        check(!rule.allows(card("other-reprint", "cn", "A", EffectSupport.SUPPORTED)), "同名但未审核再版被放行");
        check(rule.coversDate(LocalDate.parse("2025-01-17")) && rule.coversDate(LocalDate.parse("2026-01-15"))
            && !rule.coversDate(LocalDate.parse("2026-01-16")), "生效区间边界错误");
        // A loaded edition must remain frozen when its input JSON is modified for a later snapshot.
        json.getAsJsonArray("bannedCardIds").add("reprint");
        check(rule.allows(card("reprint", "cn", "A", EffectSupport.SUPPORTED)), "已创建规则不是不可变快照");
        check(!BundledRules.ruleset(json).allows(card("reprint", "cn", "A", EffectSupport.SUPPORTED)), "白名单越过禁卡表");
        pool.addProperty("mode", "REVIEWED_CARD_IDS");
        var explicit = BundledRules.ruleset(json);
        check(!explicit.allows(card("early", "cn", "G", EffectSupport.SUPPORTED)), "精确编号模式仍按标记自动扩张卡池");
        pool.addProperty("unexpected_allow_everything", true);
        rejects(() -> BundledRules.ruleset(json)); pool.remove("unexpected_allow_everything");
        json.addProperty("schemaVersion", 99); rejects(() -> BundledRules.ruleset(json));
        json.addProperty("schemaVersion", 2);
        json.addProperty("trainerClassifcation", true); rejects(() -> BundledRules.ruleset(json)); json.remove("trainerClassifcation");
        var classification = json.getAsJsonObject("trainerClassification");
        classification.addProperty("pokemonToolsAreItems", "maybe"); rejects(() -> BundledRules.ruleset(json));
        classification.addProperty("pokemonToolsAreItems", false);
        pool.addProperty("asOfDate", "2027-01-01"); rejects(() -> BundledRules.ruleset(json));
        pool.addProperty("asOfDate", "2025-01-17");
        pool.addProperty("mode", "ALL_CARDS"); rejects(() -> BundledRules.ruleset(json));
        pool.remove("asOfDate"); pool.remove("legalFromDates"); pool.remove("basicEnergyTypes"); pool.getAsJsonArray("legalCardIds").remove(0);
        rejects(() -> BundledRules.ruleset(json)); // ALL_CARDS must explicitly be casual, never official.
        json.getAsJsonObject("edition").addProperty("availability", "PLAYABLE_SNAPSHOT");
        json.getAsJsonObject("edition").addProperty("casualOnly", true);
        check(BundledRules.ruleset(json).cardPool().mode() == CardPoolPolicy.Mode.ALL_CARDS, "合法自定义全卡池无法加载");
        json.remove("edition"); rejects(() -> BundledRules.ruleset(json));
    }
    private static void openTableAndBox(Map<String, Ruleset> rules, CardDatabase cards, CombatCatalog combat) {
        var boxes = new CardBoxService(Clock.systemUTC());
        var a = box(boxes, A); var b = box(boxes, B); var other = box(boxes, A);
        var editor = new BoxEditorSession(boxes, cards, combat, rules, Map.of(), A, a.id());
        var row = editor.view(A).rules().stream().filter(r -> r.id().equals("custom-open-all")).findFirst().orElseThrow();
        check(row.canStart() && row.statusZh().equals("仅休闲可用"), "卡盒没有区分开放可开局和赛事资格");
        var manager = new CardTableManager(rules, boxes, cards, combat, () -> new ServerRandom(11));
        var key = new CardTableKey("minecraft:overworld", 31999);
        manager.open(key, A, "custom-open-all"); manager.join(key, B);
        manager.selectBox(key, A, a.id()); manager.selectBox(key, B, b.id());
        manager.setReady(key, A, true); manager.setReady(key, B, true);
        var match = manager.start(key, A);
        check(match.ruleset().id().equals("custom-open-all"), "双人卡桌没有冻结开放规则包");
        rejects(() -> boxes.take(A, a.id(), a.contents().getFirst()));
        manager.endMatch(match.id());
        var practice = manager.startPractice(A, a.id(), other.id(), "custom-open-all");
        check(practice.match().ruleset().id().equals("custom-open-all"), "练习未复用同一规则包");
        manager.endMatch(practice.matchId());
        rejects(() -> manager.startPractice(A, a.id(), other.id(), "standard-cn-2025"));
        // Rejection must not leak locks onto the real card boxes.
        boxes.take(A, a.id(), a.contents().getFirst());
    }
    private static DeckBox box(CardBoxService boxes, UUID owner) {
        var box = boxes.create(owner, "规则包测试", "custom-open-all");
        for (int i = 0; i < 60; i++) boxes.put(owner, box.id(), boxes.mint(owner, i < 4 ? "fixture-basic-h" : "fixture-energy-h").instanceId());
        return box;
    }
    private static List<CardInstance> deck(UUID owner) {
        var result = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) result.add(new CardInstance(UUID.randomUUID(), owner, i < 4 ? "fixture-basic-h" : "fixture-energy-h"));
        return result;
    }
    private static CardDefinition card(String id, String region, String mark, EffectSupport support) {
        return new CardDefinition(id, "测试卡", CardCategory.POKEMON, TrainerCategory.NONE, mark, Set.of(region), true, false, 4,
            Map.of(), support, support == EffectSupport.UNSUPPORTED ? "未实现" : null);
    }
    private static boolean has(DeckValidationReport report, String code) { return report.issues().stream().anyMatch(i -> i.code().equals(code)); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("服务器接受了应被拒绝的规则／卡盒操作");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
