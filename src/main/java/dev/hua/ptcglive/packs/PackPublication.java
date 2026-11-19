package dev.hua.ptcglive.packs;

import java.util.Objects;

/**
 * Public provenance for a booster pack.  The set classification and the printed
 * card edition are deliberately separate: an English expansion can have a
 * later Simplified-Chinese printing, but it must never be silently treated as
 * the same numbered product until a card-by-card mapping has been reviewed.
 */
public record PackPublication(CatalogRoute catalogRoute, String englishSetName, String englishSeries,
                              String englishReleaseDate, String coverStatus, String coverSourceUrl,
                              String noticeZh) {
    public enum CatalogRoute {
        ENGLISH_TRANSLATED("美版扩充包分类／中文显示资料"),
        SIMPLIFIED_CHINESE_MAPPING_REQUIRED("简中发行后：待逐卡印刷映射"),
        MOD_CUSTOM("PTCG Craft 自定义收藏卡包");
        private final String labelZh;
        CatalogRoute(String labelZh) { this.labelZh = labelZh; }
        public String labelZh() { return labelZh; }
    }
    public PackPublication {
        Objects.requireNonNull(catalogRoute, "卡包目录路线");
        englishSetName = safe(englishSetName); englishSeries = safe(englishSeries); englishReleaseDate = safe(englishReleaseDate);
        coverStatus = safe(coverStatus); coverSourceUrl = safe(coverSourceUrl); noticeZh = safe(noticeZh);
    }
    public static PackPublication unreviewed() {
        return new PackPublication(CatalogRoute.ENGLISH_TRANSLATED, "", "", "", "UNREVIEWED", "",
            "卡包发行资料尚未审核；当前仅可作为模组自定义抽取配置使用。");
    }
    private static String safe(String value) { return value == null ? "" : value; }
}
