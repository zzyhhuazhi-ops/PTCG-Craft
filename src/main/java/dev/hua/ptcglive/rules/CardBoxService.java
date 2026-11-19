package dev.hua.ptcglive.rules;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** In-memory authoritative ownership service; persistence adapters are added without changing these invariants. */
public final class CardBoxService {
    public record SavedCard(UUID instanceId, UUID ownerId, String definitionId, UUID boxId, String physicalData) {
        public SavedCard(UUID instanceId, UUID ownerId, String definitionId, UUID boxId) {this(instanceId,ownerId,definitionId,boxId,"");}
    }
    public record SavedBox(UUID id, UUID ownerId, String nameZh, String selectedRulesetId, java.time.Instant lastModified, List<UUID> contents, Map<String, Integer> plannedCards) {
        public SavedBox(UUID id, UUID ownerId, String nameZh, String selectedRulesetId, java.time.Instant lastModified, List<UUID> contents) {
            this(id, ownerId, nameZh, selectedRulesetId, lastModified, contents, Map.of());
        }
    }
    public record Discovery(UUID owner, String definitionId) { }
    public record CardHistory(UUID owner, String definitionId, long firstAcquired, long matches, long wins, long knockouts,
                              long completedMatches,long completedWins,boolean legacyResultsUnknown) {
        public CardHistory(UUID owner,String definitionId,long firstAcquired,long matches,long wins,long knockouts){
            this(owner,definitionId,firstAcquired,matches,wins,knockouts,0,0,matches>0||wins>0);
        }
        public String winRate(){return completedMatches==0?"—":String.format(java.util.Locale.ROOT,"%.1f%%",completedWins*100.0/completedMatches);}
    }
    public record Snapshot(List<SavedCard> cards, List<SavedBox> boxes, List<Discovery> discoveries, List<CardHistory> histories) {
        public Snapshot(List<SavedCard> cards,List<SavedBox> boxes,List<Discovery> discoveries){this(cards,boxes,discoveries,List.of());}
        public Snapshot(List<SavedCard> cards, List<SavedBox> boxes) { this(cards, boxes, List.of()); }
    }
    private final Clock clock;
    private final Map<UUID, CardInstance> cards = new HashMap<>();
    private final Map<UUID, DeckBox> boxes = new HashMap<>();
    private final Map<UUID, String> physicalData = new HashMap<>();
    private final Map<UUID, UUID> cardLocation = new HashMap<>();
    private final Map<UUID, UUID> boxLocks = new HashMap<>();
    private final Map<Discovery,CardHistory> histories=new HashMap<>();
    private final java.util.Set<Discovery> discoveries = new java.util.HashSet<>();
    public synchronized void observe(UUID owner, java.util.Collection<String> ids) {
        boolean modified=false;
        for(String id:ids) {acquired(owner,id,false);modified |= discoveries.add(new Discovery(owner,id));}
        if(modified) changed();
    }
    /** Missing legacy timestamps remain unknown; never infer an old acquisition date. */
    public synchronized void acquired(UUID owner,String id,boolean legacyUnknown) {
        var key=new Discovery(owner,id);
        if(histories.containsKey(key))return;
        histories.put(key,new CardHistory(owner,id,legacyUnknown||discoveries.contains(key)?0:clock.millis(),0,0,0));changed();
    }
    public synchronized CardHistory history(UUID owner,String id){return histories.get(new Discovery(owner,id));}
    public synchronized void addHistory(UUID owner,String id,long matches,long wins,long knockouts){
        if(matches<0||wins<0||knockouts<0)throw new IllegalArgumentException("negative history delta");
        acquired(owner,id,true);var key=new Discovery(owner,id);var h=histories.get(key);
        histories.put(key,new CardHistory(owner,id,h.firstAcquired(),saturated(h.matches(),matches),saturated(h.wins(),wins),saturated(h.knockouts(),knockouts),h.completedMatches(),h.completedWins(),h.legacyResultsUnknown()));changed();
    }
    /** Called once per finished match; interrupted matches never enter the win-rate denominator. */
    public synchronized void completeHistory(UUID owner,String id,boolean won){
        acquired(owner,id,true);var key=new Discovery(owner,id);var h=histories.get(key);
        histories.put(key,new CardHistory(owner,id,h.firstAcquired(),h.matches(),saturated(h.wins(),won?1:0),h.knockouts(),
            saturated(h.completedMatches(),1),saturated(h.completedWins(),won?1:0),h.legacyResultsUnknown()));changed();
    }
    private static long saturated(long a,long b){return a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b;}
    public synchronized boolean discovered(UUID owner, String id) { return discoveries.contains(new Discovery(owner,id)); }
    private Runnable changeListener = () -> { };

