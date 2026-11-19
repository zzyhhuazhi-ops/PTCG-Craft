import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import javax.imageio.ImageIO;

/** Tests exact native grids, alpha, palette, model lookup and translated presentation. */
public final class CardEncyclopediaResourcesTest {
    static byte[] resource(String path) throws Exception {
        try (var input = CardEncyclopediaResourcesTest.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new AssertionError("Missing " + path);
            return input.readAllBytes();
        }
    }
    static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        for (int size : new int[]{16,32,64}) {
            var data = resource("assets/ptcglive/textures/item/card_encyclopedia_" + size + ".png");
            var image = ImageIO.read(new java.io.ByteArrayInputStream(data));
            require(image != null && image.getWidth() == size && image.getHeight() == size, "Wrong dimensions");
            var colors = new HashSet<Integer>(); int transparent = 0; int opaque = 0;
            for (int y=0;y<size;y++) for (int x=0;x<size;x++) {
                int color=image.getRGB(x,y), alpha=color>>>24;
                require(alpha==0 || alpha==255, "Antialiased pixel found");
                if(alpha==0) transparent++; else { opaque++; colors.add(color); }
            }
            require(transparent>size && opaque>size*size/3, "Missing transparent background or book silhouette");
            require(colors.size()>=7 && colors.size()<=11, "Unexpected pixel palette");
            require((image.getRGB(0,0)>>>24)==0, "Background is not transparent");
            require(colors.contains(0xffaf3934) && colors.contains(0xffd9b75f), "Missing ball or gold details");
        }
        var model=JsonParser.parseString(new String(resource("assets/ptcglive/models/item/card_encyclopedia.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        require(model.get("parent").getAsString().equals("minecraft:item/generated"), "Wrong item model");
        require(model.getAsJsonObject("textures").get("layer0").getAsString().equals("ptcglive:item/card_encyclopedia_32"), "Main texture must be 32px");
        for(String locale:new String[]{"zh_cn","en_us"}) {
            var lang=JsonParser.parseString(new String(resource("assets/ptcglive/lang/"+locale+".json"), StandardCharsets.UTF_8)).getAsJsonObject();
            for(String key:new String[]{"item.ptcglive.card_encyclopedia","tooltip.ptcglive.card_encyclopedia.description"})
                require(lang.has(key) && !lang.get(key).getAsString().isBlank(), "Missing translation "+key);
            if(locale.equals("zh_cn")) require(lang.get("tooltip.ptcglive.card_encyclopedia.description").getAsString().equals("收录每一张卡牌，记录属于你的宝可梦旅程。"),"Description changed");
        }
        System.out.println("CardEncyclopediaResourcesTest passed: 16/32/64 native pixels, binary alpha, palette, model and translations");
    }
}
