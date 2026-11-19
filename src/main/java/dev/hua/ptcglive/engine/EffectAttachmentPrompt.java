package dev.hua.ptcglive.engine;

import dev.hua.ptcglive.rules.CardInstance;
import java.util.*;

/** Owner-only selected cards and current public targets; no deck order or executable effect data. */
public record EffectAttachmentPrompt(UUID resolutionId, long step, List<CardInstance> cards, List<UUID> targets, String actionZh,
                                     Map<UUID, UUID> assignedTargets) {
    public EffectAttachmentPrompt {
        cards = List.copyOf(cards); targets = List.copyOf(targets);
        actionZh = actionZh == null || actionZh.isBlank() ? "附能" : actionZh;
        assignedTargets = Map.copyOf(assignedTargets == null ? Map.of() : assignedTargets);
    }
    public EffectAttachmentPrompt(UUID resolutionId, long step, List<CardInstance> cards, List<UUID> targets, String actionZh) {
        this(resolutionId, step, cards, targets, actionZh, Map.of());
    }
    public EffectAttachmentPrompt(UUID resolutionId, long step, List<CardInstance> cards, List<UUID> targets) {
        this(resolutionId, step, cards, targets, "附能", Map.of());
    }
}
