package dev.hua.ptcglive.compat.cobblemon;

import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.CardLabel;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Immutable server-side species/card pool. No Cobblemon classes are loaded here. */
public final class CobblemonRewardTable {
    private static final String INDEX = "/data/ptcglive/cobblemon-card-species.tsv";
    private final Map<String, List<CardCatalog.Entry>> cardsBySpecies;

    private CobblemonRewardTable(Map<String, List<CardCatalog.Entry>> cardsBySpecies) {
        this.cardsBySpecies = cardsBySpecies;
    }

    public static CobblemonRewardTable load(Collection<CardCatalog.Entry> catalog) {
        var byId = new LinkedHashMap<String, CardCatalog.Entry>();
        catalog.forEach(card -> byId.put(card.id(), card));
        var pools = new LinkedHashMap<String, List<CardCatalog.Entry>>();
        var stream = CobblemonRewardTable.class.getResourceAsStream(INDEX);
        if (stream == null) throw new IllegalStateException("缺少 Cobblemon 卡牌物种索引");
        try (stream; var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\\t", -1);
                if (fields.length != 2 || !fields[0].matches("[a-z0-9_-]+"))
                    throw new IllegalStateException("Cobblemon 卡牌物种索引记录无效：" + line);
                CardCatalog.Entry card = byId.get(fields[1]);
                if (card == null) throw new IllegalStateException("Cobblemon 奖励引用未注册卡牌：" + fields[1]);
                pools.computeIfAbsent(fields[0], ignored -> new ArrayList<>()).add(card);
            }
        } catch (java.io.IOException error) {
            throw new IllegalStateException("无法读取 Cobblemon 卡牌物种索引", error);
        }
        var immutable = new LinkedHashMap<String, List<CardCatalog.Entry>>();
        pools.forEach((species, cards) -> immutable.put(species, List.copyOf(cards)));
        return new CobblemonRewardTable(Map.copyOf(immutable));
    }

    /** One guaranteed card, with independent 40% and 15% rolls for cards two and three. */
    public List<CardCatalog.Entry> roll(String species, RandomGenerator random) {
        List<CardCatalog.Entry> pool = cardsBySpecies.get(species);
        if (pool == null || pool.isEmpty()) return List.of();
        int count = 1 + (random.nextDouble() < 0.40 ? 1 : 0) + (random.nextDouble() < 0.15 ? 1 : 0);
        var available = new ArrayList<>(pool);
        var result = new ArrayList<CardCatalog.Entry>(count);
        while (result.size() < count && !available.isEmpty()) {
            var tiers = new LinkedHashMap<Integer, List<CardCatalog.Entry>>();
            for (var card : available)
                tiers.computeIfAbsent(rarityWeight(card.rarityZh()), ignored -> new ArrayList<>()).add(card);
            int total = tiers.keySet().stream().mapToInt(Integer::intValue).sum();
            int choice = random.nextInt(total);
            List<CardCatalog.Entry> selectedTier = null;
            for (var tier : tiers.entrySet()) {
                choice -= tier.getKey();
                if (choice < 0) { selectedTier = tier.getValue(); break; }
            }
            CardCatalog.Entry selected = selectedTier.get(random.nextInt(selectedTier.size()));
            available.remove(selected);
            result.add(selected);
        }
        return List.copyOf(result);
    }

    /** Percentage weight by printed rarity. Available tiers total at most 100, so the floor is 1%. */
    public static int rarityWeight(String printedRarity) {
        return switch (CardLabel.rarityCode(printedRarity)) {
            case "C" -> 31;
            case "U" -> 22;
            case "R" -> 16;
            case "RR", "AR", "CHR", "A", "PR", "K" -> 11;
            case "RRR" -> 7;
            case "SR", "S", "ACE", "CLASSIC", "MA", "PROMO" -> 5;
            case "SAR", "SSR" -> 4;
            case "HR" -> 3;
            case "UR", "MUR" -> 1;
            default -> 1;
        };
    }

    public int speciesCount() { return cardsBySpecies.size(); }
    public int cardCount(String species) { return cardsBySpecies.getOrDefault(species, List.of()).size(); }
}
