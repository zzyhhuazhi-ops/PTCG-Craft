package dev.hua.ptcglive.engine;

import java.util.*;

/**
 * Public faces only. The reveal ID is ephemeral and unrelated to any hidden card instance.
 * titleZh names a non-search reveal (“对手公开手牌 N 张”, actor = the hand's owner); null for a searched-card reveal.
 */
public record EffectCardReveal(UUID id, UUID actor, UUID confirmingPlayer, long step, List<String> cardIds, String titleZh) {
    public EffectCardReveal {
        Objects.requireNonNull(id); Objects.requireNonNull(actor); Objects.requireNonNull(confirmingPlayer);
        cardIds = List.copyOf(cardIds);
        if (cardIds.isEmpty() || cardIds.size() > 60) throw new IllegalArgumentException("展示卡牌数量无效");
    }
    public EffectCardReveal(UUID id, UUID actor, UUID confirmingPlayer, long step, List<String> cardIds) {
        this(id, actor, confirmingPlayer, step, cardIds, null);
    }
}
