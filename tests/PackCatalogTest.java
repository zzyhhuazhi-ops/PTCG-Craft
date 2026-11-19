import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.packs.PackCatalog;
import dev.hua.ptcglive.packs.SetPackDefinition;
import dev.hua.ptcglive.economy.EconomyConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;

/**
 * One 美版 booster pack per real expansion with cards in the mod: every catalog
 * card is either in exactly its own expansion's pool or in a set explicitly
 * listed as having no real booster; no pool crosses expansions.
 */
public final class PackCatalogTest {
    public static void main(String[] args) throws Exception {
        var packs = PackCatalog.loadBundled(); var catalog = CardCatalog.loadBase();
        JsonObject identities = JsonParser.parseString(Files.readString(Path.of("tools/packs/booster/set-identities.json"))).getAsJsonObject();
        var nonBooster = new HashSet<String>();
        identities.getAsJsonArray("nonBoosterSets").forEach(e -> nonBooster.add(e.getAsJsonObject().get("setId").getAsString()));
        var owner = new HashMap<String, String>();
        for (var pack : packs.values()) for (String source : pack.identity().sourceSetIds())
            if (owner.put(source, pack.setId()) != null) throw new AssertionError("同一 PTCG Live 系列归入了两个补充包: " + source);

        var expected = new HashMap<String, HashSet<String>>();
        for (var card : catalog) {
            String pack = owner.get(card.set());
            if (pack != null) expected.computeIfAbsent(pack, k -> new HashSet<>()).add(card.id());
            else if (!nonBooster.contains(card.set())) throw new AssertionError("卡牌所属系列既没有补充包也没有登记为非补充包: " + card.id());
        }
        var seen = new HashMap<String, String>();
        String previousDate = "";
        for (var pack : packs.values()) {
            if (!pack.setId().matches("[a-z][a-z0-9_]*")) throw new AssertionError("补充包 ID 不是小写下划线 slug: " + pack.setId());
            if (pack.cardsPerPack() != SetPackDefinition.CARDS_PER_PACK) throw new AssertionError("当前玩法应为每包 5 张: " + pack.setId());
            if (pack.identity().artVariants().isEmpty()) throw new AssertionError("READY 补充包没有美版封面: " + pack.setId());
            if (pack.identity().chineseName().isBlank() || pack.identity().officialEnglishName().isBlank() || pack.identity().setCode().isBlank())
                throw new AssertionError("补充包缺少中英文名或系列代码: " + pack.setId());
            if (pack.pools().values().stream().anyMatch(java.util.Map::isEmpty)) throw new AssertionError("空卡池被注册: " + pack.setId());
            if (pack.identity().releaseDate().compareTo(previousDate) < 0) throw new AssertionError("补充包没有按美版发售日排序: " + pack.setId());
            previousDate = pack.identity().releaseDate();
            var actual = new HashSet<String>(); pack.pools().values().forEach(pool -> actual.addAll(pool.keySet()));
            if (!actual.equals(expected.getOrDefault(pack.setId(), new HashSet<>()))) throw new AssertionError("卡池不完整或含其他系列的卡: " + pack.setId());
            for (String card : actual) if (seen.putIfAbsent(card, pack.setId()) != null) throw new AssertionError("同一张卡进入了两个补充包: " + card);
        }
        if (!expected.keySet().equals(packs.keySet())) throw new AssertionError("有卡牌的扩充包与已注册补充包不一致");

        if (EconomyConfig.loadBundled().packPrice() != 20) throw new AssertionError("统一卡包售价不是 20 宝可币");
        for (String setId : packs.keySet()) {
            if (PackCatalogTest.class.getClassLoader().getResource("assets/ptcglive/models/item/" + setId + "_merchant_deed.json") != null)
                throw new AssertionError("已废除的委托书模型仍存在: " + setId);
        }
        System.out.println("PackCatalogTest passed: " + packs.size() + " 个美版补充包、" + seen.size()
            + " 张卡各归其扩充包，卡池不跨系列；手机商城可直接遍历注册表，统一售价 20 币");
    }
}
