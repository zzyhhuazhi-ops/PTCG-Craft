package dev.hua.ptcglive.rules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hua.ptcglive.CardExclusions;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads explicit bundled JSON snapshots. Online content is never fetched automatically during play. */
public final class BundledRules {
    private BundledRules() { }
    /** Runtime choices are deliberately separate from immutable historical snapshots. */
    public static Map<String, Ruleset> loadCurrentRulesets() {
        Map<String, Ruleset> result = new LinkedHashMap<>();
        for (String id : CurrentFormats.IDS) {
            JsonObject data=object("/data/ptcglive/rulesets/current/" + id + ".json");
            if(id.equals(CurrentFormats.CHINESE)||id.equals(CurrentFormats.INTERNATIONAL))addSameNameReprints(data);
            Ruleset rule = ruleset(data);
            if (!id.equals(rule.id())) throw new IllegalStateException("当前赛制文件 ID 不匹配：" + id);
            result.put(id, rule);
        }
        return java.util.Collections.unmodifiableMap(result);
    }
    /** Same printed name waives only the selected current format's regulation-mark gate. */
    private static void addSameNameReprints(JsonObject data) {
        var original=ruleset(data);var definitions=loadCardDefinitions();
        var legalNames=new java.util.HashSet<String>();
        for(var card:definitions.all())if(!card.basicEnergy()&&card.effectSupport()==EffectSupport.SUPPORTED
            &&original.cardPool().assess(original,card).status()==CardPoolPolicy.Status.LEGAL)
            legalNames.add(card.category()+"\u0000"+card.nameZh());
        var ids=new LinkedHashSet<>(original.cardPool().legalCardIds());
        for(var card:definitions.all())if(!card.basicEnergy()&&legalNames.contains(card.category()+"\u0000"+card.nameZh()))ids.add(card.id());
        var array=new JsonArray();ids.stream().sorted().forEach(array::add);data.getAsJsonObject("cardPool").add("legalCardIds",array);
    }
    public static Map<String, Ruleset> loadRulesets() {
        JsonObject index = object("/data/ptcglive/rulesets/index.json");
        Map<String, Ruleset> result = new LinkedHashMap<>();
        for (JsonElement file : index.getAsJsonArray("rulesets")) {
            JsonObject data = object("/data/ptcglive/rulesets/" + file.getAsString());
            Ruleset ruleset = ruleset(data);
            if (result.putIfAbsent(ruleset.id(), ruleset) != null) throw new IllegalStateException("重复规则包: " + ruleset.id());
        }
        // Validate the dated regional index at startup; stale/missing IDs must not silently select another region.
        StandardRulesCalendar.load(result);
        return java.util.Collections.unmodifiableMap(result);
    }
    public static CardDatabase loadCardDefinitions() {
        JsonObject index = object("/data/ptcglive/cards/index.json");
        List<CardDefinition> cards = new ArrayList<>();
        for (JsonElement file : index.getAsJsonArray("files")) {
            for (JsonElement element : object("/data/ptcglive/cards/" + file.getAsString()).getAsJsonArray("cards")) {
                JsonObject item = element.getAsJsonObject();
                if (!CardExclusions.contains(item.get("id").getAsString())) cards.add(card(item));
            }
        }
        CardEffectAuditCatalog.loadBundled().requireEvidenceForSupported(cards);
        JsonObject heightData = object("/data/ptcglive/cards/printed-heights.json");
        requireKnownKeys(heightData, Set.of("schemaVersion", "cards"));
        if (heightData.get("schemaVersion").getAsInt() != 1) throw new IllegalArgumentException("未知印刷身高版本");
        Map<String, PrintedHeight> heights = new LinkedHashMap<>();
        heightData.getAsJsonObject("cards").entrySet().forEach(entry -> {
            if (CardExclusions.contains(entry.getKey())) return;
            var height = entry.getValue().getAsJsonObject();
            requireKnownKeys(height, Set.of("unit", "value"));
            var number = height.get("value").getAsBigDecimal().intValueExact();
            heights.put(entry.getKey(), new PrintedHeight(PrintedHeight.Unit.valueOf(height.get("unit").getAsString()), number));
        });
        return new CardDatabase(cards, heights);
    }
    public static Ruleset ruleset(JsonObject data) {
        int schema = data.has("schemaVersion") ? data.get("schemaVersion").getAsInt() : 1;
        if (schema != 1 && schema != 2) throw new IllegalArgumentException("未知规则包数据版本：" + schema);
        if (schema == 2 && (!data.has("edition") || !data.has("cardPool")))
            throw new IllegalArgumentException("新版规则包必须显式声明档案状态与卡池策略");
        if (schema == 2) requireKnownKeys(data, Set.of("schemaVersion", "id", "version", "displayNameZh", "region", "effectiveDate",
            "edition", "cardPool", "trainerClassification", "legalRegulationMarks", "legalBasicEnergyIds", "bannedCardIds",
            "deckTagLimits", "setupProcedure", "matchSetup", "firstTurn", "turnActions", "triggerTiming", "changeLogZh", "sources"));
        List<RuleSource> sources = new ArrayList<>();
        for (JsonElement element : data.getAsJsonArray("sources")) {
            JsonObject s = element.getAsJsonObject();
            sources.add(new RuleSource(s.get("titleZh").getAsString(), s.get("url").getAsString(),
                dateOrNull(s, "publishedDate"), dateOrNull(s, "effectiveDate"), s.get("region").getAsString(),
                s.get("documentVersion").getAsString(), s.get("noteZh").getAsString()));
        }
        Map<String, Integer> limits = new HashMap<>();
        data.getAsJsonObject("deckTagLimits").entrySet().forEach(e -> limits.put(e.getKey(), e.getValue().getAsInt()));
        return new Ruleset(data.get("id").getAsString(), data.get("version").getAsString(), data.get("displayNameZh").getAsString(),
            data.get("region").getAsString(), LocalDate.parse(data.get("effectiveDate").getAsString()), strings(data, "legalRegulationMarks"),
            strings(data, "bannedCardIds"), limits, sources, stringsList(data, "changeLogZh"),
            data.has("legalBasicEnergyIds") ? strings(data, "legalBasicEnergyIds") : Set.of(),
            SetupProcedure.fromId(data.has("setupProcedure") ? data.get("setupProcedure").getAsString() : "international-setup-v1"),
            data.has("matchSetup") ? new MatchSetupRules(data.getAsJsonObject("matchSetup").get("deckSize").getAsInt(), data.getAsJsonObject("matchSetup").get("openingHandSize").getAsInt(),
                data.getAsJsonObject("matchSetup").get("prizeCards").getAsInt(), data.getAsJsonObject("matchSetup").get("benchLimit").getAsInt())
                : MatchSetupRules.INTERNATIONAL_STANDARD,
            data.has("firstTurn") ? new FirstTurnRules(data.getAsJsonObject("firstTurn").get("firstPlayerMayAttack").getAsBoolean(),
                data.getAsJsonObject("firstTurn").get("firstPlayerMayPlaySupporter").getAsBoolean()) : FirstTurnRules.INTERNATIONAL_STANDARD,
            data.has("turnActions") ? new TurnActionRules(data.getAsJsonObject("turnActions").get("energyAttachmentsFromHand").getAsInt(),
                data.getAsJsonObject("turnActions").get("retreats").getAsInt(), data.getAsJsonObject("turnActions").get("supporters").getAsInt(),
                data.getAsJsonObject("turnActions").get("stadiums").getAsInt()) : TurnActionRules.INTERNATIONAL_STANDARD,
            data.has("edition") ? edition(data.getAsJsonObject("edition")) : RulesetEdition.LEGACY,
            data.has("cardPool") ? cardPool(data.getAsJsonObject("cardPool")) : CardPoolPolicy.LEGACY,
            data.has("trainerClassification") ? trainerClassification(data.getAsJsonObject("trainerClassification")) : TrainerClassificationRules.MODERN,
            data.has("triggerTiming") ? TriggerTiming.valueOf(data.get("triggerTiming").getAsString()) : TriggerTiming.UNVERIFIED);
    }
    private static TrainerClassificationRules trainerClassification(JsonObject data) {
        requireKnownKeys(data, Set.of("pokemonToolsAreItems"));
        var value = data.get("pokemonToolsAreItems");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException("工具分类规则必须显式使用布尔值");
        return new TrainerClassificationRules(value.getAsBoolean());
    }
    private static RulesetEdition edition(JsonObject data) {
        requireKnownKeys(data, Set.of("seasonYear", "effectiveUntil", "availability", "casualOnly", "noticeZh"));
        return new RulesetEdition(data.get("seasonYear").getAsInt(), dateOrNull(data, "effectiveUntil"),
            RulesetEdition.Availability.valueOf(data.get("availability").getAsString()),
            data.get("casualOnly").getAsBoolean(), data.get("noticeZh").getAsString());
    }
    private static CardPoolPolicy cardPool(JsonObject data) {
        requireKnownKeys(data, Set.of("mode", "legalCardIds", "asOfDate", "legalFromDates", "basicEnergyTypes", "practiceMappings"));
        Map<String, LocalDate> legalFromDates = new LinkedHashMap<>();
        if (data.has("legalFromDates")) data.getAsJsonObject("legalFromDates").entrySet().forEach(entry ->
            legalFromDates.put(entry.getKey(), LocalDate.parse(entry.getValue().getAsString())));
        Set<dev.hua.ptcglive.engine.field.EnergyType> types = new LinkedHashSet<>();
        if (data.has("basicEnergyTypes")) for (var value : data.getAsJsonArray("basicEnergyTypes")) {
            var type = dev.hua.ptcglive.engine.field.EnergyType.valueOf(value.getAsString());
            if (!types.add(type)) throw new IllegalArgumentException("基本能量属性重复：" + type);
        }
        var mappings = new LinkedHashMap<String, PracticeCardMapping>();
        if (data.has("practiceMappings")) data.getAsJsonObject("practiceMappings").entrySet().forEach(entry -> {
            var mapping = entry.getValue().getAsJsonObject();
            requireKnownKeys(mapping, Set.of("sourceRegion", "targetRegion", "targetPrintingId", "regulationMark", "legalFrom",
                "category", "basicEnergyType", "sourceUrl", "noteZh"));
            var energy = optionalString(mapping, "basicEnergyType");
            mappings.put(entry.getKey(), new PracticeCardMapping(mapping.get("sourceRegion").getAsString(), mapping.get("targetRegion").getAsString(),
                mapping.get("targetPrintingId").getAsString(), mapping.get("regulationMark").getAsString(), dateOrNull(mapping, "legalFrom"),
                CardCategory.valueOf(mapping.get("category").getAsString()), energy == null ? null : dev.hua.ptcglive.engine.field.EnergyType.valueOf(energy),
                mapping.get("sourceUrl").getAsString(), mapping.get("noteZh").getAsString()));
        });
        return new CardPoolPolicy(CardPoolPolicy.Mode.valueOf(data.get("mode").getAsString()),
            data.has("legalCardIds") ? strings(data, "legalCardIds") : Set.of(), dateOrNull(data, "asOfDate"), legalFromDates, types, mappings);
    }
    private static void requireKnownKeys(JsonObject data, Set<String> keys) {
        for (String key : data.keySet()) if (!keys.contains(key)) throw new IllegalArgumentException("未知规则策略字段：" + key);
    }
    private static CardDefinition card(JsonObject data) {
        Map<String, Integer> limits = new HashMap<>();
        data.getAsJsonObject("specialDeckLimits").entrySet().forEach(e -> limits.put(e.getKey(), e.getValue().getAsInt()));
        return new CardDefinition(data.get("id").getAsString(), dev.hua.ptcglive.CardNameLocalization.name(data.get("id").getAsString(), data.get("nameZh").getAsString()),
            CardCategory.valueOf(data.get("category").getAsString()), TrainerCategory.valueOf(data.get("trainerCategory").getAsString()),
            PrintedRegulationMarks.definitionMark(data.get("id").getAsString(), data.get("regulationMark").getAsString()),
            strings(data, "regions"), data.get("basicPokemon").getAsBoolean(),
            data.get("basicEnergy").getAsBoolean(), data.get("copyLimit").getAsInt(), limits,
            EffectSupport.valueOf(data.get("effectSupport").getAsString()), optionalString(data, "unsupportedReasonZh"),
            pokemonFamilies(data), pokemonTypes(data), battleStyles(data));
    }
    private static Set<BattleStyle> battleStyles(JsonObject data) {
        var values=new LinkedHashSet<>(BattleStyle.fromPokemonFamilies(pokemonFamilies(data)));
        if(data.has("battleStyles"))for(JsonElement value:data.getAsJsonArray("battleStyles"))
            values.add(BattleStyle.valueOf(value.getAsString()));
        return Set.copyOf(values);
    }
    private static Set<PokemonFamily> pokemonFamilies(JsonObject data) {
        if (!data.has("pokemonFamilies")) return Set.of();
        var values = new LinkedHashSet<PokemonFamily>();
        for (JsonElement value : data.getAsJsonArray("pokemonFamilies"))
            values.add(PokemonFamily.valueOf(value.getAsString().trim().toUpperCase(java.util.Locale.ROOT)));
        return Set.copyOf(values);
    }
    private static Set<dev.hua.ptcglive.engine.field.EnergyType> pokemonTypes(JsonObject data) {
        if (!data.has("pokemonTypes")) return Set.of();
        var values = new LinkedHashSet<dev.hua.ptcglive.engine.field.EnergyType>();
        for (JsonElement value : data.getAsJsonArray("pokemonTypes"))
            values.add(dev.hua.ptcglive.engine.field.EnergyType.valueOf(value.getAsString().trim().toUpperCase(java.util.Locale.ROOT)));
        return Set.copyOf(values);
    }
    private static JsonObject object(String path) {
        InputStream stream = BundledRules.class.getResourceAsStream(path);
        if (stream == null) throw new IllegalStateException("缺少规则数据文件: " + path);
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) { return JsonParser.parseReader(reader).getAsJsonObject(); }
        catch (Exception e) { throw new IllegalStateException("无法读取规则数据文件: " + path, e); }
    }
    private static Set<String> strings(JsonObject data, String name) { return new LinkedHashSet<>(stringsList(data, name)); }
    private static List<String> stringsList(JsonObject data, String name) {
        List<String> result = new ArrayList<>(); for (JsonElement e : data.getAsJsonArray(name)) result.add(e.getAsString()); return result;
    }
    private static String optionalString(JsonObject object, String name) { return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : null; }
    private static LocalDate dateOrNull(JsonObject object, String name) { String value = optionalString(object, name); return value == null || value.isBlank() ? null : LocalDate.parse(value); }
}
