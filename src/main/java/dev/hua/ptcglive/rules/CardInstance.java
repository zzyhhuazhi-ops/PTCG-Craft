package dev.hua.ptcglive.rules;

import java.util.Objects;
import java.util.UUID;

/** One owned physical/digital card identity. A UUID can occupy no more than one card box. */
public record CardInstance(UUID instanceId, UUID ownerId, String definitionId) {
    public CardInstance {
        Objects.requireNonNull(instanceId, "卡牌实例 ID");
        Objects.requireNonNull(ownerId, "所有者 ID");
        if (definitionId == null || definitionId.isBlank()) throw new IllegalArgumentException("卡牌定义 ID 为空");
    }
}
