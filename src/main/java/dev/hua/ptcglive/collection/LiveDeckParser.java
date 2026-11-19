package dev.hua.ptcglive.collection;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Resolves the printed set/number, never a fuzzy card-name match. */
public final class LiveDeckParser {
    public static final int MAX_TEXT = 16384;
    private static final Pattern CARD = Pattern.compile("^(\\d{1,2})\\s+(.+?)\\s+([A-Za-z0-9-]+)\\s+([A-Za-z]*\\d+[A-Za-z]?(?:/\\d+)?)(?:\\s+PH)?$");
    private static final Map<String, List<String>> SETS = loadSets();
    static Set<String> supportedCodes() { return SETS.keySet(); }
    private LiveDeckParser() { }

    /** Uses exact English printings so copying and importing preserve card versions. */
    public static String export(Map<String, Integer> counts) {
        Map<String, String> codes = new HashMap<>();
        SETS.forEach((code, sets) -> sets.forEach(set -> {
            if (code.startsWith("PR-") || !codes.containsKey(set)) codes.put(set, code);
        }));
        Map<String, List<String>> sections = new LinkedHashMap<>();
        sections.put("POKEMON", new ArrayList<>());
        sections.put("TRAINER", new ArrayList<>());
        sections.put("ENERGY", new ArrayList<>());
        int total = 0;
        for (var entry : counts.entrySet()) {
            String id = entry.getKey();
            var printing = Pattern.compile("^(.+)_en_(\\d+)([a-z]?)$").matcher(id);
            var facts = CollectionSearch.allFacts().get(id);
            if (!printing.matches() || !codes.containsKey(printing.group(1)) || facts == null
                || facts.englishName().isBlank() || !sections.containsKey(facts.category()))
                throw new IllegalArgumentException("无法导出 Live 格式：" + id + " 缺少对应的英文版本信息");
            if (entry.getValue() < 1) throw new IllegalArgumentException("卡表数量无效");
            String prefix = switch (printing.group(1)) {
                case "bw11r", "xy9-5r" -> "RC";
                case "swsh7-5r" -> "CC";
                case "swsh9a", "swsh10a", "swsh11a", "swsh12a" -> "TG";
                case "swsh12-5a" -> "GG";
                default -> "";
            };
            sections.get(facts.category()).add(entry.getValue() + " " + facts.englishName() + " "
                + codes.get(printing.group(1)) + " " + prefix + Integer.parseInt(printing.group(2)) + printing.group(3));
            total += entry.getValue();
        }
        StringBuilder text = new StringBuilder();
        for (var section : sections.entrySet()) {
            String heading = switch (section.getKey()) { case "POKEMON" -> "Pokémon"; case "TRAINER" -> "Trainer"; default -> "Energy"; };
            text.append(heading).append(": ").append(section.getValue().size()).append('\n');
            section.getValue().forEach(line -> text.append(line).append('\n'));
            text.append('\n');
        }
        return text.append("Total Cards: ").append(total).toString();
    }

    public static Map<String, Integer> parse(String text, Set<String> availableIds) {
        if (text == null || text.isBlank() || text.length() > MAX_TEXT)
            throw new IllegalArgumentException("请复制完整的 Live 卡表（最多 16384 字符）");
        text = text.replace('\u00a0', ' ').replace("\uFEFF", "")
            .replaceAll("(?m)^[\\t ]*\\|[\\t :|\\-]*\\|[\\t ]*$", "").strip();
        try { return parseLines(text, availableIds); }
        catch (IllegalArgumentException original) {
            var recovered = CollapsedLiveDeckParser.recover(text, (code, number) ->
                parseLines("60 Card " + code + " " + number, availableIds).keySet().iterator().next());
            if (recovered != null) return recovered;
            throw original;
        }
    }
    private static Map<String, Integer> parseLines(String text, Set<String> availableIds) {
        Map<String, Integer> result = new LinkedHashMap<>();
        int total = 0, lineNumber = 0;
        for (String raw : text.replace('\u00a0', ' ').replace("\uFEFF", "").split("\\R")) {
            lineNumber++;
            String line = raw.strip();
            if (line.length() > 240) throw new IllegalArgumentException("第 " + lineNumber + " 行过长，请仅粘贴卡组文本");
            if (line.isEmpty() || line.startsWith("******") || line.matches("(?iu)^(Pokémon|Pokemon|Trainer|Energy|宝可梦|寶可夢|训练家|訓練家|能量)\\s*[:：]\\s*\\d+$")
                || line.matches("(?iu)^(Total Cards|Total|合计|總計)\\s*[:：]\\s*60$")) continue;
            var match = CARD.matcher(line);
            if (!match.matches()) throw new IllegalArgumentException("第 " + lineNumber + " 行无法识别：" + line);
            int count = Integer.parseInt(match.group(1));
            if (count < 1 || count > 60 || total + count > 60) throw new IllegalArgumentException("卡表数量无效或超过 60 张：第 " + lineNumber + " 行");
            String code = match.group(3).toUpperCase(Locale.ROOT);
            String rawNumber = match.group(4).split("/")[0];
            // Live also writes gallery identifiers as BRS-TG 3 / CRZ-GG 1.
            var gallery = Pattern.compile("^(.+)-(TG|GG|RC|CC)$").matcher(code);
            if (gallery.matches()) { code = gallery.group(1); rawNumber = gallery.group(2) + rawNumber; }
            String number = normalizeNumber(rawNumber);
            List<String> candidates = new ArrayList<>();
            for (String set : SETS.getOrDefault(code, List.of())) {
                String prefix = switch (set) {
                    case "bw11r", "xy9-5r" -> "rc";
                    case "swsh7-5r" -> "cc";
                    case "swsh9a", "swsh10a", "swsh11a", "swsh12a" -> "tg";
                    case "swsh12-5a" -> "gg";
                    default -> "";
                };
                String suppliedPrefix = number.replaceAll("[0-9].*$", "");
                if (!prefix.equals(suppliedPrefix)) continue;
                String suffix = number.substring(prefix.length());
                String id = set + "_en_" + suffix;
                if (availableIds.contains(id)) candidates.add(id);
            }
            if (candidates.size() != 1) throw new IllegalArgumentException("无法唯一匹配 " + code + " " + match.group(4) + "（" + match.group(2) + "）；请核对编号或更新卡库，卡盒未改动");
            result.merge(candidates.getFirst(), count, Integer::sum); total += count;
        }
        if (total != 60) throw new IllegalArgumentException("卡表实际为 " + total + " 张，需要完整的 60 张卡表");
        return Collections.unmodifiableMap(result);
    }
    private static String normalizeNumber(String number) {
        var match = Pattern.compile("([A-Za-z]*)(\\d+)([A-Za-z]?)").matcher(number);
        if (!match.matches()) throw new IllegalArgumentException("无效卡号：" + number);
        return match.group(1).toLowerCase(Locale.ROOT) + String.format(Locale.ROOT, "%03d", Integer.parseInt(match.group(2))) + match.group(3).toLowerCase(Locale.ROOT);
    }
    private static Map<String, List<String>> loadSets() {
        Map<String, List<String>> sets = new LinkedHashMap<>();
        try (var stream = LiveDeckParser.class.getResourceAsStream("/ptcg-live-sets.tsv")) {
            if (stream == null) throw new IllegalStateException("缺少 Live 系列映射");
            for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                String[] fields = line.split("\\t");
                sets.computeIfAbsent(fields[0], key -> new ArrayList<>()).add(fields[1]);
            }
        } catch (IOException failure) { throw new IllegalStateException("无法读取 Live 系列映射", failure); }
        return sets;
    }
}
