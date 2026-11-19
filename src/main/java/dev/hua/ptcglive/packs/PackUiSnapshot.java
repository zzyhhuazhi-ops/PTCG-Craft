package dev.hua.ptcglive.packs;

import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.rules.CardDatabase;
import dev.hua.ptcglive.rules.CardDefinition;
import dev.hua.ptcglive.rules.TrainerCategory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Owner-only packet model for a pack opening screen. Every displayed chance
 * is calculated from the exact immutable weights later consumed by
 * {@link PackOpeningService}; there is no client-side odds table.
 */
public record PackUiSnapshot(String sessionId, Phase phase, String setId, String packNameZh,
                             String dataVersion, String descriptionZh, List<SlotRow> slots,
                             List<CardRow> cards, List<CardRow> results, PackInfo info, PackDestination destination) {
    public PackUiSnapshot(String sessionId, Phase phase, String setId, String packNameZh, String dataVersion,
                          String descriptionZh, List<SlotRow> slots, List<CardRow> cards, List<CardRow> results, PackInfo info) {
        this(sessionId,phase,setId,packNameZh,dataVersion,descriptionZh,slots,cards,results,info,PackDestination.INVENTORY);
    }
    public PackUiSnapshot withDestination(PackDestination value) {
        return new PackUiSnapshot(sessionId,phase,setId,packNameZh,dataVersion,descriptionZh,slots,cards,results,info,value);
    }
    public enum Phase { PREVIEW, RESULTS, BROWSE }
    public PackUiSnapshot browseOnly() {
        return new PackUiSnapshot(sessionId,Phase.BROWSE,setId,packNameZh,dataVersion,descriptionZh,slots,cards,List.of(),info,destination);
    }
    public record SlotRow(String positionsZh, String slotNameZh, int count, int totalWeight, String explanationZh) { }
    public record ChanceRow(String positionsZh, double probability) { }
    public record CardRow(String definitionId, String nameZh, String printedNumber, String rarityZh,
                          String typeZh, List<ChanceRow> chances) { }
    /**
     * Display-only identity of the 美版 booster pack.  {@code artTexture} is the
     * full texture path of the held stack's pack art variant (empty for test packs).
     */
    public record PackInfo(String chineseName, String englishName, String setCode, String eraZh, String releaseDate,
                           String artTexture, String artLabel, int poolSize, int cardsPerPack, String oddsNoteZh, boolean custom) { }

    public PackUiSnapshot {
        Objects.requireNonNull(sessionId, "抽卡会话"); Objects.requireNonNull(phase, "抽卡阶段");
        Objects.requireNonNull(setId, "setId"); Objects.requireNonNull(packNameZh, "卡包名");
        Objects.requireNonNull(dataVersion, "概率表版本"); Objects.requireNonNull(descriptionZh, "卡包说明");
        slots = List.copyOf(slots); cards = List.copyOf(cards); results = List.copyOf(results);
        Objects.requireNonNull(info, "卡包信息");
        if(destination==null)destination=PackDestination.INVENTORY;
    }

    public static PackUiSnapshot preview(UUID sessionId, SetPackDefinition pack, List<CardCatalog.Entry> catalog,
                                         CardDatabase definitions) {
        return preview(sessionId, pack, catalog, definitions, 0);
    }
    /** @param customModelData art variant of the held stack (0 = the pack's default art). */
    public static PackUiSnapshot preview(UUID sessionId, SetPackDefinition pack, List<CardCatalog.Entry> catalog,
                                         CardDatabase definitions, int customModelData) {
        Objects.requireNonNull(sessionId, "会话 ID"); Objects.requireNonNull(pack, "卡包");
        var byId = new LinkedHashMap<String, CardCatalog.Entry>();
        for (var entry : catalog) byId.put(entry.id(), entry);
        var slots = new ArrayList<SlotRow>();
        int nextPosition = 1;
        for (var slot : pack.slots()) {
            int end = nextPosition + slot.count() - 1;
            String positions = nextPosition == end ? "第 " + nextPosition + " 张" : "第 " + nextPosition + "～" + end + " 张";
            int total = pack.pools().get(slot.poolId()).values().stream().mapToInt(Integer::intValue).sum();
            slots.add(new SlotRow(positions, slot.displayNameZh(), slot.count(), total,
                positions + " 从“" + slot.displayNameZh() + "”按下列权重独立抽取。"));
            nextPosition = end + 1;
        }
        var rows = new ArrayList<CardRow>();
        for (String id : pack.pools().values().stream().flatMap(pool -> pool.keySet().stream()).distinct().toList()) {
            CardCatalog.Entry entry = byId.get(id);
            if (entry == null) throw new IllegalStateException("卡包引用了未注册卡牌：" + id);
            var chances = new ArrayList<ChanceRow>();
            int position = 1;
            for (var slot : pack.slots()) {
                Map<String, Integer> pool = pack.pools().get(slot.poolId());
                Integer weight = pool.get(id);
                if (weight != null) {
                    int total = pool.values().stream().mapToInt(Integer::intValue).sum();
                    int end = position + slot.count() - 1;
                    String positions = position == end ? "第 " + position + " 张" : "第 " + position + "～" + end + " 张";
                    chances.add(new ChanceRow(positions, (double) weight / total));
                }
                position += slot.count();
            }
            rows.add(row(entry, definitions.find(id).orElse(null), chances));
        }
        rows.sort(Comparator.comparing(CardRow::printedNumber).thenComparing(CardRow::definitionId));
        String publication = "发行分类：" + pack.publication().catalogRoute().labelZh()
            + (pack.publication().englishSetName().isBlank() ? "" : " · 美版扩充包：" + pack.publication().englishSetName())
            + (pack.publication().englishReleaseDate().isBlank() ? "" : " · 美版发售日：" + pack.publication().englishReleaseDate());
        int cardsPerPack = pack.cardsPerPack();
        String odds = "每包固定 " + cardsPerPack + " 张；官方开出概率未公开，本页概率是模组自定义配置（服务器按同一组权重结算），不等同于实体卡包或 PTCG Live 发卡。";
        if (pack.custom()) odds = "每包 " + cardsPerPack + " 张；所有收录印刷版本独立等概率抽取，允许重复。PTCG Craft 自定义玩法；显示概率与服务器实际权重相同。";
        var identity = pack.identity();
        var variant = identity.variant(customModelData);
        String art = variant == null ? "" : "ptcglive:textures/item/booster_packs/" + pack.setId() + "/" + variant.key() + ".png";
        var info = new PackInfo(identity.chineseName(), identity.officialEnglishName(), identity.setCode(), identity.eraChineseName(),
            identity.releaseDate(), art, variant == null ? "" : variant.label(), rows.size(), cardsPerPack, odds, pack.custom());
        return new PackUiSnapshot(sessionId.toString(), Phase.PREVIEW, pack.setId(), pack.displayNameZh(), pack.dataVersion(),
            odds + "\n" + publication + "\n" + pack.publication().noticeZh(), slots, rows, List.of(), info);
    }

    public PackUiSnapshot withResults(List<String> definitionIds, List<CardCatalog.Entry> catalog, CardDatabase definitions) {
        return withBatchResults(definitionIds, 1, catalog, definitions);
    }
    public PackUiSnapshot withBatchResults(List<String> definitionIds, int packCount, List<CardCatalog.Entry> catalog, CardDatabase definitions) {
        PackBatchPolicy.validateCount(packCount);
        var byId = new LinkedHashMap<String, CardCatalog.Entry>();
        for (var entry : catalog) byId.put(entry.id(), entry);
        var result = new ArrayList<CardRow>();
        for (String id : definitionIds) {
            CardCatalog.Entry entry = byId.get(id);
            if (entry == null) throw new IllegalStateException("服务器抽到了未注册卡牌：" + id);
            result.add(row(entry, definitions.find(id).orElse(null), List.of()));
        }
        if (result.size() != Math.multiplyExact(info.cardsPerPack(), packCount)) throw new IllegalArgumentException("整批开包结果张数不匹配");
        return new PackUiSnapshot(sessionId, Phase.RESULTS, setId, packNameZh, dataVersion, descriptionZh, slots, List.of(), result, info, destination);
    }

    private static CardRow row(CardCatalog.Entry entry, CardDefinition definition, List<ChanceRow> chances) {
        return new CardRow(entry.id(), entry.nameZh(), entry.printedNumber(), entry.rarityZh(), typeZh(definition), chances);
    }
    private static String typeZh(CardDefinition definition) {
        if (definition == null) return "图鉴类型待导入";
        return switch (definition.category()) {
            case POKEMON -> definition.basicPokemon() ? "基础宝可梦" : "进化宝可梦";
            case ENERGY -> definition.basicEnergy() ? "基本能量" : "特殊能量";
            case TRAINER -> switch (definition.trainerCategory()) {
                case ITEM -> "道具"; case SUPPORTER -> "支援者"; case STADIUM -> "场地"; case TOOL -> "宝可梦道具";
                case NONE -> "训练家";
            };
        };
    }
    public static String percent(double probability) {
        return String.format(Locale.ROOT, "%.4f%%", probability * 100.0d);
    }
}
