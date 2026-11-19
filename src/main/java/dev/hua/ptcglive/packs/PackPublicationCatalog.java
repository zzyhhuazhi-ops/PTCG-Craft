package dev.hua.ptcglive.packs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** Applies the versioned catalog policy to one generated 美版 booster pack identity. */
final class PackPublicationCatalog {
    private static LocalDate simplifiedChineseDebut;
    private PackPublicationCatalog() { }
    static synchronized PackPublication of(BoosterSetIdentity identity) {
        if (simplifiedChineseDebut == null) {
            JsonObject policy = read("/data/ptcglive/packs/pack-catalog-policy-2026-10-04.json");
            if (policy.get("schemaVersion").getAsInt() != 1) throw new IllegalStateException("未知卡包目录策略版本");
            simplifiedChineseDebut = LocalDate.parse(policy.get("simplifiedChineseOfficialDebut").getAsString());
        }
        String date = identity.releaseDate();
        PackPublication.CatalogRoute route;
        String notice;
        if (!date.isBlank() && !LocalDate.parse(date).isBefore(simplifiedChineseDebut)) {
            route = PackPublication.CatalogRoute.SIMPLIFIED_CHINESE_MAPPING_REQUIRED;
            notice = "按目录策略，此美版扩充包晚于简中正式发行。当前稳定卡牌 ID 仍为英文版编号、名称为中文显示；尚未导入逐张简中印刷编号映射，不能标称为简中印刷卡池。";
        } else {
            route = PackPublication.CatalogRoute.ENGLISH_TRANSLATED;
            notice = "按美版扩充包的发行分类抽取；卡牌使用模组已核对的中文显示资料，不按中国大陆独立产品线重新分包。";
        }
        return new PackPublication(route, identity.officialEnglishName(), identity.eraEnglishName(), date,
            identity.artVariants().isEmpty() ? "MISSING_ART" : "US_BOOSTER_ART_REVIEWED", "", notice);
    }
    private static JsonObject read(String resource) {
        var stream = PackPublicationCatalog.class.getResourceAsStream(resource);
        if (stream == null) throw new IllegalStateException("缺少卡包发行资料: " + resource);
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception error) { throw new IllegalStateException("无法读取卡包发行资料: " + resource, error); }
    }
}
