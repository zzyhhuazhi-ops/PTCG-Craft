package dev.hua.ptcglive.rules;

import java.util.Map;
import java.util.Objects;

/** Data-only definition for this mod's custom five-card pack gameplay, not an official product claim. */
public record CardPackDefinition(
    String setId, String displayNameZh, Map<String, Integer> cardWeights,
    Map<String, Integer> rarityWeights, String guaranteeDescriptionZh, String sourceVersion
) {
    public static final int CARDS_PER_PACK = 5;
    public CardPackDefinition {
        if (setId == null || !setId.matches("[a-z0-9-]+")) throw new IllegalArgumentException("卡包 setId 无效");
        if (displayNameZh == null || displayNameZh.isBlank()) throw new IllegalArgumentException("卡包名称为空");
        cardWeights = positive(cardWeights, "卡牌权重");
        rarityWeights = positive(rarityWeights, "稀有度权重");
        Objects.requireNonNull(guaranteeDescriptionZh, "保底说明");
        Objects.requireNonNull(sourceVersion, "数据版本");
    }
    private static Map<String, Integer> positive(Map<String, Integer> source, String label) {
        if (source == null || source.isEmpty() || source.values().stream().anyMatch(v -> v == null || v <= 0))
            throw new IllegalArgumentException(label + "必须存在且全部为正数");
        return Map.copyOf(source);
    }
}
