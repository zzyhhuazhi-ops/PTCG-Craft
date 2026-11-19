package dev.hua.ptcglive.packs;

/** One configured opening slot. Its pool is also the only source used by the probability display. */
public record PackSlot(String poolId, int count, String displayNameZh) {
    public PackSlot {
        if (poolId == null || poolId.isBlank() || count < 1) throw new IllegalArgumentException("卡包栏位无效");
        if (displayNameZh == null || displayNameZh.isBlank()) throw new IllegalArgumentException("卡包栏位名称为空");
    }
}
