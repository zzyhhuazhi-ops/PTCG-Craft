package dev.hua.ptcglive.rules;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/** Regional civil-year index. Selecting a dated entry never upgrades its independent playability audit status. */
public final class StandardRulesCalendar {
    public record Segment(String region, int year, LocalDate from, LocalDate until, String rulesetId) { }
    private final LocalDate verifiedThrough;
    private final List<Segment> segments;
    private final Map<String, Ruleset> rules;

    public StandardRulesCalendar(LocalDate verifiedThrough, List<Segment> segments, Map<String, Ruleset> rules) {
        this.verifiedThrough = Objects.requireNonNull(verifiedThrough);
        this.rules = Map.copyOf(rules);
        this.segments = segments.stream().sorted(Comparator.comparing(Segment::region).thenComparing(Segment::from)).toList();
        var grouped = new LinkedHashMap<String, List<Segment>>();
        for (var entry : this.segments) {
            var rule = rules.get(entry.rulesetId());
            if (rule == null || !rule.region().equals(entry.region()) || !rule.coversDate(entry.from()) || !rule.coversDate(entry.until()))
                throw new IllegalArgumentException("年份索引引用了缺失、跨地区或日期不符的规则包：" + entry.rulesetId());
            if (entry.from().isAfter(entry.until()) || entry.until().isAfter(verifiedThrough)
                || entry.from().getYear() != entry.year() || entry.until().getYear() != entry.year())
                throw new IllegalArgumentException("年份索引日期区间无效：" + entry.rulesetId());
            grouped.computeIfAbsent(entry.region() + "/" + entry.year(), ignored -> new ArrayList<>()).add(entry);
        }
        if (grouped.isEmpty()) throw new IllegalArgumentException("年份索引不能为空");
        for (var group : grouped.values()) {
            var next = LocalDate.of(group.getFirst().year(), 1, 1);
            for (var entry : group) {
                if (!entry.from().equals(next)) throw new IllegalArgumentException("年份规则区间重复或存在空缺：" + entry.rulesetId());
                next = entry.until().plusDays(1);
            }
            var yearEnd = LocalDate.of(group.getFirst().year(), 12, 31);
            var expectedEnd = yearEnd.isAfter(verifiedThrough) ? verifiedThrough : yearEnd;
            if (!next.equals(expectedEnd.plusDays(1))) throw new IllegalArgumentException("年份规则索引未覆盖至核验截止日");
        }
    }
    public LocalDate verifiedThrough() { return verifiedThrough; }
    public List<Segment> forYear(String region, int year) {
        return segments.stream().filter(s -> s.region().equals(region) && s.year() == year).toList();
    }
    public Optional<Ruleset> forDate(String region, LocalDate date) {
        Objects.requireNonNull(date);
        return segments.stream().filter(s -> s.region().equals(region) && !date.isBefore(s.from()) && !date.isAfter(s.until()))
            .findFirst().map(s -> rules.get(s.rulesetId()));
    }
    public static StandardRulesCalendar load(Map<String, Ruleset> rules) {
        var path = "/data/ptcglive/rulesets/calendar.json";
        try (var stream = StandardRulesCalendar.class.getResourceAsStream(path);
             var reader = new InputStreamReader(Objects.requireNonNull(stream, "缺少年份规则索引"), StandardCharsets.UTF_8)) {
            var data = JsonParser.parseReader(reader).getAsJsonObject();
            if (data.get("schemaVersion").getAsInt() != 1) throw new IllegalArgumentException("未知年份索引版本");
            var segments = new ArrayList<Segment>();
            for (var value : data.getAsJsonArray("segments")) {
                var row = value.getAsJsonObject();
                segments.add(new Segment(row.get("region").getAsString(), row.get("year").getAsInt(),
                    LocalDate.parse(row.get("from").getAsString()), LocalDate.parse(row.get("until").getAsString()), row.get("rulesetId").getAsString()));
            }
            return new StandardRulesCalendar(LocalDate.parse(data.get("verifiedThrough").getAsString()), segments, rules);
        } catch (java.io.IOException e) { throw new IllegalStateException("不能读取年份规则索引", e); }
    }
}
