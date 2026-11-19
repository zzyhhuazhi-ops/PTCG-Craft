package dev.hua.ptcglive.engine.effect;

/** Explicit printed Rule Box filter for a card-selection specification. */
public enum RuleBoxFilter {
    ANY, HAS, NONE;

    public static RuleBoxFilter fromWire(String value) {
        if (value == null || value.isBlank()) return ANY;
        try { return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("rule_box 必须为 ANY、HAS 或 NONE", invalid); }
    }
}