    public CardBoxService(Clock clock) { this.clock = clock; }
    /** Commit one compound move or restore the ledger, including box locations, on failure. */
    public synchronized <T> T transaction(java.util.function.Supplier<T> operation) {
        return transaction(operation, () -> { });
    }
    /** Last commit must itself be atomic (e.g. opening audit); no fallible work follows it. */
    public synchronized <T> T transaction(java.util.function.Supplier<T> operation, Runnable finalCommit) {
        Snapshot before = snapshot();
        var locks = new HashMap<>(boxLocks);
        Runnable listener = changeListener;
        changeListener = () -> { };
        try {
            T value = operation.get();
            listener.run();
            finalCommit.run();
            return value;
        } catch (RuntimeException failure) {
            CardBoxService restored = restore(clock, before);
            cards.clear(); cards.putAll(restored.cards); physicalData.clear(); physicalData.putAll(restored.physicalData);
            boxes.clear(); boxes.putAll(restored.boxes);
            cardLocation.clear(); cardLocation.putAll(restored.cardLocation);
            discoveries.clear(); discoveries.addAll(restored.discoveries);histories.clear();histories.putAll(restored.histories);
            boxLocks.clear(); boxLocks.putAll(locks);
            throw failure;
        } finally { changeListener = listener; }
    }
    /** Format 1–3 unboxed records were shadow ownership, not a verified physical deposit. */
    public static Snapshot retireLegacyUnboxed(Snapshot old) {
        var boxed = old.cards().stream().filter(card -> card.boxId() != null).toList();
        return new Snapshot(boxed, old.boxes(), old.discoveries(),old.histories());
    }
    public synchronized CardInstance mint(UUID ownerId, String definitionId) {
        var card = new CardInstance(UUID.randomUUID(), ownerId, definitionId); cards.put(card.instanceId(), card);
        acquired(ownerId,definitionId,false);discoveries.add(new Discovery(ownerId,definitionId)); changed(); return card;
    }
    /** Transaction rollback hook: only cards just minted for this owner and not placed in a box can be revoked. */
    public synchronized void revokeUnboxed(UUID ownerId, List<CardInstance> minted) {
        for (CardInstance card : minted) {
            CardInstance stored = requireCard(card.instanceId());
            if (!stored.ownerId().equals(ownerId) || cardLocation.containsKey(card.instanceId()))
                throw new IllegalStateException("不能回滚已经放入卡盒或不属于该玩家的卡牌");
        }
        for (CardInstance card : minted) {cards.remove(card.instanceId()); physicalData.remove(card.instanceId());} changed();
    }
    public synchronized DeckBox create(UUID ownerId, String nameZh, String rulesetId) {
        var box = new DeckBox(UUID.randomUUID(), ownerId, nameZh, rulesetId, clock.instant()); boxes.put(box.id(), box); changed(); return box;
    }
    /** Atomically mints and locks a fixed deck list into one owner-only card box. */
    public synchronized DeckBox createPopulated(UUID ownerId, String nameZh, String rulesetId, List<String> definitionIds) {
        if (definitionIds.isEmpty() || definitionIds.size() > 60) throw new IllegalArgumentException("卡盒内容必须为 1–60 张卡牌");
        var box = new DeckBox(UUID.randomUUID(), ownerId, nameZh, rulesetId, clock.instant());
        List<CardInstance> minted = new ArrayList<>();
        try {
            for (String definitionId : definitionIds) {
                if (definitionId == null || definitionId.isBlank()) throw new IllegalArgumentException("卡牌定义 ID 不能为空");
                var card = new CardInstance(UUID.randomUUID(), ownerId, definitionId);
                cards.put(card.instanceId(), card); minted.add(card);
                box.add(card.instanceId(), clock.instant()); cardLocation.put(card.instanceId(), box.id());
            }
            boxes.put(box.id(), box);
            for(String id:definitionIds) {acquired(ownerId,id,false);discoveries.add(new Discovery(ownerId,id));}
            changed(); return box;
        } catch (RuntimeException failure) {
            for (CardInstance card : minted) { cards.remove(card.instanceId()); cardLocation.remove(card.instanceId()); }
            throw failure;
        }
    }
    public synchronized void put(UUID ownerId, UUID boxId, UUID cardId) {
        var box = ownedBox(ownerId, boxId); ensureUnlocked(boxId);
        var card = requireCard(cardId);
        if (!card.ownerId().equals(ownerId)) throw new IllegalStateException("不能放入不属于你的卡牌");
        if (cardLocation.containsKey(cardId)) throw new IllegalStateException("同一张实际卡不能同时存在多个卡盒");
        box.add(cardId, clock.instant()); cardLocation.put(cardId, boxId); changed();
    }
    public synchronized String physicalData(UUID ownerId, UUID cardId) {
        if (!requireCard(cardId).ownerId().equals(ownerId)) throw new IllegalStateException("卡牌归属不符");
        return physicalData.getOrDefault(cardId, "");
    }
    /** Direct physical deposit: never leaves an appraised copy in the encyclopedia. */
    public synchronized CardInstance depositPhysical(UUID ownerId,UUID boxId,String definitionId,String data) {
        if (data==null||data.isBlank()||data.length()>262144) throw new IllegalArgumentException("实体卡牌数据无效");
        return transaction(()->{var card=mint(ownerId,definitionId);put(ownerId,boxId,card.instanceId());
            physicalData.put(card.instanceId(),data);changed();return card;});
    }
    public synchronized void take(UUID ownerId, UUID boxId, UUID cardId) {
        if (!physicalData(ownerId,cardId).isEmpty()) throw new IllegalStateException("已鉴定卡只能取回背包，不能存入图鉴");
        takeForInventory(ownerId,boxId,cardId);
    }
    /** Transfer adapter must immediately materialize and revoke this record in one transaction. */
    public synchronized void takeForInventory(UUID ownerId, UUID boxId, UUID cardId) {
        var box = ownedBox(ownerId, boxId); ensureUnlocked(boxId); box.remove(cardId, clock.instant()); cardLocation.remove(cardId); changed();
    }
    public synchronized void rename(UUID ownerId, UUID boxId, String nameZh) {
        var box = ownedBox(ownerId, boxId); ensureUnlocked(boxId); box.rename(nameZh, clock.instant()); changed();
    }
    public synchronized void plan(UUID ownerId, UUID boxId, Map<String, Integer> cards) {
        var box = ownedBox(ownerId, boxId); ensureUnlocked(boxId); box.plan(cards, clock.instant()); changed();
    }
    public synchronized Map<String, Integer> missingCards(UUID ownerId, UUID boxId) {
        var remaining = new java.util.LinkedHashMap<>(ownedBox(ownerId, boxId).plannedCards());
        for (var card : contentsForValidation(ownerId, boxId)) remaining.computeIfPresent(card.definitionId(), (id, count) -> count - 1);
        remaining.entrySet().removeIf(e -> e.getValue() <= 0);
        return java.util.Collections.unmodifiableMap(remaining);
    }
    public synchronized void selectRuleset(UUID ownerId, UUID boxId, String rulesetId) {
        var box = ownedBox(ownerId, boxId); ensureUnlocked(boxId); box.selectRuleset(rulesetId, clock.instant()); changed();
    }
    public synchronized void sort(UUID ownerId, UUID boxId) {
        var box = ownedBox(ownerId, boxId); ensureUnlocked(boxId);
        box.sort(java.util.Comparator.<UUID, String>comparing(id -> requireCard(id).definitionId()).thenComparing(UUID::toString), clock.instant());
        changed();
    }
    /** Only an empty, unlocked box may be removed. Existing item credentials become invalid, never new boxes. */
    public synchronized void deleteEmpty(UUID ownerId, UUID boxId) {
        var box = ownedBox(ownerId, boxId); ensureUnlocked(boxId);
        if (!box.contents().isEmpty()) throw new IllegalStateException("只能删除空卡盒，请先取出所有卡牌");
        boxes.remove(boxId); changed();
    }
    public synchronized List<CardInstance> snapshotForMatch(UUID ownerId, UUID boxId, UUID matchId) {
        return snapshotForMatch(ownerId, boxId, matchId, matchId);
    }
    /**
     * Creates an immutable deck snapshot while keeping the physical card box locked by
     * a longer-lived owner such as a best-of-three series.  The game ID is deliberately
     * not accepted as a client decision; callers generate it on the server.
     */
    public synchronized List<CardInstance> snapshotForMatch(UUID ownerId, UUID boxId, UUID matchId, UUID lockOwnerId) {
        var box = ownedBox(ownerId, boxId);
        if (!missingCards(ownerId, boxId).isEmpty()) throw new IllegalStateException("导入的卡组尚有缺卡，请先补齐半透明卡牌");
        UUID existing = boxLocks.get(boxId);
        if (existing != null && !existing.equals(lockOwnerId)) throw new IllegalStateException("卡盒已被其他对局锁定，暂时无法编辑");
        if (existing == null) { boxLocks.put(boxId, lockOwnerId); changed(); }
        var snapshot = new ArrayList<CardInstance>(); for (UUID id : box.contents()) snapshot.add(requireCard(id)); return List.copyOf(snapshot);
    }
    public synchronized void unlock(UUID matchId) { if (boxLocks.entrySet().removeIf(e -> e.getValue().equals(matchId))) changed(); }
    public synchronized DeckBox getOwned(UUID ownerId, UUID boxId) { return ownedBox(ownerId, boxId); }
    public synchronized boolean isLocked(UUID ownerId, UUID boxId) {
        ownedBox(ownerId, boxId); return boxLocks.containsKey(boxId);
    }
    /** UI/commands must not open a locked box; internal match validation uses a separate read path. */
    public synchronized List<CardInstance> contentsForEditing(UUID ownerId, UUID boxId) {
        ownedBox(ownerId, boxId); ensureUnlocked(boxId); return contentsForValidation(ownerId, boxId);
    }
    /** Owner-only list for the future card-box screen and the temporary command interface. */
    public synchronized List<DeckBox> boxesForOwner(UUID ownerId) {
        return boxes.values().stream().filter(box -> box.ownerId().equals(ownerId)).sorted(java.util.Comparator.comparing(DeckBox::lastModified).reversed()).toList();
    }
    /** Read-only card list for the common validator; it cannot reveal another owner's box. */
    public synchronized List<CardInstance> contentsForValidation(UUID ownerId, UUID boxId) {
        var box = ownedBox(ownerId, boxId);
        var result = new ArrayList<CardInstance>();
        for (UUID id : box.contents()) result.add(requireCard(id));
        return List.copyOf(result);
    }
    /** Read-only unboxed collection view, intended for a future owner-only card collection screen. */
    public synchronized List<CardInstance> unboxedForOwner(UUID ownerId) {
        return cards.values().stream().filter(card -> card.ownerId().equals(ownerId) && !cardLocation.containsKey(card.instanceId())).toList();
    }
    /** Snapshot excludes transient match locks. On a restart, unfinished matches are safely unlocked. */
    public synchronized Snapshot snapshot() {
        List<SavedCard> savedCards = new ArrayList<>();
        for (CardInstance card : cards.values()) savedCards.add(new SavedCard(card.instanceId(), card.ownerId(), card.definitionId(), cardLocation.get(card.instanceId()), physicalData.getOrDefault(card.instanceId(), "")));
        List<SavedBox> savedBoxes = new ArrayList<>();
        for (DeckBox box : boxes.values()) savedBoxes.add(new SavedBox(box.id(), box.ownerId(), box.nameZh(), box.selectedRulesetId(), box.lastModified(), box.contents(), box.plannedCards()));
        return new Snapshot(List.copyOf(savedCards), List.copyOf(savedBoxes), List.copyOf(discoveries),List.copyOf(histories.values()));
    }
    public static CardBoxService restore(Clock clock, Snapshot snapshot) {
        CardBoxService service = new CardBoxService(clock);
        service.discoveries.addAll(snapshot.discoveries());
        if(snapshot.histories()!=null)for(var h:snapshot.histories())service.histories.put(new Discovery(h.owner(),h.definitionId()),h);
        for(var d:snapshot.discoveries())service.histories.putIfAbsent(d,new CardHistory(d.owner(),d.definitionId(),0,0,0,0));
        for(var c:snapshot.cards())service.histories.putIfAbsent(new Discovery(c.ownerId(),c.definitionId()),new CardHistory(c.ownerId(),c.definitionId(),0,0,0,0));
        for (SavedCard saved : snapshot.cards()) {
            if (service.cards.putIfAbsent(saved.instanceId(), new CardInstance(saved.instanceId(), saved.ownerId(), saved.definitionId())) != null)
                throw new IllegalArgumentException("存档中存在重复卡牌实例 ID");
            if(saved.physicalData()!=null&&!saved.physicalData().isBlank()) {
                if(saved.boxId()==null) throw new IllegalArgumentException("已鉴定卡不能位于图鉴");
                service.physicalData.put(saved.instanceId(),saved.physicalData());
            }
        }
        for (SavedBox saved : snapshot.boxes()) {
            if (service.boxes.putIfAbsent(saved.id(), DeckBox.restore(saved.id(), saved.ownerId(), saved.nameZh(), CurrentFormats.migrate(saved.selectedRulesetId()), saved.lastModified(), saved.contents())) != null)
                throw new IllegalArgumentException("存档中存在重复卡盒 ID");
            service.boxes.get(saved.id()).plan(saved.plannedCards() == null ? Map.of() : saved.plannedCards(), saved.lastModified());
            for (UUID cardId : saved.contents()) {
                CardInstance card = service.requireCard(cardId);
                if (!card.ownerId().equals(saved.ownerId()) || service.cardLocation.putIfAbsent(cardId, saved.id()) != null)
                    throw new IllegalArgumentException("存档中的卡盒归属或卡牌位置无效");
            }
        }
        for (SavedCard saved : snapshot.cards()) {
            UUID actual = service.cardLocation.get(saved.instanceId());
            if (!java.util.Objects.equals(actual, saved.boxId())) throw new IllegalArgumentException("存档中的卡牌位置与卡盒内容不一致");
        }
        return service;
    }
    /** Restores persistent ownership while retaining current transient match locks. */
    public synchronized void restoreSnapshot(Snapshot snapshot) {
        CardBoxService restored = restore(clock, snapshot);
        cards.clear(); cards.putAll(restored.cards); physicalData.clear(); physicalData.putAll(restored.physicalData); boxes.clear(); boxes.putAll(restored.boxes);
        cardLocation.clear(); cardLocation.putAll(restored.cardLocation); discoveries.clear(); discoveries.addAll(restored.discoveries);histories.clear();histories.putAll(restored.histories);
    }
    public synchronized void onChanged(Runnable listener) { this.changeListener = java.util.Objects.requireNonNull(listener); }
    private DeckBox ownedBox(UUID ownerId, UUID boxId) {
        var box = boxes.get(boxId); if (box == null || !box.ownerId().equals(ownerId)) throw new IllegalArgumentException("找不到你的卡盒"); return box;
    }
    private CardInstance requireCard(UUID id) { var card = cards.get(id); if (card == null) throw new IllegalArgumentException("找不到卡牌实例"); return card; }
    private void ensureUnlocked(UUID boxId) { if (boxLocks.containsKey(boxId)) throw new IllegalStateException("卡盒已被对局锁定，暂时无法编辑"); }
    private void changed() { changeListener.run(); }
}
