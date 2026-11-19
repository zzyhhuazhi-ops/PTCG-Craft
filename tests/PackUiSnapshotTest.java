import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.packs.PackCatalog;
import dev.hua.ptcglive.packs.PackOpeningService;
import dev.hua.ptcglive.packs.PackUiSnapshot;
import dev.hua.ptcglive.rules.BundledRules;
import java.util.HashSet;
import java.util.UUID;

/** The client preview is derived from exactly the same server pool weights used for opening. */
public final class PackUiSnapshotTest {
    public static void main(String[] args) {
        var pack = PackCatalog.loadBundled().values().iterator().next();
        var snapshot = PackUiSnapshot.preview(UUID.randomUUID(), pack, CardCatalog.load(), BundledRules.loadCardDefinitions());
        if (!snapshot.setId().equals(pack.setId()) || !snapshot.dataVersion().equals(pack.dataVersion()))
            throw new AssertionError("抽卡界面未携带服务器卡包版本");
        if (snapshot.slots().stream().mapToInt(PackUiSnapshot.SlotRow::count).sum() != 5)
            throw new AssertionError("抽卡界面没有明确固定五张位置");
        var info = snapshot.info();
        if (info.chineseName().isBlank() || info.englishName().isBlank() || info.setCode().isBlank() || info.releaseDate().isBlank()
            || !info.artTexture().startsWith("ptcglive:textures/item/booster_packs/" + pack.setId() + "/") || info.poolSize() != snapshot.cards().size()
            || info.cardsPerPack() != pack.cardsPerPack())
            throw new AssertionError("抽卡界面缺少美版封面、中英文系列名或卡池数量");
        var second = pack.identity().artVariants().get(pack.identity().artVariants().size() - 1);
        var variantSnapshot = PackUiSnapshot.preview(UUID.randomUUID(), pack, CardCatalog.load(), BundledRules.loadCardDefinitions(), second.customModelData());
        if (!variantSnapshot.info().artTexture().endsWith("/" + second.key() + ".png")) throw new AssertionError("抽卡界面没有显示所持卡包的封面变体");
        var expected = new HashSet<String>(); pack.pools().values().forEach(pool -> expected.addAll(pool.keySet()));
        var displayed = new HashSet<String>();
        for (var row : snapshot.cards()) {
            displayed.add(row.definitionId());
            if (row.nameZh().isBlank() || row.printedNumber().isBlank() || row.rarityZh().isBlank() || row.typeZh().isBlank())
                throw new AssertionError("抽卡图鉴行缺少中文显示字段");
            for (var chance : row.chances()) {
                var slot = snapshot.slots().stream().filter(value -> value.positionsZh().equals(chance.positionsZh())).findFirst().orElseThrow();
                String poolId = pack.slots().stream().filter(value -> value.displayNameZh().equals(slot.slotNameZh())).findFirst().orElseThrow().poolId();
                int weight = pack.pools().get(poolId).get(row.definitionId());
                double exact = PackOpeningService.probability(pack.pools().get(poolId), row.definitionId());
                if (weight < 1 || Math.abs(exact - chance.probability()) > 0.0000000001d)
                    throw new AssertionError("预览概率与服务器实际权重不一致");
            }
        }
        if (!displayed.equals(expected)) throw new AssertionError("抽卡界面没有展示完整卡池");
        var result = snapshot.withResults(snapshot.cards().stream().limit(5).map(PackUiSnapshot.CardRow::definitionId).toList(),
            CardCatalog.load(), BundledRules.loadCardDefinitions());
        if (result.phase() != PackUiSnapshot.Phase.RESULTS || result.results().size() != 5 || !result.cards().isEmpty()
            || !result.slots().equals(snapshot.slots()) || !result.info().equals(snapshot.info()))
            throw new AssertionError("精简结果包缺少结果或改变了原概率说明");
        System.out.println("PackUiSnapshotTest passed: complete pool, five positions, exact displayed odds and server result snapshot");
    }
}
