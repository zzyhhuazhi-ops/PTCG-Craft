package dev.hua.ptcglive.packs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.CardDatabase;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Cross-set acquisition pools reference existing printings; they never define gameplay rules. */
public final class CustomPackCatalog {
    public static final String RESOURCE = "/data/ptcglive/packs/custom-packs.json";
    private CustomPackCatalog() { }
    public static Map<String, SetPackDefinition> loadBundled(List<CardCatalog.Entry> cards, CardDatabase definitions) {
        var config = config();
        var energy = energyPack(config, cards, definitions);
        var exclusive = exclusivePack(config, cards);
        var supporters = supporterPack(cards, definitions);
        var result = new LinkedHashMap<String, SetPackDefinition>();
        for (var pack : List.of(energy, exclusive, supporters)) result.put(pack.setId(), pack);
        for (String era : List.of("sm", "swsh", "sv")) {
            var pack = supporterPack(cards, definitions, era);
            result.put(pack.setId(), pack);
        }
        var mega = megaSupporterPack(cards, definitions);
        result.put(mega.setId(), mega);
        for (var element : config.getAsJsonArray("collectionSets")) {
            var row = element.getAsJsonObject();
            var pack = collectionPack(cards, row.get("setId").getAsString(), row.get("nameZh").getAsString(),
                row.get("era").getAsString(), row.get("eraZh").getAsString());
            result.put(pack.setId(), pack);
        }
        return Map.copyOf(result);
    }
    private static SetPackDefinition collectionPack(List<CardCatalog.Entry> cards, String source, String title, String era, String eraZh) {
        var weights = new TreeMap<String, Integer>();
        for (var card : cards) if (source.equals(card.set())) weights.put(card.id(), 1);
        if (weights.isEmpty()) throw new IllegalStateException("收藏卡包没有已注册卡牌：" + source);
        var identity = new BoosterSetIdentity(source.toUpperCase(java.util.Locale.ROOT), title, title, title, title,
            "", era, eraZh, eraZh, List.of(source), weights.size(), null, "collection_equal_printing",
            List.of(new BoosterSetIdentity.ArtVariant("main", "美版借用封面", 1)));
        return new SetPackDefinition("collection_" + source, title, "collection-20261030:" + fingerprint(String.join("\n", weights.keySet())),
            Map.of("collection", weights), List.of(new PackSlot("collection", SetPackDefinition.CARDS_PER_PACK, "各印刷版本等概率")),
            new PackPublication(PackPublication.CatalogRoute.MOD_CUSTOM, "", "", "", "BORROWED_ENGLISH_PACK_ART", "",
                "模组自定义收藏卡包，不是官方补充包。封面借用美版实体补充包，仅作主题展示，不代表本包卡池。收录本模组已有的对应宣传卡、重印卡或起始卡组印刷；复用原卡牌 ID。每包 5 张，各印刷版本独立等概率，允许重复；售价 20 宝可币。对战资格仍由所选赛制和现有规则支持决定。"), identity);
    }
    public static JsonObject config() {
        var stream = CustomPackCatalog.class.getResourceAsStream(RESOURCE);
        if (stream == null) throw new IllegalStateException("缺少自定义卡包配置");
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var config = JsonParser.parseReader(reader).getAsJsonObject();
            if (config.get("schemaVersion").getAsInt() != 1) throw new IllegalArgumentException("不支持的自定义卡包配置版本");
            return config;
        } catch (java.io.IOException error) { throw new IllegalStateException("无法读取自定义卡包配置", error); }
    }
    public static SetPackDefinition energyPack(JsonObject config, List<CardCatalog.Entry> cards, CardDatabase definitions) {
        int count = config.get("energy_pack_card_count").getAsInt();
        if (count < 1 || count > 36) throw new IllegalArgumentException("全能量卡包张数必须为 1～36");
        var weights = new TreeMap<String, Integer>(); var sources = new TreeSet<String>();
        var seen = new HashSet<String>();
        for (var card : cards) {
            if (!seen.add(card.id())) throw new IllegalArgumentException("重复的收藏卡牌 ID：" + card.id());
            if (definitions.find(card.id()).map(d -> d.category() == CardCategory.ENERGY).orElse(false)) {
                weights.put(card.id(), 1); sources.add(card.set());
            }
        }
        if (weights.isEmpty()) throw new IllegalStateException("全能量卡包没有可抽取的能量卡");
        String version = config.get("dataVersion").getAsString() + ":" + fingerprint(count + "\n" + String.join("\n", weights.keySet()));
        var identity = new BoosterSetIdentity("ENERGY", "All Energy Pack", "All Energy Pack", "全能量卡包", "All Energy Pack",
            "", "mod_custom", "PTCG Craft Custom", "模组自定义", List.copyOf(sources), weights.size(), null,
            "all_energy_equal_printing", List.of(new BoosterSetIdentity.ArtVariant("main", "全能量", 1)));
        return new SetPackDefinition("all_energy_pack", "全能量卡包", version, Map.of("energy", weights),
            List.of(new PackSlot("energy", count, "全部能量印刷版本等概率")),
            new PackPublication(PackPublication.CatalogRoute.MOD_CUSTOM, "", "", "", "MOD_CUSTOM_ART", "",
                "PTCG Craft 自定义收藏卡包，收录现有数据库中的基础与特殊能量，包括历史妖精能量。每个印刷版本独立等概率，允许重复；对战仍遵守所选规则包及卡牌效果支持状态。"), identity);
    }
    public static SetPackDefinition exclusivePack(JsonObject config, List<CardCatalog.Entry> cards) {
        String setId = "simplified_chinese_exclusive";
        var weights = new TreeMap<String, Integer>();
        var sources = new TreeSet<String>();
        for (var card : cards) {
            if (!setId.equals(card.set())) continue;
            if (weights.putIfAbsent(card.id(), 1) != null) throw new IllegalArgumentException("重复的简中收藏卡 ID：" + card.id());
            sources.add(card.printedNumber().split(" ", 2)[0]);
        }
        if (weights.isEmpty()) throw new IllegalStateException("简中独占收藏卡包没有可抽取的卡牌");
        String version = config.get("dataVersion").getAsString() + ":" + fingerprint(String.join("\n", weights.keySet()));
        var identity = new BoosterSetIdentity("SC-EXCLUSIVE", "Simplified Chinese Original Art Collection",
            "Simplified Chinese Original Art Collection", "简中独占收藏", "Simplified Chinese Original Art Collection",
            "", "mod_custom", "PTCG Craft Custom", "模组自定义", List.copyOf(sources), weights.size(), null,
            "sc_exclusive_equal_printing", List.of(new BoosterSetIdentity.ArtVariant("main", "简中独占", 1)));
        return new SetPackDefinition(setId, "简中独占收藏卡包", version, Map.of("collectible", weights),
            List.of(new PackSlot("collectible", SetPackDefinition.CARDS_PER_PACK, "各印刷版本等概率")),
            new PackPublication(PackPublication.CatalogRoute.MOD_CUSTOM, "", "", "", "MOD_CUSTOM_ART", "",
                "收录已核对的简中首发独有卡面，包含普通卡、光辉卡与宣传卡；之后在其他语言再版仍保留。同插画不重复新增。各收录版本等概率，允许重复；已接入对战规则，组卡资格以所选赛制校验为准。"), identity);
    }
    public static SetPackDefinition supporterPack(List<CardCatalog.Entry> cards, CardDatabase definitions) {
        return supporterPack(cards, definitions, "");
    }
    private static SetPackDefinition supporterPack(List<CardCatalog.Entry> cards, CardDatabase definitions, String era) {
        String setId = era.isEmpty() ? dev.hua.ptcglive.economy.EconomyConfig.SUPPORTER_HIGH_RARITY_PACK : "cn_supporter_" + era;
        String eraName = switch (era) { case "sm" -> "太阳＆月亮"; case "swsh" -> "剑＆盾"; case "sv" -> "朱＆紫"; default -> "全"; };
        String title = era.isEmpty() ? "全支援者高罕卡包" : eraName + "高罕支援者收藏卡包";
        var stream = CustomPackCatalog.class.getResourceAsStream("/data/ptcglive/packs/supporter-high-rarity.json");
        if (stream == null) throw new IllegalStateException("缺少支援者高罕卡池核对表");
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var manifest = JsonParser.parseReader(reader).getAsJsonObject();
            if (manifest.get("schemaVersion").getAsInt() != 1) throw new IllegalArgumentException("不支持的支援者卡池版本");
            var byId = new HashMap<String, CardCatalog.Entry>();
            cards.forEach(c -> byId.put(c.id(), c));
            var weights = new TreeMap<String, Integer>(); var sources = new TreeSet<String>();
            var printings = new HashSet<String>();
            var high = Set.of("SR", "SAR", "UR", "HR", "SSR", "CSR", "CHR", "AR", "MA", "MUR", "★★★");
            for (var element : manifest.getAsJsonArray("cards")) {
                var row = element.getAsJsonObject(); String id = row.get("cardId").getAsString();
                if (!era.isEmpty() && !era.equals(row.get("era").getAsString())) continue;
                var card = byId.get(id);
                String rarity = row.get("rarity").getAsString();
                String printing = row.get("setCode").getAsString() + ":" + row.get("cardIndex").getAsString();
                boolean reused = row.has("reuseExistingArtwork") && row.get("reuseExistingArtwork").getAsBoolean();
                if (card == null || !high.contains(rarity) || (!reused && !rarity.equals(card.rarityZh()))
                        || (reused && (!id.contains("_en_") || !id.equals(row.get("artworkId").getAsString())))
                        || !row.has("cardType") || !"Supporter".equals(row.get("cardType").getAsString())
                        || !printings.add(printing) || weights.putIfAbsent(id, 1) != null)
                    throw new IllegalArgumentException("支援者高罕卡池存在未核对、重复或不符合条件的印刷：" + id);
                sources.add(row.get("setCode").getAsString());
            }
            if (weights.isEmpty()) throw new IllegalStateException("支援者高罕卡池为空");
            String note = "当前按已核对的简中发行清单筛选，按插画去重；相同插画复用旧卡 ID，旧卡显示原有版本资料。";
            var identity = new BoosterSetIdentity("SC-SUPPORTER", "High Rarity Supporter Collection", "High Rarity Supporter Collection",
                title, "High Rarity Supporter Collection", "", "mod_custom", "PTCG Craft Custom", "模组自定义",
                List.copyOf(sources), weights.size(), null, "sc_supporter_equal_printing", List.of(new BoosterSetIdentity.ArtVariant("main", "支援者高罕", 1)));
            return new SetPackDefinition(setId, title, "cn-supporter-artworks-2026-10-25:" + fingerprint(String.join("\n", weights.keySet())),
                Map.of("supporter", weights), List.of(new PackSlot("supporter", SetPackDefinition.CARDS_PER_PACK, "高罕支援者插画等概率")),
                new PackPublication(PackPublication.CatalogRoute.MOD_CUSTOM, "", "", "", "MOD_CUSTOM_ART", "",
                    "简中正式发行的高罕支援者主题，不限人物性别。售价 2000 宝可币；每包 5 张，各插画等概率，允许抽到重复卡。本轮不新增对战规则。" + note), identity);
        } catch (java.io.IOException error) { throw new IllegalStateException("无法读取支援者高罕卡池", error); }
    }
    private static SetPackDefinition megaSupporterPack(List<CardCatalog.Entry> cards, CardDatabase definitions) {
        var stream = CustomPackCatalog.class.getResourceAsStream("/data/ptcglive/packs/mega-supporter-high-rarity.json");
        if (stream == null) throw new IllegalStateException("缺少 Mega 高罕支援者卡池");
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var manifest = JsonParser.parseReader(reader).getAsJsonObject();
            if (manifest.get("schemaVersion").getAsInt() != 1) throw new IllegalArgumentException("未知 Mega 卡池版本");
            var registered = new HashSet<String>(); cards.forEach(c -> registered.add(c.id()));
            var weights = new TreeMap<String, Integer>(); var sources = new TreeSet<String>();
            for (var element : manifest.getAsJsonArray("cards")) {
                var row = element.getAsJsonObject(); String id = row.get("cardId").getAsString();
                var definition = definitions.find(id).orElseThrow();
                if (!registered.contains(id) || !id.startsWith("me") || !Set.of("UR", "SIR").contains(row.get("rarity").getAsString())
                    || definition.category() != CardCategory.TRAINER
                    || definition.trainerCategory() != dev.hua.ptcglive.rules.TrainerCategory.SUPPORTER
                    || weights.putIfAbsent(id, 1) != null) throw new IllegalArgumentException("Mega 高罕支援者卡池条目无效：" + id);
                sources.add(row.get("setCode").getAsString());
            }
            if (weights.isEmpty()) throw new IllegalStateException("Mega 高罕支援者卡池为空");
            String title = "Mega 汉化高罕支援者收藏卡包";
            var identity = new BoosterSetIdentity("ME-SUPPORTER", "Mega High Rarity Supporter Collection",
                "Mega High Rarity Supporter Collection", title, "Mega High Rarity Supporter Collection", "",
                "mega_evolution", "Mega Evolution", "超级进化", List.copyOf(sources), weights.size(), null,
                "mega_supporter_equal_printing", List.of(new BoosterSetIdentity.ArtVariant("main", "Mega 支援者", 1)));
            return new SetPackDefinition("mega_supporter_high_rarity", title, "mega-supporter-20261025:" + fingerprint(String.join("\n", weights.keySet())),
                Map.of("supporter", weights), List.of(new PackSlot("supporter", SetPackDefinition.CARDS_PER_PACK, "高罕支援者等概率")),
                new PackPublication(PackPublication.CatalogRoute.MOD_CUSTOM, "", "", "", "MOD_CUSTOM_ART", "",
                    "收录 Mega 英文版 UR、SIR 支援者，使用工具汉化卡面，并非官方简中印刷。每包 5 张，各卡等概率，允许重复；售价 2000 宝可币。复用原卡 ID 与规则。"), identity);
        } catch (java.io.IOException error) { throw new IllegalStateException("无法读取 Mega 高罕支援者卡池", error); }
    }
    private static String fingerprint(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 16); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException("缺少 SHA-256", error); }
    }
}
