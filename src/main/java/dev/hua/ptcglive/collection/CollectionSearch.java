package dev.hua.ptcglive.collection;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.rules.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.*;

/** One immutable search index for every printing, including collection-only cards. */
public final class CollectionSearch {
    public record Facts(String category, String subtype, Set<String> types, String stage, Set<String> traits, String chain, String englishName) {
        public Facts { types = Set.copyOf(types); traits = Set.copyOf(traits); }
    }
    public record Option(String value, String label) { }
    public record Facet(String id, String label, List<Option> options) { }
    private static final Map<String, Facts> FACTS = loadFacts();
    private static final Map<String, CardCatalog.Entry> CARDS = loadCards();
    private static final Map<String, String> ORDER_KEYS = orderKeys();
    private static final Map<String, Ruleset> RULES = BundledRules.loadCurrentRulesets();
    private static final Map<String, Set<String>> LEGAL = legalPools();
    private static final List<Facet> FACETS = buildFacets();
    private static final Map<String, String> TEXT = searchableText();
    private CollectionSearch() { }
    public static Facts facts(String id) { return FACTS.get(id); }
    public static Map<String, Facts> allFacts() { return FACTS; }
    public static List<Facet> facets() { return FACETS; }
    public static CollectionFilters parseFilters(String json) {
        if(json==null||json.length()>8192)throw new IllegalArgumentException("筛选数据过长");
        try { var filter=new Gson().fromJson(json,CollectionFilters.class);Objects.requireNonNull(filter);validate(filter);return filter; }
        catch(RuntimeException failure){throw new IllegalArgumentException("筛选数据无效，请重新选择",failure);}
    }
    public static List<Option> formats() {
        var out = new ArrayList<Option>(); out.add(new Option("ALL", "全部卡池"));
        CurrentFormats.IDS.forEach(id -> out.add(new Option(id, RULES.get(id).displayNameZh())));
        return List.copyOf(out);
    }
    public static void validate(CollectionFilters filter) {
        if (!filter.format().equals("ALL") && !RULES.containsKey(filter.format())) throw new IllegalArgumentException("未知赛制筛选");
        for (var facet : FACETS) {
            Set<String> valid = new HashSet<>(); facet.options().forEach(o -> valid.add(o.value()));
            if (!valid.containsAll(filter.facets().getOrDefault(facet.id(), Set.of()))) throw new IllegalArgumentException("未知" + facet.label() + "选项");
        }
    }
    public static List<String> queryTerms(String query) {
        if (query.length() > 80 || query.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("搜索文字无效");
        return Arrays.stream(normalizeQuery(query).split("\\s+")).filter(s -> !s.isBlank()).toList();
    }
    public static String normalizeQuery(String text) { return text.strip().toLowerCase(Locale.ROOT).replace('：', ':').replace("／", "").replace("/", ""); }
    public static boolean matches(CardCatalog.Entry card, int inventory, int collection, CollectionFilters filter, List<String> terms) {
        Facts facts = FACTS.get(card.id());
        if (facts == null || !filter.acceptsOwnership(inventory, collection)) return false;
        if (!filter.format().equals("ALL") && !LEGAL.get(filter.format()).contains(card.id())) return false;
        for (var facet : filter.facets().entrySet()) {
            Set<String> actual = values(card, facts, facet.getKey());
            if (Collections.disjoint(actual, facet.getValue())) return false;
        }
        String text = TEXT.get(card.id()); return terms.stream().allMatch(text::contains);
    }
    public static Comparator<CardCatalog.Entry> comparator(CollectionFilters filter) {
        Collator names = Collator.getInstance(Locale.CHINA);
        Comparator<CardCatalog.Entry> order = switch (filter.sort()) {
            case "NAME" -> (a,b) -> names.compare(a.nameZh(), b.nameZh());
            case "TYPE" -> Comparator.comparingInt(c -> typeOrder(FACTS.get(c.id())));
            case "EVOLUTION" -> Comparator.comparing((CardCatalog.Entry c) -> FACTS.get(c.id()).chain()).thenComparingInt(c -> stageOrder(FACTS.get(c.id()).stage()));
            case "RARITY" -> Comparator.comparingInt(c -> rarityOrder(c.rarityZh()));
            default -> Comparator.comparingInt((CardCatalog.Entry c) -> List.of("bw","xy","sm","swsh","sv","me","other").indexOf(c.era()))
                .thenComparing(c -> ORDER_KEYS.get(c.set()));
        };
        order = order.thenComparing(c -> ORDER_KEYS.get(c.id()));
        return filter.descending() ? order.reversed() : order;
    }
    public static String typeLabel(String id) { Facts f = FACTS.get(id); return f == null ? "类型待核对" : label(f.category()) + (f.subtype().isEmpty() ? "" : " · " + label(f.subtype())); }
    public static String label(String key) {
        return switch (key) {
            case "bw" -> "黑白"; case "xy" -> "XY"; case "sm" -> "太阳／月亮"; case "swsh" -> "剑／盾"; case "sv" -> "朱／紫"; case "me" -> "Mega"; case "other" -> "其他";
            case "POKEMON" -> "宝可梦"; case "TRAINER" -> "训练家"; case "ENERGY" -> "能量";
            case "ITEM" -> "物品"; case "SUPPORTER" -> "支援者"; case "TOOL" -> "宝可梦道具"; case "STADIUM" -> "竞技场";
            case "BASIC_ENERGY" -> "基本能量"; case "SPECIAL_ENERGY" -> "特殊能量";
            case "GRASS" -> "草"; case "FIRE" -> "火"; case "WATER" -> "水"; case "LIGHTNING" -> "雷"; case "PSYCHIC" -> "超"; case "FIGHTING" -> "斗";
            case "DARKNESS" -> "恶"; case "METAL" -> "钢"; case "FAIRY" -> "妖精"; case "DRAGON" -> "龙"; case "COLORLESS" -> "无色";
            case "BASIC" -> "基础"; case "STAGE_1" -> "1阶进化"; case "STAGE_2" -> "2阶进化"; case "MEGA" -> "M进化"; case "RESTORED" -> "复原";
            case "V_EVOLUTION" -> "V进化"; case "V_UNION" -> "V-UNION"; case "LEVEL_X" -> "LV.X"; case "LEGEND" -> "传说";
            case "RULE_BOX" -> "规则框"; case "EX_LEGACY" -> "宝可梦-EX"; case "EX" -> "宝可梦ex"; case "TAG_TEAM" -> "TAG TEAM";
            case "TERA" -> "太晶"; case "MEGA_EVOLUTION_EX" -> "超级进化ex"; case "RADIANT" -> "光辉"; case "PRISM_STAR" -> "棱镜之星"; case "ACE_SPEC" -> "ACE SPEC";
            case "ANCIENT" -> "古代"; case "FUTURE" -> "未来"; case "ULTRA_BEAST" -> "究极异兽"; case "TEAM_PLASMA" -> "等离子团";
            case "SINGLE_STRIKE" -> "一击"; case "RAPID_STRIKE" -> "连击"; case "FUSION_STRIKE" -> "融合";
            case "NONE" -> "无已核对字母"; case "UNKNOWN" -> "待核对";
            case "ALL" -> "全部"; case "OWNED" -> "已持有"; case "MISSING" -> "未持有"; case "DUPLICATES" -> "重复卡";
            case "INVENTORY" -> "在背包"; case "DEPOSITED" -> "已存入图鉴"; case "NOT_DEPOSITED" -> "背包未存入";
            case "EXPANSION" -> "系列编号"; case "NAME" -> "卡牌名称"; case "TYPE" -> "属性"; case "EVOLUTION" -> "进化链"; case "RARITY" -> "稀有度";
            default -> key;
        };
    }
    private static Set<String> values(CardCatalog.Entry c, Facts f, String group) {
        return switch(group) {
            case "era" -> Set.of(c.era()); case "set" -> Set.of(c.set());
            case "kind" -> f.subtype().isEmpty() ? Set.of(f.category()) : Set.of(f.category(), f.subtype());
            case "type" -> f.types(); case "stage" -> f.stage().isEmpty() ? Set.of() : Set.of(f.stage());
            case "rarity" -> Set.of(c.rarityZh()); case "mark" -> Set.of(c.regulationMark().isEmpty() ? "NONE" : c.regulationMark());
            case "trait" -> f.traits(); default -> throw new IllegalArgumentException("未知筛选分类");
        };
    }
    private static Map<String,Facts> loadFacts() {
        var stream = CollectionSearch.class.getResourceAsStream("/data/ptcglive/collection-card-facts.json");
        if(stream == null) throw new IllegalStateException("缺少图鉴分类数据");
        try(stream;var r=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            Map<String,Facts> data = new Gson().fromJson(r,new TypeToken<Map<String,Facts>>(){}.getType()); return Map.copyOf(data);
        } catch(IOException ex) { throw new IllegalStateException("图鉴分类数据无法读取",ex); }
    }
    private static Map<String,CardCatalog.Entry> loadCards() {
        var result = new LinkedHashMap<String,CardCatalog.Entry>();
        for(var c:CardCatalog.load()) { if(!FACTS.containsKey(c.id())) throw new IllegalStateException("卡牌缺少检索资料："+c.id()); result.put(c.id(),c); }
        return Collections.unmodifiableMap(result);
    }
    private static Map<String,Set<String>> legalPools() {
        var result = new HashMap<String,Set<String>>(); RULES.keySet().forEach(id->result.put(id,new HashSet<>()));
        for(var c:CARDS.values()) {
            var f=FACTS.get(c.id()); boolean basicEnergy=f.subtype().equals("BASIC_ENERGY");
            var trainer=f.category().equals("TRAINER") ? TrainerCategory.valueOf(f.subtype()) : TrainerCategory.NONE;
            var definition=new CardDefinition(c.id(),c.nameZh(),CardCategory.valueOf(f.category()),trainer,
                c.regulationMark().isEmpty()?"NONE":c.regulationMark(),Set.of("international","cn"),f.stage().equals("BASIC"),basicEnergy,4,Map.of(),EffectSupport.UNSUPPORTED,"仅图鉴检索资料");
            EnergyType energy=basicEnergy&&f.types().size()==1?EnergyType.valueOf(f.types().iterator().next()):null;
            RULES.forEach((id,rule)->{if(rule.cardPool().assess(rule,definition,energy).status()==CardPoolPolicy.Status.LEGAL)result.get(id).add(c.id());});
        }
        result.replaceAll((id,set)->Set.copyOf(set));return Map.copyOf(result);
    }
    private static List<Facet> buildFacets() {
        var setNames=new HashMap<String,String>();
        dev.hua.ptcglive.packs.PackCatalog.loadBundled().values().forEach(p->p.identity().sourceSetIds().forEach(id->setNames.put(id,p.identity().chineseName())));
        var labels=List.of("世代","系列","卡牌类型","属性","进化阶段","稀有度","赛制标记","特殊类别");var out=new ArrayList<Facet>();
        for(int i=0;i<CollectionFilters.GROUPS.size();i++) {
            String group=CollectionFilters.GROUPS.get(i);var values=new TreeSet<String>();
            CARDS.values().forEach(c->values.addAll(values(c,FACTS.get(c.id()),group)));
            var ordered=new ArrayList<>(values);
            if(group.equals("era"))ordered.sort(Comparator.comparingInt(v->List.of("me","sv","swsh","sm","xy","bw","other").indexOf(v)));
            if(group.equals("set"))ordered.sort(Comparator.comparing(CollectionSearch::natural));
            if(group.equals("type"))ordered.sort(Comparator.comparingInt(v->List.of("GRASS","FIRE","WATER","LIGHTNING","PSYCHIC","FIGHTING","DARKNESS","METAL","FAIRY","DRAGON","COLORLESS").indexOf(v)));
            if(group.equals("stage"))ordered.sort(Comparator.comparingInt(CollectionSearch::stageOrder));
            out.add(new Facet(group,labels.get(i),ordered.stream().map(v->new Option(v,group.equals("set")?setNames.getOrDefault(v,"")+" "+v.toUpperCase(Locale.ROOT):label(v))).toList()));
        }
        return List.copyOf(out);
    }
    private static Map<String,String> searchableText() {
        var out=new HashMap<String,String>();CARDS.values().forEach(c->{var f=FACTS.get(c.id());var text=new StringBuilder(c.label()+" "+c.id()+" "+c.set()+" "+f.englishName()+" "+PrintedRegulationMarks.searchTerm(c.id()));
            for(String group:CollectionFilters.GROUPS)for(String value:values(c,f,group))text.append(' ').append(label(value)).append(' ').append(value);out.put(c.id(),normalizeQuery(text.toString()));});return Map.copyOf(out);
    }
    private static String natural(String value) { var m=java.util.regex.Pattern.compile("\\d+").matcher(value);return m.replaceAll(r->String.format(Locale.ROOT,"%08d",Integer.parseInt(r.group()))); }
    private static Map<String,String> orderKeys() { var out=new HashMap<String,String>();CARDS.values().forEach(c->{out.put(c.id(),natural(c.id()));out.computeIfAbsent(c.set(),CollectionSearch::natural);});return Map.copyOf(out); }
    private static int stageOrder(String stage) { return switch(stage){case "BASIC","RESTORED"->0;case "STAGE_1","VMAX","VSTAR","MEGA","V_EVOLUTION","LEVEL_X"->1;case "STAGE_2"->2;default->3;}; }
    private static int typeOrder(Facts f) { return f.types().stream().mapToInt(t->List.of("GRASS","FIRE","WATER","LIGHTNING","PSYCHIC","FIGHTING","DARKNESS","METAL","FAIRY","DRAGON","COLORLESS").indexOf(t)).min().orElse(99); }
    private static int rarityOrder(String r) { return switch(dev.hua.ptcglive.CardLabel.rarityCode(r)){
        case "C"->0;case "U"->1;case "R"->2;case "S","K","ACE","A","PR","CLASSIC"->3;
        case "RR"->4;case "RRR"->5;case "SR","SSR","HR"->6;case "AR","CHR"->7;
        case "SAR","CSR","MA"->8;case "UR"->9;case "MUR","BWR"->10;default->99;
    }; }
}
