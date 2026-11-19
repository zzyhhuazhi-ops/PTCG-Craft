import com.google.gson.JsonParser;
import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.client.CardArtResources;
import dev.hua.ptcglive.client.CardItemRenderer;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Runs the real renderer's sprite selection without opening Minecraft or a GL context. */
public final class CardItemArtRoutingTest {
    public static void main(String[] args) throws Exception {
        ResourceManager resources = resources(Map.of());
        CardArtResources.invalidate();
        var reported = CardItemRenderer.atlasSprite(resources, "sd100_cn_028");
        check(reported.toString().equals("ptcglive_mc:item/cards/swsh7_en_013"),
            "用户截图 SD100 028 的实际渲染器仍请求不存在的自身 PNG：" + reported);
        int starters = 0;
        for (var card : CardCatalog.load()) {
            if (!card.set().equals("sd100")) continue;
            var sprite = CardItemRenderer.atlasSprite(resources, card.id());
            for (String namespace : List.of("ptcglive", "ptcglive_mc")) {
                String path = "assets/" + namespace + "/models/item/" + card.id() + ".json";
                try (var stream = loader().getResourceAsStream(path)) {
                    check(stream != null, "缺少模型 " + path);
                    var model = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                    check(model.getAsJsonObject("textures").get("layer0").getAsString().equals(sprite.toString()),
                        "动态渲染与 JSON 模型没有使用同一图集精灵：" + card.id());
                }
            }
            String png = "assets/" + sprite.getNamespace() + "/textures/" + sprite.getPath() + ".png";
            try (var stream = loader().getResourceAsStream(png)) {
                check(stream != null, "实际渲染路径缺少 PNG：" + png);
                check(Arrays.equals(stream.readNBytes(8), new byte[]{(byte)137,80,78,71,13,10,26,10}), "图集纹理不是 PNG：" + png);
            }
            var fallback = CardArtResources.thumbnail(resources, card.id());
            check(resources.getResource(fallback).isPresent(), "预组缩略图回退不存在：" + card.id());
            starters++;
        }
        check(starters == 438, "必须检查全部 438 张起始卡牌");
        check(CardItemRenderer.atlasSprite(resources, "swsh7_en_013").toString().equals("ptcglive_mc:item/cards/swsh7_en_013"),
            "普通卡路径被改坏");
        // F3+T / resource-pack changes must not retain the previous alias target.
        var override = resources(Map.of("ptcglive_mc:card_art_aliases.json",
            "{\"aliases\":{\"sd100_cn_028\":\"sve_en_001\"}}"));
        CardArtResources.invalidate();
        check(CardItemRenderer.atlasSprite(override, "sd100_cn_028").toString().equals("ptcglive_mc:item/cards/sve_en_001"),
            "资源重载后仍使用旧别名");
        CardArtResources.invalidate();
        check(CardItemRenderer.atlasSprite(resources, "sd100_cn_028").equals(reported), "资源重载无法恢复原始别名");
        var hd = resources(Map.of("ptcglive_mc:card_art/swsh7_en_013.jpg", "fixture",
            "ptcglive_mc:card_art/sd100_cn_028.jpg", "!missing"));
        check(CardArtResources.select(hd, "sd100_cn_028").highDefinition(), "展示柜别名卡未选择已安装的高清资源");
        check(CardArtResources.select(hd, "sd100_cn_028").resource().toString().equals("ptcglive_mc:card_art/swsh7_en_013.jpg"),
            "展示柜高清资源路径错误");
        CardArtResources.invalidate();
        var noHd = resources(Map.of("ptcglive_mc:card_art/swsh7_en_013.jpg", "!missing",
            "ptcglive_mc:card_art/sd100_cn_028.jpg", "!missing"));
        check(!CardArtResources.select(noHd, "sd100_cn_028").highDefinition(), "卸载资源包后未回退缩略图");
        for (var facing : List.of(net.minecraft.util.math.Direction.NORTH, net.minecraft.util.math.Direction.EAST,
                net.minecraft.util.math.Direction.SOUTH, net.minecraft.util.math.Direction.WEST)) {
            var matrices = new net.minecraft.client.util.math.MatrixStack();
            dev.hua.ptcglive.client.CardDisplayCaseBlockEntityRenderer.applyPlacement(matrices, facing);
            var matrix = matrices.peek().getPositionMatrix();
            var bottom = matrix.transformPosition(new org.joml.Vector3f(0.5f, 0, 0.5f));
            var top = matrix.transformPosition(new org.joml.Vector3f(0.5f, 1, 0.5f));
            var left = matrix.transformPosition(new org.joml.Vector3f(0, 0, 0.5f));
            var right = matrix.transformPosition(new org.joml.Vector3f(1, 0, 0.5f));
            check(Math.abs(bottom.x - (0.5f - 0.1f * facing.getOffsetX())) < 0.00001f
                && Math.abs(bottom.z - (0.5f - 0.1f * facing.getOffsetZ())) < 0.00001f
                && Math.abs(bottom.y - 1.1875f) < 0.00001f, "卡牌没有对齐旋转后的支架 " + facing);
            check(Math.abs(top.distance(bottom) - 0.80f) < 0.00001f
                && Math.abs(right.distance(left) - 0.58f) < 0.00001f, "展示卡大小改变 " + facing);
        }
        System.out.println("CardItemArtRoutingTest passed: all 438 SD100 runtime sprites match models and PNGs; reload and normal cards preserved");
    }
    private static ClassLoader loader() { return CardItemArtRoutingTest.class.getClassLoader(); }
    private static ResourceManager resources(Map<String,String> overrides) {
        return (ResourceManager) Proxy.newProxyInstance(loader(), new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
            if (!method.getName().equals("getResource")) throw new UnsupportedOperationException(method.getName());
            Identifier id = (Identifier) args[0];
            String override = overrides.get(id.toString());
            if ("!missing".equals(override)) return Optional.empty();
            if (override != null) return Optional.of(new Resource(null,
                () -> new ByteArrayInputStream(override.getBytes(StandardCharsets.UTF_8))));
            String path = "assets/" + id.getNamespace() + "/" + id.getPath();
            return loader().getResource(path) == null ? Optional.empty()
                : Optional.of(new Resource(null, () -> loader().getResourceAsStream(path)));
        });
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
