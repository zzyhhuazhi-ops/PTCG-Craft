package dev.hua.ptcglive.packs;

import dev.hua.ptcglive.engine.RandomAuditEvent;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Physical pack opening audit, deliberately independent from any shop. */
public final class PackOpeningAuditService {
    public record Record(UUID playerId, String setId, String dataVersion, String openedAtUtc, long serverTick,
                         List<String> cardDefinitionIds, List<RandomAuditEvent> randomAudit) { }
    public record Snapshot(List<Record> records) { }
    private static final int MAX_RECORDS = 10_000;
    private final Map<String, SetPackDefinition> packs;
    private final List<Record> records = new ArrayList<>();
    private Runnable changeListener = () -> { };

    public PackOpeningAuditService(Map<String, SetPackDefinition> packs) { this(packs, new Snapshot(List.of())); }
    public PackOpeningAuditService(Map<String, SetPackDefinition> packs, Snapshot saved) {
        this.packs = Map.copyOf(packs);
        if (saved.records().size() > MAX_RECORDS) throw new IllegalArgumentException("开包流水超过安全上限");
        records.addAll(saved.records());
    }
    public synchronized void record(UUID playerId, List<PackOpeningService.Result> opened, long serverTick) {
        if (serverTick < 0) throw new IllegalArgumentException("服务器刻必须为非负数");
        PackBatchPolicy.validateCount(opened.size());
        List<Record> before = List.copyOf(records);
        try {
            for (var result : opened) {
                var pack = packs.get(result.setId());
                if (pack == null || !pack.dataVersion().equals(result.dataVersion()) || result.cards().size() != pack.cardsPerPack())
                    throw new IllegalArgumentException("开包日志的卡包版本或张数无效");
                records.add(new Record(playerId, result.setId(), result.dataVersion(), java.time.Instant.now().toString(), serverTick,
                    result.cards().stream().map(CardInstance::definitionId).toList(), result.audit()));
                if (records.size() > MAX_RECORDS) records.remove(0);
            }
            changeListener.run();
        } catch (RuntimeException failure) { records.clear(); records.addAll(before); throw failure; }
    }
    public synchronized Snapshot snapshot() { return new Snapshot(List.copyOf(records)); }
    public synchronized List<Record> recordsForAdministration() { return List.copyOf(records); }
    public synchronized void onChanged(Runnable listener) { changeListener = Objects.requireNonNull(listener); }
}
