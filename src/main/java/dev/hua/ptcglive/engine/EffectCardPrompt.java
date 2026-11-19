package dev.hua.ptcglive.engine;

import dev.hua.ptcglive.rules.CardInstance;
import java.util.*;

/** Recipient-private presentation of one server-held choice; candidate order is never deck order. */
public record EffectCardPrompt(UUID resolutionId, long step, String labelZh, int minimum, int maximum,
                               List<CardInstance> candidates, List<UUID> selected, List<CardInstance> inspectedCards) {
    public EffectCardPrompt(UUID resolutionId, long step, String labelZh, int minimum, int maximum,
                            List<CardInstance> candidates, List<UUID> selected) {
        this(resolutionId, step, labelZh, minimum, maximum, candidates, selected, List.of());
    }
    public EffectCardPrompt {
        candidates = List.copyOf(candidates); selected = List.copyOf(selected); inspectedCards = List.copyOf(inspectedCards);
    }
    /** Looking at the top N also shows ineligible cards, only to the entitled player. */
    public List<CardInstance> visibleCards() { return inspectedCards.isEmpty() ? candidates : inspectedCards; }
}
