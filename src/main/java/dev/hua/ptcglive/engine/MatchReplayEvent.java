package dev.hua.ptcglive.engine;

import java.util.Objects;
import java.util.UUID;

/**
 * Server-only public-fact action transcript. It deliberately contains no
 * card instance, deck order, hand identity or Prize identity; those remain in
 * the authoritative match snapshot and are never sent through MatchClientView.
 */
public record MatchReplayEvent(long sequence, UUID actor, String operation, int randomEventsThrough) {
    public MatchReplayEvent {
        if (sequence < 1 || randomEventsThrough < 0) throw new IllegalArgumentException("回放事件序号无效");
        Objects.requireNonNull(actor, "回放动作玩家");
        if (operation == null || operation.isBlank()) throw new IllegalArgumentException("回放动作说明为空");
    }
}
