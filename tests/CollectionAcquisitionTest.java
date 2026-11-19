import com.google.gson.JsonParser;
import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.economy.EconomyConfig;
import dev.hua.ptcglive.packs.*;
import dev.hua.ptcglive.rules.*;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import javax.imageio.ImageIO;

/** New acquisition routes must use registered identities and the existing server opening pipeline. */
public final class CollectionAcquisitionTest {
    public static void main(String[] args) throws Exception {
        var cards = CardCatalog.load();
        var definitions = BundledRules.loadCardDefinitions();
        var packs = new TreeMap<>(PackCatalog.loadBundled());
        packs.putAll(CustomPackCatalog.loadBundled(cards, definitions));
        var covered = new HashSet<String>();
        packs.values().forEach(p -> p.pools().values().forEach(pool -> covered.addAll(pool.keySet())));
        // Already-delivered duplicate CN IDs stay for saves; their canonical artwork IDs are obtainable.
        var compatibility = JsonParser.parseString(Files.readString(Path.of("tests/fixtures/supporter-compatibility-ids.json"))).getAsJsonObject();
        compatibility.entrySet().forEach(e -> {
            check(covered.contains(e.getValue().getAsString()), "兼容印刷的对应插画没有卡包：" + e.getKey());
            check(!covered.contains(e.getKey()), "旧同插画兼容 ID 被重复加入卡池：" + e.getKey());
        });
        var missing = cards.stream().filter(c -> !c.set().equals("sd100") && !covered.contains(c.id()) && !compatibility.has(c.id())).map(CardCatalog.Entry::id).toList();
        check(missing.isEmpty(), "卡牌没有开包入口（预组100除外）：" + missing);
        var registered = new HashSet<String>(); cards.forEach(c -> registered.add(c.id()));
        check(registered.containsAll(covered), "卡池含已删除或未注册卡牌");
        var boxes = new CardBoxService(Clock.systemUTC());
        var opening = new PackOpeningService(boxes);
        var prices = EconomyConfig.loadBundled();
        var owner = UUID.randomUUID();
        var report = new ArrayList<String>(); report.add("卡包\t源系列\t卡池张数\t售价\t每包张数");
        int count = 0, printings = 0;
        Path assets = Path.of("src/main/resources/assets/ptcglive");
        for (var pack : packs.values()) {
            if (!pack.setId().startsWith("collection_")) continue;
            count++;
            check(pack.custom() && pack.publication().noticeZh().contains("不是官方补充包"), "虚构官方产品");
            String source = pack.identity().sourceSetIds().getFirst();
            var expected = new TreeSet<String>(); cards.stream().filter(c -> c.set().equals(source)).forEach(c -> expected.add(c.id()));
            check(pack.pools().get("collection").keySet().equals(expected), "卡池漏卡或错误串入系列：" + source);
            printings += expected.size();
            check(prices.packPrice(pack.setId()) == 20 && pack.cardsPerPack() == 5, "收藏卡包价格或张数错误");
            var preview = PackUiSnapshot.preview(UUID.randomUUID(), pack, cards, definitions);
            for (var card : preview.cards()) check(Math.abs(card.chances().getFirst().probability() - 1d / expected.size()) < 1e-12, "显示概率与权重不一致");
            check(ImageIO.read(assets.resolve(preview.info().artTexture().substring("ptcglive:".length())).toFile()) != null, "封面无法读取");
            check(Files.isRegularFile(assets.resolve("models/item/" + pack.itemPath() + ".json")), "物品模型缺失");
            for (int seed = 0; seed < 20; seed++) {
                var result = opening.open(owner, pack, new ServerRandom(seed));
                check(result.cards().size() == 5 && result.cards().stream().allMatch(c -> expected.contains(c.definitionId())), "服务端开包跨池或少发卡");
            }
            report.add(pack.displayNameZh() + "\t" + source + "\t" + expected.size() + "\t20\t5");
        }
        check(count == 13 && boxes.unboxedForOwner(owner).size() == count * 100, "卡包缺失或开包资产不守恒");
        check(prices.packPrice(EconomyConfig.SC_EXCLUSIVE_PACK) == 2000 && prices.packPrice(EconomyConfig.SUPPORTER_HIGH_RARITY_PACK) == 2000, "原收藏包价格改变");
        for (String lang : List.of("zh_cn", "en_us")) {
            var names = JsonParser.parseString(Files.readString(assets.resolve("lang/" + lang + ".json"))).getAsJsonObject();
            for (String key : List.of("block.ptcglive.card_display_case", "block.ptcglive.card_recycling_station", "item.ptcglive.phone", "item.ptcglive.card_box"))
                check(names.has(key) && names.get(key).getAsString().matches(".*[\u4e00-\u9fff].*"), "中文回退名称缺失：" + key);
        }
        Path output = Path.of("outputs/rule-screen-0.7.26"); Files.createDirectories(output);
        Files.write(output.resolve("collection-pack-audit.tsv"), report);
        System.out.println("CollectionAcquisitionTest passed: " + count + " packs, " + printings + " printings; " + covered.size() + " catalog cards have pack access; SD100 retains its selector.");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
