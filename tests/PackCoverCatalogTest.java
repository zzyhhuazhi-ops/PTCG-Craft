import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hua.ptcglive.packs.PackCatalog;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import javax.imageio.ImageIO;

/**
 * Every READY 美版 booster pack has an item model, one override + model per pack
 * art variant, a 128×128 transparent texture per variant, reviewed source
 * metadata, and separate Chinese/English display names.  Legacy pack models and
 * the old cover textures are gone.
 */
public final class PackCoverCatalogTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of("src/main/resources/assets/ptcglive");
        JsonObject zh = json(root.resolve("lang/zh_cn.json")), en = json(root.resolve("lang/en_us.json"));
        JsonObject sources = json(Path.of("tools/packs/booster/pack-art-sources.json")).getAsJsonObject("packs");
        int textures = 0;
        for (var pack : PackCatalog.loadBundled().values()) {
            String item = pack.setId() + "_booster_pack";
            JsonObject model = json(root.resolve("models/item/" + item + ".json"));
            var overrides = model.getAsJsonArray("overrides");
            var variants = pack.identity().artVariants();
            if (overrides.size() != variants.size()) throw new AssertionError("封面变体与模型覆盖数量不一致: " + pack.setId());
            var reviewed = new HashSet<String>();
            sources.getAsJsonObject(pack.setId()).getAsJsonArray("variants").forEach(v -> {
                JsonObject row = v.getAsJsonObject();
                String url=row.get("url").getAsString();
                String page=row.has("descriptionUrl")?row.get("descriptionUrl").getAsString():"";
                boolean sourceReviewed=url.startsWith("https://archives.bulbagarden.net/")
                    ||url.startsWith("https://fserveu20221222.blob.core.windows.net/files/Pokemon/")&&page.startsWith("https://pokemon.gamespress.com/")
                    ||url.startsWith("https://fservus20221227.blob.core.windows.net/files/PokemonAmerica/")&&page.startsWith("https://press.pokemon.com/en/releases/");
                if (!row.has("sha1") || !row.get("sha1").getAsString().matches("[a-fA-F0-9]{40}") || !sourceReviewed)
                    throw new AssertionError("封面来源缺少 sha1 或不是已核对来源: " + pack.setId());
                reviewed.add(row.get("key").getAsString());
            });
            for (int i = 0; i < variants.size(); i++) {
                var variant = variants.get(i); var override = overrides.get(i).getAsJsonObject();
                if (override.getAsJsonObject("predicate").get("custom_model_data").getAsInt() != variant.customModelData())
                    throw new AssertionError("模型覆盖顺序或编号错误: " + pack.setId());
                String path = "booster_packs/" + pack.setId() + "/" + variant.key();
                if (!override.get("model").getAsString().equals("ptcglive:item/" + path) || !Files.isRegularFile(root.resolve("models/item/" + path + ".json")))
                    throw new AssertionError("封面变体模型缺失: " + path);
                BufferedImage image = ImageIO.read(root.resolve("textures/item/" + path + ".png").toFile());
                if (image == null || image.getWidth() != 128 || image.getHeight() != 128 || !image.getColorModel().hasAlpha())
                    throw new AssertionError("封面贴图必须是 128×128 透明 PNG: " + path);
                if ((image.getRGB(0, 0) >>> 24) != 0) throw new AssertionError("封面贴图四周应为透明: " + path);
                if (!reviewed.contains(variant.key())) throw new AssertionError("封面变体没有来源记录: " + path);
                textures++;
            }
            String key = "item.ptcglive." + item;
            if (!zh.has(key) || !en.has(key) || zh.get(key).getAsString().equals(en.get(key).getAsString()))
                throw new AssertionError("补充包缺少分离的中英文显示名: " + key);
            if (!en.get(key).getAsString().equals(pack.identity().officialEnglishName() + " Booster Pack"))
                throw new AssertionError("英文显示名不是官方英文扩充包名: " + key);
        }
        if (!Files.isRegularFile(root.resolve("models/item/retired_booster_pack.json")) || !Files.isRegularFile(root.resolve("textures/item/retired_booster_pack.png")))
            throw new AssertionError("缺少已停用卡包的模型或贴图");
        try (var files = Files.list(root.resolve("models/item"))) {
            if (files.anyMatch(f -> f.getFileName().toString().matches("[a-z0-9-]+_pack\\.json") && !f.getFileName().toString().endsWith("_booster_pack.json")))
                throw new AssertionError("旧 <setId>_pack 模型仍存在");
        }
        if (Files.exists(root.resolve("textures/item/packs"))) throw new AssertionError("旧封面贴图目录仍存在");
        System.out.println("PackCoverCatalogTest passed: " + textures + " 张美版封面贴图、模型覆盖、来源 sha1 与中英文名称齐全；旧卡包模型已移除");
    }
    private static JsonObject json(Path path) throws Exception { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
}
