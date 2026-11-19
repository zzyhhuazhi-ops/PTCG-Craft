package dev.hua.ptcglive;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable, stable card IDs shared by the dedicated server and client. */
public final class CardCatalog {
    /**
     * The packaged index keeps display facts separate from the formatted item
     * name so collection, encyclopedia and pack screens can show the printed
     * Chinese name, collector number and rarity without reparsing a tooltip.
     */
    public record Entry(String id, String set, String era, String label,
                        String nameZh, String printedNumber, String rarityZh) {
        public String regulationMark() { return dev.hua.ptcglive.rules.PrintedRegulationMarks.mark(id); }
    }
    public static final Set<String> ERAS = Set.of("bw", "xy", "sm", "swsh", "sv", "me", "other");

    private CardCatalog() { }

    public static List<Entry> load() {
        var entries = new ArrayList<>(loadBase());
        var ids = new HashSet<String>(); entries.forEach(entry -> ids.add(entry.id()));
        for (String resource : List.of("/ptcg-energy-printings.tsv", "/ptcg-sc-exclusive.tsv", "/ptcg-cn-supporters.tsv")) {
            var stream = CardCatalog.class.getResourceAsStream(resource);
            if (stream == null) throw new IllegalStateException("缺少卡牌印刷版本目录：" + resource);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                for (var entry : read(reader)) {
                    if (!ids.add(entry.id())) throw new IllegalStateException("重复印刷 ID：" + entry.id());
                    if (!CardExclusions.contains(entry.id())) entries.add(entry);
                }
            } catch (IOException error) { throw new IllegalStateException("无法读取卡牌印刷版本目录：" + resource, error); }
        }
        return List.copyOf(entries);
    }

    /** Original imported printings; expansion packs keep their reviewed collation pools. */
    public static List<Entry> loadBase() {
        List<Entry> entries = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (String resource : List.of("/ptcg-cards.tsv", "/ptcg-starter-deck-100.tsv", "/ptcg-mega-completion.tsv")) {
            var stream = CardCatalog.class.getResourceAsStream(resource);
            if (stream == null) throw new IllegalStateException("Missing packaged card catalog: " + resource);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                for (Entry entry : read(reader)) {
                    if (!ids.add(entry.id())) throw new IllegalStateException("Duplicate packaged card ID: " + entry.id());
                    if (!CardExclusions.contains(entry.id())) entries.add(entry);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot load card catalog: " + resource, e);
            }
        }
        return List.copyOf(entries);
    }

    public static List<Entry> read(Reader reader) throws IOException {
        List<Entry> entries = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        var buffered = new BufferedReader(reader);
        String line;
        while ((line = buffered.readLine()) != null) {
            String[] fields = line.split("\t", -1);
            if (fields.length != 4 || !fields[0].matches("[a-z0-9_-]+")
                || !fields[1].matches("[a-z0-9_-]+") || !ERAS.contains(fields[2])
                || fields[3].isBlank() || !ids.add(fields[0])) {
                throw new IOException("Invalid or duplicate card at line " + (entries.size() + 1));
            }
            String reviewed = CardNameReviews.apply(fields[0], CardNameLocalization.label(fields[0], fields[3]));
            DisplayFields display = parseDisplayFields(reviewed);
            String rarity=CardRarity.printed(fields[0],display.rarityZh());
            entries.add(new Entry(fields[0], fields[1], fields[2], CardLabel.format(display.nameZh()+" · "+display.printedNumber()+" · "+rarity),
                display.nameZh(), display.printedNumber(), rarity));
        }
        if (entries.isEmpty()) throw new IOException("Empty card catalog");
        return List.copyOf(entries);
    }

    /**
     * The main imported catalogue uses {@code 名称 · 系列 编号 · 稀有度}.
     * Starter Deck 100 predates that importer and deliberately retains its
     * printed {@code 名称 稀有度 系列 编号} format.  Both formats are immutable
     * source data, so the UI normalises them here instead of mutating IDs or
     * tooltip labels.
     */
    private static DisplayFields parseDisplayFields(String value) throws IOException {
        String[] dotted = value.split(" · ", -1);
        if (dotted.length == 3 && !dotted[0].isBlank() && !dotted[1].isBlank() && !dotted[2].isBlank()) {
            return new DisplayFields(dotted[0], dotted[1], dotted[2]);
        }

        String[] words = value.trim().split("\\s+");
        if (words.length >= 4) {
            String name = String.join(" ", java.util.Arrays.copyOfRange(words, 0, words.length - 3));
            String rarity = words[words.length - 3];
            String number = words[words.length - 2] + " " + words[words.length - 1];
            if (!name.isBlank() && !rarity.isBlank() && !number.isBlank()) {
                return new DisplayFields(name, number, rarity);
            }
        }
        throw new IOException("卡牌图鉴显示字段无效: " + value);
    }

    private record DisplayFields(String nameZh, String printedNumber, String rarityZh) { }
}
