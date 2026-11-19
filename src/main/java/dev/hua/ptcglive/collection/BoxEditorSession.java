package dev.hua.ptcglive.collection;

import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Owner-only, batched editor. The client selects intentions; it never supplies card ownership or counts. */
public final class BoxEditorSession {
    /** Default collection page size until the client reports how many cards its grid can show. */
    public static final int PAGE_SIZE = 12;
    /** Upper bound for a client-requested page size; keeps every snapshot small. */
    public static final int MAX_PAGE_SIZE = 60;
    public record CardRow(String definitionId, String nameZh, int count, int inventoryCount, int collectionCount, boolean missing) {
        public CardRow(String id,String name,int count){this(id,name,count,0,count,false);}
        public CardRow(String id,String name,int count,int inventory,int collection){this(id,name,count,inventory,collection,false);}
    }
    public record RuleRow(String id, String nameZh, String version, String statusZh, boolean canStart, List<String> reasons) { }
    public record Snapshot(UUID sessionId, UUID boxId, int revision, String nameZh, String rulesetId,
                           String lastModified, int totalCards, List<CardRow> contents, List<CardRow> collection,
                           int collectionPage, int collectionPages, String query, List<RuleRow> rules,
                           String exportText, boolean deleted, int collectionPageSize, int collectionCards, String source) { }
    private final UUID sessionId = UUID.randomUUID(), owner, boxId;
    private final CardBoxService boxes;
    private final CardDatabase cards;
    private final DeckValidator validator;
    private final Map<String, Ruleset> rules;
    private final Map<String, String> labels;
    private int revision, page, pageSize = PAGE_SIZE;
    private String query = "";
    private String source = "INVENTORY";
    private Snapshot last;
    private java.util.function.Supplier<Map<String,Integer>> inventory = Map::of;
    private java.util.function.Consumer<String> addInventory;
    private java.util.function.Consumer<UUID> returnPhysical;
    public synchronized void connectPhysicalReturn(java.util.function.Consumer<UUID> action){returnPhysical=Objects.requireNonNull(action);}
    private void removeCard(UUID actor,CardInstance card){
        if(boxes.physicalData(actor,card.instanceId()).isEmpty())boxes.take(actor,boxId,card.instanceId());
        else {if(returnPhysical==null)throw new IllegalStateException("已鉴定卡只能从游戏界面取回背包");returnPhysical.accept(card.instanceId());}
    }
    private java.util.function.BooleanSupplier creativeMode = () -> false;
    /** Server-owned live mode check; never supplied by an incoming packet. */
    public synchronized void connectCreativeMode(java.util.function.BooleanSupplier mode) { creativeMode = Objects.requireNonNull(mode); }
    private java.util.function.Consumer<Runnable> atomic = move -> boxesTransaction(move);
    private void boxesTransaction(Runnable move) { boxes.transaction(() -> { move.run(); return null; }); }
    public synchronized void connectTransaction(java.util.function.Consumer<Runnable> transaction) { atomic = Objects.requireNonNull(transaction); }
    public synchronized void connectInventory(java.util.function.Supplier<Map<String,Integer>> counts,
                                               java.util.function.Consumer<String> add) {
        inventory = Objects.requireNonNull(counts); addInventory = Objects.requireNonNull(add);
    }

