package dev.hua.ptcglive.persistence;

import dev.hua.ptcglive.engine.RandomAuditEvent;
import dev.hua.ptcglive.economy.EconomyService;
import dev.hua.ptcglive.economy.PokeCoinLedger;
import dev.hua.ptcglive.packs.PackOpeningAuditService;
import dev.hua.ptcglive.rules.CardBoxService;
import dev.hua.ptcglive.starter.StarterDeck100Service;
import dev.hua.ptcglive.table.CardTableKey;
import dev.hua.ptcglive.table.CardTableManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

/** World-saved, server-only ledger for cards, boxes, PokéCoin accounts and transaction history. */
public final class PtcgPersistentState extends PersistentState {
    public static final String ID = "ptcglive_ledger";
    private static final int FORMAT = 8;
    public static final PersistentState.Type<PtcgPersistentState> TYPE = new PersistentState.Type<>(
        PtcgPersistentState::new, PtcgPersistentState::fromNbt, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private CardBoxService.Snapshot savedCards = new CardBoxService.Snapshot(List.of(), List.of());
    private PokeCoinLedger.Snapshot savedEconomy = new PokeCoinLedger.Snapshot(List.of(), List.of(), List.of());
    private PackOpeningAuditService.Snapshot savedPackOpeningAudit = new PackOpeningAuditService.Snapshot(List.of());
    private StarterDeck100Service.Snapshot savedStarterDeck100 = new StarterDeck100Service.Snapshot(List.of());
    private CardTableManager.Snapshot savedTables = new CardTableManager.Snapshot(List.of(), List.of(), List.of());
    private CardBoxService liveCards;
    private EconomyService liveEconomy;
    private PackOpeningAuditService livePackOpeningAudit;
    private StarterDeck100Service liveStarterDeck100;
    private CardTableManager liveTables;

    public static PtcgPersistentState get(ServerWorld overworld) {
        return overworld.getPersistentStateManager().getOrCreate(TYPE, ID);
    }
    public CardBoxService.Snapshot cardSnapshot() { return savedCards; }
    public PokeCoinLedger.Snapshot economySnapshot() { return savedEconomy; }
    public PackOpeningAuditService.Snapshot packOpeningAuditSnapshot() { return savedPackOpeningAudit; }
    public StarterDeck100Service.Snapshot starterDeck100Snapshot() { return savedStarterDeck100; }
    public CardTableManager.Snapshot tableSnapshot() { return savedTables; }
    public void bind(CardBoxService cards, EconomyService economy, PackOpeningAuditService packOpeningAudit,
                     StarterDeck100Service starterDeck100, CardTableManager tables) {
        liveCards = cards; liveEconomy = economy; livePackOpeningAudit = packOpeningAudit;
        liveStarterDeck100 = starterDeck100; liveTables = tables;
        cards.onChanged(this::markDirty); economy.onChanged(this::markDirty); packOpeningAudit.onChanged(this::markDirty);
        starterDeck100.onChanged(this::markDirty); tables.onChanged(this::markDirty);
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        CardBoxService.Snapshot cards = liveCards == null ? savedCards : liveCards.snapshot();
        PokeCoinLedger.Snapshot economy = liveEconomy == null ? savedEconomy : liveEconomy.snapshot();
        PackOpeningAuditService.Snapshot packOpeningAudit = livePackOpeningAudit == null ? savedPackOpeningAudit : livePackOpeningAudit.snapshot();
        StarterDeck100Service.Snapshot starterDeck100 = liveStarterDeck100 == null ? savedStarterDeck100 : liveStarterDeck100.snapshot();
        CardTableManager.Snapshot tables = liveTables == null ? savedTables : liveTables.snapshot();
        nbt.putInt("format", FORMAT);
        nbt.put("cards", writeCards(cards)); nbt.put("boxes", writeBoxes(cards));
        NbtList discoveries = new NbtList();
        for (var seen : cards.discoveries()) { var raw=new NbtCompound();raw.putUuid("owner",seen.owner());raw.putString("definition",seen.definitionId());discoveries.add(raw); }
        nbt.put("collectionDiscovery",discoveries);
        NbtList histories=new NbtList();for(var h:cards.histories()){
            var raw=new NbtCompound();raw.putUuid("owner",h.owner());raw.putString("definition",h.definitionId());raw.putLong("first",h.firstAcquired());
            raw.putLong("matches",h.matches());raw.putLong("wins",h.wins());raw.putLong("knockouts",h.knockouts());
            raw.putLong("completedMatches",h.completedMatches());raw.putLong("completedWins",h.completedWins());raw.putBoolean("legacyResultsUnknown",h.legacyResultsUnknown());histories.add(raw);
        }nbt.put("cardHistories",histories);
        nbt.put("walletBalances", writeBalances(economy));
        nbt.put("economyHistory", writeEconomyHistory(economy));
        nbt.put("economyRequests", writeEconomyRequests(economy));
        NbtList rentals = new NbtList();
        for(var row:economy.rentals()) {
            var raw=new NbtCompound(); raw.putUuid("machine",row.machine()); raw.putUuid("owner",row.owner());
            raw.putLong("paid",row.paid()); raw.putLong("pending",row.pending()); raw.putInt("half",row.half()); raw.putInt("eighth",row.eighth());raw.putBoolean("closed",row.closed()); rentals.add(raw);
        }
        nbt.put("rentalReceipts",rentals);
        nbt.put("packOpenAudit", writeOpeningAudit(packOpeningAudit));
        nbt.put("starterDeck100Audit", writeStarterDeck100Audit(starterDeck100));
        nbt.put("tableWaiting", writeWaitingTables(tables));
        nbt.put("tableInterrupted", writeInterruptedMatches(tables));
        nbt.put("tableCompleted", writeCompletedMatches(tables));
        return nbt;
    }

    private static PtcgPersistentState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        PtcgPersistentState state = new PtcgPersistentState();
        if (!nbt.contains("format")) return state;
        int format = nbt.getInt("format");
        if (format < 1 || format > FORMAT) throw new IllegalStateException("不支持的 PTCG Craft 存档版本");
        List<CardBoxService.SavedCard> cards = new ArrayList<>();
        for (int i = 0; i < nbt.getList("cards", NbtElement.COMPOUND_TYPE).size(); i++) {
            NbtCompound raw = nbt.getList("cards", NbtElement.COMPOUND_TYPE).getCompound(i);
            cards.add(new CardBoxService.SavedCard(uuid(raw, "id"), uuid(raw, "owner"), raw.getString("definition"),
                raw.containsUuid("box") ? raw.getUuid("box") : null, raw.getString("physicalData")));
        }
        List<CardBoxService.SavedBox> boxes = new ArrayList<>();
        NbtList rawBoxes = nbt.getList("boxes", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < rawBoxes.size(); i++) {
            NbtCompound raw = rawBoxes.getCompound(i); List<UUID> contents = new ArrayList<>();
            NbtList rawContents = raw.getList("contents", NbtElement.COMPOUND_TYPE);
            for (int card = 0; card < rawContents.size(); card++) contents.add(uuid(rawContents.getCompound(card), "id"));
            var plan = new java.util.LinkedHashMap<String, Integer>();
            var planned = raw.getList("plannedCards", NbtElement.COMPOUND_TYPE);
            for (int entry = 0; entry < planned.size(); entry++) {
                var row = planned.getCompound(entry); plan.put(row.getString("definition"), row.getInt("count"));
            }
            boxes.add(new CardBoxService.SavedBox(uuid(raw, "id"), uuid(raw, "owner"), raw.getString("name"), raw.getString("ruleset"),
                Instant.ofEpochMilli(raw.getLong("modified")), List.copyOf(contents), plan));
        }
        List<CardBoxService.Discovery> discoveries = new ArrayList<>();
        var rawDiscoveries = nbt.getList("collectionDiscovery",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<rawDiscoveries.size();i++){var raw=rawDiscoveries.getCompound(i);discoveries.add(new CardBoxService.Discovery(uuid(raw,"owner"),raw.getString("definition")));}
        var histories=new ArrayList<CardBoxService.CardHistory>();var rawHistories=nbt.getList("cardHistories",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<rawHistories.size();i++){var r=rawHistories.getCompound(i);histories.add(new CardBoxService.CardHistory(uuid(r,"owner"),r.getString("definition"),Math.max(0,r.getLong("first")),Math.max(0,r.getLong("matches")),Math.max(0,r.getLong("wins")),Math.max(0,r.getLong("knockouts")),
            Math.max(0,r.getLong("completedMatches")),Math.max(0,r.getLong("completedWins")),r.contains("completedMatches")?r.getBoolean("legacyResultsUnknown"):r.getLong("matches")>0||r.getLong("wins")>0));}
        state.savedCards = new CardBoxService.Snapshot(List.copyOf(cards), List.copyOf(boxes),List.copyOf(discoveries),List.copyOf(histories));
        if (format < 4) {
            state.savedCards = CardBoxService.retireLegacyUnboxed(state.savedCards);
            org.slf4j.LoggerFactory.getLogger("ptcglive").info(
                "收藏迁移 {} → 4：按用户指定废除 {} 条旧未装盒收藏，保留 {} 张已装盒卡；不修改玩家背包。",
                format, cards.size() - state.savedCards.cards().size(), state.savedCards.cards().size());
            state.markDirty();
        }
        List<PackOpeningAuditService.Record> openingAudit = new ArrayList<>();
        NbtList rawOpeningAudit = nbt.getList("packOpenAudit", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < rawOpeningAudit.size(); i++) openingAudit.add(readOpeningRecord(rawOpeningAudit.getCompound(i)));
        state.savedPackOpeningAudit = new PackOpeningAuditService.Snapshot(List.copyOf(openingAudit));
        if (format >= 5) {
            List<PokeCoinLedger.SavedBalance> balances = new ArrayList<>();
            NbtList rawBalances = nbt.getList("walletBalances", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < rawBalances.size(); i++) {
                var raw = rawBalances.getCompound(i); balances.add(new PokeCoinLedger.SavedBalance(uuid(raw, "player"), raw.getLong("balance")));
            }
            List<PokeCoinLedger.Transaction> history = new ArrayList<>();
            NbtList rawHistory = nbt.getList("economyHistory", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < rawHistory.size(); i++) history.add(readEconomyRecord(rawHistory.getCompound(i)));
            List<UUID> requests = new ArrayList<>();
            NbtList rawRequests = nbt.getList("economyRequests", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < rawRequests.size(); i++) requests.add(uuid(rawRequests.getCompound(i), "id"));
            List<PokeCoinLedger.RentalReceipt> rentals=new ArrayList<>();
            var rawRentals=nbt.getList("rentalReceipts",NbtElement.COMPOUND_TYPE);
            for(int i=0;i<rawRentals.size();i++) {
                var raw=rawRentals.getCompound(i);
                rentals.add(new PokeCoinLedger.RentalReceipt(uuid(raw,"machine"),uuid(raw,"owner"),raw.getLong("paid"),raw.getLong("pending"),raw.getInt("half"),raw.getBoolean("closed"),raw.contains("eighth")?raw.getInt("eighth"):raw.getInt("half")*4));
            }
            state.savedEconomy = new PokeCoinLedger.Snapshot(List.copyOf(balances), List.copyOf(history), List.copyOf(requests),List.copyOf(rentals));
        }
        List<StarterDeck100Service.GrantRecord> starterDeckAudit = new ArrayList<>();
        NbtList rawStarterDeckAudit = nbt.getList("starterDeck100Audit", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < rawStarterDeckAudit.size(); i++) starterDeckAudit.add(readStarterDeck100Record(rawStarterDeckAudit.getCompound(i)));
        state.savedStarterDeck100 = new StarterDeck100Service.Snapshot(List.copyOf(starterDeckAudit));
        if (format >= 2) {
            List<CardTableManager.SavedWaitingTable> waitingTables = new ArrayList<>();
            NbtList rawWaitingTables = nbt.getList("tableWaiting", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < rawWaitingTables.size(); i++) {
                NbtCompound raw = rawWaitingTables.getCompound(i);
                CardTableKey key = new CardTableKey(raw.getString("dimension"), raw.getLong("position"));
                waitingTables.add(new CardTableManager.SavedWaitingTable(key, uuid(raw, "host"), raw.getString("ruleset"),
                    raw.containsUuid("guest") ? raw.getUuid("guest") : null,
                    raw.containsUuid("hostBox") ? raw.getUuid("hostBox") : null,
                    raw.containsUuid("guestBox") ? raw.getUuid("guestBox") : null));
            }
            List<CardTableManager.InterruptedMatch> interruptedMatches = new ArrayList<>();
            NbtList rawInterrupted = nbt.getList("tableInterrupted", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < rawInterrupted.size(); i++) {
                NbtCompound raw = rawInterrupted.getCompound(i);
                CardTableKey key = raw.getBoolean("hasTable") ? new CardTableKey(raw.getString("dimension"), raw.getLong("position")) : null;
                interruptedMatches.add(new CardTableManager.InterruptedMatch(uuid(raw, "match"), key, uuid(raw, "first"),
                    raw.containsUuid("second") ? raw.getUuid("second") : null, Instant.ofEpochMilli(raw.getLong("recorded")), raw.getString("reason")));
            }
            List<CardTableManager.CompletedMatch> completedMatches = new ArrayList<>();
            if (format >= 3) {
                NbtList rawCompleted = nbt.getList("tableCompleted", NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < rawCompleted.size(); i++) {
                    NbtCompound raw = rawCompleted.getCompound(i);
                    CardTableKey key = raw.getBoolean("hasTable") ? new CardTableKey(raw.getString("dimension"), raw.getLong("position")) : null;
                    List<dev.hua.ptcglive.engine.MatchReplayEvent> replay = new ArrayList<>();
                    NbtList rawReplay = raw.getList("replay", NbtElement.COMPOUND_TYPE);
                    for (int replayIndex = 0; replayIndex < rawReplay.size(); replayIndex++) {
                        NbtCompound event = rawReplay.getCompound(replayIndex);
                        replay.add(new dev.hua.ptcglive.engine.MatchReplayEvent(event.getLong("sequence"), uuid(event, "actor"),
                            event.getString("operation"), event.getInt("randomThrough")));
                    }
                    List<RandomAuditEvent> random = new ArrayList<>();
                    NbtList rawRandom = raw.getList("random", NbtElement.COMPOUND_TYPE);
                    for (int randomIndex = 0; randomIndex < rawRandom.size(); randomIndex++) {
                        NbtCompound event = rawRandom.getCompound(randomIndex);
                        random.add(new RandomAuditEvent(event.getLong("sequence"), event.getString("operation"), event.getInt("bound"), event.getInt("result")));
                    }
                    dev.hua.ptcglive.engine.MatchResult.Reason reason;
                    try { reason = dev.hua.ptcglive.engine.MatchResult.Reason.valueOf(raw.getString("reason")); }
                    catch (IllegalArgumentException invalidReason) { throw new IllegalStateException("PTCG Craft 存档包含未知对局结束原因", invalidReason); }
                    completedMatches.add(new CardTableManager.CompletedMatch(uuid(raw, "match"), key, uuid(raw, "first"),
                        raw.containsUuid("second") ? raw.getUuid("second") : null, raw.containsUuid("winner") ? raw.getUuid("winner") : null,
                        reason, Instant.ofEpochMilli(raw.getLong("finished")), List.copyOf(replay), List.copyOf(random)));
                }
            }
            state.savedTables = new CardTableManager.Snapshot(List.copyOf(waitingTables), List.copyOf(interruptedMatches), List.copyOf(completedMatches));
        }
        return state;
    }
    private static NbtList writeCards(CardBoxService.Snapshot snapshot) {
        NbtList result = new NbtList();
        for (var card : snapshot.cards()) { NbtCompound raw = new NbtCompound(); raw.putUuid("id", card.instanceId()); raw.putUuid("owner", card.ownerId()); raw.putString("definition", card.definitionId()); if (card.boxId() != null) raw.putUuid("box", card.boxId()); if(card.physicalData()!=null&&!card.physicalData().isBlank()) raw.putString("physicalData",card.physicalData()); result.add(raw); }
        return result;
    }
    private static NbtList writeBoxes(CardBoxService.Snapshot snapshot) {
        NbtList result = new NbtList();
        for (var box : snapshot.boxes()) {
            NbtCompound raw = new NbtCompound(); raw.putUuid("id", box.id()); raw.putUuid("owner", box.ownerId()); raw.putString("name", box.nameZh()); raw.putString("ruleset", box.selectedRulesetId()); raw.putLong("modified", box.lastModified().toEpochMilli());
            NbtList contents = new NbtList(); for (UUID id : box.contents()) { NbtCompound item = new NbtCompound(); item.putUuid("id", id); contents.add(item); } raw.put("contents", contents);
            NbtList planned = new NbtList();
            box.plannedCards().forEach((id, count) -> { var row = new NbtCompound(); row.putString("definition", id); row.putInt("count", count); planned.add(row); });
            raw.put("plannedCards", planned); result.add(raw);
        }
        return result;
    }
    private static NbtList writeBalances(PokeCoinLedger.Snapshot snapshot) {
        NbtList result = new NbtList();
        for (var row : snapshot.balances()) { var raw = new NbtCompound(); raw.putUuid("player", row.playerId()); raw.putLong("balance", row.balance()); result.add(raw); }
        return result;
    }
    private static NbtList writeEconomyHistory(PokeCoinLedger.Snapshot snapshot) {
        NbtList result = new NbtList();
        for (var row : snapshot.history()) {
            var raw = new NbtCompound(); raw.putUuid("id", row.id()); raw.putUuid("player", row.playerId()); raw.putString("kind", row.kind().name());
            raw.putLong("delta", row.delta()); raw.putLong("balance", row.balanceAfter()); raw.putString("detail", row.detail());
            raw.putLong("recorded", row.recordedAt().toEpochMilli()); raw.putLong("tick", row.serverTick()); result.add(raw);
        }
        return result;
    }
    private static PokeCoinLedger.Transaction readEconomyRecord(NbtCompound raw) {
        try {
            return new PokeCoinLedger.Transaction(uuid(raw, "id"), uuid(raw, "player"), PokeCoinLedger.Kind.valueOf(raw.getString("kind")),
                raw.getLong("delta"), raw.getLong("balance"), raw.getString("detail"), Instant.ofEpochMilli(raw.getLong("recorded")), raw.getLong("tick"));
        } catch (IllegalArgumentException error) { throw new IllegalStateException("宝可币存档包含未知交易类型", error); }
    }
    private static NbtList writeEconomyRequests(PokeCoinLedger.Snapshot snapshot) {
        NbtList result = new NbtList(); for (UUID id : snapshot.processedRequests()) { var raw = new NbtCompound(); raw.putUuid("id", id); result.add(raw); } return result;
    }
    private static NbtList writeOpeningAudit(PackOpeningAuditService.Snapshot snapshot) {
        NbtList result = new NbtList(); for (var record : snapshot.records()) result.add(writeOpeningRecord(record)); return result;
    }
    private static NbtCompound writeOpeningRecord(PackOpeningAuditService.Record record) {
        NbtCompound raw = new NbtCompound(); raw.putUuid("player", record.playerId()); raw.putString("set", record.setId()); raw.putString("version", record.dataVersion()); raw.putString("openedAtUtc", record.openedAtUtc()); raw.putLong("tick", record.serverTick());
        NbtList cards = new NbtList(); for (String id : record.cardDefinitionIds()) { NbtCompound item = new NbtCompound(); item.putString("id", id); cards.add(item); } raw.put("cards", cards);
        NbtList random = new NbtList(); for (RandomAuditEvent event : record.randomAudit()) { NbtCompound item = new NbtCompound(); item.putLong("sequence", event.sequence()); item.putString("operation", event.operation()); item.putInt("bound", event.bound()); item.putInt("result", event.result()); random.add(item); } raw.put("random", random);
        return raw;
    }
    private static PackOpeningAuditService.Record readOpeningRecord(NbtCompound raw) {
        List<String> cards = new ArrayList<>(); NbtList rawCards = raw.getList("cards", NbtElement.COMPOUND_TYPE); for (int i = 0; i < rawCards.size(); i++) cards.add(rawCards.getCompound(i).getString("id"));
        List<RandomAuditEvent> random = new ArrayList<>(); NbtList rawRandom = raw.getList("random", NbtElement.COMPOUND_TYPE); for (int i = 0; i < rawRandom.size(); i++) { NbtCompound event = rawRandom.getCompound(i); random.add(new RandomAuditEvent(event.getLong("sequence"), event.getString("operation"), event.getInt("bound"), event.getInt("result"))); }
        return new PackOpeningAuditService.Record(uuid(raw, "player"), raw.getString("set"), raw.getString("version"), raw.getString("openedAtUtc"), raw.getLong("tick"), List.copyOf(cards), List.copyOf(random));
    }
    private static NbtList writeStarterDeck100Audit(StarterDeck100Service.Snapshot snapshot) {
        NbtList result = new NbtList();
        for (var record : snapshot.grants()) {
            NbtCompound raw = new NbtCompound();
            raw.putUuid("owner", record.ownerId()); raw.putString("number", record.deckNumber()); raw.putString("name", record.deckNameZh()); raw.putUuid("box", record.boxId()); raw.putLong("tick", record.serverTick());
            NbtList random = new NbtList();
            for (RandomAuditEvent event : record.randomAudit()) {
                NbtCompound item = new NbtCompound(); item.putLong("sequence", event.sequence()); item.putString("operation", event.operation()); item.putInt("bound", event.bound()); item.putInt("result", event.result()); random.add(item);
            }
            raw.put("random", random); result.add(raw);
        }
        return result;
    }
    private static StarterDeck100Service.GrantRecord readStarterDeck100Record(NbtCompound raw) {
        List<RandomAuditEvent> random = new ArrayList<>();
        NbtList rawRandom = raw.getList("random", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < rawRandom.size(); i++) {
            NbtCompound event = rawRandom.getCompound(i);
            random.add(new RandomAuditEvent(event.getLong("sequence"), event.getString("operation"), event.getInt("bound"), event.getInt("result")));
        }
        return new StarterDeck100Service.GrantRecord(uuid(raw, "owner"), raw.getString("number"), raw.getString("name"), uuid(raw, "box"), raw.getLong("tick"), List.copyOf(random));
    }
    private static NbtList writeWaitingTables(CardTableManager.Snapshot snapshot) {
        NbtList result = new NbtList();
        for (CardTableManager.SavedWaitingTable table : snapshot.waitingTables()) {
            NbtCompound raw = new NbtCompound();
            raw.putString("dimension", table.key().dimensionId()); raw.putLong("position", table.key().packedBlockPos());
            raw.putUuid("host", table.host()); raw.putString("ruleset", table.rulesetId());
            if (table.guest() != null) raw.putUuid("guest", table.guest());
            if (table.hostBox() != null) raw.putUuid("hostBox", table.hostBox());
            if (table.guestBox() != null) raw.putUuid("guestBox", table.guestBox());
            result.add(raw);
        }
        return result;
    }
    private static NbtList writeInterruptedMatches(CardTableManager.Snapshot snapshot) {
        NbtList result = new NbtList();
        for (CardTableManager.InterruptedMatch match : snapshot.interruptedMatches()) {
            NbtCompound raw = new NbtCompound(); raw.putUuid("match", match.matchId()); raw.putUuid("first", match.firstPlayer());
            if (match.secondPlayer() != null) raw.putUuid("second", match.secondPlayer());
            raw.putLong("recorded", match.recordedAt().toEpochMilli()); raw.putString("reason", match.reasonZh());
            if (match.table() != null) {
                raw.putBoolean("hasTable", true); raw.putString("dimension", match.table().dimensionId()); raw.putLong("position", match.table().packedBlockPos());
            } else raw.putBoolean("hasTable", false);
            result.add(raw);
        }
        return result;
    }
    private static NbtList writeCompletedMatches(CardTableManager.Snapshot snapshot) {
        NbtList result = new NbtList();
        for (CardTableManager.CompletedMatch match : snapshot.completedMatches()) {
            NbtCompound raw = new NbtCompound(); raw.putUuid("match", match.matchId()); raw.putUuid("first", match.firstPlayer());
            if (match.secondPlayer() != null) raw.putUuid("second", match.secondPlayer());
            if (match.winner() != null) raw.putUuid("winner", match.winner());
            raw.putString("reason", match.reason().name()); raw.putLong("finished", match.finishedAt().toEpochMilli());
            if (match.table() != null) {
                raw.putBoolean("hasTable", true); raw.putString("dimension", match.table().dimensionId()); raw.putLong("position", match.table().packedBlockPos());
            } else raw.putBoolean("hasTable", false);
            NbtList replay = new NbtList();
            for (dev.hua.ptcglive.engine.MatchReplayEvent event : match.replay()) {
                NbtCompound item = new NbtCompound(); item.putLong("sequence", event.sequence()); item.putUuid("actor", event.actor());
                item.putString("operation", event.operation()); item.putInt("randomThrough", event.randomEventsThrough()); replay.add(item);
            }
            raw.put("replay", replay);
            NbtList random = new NbtList();
            for (RandomAuditEvent event : match.randomAudit()) {
                NbtCompound item = new NbtCompound(); item.putLong("sequence", event.sequence()); item.putString("operation", event.operation());
                item.putInt("bound", event.bound()); item.putInt("result", event.result()); random.add(item);
            }
            raw.put("random", random); result.add(raw);
        }
        return result;
    }
    private static UUID uuid(NbtCompound raw, String key) { if (!raw.containsUuid(key)) throw new IllegalStateException("PTCG Craft 存档缺少 UUID 字段: " + key); return raw.getUuid(key); }
}
