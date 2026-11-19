import com.google.gson.JsonParser;
import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.packs.*;
import dev.hua.ptcglive.rules.*;
import java.time.Clock;
import java.util.*;

/** Card printing identity, exact odds and ownership must survive the cross-set pool. */
public final class AllEnergyPackTest {
    public static void main(String[] args) throws Exception {
        var catalog = CardCatalog.load();
        for (int number = 75; number <= 83; number++) {
            String id = "xy9-5_en_%03d".formatted(number);
            for (String namespace : List.of("ptcglive", "ptcglive_mc")) {
                try (var stream = AllEnergyPackTest.class.getResourceAsStream("/assets/" + namespace + "/models/item/" + id + ".json")) {
                    check(stream != null, "缺少能量模型：" + id);
                    var model = JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    check(model.getAsJsonObject("textures").get("layer0").getAsString().equals("ptcglive_mc:item/cards/" + id), "能量模型仍是纸张占位：" + id);
                }
            }
            for (String path : List.of("textures/item/cards/" + id + ".png", "card_thumb/" + id + ".jpg")) {
                try (var stream = AllEnergyPackTest.class.getResourceAsStream("/assets/ptcglive_mc/" + path)) {
                    check(stream != null && javax.imageio.ImageIO.read(stream) != null, "能量图像缺失或无法解码：" + id);
                }
            }
            check(catalog.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow().label().contains(" C "), "能量稀有度仍为问号：" + id);
        }
        var definitions = BundledRules.loadCardDefinitions();
        var custom = CustomPackCatalog.loadBundled(catalog, definitions);
        var pack = custom.get("all_energy_pack");
        var expected = new TreeSet<String>();
        for (var card : catalog) if (definitions.find(card.id()).map(d -> d.category() == CardCategory.ENERGY).orElse(false)) expected.add(card.id());
        check(pack != null && pack.custom() && pack.itemPath().equals("all_energy_pack"), "自定义卡包身份错误");
        check(pack.pools().get("energy").keySet().equals(expected), "遗漏印刷版本或混入非能量卡");
        check(expected.size() > 100, "不能只收录新版基本能量");
        var everyPrinting = new TreeSet<String>();
        definitions.all().stream().filter(d -> d.category() == CardCategory.ENERGY && !d.id().startsWith("fixture-"))
            .forEach(d -> everyPrinting.add(d.id()));
        check(expected.equals(everyPrinting), "数据库的能量卡缺少独立物品记录");
        var combat = BundledCombatData.load();
        check(expected.stream().anyMatch(id -> combat.energyType(id).orElse(null) == dev.hua.ptcglive.engine.field.EnergyType.FAIRY), "历史妖精能量被过滤");
        check(expected.stream().anyMatch(id -> !definitions.find(id).orElseThrow().basicEnergy()), "特殊能量被过滤");
        check(expected.stream().allMatch(id -> definitions.find(id).orElseThrow().effectSupport() == EffectSupport.SUPPORTED && combat.energyProfile(id).isPresent()), "能量卡规则未完成或无法加载");
        var view = PackUiSnapshot.preview(UUID.randomUUID(), pack, catalog, definitions);
        check(view.info().custom() && view.info().cardsPerPack() == 5 && view.cards().size() == expected.size(), "卡池展示不完整");
        check(!view.descriptionZh().contains("官方开出概率未公开"), "自定义产品不能冒充官方卡包");
        for (var row : view.cards()) {
            check(row.chances().size() == 1 && Math.abs(row.chances().getFirst().probability() - 1d / expected.size()) < 1e-12, "展示概率与实际不一致");
            check(row.rarityZh().equals(catalog.stream().filter(c -> c.id().equals(row.definitionId())).findFirst().orElseThrow().rarityZh()), "稀有度被覆盖");
        }
        var boxes = new CardBoxService(Clock.systemUTC()); var opening = new PackOpeningService(boxes); UUID owner = UUID.randomUUID();
        for (int seed = 0; seed < 100; seed++) {
            var result = opening.open(owner, pack, new ServerRandom(seed));
            check(result.cards().size() == 5 && result.cards().stream().allMatch(c -> expected.contains(c.definitionId())), "服务器开包结果错误");
        }
        check(boxes.unboxedForOwner(owner).size() == 500, "重复抽取的卡牌被合并或丢失");
        var config = JsonParser.parseString("{\"schemaVersion\":1,\"dataVersion\":\"test\",\"energy_pack_card_count\":3}").getAsJsonObject();
        var three = CustomPackCatalog.energyPack(config, catalog, definitions);
        check(three.cardsPerPack() == 3 && !three.dataVersion().equals(pack.dataVersion()), "可配置张数未进入版本化概率快照");
        check(opening.open(owner, three, new ServerRandom(0)).cards().size() == 3, "开包仍硬编码五张");
        config.addProperty("energy_pack_card_count", 0);
        try { CustomPackCatalog.energyPack(config, catalog, definitions); throw new AssertionError("接受了零张配置"); }
        catch (IllegalArgumentException expectedError) { }
        System.out.println("AllEnergyPackTest passed: " + expected.size() + " independent Energy printings, exact odds, configurable count and duplicate ownership");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
