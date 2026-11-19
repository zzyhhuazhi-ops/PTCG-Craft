package dev.hua.ptcglive.rules;

import java.time.LocalDate;
import java.util.Objects;

/** A dated edition is not automatically a verified rules implementation. */
public record RulesetEdition(int seasonYear, LocalDate effectiveUntil, Availability availability,
                             boolean casualOnly, String noticeZh) {
    public enum Availability { PLAYABLE_SNAPSHOT, REFERENCE_ONLY }
    public static final RulesetEdition LEGACY = new RulesetEdition(0, null, Availability.PLAYABLE_SNAPSHOT, false, "");
    public RulesetEdition {
        if (seasonYear != 0 && (seasonYear < 1996 || seasonYear > 9999)) throw new IllegalArgumentException("规则档案年份无效");
        Objects.requireNonNull(availability, "规则可用状态");
        noticeZh = Objects.requireNonNull(noticeZh, "规则说明");
        if (availability == Availability.REFERENCE_ONLY && noticeZh.isBlank())
            throw new IllegalArgumentException("待核验规则必须提供不能开局的原因");
    }
    public boolean matchEnabled() { return availability == Availability.PLAYABLE_SNAPSHOT; }
}
