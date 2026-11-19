import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.engine.field.EnergyType;
import java.time.LocalDate;
import java.util.*;

/** Calendar boundaries and regional Energy exclusions must not depend on the computer's current date. */
public final class StandardRulesCalendarTest {
    public static void main(String[] args) {
        var rules = BundledRules.loadRulesets();
        var calendar = StandardRulesCalendar.load(rules);
        for (String region : List.of("cn", "international")) for (int year : List.of(2024, 2025, 2026)) {
            var rows = calendar.forYear(region, year);
            check(!rows.isEmpty(), "缺少年份：" + region + year);
            var end = year == 2026 ? LocalDate.of(2026, 10, 9) : LocalDate.of(year, 12, 31);
            for (var day = LocalDate.of(year, 1, 1); !day.isAfter(end); day = day.plusDays(1)) {
                var rule = calendar.forDate(region, day).orElseThrow();
                check(rule.region().equals(region) && rule.coversDate(day), "日期或地区发生串包");
            }
        }
        marks(calendar, "international", "2024-04-04", "E", "F", "G");
        marks(calendar, "international", "2024-04-05", "F", "G", "H");
        marks(calendar, "international", "2025-04-10", "F", "G", "H");
        marks(calendar, "international", "2025-04-11", "G", "H", "I");
        marks(calendar, "international", "2026-04-09", "G", "H", "I");
        marks(calendar, "international", "2026-04-10", "H", "I", "J");
        marks(calendar, "cn", "2024-05-16", "C", "D", "E");
        marks(calendar, "cn", "2024-05-17", "C", "D", "E", "F");
        marks(calendar, "cn", "2024-06-18", "D", "E", "F");
        marks(calendar, "cn", "2025-01-16", "D", "E", "F");
        marks(calendar, "cn", "2025-01-17", "E", "F", "G");
        marks(calendar, "cn", "2026-01-15", "E", "F", "G");
        marks(calendar, "cn", "2026-01-16", "F", "G", "H");
        marks(calendar, "cn", "2026-07-16", "G", "H", "I");
        marks(calendar, "cn", "2026-09-16", "G", "H", "I", "J");
        check(calendar.forDate("jp", LocalDate.of(2025, 1, 1)).isEmpty(), "误加入日版");
        check(calendar.forDate("cn", LocalDate.of(2026, 10, 10)).isEmpty(), "把未核验的未来当作已审核规则");
        check(calendar.forDate("cn", LocalDate.of(2023, 12, 31)).isEmpty(), "超出本次年份范围");
        check(rule(calendar, "cn", "2025-01-16").trainerClassification().pokemonToolsAreItems(), "简中工具提前独立");
        check(!rule(calendar, "cn", "2025-01-17").trainerClassification().pokemonToolsAreItems(), "简中工具未独立");
        var energy = new CardDefinition("reviewed-energy", "基本能量", CardCategory.ENERGY, TrainerCategory.NONE,
            "NONE", Set.of("cn"), false, true, 60, Map.of(), EffectSupport.SUPPORTED, null);
        var before = rule(calendar, "cn", "2024-06-17");
        var after = rule(calendar, "cn", "2024-06-18");
        var nine = new CardPoolPolicy(CardPoolPolicy.Mode.REGULATION_MARKS, Set.of(), before.effectiveDate(),
            Map.of(energy.id(), before.effectiveDate()), before.cardPool().basicEnergyTypes());
        var eight = new CardPoolPolicy(CardPoolPolicy.Mode.REGULATION_MARKS, Set.of(energy.id()), after.effectiveDate(),
            Map.of(energy.id(), after.effectiveDate()), after.cardPool().basicEnergyTypes());
        check(nine.assess(before, energy, EnergyType.FAIRY).status() == CardPoolPolicy.Status.LEGAL, "当时合法妖精能量被拒绝");
        check(eight.assess(after, energy, EnergyType.FAIRY).status() == CardPoolPolicy.Status.ILLEGAL, "白名单绕过妖精退出");
        check(eight.assess(after, energy, EnergyType.GRASS).status() == CardPoolPolicy.Status.LEGAL, "基本草能量需要规则标记");
        check(eight.assess(after, energy).status() == CardPoolPolicy.Status.NEEDS_DATA, "缺少能量属性时猜测合法");
        check(after.cardPool().assess(after, energy, EnergyType.GRASS).status() == CardPoolPolicy.Status.NEEDS_DATA, "基本能量绕过印刷日期审核");
        var foreign = new CardDefinition("reviewed-energy", "基本能量", CardCategory.ENERGY, TrainerCategory.NONE,
            "NONE", Set.of("international"), false, true, 60, Map.of(), EffectSupport.SUPPORTED, null);
        check(eight.assess(after, foreign, EnergyType.GRASS).status() == CardPoolPolicy.Status.ILLEGAL, "基本能量绕过地区检查");
        rejects(() -> new CardPoolPolicy(CardPoolPolicy.Mode.REGULATION_MARKS, Set.of(), null, Map.of(), Set.of(EnergyType.DRAGON)));
        rejects(() -> new CardPoolPolicy(CardPoolPolicy.Mode.ALL_CARDS, Set.of(), null, Map.of(), Set.of(EnergyType.GRASS)));
        var rows = calendar.forYear("cn", 2024);
        rejects(() -> new StandardRulesCalendar(calendar.verifiedThrough(), List.of(rows.getFirst(), rows.getFirst()), rules));
        var missing = new HashMap<>(rules); missing.remove(rows.getFirst().rulesetId());
        rejects(() -> new StandardRulesCalendar(calendar.verifiedThrough(), rows, missing));
        System.out.println("StandardRulesCalendarTest passed: six regional years, 2024-2026 day coverage, rotation boundaries, Tool classification, Energy and fail-closed legality");
    }
    private static Ruleset rule(StandardRulesCalendar c, String region, String date) { return c.forDate(region, LocalDate.parse(date)).orElseThrow(); }
    private static void marks(StandardRulesCalendar c, String region, String date, String... marks) {
        check(rule(c, region, date).legalRegulationMarks().equals(Set.of(marks)), "错误轮替：" + region + date);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void rejects(Runnable r) { try { r.run(); } catch (IllegalArgumentException e) { return; } throw new AssertionError("应拒绝无效规则数据"); }
}
