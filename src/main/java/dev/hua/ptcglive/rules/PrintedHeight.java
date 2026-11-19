package dev.hua.ptcglive.rules;

import java.util.Objects;

/** Exact text value of a specific printing, never a species-database measurement. */
public record PrintedHeight(Unit unit, int value) {
    public enum Unit { INCH, CM }
    public PrintedHeight {
        Objects.requireNonNull(unit, "印刷身高单位");
        if (value <= 0) throw new IllegalArgumentException("印刷身高必须为正数");
    }
    public String key() { return unit.name() + ":" + value; }
    public String labelZh() {
        return unit == Unit.INCH ? (value / 12) + "英尺" + (value % 12) + "英寸"
            : java.math.BigDecimal.valueOf(value, 2).stripTrailingZeros().toPlainString() + "米";
    }
}
