package dev.hua.ptcglive.collection;

import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/** Recovers lost row separators only when the complete 60-card interpretation is unique. */
final class CollapsedLiveDeckParser {
    private static final Pattern HEADER = Pattern.compile("(?iu)(Pokémon|Pokemon|Trainer|Energy)\\s*[:：]\\s*");
    private static final Pattern COUNT = Pattern.compile("^(\\d{1,2})\\s+(.+)$");
    private static final Pattern ROW = Pattern.compile("^(.+?)\\s+((?:" + String.join("|", LiveDeckParser.supportedCodes().stream().map(Pattern::quote).toList())
        + ")(?:-(?:TG|GG|RC|CC))?)\\s+([A-Za-z]*\\d+[A-Za-z]?(?:/\\d+)?)(?:\\s+PH)?(?=\\s|$)", Pattern.CASE_INSENSITIVE);
    private final List<String> sections = new ArrayList<>();
    private final List<Map<String, Integer>> solutions = new ArrayList<>();
    private final BiFunction<String, String, String> resolve;
    private int attempts;
    private boolean exhausted;

    private CollapsedLiveDeckParser(BiFunction<String, String, String> resolve) { this.resolve = resolve; }

    static Map<String, Integer> recover(String text, BiFunction<String, String, String> resolve) {
        var parser = new CollapsedLiveDeckParser(resolve);
        text = text.replaceAll("(?iu)Total Cards\\s*[:：]\\s*60\\s*$", "").strip();
        var headers = HEADER.matcher(text);
        var starts = new ArrayList<Integer>(); var ends = new ArrayList<Integer>(); var names = new ArrayList<String>();
        while (headers.find()) { starts.add(headers.start()); ends.add(headers.end()); names.add(headers.group(1).toLowerCase(Locale.ROOT)); }
        if (starts.size() != 3 || starts.getFirst() != 0 || !names.get(0).matches("pok[eé]mon")
            || !names.get(1).equals("trainer") || !names.get(2).equals("energy")) return null;
        for (int i = 0; i < starts.size(); i++) parser.sections.add(text.substring(ends.get(i), i + 1 < starts.size() ? starts.get(i + 1) : text.length()).strip().replaceAll("\\s+", " "));
        parser.section(0, 0, new LinkedHashMap<>());
        if (parser.exhausted || parser.solutions.size() > 1)
            throw new IllegalArgumentException("卡表换行丢失，无法唯一确定卡号与张数；请重新复制保留换行的原始卡表");
        return parser.solutions.size() == 1 ? parser.solutions.getFirst() : null;
    }

    private void section(int index, int total, Map<String, Integer> plan) {
        if (solutions.size() > 1 || exhausted) return;
        if (index == sections.size()) {
            if (total == 60 && solutions.stream().noneMatch(plan::equals)) solutions.add(Collections.unmodifiableMap(new LinkedHashMap<>(plan)));
            return;
        }
        String text = sections.get(index);
        var leading = Pattern.compile("^(\\d+)\\s*(.*)$").matcher(text);
        if (!leading.matches()) return;
        String digits = leading.group(1), rest = leading.group(2);
        if (rest.isEmpty() && digits.equals("0")) { section(index + 1, total, plan); return; }
        // A retained space/newline separates the section total from the first row count.
        if (COUNT.matcher(rest).matches()) { rows(index, rest, total, plan); return; }
        for (int length = 1; length <= 2 && length < digits.length(); length++) {
            String heading = digits.substring(0, digits.length() - length);
            if (heading.length() > 2 || Integer.parseInt(heading) > 60) continue;
            rows(index, digits.substring(digits.length() - length) + " " + rest, total, plan);
        }
    }

    private void rows(int section, String text, int total, Map<String, Integer> plan) {
        if (solutions.size() > 1 || exhausted) return;
        if (++attempts > 10000) { exhausted = true; return; }
        var countMatch = COUNT.matcher(text);
        if (!countMatch.matches()) return;
        int count = Integer.parseInt(countMatch.group(1));
        if (count < 1 || total + count > 60) return;
        var row = ROW.matcher(countMatch.group(2));
        if (!row.find()) return;
        String code = row.group(2), number = row.group(3);
        String rest = countMatch.group(2).substring(row.end()).strip();
        if (rest.isEmpty() || COUNT.matcher(rest).matches()) {
            add(section, row.group(1), code, number, count, rest, total, plan);
        } else if (number.matches("[A-Za-z]*\\d+")) {
            // PRE 714 Drakloak may mean PRE 71 + 4 copies, or PRE 7 + 14 copies.
            for (int length = 1; length <= 2 && length < number.length(); length++) {
                String cardNumber = number.substring(0, number.length() - length);
                if (!cardNumber.matches("[A-Za-z]*\\d+")) continue;
                add(section, row.group(1), code, cardNumber, count, number.substring(number.length() - length) + " " + rest, total, plan);
            }
        }
    }

    private void add(int section, String name, String code, String number, int count, String rest, int total, Map<String, Integer> plan) {
        String id;
        try { id = resolve.apply(code, number); } catch (IllegalArgumentException ignored) { return; }
        var facts = CollectionSearch.allFacts().get(id);
        if (facts == null || !normalizeName(name).equals(normalizeName(facts.englishName()))) return;
        var next = new LinkedHashMap<>(plan); next.merge(id, count, Integer::sum);
        if (rest.isEmpty()) section(section + 1, total + count, next);
        else rows(section, rest, total + count, next);
    }
    private static String normalizeName(String name) {
        String value = name.toLowerCase(Locale.ROOT).replaceFirst("^basic\\s+", "");
        for (var entry : Map.of("g", "grass", "r", "fire", "w", "water", "l", "lightning", "p", "psychic", "f", "fighting", "d", "darkness", "m", "metal", "y", "fairy").entrySet())
            value = value.replace("{" + entry.getKey() + "}", entry.getValue());
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKD).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
