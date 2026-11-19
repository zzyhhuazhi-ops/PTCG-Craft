package dev.hua.ptcglive.rules;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Printed-card facts shared by collection UI and authoritative deck validation.
 * An empty entry is not a printed letter and never implies combat support. */
public final class PrintedRegulationMarks {
    private static final Map<String, String> MARKS = load();
    private PrintedRegulationMarks() { }
    public static Optional<String> find(String cardId) { return Optional.ofNullable(MARKS.get(cardId)); }
    public static String mark(String cardId) { return MARKS.getOrDefault(cardId, ""); }
    public static Map<String, String> all() { return MARKS; }
    public static String labelZh(String cardId) {
        String mark = mark(cardId);
        return "赛制标记：" + (mark.isEmpty() ? "无已核对字母" : mark);
    }
    public static String searchTerm(String cardId) {
        String mark = mark(cardId);
        return "标记:" + (mark.isEmpty() ? "无" : mark);
    }
    /** Preserve unregistered engine fixtures, but never borrow their marks for collectible IDs. */
    public static String definitionMark(String cardId, String fallback) {
        return find(cardId).map(mark -> mark.isEmpty() ? "NONE" : mark).orElse(fallback);
    }
    private static Map<String, String> load() {
        var stream = PrintedRegulationMarks.class.getResourceAsStream("/data/ptcglive/printed-regulation-marks.tsv");
        if (stream == null) throw new IllegalStateException("缺少卡面赛制标记索引");
        var result = new LinkedHashMap<String, String>();
        try (stream; var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\t", -1);
                if (fields.length != 2 || !fields[0].matches("[a-z0-9_-]+")
                        || !fields[1].matches("[A-J]?") || result.putIfAbsent(fields[0], fields[1]) != null)
                    throw new IllegalStateException("卡面赛制标记索引含无效或重复记录");
            }
        } catch (java.io.IOException failure) { throw new IllegalStateException("无法读取卡面赛制标记索引", failure); }
        if (result.isEmpty()) throw new IllegalStateException("卡面赛制标记索引为空");
        return Map.copyOf(result);
    }
}
