package dev.hua.ptcglive.engine.tournament;

import java.util.Objects;

/** Immutable, versionable server configuration for one tournament match. */
public record TournamentSettings(
    String id,
    String displayNameZh,
    TournamentMatchFormat format,
    int timeLimitMinutes,
    boolean requiresDeclaredWinner,
    TiebreakerPolicy tiebreakerPolicy
) {
    public TournamentSettings {
        if (id == null || !id.matches("[a-z0-9-]+")) throw new IllegalArgumentException("赛事设置 ID 无效");
        if (displayNameZh == null || displayNameZh.isBlank()) throw new IllegalArgumentException("赛事设置名称为空");
        Objects.requireNonNull(format, "对局赛制");
        if (timeLimitMinutes < 1 || timeLimitMinutes > 600) throw new IllegalArgumentException("限时必须在 1 至 600 分钟之间");
        Objects.requireNonNull(tiebreakerPolicy, "决胜局规则");
    }
}
