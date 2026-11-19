package dev.hua.ptcglive.client;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/** Read-only, resource-pack-overridable handbook content. Recipes come from the world. */
final class FieldGuideData {
    record Page(String text, List<String> items, String recipe) { }
    record Entry(String id, String section, String title, String icon, List<Page> pages, List<String> links) { }
    record Book(List<Entry> entries) { }
    static Book load(ResourceManager resources) {
        try (var reader = new InputStreamReader(resources.getResource(Identifier.of("ptcglive", "guide/zh_cn.json"))
                .orElseThrow().getInputStream(), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, Book.class);
        } catch (Exception failure) {
            org.slf4j.LoggerFactory.getLogger("ptcglive-guide").error("Cannot load handbook", failure);
            return new Book(List.of(new Entry("error", "手册", "内容载入失败", "minecraft:book",
                List.of(new Page("请检查资源包中的 ptcglive/guide/zh_cn.json，然后重新打开手册。", List.of(), "")), List.of())));
        }
    }
}
