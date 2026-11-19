package dev.hua.ptcglive.packs;

import java.util.List;
import java.util.Objects;

/**
 * Real-world 美版 (US English) expansion behind one booster pack.  The pack key
 * ({@code setId}) is a stable lower-case slug; card IDs keep their own PTCG Live
 * set prefixes listed in {@code sourceSetIds}.  Display names are data, never
 * parsed back into identity.
 */
public record BoosterSetIdentity(String setCode, String officialEnglishName, String officialEnglishFullName,
                                 String chineseName, String displayNameEn, String releaseDate,
                                 String era, String eraEnglishName, String eraChineseName,
                                 List<String> sourceSetIds, int cardCount, Integer officialCardsPerPack,
                                 String collationProfile, List<ArtVariant> artVariants) {
    /** One printed pack design; {@code customModelData} selects its item model override. */
    public record ArtVariant(String key, String label, int customModelData) {
        public ArtVariant {
            if (key == null || !key.matches("[a-z0-9_]+")) throw new IllegalArgumentException("封面变体键无效");
            if (customModelData < 1) throw new IllegalArgumentException("封面变体模型编号无效");
        }
    }

    public BoosterSetIdentity {
        setCode = safe(setCode); officialEnglishName = safe(officialEnglishName); officialEnglishFullName = safe(officialEnglishFullName);
        chineseName = safe(chineseName); displayNameEn = safe(displayNameEn); releaseDate = safe(releaseDate);
        era = safe(era); eraEnglishName = safe(eraEnglishName); eraChineseName = safe(eraChineseName);
        collationProfile = safe(collationProfile);
        sourceSetIds = List.copyOf(Objects.requireNonNull(sourceSetIds, "来源系列"));
        artVariants = List.copyOf(Objects.requireNonNull(artVariants, "封面变体"));
    }

    /** Identity for hand-built test packs that do not correspond to a real expansion. */
    public static BoosterSetIdentity unreviewed(String setId) {
        return new BoosterSetIdentity("", "", "", "", "", "", "", "", "", List.of(setId), 0, null, "", List.of());
    }

    public ArtVariant variant(int customModelData) {
        for (ArtVariant variant : artVariants) if (variant.customModelData() == customModelData) return variant;
        return artVariants.isEmpty() ? null : artVariants.getFirst();
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
