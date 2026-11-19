package dev.hua.ptcglive.table;

import java.util.Objects;

/** Stable world/dimension/position identity. There is intentionally no global cap on table keys. */
public record CardTableKey(String dimensionId, long packedBlockPos) {
    public CardTableKey { Objects.requireNonNull(dimensionId, "维度 ID"); }
    @Override public String toString() { return dimensionId + "@" + packedBlockPos; }
}
