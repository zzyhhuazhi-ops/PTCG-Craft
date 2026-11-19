package dev.hua.ptcglive.rules;

import dev.hua.ptcglive.engine.field.EnergyType;
import java.time.LocalDate;
import java.util.Objects;

/** Explicit, sourced proxy identity used only by casual regional pools; never edits the original printing. */
public record PracticeCardMapping(String sourceRegion, String targetRegion, String targetPrintingId,
                                  String regulationMark, LocalDate legalFrom, CardCategory category,
                                  EnergyType basicEnergyType, String sourceUrl, String noteZh) {
    public PracticeCardMapping {
        for (String value : new String[]{sourceRegion, targetRegion, targetPrintingId, regulationMark, sourceUrl, noteZh})
            if (value == null || value.isBlank()) throw new IllegalArgumentException("练习卡牌对应记录必须包含身份、来源与说明");
        Objects.requireNonNull(legalFrom); Objects.requireNonNull(category);
        if (!sourceUrl.startsWith("https://")) throw new IllegalArgumentException("对应记录须提供可追溯来源链接");
        if (category == CardCategory.ENERGY && basicEnergyType == null || category != CardCategory.ENERGY && basicEnergyType != null)
            throw new IllegalArgumentException("当前练习对应仅支持普通卡与明确属性的基本能量，不猜测特殊能量等价");
        if (basicEnergyType == EnergyType.DRAGON || basicEnergyType == EnergyType.COLORLESS)
            throw new IllegalArgumentException("不存在该属性的基本能量卡");
    }
}
