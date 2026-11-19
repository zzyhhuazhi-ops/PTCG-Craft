package dev.hua.ptcglive.rules;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Mutable only through CardBoxService; snapshots protect a started match from later edits. */
public final class DeckBox {
    public static final int MAX_CARDS = 60;
    private final UUID id, ownerId;
    private String nameZh, selectedRulesetId;
    private final List<UUID> contents = new ArrayList<>();
    private Instant lastModified;
    private java.util.Map<String, Integer> plannedCards = java.util.Map.of();
    public java.util.Map<String, Integer> plannedCards() { return plannedCards; }
    void plan(java.util.Map<String, Integer> cards, Instant now) {
        if (cards.size() > 60 || cards.entrySet().stream().anyMatch(e -> e.getKey() == null || e.getKey().isBlank()
            || e.getValue() == null || e.getValue() < 1 || e.getValue() > 60)
            || cards.values().stream().mapToInt(Integer::intValue).sum() > 60)
            throw new IllegalArgumentException("无效的卡组缺卡计划");
        plannedCards = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(cards)); lastModified = now;
    }

    DeckBox(UUID id, UUID ownerId, String nameZh, String selectedRulesetId, Instant now) {
        this.id = Objects.requireNonNull(id); this.ownerId = Objects.requireNonNull(ownerId);
        rename(nameZh, now); this.selectedRulesetId = Objects.requireNonNull(selectedRulesetId);
    }
    public UUID id() { return id; }
    public UUID ownerId() { return ownerId; }
    public String nameZh() { return nameZh; }
    public String selectedRulesetId() { return selectedRulesetId; }
    public Instant lastModified() { return lastModified; }
    public List<UUID> contents() { return List.copyOf(contents); }
    void rename(String name, Instant now) {
        if (name == null || name.isBlank() || name.length() > 40) throw new IllegalArgumentException("卡盒名称须为 1 至 40 个字符");
        nameZh = name; lastModified = now;
    }
    void selectRuleset(String id, Instant now) { selectedRulesetId = Objects.requireNonNull(id); lastModified = now; }
    void add(UUID card, Instant now) {
        if (contents.size() >= MAX_CARDS) throw new IllegalStateException("卡盒最多容纳 60 张卡");
        contents.add(card); lastModified = now;
    }
    void remove(UUID card, Instant now) { if (!contents.remove(card)) throw new IllegalArgumentException("卡盒中没有这张卡"); lastModified = now; }
    void sort(java.util.Comparator<UUID> order, Instant now) { contents.sort(order); lastModified = now; }
    static DeckBox restore(UUID id, UUID ownerId, String nameZh, String rulesetId, Instant lastModified, List<UUID> contents) {
        DeckBox box = new DeckBox(id, ownerId, nameZh, rulesetId, lastModified);
        if (contents.size() > MAX_CARDS) throw new IllegalArgumentException("存档中的卡盒超过 60 张");
        box.contents.addAll(contents); box.lastModified = lastModified;
        return box;
    }
}
