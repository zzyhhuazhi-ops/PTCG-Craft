import dev.hua.ptcglive.CardLabel;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class PresentationResourcesTest {
    public static void main(String[] args) throws Exception {
        checkRecipe("card_table", List.of("PPP", "S S", "S S"), java.util.Map.of("P", "tag:minecraft:planks", "S", "item:minecraft:stick"));
        checkRecipe("card_display_case", List.of(" I ", " D ", " S "), java.util.Map.of("I", "item:minecraft:iron_ingot", "D", "item:minecraft:deepslate", "S", "item:minecraft:smooth_stone"));
        checkRecipe("card_recycling_station", List.of("CCC", "CBC", "CCC"), java.util.Map.of("C", "item:minecraft:cobblestone", "B", "item:ptcglive:card_box"));
        checkRecipe("phone", List.of("CGC", "GRG", "CCC"), java.util.Map.of("C", "item:minecraft:copper_ingot", "G", "item:minecraft:glass_pane", "R", "item:minecraft:redstone"));
        checkRecipe("card_box", List.of("   ", "PBP", "   "), java.util.Map.of("P", "tag:minecraft:planks", "B", "item:minecraft:book"));
        try (var input = PresentationResourcesTest.class.getClassLoader().getResourceAsStream("data/ptcglive/recipe/card_box_dye.json")) {
            check(input != null, "missing card-box dye recipe");
            var dyeRecipe = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            check(dyeRecipe.get("type").getAsString().equals("minecraft:crafting_special_armordye"), "card box must use the vanilla dye recipe");
        }
        for (var id : List.of("card_table", "card_display_case", "card_recycling_station", "phone", "card_box")) {
            try (var input = PresentationResourcesTest.class.getClassLoader().getResourceAsStream("data/ptcglive/advancement/recipes/" + id + ".json")) {
                check(input != null, "recipe book discovery missing");
                var unlock = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                check(unlock.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString().equals("ptcglive:" + id), "recipe book reward mismatch");
            }
        }
        try (var stream = PresentationResourcesTest.class.getClassLoader().getResourceAsStream("assets/ptcglive_mc/lang/zh_cn.json")) {
            var lang=JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            check(lang.get("block.ptcglive.card_display_case").getAsString().equals("卡牌展示柜"),"展示柜中文名缺失");
            check(lang.get("block.ptcglive.card_recycling_station").getAsString().equals("卡牌回收站"),"回收站中文名缺失");
        }
        check(CardLabel.format("搬运小匠 · ZSV10-5 047 · 普通").equals("搬运小匠 C ZSV10-5 047"), "requested name order");
        check(CardLabel.format("喷火龙ex · SV3 125 · 双重稀有").equals("喷火龙ex RR SV3 125"), "ex title lost");
        check(CardLabel.format("训练家 · SV1 201 · 超稀有").equals("训练家 SR SV1 201"), "SR abbreviation");
        check(CardLabel.format("未知 · BW 001 · 未分类").contains("?"), "unknown rarity fabricated");
        for (String rarity : new String[]{"C", "U", "R", "RR", "SR", "SAR", "PROMO"})
            check(CardLabel.format("基本草能量 · XY9-5 075 · " + rarity).equals("基本草能量 " + rarity + " XY9-5 075"), "existing rarity code lost: " + rarity);
        check(dev.hua.ptcglive.CardNameReviews.apply("zsv10-5_en_079", "Air Balloon · ZSV10-5 079 · 非凡").equals("气球 · ZSV10-5 079 · 非凡"), "reviewed Chinese title not applied");
        check(dev.hua.ptcglive.CardNameReviews.apply("unreviewed", "甲 · SET 001 · 稀有").equals("甲 · SET 001 · 稀有"), "unreviewed title fabricated");
        for (String path : List.of("assets/ptcglive/blockstates/card_table.json", "assets/ptcglive/models/block/card_table.json",
            "assets/ptcglive/models/item/card_table.json", "assets/ptcglive/models/item/phone.json", "assets/ptcglive/models/item/card_box.json",
            "assets/ptcglive/blockstates/card_recycling_station.json", "assets/ptcglive/models/block/card_recycling_station.json", "assets/ptcglive/models/item/card_recycling_station.json", "data/ptcglive/loot_table/blocks/card_recycling_station.json",
            "assets/ptcglive/blockstates/card_display_case.json", "assets/ptcglive/models/block/card_display_case_lower.json",
            "assets/ptcglive/models/block/card_display_case_upper.json", "assets/ptcglive/models/item/card_display_case.json",
            "data/ptcglive/loot_table/blocks/card_table.json", "data/ptcglive/loot_table/blocks/card_display_case.json",
            "data/ptcglive/recipe/card_table.json", "data/ptcglive/recipe/card_display_case.json", "data/ptcglive/recipe/phone.json")) {
            try (var stream = PresentationResourcesTest.class.getClassLoader().getResourceAsStream(path)) {
                check(stream != null, "missing " + path); JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        for (String path : List.of("assets/ptcglive/textures/item/card_box_tint.png", "assets/ptcglive/textures/item/card_box_base.png")) {
            try (var stream = PresentationResourcesTest.class.getClassLoader().getResourceAsStream(path)) {
                check(stream != null && stream.readAllBytes().length > 64, "missing or empty " + path);
            }
        }
        try (var stream = PresentationResourcesTest.class.getClassLoader().getResourceAsStream("data/minecraft/tags/item/dyeable.json")) {
            check(stream != null, "card box is not registered as dyeable");
            var tag = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            check(tag.getAsJsonArray("values").asList().stream().anyMatch(entry -> entry.getAsString().equals("ptcglive:card_box")), "card box missing from dyeable tag");
        }
        System.out.println("PresentationResourcesTest passed: labels, rarity fallback, table/phone assets and recipes, dyeable card box");
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void checkRecipe(String id, List<String> rows, java.util.Map<String, String> ingredients) throws Exception {
        try (var input = PresentationResourcesTest.class.getClassLoader().getResourceAsStream("data/ptcglive/recipe/" + id + ".json")) {
            check(input != null, "missing recipe: " + id);
            var recipe = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            check(recipe.get("type").getAsString().equals("minecraft:crafting_shaped"), "recipe type");
            check(recipe.getAsJsonArray("pattern").asList().stream().map(e -> e.getAsString()).toList().equals(rows), "requested recipe layout: " + id);
            var key = recipe.getAsJsonObject("key"); check(key.size() == ingredients.size(), "extra recipe ingredients");
            for (var e : ingredients.entrySet()) {
                var field = e.getValue().split(":", 2); var value = key.getAsJsonObject(e.getKey());
                check(value.size() == 1 && value.get(field[0]).getAsString().equals(field[1]), "incorrect ingredient: " + id + "/" + e.getKey());
            }
            check(recipe.getAsJsonObject("result").get("id").getAsString().equals("ptcglive:" + id), "incorrect output");
            check(recipe.getAsJsonObject("result").get("count").getAsInt() == 1, "incorrect output count");
        }
    }
}