    public BoxEditorSession(CardBoxService boxes, CardDatabase cards, CombatCatalog combat,
                            Map<String, Ruleset> rules, Map<String, String> labels, UUID owner, UUID boxId) {
        this.boxes = Objects.requireNonNull(boxes); this.cards = Objects.requireNonNull(cards);
        this.validator = new DeckValidator(cards, combat); this.rules = Map.copyOf(rules);
        this.labels = labels; this.owner = owner; this.boxId = boxId;
        boxes.contentsForEditing(owner, boxId); // reject foreign or locked credentials before opening
    }
    public UUID sessionId() { return sessionId; }
    public UUID boxId() { return boxId; }
    public synchronized Snapshot view(UUID actor) { authorize(actor); return snapshot(""); }
    public synchronized Snapshot submit(UUID actor, int expectedRevision, String action, String argument, int requestedPage, String search) {
        authorize(actor);
        if (last == null || expectedRevision != revision) throw new IllegalStateException("界面已更新，请刷新后再操作；重复请求不会重复执行");
        boxes.contentsForEditing(actor, boxId); // every action, including export and refresh, rechecks the match lock
        String export = "";
        switch (action) {
            case "IMPORT_LIVE" -> {
                var plan = LiveDeckParser.parse(argument, labels.keySet());
                atomic.accept(() -> {
                    // Keep already boxed copies up to the requested count; return extras to the encyclopedia.
                    var keep = new LinkedHashMap<>(plan);
                    for (var card : boxes.contentsForEditing(actor, boxId)) {
                        int needed = keep.getOrDefault(card.definitionId(), 0);
                        if (needed > 0) keep.put(card.definitionId(), needed - 1);
                        else removeCard(actor,card);
                    }
                    boxes.plan(actor, boxId, plan);
                    boolean creative = creativeMode.getAsBoolean();
                    for (var entry : boxes.missingCards(actor, boxId).entrySet())
                        for (int i = 0; i < entry.getValue(); i++) if (!fillOne(actor, entry.getKey())) {
                            if (!creative) break;
                            boxes.put(actor, boxId, boxes.mint(actor, entry.getKey()).instanceId());
                        }
                });
                page = 0;
            }
            case "FILL_MISSING" -> {
                if (!boxes.missingCards(actor, boxId).containsKey(argument)) throw new IllegalArgumentException("此卡已补齐，请刷新");
                atomic.accept(() -> { if (!fillOne(actor, argument)) throw new IllegalArgumentException("背包和图鉴中还没有此卡；获得后点击半透明卡牌补入"); });
            }
            case "REFRESH" -> { }
            case "SOURCE" -> {
                if (!"INVENTORY".equals(argument) && !"COLLECTION".equals(argument))
                    throw new IllegalArgumentException("卡牌来源无效");
                source = argument; page = 0;
            }
            case "PAGE" -> {
                if (search == null || search.length() > 40 || search.codePoints().anyMatch(Character::isISOControl)
                        || requestedPage < 0 || requestedPage > 1000000) throw new IllegalArgumentException("搜索或页码无效");
                // A non-blank argument is the client's grid capacity. Blank keeps the current size (old clients).
                // Validate it before touching any state so a rejected request changes nothing.
                int requestedSize = argument.isBlank() ? pageSize : parsePageSize(argument);
                query = search.strip(); page = requestedPage; pageSize = requestedSize;
            }
            case "ADD" -> {
                if (source.equals("INVENTORY")) {
                    if (addInventory == null || inventory.get().getOrDefault(argument, 0) < 1)
                        throw new IllegalArgumentException("背包中的此卡已用完，请刷新");
                    addInventory.accept(argument);
                }
                else {
                    CardInstance chosen = boxes.unboxedForOwner(actor).stream().filter(c -> c.definitionId().equals(argument))
                        .min(Comparator.comparing(c -> c.instanceId().toString())).orElseThrow(() -> new IllegalArgumentException("收藏中的此卡已用完，请刷新"));
                    boxes.transaction(() -> { boxes.put(actor, boxId, chosen.instanceId()); return null; });
                }
            }
            case "TAKE" -> {
                CardInstance chosen = boxes.contentsForEditing(actor, boxId).stream().filter(c -> c.definitionId().equals(argument))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("此卡已不在卡盒中"));
                removeCard(actor,chosen);
            }
            case "RENAME" -> {
                if (argument == null || argument.codePoints().anyMatch(Character::isISOControl))
                    throw new IllegalArgumentException("卡盒名称不能含换行或控制字符");
                boxes.rename(actor, boxId, argument);
            }
            case "RULESET" -> {
                if (!rules.containsKey(argument)) throw new IllegalArgumentException("服务器没有安装此规则包");
                boxes.selectRuleset(actor, boxId, argument);
            }
            case "SORT" -> boxes.sort(actor, boxId);
            case "EXPORT" -> {
                Map<String, Integer> counts = new LinkedHashMap<>();
                for (CardRow row : rows(boxes.contentsForEditing(actor, boxId)))
                    counts.put(row.definitionId(), row.count());
                export = LiveDeckParser.export(counts);
            }
            case "DELETE_EMPTY" -> {
                boxes.deleteEmpty(actor, boxId);
                return last = new Snapshot(sessionId, boxId, ++revision, "已删除空卡盒", "", "", 0,
                    List.of(), List.of(), 0, 1, "", List.of(), "", true, pageSize, 0, source);
            }
            default -> throw new IllegalArgumentException("不允许的卡盒操作");
        }
        return snapshot(export);
    }
    private void authorize(UUID actor) {
        if (!owner.equals(actor)) throw new IllegalArgumentException("不能访问他人的卡盒或收藏");
        boxes.contentsForEditing(actor, boxId);
    }
    private boolean fillOne(UUID actor, String id) {
        if (addInventory != null && inventory.get().getOrDefault(id, 0) > 0) { addInventory.accept(id); return true; }
        var card = boxes.unboxedForOwner(actor).stream().filter(c -> c.definitionId().equals(id)).findFirst();
        if (card.isEmpty()) return false;
        boxes.put(actor, boxId, card.get().instanceId()); return true;
    }
    private Snapshot snapshot(String export) {
        var box = boxes.getOwned(owner, boxId); var contents = boxes.contentsForEditing(owner, boxId);
        var missing = boxes.missingCards(owner, boxId);
        boolean fromInventory = source.equals("INVENTORY");
        Map<String,Integer> counts = new LinkedHashMap<>();
        if (fromInventory) counts.putAll(inventory.get());
        else for (var card : boxes.unboxedForOwner(owner)) counts.merge(card.definitionId(), 1, Math::addExact);
        var available = counts.entrySet().stream().filter(e -> e.getValue() > 0).map(e -> new CardRow(e.getKey(),
            labels.getOrDefault(e.getKey(), e.getKey()), e.getValue(), fromInventory ? e.getValue() : 0, fromInventory ? 0 : e.getValue()))
            .filter(row -> (row.nameZh() + " " + row.definitionId() + " " + PrintedRegulationMarks.searchTerm(row.definitionId()))
                .toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT).replace('：', ':')))
            .sorted(Comparator.comparing(CardRow::definitionId)).toList();
        int pages = Math.max(1, (available.size() + pageSize - 1) / pageSize);
        page = Math.min(page, pages - 1);
        List<RuleRow> reports = new ArrayList<>();
        rules.values().stream().sorted(Comparator.comparingInt((Ruleset rule) -> dev.hua.ptcglive.rules.CurrentFormats.order(rule.id()))
            .thenComparing(Ruleset::id)).forEach(rule -> {
            var report = validator.validate(rule, contents);
            var details = new ArrayList<>(report.issues().stream().map(DeckIssue::messageZh).distinct().toList());
            details.addAll(rule.policySummaryZh().lines().toList());
            if (!missing.isEmpty()) details.add("导入卡组缺少 " + missing.values().stream().mapToInt(Integer::intValue).sum() + " 张；半透明占位不是真实卡牌。");
            reports.add(new RuleRow(rule.id(), rule.selectionLabelZh(), rule.version(), missing.isEmpty() ? report.statusZh() : "卡组缺卡", missing.isEmpty() && report.canStartMatch(),
                List.copyOf(details)));
        });
        if (!rules.containsKey(box.selectedRulesetId())) reports.add(new RuleRow(box.selectedRulesetId(), "未安装的规则包", "未知",
            "需更新卡牌／规则数据", false, List.of("所选规则包未安装，不能在卡桌开局。")));
        var displayed = new ArrayList<>(rows(contents));
        missing.forEach((id, count) -> displayed.add(new CardRow(id, labels.getOrDefault(id, id), count, 0, 0, true)));
        return last = new Snapshot(sessionId, boxId, ++revision, box.nameZh(), box.selectedRulesetId(), box.lastModified().toString(),
            contents.size(), List.copyOf(displayed), List.copyOf(available.subList(page * pageSize, Math.min(available.size(), (page + 1) * pageSize))),
            page, pages, query, List.copyOf(reports), export, false, pageSize, available.stream().mapToInt(CardRow::count).sum(), source);
    }
    private static int parsePageSize(String argument) {
        int size;
        try { size = Integer.parseInt(argument.strip()); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("每页张数无效"); }
        if (size < 1) throw new IllegalArgumentException("每页张数无效");
        return Math.min(MAX_PAGE_SIZE, size);
    }
    private List<CardRow> rows(List<CardInstance> instances) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (var card : instances) counts.merge(card.definitionId(), 1, Integer::sum);
        return counts.entrySet().stream().map(e -> new CardRow(e.getKey(),
            labels.getOrDefault(e.getKey(), cards.find(e.getKey()).map(CardDefinition::nameZh).orElse(e.getKey())), e.getValue())).toList();
    }
}
