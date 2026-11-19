package dev.hua.ptcglive.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/** Joins server-side batches into one contiguous client list without exposing page boundaries. */
final class PagedRows<T> {
    private final TreeMap<Integer, List<T>> batches = new TreeMap<>();
    private String key = "";
    private int batchCount = 1;
    private List<T> flattened = List.of();

    void accept(String nextKey, int batch, int batchesTotal, List<T> rows, boolean reset) {
        if (reset || !Objects.equals(key, nextKey)) {
            batches.clear();
            key = nextKey;
        }
        batchCount = Math.max(1, batchesTotal);
        if (batch >= 0 && batch < batchCount) batches.put(batch, List.copyOf(rows));
        batches.tailMap(batchCount).clear();
        var result = new ArrayList<T>();
        for (int index = 0; ; index++) {
            List<T> batchRows = batches.get(index);
            if (batchRows == null) break;
            result.addAll(batchRows);
        }
        flattened = List.copyOf(result);
    }

    List<T> rows() { return flattened; }
    void update(java.util.function.UnaryOperator<T> change) {
        batches.replaceAll((index, rows) -> rows.stream().map(change).toList());
        flattened = flattened.stream().map(change).toList();
    }

    int nextBatch() {
        int batch = 0;
        while (batches.containsKey(batch)) batch++;
        return batch;
    }

    boolean hasMore() { return nextBatch() < batchCount; }
}
